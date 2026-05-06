# 多智能体编排示例 (Multi-Agent Orchestration Example)

本示例展示了如何使用 AgentScope Runtime 实现**多智能体协同**的功能，包括服务注册发现、远程调用、负载均衡和多种编排模式。

## 功能概述

### 核心能力

| 能力 | 说明 |
|------|------|
| **服务注册发现** | 基于 Nacos 的 Agent 自动注册与发现 |
| **远程调用** | 通过 A2A 协议跨节点调用 Agent |
| **负载均衡** | 支持轮询、随机、加权随机策略 |
| **多种编排模式** | Fan-Out、Pipeline、Master-Slave、Scatter-Gather |

### 编排模式详解

```
┌─────────────────────────────────────────────────────────────────┐
│  Fan-Out (并行扇出)                                              │
│  ┌──────┐    ┌─────────┐                                        │
│  │      │───►│ Agent A │                                        │
│  │Master│───►│ Agent B │  所有 Agent 并行处理同一输入            │
│  │      │───►│ Agent C │                                        │
│  └──────┘    └─────────┘                                        │
├─────────────────────────────────────────────────────────────────┤
│  Pipeline (流水线)                                               │
│  ┌──────┐    ┌─────────┐    ┌─────────┐    ┌─────────┐         │
│  │Input │───►│ Agent A │───►│ Agent B │───►│ Agent C │───►Result│
│  └──────┘    └─────────┘    └─────────┘    └─────────┘         │
│              前一个 Agent 的输出作为下一个的输入                  │
├─────────────────────────────────────────────────────────────────┤
│  Master-Slave (主从协作)                                        │
│                   ┌──────────┐                                  │
│              ┌───►│ Slave A  │───┐                              │
│  ┌──────┐    │    └──────────┘   │    ┌──────┐                  │
│  │Master│───►├───►│ Slave B  │───┼───►│Master│───► Final Result │
│  └──────┘    │    └──────────┘   │    └──────┘                  │
│              └───►│ Slave C  │───┘    (聚合)                     │
│                   └──────────┘                                  │
│              Master 分发任务，收集结果后聚合                      │
└─────────────────────────────────────────────────────────────────┘
```

## 项目结构

```
multi_agent_orchestration_example/
├── src/main/java/io/agentscope/examples/orchestration/
│   ├── OrchestrationDemo.java       # 演示程序 - 展示所有编排模式
│   ├── MasterAgentApplication.java  # 主 Agent - 协调调度从 Agent
│   ├── WorkerAgentApplication.java  # 从 Agent 启动器
│   └── SimpleWorkerAgent.java       # 简单 Worker Agent 实现
├── pom.xml
└── README.md
```

## 前置条件

1. **Java 17+**
2. **Maven 3.6+**
3. **Nacos Server** (服务注册中心)

---

## 快速开始（详细步骤）

### 第一步：启动 Nacos 服务注册中心

> Nacos 是阿里巴巴开源的服务发现和配置管理平台，本示例用它来实现 Agent 的自动注册与发现。

#### 方式一：使用 Docker 启动（推荐）

```bash
# 拉取并启动 Nacos 容器
docker run -d --name nacos \
  -e MODE=standalone \
  -p 8848:8848 \
  -p 9848:9848 \
  nacos/nacos-server:v2.4.0

# 查看启动日志（等待 "Nacos started successfully" 出现）
docker logs -f nacos
```

#### 方式二：本地安装启动

```bash
# 1. 下载 Nacos
wget https://github.com/alibaba/nacos/releases/download/2.3.2/nacos-server-2.4.0.zip
unzip nacos-server-2.4.0.zip
cd nacos/bin

# 2. 单机模式启动
# macOS/Linux:
./startup.sh -m standalone

# Windows:
startup.cmd -m standalone
```

#### 验证 Nacos 是否启动成功

```bash
# 方式一：访问 Web 控制台
# 打开浏览器访问: http://localhost:8848/nacos
# 用户名: nacos
# 密码: nacos

# 方式二：命令行检查
curl -s http://localhost:8848/nacos/v1/ns/service/list | head -20
# 如果返回 JSON 数据，说明 Nacos 启动成功
```

