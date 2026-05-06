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

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Represents a task to be executed by an agent.
 */
public class Task {

    /**
     * Unique identifier for this task.
     */
    private String taskId;

    /**
     * Name of the target agent to execute this task.
     */
    private String targetAgent;

    /**
     * Input message for the task.
     */
    private String input;

    /**
     * Result of the task execution.
     */
    private String result;

    /**
     * Error message if task failed.
     */
    private String error;

    /**
     * Current status of the task.
     */
    private TaskStatus status;

    /**
     * Task creation timestamp.
     */
    private long createdAt;

    /**
     * Task start timestamp.
     */
    private long startedAt;

    /**
     * Task completion timestamp.
     */
    private long completedAt;

    /**
     * Task timeout in milliseconds (0 means no timeout).
     */
    private long timeout;

    /**
     * Task priority (higher value = higher priority).
     */
    private int priority;

    /**
     * Additional metadata.
     */
    private Map<String, Object> metadata;

    public Task() {
        this.taskId = UUID.randomUUID().toString();
        this.status = TaskStatus.PENDING;
        this.createdAt = System.currentTimeMillis();
        this.metadata = new HashMap<>();
        this.priority = 0;
        this.timeout = 0;
    }

    public Task(String targetAgent, String input) {
        this();
        this.targetAgent = targetAgent;
        this.input = input;
    }

    /**
     * Mark the task as started.
     */
    public void start() {
        this.status = TaskStatus.RUNNING;
        this.startedAt = System.currentTimeMillis();
    }

    /**
     * Mark the task as completed.
     */
    public void complete(String result) {
        this.status = TaskStatus.COMPLETED;
        this.result = result;
        this.completedAt = System.currentTimeMillis();
    }

    /**
     * Mark the task as failed.
     */
    public void fail(String error) {
        this.status = TaskStatus.FAILED;
        this.error = error;
        this.completedAt = System.currentTimeMillis();
    }

    /**
     * Mark the task as cancelled.
     */
    public void cancel() {
        this.status = TaskStatus.CANCELLED;
        this.completedAt = System.currentTimeMillis();
    }

    /**
     * Mark the task as timed out.
     */
    public void timeout() {
        this.status = TaskStatus.TIMEOUT;
        this.completedAt = System.currentTimeMillis();
    }

    /**
     * Check if the task has timed out.
     */
    public boolean isTimedOut() {
        if (timeout <= 0 || startedAt <= 0) {
            return false;
        }
        return System.currentTimeMillis() - startedAt > timeout;
    }

    /**
     * Get the duration of the task in milliseconds.
     */
    public long getDuration() {
        if (startedAt <= 0) {
            return 0;
        }
        long endTime = completedAt > 0 ? completedAt : System.currentTimeMillis();
        return endTime - startedAt;
    }

    // Builder pattern
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private final Task task = new Task();

        public Builder taskId(String taskId) {
            task.taskId = taskId;
            return this;
        }

        public Builder targetAgent(String targetAgent) {
            task.targetAgent = targetAgent;
            return this;
        }

        public Builder input(String input) {
            task.input = input;
            return this;
        }

        public Builder timeout(long timeout) {
            task.timeout = timeout;
            return this;
        }

        public Builder priority(int priority) {
            task.priority = priority;
            return this;
        }

        public Builder metadata(Map<String, Object> metadata) {
            task.metadata = metadata != null ? new HashMap<>(metadata) : new HashMap<>();
            return this;
        }

        public Builder addMetadata(String key, Object value) {
            task.metadata.put(key, value);
            return this;
        }

        public Task build() {
            return task;
        }
    }

    // Getters and Setters
    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public String getTargetAgent() {
        return targetAgent;
    }

    public void setTargetAgent(String targetAgent) {
        this.targetAgent = targetAgent;
    }

    public String getInput() {
        return input;
    }

    public void setInput(String input) {
        this.input = input;
    }

    public String getResult() {
        return result;
    }

    public void setResult(String result) {
        this.result = result;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public TaskStatus getStatus() {
        return status;
    }

    public void setStatus(TaskStatus status) {
        this.status = status;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public long getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(long startedAt) {
        this.startedAt = startedAt;
    }

    public long getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(long completedAt) {
        this.completedAt = completedAt;
    }

    public long getTimeout() {
        return timeout;
    }

    public void setTimeout(long timeout) {
        this.timeout = timeout;
    }

    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }

    @Override
    public String toString() {
        return "Task{" +
                "taskId='" + taskId + '\'' +
                ", targetAgent='" + targetAgent + '\'' +
                ", status=" + status +
                ", duration=" + getDuration() + "ms" +
                '}';
    }
}
