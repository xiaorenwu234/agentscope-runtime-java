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
package io.agentscope.runtime.rpc;

import io.agentscope.runtime.engine.schemas.Event;
import reactor.core.publisher.Flux;

import java.util.concurrent.CompletableFuture;

/**
 * Client interface for invoking remote agents.
 */
public interface AgentClient {

    /**
     * Send a message to an agent and wait for the response (blocking).
     *
     * @param agentName the name of the target agent
     * @param message   the message to send
     * @return the response message
     * @throws AgentClientException if the invocation fails
     */
    String send(String agentName, String message) throws AgentClientException;

    /**
     * Send a message to an agent asynchronously.
     *
     * @param agentName the name of the target agent
     * @param message   the message to send
     * @return a CompletableFuture that completes with the response
     */
    CompletableFuture<String> sendAsync(String agentName, String message);

    /**
     * Send a message to an agent and stream the response.
     *
     * @param agentName the name of the target agent
     * @param message   the message to send
     * @return a Flux of events from the agent
     */
    Flux<Event> stream(String agentName, String message);

    /**
     * Send a message to a specific agent instance.
     *
     * @param endpoint the endpoint URL of the agent (e.g., "http://localhost:8090/a2a/")
     * @param message  the message to send
     * @return the response message
     * @throws AgentClientException if the invocation fails
     */
    String sendToEndpoint(String endpoint, String message) throws AgentClientException;

    /**
     * Stream response from a specific agent instance.
     *
     * @param endpoint the endpoint URL of the agent
     * @param message  the message to send
     * @return a Flux of events from the agent
     */
    Flux<Event> streamFromEndpoint(String endpoint, String message);

    /**
     * Check if an agent is available.
     *
     * @param agentName the name of the agent
     * @return true if at least one instance is available
     */
    boolean isAvailable(String agentName);

    /**
     * Close the client and release resources.
     */
    void close();
}
