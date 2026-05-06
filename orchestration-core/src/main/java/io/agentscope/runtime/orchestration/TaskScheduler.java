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

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Interface for task scheduling and execution.
 */
public interface TaskScheduler {

    /**
     * Submit a task for execution.
     *
     * @param task the task to submit
     * @return the task ID
     */
    String submit(Task task);

    /**
     * Submit a task for asynchronous execution.
     *
     * @param task the task to submit
     * @return a CompletableFuture that completes with the task result
     */
    CompletableFuture<Task> submitAsync(Task task);

    /**
     * Submit multiple tasks for parallel execution.
     *
     * @param tasks the tasks to submit
     * @return a list of CompletableFutures for each task
     */
    List<CompletableFuture<Task>> submitBatch(List<Task> tasks);

    /**
     * Submit multiple tasks and wait for all to complete.
     *
     * @param tasks the tasks to submit
     * @return the completed tasks
     */
    List<Task> submitBatchAndWait(List<Task> tasks);

    /**
     * Get the status of a task.
     *
     * @param taskId the task ID
     * @return the task status, or null if not found
     */
    TaskStatus getStatus(String taskId);

    /**
     * Get a task by ID.
     *
     * @param taskId the task ID
     * @return the task, or null if not found
     */
    Task getTask(String taskId);

    /**
     * Cancel a task.
     *
     * @param taskId the task ID
     * @return true if the task was cancelled
     */
    boolean cancel(String taskId);

    /**
     * Shutdown the scheduler.
     */
    void shutdown();
}
