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
package io.agentscope.runtime.orchestration;

import io.agentscope.runtime.rpc.AgentClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Orchestrator for coordinating multiple agents.
 * Supports various coordination patterns like fan-out, pipeline, and master-slave.
 */
public class AgentOrchestrator {

    private static final Logger logger = LoggerFactory.getLogger(AgentOrchestrator.class);

    private final AgentClient agentClient;

    private final TaskScheduler scheduler;

    public AgentOrchestrator(AgentClient agentClient) {
        this.agentClient = agentClient;
        this.scheduler = new DefaultTaskScheduler(agentClient);
    }

    public AgentOrchestrator(AgentClient agentClient, TaskScheduler scheduler) {
        this.agentClient = agentClient;
        this.scheduler = scheduler;
    }

    /**
     * Fan-out: Send the same request to multiple agents in parallel and collect results.
     *
     * @param agentNames the names of agents to invoke
     * @param input      the input message
     * @return map of agent name to result
     */
    public Map<String, String> fanOut(List<String> agentNames, String input) {
        logger.info("Fan-out to {} agents", agentNames.size());

        List<Task> tasks = agentNames.stream()
                .map(agent -> Task.builder()
                        .targetAgent(agent)
                        .input(input)
                        .build())
                .collect(Collectors.toList());

        List<Task> results = scheduler.submitBatchAndWait(tasks);

        Map<String, String> resultMap = new HashMap<>();
        for (Task task : results) {
            if (task.getStatus() == TaskStatus.COMPLETED) {
                resultMap.put(task.getTargetAgent(), task.getResult());
            }
            else {
                resultMap.put(task.getTargetAgent(), "ERROR: " + task.getError());
            }
        }

        return resultMap;
    }

    /**
     * Fan-out async: Send the same request to multiple agents in parallel.
     *
     * @param agentNames the names of agents to invoke
     * @param input      the input message
     * @return list of CompletableFutures for each agent
     */
    public List<CompletableFuture<Task>> fanOutAsync(List<String> agentNames, String input) {
        logger.info("Fan-out async to {} agents", agentNames.size());

        List<Task> tasks = agentNames.stream()
                .map(agent -> Task.builder()
                        .targetAgent(agent)
                        .input(input)
                        .build())
                .collect(Collectors.toList());

        return scheduler.submitBatch(tasks);
    }

    /**
     * Pipeline: Execute agents sequentially, passing output of one as input to the next.
     *
     * @param agentNames the ordered list of agents to execute
     * @param input      the initial input
     * @return the final result
     */
    public String pipeline(List<String> agentNames, String input) {
        logger.info("Pipeline through {} agents", agentNames.size());

        String currentInput = input;
        for (String agentName : agentNames) {
            logger.info("Pipeline step: invoking {}", agentName);
            try {
                currentInput = agentClient.send(agentName, currentInput);
            }
            catch (Exception e) {
                logger.error("Pipeline failed at {}: {}", agentName, e.getMessage());
                throw new RuntimeException("Pipeline failed at " + agentName, e);
            }
        }

        return currentInput;
    }

    /**
     * Master-Slave: A master agent coordinates multiple slave agents.
     * The master receives the request, distributes sub-tasks to slaves,
     * and aggregates the results.
     *
     * @param masterAgent the master agent name
     * @param slaveAgents the slave agent names
     * @param input       the input message
     * @return the aggregated result from master
     */
    public MasterSlaveResult masterSlave(String masterAgent, List<String> slaveAgents, String input) {
        logger.info("Master-Slave: master={}, slaves={}", masterAgent, slaveAgents);

        MasterSlaveResult result = new MasterSlaveResult();
        result.setMasterAgent(masterAgent);
        result.setSlaveAgents(slaveAgents);
        result.setInput(input);

        try {
            // Step 1: Master analyzes the task and potentially splits it
            String masterAnalysis = agentClient.send(masterAgent,
                    "ANALYZE_AND_DISTRIBUTE: " + input + "\nAvailable workers: " + slaveAgents);
            result.setMasterAnalysis(masterAnalysis);

            // Step 2: Fan-out to all slaves in parallel
            Map<String, String> slaveResults = fanOut(slaveAgents, input);
            result.setSlaveResults(slaveResults);

            // Step 3: Master aggregates results
            StringBuilder aggregationInput = new StringBuilder();
            aggregationInput.append("AGGREGATE_RESULTS:\n");
            aggregationInput.append("Original request: ").append(input).append("\n\n");
            aggregationInput.append("Worker results:\n");
            for (Map.Entry<String, String> entry : slaveResults.entrySet()) {
                aggregationInput.append("- ").append(entry.getKey()).append(": ")
                        .append(entry.getValue()).append("\n");
            }

            String finalResult = agentClient.send(masterAgent, aggregationInput.toString());
            result.setFinalResult(finalResult);
            result.setSuccess(true);

        }
        catch (Exception e) {
            logger.error("Master-Slave execution failed: {}", e.getMessage(), e);
            result.setSuccess(false);
            result.setError(e.getMessage());
        }

        return result;
    }

