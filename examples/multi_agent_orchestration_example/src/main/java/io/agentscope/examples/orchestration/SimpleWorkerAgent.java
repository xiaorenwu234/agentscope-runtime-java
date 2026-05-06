/*
 * Copyright 2024-2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.agentscope.examples.orchestration;

import io.agentscope.core.ReActAgent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.model.DashScopeChatModel;
import io.agentscope.runtime.adapters.AgentHandler;
import io.agentscope.runtime.adapters.MessageAdapter;
import io.agentscope.runtime.adapters.StreamAdapter;
import io.agentscope.runtime.engine.schemas.AgentRequest;
import io.agentscope.runtime.engine.schemas.Message;
import io.agentscope.runtime.engine.schemas.TextContent;
import io.agentscope.runtime.sandbox.manager.SandboxService;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * Simple worker agent that processes tasks.
 * This is a mock implementation for demonstration purposes.
 */
public class SimpleWorkerAgent implements AgentHandler {

    private final String agentName;

    private final String agentType;

    private volatile boolean running = false;

    public SimpleWorkerAgent(String agentName, String agentType) {
        this.agentName = agentName;
        this.agentType = agentType;
    }

    @Override
    public SandboxService getSandboxService() {
        return null;
    }

    @Override
    public String getName() {
        return agentName;
    }

    @Override
    public String getDescription() {
        return "Worker agent of type: " + agentType;
    }

    @Override
    public String getFrameworkType() {
        return "SimpleWorker";
    }

    @Override
    public void start() {
        running = true;
        System.out.println("[" + agentName + "] Worker agent started");
    }

    @Override
    public Flux<?> streamQuery(AgentRequest request, Object messages) {
        // Extract input text
        String inputText = extractInputText(request);

        // Simulate processing based on agent type
        String result = processTask(inputText);

        // Return result as text content
        TextContent content = new TextContent();
        content.setText(result);

        return Flux.just(content);
    }

    private String extractInputText(AgentRequest request) {
        if (request.getInput() == null || request.getInput().isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (Message msg : request.getInput()) {
            if (msg.getContent() != null) {
                for (var c : msg.getContent()) {
                    if (c instanceof TextContent tc) {
                        sb.append(tc.getText());
                    }
                }
            }
        }
        return sb.toString();
    }

    private String processTask(String input) {
        // Simulate different worker behaviors
        return switch (agentType) {
            case "translator" -> translateTask(input);
            case "analyzer" -> analyzeTask(input);
            case "summarizer" -> summarizeTask(input);
            default -> "Processed by " + agentName + ": " + input;
        };
    }

    private String translateTask(String input) {
        // Mock translation
        ReActAgent jarvis = ReActAgent.builder()
                .name("Jarvis")
                .sysPrompt("你是一个翻译专家，负责翻译输入文本")
                .model(DashScopeChatModel.builder()
                        .apiKey(System.getenv("DASHSCOPE_API_KEY"))
                        .modelName("qwen3-max")
                        .build())
                .build();

        // 发送消息
        Msg msg = Msg.builder()
                .textContent(input)
                .build();

        Msg response = jarvis.call(msg).block();
        System.out.println(response.getTextContent());

        return response.getTextContent();
    }

    private String analyzeTask(String input) {
        ReActAgent jarvis = ReActAgent.builder()
                .name("Jarvis")
                .sysPrompt("你是一个分析专家，负责分析输入文本")
                .model(DashScopeChatModel.builder()
                        .apiKey(System.getenv("DASHSCOPE_API_KEY"))
                        .modelName("qwen3-max")
                        .build())
                .build();

        // 发送消息
        Msg msg = Msg.builder()
                .textContent(input)
                .build();

        Msg response = jarvis.call(msg).block();
        System.out.println(response.getTextContent());

        return response.getTextContent();
    }

    private String summarizeTask(String input) {
        ReActAgent jarvis = ReActAgent.builder()
                .name("Jarvis")
                .sysPrompt("你是一个总结专家，负责总结输入文本")
                .model(DashScopeChatModel.builder()
                        .apiKey(System.getenv("DASHSCOPE_API_KEY"))
                        .modelName("qwen3-max")
                        .build())
                .build();

        // 发送消息
        Msg msg = Msg.builder()
                .textContent(input)
                .build();

        Msg response = jarvis.call(msg).block();
        System.out.println(response.getTextContent());

        return response.getTextContent();
    }

    @Override
    public void stop() {
        running = false;
        System.out.println("[" + agentName + "] Worker agent stopped");
    }

    @Override
    public boolean isHealthy() {
        return running;
    }

    @Override
    public StreamAdapter getStreamAdapter() {
        return rawStream -> {
            Flux<Object> objectFlux = (Flux<Object>) rawStream;
            return objectFlux.filter(o -> o instanceof io.agentscope.runtime.engine.schemas.Event)
                    .cast(io.agentscope.runtime.engine.schemas.Event.class);
        };
    }

    @Override
    public MessageAdapter getMessageAdapter() {
        return new MessageAdapter() {
            @Override
            public List<Message> frameworkMsgToMessage(Object frameworkMsg) {
                return List.of();
            }

            @Override
            public Object messageToFrameworkMsg(Object messages) {
                return messages;
            }
        };
    }
}