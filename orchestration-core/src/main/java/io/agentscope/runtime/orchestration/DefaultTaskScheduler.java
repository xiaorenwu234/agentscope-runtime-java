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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * Default implementation of TaskScheduler.
 */
public class DefaultTaskScheduler implements TaskScheduler {

    private static final Logger logger = LoggerFactory.getLogger(DefaultTaskScheduler.class);

    private final AgentClient agentClient;

    private final ExecutorService executor;

    private final Map<String, Task> tasks = new ConcurrentHashMap<>();

    private final Map<String, CompletableFuture<Task>> futures = new ConcurrentHashMap<>();

    public DefaultTaskScheduler(AgentClient agentClient) {
        this(agentClient, Runtime.getRuntime().availableProcessors() * 2);
    }

    public DefaultTaskScheduler(AgentClient agentClient, int threadPoolSize) {
        this.agentClient = agentClient;
        this.executor = Executors.newFixedThreadPool(threadPoolSize);
    }

    @Override
    public String submit(Task task) {
        tasks.put(task.getTaskId(), task);
        executeTask(task);
        return task.getTaskId();
    }

    @Override
    public CompletableFuture<Task> submitAsync(Task task) {
        tasks.put(task.getTaskId(), task);
        CompletableFuture<Task> future = CompletableFuture.supplyAsync(() -> {
            executeTask(task);
            return task;
        }, executor);
        futures.put(task.getTaskId(), future);
        return future;
    }

    @Override
    public List<CompletableFuture<Task>> submitBatch(List<Task> taskList) {
        List<CompletableFuture<Task>> result = new ArrayList<>();
        for (Task task : taskList) {
            result.add(submitAsync(task));
        }
        return result;
    }

    @Override
    public List<Task> submitBatchAndWait(List<Task> taskList) {
        List<CompletableFuture<Task>> futureList = submitBatch(taskList);
        return futureList.stream()
                .map(CompletableFuture::join)
                .collect(Collectors.toList());
    }

    @Override
    public TaskStatus getStatus(String taskId) {
        Task task = tasks.get(taskId);
        return task != null ? task.getStatus() : null;
    }

    @Override
    public Task getTask(String taskId) {
        return tasks.get(taskId);
    }

    @Override
    public boolean cancel(String taskId) {
        CompletableFuture<Task> future = futures.get(taskId);
        if (future != null && !future.isDone()) {
            boolean cancelled = future.cancel(true);
            if (cancelled) {
                Task task = tasks.get(taskId);
                if (task != null) {
                    task.cancel();
                }
            }
            return cancelled;
        }
        return false;
    }

    @Override
    public void shutdown() {
        executor.shutdown();
    }

    private void executeTask(Task task) {
        task.start();
        logger.info("Executing task {} to agent {}", task.getTaskId(), task.getTargetAgent());

        try {
            // Check timeout
            if (task.isTimedOut()) {
                task.timeout();
                logger.warn("Task {} timed out before execution", task.getTaskId());
                return;
            }

            // Execute via agent client
            String result = agentClient.send(task.getTargetAgent(), task.getInput());
            task.complete(result);
            logger.info("Task {} completed successfully", task.getTaskId());
        }
        catch (Exception e) {
            task.fail(e.getMessage());
            logger.error("Task {} failed: {}", task.getTaskId(), e.getMessage(), e);
        }
    }
}
