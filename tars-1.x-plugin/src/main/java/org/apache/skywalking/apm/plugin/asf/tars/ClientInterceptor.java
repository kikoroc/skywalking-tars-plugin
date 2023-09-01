package org.apache.skywalking.apm.plugin.asf.tars;

import com.google.gson.Gson;
import com.qq.tars.client.rpc.ServantInvokeContext;
import com.qq.tars.common.support.Holder;
import org.apache.skywalking.apm.agent.core.context.CarrierItem;
import org.apache.skywalking.apm.agent.core.context.ContextCarrier;
import org.apache.skywalking.apm.agent.core.context.ContextManager;
import org.apache.skywalking.apm.agent.core.context.tag.AbstractTag;
import org.apache.skywalking.apm.agent.core.context.tag.StringTag;
import org.apache.skywalking.apm.agent.core.context.tag.Tags;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractSpan;
import org.apache.skywalking.apm.agent.core.context.trace.SpanLayer;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;
import org.apache.skywalking.apm.network.trace.component.ComponentsDefine;

import java.lang.reflect.Method;
import java.util.HashMap;

/**
 * @author: wangpeng (kikoroc@gmail.com)
 * @date: 2021/1/13
 * @description:
 */
public class ClientInterceptor implements InstanceMethodsAroundInterceptor {

    private static final AbstractTag<String> ARGUMENTS_TAG = new StringTag("arguments");
    private final Gson gson = new Gson();

    @Override
    public void beforeMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes, MethodInterceptResult result) throws Throwable {
        ServantInvokeContext request = (ServantInvokeContext) allArguments[0];
        // 只处理同步调用
        if(request.isNormal()) {
            final ContextCarrier contextCarrier = new ContextCarrier();
            AbstractSpan span = ContextManager.createExitSpan(generateOperationName(request), contextCarrier,
                    request.getInvoker().getUrl().getHost() + ":" + request.getInvoker().getUrl().getPort());
            CarrierItem next = contextCarrier.items();
            while (next.hasNext()) {
                next = next.next();
                if (request.getAttachments() == null) {
                    request.setAttachments(new HashMap<>());
                }
                request.getAttachments().put(next.getHeadKey(), next.getHeadValue());
            }

            // url跟peer重复，不需要上报了
            //Tags.URL.set(span, request.getInvoker().getUrl().toIdentityString() + "." + request.getMethodName());
            // 日志统一由日志服务处理，apm里面不记录日志
            //collectArguments(request.getArguments(), span);
            span.setComponent(ComponentsDefine.TARS_JAVA);
            SpanLayer.asRPCFramework(span);
        }
    }

    @Override
    public Object afterMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes, Object ret) throws Throwable {
        // async return null
        // futurn return CompletableFuture
        // sync return response tars会抛出异常，这里不需要调用dealException跟踪异常信息
        ServantInvokeContext request = (ServantInvokeContext) allArguments[0];
        // 只处理同步调用
        if(request.isNormal()) {
            ContextManager.stopSpan();
        }
        return ret;
    }

    @Override
    public void handleMethodException(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes, Throwable t) {
        ServantInvokeContext request = (ServantInvokeContext) allArguments[0];
        // 只处理同步调用
        if(request.isNormal()) {
            dealException(t);
        }
    }

    private String generateOperationName(ServantInvokeContext request){
//        return request.getInvoker().getApi().getSimpleName()+"."+request.getMethodName();
        return request.getInvoker().getUrl().getPath()+"."+request.getMethodName();
    }

    private void dealException(Throwable throwable) {
        AbstractSpan span = ContextManager.activeSpan();
        span.log(throwable);
    }

    /**
     * * tars接口一般三种形式
     *      * 1. api(XxxReq tReq) 只有请求参数
     *      * 2. api(XxxReq tReq, out XxxRsp tRsp) 请求参数+响应
     *      * 3. api(out XxxRsp tRsp) 无请求参数，只有响应
     * @param args
     * @param span
     */
    private void collectArguments(Object[] args, AbstractSpan span){
        if (args.length >= 1) {
            Object first = args[0];
            String param;
            if(!(first instanceof Holder)){
                param = gson.toJson(first);
            }else{
                param = "no param";
            }

            span.tag(ARGUMENTS_TAG, param);
        }
    }

}
