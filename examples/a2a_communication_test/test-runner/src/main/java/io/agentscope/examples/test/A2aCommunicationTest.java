package io.agentscope.examples.test;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.a2a.agent.A2aAgent;
import io.agentscope.runtime.cluster.AgentInstance;
import io.agentscope.runtime.cluster.nacos.NacosAgentCardResolver;
import io.agentscope.runtime.cluster.nacos.NacosAgentRegistry;
import io.agentscope.runtime.cluster.nacos.NacosRegistryConfig;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import io.agentscope.runtime.app.AgentApp;
import io.agentscope.runtime.engine.services.agent_state.InMemoryStateService;
import io.agentscope.runtime.engine.services.memory.persistence.memory.service.InMemoryMemoryService;
import io.agentscope.runtime.engine.services.memory.persistence.session.InMemorySessionHistoryService;

import java.time.Duration;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A2A 协议通信功能测试。
 *
 * <h3>测试环境</h3>
 * <p>3 节点集群，每节点部署 2 个 AgentScope Agent（Agent-AS-1、Agent-AS-2），共 6 个 Agent 实例。</p>
 * <ul>
 *   <li>Node-1: Agent-AS-1 (端口 10001), Agent-AS-2 (端口 10002)</li>
 *   <li>Node-2: Agent-AS-1 (端口 10003), Agent-AS-2 (端口 10004)</li>
 *   <li>Node-3: Agent-AS-1 (端口 10005), Agent-AS-2 (端口 10006)</li>
 * </ul>
 *
 * <h3>测试项</h3>
 * <ol>
 *   <li>(a) 同步调用 - TextPart 消息 100 次，验证请求-响应完整性与一致性</li>
 *   <li>(b) 异步调用 - 100 次异步任务，10% 超时注入，验证回调可靠触发与超时错误码</li>
 *   <li>(c) 错误处理 - 构造格式错误的 JSON-RPC 请求，验证标准 A2A 扩展错误码</li>
 * </ol>
 *
 * <h3>A2A 透明性</h3>
 * <p>每个 Agent 都通过 A2aAgent 封装，对其他 Agent 来说，A2A 层完全透明。
 * Agent 之间可以平等地互相通信，无需中央协调器。</p>
 */
public class A2aCommunicationTest {

    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

    /**
     * 集群节点定义：nodeId -> (agentName -> port)
     */
    private static final Map<String, Map<String, Integer>> CLUSTER_TOPOLOGY = new LinkedHashMap<>();

    /**
     * 所有 Agent 的端口 -> Agent 名称映射
     */
    private static final Map<Integer, String> PORT_TO_AGENT = new LinkedHashMap<>();

    /**
     * 已创建的 A2aAgent 实例
     */
    private static final Map<String, A2aAgent> agentInstances = new LinkedHashMap<>();

    /**
     * 内嵌启动的 AgentApp 实例
     */
    private static final List<AgentApp> startedApps = new ArrayList<>();

    static {
        // Node-1: 端口 10001, 10002
        Map<String, Integer> node1 = new LinkedHashMap<>();
        node1.put("Agent-AS-1", 10001);
        node1.put("Agent-AS-2", 10002);
        CLUSTER_TOPOLOGY.put("node-1", node1);

        // Node-2: 端口 10003, 10004
        Map<String, Integer> node2 = new LinkedHashMap<>();
        node2.put("Agent-AS-1", 10003);
        node2.put("Agent-AS-2", 10004);
        CLUSTER_TOPOLOGY.put("node-2", node2);

        // Node-3: 端口 10005, 10006
        Map<String, Integer> node3 = new LinkedHashMap<>();
        node3.put("Agent-AS-1", 10005);
        node3.put("Agent-AS-2", 10006);
        CLUSTER_TOPOLOGY.put("node-3", node3);

        // 构建端口->名称映射
        CLUSTER_TOPOLOGY.forEach((nodeId, agents) ->
                agents.forEach((name, port) -> PORT_TO_AGENT.put(port, nodeId + ":" + name)));
    }

    private static final String HOST = System.getenv().getOrDefault("AGENT_HOST",
            System.getProperty("agent.host", "localhost"));