### 第二步：编译项目

```bash
# 进入项目根目录
cd agentscope-runtime-java

# 编译整个项目（跳过测试以加快速度）
mvn clean install -DskipTests

# 或者只编译本示例模块
mvn clean compile -pl examples/multi_agent_orchestration_example -am
```

### 第三步：启动 Worker Agents

> 本示例需要启动 3 个 Worker Agent，它们会自动注册到 Nacos。
> 请打开 **3 个终端窗口**，分别执行以下命令。

**终端 1 - 启动 Translator Agent（翻译智能体）:**

```bash
cd examples/multi_agent_orchestration_example

mvn exec:java -Dexec.mainClass="io.agentscope.examples.orchestration.WorkerAgentApplication" \
  -Dserver.port=8091 \
  -Dexec.args="--port=8091 --agent-name=translator --agent-type=translator"
```

启动成功后，你应该看到类似输出：
```
========================================
Starting Worker Agent
  Name: translator
  Type: translator
  Port: 8091
  Nacos: localhost:8848
========================================
...
[ClusterService] Registered to cluster: translator at 192.168.x.x:8091
```

**终端 2 - 启动 Analyzer Agent（分析智能体）:**

```bash
cd examples/multi_agent_orchestration_example

mvn exec:java -Dexec.mainClass="io.agentscope.examples.orchestration.WorkerAgentApplication" \
  -Dserver.port=8092 \
  -Dexec.args="--port=8092 --agent-name=analyzer --agent-type=analyzer"
```

**终端 3 - 启动 Summarizer Agent（总结智能体）:**

```bash
cd examples/multi_agent_orchestration_example

mvn exec:java -Dexec.mainClass="io.agentscope.examples.orchestration.WorkerAgentApplication" \
  -Dserver.port=8093 \
  -Dexec.args="--port=8093 --agent-name=summarizer --agent-type=summarizer"
```

#### 验证 Agent 注册成功

打开 Nacos 控制台 http://localhost:8848/nacos，进入 **服务管理 > 服务列表**，你应该看到 3 个服务：
- `translator`
- `analyzer`  
- `summarizer`

或者使用命令行验证：
```bash
# 查看已注册的服务
curl -s "http://localhost:8848/nacos/v1/ns/service/list?pageNo=1&pageSize=10" | jq
```

### 第四步：运行演示程序

**打开第 4 个终端窗口**，选择以下任一方式运行：

#### 方式一：运行完整演示（推荐新手）

```bash
cd examples/multi_agent_orchestration_example

mvn exec:java -Dexec.mainClass="io.agentscope.examples.orchestration.OrchestrationDemo"
```

这会依次演示：
1. 服务发现 - 从 Nacos 发现所有已注册的 Agent
2. Fan-Out 模式 - 并行调用所有 Agent
3. Pipeline 模式 - 串行流水线处理
4. Master-Slave 模式 - 主从协作模式

#### 方式二：启动 Master Agent 并手动调用

```bash
# 启动 Master Agent
mvn exec:java -Dexec.mainClass="io.agentscope.examples.orchestration.MasterAgentApplication" \
  -Dserver.port=8090 \
  -Dnacos.logging.default.config.enabled=false \
  -Dexec.args="--port=8090"
```

然后使用 curl 发送请求：

```bash
# 发送测试消息
curl -X POST http://localhost:8090/a2a \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "method": "message/send",
    "params": {
      "message": {
        "role": "user",
        "content": [{"type": "text", "text": "Hello, please process this message"}]
      }
    },
    "id": "1"
  }'
```

---

## 一键启动脚本（可选）

为了方便使用，你可以创建一个启动脚本：

