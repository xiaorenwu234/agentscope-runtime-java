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

import io.agentscope.runtime.cluster.AgentInstance;
import io.agentscope.runtime.cluster.AgentRegistry;
import io.agentscope.runtime.cluster.nacos.NacosAgentRegistry;
import io.agentscope.runtime.cluster.nacos.NacosRegistryConfig;
import io.agentscope.runtime.orchestration.AgentOrchestrator;
import io.agentscope.runtime.rpc.AgentClient;
import io.agentscope.runtime.rpc.DefaultAgentClient;

import java.util.List;
import java.util.Map;

/**
 * Demo application showing multi-agent orchestration.
 * 
 * This demo shows how to:
 * 1. Connect to Nacos registry
 * 2. Discover available agents
 * 3. Use AgentOrchestrator for fan-out, pipeline, and master-slave patterns
 * 
 * Prerequisites:
 * 1. Start Nacos: docker run -d -p 8848:8848 nacos/nacos-server:latest
 * 2. Start worker agents:
 *    - WorkerAgentApplication with --agent-name=translator --agent-type=translator --port=8091
 *    - WorkerAgentApplication with --agent-name=analyzer --agent-type=analyzer --port=8092
 *    - WorkerAgentApplication with --agent-name=summarizer --agent-type=summarizer --port=8093
 */
public class OrchestrationDemo {

    public static void main(String[] args) throws Exception {
        String nacosAddr = args.length > 0 ? args[0] : "localhost:8848";

        System.out.println("===========================================");
        System.out.println("Multi-Agent Orchestration Demo");
        System.out.println("===========================================\n");

        // Create registry
        NacosRegistryConfig config = NacosRegistryConfig.builder()
                .serverAddr(nacosAddr)
                .build();
        AgentRegistry registry = new NacosAgentRegistry(config);

        // Create agent client
        AgentClient agentClient = new DefaultAgentClient(registry);

        // Create orchestrator
        AgentOrchestrator orchestrator = new AgentOrchestrator(agentClient);

        try {
            // Demo 1: Discover available agents
            System.out.println("--- Demo 1: Service Discovery ---\n");
            discoverAgents(registry);

            // Demo 2: Fan-out pattern
            System.out.println("\n--- Demo 2: Fan-Out Pattern ---\n");
            demoFanOut(orchestrator);

            // Demo 3: Pipeline pattern
            System.out.println("\n--- Demo 3: Pipeline Pattern ---\n");
            demoPipeline(orchestrator);

            // Demo 4: Master-Slave pattern
            System.out.println("\n--- Demo 4: Master-Slave Pattern ---\n");
            demoMasterSlave(orchestrator);

            // Demo 5: Scatter-Gather pattern
            System.out.println("\n--- Demo 5: Scatter-Gather Pattern ---\n");
            demoScatterGather(orchestrator);

        } finally {
            orchestrator.shutdown();
            registry.close();
        }

        System.out.println("\n===========================================");
        System.out.println("Demo completed successfully!");
        System.out.println("===========================================");
    }

    private static void discoverAgents(AgentRegistry registry) {
        String[] agentNames = {"translator", "analyzer", "summarizer", "master"};

        for (String name : agentNames) {
            try {
                List<AgentInstance> instances = registry.discover(name);
                if (instances.isEmpty()) {
                    System.out.println("  [" + name + "] No instances found");
                } else {
                    for (AgentInstance instance : instances) {
                        System.out.println("  [" + name + "] Found: " + instance.getEndpoint());
                    }
                }
            } catch (Exception e) {
                System.out.println("  [" + name + "] Discovery failed: " + e.getMessage());
            }
        }
    }

    private static void demoFanOut(AgentOrchestrator orchestrator) {
        String input = "Hello, this is a test message for multi-agent processing.";
        System.out.println("Input: " + input);
        System.out.println("Sending to all workers in parallel...\n");

        List<String> workers = List.of("translator", "analyzer", "summarizer");
        Map<String, String> results = orchestrator.fanOut(workers, input);

        System.out.println("Results:");
        for (Map.Entry<String, String> entry : results.entrySet()) {
            System.out.println("  [" + entry.getKey() + "]: " + 
                    truncate(entry.getValue(), 100));
        }
    }

    private static void demoPipeline(AgentOrchestrator orchestrator) {
        String input = "Process this text step by step.";
        System.out.println("Input: " + input);
        System.out.println("Pipeline: translator -> analyzer -> summarizer\n");

        List<String> pipeline = List.of("translator", "analyzer", "summarizer");
        String result = orchestrator.pipeline(pipeline, input);

        System.out.println("Final Result: " + truncate(result, 200));
    }

    private static void demoMasterSlave(AgentOrchestrator orchestrator) {
        String input = "Complex task that requires coordination.";
        System.out.println("Input: " + input);
        System.out.println("Master: master, Slaves: translator, analyzer, summarizer\n");

        List<String> slaves = List.of("translator", "analyzer", "summarizer");
        AgentOrchestrator.MasterSlaveResult result = 
                orchestrator.masterSlave("master", slaves, input);

        System.out.println("Success: " + result.isSuccess());
        if (result.isSuccess()) {
            System.out.println("Final Result: " + truncate(result.getFinalResult(), 200));
        } else {
            System.out.println("Error: " + result.getError());
        }
    }

    private static void demoScatterGather(AgentOrchestrator orchestrator) {
        String input = "Gather results from multiple sources.";
        System.out.println("Input: " + input);
        System.out.println("Strategy: ALL_SUCCESS\n");

        List<String> agents = List.of("translator", "analyzer");
        String result = orchestrator.scatterGather(agents, input, 
                AgentOrchestrator.GatherStrategy.ALL_SUCCESS);

        System.out.println("Gathered Results: " + truncate(result, 300));
    }

    private static String truncate(String text, int maxLength) {
        if (text == null) return "null";
        if (text.length() <= maxLength) return text.replace("\n", " ");
        return text.substring(0, maxLength).replace("\n", " ") + "...";
    }
}
