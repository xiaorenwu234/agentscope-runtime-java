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

import io.agentscope.runtime.app.AgentApp;

/**
 * Worker Agent Application.
 * Starts a worker agent that registers to Nacos for service discovery.
 *
 * Usage:
 *   java -jar worker-agent.jar --port=8091 --agent-name=translator --agent-type=translator
 *   java -jar worker-agent.jar --port=8092 --agent-name=analyzer --agent-type=analyzer
 *   java -jar worker-agent.jar --port=8093 --agent-name=summarizer --agent-type=summarizer
 */
public class WorkerAgentApplication {

    public static void main(String[] args) {
        // Parse command line arguments
        int port = getIntArg(args, "--port", 8091);
        String agentName = getStringArg(args, "--agent-name", "worker");
        String agentType = getStringArg(args, "--agent-type", "default");
        String nacosAddr = getStringArg(args, "--nacos-addr", "localhost:8848");

        System.out.println("========================================");
        System.out.println("Starting Worker Agent");
        System.out.println("  Name: " + agentName);
        System.out.println("  Type: " + agentType);
        System.out.println("  Port: " + port);
        System.out.println("  Nacos: " + nacosAddr);
        System.out.println("========================================");

        // Create worker agent
        SimpleWorkerAgent workerAgent = new SimpleWorkerAgent(agentName, agentType);

        // Create and configure AgentApp
        AgentApp app = new AgentApp(workerAgent);

        // Enable cluster mode with Nacos using the new API
        app.cluster(true, nacosAddr);

        // Run the application
        app.run(port);
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