    /**
     * Scatter-Gather: Scatter a request to multiple agents and gather responses.
     * Similar to fan-out but with configurable gathering strategy.
     *
     * @param agentNames     the agents to scatter to
     * @param input          the input message
     * @param gatherStrategy the strategy for gathering results
     * @return the gathered result
     */
    public String scatterGather(List<String> agentNames, String input, GatherStrategy gatherStrategy) {
        logger.info("Scatter-Gather to {} agents with strategy {}", agentNames.size(), gatherStrategy);

        Map<String, String> results = fanOut(agentNames, input);

        return switch (gatherStrategy) {
            case FIRST_SUCCESS -> results.values().stream()
                    .filter(r -> !r.startsWith("ERROR:"))
                    .findFirst()
                    .orElse("No successful result");
            case ALL_SUCCESS -> results.values().stream()
                    .filter(r -> !r.startsWith("ERROR:"))
                    .collect(Collectors.joining("\n---\n"));
            case MAJORITY -> getMajorityResult(results);
            case CONCATENATE -> String.join("\n---\n", results.values());
        };
    }

    private String getMajorityResult(Map<String, String> results) {
        // Simple majority: return most common result
        Map<String, Long> counts = results.values().stream()
                .collect(Collectors.groupingBy(r -> r, Collectors.counting()));
        return counts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("");
    }

    /**
     * Shutdown the orchestrator.
     */
    public void shutdown() {
        scheduler.shutdown();
        agentClient.close();
    }

    /**
     * Strategy for gathering results from multiple agents.
     */
    public enum GatherStrategy {

        /**
         * Return the first successful result.
         */
        FIRST_SUCCESS,
        /**
         * Return all successful results concatenated.
         */
        ALL_SUCCESS,
        /**
         * Return the most common result (majority voting).
         */
        MAJORITY,
        /**
         * Concatenate all results.
         */
        CONCATENATE
    }

    /**
     * Result of master-slave execution.
     */
    public static class MasterSlaveResult {

        private String masterAgent;

        private List<String> slaveAgents;

        private String input;

        private String masterAnalysis;

        private Map<String, String> slaveResults;

        private String finalResult;

        private boolean success;

        private String error;

        // Getters and Setters
        public String getMasterAgent() {
            return masterAgent;
        }

        public void setMasterAgent(String masterAgent) {
            this.masterAgent = masterAgent;
        }

        public List<String> getSlaveAgents() {
            return slaveAgents;
        }

        public void setSlaveAgents(List<String> slaveAgents) {
            this.slaveAgents = slaveAgents;
        }

        public String getInput() {
            return input;
        }

        public void setInput(String input) {
            this.input = input;
        }

        public String getMasterAnalysis() {
            return masterAnalysis;
        }

        public void setMasterAnalysis(String masterAnalysis) {
            this.masterAnalysis = masterAnalysis;
        }

        public Map<String, String> getSlaveResults() {
            return slaveResults;
        }

        public void setSlaveResults(Map<String, String> slaveResults) {
            this.slaveResults = slaveResults;
        }

        public String getFinalResult() {
            return finalResult;
        }

        public void setFinalResult(String finalResult) {
            this.finalResult = finalResult;
        }

        public boolean isSuccess() {
            return success;
        }

        public void setSuccess(boolean success) {
            this.success = success;
        }

        public String getError() {
            return error;
        }

        public void setError(String error) {
            this.error = error;
        }

        @Override
        public String toString() {
            return "MasterSlaveResult{" +
                    "masterAgent='" + masterAgent + '\'' +
                    ", slaveAgents=" + slaveAgents +
                    ", success=" + success +
                    ", finalResult='" + finalResult + '\'' +
                    '}';
        }
    }
}