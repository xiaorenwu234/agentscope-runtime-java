package io.agentscope.examples.agentscope;

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

public class AgentScopeA2aServerApplication {
    private static final Logger logger = LoggerFactory.getLogger(AgentScopeA2aServerApplication.class);
    private static NacosAgentRegistry nacosRegistry;

    public static void main(String[] args) {
        if (System.getenv("DASHSCOPE_API_KEY") == null) {
            System.err.println("请设置环境变量 DASHSCOPE_API_KEY");
            System.exit(1);
        }
        
        // 添加关闭钩子，确保服务正常注销
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("Shutting down and deregistering from Nacos...");
            if (nacosRegistry != null) {
                nacosRegistry.close();
            }
        }));
        
        runAgent();
    }

    private static void runAgent() {
        MyAgentScopeAgentHandler agentHandler = new MyAgentScopeAgentHandler();

        // 内存状态服务
        agentHandler.setStateService(new InMemoryStateService());
        agentHandler.setSessionHistoryService(new InMemorySessionHistoryService());
        agentHandler.setMemoryService(new InMemoryMemoryService());

        // 沙箱服务
        agentHandler.setSandboxService(buildSandboxService());

        // 启动 AgentApp，端口 10001
        String host = "localhost";
        int port = 10001;
        AgentApp agentApp = new AgentApp(agentHandler);
        agentApp.run(host, port);
        
        // 注册到 Nacos
        registerToNacos(host, port);
    }

    @NotNull
    private static SandboxService buildSandboxService() {
        var clientConfig = DockerClientStarter.builder().build();
        var managerConfig = ManagerConfig.builder()
                .clientStarter(clientConfig)
                .build();
        return new SandboxService(managerConfig);
    }

    /**
     * Register this service to Nacos.
     */
    private static void registerToNacos(String host, int port) {
        try {
            // 从环境变量或系统属性获取Nacos配置，使用默认值
            String nacosServer = System.getenv().getOrDefault("NACOS_SERVER_ADDR", 
                    System.getProperty("nacos.server.addr", "localhost:8848"));
            String namespace = System.getenv().getOrDefault("NACOS_NAMESPACE", 
                    System.getProperty("nacos.namespace", "public"));
            String group = System.getenv().getOrDefault("NACOS_GROUP", 
                    System.getProperty("nacos.group", "DEFAULT_GROUP"));
            String serviceName = System.getenv().getOrDefault("NACOS_SERVICE_NAME", 
                    System.getProperty("nacos.service.name", "agentscope-a2a-server"));
            
            logger.info("Registering to Nacos at {} with service name: {}", nacosServer, serviceName);
            
            // 创建Nacos注册配置
            NacosRegistryConfig config = NacosRegistryConfig.builder()
                    .serverAddr(nacosServer)
                    .namespace(namespace)
                    .group(group)
                    .build();
            
            // 创建注册中心
            nacosRegistry = new NacosAgentRegistry(config);
            
            // 创建Agent实例
            String instanceId = serviceName + "-" + host + "-" + port + "-" + System.currentTimeMillis();
            AgentInstance instance = AgentInstance.builder()
                    .instanceId(instanceId)
                    .agentName(serviceName)
                    .host(host)
                    .port(port)
                    .weight(1.0)
                    .build();
            
            // 注册到Nacos
            nacosRegistry.register(instance);
            
            logger.info("Successfully registered to Nacos: {} ({}:{})", 
                    serviceName, host, port);
            
        } catch (Exception e) {
            logger.error("Failed to register to Nacos: {}", e.getMessage(), e);
            // 不阻止服务启动，仅记录错误
        }
    }
}