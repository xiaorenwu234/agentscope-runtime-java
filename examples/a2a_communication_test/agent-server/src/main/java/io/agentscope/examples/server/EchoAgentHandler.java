package io.agentscope.examples.server;

import java.util.List;

import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.Event;
import io.agentscope.core.agent.EventType;
import io.agentscope.core.agent.StreamOptions;
import io.agentscope.core.formatter.dashscope.DashScopeChatFormatter;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.model.DashScopeChatModel;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.runtime.adapters.agentscope.AgentScopeAgentHandler;
import io.agentscope.runtime.engine.schemas.AgentRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

import static io.agentscope.core.agent.EventType.AGENT_RESULT;

/**
 * Echo Agent Handler - 简单的回显型 Agent，用于 A2A 通信测试。
 *
 * <p>该 Agent 会将收到的消息原样回显，并附上自己的名称标识。
 * 设计为通信测试用的轻量级 Agent，不依赖沙箱和复杂工具。</p>
 */
public class EchoAgentHandler extends AgentScopeAgentHandler {
    private static final Logger logger = LoggerFactory.getLogger(EchoAgentHandler.class);
    private final String agentName;
    private final String apiKey;

    public EchoAgentHandler(String agentName) {
        this.agentName = agentName;
        this.apiKey = System.getenv("DASHSCOPE_API_KEY");
    }

    @Override
    public Flux<Event> streamQuery(AgentRequest request, Object messages) {
        try {
            Toolkit toolkit = new Toolkit();

            ReActAgent agent = ReActAgent.builder()
                    .name(agentName)
                    .sysPrompt("你是一个通信测试 Agent。你的名称是 " + agentName + "。" +
                            "当收到消息时，请用以下格式回复：\n" +
                            "[来自 " + agentName + "] 收到消息: <原样回显用户的消息>\n\n" +
                            "请确保完全回显用户发送的文本内容，不要修改或省略任何字符。")
                    .toolkit(toolkit)
                    .model(
                            DashScopeChatModel.builder()
                                    .apiKey(apiKey)
                                    .modelName("qwen-max")
                                    .stream(true)
                                    .formatter(new DashScopeChatFormatter())
                                    .build()
                    )
                    .build();

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

            StreamOptions streamOptions = StreamOptions.builder()
                    .eventTypes(EventType.REASONING, EventType.TOOL_RESULT)
                    .incremental(true)
                    .build();

//            return agent.stream(queryMessage, streamOptions)
//                    .doOnNext(event -> logger.debug("[{}] event: {}", agentName, event));

            return Flux.concat(
                    Flux.just(
                            new Event(AGENT_RESULT,
                                    Msg.builder()
                                            .content(TextBlock.builder().text("ok").build())
                                            .build(),
                                    false)
                    ),
                    Flux.just(
                            new Event(AGENT_RESULT,
                                    Msg.builder()
                                            .content(TextBlock.builder().text("").build())
                                            .build(),
                                    true)
                    )
            );

        } catch (Exception e) {
            logger.error("[{}] streamQuery error", agentName, e);
            return Flux.error(e);
        }
    }

    @Override
    public boolean isHealthy() {
        return true;
    }

    @Override
    public String getName() {
        return agentName;
    }

    @Override
    public String getDescription() {
        return "A2A通信测试Agent - " + agentName;
    }
}
