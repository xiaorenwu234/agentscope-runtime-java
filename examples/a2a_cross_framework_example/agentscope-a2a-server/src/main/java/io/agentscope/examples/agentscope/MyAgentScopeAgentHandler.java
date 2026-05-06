package io.agentscope.examples.agentscope;

import java.util.List;

import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.EventType;
import io.agentscope.core.agent.StreamOptions;
import io.agentscope.core.formatter.dashscope.DashScopeChatFormatter;
import io.agentscope.core.message.Msg;
import io.agentscope.core.model.DashScopeChatModel;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.runtime.adapters.agentscope.AgentScopeAgentHandler;
import io.agentscope.runtime.engine.agents.agentscope.tools.ToolkitInit;
import io.agentscope.runtime.engine.schemas.AgentRequest;
import io.agentscope.runtime.sandbox.box.BaseSandbox;
import io.agentscope.runtime.sandbox.box.Sandbox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

public class MyAgentScopeAgentHandler extends AgentScopeAgentHandler {
    private static final Logger logger = LoggerFactory.getLogger(MyAgentScopeAgentHandler.class);
    private final String apiKey;

    public MyAgentScopeAgentHandler() {
        this.apiKey = System.getenv("DASHSCOPE_API_KEY");
    }

    @Override
    public Flux<io.agentscope.core.agent.Event> streamQuery(AgentRequest request, Object messages) {
        String sessionId = request.getSessionId();
        String userId = request.getUserId();

        try {
            // 工具集
            Toolkit toolkit = new Toolkit();
            if (sandboxService != null) {
                try {
                    Sandbox sandbox = new BaseSandbox(sandboxService, userId, sessionId);
                    toolkit.registerTool(ToolkitInit.RunPythonCodeTool(sandbox));
                    logger.debug("已注册 Python 执行工具");
                } catch (Exception e) {
                    logger.warn("沙箱初始化失败", e);
                }
            }
            // 构建 Agent
            ReActAgent.Builder agentBuilder = ReActAgent.builder()
                    .name("Friday")
                    .sysPrompt("你是一个智能助手，能执行Python代码、回答问题。")
                    .toolkit(toolkit)
                    .model(
                            DashScopeChatModel.builder()
                                    .apiKey(apiKey)
                                    .modelName("qwen-max")
                                    .stream(true)
                                    .formatter(new DashScopeChatFormatter())
                                    .build()
                    );

            ReActAgent agent = agentBuilder.build();

            // 消息处理
            List<Msg> agentMessages;
            if (messages instanceof List) {
                agentMessages = (List<Msg>) messages;
            } else if (messages instanceof Msg) {
                agentMessages = List.of((Msg) messages);
            } else {
                agentMessages = List.of();
            }

            Msg queryMessage;
            if (agentMessages.isEmpty()) {
                queryMessage = Msg.builder().role(io.agentscope.core.message.MsgRole.USER).build();
            } else if (agentMessages.size() == 1) {
                queryMessage = agentMessages.get(0);
            } else {
                for (int i = 0; i < agentMessages.size() - 1; i++) {
                    agent.getMemory().addMessage(agentMessages.get(i));
                }
                queryMessage = agentMessages.get(agentMessages.size() - 1);
            }

            // 流式输出
            StreamOptions streamOptions = StreamOptions.builder()
                    .eventTypes(EventType.REASONING, EventType.TOOL_RESULT)
                    .incremental(true)
                    .build();

            return agent.stream(queryMessage, streamOptions)
                    .doOnNext(event -> logger.info("事件: {}", event));

        } catch (Exception e) {
            logger.error("streamQuery 异常", e);
            return Flux.error(e);
        }
    }

    @Override
    public boolean isHealthy() {
        return true;
    }

    @Override
    public String getName() {
        return "DemoAgent";
    }

    @Override
    public String getDescription() {
        return "AgentScope 流式智能体";
    }
}