APM系统[skywalking](https://github.com/apache/skywalking)的tars插件

实现收集tars rpc client/server端的性能数据和调用链路。

### 编译

1. 下载`skywalking`源码，https://github.com/apache/skywalking
2. 将`tars-1.x-plugin`放到`apm-sniffer\apm-sdk-plugin`目录下
3. 编辑`ComponentsDefine.java`，增加tars组件定义

```
public static final OfficialComponent TARS_JAVA = new OfficialComponent(106, "tars-java");
```

4. 编辑`component-libraries.yml`，增加定义

```
tars-java:
  id: 106
  languages: Java
```

5. 编译`mvn clean package -DskipTests=true`

6. 生成`skywalking-oap`(数据收集&处理)和`skywalking-ui`(web界面展示)的docker image

```
export HUB={your_hub} && export TAG=v8.3 && export ES_VERSION=es7 && make docker
```

7. `skywalking-oap`(数据收集&处理)和`skywalking-ui`(web界面展示)的k8s服务定义文件

8. 打包`skywalking-agent`，确保我们编译的`tars-1.x-plugin.jar`在`plugins`目录下，打tar.gz包并上传到oss供tars服务dockerfile下载安装

### 参考资料

> 1. 性能分析工具SkyWalking插件开发指南(https://www.bilibili.com/read/cv6638733/)
> 2. 安装Java agent(https://skyapm.github.io/document-cn-translation-of-skywalking/zh/6.2.0/setup/service-agent/java-agent/)
> 3. skywalking官方文档(https://github.com/apache/skywalking/tree/master/docs/en/guides)


### 修改点

1. `docker/oap/`和`docker/ui`，Dockerfile修改时区为UTC+8

```
RUN echo "http://mirrors.aliyun.com/alpine/v3.4/main/" > /etc/apk/repositories
RUN apk add --no-cache tzdata
ENV TZ=Asia/Shanghai
RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone
```

2. 修改日志配置`log4j2.xml`和`logback.xml`

3. 取消root pom中的一些插件定义，加快编译速度

