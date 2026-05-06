# A2A Cross-Framework Communication Example

本示例展示了如何使用 A2A (Agent-to-Agent) 协议实现 **Spring AI Alibaba (SAA)** 和 **AgentScope** 两个不同框架之间的跨框架通信。

## 📋 项目结构

```
a2a_cross_framework_example/
├── saa-a2a-server/              # Spring AI Alibaba A2A Server (端口 8081)
├── a2a-client-test/             # A2A 客户端测试应用
├── pom.xml                      # 父 POM
└── README.md                    # 本文件
```

> **注意**: AgentScope A2A Server 模块需要从主项目 `agentscope-runtime-java` 中获取依赖,因此暂未包含在此示例中。客户端可以同时测试 SAA Server 和 AgentScope Server(如果你单独启动了 AgentScope Server)。

## 🎯 示例目标

1. **启动 SAA A2A Server** - 使用 Spring AI Alibaba 框架暴露 A2A 服务
2. **启动 AgentScope A2A Server** - 使用 AgentScope 框架暴露 A2A 服务
3. **跨框架通信** - 使用统一的 A2A 客户端与两个不同框架的服务器进行通信

## 🚀 快速开始

### 前置条件

- Java 17+
- Maven 3.6+
- (可选) 配置 AI 模型 API Key (如 DashScope)

### 步骤 1: 编译项目

```bash
cd a2a_cross_framework_example
mvn clean install
```

### 步骤 2: 启动 Spring AI Alibaba A2A Server

```bash
cd saa-a2a-server
mvn spring-boot:run
```

服务器将在 `http://localhost:8081` 启动:
- Agent Card: `http://localhost:8081/.well-known/agent.json`
- A2A Endpoint: `http://localhost:8081/a2a`

### 步骤 3: (可选)启动 AgentScope A2A Server

如果你想测试与 AgentScope Server 的通信,需要先构建主项目:

```bash
# 在主项目目录中
cd c:\Users\xht\Desktop\agentscope-runtime-java
mvn clean install -DskipTests -pl agentscope-extensions-a2a/agentscope-extensions-a2a-server -am

# 然后参考 agentscope-a2a-server 目录中的示例代码启动服务器
```

服务器将在 `http://localhost:8082` 启动:
- Agent Card: `http://localhost:8082/.well-known/agent.json`
- A2A Endpoint: `http://localhost:8082/a2a`

### 步骤 4: 运行客户端测试

打开新的终端:

```bash
cd a2a-client-test
mvn exec:java
```

客户端将提供交互式界面,你可以选择:
1. 与 SAA Server 通信
2. 与 AgentScope Server 通信
3. 同时向两个服务器发送相同消息,比较响应
4. 退出

## 🔍 核心概念

### A2A 协议

A2A (Agent-to-Agent) 是一个开放的 Agent 互操作协议,支持:
- **AgentCard 发现**: 自动发现 Agent 的能力和功能
- **JSON-RPC 传输**: 标准化的通信协议
- **流式响应**: 支持实时流式输出
- **任务管理**: 支持异步任务、取消等操作

### AgentCard

AgentCard 是 Agent 的"名片",包含:
- Agent 名称和描述
- 支持的技能和能力
- 访问端点和协议
- 认证和安全配置

客户端通过访问 `/.well-known/agent.json` 获取 AgentCard,自动了解如何与该 Agent 通信。

### A2aAgent

`A2aAgent` 是 AgentScope 提供的 A2A 客户端实现:
- 自动从服务器获取 AgentCard
- 将本地 Msg 转换为 A2A Message
- 处理流式响应和事件
- 支持任务管理和中断

## 💡 代码示例

### 创建 A2A 客户端

```java
// 使用 WellKnownAgentCardResolver 自动发现 Agent
WellKnownAgentCardResolver cardResolver = WellKnownAgentCardResolver.builder()
        .baseUrl("http://localhost:8081")
        .agentCardPath("/.well-known/agent.json")
        .build();

A2aAgent agent = A2aAgent.builder()
        .name("remote-assistant")
        .agentCardResolver(cardResolver)
        .build();
```

### 调用远程 Agent

```java
Msg userMsg = Msg.builder()
        .role(MsgRole.USER)
        .content(TextBlock.builder().text("Hello!").build())
        .build();

// 流式调用
agent.stream(userMsg)
        .filter(event -> event.getMessage() != null)
        .map(event -> event.getMessage().getTextContent())
        .doOnNext(text -> System.out.print(text))
        .then()
        .block();
```

## 🔧 配置说明

### SAA A2A Server 配置

在 `saa-a2a-server/src/main/resources/application.yml` 中:

```yaml
spring.ai.alibaba.a2a.server.type=JSONRPC
spring.ai.alibaba.a2a.server.address=localhost
spring.ai.alibaba.a2a.server.port=8081
spring.ai.alibaba.a2a.server.card.name=SAA Assistant
spring.ai.alibaba.a2a.server.card.description=A helpful assistant powered by Spring AI Alibaba
```

### AgentScope A2A Server 配置

在 `AgentScopeA2aServerApplication.java` 中:

```java
AgentScopeA2aServer server = AgentScopeA2aServer.builder(agentBuilder)
        .deploymentProperties(new DeploymentProperties.Builder()
                .host("localhost")
                .port(8082)
                .path("/a2a")
                .build())
        .build();
```

## 🧪 测试场景

### 场景 1: 单服务器通信

测试客户端与单个 A2A 服务器的基本通信流程。

### 场景 2: 跨框架对比

同时向 SAA 和 AgentScope 服务器发送相同消息,观察:
- 响应速度
- 输出格式
- 任务处理方式

### 场景 3: 流式响应

观察 A2A 协议如何支持实时流式输出。

## 📝 注意事项

1. **模型配置**: 示例中的 Agent 需要配置实际的 AI 模型(如 DashScope)才能正常工作
2. **端口占用**: 确保 8081 和 8082 端口未被占用
3. **依赖版本**: 确保使用兼容的 A2A SDK 版本
4. **网络访问**: 客户端需要能够访问服务器的 AgentCard 端点和 A2A 端点

## 🔗 相关资源

- [A2A Protocol Specification](https://a2a-protocol.org)
- [AgentScope Documentation](https://agentscope.io)
- [Spring AI Alibaba Documentation](https://java2ai.com)

## ❓ 常见问题

**Q: 为什么需要两个不同的服务器?**  
A: 本示例的目的是展示 A2A 协议的跨框架互操作性。通过两个不同框架实现的服务器,证明 A2A 协议可以实现框架无关的 Agent 通信。

**Q: 可以只启动一个服务器吗?**  
A: 可以。客户端测试代码会处理服务器不可用的情况,你可以只启动其中一个服务器进行测试。

**Q: 如何添加自定义工具?**  
A: 在构建 Agent 时添加工具配置。对于 SAA,使用 `spring-ai-alibaba-agent-framework` 的工具注册机制;对于 AgentScope,使用 `ReActAgent.builder().tools(...)` 方法。

## 📄 许可证

Apache License 2.0