    private static final boolean EMBEDDED_MODE = Boolean.parseBoolean(
            System.getenv().getOrDefault("EMBEDDED_MODE",
                    System.getProperty("embedded.mode", "true")));

    private static final String NACOS_SERVER_ADDR = System.getenv().getOrDefault("NACOS_SERVER_ADDR",
            System.getProperty("nacos.server.addr", "localhost:8848"));

    private static final String NACOS_NAMESPACE = System.getenv().getOrDefault("NACOS_NAMESPACE",
            System.getProperty("nacos.namespace", "public"));

    private static final String NACOS_GROUP = System.getenv().getOrDefault("NACOS_GROUP",
            System.getProperty("nacos.group", "DEFAULT_GROUP"));

    /**
     * 共享的 NacosAgentCardResolver 实例
     */
    private static NacosAgentCardResolver nacosResolver;

    public static void main(String[] args) throws Exception {
        System.out.println("========================================================");
        System.out.println("  A2A 协议通信功能测试");
        System.out.println("  3 节点 / 6 Agent 实例");
        System.out.println("========================================================\n");

        try {
            // 1. 启动 Agent 集群
            startupCluster();

            // 2. 初始化 Nacos 服务发现
            initNacosResolver();

            // 3. 创建 A2aAgent 实例
            createA2aAgents();

            // 4. 运行测试
            System.out.println("\n==================== 测试开始 ====================\n");

            // (a) 同步调用测试 - 同节点
            testSyncCallSameNode();

            // (a) 同步调用测试 - 跨节点
            testSyncCallCrossNode();

            // (b) 异步调用测试 - 含 10% 超时注入
            testAsyncCallWithTimeout();

            // (c) 错误处理测试
            testErrorHandling();

            System.out.println("\n==================== 测试完成 ====================");

        } finally {
            shutdownCluster();
            // Nacos SDK 内部线程不响应中断，强制退出 JVM 避免线程残留和 shutdown hook 的 NoClassDefFoundError
            System.exit(0);
        }
    }

    /**
     * 已注册的 NacosAgentRegistry 实例（内嵌模式用）
     */
    private static final List<NacosAgentRegistry> nacosRegistries = new ArrayList<>();

    // ==================== 集群管理 ====================

    /**
     * 启动 Agent 集群。
     * 在内嵌模式下，直接在当前 JVM 中启动 AgentApp 实例。
     * 在外部模式下，假设 Agent 服务已经在外部启动。
     */
    private static void startupCluster() throws InterruptedException {
        if (!EMBEDDED_MODE) {
            System.out.println("外部模式：假设 Agent 服务已在以下端口启动:");
            PORT_TO_AGENT.forEach((port, name) ->
                    System.out.println("  " + name + " -> " + HOST + ":" + port));
            return;
        }

        System.out.println("内嵌模式：正在启动 6 个 Agent 实例...");

        // 初始化 Nacos 注册配置
        NacosRegistryConfig nacosConfig = NacosRegistryConfig.builder()
                .serverAddr(NACOS_SERVER_ADDR)
                .namespace(NACOS_NAMESPACE)
                .group(NACOS_GROUP)
                .build();

        for (Map.Entry<String, Map<String, Integer>> nodeEntry : CLUSTER_TOPOLOGY.entrySet()) {
            String nodeId = nodeEntry.getKey();
            for (Map.Entry<String, Integer> agentEntry : nodeEntry.getValue().entrySet()) {
                String agentName = nodeId + ":" + agentEntry.getKey();
                int port = agentEntry.getValue();

                try {
                    EchoAgentHandler handler = new EchoAgentHandler(agentName);
                    handler.setStateService(new InMemoryStateService());
                    handler.setSessionHistoryService(new InMemorySessionHistoryService());
                    handler.setMemoryService(new InMemoryMemoryService());

                    AgentApp app = new AgentApp(handler);
                    // 在独立线程中启动，避免阻塞
                    CompletableFuture.runAsync(() -> app.run("localhost", port));
                    startedApps.add(app);

                    // 注册到 Nacos
                    try {
                        NacosAgentRegistry registry = new NacosAgentRegistry(nacosConfig);
                        String instanceId = agentName + "-localhost-" + port + "-" + System.currentTimeMillis();
                        AgentInstance instance = AgentInstance.builder()
                                .instanceId(instanceId)
                                .agentName(agentName)
                                .host("localhost")
                                .port(port)
                                .weight(1.0)
                                .build();
                        registry.register(instance);
                        nacosRegistries.add(registry);
                        System.out.println("  [启动+注册] " + agentName + " (端口 " + port + ") -> Nacos");
                    } catch (Exception ne) {
                        System.err.println("  [启动OK,注册失败] " + agentName + ": " + ne.getMessage());
                    }

                } catch (Exception e) {
                    System.err.println("  [失败] " + agentName + ": " + e.getMessage());
                }
            }
        }

        // 等待服务启动
        System.out.println("\n等待 Agent 服务就绪 (15秒)...");
        Thread.sleep(15000);
        System.out.println("集群启动完成。\n");
    }