```bash
#!/bin/bash
# start-demo.sh

PROJECT_DIR=$(cd "$(dirname "$0")/.." && pwd)
EXAMPLE_DIR="$PROJECT_DIR/examples/multi_agent_orchestration_example"

echo "Starting Multi-Agent Orchestration Demo..."
echo "Project: $EXAMPLE_DIR"
echo ""

# 检查 Nacos
if ! curl -s http://localhost:8848/nacos > /dev/null 2>&1; then
    echo "ERROR: Nacos is not running on localhost:8848"
    echo "Please start Nacos first: docker run -d --name nacos -e MODE=standalone -p 8848:8848 -p 9848:9848 nacos/nacos-server:v2.4.0"
    exit 1
fi
echo "✓ Nacos is running"

cd "$EXAMPLE_DIR"

# 启动 3 个 Worker Agent（后台运行）
echo "Starting Worker Agents..."
mvn exec:java -Dexec.mainClass="io.agentscope.examples.orchestration.WorkerAgentApplication" \
  -Dexec.args="--port=8091 --agent-name=translator --agent-type=translator" &
PID1=$!

mvn exec:java -Dexec.mainClass="io.agentscope.examples.orchestration.WorkerAgentApplication" \
  -Dexec.args="--port=8092 --agent-name=analyzer --agent-type=analyzer" &
PID2=$!

mvn exec:java -Dexec.mainClass="io.agentscope.examples.orchestration.WorkerAgentApplication" \
  -Dexec.args="--port=8093 --agent-name=summarizer --agent-type=summarizer" &
PID3=$!

echo "Worker Agents started (PIDs: $PID1, $PID2, $PID3)"
echo "Waiting 10 seconds for agents to register..."
sleep 10

# 运行演示
echo "Running OrchestrationDemo..."
mvn exec:java -Dexec.mainClass="io.agentscope.examples.orchestration.OrchestrationDemo"

# 清理
echo "Stopping Worker Agents..."
kill $PID1 $PID2 $PID3 2>/dev/null
echo "Done!"
```

---

## 演示输出示例

运行 `OrchestrationDemo` 后，你会看到类似以下输出：

```
===========================================
Multi-Agent Orchestration Demo
===========================================

--- Demo 1: Service Discovery ---

  [translator] Found: http://192.168.1.100:8091
  [analyzer] Found: http://192.168.1.100:8092  
  [summarizer] Found: http://192.168.1.100:8093

--- Demo 2: Fan-Out Pattern ---

Input: Hello, this is a test message for multi-agent processing.
Sending to all workers in parallel...

Results:
  [translator]: 你好，这是一条用于多智能体处理的测试消息。
  [analyzer]: Analysis: The input is a greeting message...
  [summarizer]: Summary: Test message for multi-agent system.

--- Demo 3: Pipeline Pattern ---

Input: Process this text step by step.
Pipeline: translator -> analyzer -> summarizer

Final Result: [Summarized translated and analyzed content]

--- Demo 4: Master-Slave Pattern ---

Input: Complex task that requires coordination.
Master: master, Slaves: translator, analyzer, summarizer

Success: true
Final Result: === Master Agent Orchestration Results ===...

===========================================
Demo completed successfully!
===========================================
```

---

## 配置说明

### 命令行参数

| 参数 | 说明 | 默认值 |
|------|------|--------|
| `--port` | Agent 服务端口 | 8091 |
| `--agent-name` | Agent 名称（用于服务发现） | worker |
| `--agent-type` | Agent 类型 | default |
| `--nacos-addr` | Nacos 服务地址 | localhost:8848 |

### 在代码中配置集群

推荐使用 `AgentApp.cluster()` 方法配置集群：

```java
// 创建 Agent 应用
AgentApp app = new AgentApp(myAgent);

// 简单配置：启用集群 + Nacos 地址
app.cluster(true, "localhost:8848");

// 完整配置：包含命名空间、分组、认证信息
app.cluster(
    true,                    // 启用集群
    "localhost:8848",        // Nacos 服务地址
    "public",                // 命名空间
    "DEFAULT_GROUP",         // 分组
    "nacos",                 // 用户名（可选）
    "nacos"                  // 密码（可选）
);

// 启动应用
app.run(8091);
```

### 配置文件方式（application.properties）

你也可以在 `src/main/resources/application.properties` 中配置：

```properties
# 启用集群模式
agentscope.cluster.enabled=true

# Nacos 配置
agentscope.cluster.nacos.server-addr=localhost:8848
agentscope.cluster.nacos.namespace=public
agentscope.cluster.nacos.group=DEFAULT_GROUP
agentscope.cluster.nacos.username=nacos
agentscope.cluster.nacos.password=nacos

# 服务端口
server.port=8091
```

