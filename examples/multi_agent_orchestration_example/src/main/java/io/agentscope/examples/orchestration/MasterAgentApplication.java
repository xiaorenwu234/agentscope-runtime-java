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

import io.agentscope.runtime.adapters.AgentHandler;
import io.agentscope.runtime.adapters.MessageAdapter;
import io.agentscope.runtime.adapters.StreamAdapter;
import io.agentscope.runtime.app.AgentApp;
import io.agentscope.runtime.cluster.AgentRegistry;
import io.agentscope.runtime.cluster.nacos.NacosAgentRegistry;
import io.agentscope.runtime.cluster.nacos.NacosRegistryConfig;
import io.agentscope.runtime.engine.schemas.AgentRequest;
import io.agentscope.runtime.engine.schemas.Message;
import io.agentscope.runtime.engine.schemas.TextContent;
import io.agentscope.runtime.orchestration.AgentOrchestrator;
import io.agentscope.runtime.rpc.AgentClient;
import io.agentscope.runtime.rpc.DefaultAgentClient;
import io.agentscope.runtime.sandbox.manager.SandboxService;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

/**
 * Master Agent Application.
 * Demonstrates multi-agent orchestration with master-slave pattern.
 *
 * Usage:
 *   java -jar master-agent.jar --port=8090 --nacos-addr=localhost:8848
 */
public class MasterAgentApplication {

    public static void main(String[] args) {
        // Parse command line arguments
        int port = getIntArg(args, "--port", 8090);
        String nacosAddr = getStringArg(args, "--nacos-addr", "localhost:8848");

        System.out.println("========================================");
        System.out.println("Starting Master Agent (Orchestrator)");
        System.out.println("  Port: " + port);
        System.out.println("  Nacos: " + nacosAddr);
        System.out.println("========================================");

        // Create Nacos registry
        NacosRegistryConfig config = NacosRegistryConfig.builder()
                .serverAddr(nacosAddr)
                .build();
        AgentRegistry registry = new NacosAgentRegistry(config);

        // Create agent client with load balancing
        AgentClient agentClient = new DefaultAgentClient(registry);

        // Create orchestrator
        AgentOrchestrator orchestrator = new AgentOrchestrator(agentClient);

        // Create master agent handler
        MasterAgentHandler masterAgent = new MasterAgentHandler(orchestrator);

        // Create and run AgentApp
        AgentApp app = new AgentApp(masterAgent);

        // Enable cluster mode
        System.setProperty("agentscope.cluster.enabled", "true");
        System.setProperty("agentscope.cluster.nacos.server-addr", nacosAddr);

        app.run(port);
    }

    /**
     * Master agent handler that coordinates worker agents.
     */
    static class MasterAgentHandler implements AgentHandler {

        private final AgentOrchestrator orchestrator;

        private volatile boolean running = false;

        public MasterAgentHandler(AgentOrchestrator orchestrator) {
            this.orchestrator = orchestrator;
        }

        @Override
        public SandboxService getSandboxService() {
            return null;
        }

        @Override
        public String getName() {
            return "master";
        }

        @Override
        public String getDescription() {
            return "Master agent that orchestrates worker agents";
        }

        @Override
        public String getFrameworkType() {
            return "MasterOrchestrator";
        }

        @Override
        public void start() {
            running = true;
            System.out.println("[Master] Master agent started");
        }

        @Override
        public Flux<?> streamQuery(AgentRequest request, Object messages) {
            String inputText = extractInputText(request);
            System.out.println("[Master] Received request: " + inputText);

            // Define worker agents to coordinate
            List<String> workers = List.of("translator", "analyzer", "summarizer");

            // Fan-out to all workers
            System.out.println("[Master] Dispatching to workers: " + workers);
            Map<String, String> results = orchestrator.fanOut(workers, inputText);

            // Aggregate results
            StringBuilder aggregated = new StringBuilder();
            aggregated.append("=== Master Agent Orchestration Results ===\n\n");
            aggregated.append("Original Input: ").append(inputText).append("\n\n");
            aggregated.append("Worker Results:\n");
            aggregated.append("-".repeat(40)).append("\n");

            for (Map.Entry<String, String> entry : results.entrySet()) {
                aggregated.append("\n[").append(entry.getKey()).append("]\n");
                aggregated.append(entry.getValue()).append("\n");
            }

            aggregated.append("\n").append("=".repeat(40));
            aggregated.append("\nAll tasks completed by Master Agent.\n");

            TextContent content = new TextContent();
            content.setText(aggregated.toString());

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

        @Override
        public void stop() {
            running = false;
            orchestrator.shutdown();
            System.out.println("[Master] Master agent stopped");
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

    private static int getIntArg(String[] args, String name, int defaultValue) {
        for (String arg : args) {
            if (arg.startsWith(name + "=")) {
                try {
                    return Integer.parseInt(arg.substring(name.length() + 1));
                }
                catch (NumberFormatException e) {
                    return defaultValue;
                }
            }
        }
        return defaultValue;
    }

    private static String getStringArg(String[] args, String name, String defaultValue) {
        for (String arg : args) {
            if (arg.startsWith(name + "=")) {
                return arg.substring(name.length() + 1);
            }
        }
        return defaultValue;
    }
}