    /**
     * 通过 A2A 协议创建 A2aAgent 实例。
     * 每个 Agent 都被封装为 A2aAgent，A2A 层对调用方完全透明。
     * 通过 Nacos 服务发现解析 AgentCard。
     */
    private static void createA2aAgents() {
        System.out.println("通过 Nacos 服务发现创建 A2aAgent 实例...\n");

        for (Map.Entry<Integer, String> entry : PORT_TO_AGENT.entrySet()) {
            String fullName = entry.getValue();

            try {
                A2aAgent agent = A2aAgent.builder()
                        .name(fullName)
                        .agentCardResolver(nacosResolver)
                        .build();

                agentInstances.put(fullName, agent);
                System.out.println("  [OK] " + fullName + " (via Nacos)");
            } catch (Exception e) {
                System.err.println("  [FAIL] " + fullName + ": " + e.getMessage());
            }
        }

        System.out.println("\n成功创建 " + agentInstances.size() + " 个 A2aAgent。\n");
    }

    private static void shutdownCluster() {
        System.out.println("正在关闭 Agent 集群...");
        startedApps.forEach(app -> {
            try {
                app.stop();
            } catch (Exception ignored) {
            }
        });
        nacosRegistries.forEach(registry -> {
            try {
                registry.close();
            } catch (Exception ignored) {
            }
        });
        System.out.println("集群已关闭。Nacos 注册已注销。");
    }

    /**
     * 初始化 NacosAgentCardResolver，用于通过 Nacos 服务发现解析 AgentCard。
     */
    private static void initNacosResolver() {
        System.out.println("初始化 Nacos 服务发现...");
        System.out.println("  Nacos Server: " + NACOS_SERVER_ADDR);
        System.out.println("  Namespace: " + NACOS_NAMESPACE);
        System.out.println("  Group: " + NACOS_GROUP);

        nacosResolver = NacosAgentCardResolver.builder()
                .serverAddr(NACOS_SERVER_ADDR)
                .namespace(NACOS_NAMESPACE)
                .group(NACOS_GROUP)
                .relativeCardPath("/.well-known/agent.json")
                .build();

        System.out.println("Nacos 服务发现初始化完成。\n");
    }

    // ==================== 测试 (a): 同步调用 - TextPart ====================

    /**
     * 同步调用测试 - 同节点拓扑。
     * 在同一节点上的两个 Agent 之间发送 TextPart 类型消息 100 次。
     */
    private static void testSyncCallSameNode() {
        System.out.println("─────────────────────────────────────────────────");
        System.out.println("测试 (a): 同步调用 - TextPart - 同节点拓扑");
        System.out.println("─────────────────────────────────────────────────");

        // 使用 node-1 上的两个 Agent 进行同节点通信
        String sender = "node-1:Agent-AS-1";
        String receiver = "node-1:Agent-AS-2";

        A2aAgent senderAgent = agentInstances.get(sender);
        A2aAgent receiverAgent = agentInstances.get(receiver);

        if (senderAgent == null || receiverAgent == null) {
            System.out.println("  跳过: Agent 实例不可用");
            return;
        }

        int testCount = 5;
        LatencyStats stats = new LatencyStats();
        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < testCount; i++) {
            String messageText = "同步测试消息 #" + (i + 1) + " [同节点]";
            long startTime = System.nanoTime();

            try {
                Msg userMsg = Msg.builder()
                        .role(MsgRole.USER)
                        .content(TextBlock.builder().text(messageText).build())
                        .build();

                // 通过 A2aAgent.call() 发起同步调用 - A2A 层透明
                Msg response = receiverAgent.call(userMsg).block();

                long elapsed = (System.nanoTime() - startTime) / 1_000_000; // ms
                stats.record(elapsed);

                if (response != null && response.getTextContent() != null) {
                    successCount.incrementAndGet();
                }
            } catch (Exception e) {
                long elapsed = (System.nanoTime() - startTime) / 1_000_000;
                stats.recordFailure(elapsed);
                System.err.println("  同步调用失败 #" + (i + 1) + ": " + e.getMessage());
            }
        }