---

## 核心 API

### AgentApp - 应用入口

```java
import io.agentscope.runtime.app.AgentApp;

// 创建自定义 Agent
MyWorkerAgent myAgent = new MyWorkerAgent("my-agent", "processor");

// 创建应用并配置
AgentApp app = new AgentApp(myAgent);

// 配置集群（注册到 Nacos）
app.cluster(true, "localhost:8848");

// 配置 CORS（可选）
app.cors(registry -> registry
    .addMapping("/**")
    .allowedOrigins("*")
    .allowedMethods("GET", "POST"));

// 启动应用
app.run(8091);
```

### AgentOrchestrator - 编排器

```java
import io.agentscope.runtime.orchestration.AgentOrchestrator;
import io.agentscope.runtime.rpc.DefaultAgentClient;
import io.agentscope.runtime.cluster.nacos.NacosAgentRegistry;

// 创建编排器
NacosRegistryConfig config = NacosRegistryConfig.builder()
    .serverAddr("localhost:8848")
    .namespace("public")
    .group("DEFAULT_GROUP")
    .build();
AgentRegistry registry = new NacosAgentRegistry(config);
AgentClient client = new DefaultAgentClient(registry);
AgentOrchestrator orchestrator = new AgentOrchestrator(client);

// Fan-Out: 并行调用多个 Agent
Map<String, String> results = orchestrator.fanOut(
    List.of("translator", "analyzer", "summarizer"), 
    "Hello, process this message"
);

// Pipeline: 串行流水线调用
String result = orchestrator.pipeline(
    List.of("translator", "analyzer", "summarizer"), 
    "Input text"
);

// Master-Slave: 主从调度模式
MasterSlaveResult result = orchestrator.masterSlave(
    "master", 
    List.of("translator", "analyzer", "summarizer"), 
    "Complex task"
);

// Scatter-Gather: 散布收集模式
String gathered = orchestrator.scatterGather(
    List.of("agent1", "agent2"), 
    "query",
    GatherStrategy.ALL_SUCCESS
);
```

### AgentRegistry - 服务注册与发现

```java
import io.agentscope.runtime.cluster.AgentRegistry;
import io.agentscope.runtime.cluster.AgentInstance;
import io.agentscope.runtime.cluster.nacos.NacosAgentRegistry;

// 创建 Nacos 注册中心客户端
NacosRegistryConfig config = NacosRegistryConfig.builder()
    .serverAddr("localhost:8848")
    .build();
AgentRegistry registry = new NacosAgentRegistry(config);

// 手动注册 Agent（通常由 AgentApp 自动完成）
AgentInstance instance = AgentInstance.builder()
    .instanceId("my-agent-1")
    .agentName("translator")
    .host("192.168.1.100")
    .port(8091)
    .build();
registry.register(instance);

// 发现 Agent
List<AgentInstance> instances = registry.discover("translator");
for (AgentInstance inst : instances) {
    System.out.printf("Found: %s at %s:%d%n", 
        inst.getAgentName(), inst.getHost(), inst.getPort());
}

// 订阅 Agent 变更事件
registry.subscribe("translator", event -> {
    System.out.println("Agent instances changed: " + event);
});
```

---

## 扩展开发

### 自定义 Worker Agent

```java
import io.agentscope.runtime.adapters.AgentHandler;
import io.agentscope.runtime.protocol.a2a.AgentRequest;
import reactor.core.publisher.Flux;

public class MyWorkerAgent implements AgentHandler {
    
    private final String name;
    private final String agentType;
    
    public MyWorkerAgent(String name, String agentType) {
        this.name = name;
        this.agentType = agentType;
    }
    
    @Override
    public String getName() {
        return name;
    }
    
    @Override
    public String getDescription() {
        return "My custom " + agentType + " agent";
    }
    
    @Override
    public String getFrameworkType() {
        return "CustomAgent";
    }
    
    @Override
    public Flux<?> streamQuery(AgentRequest request, Object messages) {
        // 实现你的业务逻辑
        String input = extractInput(messages);
        String result = processInput(input);
        return Flux.just(new TextContent(result));
    }
    
    private String extractInput(Object messages) {
        // 从消息中提取输入文本
        // ...
    }
    
    private String processInput(String input) {
        // 处理输入并返回结果
        return "[" + name + "] Processed: " + input;
    }
}
```

