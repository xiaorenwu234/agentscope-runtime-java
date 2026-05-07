package io.agentscope.examples.saa;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import com.alibaba.cloud.ai.sandbox.ToolkitInit;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import io.agentscope.core.agent.Event;
import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.TextBlock;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import io.agentscope.core.message.Msg;
import com.alibaba.cloud.ai.graph.checkpoint.savers.MemorySaver;
import io.agentscope.runtime.adapters.agentscope.AgentScopeAgentHandler;
import io.agentscope.runtime.engine.schemas.AgentRequest;
import io.agentscope.runtime.sandbox.box.BaseSandbox;
import io.agentscope.runtime.sandbox.box.Sandbox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.ToolCallback;
import reactor.core.publisher.Flux;

import java.util.List;

import static io.agentscope.core.agent.EventType.AGENT_RESULT;

public class MySaaAgentHandler extends AgentScopeAgentHandler {
    private static final Logger logger = LoggerFactory.getLogger(MySaaAgentHandler.class);
    private final String apiKey;

    public MySaaAgentHandler() {
        this.apiKey = System.getenv("DASHSCOPE_API_KEY");
    }

    @Override
    public Flux<Event> streamQuery(AgentRequest request, Object messages) {
        String sessionId = request.getSessionId();
        String userId = request.getUserId();

        try {
            ToolCallback toolCallback = null;
            // 工具集
            Sandbox sandbox = null;
            if (sandboxService != null) {
                try {
                    sandbox = new BaseSandbox(sandboxService, userId, sessionId);
                    toolCallback = ToolkitInit.RunPythonCodeTool(sandbox);
                    logger.debug("已注册 Python 执行工具");
                } catch (Exception e) {
                    logger.warn("沙箱初始化失败", e);
                }
            }

            DashScopeApi dashScopeApi = DashScopeApi.builder()
                    .apiKey(apiKey)
                    .build();

            ChatModel chatModel = DashScopeChatModel.builder()
                    .dashScopeApi(dashScopeApi)
                    .build();

            ReactAgent agent = ReactAgent.builder()
                    .name("Friday")
                    .model(chatModel)
                    .description("你是一个智能助手，能执行Python代码、回答问题。")
                    .instruction("你是一个智能助手，可以异步查询天气和股票信息。当用户询问时，使用相应的工具。")
                    .tools(toolCallback)
                    .saver(new MemorySaver())
                    .build();

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
                queryMessage = agentMessages.get(agentMessages.size() - 1);
            }
            StringBuilder inputString = new StringBuilder();
            for (ContentBlock contentBlock : queryMessage.getContent()) {
                if (contentBlock instanceof TextBlock textBlock) {
                    inputString.append(textBlock.getText());
                }
            }

            return agent.stream(inputString.toString())
                    .filter(response -> response.node() != null)
                    .map(response -> {
                        String chunk = "";
                        if (response instanceof StreamingOutput<?> streamingOutput) {
                            if (streamingOutput.message() != null && streamingOutput.message() instanceof AssistantMessage assistantMessage) {
                                chunk = assistantMessage.getText();
                            }
                        }
                        Msg msg = Msg.builder()
                                .content(TextBlock.builder().text(chunk).build())
                                .build();
                        return new Event(AGENT_RESULT, msg, false);
                    })
                    .concatWith(Flux.just(
                            new Event(AGENT_RESULT,
                                    Msg.builder().content(TextBlock.builder().text("").build()).build(),
                                    true)
                    ));
        } catch (GraphRunnerException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public boolean isHealthy() {
        return true;
    }

    @Override
    public String getName() {
        return "DemoAgent_SAA";
    }

    @Override
    public String getDescription() {
        return "SAA 流式智能体";
    }
}