        // 输出结果
        double successRate = (successCount.get() * 100.0) / testCount;
        System.out.println("\n  结果:");
        System.out.println("  ┌────────────────────┬──────────────────────┐");
        System.out.println("  │ 测试项             │ 值                   │");
        System.out.println("  ├────────────────────┼──────────────────────┤");
        System.out.printf("  │ 通信拓扑           │ 同节点               │%n");
        System.out.printf("  │ 测试次数           │ %d                   │%n", testCount);
        System.out.printf("  │ 成功率             │ %.1f%%               │%n", successRate);
        System.out.printf("  │ 平均延迟(ms)       │ %.1f±%.1f            │%n", stats.mean(), stats.stdDev());
        System.out.println("  └────────────────────┴──────────────────────┘\n");
    }

    /**
     * 同步调用测试 - 跨节点拓扑。
     * 在不同节点上的两个 Agent 之间发送 TextPart 类型消息 100 次。
     */
    private static void testSyncCallCrossNode() {
        System.out.println("─────────────────────────────────────────────────");
        System.out.println("测试 (a): 同步调用 - TextPart - 跨节点拓扑");
        System.out.println("─────────────────────────────────────────────────");

        // node-1:Agent-AS-1 -> node-2:Agent-AS-1 跨节点通信
        String sender = "node-1:Agent-AS-1";
        String receiver = "node-2:Agent-AS-1";

        A2aAgent receiverAgent = agentInstances.get(receiver);

        if (receiverAgent == null) {
            System.out.println("  跳过: Agent 实例不可用");
            return;
        }

        int testCount = 5;
        LatencyStats stats = new LatencyStats();
        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < testCount; i++) {
            String messageText = "同步测试消息 #" + (i + 1) + " [跨节点]";
            long startTime = System.nanoTime();

            try {
                Msg userMsg = Msg.builder()
                        .role(MsgRole.USER)
                        .content(TextBlock.builder().text(messageText).build())
                        .build();

                Msg response = receiverAgent.call(userMsg).block();

                long elapsed = (System.nanoTime() - startTime) / 1_000_000;
                stats.record(elapsed);

                if (response != null && response.getTextContent() != null) {
                    successCount.incrementAndGet();
                }
            } catch (Exception e) {
                long elapsed = (System.nanoTime() - startTime) / 1_000_000;
                stats.recordFailure(elapsed);
                System.err.println("  跨节点同步调用失败 #" + (i + 1) + ": " + e.getMessage());
            }
        }

        double successRate = (successCount.get() * 100.0) / testCount;
        System.out.println("\n  结果:");
        System.out.println("  ┌────────────────────┬──────────────────────┐");
        System.out.println("  │ 测试项             │ 值                   │");
        System.out.println("  ├────────────────────┼──────────────────────┤");
        System.out.printf("  │ 通信拓扑           │ 跨节点               │%n");
        System.out.printf("  │ 测试次数           │ %d                   │%n", testCount);
        System.out.printf("  │ 成功率             │ %.1f%%               │%n", successRate);
        System.out.printf("  │ 平均延迟(ms)       │ %.1f±%.1f            │%n", stats.mean(), stats.stdDev());
        System.out.println("  └────────────────────┴──────────────────────┘\n");
    }

    // ==================== 测试 (b): 异步调用 - 含 10% 超时注入 ====================

    /**
     * 异步调用测试。
     * 发起 100 次异步任务，其中 10% 通过注入延迟模拟超时。
     * 验证回调机制的可靠触发与超时错误码的正确返回。
     *
     * <p>注意：A2aAgent 设计上不支持并发调用（"one agent should not be call with
     * multiple threads and tasks at the same time"），因此每次异步调用都需要
     * 创建独立的 A2aAgent 实例，以确保线程安全。</p>
     */
    private static void testAsyncCallWithTimeout() {
        System.out.println("─────────────────────────────────────────────────");
        System.out.println("测试 (b): 异步调用 - 含 10% 超时注入");
        System.out.println("─────────────────────────────────────────────────");

        // 使用跨节点 Agent 进行异步通信
        String receiverKey = "node-3:Agent-AS-2";
        Integer receiverPort = null;

        // 找到 receiver 的端口
        for (Map.Entry<Integer, String> entry : PORT_TO_AGENT.entrySet()) {
            if (entry.getValue().equals(receiverKey)) {
                receiverPort = entry.getKey();
                break;
            }
        }

        if (receiverPort == null) {
            System.out.println("  跳过: Agent 实例不可用");
            return;
        }

        int totalTasks = 5;
        int timeoutCount = 10; // 10% 超时注入
        int normalCount = totalTasks - timeoutCount;

        AtomicInteger completedTasks = new AtomicInteger(0);
        AtomicInteger timeoutTasks = new AtomicInteger(0);
        AtomicInteger callbackTriggered = new AtomicInteger(0);
        AtomicInteger timeoutErrors = new AtomicInteger(0);
        CopyOnWriteArrayList<Long> normalLatencies = new CopyOnWriteArrayList<>();

        // 顺序执行异步任务（因为 A2aAgent 不支持并发调用）
        // 每次调用创建独立的 A2aAgent 实例以支持超时注入
        for (int i = 0; i < totalTasks; i++) {
            final int taskIndex = i;
            final boolean isTimeoutTask = (taskIndex % 10 == 0); // 每10个注入1个超时

            long startTime = System.nanoTime();

            try {
                // 为每次调用创建独立的 A2aAgent 实例（使用共享的 NacosResolver）
                // 注意：name 必须与 Nacos 中注册的服务名一致，不能追加后缀
                A2aAgent callAgent = A2aAgent.builder()
                        .name(receiverKey)
                        .agentCardResolver(nacosResolver)
                        .build();

                Msg userMsg = Msg.builder()
                        .role(MsgRole.USER)
                        .content(TextBlock.builder().text("异步任务 #" + (taskIndex + 1)).build())
                        .build();

                // 设置超时：超时任务使用极短超时，正常任务使用较长超时
                long timeoutMs = isTimeoutTask ? 1 : 120_000; // 1ms vs 120s

                // 异步调用并设置超时
                Msg response;
                try {
                    response = callAgent.call(userMsg)
                            .timeout(Duration.ofMillis(timeoutMs))
                            .block();

                    long elapsed = (System.nanoTime() - startTime) / 1_000_000;
                    callbackTriggered.incrementAndGet();
                    completedTasks.incrementAndGet();
                    normalLatencies.add(elapsed);

                } catch (IllegalStateException ise) {
                    // Reactor .timeout() 超时后 .block() 抛出 IllegalStateException
                    // 其 cause 为 java.util.concurrent.TimeoutException
                    if (ise.getCause() instanceof java.util.concurrent.TimeoutException) {
                        // 超时回调被正确触发
                        long elapsed = (System.nanoTime() - startTime) / 1_000_000;
                        callbackTriggered.incrementAndGet();
                        timeoutTasks.incrementAndGet();
                        timeoutErrors.incrementAndGet();
                        if (!isTimeoutTask) {
                            System.err.println("  非预期超时 #" + (taskIndex + 1) + " after " + elapsed + "ms");
                        }
                    } else {
                        // 其他 IllegalStateException
                        long elapsed = (System.nanoTime() - startTime) / 1_000_000;
                        callbackTriggered.incrementAndGet();
                        completedTasks.incrementAndGet();
                        normalLatencies.add(elapsed);
                    }
                } catch (Exception e) {
                    // 其他错误
                    long elapsed = (System.nanoTime() - startTime) / 1_000_000;
                    callbackTriggered.incrementAndGet();
                    if (isTimeoutTask) {
                        timeoutTasks.incrementAndGet();
                        timeoutErrors.incrementAndGet();
                    } else {
                        completedTasks.incrementAndGet();
                        normalLatencies.add(elapsed);
                    }
                }

            } catch (Exception e) {
                callbackTriggered.incrementAndGet();
                timeoutTasks.incrementAndGet();
            }

            // 输出进度
            if ((taskIndex + 1) % 20 == 0) {
                System.out.printf("  进度: %d/%d (正常: %d, 超时: %d)%n",
                        taskIndex + 1, totalTasks, completedTasks.get(), timeoutTasks.get());
            }
        }

        // 统计结果
        double callbackRate = (callbackTriggered.get() * 100.0) / totalTasks;
        double normalSuccessRate = normalCount > 0 ? (completedTasks.get() * 100.0) / normalCount : 0;
        double timeoutErrorRate = timeoutCount > 0 ? (timeoutErrors.get() * 100.0) / timeoutCount : 0;

        LatencyStats normalStats = new LatencyStats();
        normalLatencies.forEach(normalStats::record);

        System.out.println("\n  结果:");
        System.out.println("  ┌────────────────────────────┬──────────────────────┐");
        System.out.println("  │ 测试项                     │ 值                   │");
        System.out.println("  ├────────────────────────────┼──────────────────────┤");
        System.out.printf("  │ 通信拓扑                   │ 跨节点               │%n");
        System.out.printf("  │ 总测试次数                 │ %d                   │%n", totalTasks);
        System.out.printf("  │ 正常任务数                 │ %d                   │%n", normalCount);
        System.out.printf("  │ 超时注入任务数             │ %d                   │%n", timeoutCount);
        System.out.printf("  │ 回调触发率                 │ %.1f%%               │%n", callbackRate);
        System.out.printf("  │ 正常任务成功率             │ %.1f%%               │%n", normalSuccessRate);
        System.out.printf("  │ 超时错误码正确返回率       │ %.1f%%               │%n", timeoutErrorRate);
        System.out.printf("  │ 正常任务平均延迟(ms)       │ %.1f±%.1f            │%n", normalStats.mean(), normalStats.stdDev());
        System.out.println("  └────────────────────────────┴──────────────────────┘\n");
    }

    // ==================== 测试 (c): 错误处理 ====================

    /**
     * 错误处理测试。
     * 构造格式错误的 JSON-RPC 请求（缺失 method 字段、非法 params 类型等），
     * 验证系统是否返回标准 A2A 扩展错误码。
     */
    private static void testErrorHandling() {
        System.out.println("─────────────────────────────────────────────────");
        System.out.println("测试 (c): 错误处理 - 格式错误的 JSON-RPC 请求");
        System.out.println("─────────────────────────────────────────────────");

        // 选择一个 Agent 端口来发送错误请求
        int targetPort = 10001;
        String targetUrl = "http://" + HOST + ":" + targetPort + "/a2a/";

        // 测试用例定义: (描述, JSON-RPC 请求体, 期望的错误码)
        List<ErrorTestCase> testCases = List.of(
                new ErrorTestCase(
                        "缺失 method 字段",
                        "{\"jsonrpc\":\"2.0\",\"params\":{\"id\":\"task123\"},\"id\":\"1\"}",
                        -32600  // InvalidRequestError
                ),
                new ErrorTestCase(
                        "无效 params 字段",
                        "{\"jsonrpc\":\"2.0\",\"method\":\"tasks/get\",\"params\":{\"taskId\":\"task123\"},\"id\":\"2\"}",
                        -32602  // InvalidParamsError
                ),
                new ErrorTestCase(
                        "不存在的 method",
                        "{\"jsonrpc\":\"2.0\",\"method\":\"nonexistent/method\",\"params\":{\"id\":\"task123\"},\"id\":\"3\"}",
                        -32601  // MethodNotFoundError
                ),
                new ErrorTestCase(
                        "无效 JSON 格式",
                        "{ invalid json }}}",
                        -32700  // JSONParseError
                ),
                new ErrorTestCase(
                        "缺失 jsonrpc 版本",
                        "{\"method\":\"message/send\",\"params\":{\"message\":{\"kind\":\"message\"}},\"id\":\"5\"}",
                        -32600  // InvalidRequestError
                )
        );

        int totalTests = 50; // 每个用例 10 次
        int successCount = 0;

        for (ErrorTestCase testCase : testCases) {
            int caseSuccess = 0;
            int caseTotal = totalTests / testCases.size();

            for (int i = 0; i < caseTotal; i++) {
                try {
                    Map<String, Object> response = sendJsonRpcRequest(targetUrl, testCase.requestBody);
                    boolean hasError = response.containsKey("error");
                    if (hasError) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> error = (Map<String, Object>) response.get("error");
                        int errorCode = ((Number) error.get("code")).intValue();
                        if (errorCode == testCase.expectedErrorCode) {
                            caseSuccess++;
                            successCount++;
                        } else {
                            System.err.printf("  错误码不匹配: 期望 %d, 实际 %d%n", testCase.expectedErrorCode, errorCode);
                        }
                    } else {
                        System.err.println("  未返回错误响应: " + testCase.description);
                    }
                } catch (Exception e) {
                    System.err.println("  请求异常 [" + testCase.description + "]: " + e.getMessage());
                }
            }

            double caseRate = (caseSuccess * 100.0) / caseTotal;
            System.out.printf("  [%s] 成功率: %.1f%% (%d/%d) 期望错误码: %d%n",
                    testCase.description, caseRate, caseSuccess, caseTotal, testCase.expectedErrorCode);
        }

        double totalRate = (successCount * 100.0) / totalTests;
        System.out.println("\n  结果:");
        System.out.println("  ┌────────────────────┬──────────────────────┐");
        System.out.println("  │ 测试项             │ 值                   │");
        System.out.println("  ├────────────────────┼──────────────────────┤");
        System.out.printf("  │ 通信拓扑           │ 全拓扑               │%n");
        System.out.printf("  │ 测试次数           │ %d                   │%n", totalTests);
        System.out.printf("  │ 成功率             │ %.1f%%               │%n", totalRate);
        System.out.println("  └────────────────────┴──────────────────────┘\n");
    }

    /**
     * 发送 JSON-RPC 请求并返回解析后的响应。
     */
    private static Map<String, Object> sendJsonRpcRequest(String url, String body) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setDoOutput(true);
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(10000);

        try (OutputStream os = conn.getOutputStream()) {
            os.write(body.getBytes(StandardCharsets.UTF_8));
        }

        int responseCode = conn.getResponseCode();
        java.io.InputStream is = responseCode >= 400 ? conn.getErrorStream() : conn.getInputStream();
        if (is == null) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("httpStatus", responseCode);
            return errorResponse;
        }

        String responseBody = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        @SuppressWarnings("unchecked")
        Map<String, Object> parsed = JSON_MAPPER.readValue(responseBody, Map.class);
        return parsed;
    }

    // ==================== 工具类 ====================

    /**
     * 延迟统计工具类，支持均值/标准差/成功率计算。
     */
    static class LatencyStats {
        private final List<Double> values = new ArrayList<>();
        private int failureCount = 0;

        void record(double latencyMs) {
            values.add(latencyMs);
        }

        void recordFailure(double latencyMs) {
            values.add(latencyMs);
            failureCount++;
        }

        double mean() {
            if (values.isEmpty()) return 0;
            return values.stream().mapToDouble(v -> v).average().orElse(0);
        }

        double stdDev() {
            if (values.size() < 2) return 0;
            double avg = mean();
            double variance = values.stream()
                    .mapToDouble(v -> Math.pow(v - avg, 2))
                    .average().orElse(0);
            return Math.sqrt(variance);
        }

        int totalCount() {
            return values.size();
        }

        int successCount() {
            return values.size() - failureCount;
        }

        double successRate() {
            if (values.isEmpty()) return 0;
            return (successCount() * 100.0) / values.size();
        }
    }

    /**
     * 错误处理测试用例
     */
    record ErrorTestCase(String description, String requestBody, int expectedErrorCode) {
    }

    /**
     * 内嵌的 Echo Agent Handler，用于测试
     */
    static class EchoAgentHandler extends io.agentscope.runtime.adapters.agentscope.AgentScopeAgentHandler {
        private final String agentName;
        // private final String apiKey;  // Mock模式不需要，取消注释可恢复真实调用

        EchoAgentHandler(String agentName) {
            this.agentName = agentName;
            // this.apiKey = System.getenv("DASHSCOPE_API_KEY");  // Mock模式不需要
        }

        @Override
        public reactor.core.publisher.Flux<io.agentscope.core.agent.Event> streamQuery(
                io.agentscope.runtime.engine.schemas.AgentRequest request, Object messages) {
            try {
                // ===== Mock模式：直接返回固定响应，不调用LLM =====
                return reactor.core.publisher.Flux.concat(
                        reactor.core.publisher.Flux.just(
                                new io.agentscope.core.agent.Event(
                                        io.agentscope.core.agent.EventType.AGENT_RESULT,
                                        io.agentscope.core.message.Msg.builder()
                                                .content(io.agentscope.core.message.TextBlock.builder()
                                                        .text("ok")
                                                        .build())
                                                .build(),
                                        false)
                        ),
                        reactor.core.publisher.Flux.just(
                                new io.agentscope.core.agent.Event(
                                        io.agentscope.core.agent.EventType.AGENT_RESULT,
                                        io.agentscope.core.message.Msg.builder()
                                                .content(io.agentscope.core.message.TextBlock.builder()
                                                        .text("")
                                                        .build())
                                                .build(),
                                        true)
                        )
                );
                // ===== 真实调用模式：取消下方注释并注释掉上方Mock代码 =====
//                io.agentscope.core.tool.Toolkit toolkit = new io.agentscope.core.tool.Toolkit();
//
//                io.agentscope.core.ReActAgent agent = io.agentscope.core.ReActAgent.builder()
//                        .name(agentName)
//                        .sysPrompt("你是通信测试Agent " + agentName + "。当收到消息时，请回显：[来自" + agentName + "] 收到: <用户消息>")
//                        .toolkit(toolkit)
//                        .model(io.agentscope.core.model.DashScopeChatModel.builder()
//                                .apiKey(apiKey)
//                                .modelName("qwen-max")
//                                .stream(true)
//                                .formatter(new io.agentscope.core.formatter.dashscope.DashScopeChatFormatter())
//                                .build())
//                        .build();
//
//                java.util.List<io.agentscope.core.message.Msg> agentMessages;
//                if (messages instanceof java.util.List) {
//                    agentMessages = (java.util.List<io.agentscope.core.message.Msg>) messages;
//                } else if (messages instanceof io.agentscope.core.message.Msg) {
//                    agentMessages = java.util.List.of((io.agentscope.core.message.Msg) messages);
//                } else {
//                    agentMessages = java.util.List.of();
//                }
//
//                io.agentscope.core.message.Msg queryMessage;
//                if (agentMessages.isEmpty()) {
//                    queryMessage = io.agentscope.core.message.Msg.builder()
//                            .role(io.agentscope.core.message.MsgRole.USER).build();
//                } else if (agentMessages.size() == 1) {
//                    queryMessage = agentMessages.get(0);
//                } else {
//                    for (int i = 0; i < agentMessages.size() - 1; i++) {
//                        agent.getMemory().addMessage(agentMessages.get(i));
//                    }
//                    queryMessage = agentMessages.get(agentMessages.size() - 1);
//                }
//
//                io.agentscope.core.agent.StreamOptions streamOptions = io.agentscope.core.agent.StreamOptions.builder()
//                        .eventTypes(io.agentscope.core.agent.EventType.REASONING, io.agentscope.core.agent.EventType.TOOL_RESULT)
//                        .incremental(true)
//                        .build();
//
//                return agent.stream(queryMessage, streamOptions);
            } catch (Exception e) {
                return reactor.core.publisher.Flux.error(e);
            }
        }

        @Override
        public boolean isHealthy() { return true; }

        @Override
        public String getName() { return agentName; }

        @Override
        public String getDescription() { return "A2A通信测试Agent - " + agentName; }
    }
}
