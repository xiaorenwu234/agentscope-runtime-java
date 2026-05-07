# A2A客户端Nacos服务发现使用示例

## 概述

本次更新为A2A客户端添加了Nacos服务发现支持，允许客户端通过服务名称而非硬编码IP地址来发现和调用A2A服务。

## 主要改动

### 1. 新增文件

- **NacosAgentCardResolver.java**: 基于Nacos的AgentCardResolver实现
  - 通过Nacos服务发现获取服务实例地址
  - 自动从服务实例获取AgentCard
  - 支持Nacos认证和命名空间配置

### 2. 修改文件

- **A2aClientTest.java**: 
  - 添加了服务发现模式选择（直连模式 vs Nacos模式）
  - 新增 `createSaaAgentWithNacos()` 方法
  - 新增 `createAgentScopeAgentWithNacos()` 方法
  - 保留原有的直连模式方法

- **pom.xml**:
  - 添加Nacos客户端依赖 (`nacos-client 2.3.2`)
  - 添加SLF4J日志依赖

### 3. 文档

- **NACOS_SETUP.md**: Nacos服务发现配置指南
  - 服务端注册说明
  - 客户端使用指南
  - 故障排除

## 使用方式

### 方式一：直连模式（原有方式）

```
Select service discovery mode:
1. Direct connection (hardcoded IP)
2. Nacos service discovery
Enter your choice (1-2): 1
```

继续使用硬编码IP地址连接服务。

### 方式二：Nacos服务发现（新增）

```
Select service discovery mode:
1. Direct connection (hardcoded IP)
2. Nacos service discovery
Enter your choice (1-2): 2
```

然后配置Nacos参数：
- Nacos服务器地址（默认：localhost:8848）
- 命名空间（默认：public）
- 分组（默认：DEFAULT_GROUP）
- 服务名称

## 核心代码示例

### 创建NacosAgentCardResolver

```java
NacosAgentCardResolver cardResolver = NacosAgentCardResolver.builder()
    .serverAddr("localhost:8848")
    .namespace("public")
    .group("DEFAULT_GROUP")
    .relativeCardPath("/.well-known/agent.json")
    .build();

A2aAgent agent = A2aAgent.builder()
    .name("saa-a2a-server")  // Nacos中的服务名称
    .agentCardResolver(cardResolver)
    .build();
```

### 服务端注册到Nacos

**Spring AI Alibaba方式：**
```yaml
spring:
  cloud:
    nacos:
      discovery:
        server-addr: localhost:8848
        service: saa-a2a-server
```

**AgentScope方式：**
```java
NacosRegistryConfig config = NacosRegistryConfig.builder()
    .serverAddr("localhost:8848")
    .build();

NacosAgentRegistry registry = new NacosAgentRegistry(config);

AgentInstance instance = AgentInstance.builder()
    .instanceId("agentscope-a2a-server-001")
    .agentName("agentscope-a2a-server")
    .host("localhost")
    .port(10001)
    .build();

registry.register(instance);
```

## 优势

1. **动态发现**: 服务实例变化时无需修改客户端代码
2. **负载均衡**: 支持多实例自动选择
3. **健康检查**: 自动过滤不健康实例
4. **灵活配置**: 支持命名空间、分组等隔离机制

## 注意事项

1. 确保Nacos服务器已启动并可访问
2. 服务端必须先注册到Nacos
3. 服务名称必须与注册时一致
4. 确保网络和防火墙配置正确

## 编译和运行

```bash
# 编译
mvn clean compile

# 运行
mvn exec:java -Dexec.mainClass="io.agentscope.examples.client.A2aClientTest"
```

## 相关文件

- [NacosAgentCardResolver.java](src/main/java/io/agentscope/examples/client/NacosAgentCardResolver.java)
- [A2aClientTest.java](src/main/java/io/agentscope/examples/client/A2aClientTest.java)
- [NACOS_SETUP.md](../NACOS_SETUP.md)
