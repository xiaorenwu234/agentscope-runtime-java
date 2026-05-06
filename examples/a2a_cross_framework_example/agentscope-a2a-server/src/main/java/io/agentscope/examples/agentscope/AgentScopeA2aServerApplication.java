package io.agentscope.examples.agentscope;

import io.agentscope.runtime.app.AgentApp;
import io.agentscope.runtime.engine.services.agent_state.InMemoryStateService;
import io.agentscope.runtime.engine.services.memory.persistence.memory.service.InMemoryMemoryService;
import io.agentscope.runtime.engine.services.memory.persistence.session.InMemorySessionHistoryService;
import io.agentscope.runtime.sandbox.manager.ManagerConfig;
import io.agentscope.runtime.sandbox.manager.SandboxService;
import io.agentscope.runtime.sandbox.manager.client.container.docker.DockerClientStarter;
import org.jetbrains.annotations.NotNull;

public class AgentScopeA2aServerApplication {
    public static void main(String[] args) {
        if (System.getenv("DASHSCOPE_API_KEY") == null) {
            System.err.println("请设置环境变量 DASHSCOPE_API_KEY");
            System.exit(1);
        }
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
        AgentApp agentApp = new AgentApp(agentHandler);
        agentApp.run("localhost", 10001);
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