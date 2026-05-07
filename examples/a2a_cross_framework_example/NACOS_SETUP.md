# Nacos服务发现配置指南

本指南介绍如何将A2A服务器注册到Nacos，并使用服务发现方式进行客户端调用。

## 前置条件

1. 确保Nacos服务器已启动（默认地址：`localhost:8848`）
2. 确保A2A服务器支持Nacos注册

## 服务端注册到Nacos

### Spring AI Alibaba A2A Server

在Spring AI Alibaba A2A Server的`application.yml`或`application.properties`中添加Nacos配置：

```yaml
spring:
  cloud:
    nacos:
      discovery:
        server-addr: localhost:8848
        namespace: public
        group: DEFAULT_GROUP
        service: saa-a2a-server
      username: nacos  # 如果需要认证
      password: nacos  # 如果需要认证
```

或者在`pom.xml`中添加Nacos依赖：

```xml
<dependency>
    <groupId>com.alibaba.cloud</groupId>
    <artifactId>spring-cloud-starter-alibaba-nacos-discovery</artifactId>
</dependency>
```

### AgentScope A2A Server

在AgentScope A2A Server启动时，使用NacosAgentRegistry进行注册：

```java
import io.agentscope.runtime.cluster.nacos.NacosAgentRegistry;
import io.agentscope.runtime.cluster.nacos.NacosRegistryConfig;
import io.agentscope.runtime.cluster.AgentInstance;

// 创建Nacos注册配置
NacosRegistryConfig config = NacosRegistryConfig.builder()
    .serverAddr("localhost:8848")
    .namespace("public")
    .group("DEFAULT_GROUP")
    .build();

// 创建注册中心
NacosAgentRegistry registry = new NacosAgentRegistry(config);

// 创建Agent实例
AgentInstance instance = AgentInstance.builder()
    .instanceId("agentscope-a2a-server-001")
    .agentName("agentscope-a2a-server")
    .host("localhost")
    .port(10001)
    .build();

// 注册到Nacos
registry.register(instance);
```

## 客户端使用

运行客户端程序时，选择服务发现模式：

```
Select service discovery mode:
1. Direct connection (hardcoded IP)
2. Nacos service discovery
Enter your choice (1-2): 2
```

然后配置Nacos连接参数（可以使用默认值）：

```
Nacos server address [localhost:8848]: 
Nacos namespace [public]: 
Nacos group [DEFAULT_GROUP]: 
SAA service name in Nacos [saa-a2a-server]: 
AgentScope service name in Nacos [agentscope-a2a-server]: 
```

## 服务名称说明

- **saa-a2a-server**: Spring AI Alibaba A2A Server在Nacos中注册的服务名称
- **agentscope-a2a-server**: AgentScope A2A Server在Nacos中注册的服务名称

这些服务名称需要与服务端注册时使用的名称保持一致。

## 优势

使用Nacos服务发现的优势：

1. **动态服务发现**: 无需硬编码IP地址，服务实例变化时自动发现
2. **负载均衡**: 支持多个服务实例，客户端可以选择合适的实例
3. **健康检查**: Nacos自动进行健康检查，只返回健康的实例
4. **配置集中管理**: 服务配置集中管理，便于维护

## 故障排除

### 问题：无法连接到Nacos服务器

**解决方案**：
- 确认Nacos服务器已启动并运行
- 检查网络连接和防火墙设置
- 验证Nacos服务器地址配置是否正确

### 问题：找不到服务实例

**解决方案**：
- 确认服务端已成功注册到Nacos
- 检查服务名称是否匹配
- 在Nacos控制台查看服务实例状态
- 确认namespace和group配置正确

### 问题：服务实例不健康

**解决方案**：
- 检查服务端是否正常运行
- 查看Nacos控制台中的实例健康状态
- 确认心跳配置正确

## 更多信息

- [Nacos官方文档](https://nacos.io/zh-cn/docs/what-is-nacos.html)
- [AgentScope Runtime文档](../../../cookbook/zh/)