### 自定义负载均衡策略

```java
import io.agentscope.runtime.rpc.loadbalance.LoadBalancer;
import io.agentscope.runtime.cluster.AgentInstance;

public class WeightedRandomLoadBalancer implements LoadBalancer {
    
    @Override
    public AgentInstance select(List<AgentInstance> instances) {
        // 按权重随机选择
        int totalWeight = instances.stream()
            .mapToInt(AgentInstance::getWeight)
            .sum();
        
        int random = ThreadLocalRandom.current().nextInt(totalWeight);
        int current = 0;
        
        for (AgentInstance instance : instances) {
            current += instance.getWeight();
            if (random < current) {
                return instance;
            }
        }
        return instances.get(0);
    }
    
    @Override
    public String getName() {
        return "weighted-random";
    }
}
```

---

## 常见问题排查

### Q1: 连接 Nacos 失败？

**症状**：启动时报错 `Failed to register to cluster` 或 `Connection refused`

**解决方案**：
```bash
# 1. 检查 Nacos 是否运行
curl http://localhost:8848/nacos/v1/ns/service/list

# 2. 检查 Nacos 容器状态（如使用 Docker）
docker ps | grep nacos
docker logs nacos

# 3. 检查端口是否被占用
lsof -i :8848
```

### Q2: Agent 启动成功但未注册到 Nacos？

**症状**：日志显示 `AgentApp started successfully` 但 Nacos 控制台看不到服务

**解决方案**：
```java
// 确保使用了 cluster() 方法配置集群
AgentApp app = new AgentApp(myAgent);
app.cluster(true, "localhost:8848");  // ← 这行不能少！
app.run(port);
```

查看日志中是否有：
```
[ClusterService] Registered to cluster: xxx at x.x.x.x:8091
```

### Q3: Agent 注册成功但无法被发现？

**检查步骤**：
```bash
# 1. 确认服务在 Nacos 中存在
curl "http://localhost:8848/nacos/v1/ns/instance/list?serviceName=translator&groupName=DEFAULT_GROUP"

# 2. 检查服务健康状态
curl "http://localhost:8848/nacos/v1/ns/instance/list?serviceName=translator&healthyOnly=true"
```

### Q4: 调用 Agent 超时？

**解决方案**：
```bash
# 1. 检查目标 Agent 是否正常运行
curl http://localhost:8091/health

# 2. 测试 Agent 是否响应
curl -X POST http://localhost:8091/a2a \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","method":"message/send","params":{},"id":"1"}'
```

### Q5: 如何停止所有 Agent？

```bash
# 方式一：找到进程并终止
ps aux | grep WorkerAgentApplication | grep -v grep | awk '{print $2}' | xargs kill

# 方式二：按端口终止
lsof -ti :8091 | xargs kill
lsof -ti :8092 | xargs kill
lsof -ti :8093 | xargs kill
```

---

## 架构说明

```
┌─────────────────────────────────────────────────────────────────┐
│                        Nacos Server                             │
│                  (服务注册与发现中心)                             │
│                    localhost:8848                               │
└──────────┬──────────────────┬──────────────────┬────────────────┘
           │ 注册              │ 注册              │ 注册
           ▼                  ▼                  ▼
    ┌─────────────┐    ┌─────────────┐    ┌─────────────┐
    │ Translator  │    │  Analyzer   │    │ Summarizer  │
    │   Agent     │    │   Agent     │    │   Agent     │
    │  :8091      │    │  :8092      │    │  :8093      │
    └─────────────┘    └─────────────┘    └─────────────┘
           ▲                  ▲                  ▲
           │                  │                  │
           └────────┬─────────┴──────────────────┘
                    │ A2A 协议调用
           ┌────────┴────────┐
           │   Master Agent  │
           │   / Demo App    │
           │     :8090       │
           └─────────────────┘
```

---

## 许可证

Apache License 2.0
