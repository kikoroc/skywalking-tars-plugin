package org.apache.skywalking.apm.plugin.asf.tars;

import com.google.gson.Gson;
import com.qq.tars.common.support.Endpoint;
import com.qq.tars.protocol.util.TarsHelper;
import com.qq.tars.rpc.protocol.tars.TarsServantRequest;
import com.qq.tars.rpc.protocol.tars.TarsServantResponse;
import com.qq.tars.server.config.ConfigurationManager;
import com.qq.tars.server.config.ServantAdapterConfig;
import org.apache.skywalking.apm.agent.core.context.CarrierItem;
import org.apache.skywalking.apm.agent.core.context.ContextCarrier;
import org.apache.skywalking.apm.agent.core.context.ContextManager;
import org.apache.skywalking.apm.agent.core.context.tag.AbstractTag;
import org.apache.skywalking.apm.agent.core.context.tag.StringTag;
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
public class ServerInterceptor implements InstanceMethodsAroundInterceptor {

    private static final AbstractTag<String> ARGUMENTS_TAG = new StringTag("arguments");
    private final Gson gson = new Gson();

    @Override
    public void beforeMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes, MethodInterceptResult result) throws Throwable {
        if (allArguments[0] instanceof TarsServantRequest) {
            TarsServantRequest request = (TarsServantRequest) allArguments[0];
            final ContextCarrier contextCarrier = new ContextCarrier();
            CarrierItem next = contextCarrier.items();
            if (request.getContext() == null) {
                request.setContext(new HashMap<>());
            }
            while (next.hasNext()) {
                next = next.next();
                next.setHeadValue(request.getContext().get(next.getHeadKey()));
            }

            AbstractSpan span = ContextManager.createEntrySpan(generateOperationName(request), contextCarrier);
            // 标记当前服务的endpoint
            ServantAdapterConfig cfg = ConfigurationManager.getInstance().getServerConfig()
                    .getServantAdapterConfMap().get(request.getServantName());
            if(cfg != null) {
                span.setPeer(cfg.getEndpoint().host() + ":" + cfg.getEndpoint().port());
            }
            SpanLayer.asRPCFramework(span);
            span.setComponent(ComponentsDefine.TARS_JAVA);
        }
    }

    @Override
    public Object afterMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes, Object ret) throws Throwable {
        if (allArguments[0] instanceof TarsServantRequest) {
            TarsServantResponse resp = (TarsServantResponse) allArguments[1];
            // 日志统一由日志服务处理，apm里面不记录日志
            //collectArguments(resp, ContextManager.activeSpan());
            if (resp.getRet() != TarsHelper.SERVERSUCCESS || resp.getCause() != null) {
                dealException(resp.getCause());
            }
            ContextManager.stopSpan();
        }
        return ret;
    }

    @Override
    public void handleMethodException(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes, Throwable t) {
        if (allArguments[0] instanceof TarsServantRequest) {
            dealException(t);
        }
    }

    private void dealException(Throwable throwable) {
        AbstractSpan span = ContextManager.activeSpan();
        span.log(throwable);
    }

    private String generateOperationName(TarsServantRequest request) {
        return request.getServantName() + "." + request.getFunctionName();
    }

    private void collectArguments(TarsServantResponse response, AbstractSpan span) {
        String json = gson.toJson(response.getResult());
        if (json.length() > 1024) {
            json = json.substring(0, 1024) + "..(" + json.length() + ")";
        }

        span.tag(ARGUMENTS_TAG, json);
    }
}
