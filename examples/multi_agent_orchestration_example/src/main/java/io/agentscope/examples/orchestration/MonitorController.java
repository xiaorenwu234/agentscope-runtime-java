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
import io.agentscope.runtime.orchestration.AgentOrchestrator.GatherStrategy;
import io.agentscope.runtime.orchestration.AgentOrchestrator.MasterSlaveResult;
import io.agentscope.runtime.rpc.AgentClient;
import io.agentscope.runtime.rpc.DefaultAgentClient;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * REST Controller for Multi-Agent Orchestration Monitoring.
 * Provides APIs for frontend visualization.
 */
@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class MonitorController {

    private static final Logger logger = LoggerFactory.getLogger(MonitorController.class);

    @Value("${nacos.server-addr:localhost:8848}")
    private String nacosAddr;

    private AgentRegistry registry;
    private AgentClient agentClient;
    private AgentOrchestrator orchestrator;

    // Task history storage
    private final List<TaskRecord> taskHistory = new CopyOnWriteArrayList<>();
    private final Map<String, TaskRecord> runningTasks = new ConcurrentHashMap<>();

    // Known agent names to discover
    private static final List<String> AGENT_NAMES = Arrays.asList(
            "master", "translator", "analyzer", "summarizer"
    );

    @PostConstruct
    public void init() {
        logger.info("Initializing MonitorController with Nacos: {}", nacosAddr);
        try {
            NacosRegistryConfig config = NacosRegistryConfig.builder()
                    .serverAddr(nacosAddr)
                    .build();
            registry = new NacosAgentRegistry(config);
            agentClient = new DefaultAgentClient(registry);
            orchestrator = new AgentOrchestrator(agentClient);
            logger.info("MonitorController initialized successfully");
        } catch (Exception e) {
            logger.error("Failed to initialize MonitorController: {}", e.getMessage());
        }
    }

    @PreDestroy
    public void cleanup() {
        if (orchestrator != null) {
            orchestrator.shutdown();
        }
        if (registry != null) {
            registry.close();
        }
    }

    /**
     * Get all registered agents.
     */
    @GetMapping("/agents")
    public List<AgentInfo> getAgents() {
        List<AgentInfo> agents = new ArrayList<>();

        for (String agentName : AGENT_NAMES) {
            try {
                List<AgentInstance> instances = registry.discover(agentName);
                if (!instances.isEmpty()) {
                    AgentInstance instance = instances.get(0);
                    agents.add(new AgentInfo(
                            instance.getInstanceId(),
                            agentName,
                            getAgentType(agentName),
                            "online",
                            instance.getHost(),
                            instance.getPort(),
                            null
                    ));
                } else {
                    // Agent not registered, show as offline
                    agents.add(new AgentInfo(
                            agentName + "-offline",
                            agentName,
                            getAgentType(agentName),
                            "offline",
                            "unknown",
                            0,
                            null
                    ));
                }
            } catch (Exception e) {
                logger.warn("Failed to discover agent {}: {}", agentName, e.getMessage());
                agents.add(new AgentInfo(
                        agentName + "-error",
                        agentName,
                        getAgentType(agentName),
                        "offline",
                        "unknown",
                        0,
                        null
                ));
            }
        }

        return agents;
    }

    /**
     * Execute orchestration demo.
     */
    @PostMapping("/demo/{mode}")
    public TaskRecord executeDemo(
            @PathVariable String mode,
            @RequestBody DemoRequest request) {

        String taskId = UUID.randomUUID().toString();
        long startTime = System.currentTimeMillis();

        TaskRecord record = new TaskRecord();
        record.setId(taskId);
        record.setMode(mode);
        record.setInput(request.getInput());
        record.setStartTime(startTime);
        record.setStatus("running");
        record.setEvents(new ArrayList<>());

        runningTasks.put(taskId, record);

        // Add initial event
        addEvent(record, "request_received", null, null,
                "收到请求: " + truncate(request.getInput(), 50));

        try {
            List<String> workers = Arrays.asList("translator", "analyzer", "summarizer");
            String result;

            switch (mode.toLowerCase()) {
                case "fanout":
                    addEvent(record, "task_dispatched", null, null,
                            "并行分发任务到 " + workers.size() + " 个 Worker");

                    // Mark workers as starting
                    for (String worker : workers) {
                        addEvent(record, "worker_start", worker, capitalize(worker),
                                "开始处理任务");
                    }

                    Map<String, String> fanoutResults = orchestrator.fanOut(workers, request.getInput());

                    // Mark workers as complete
                    for (String worker : workers) {
                        String workerResult = fanoutResults.get(worker);
                        addEvent(record, "worker_complete", worker, capitalize(worker),
                                "处理完成", workerResult);
                    }

                    result = formatResults(fanoutResults);
                    break;

                case "pipeline":
                    for (int i = 0; i < workers.size(); i++) {
                        String worker = workers.get(i);
                        addEvent(record, "task_dispatched", null, null,
                                "传递任务到 " + capitalize(worker));
                        addEvent(record, "worker_start", worker, capitalize(worker),
                                "开始处理");
                    }

                    result = orchestrator.pipeline(workers, request.getInput());

                    for (String worker : workers) {
                        addEvent(record, "worker_complete", worker, capitalize(worker),
                                "处理完成，传递给下一个");
                    }
                    break;

                case "master-slave":
                    addEvent(record, "task_dispatched", null, null,
                            "Master 协调分配任务");

                    for (String worker : workers) {
                        addEvent(record, "worker_start", worker, capitalize(worker),
                                "Slave 开始执行");
                    }

                    MasterSlaveResult msResult = orchestrator.masterSlave("master", workers, request.getInput());

                    for (String worker : workers) {
                        addEvent(record, "worker_complete", worker, capitalize(worker),
                                "向 Master 报告结果");
                    }

                    result = msResult.isSuccess() ? msResult.getFinalResult() : "ERROR: " + msResult.getError();
                    break;

                case "scatter-gather":
                    addEvent(record, "task_dispatched", null, null,
                            "散布请求到 " + workers.size() + " 个 Worker");

                    for (String worker : workers) {
                        addEvent(record, "worker_start", worker, capitalize(worker),
                                "开始处理请求");
                    }

                    result = orchestrator.scatterGather(workers, request.getInput(), GatherStrategy.CONCATENATE);

                    for (String worker : workers) {
                        addEvent(record, "worker_complete", worker, capitalize(worker),
                                "返回处理结果");
                    }
                    break;

                default:
                    throw new IllegalArgumentException("Unknown mode: " + mode);
            }

            // Aggregation and response
            addEvent(record, "result_aggregated", null, null,
                    "聚合 " + workers.size() + " 个 Worker 的结果");
            addEvent(record, "response_sent", null, null,
                    "返回最终结果");

            record.setOutput(result);
            record.setStatus("completed");

        } catch (Exception e) {
            logger.error("Demo execution failed: {}", e.getMessage(), e);
            addEvent(record, "response_sent", null, null,
                    "执行失败: " + e.getMessage());
            record.setOutput("ERROR: " + e.getMessage());
            record.setStatus("failed");
        }

        record.setEndTime(System.currentTimeMillis());
        runningTasks.remove(taskId);
        taskHistory.add(0, record);

        // Keep only last 20 tasks
        while (taskHistory.size() > 20) {
            taskHistory.remove(taskHistory.size() - 1);
        }

        return record;
    }

    /**
     * Get task history.
     */
    @GetMapping("/tasks")
    public List<TaskRecord> getTasks() {
        return taskHistory;
    }

    /**
     * Get a specific task.
     */
    @GetMapping("/tasks/{taskId}")
    public TaskRecord getTask(@PathVariable String taskId) {
        TaskRecord running = runningTasks.get(taskId);
        if (running != null) {
            return running;
        }
        return taskHistory.stream()
                .filter(t -> t.getId().equals(taskId))
                .findFirst()
                .orElse(null);
    }

    /**
     * Health check endpoint.
     */
    @GetMapping("/health")
    public Map<String, Object> health() {
        Map<String, Object> status = new HashMap<>();
        status.put("status", "UP");
        status.put("nacosConnected", registry != null && registry.isHealthy());
        status.put("timestamp", System.currentTimeMillis());
        return status;
    }

    // Helper methods

    private void addEvent(TaskRecord record, String type, String agentId, String agentName, String message) {
        addEvent(record, type, agentId, agentName, message, null);
    }

    private void addEvent(TaskRecord record, String type, String agentId, String agentName, String message, String details) {
        TaskEvent event = new TaskEvent();
        event.setId(UUID.randomUUID().toString());
        event.setTimestamp(System.currentTimeMillis());
        event.setType(type);
        event.setAgentId(agentId);
        event.setAgentName(agentName);
        event.setMessage(message);
        event.setDetails(details);
        record.getEvents().add(event);
    }

    private String getAgentType(String agentName) {
        return switch (agentName.toLowerCase()) {
            case "master" -> "master";
            case "translator" -> "translator";
            case "analyzer" -> "analyzer";
            case "summarizer" -> "summarizer";
            default -> "worker";
        };
    }

    private String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return s.substring(0, 1).toUpperCase() + s.substring(1);
    }

    private String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() > maxLen ? s.substring(0, maxLen) + "..." : s;
    }

    private String formatResults(Map<String, String> results) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== Fan-Out Results ===\n");
        for (Map.Entry<String, String> entry : results.entrySet()) {
            sb.append("[").append(entry.getKey()).append("]: ").append(entry.getValue()).append("\n");
        }
        return sb.toString();
    }

    // DTO classes

    public static class AgentInfo {
        private String id;
        private String name;
        private String type;
        private String status;
        private String host;
        private int port;
        private String currentTask;

        public AgentInfo() {}

        public AgentInfo(String id, String name, String type, String status, String host, int port, String currentTask) {
            this.id = id;
            this.name = name;
            this.type = type;
            this.status = status;
            this.host = host;
            this.port = port;
            this.currentTask = currentTask;
        }

        // Getters and setters
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public String getHost() { return host; }
        public void setHost(String host) { this.host = host; }
        public int getPort() { return port; }
        public void setPort(int port) { this.port = port; }
        public String getCurrentTask() { return currentTask; }
        public void setCurrentTask(String currentTask) { this.currentTask = currentTask; }
    }

    public static class DemoRequest {
        private String input;

        public String getInput() { return input; }
        public void setInput(String input) { this.input = input; }
    }

    public static class TaskRecord {
        private String id;
        private String mode;
        private String input;
        private String output;
        private long startTime;
        private Long endTime;
        private String status;
        private List<TaskEvent> events;

        // Getters and setters
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getMode() { return mode; }
        public void setMode(String mode) { this.mode = mode; }
        public String getInput() { return input; }
        public void setInput(String input) { this.input = input; }
        public String getOutput() { return output; }
        public void setOutput(String output) { this.output = output; }
        public long getStartTime() { return startTime; }
        public void setStartTime(long startTime) { this.startTime = startTime; }
        public Long getEndTime() { return endTime; }
        public void setEndTime(Long endTime) { this.endTime = endTime; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public List<TaskEvent> getEvents() { return events; }
        public void setEvents(List<TaskEvent> events) { this.events = events; }
    }

    public static class TaskEvent {
        private String id;
        private long timestamp;
        private String type;
        private String agentId;
        private String agentName;
        private String message;
        private String details;

        // Getters and setters
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public long getTimestamp() { return timestamp; }
        public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public String getAgentId() { return agentId; }
        public void setAgentId(String agentId) { this.agentId = agentId; }
        public String getAgentName() { return agentName; }
        public void setAgentName(String agentName) { this.agentName = agentName; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
        public String getDetails() { return details; }
        public void setDetails(String details) { this.details = details; }
    }
}
