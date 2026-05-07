package io.agentscope.examples.server;

import io.agentscope.runtime.app.AgentApp;
import io.agentscope.runtime.cluster.AgentInstance;
import io.agentscope.runtime.cluster.nacos.NacosAgentRegistry;
import io.agentscope.runtime.cluster.nacos.NacosRegistryConfig;
import io.agentscope.runtime.engine.services.agent_state.InMemoryStateService;
import io.agentscope.runtime.engine.services.memory.persistence.memory.service.InMemoryMemoryService;
import io.agentscope.runtime.engine.services.memory.persistence.session.InMemorySessionHistoryService;
import io.agentscope.runtime.sandbox.manager.ManagerConfig;
import io.agentscope.runtime.sandbox.manager.SandboxService;
import io.agentscope.runtime.sandbox.manager.client.container.docker.DockerClientStarter;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 可复用的 Agent 服务端。
 * 通过命令行参数指定端口和名称，同一份代码运行多个实例。
 * 启动后自动注册到 Nacos，支持服务发现。
 *
 * <p>用法: java -jar agent-server.jar --port=10001 --name=Agent-AS-1 [--nacos-addr=localhost:8848]</p>
 */
public class AgentServerApplication {

    private static final Logger logger = LoggerFactory.getLogger(AgentServerApplication.class);

    private static NacosAgentRegistry nacosRegistry;

    public static void main(String[] args) {
        if (System.getenv("DASHSCOPE_API_KEY") == null) {
            System.err.println("请设置环境变量 DASHSCOPE_API_KEY");
            System.exit(1);
        }

        int port = 10001;
        String agentName = "Agent-AS-1";
        String nacosAddr = System.getenv().getOrDefault("NACOS_SERVER_ADDR",
                System.getProperty("nacos.server.addr", "localhost:8848"));
        String nacosNamespace = System.getenv().getOrDefault("NACOS_NAMESPACE",
                System.getProperty("nacos.namespace", "public"));
        String nacosGroup = System.getenv().getOrDefault("NACOS_GROUP",
                System.getProperty("nacos.group", "DEFAULT_GROUP"));

        for (String arg : args) {
            if (arg.startsWith("--port=")) {
                port = Integer.parseInt(arg.substring("--port=".length()));
            } else if (arg.startsWith("--name=")) {
                agentName = arg.substring("--name=".length());
            } else if (arg.startsWith("--nacos-addr=")) {
                nacosAddr = arg.substring("--nacos-addr=".length());
            }
        }

        // 添加关闭钩子，确保服务正常注销
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("Shutting down and deregistering from Nacos...");
            if (nacosRegistry != null) {
                nacosRegistry.close();
            }
        }));

        EchoAgentHandler agentHandler = new EchoAgentHandler(agentName);

        agentHandler.setStateService(new InMemoryStateService());
        agentHandler.setSessionHistoryService(new InMemorySessionHistoryService());
        agentHandler.setMemoryService(new InMemoryMemoryService());
        agentHandler.setSandboxService(buildSandboxService());

        String host = "localhost";
        AgentApp agentApp = new AgentApp(agentHandler);
        agentApp.run(host, port);

        // 注册到 Nacos
        registerToNacos(host, port, agentName, nacosAddr, nacosNamespace, nacosGroup);

        System.out.println("Agent [" + agentName + "] started on port " + port);
    }

    /**
     * 注册当前 Agent 实例到 Nacos。
     */
    private static void registerToNacos(String host, int port, String agentName,
                                         String nacosAddr, String namespace, String group) {
        try {
            logger.info("Registering to Nacos at {} with service name: {}", nacosAddr, agentName);

            NacosRegistryConfig config = NacosRegistryConfig.builder()
                    .serverAddr(nacosAddr)
                    .namespace(namespace)
                    .group(group)
                    .build();

            nacosRegistry = new NacosAgentRegistry(config);

            String instanceId = agentName + "-" + host + "-" + port + "-" + System.currentTimeMillis();
            AgentInstance instance = AgentInstance.builder()
                    .instanceId(instanceId)
                    .agentName(agentName)
                    .host(host)
                    .port(port)
                    .weight(1.0)
                    .build();

            nacosRegistry.register(instance);

            logger.info("Successfully registered to Nacos: {} ({}:{})", agentName, host, port);

        } catch (Exception e) {
            logger.error("Failed to register to Nacos: {}", e.getMessage(), e);
            // 不阻止服务启动，仅记录错误
        }
    }

    @NotNull
    private static SandboxService buildSandboxService() {
        var clientConfig = DockerClientStarter.builder().build();
        var managerConfig = ManagerConfig.builder()
                .clientStarter(clientConfig)
                .build();
        return new SandboxService(managerConfig);
    }
}
