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

import com.fasterxml.jackson.databind.JsonNode;
import io.a2a.spec.Message;
import io.a2a.spec.MessageSendParams;
import io.a2a.spec.SendMessageRequest;
import io.a2a.spec.SendStreamingMessageRequest;
import io.a2a.spec.TextPart;
import io.a2a.util.Utils;
import io.agentscope.runtime.cluster.AgentInstance;
import io.agentscope.runtime.cluster.AgentRegistry;
import io.agentscope.runtime.engine.schemas.Event;
import io.agentscope.runtime.engine.schemas.TextContent;
import io.agentscope.runtime.rpc.loadbalance.LoadBalancer;
import io.agentscope.runtime.rpc.loadbalance.RoundRobinLoadBalancer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Default implementation of AgentClient.
 * Uses A2A protocol for remote agent invocation.
 */
public class DefaultAgentClient implements AgentClient {

    private static final Logger logger = LoggerFactory.getLogger(DefaultAgentClient.class);

    private final AgentRegistry registry;

    private final LoadBalancer loadBalancer;

    private final ExecutorService executor;

    private final int connectTimeout;

    private final int readTimeout;

    public DefaultAgentClient(AgentRegistry registry) {
        this(registry, new RoundRobinLoadBalancer());
    }

    public DefaultAgentClient(AgentRegistry registry, LoadBalancer loadBalancer) {
        this(registry, loadBalancer, 5000, 60000);
    }

    public DefaultAgentClient(AgentRegistry registry, LoadBalancer loadBalancer,
            int connectTimeout, int readTimeout) {
        this.registry = registry;
        this.loadBalancer = loadBalancer;
        this.executor = Executors.newCachedThreadPool();
        this.connectTimeout = connectTimeout;
        this.readTimeout = readTimeout;
    }

    @Override
    public String send(String agentName, String message) throws AgentClientException {
        AgentInstance instance = selectInstance(agentName);
        if (instance == null) {
            throw new AgentClientException(agentName, null, "No available instance for agent: " + agentName);
        }

        return sendToEndpoint(instance.getA2aEndpoint(), message);
    }

    @Override
    public CompletableFuture<String> sendAsync(String agentName, String message) {
        return CompletableFuture.supplyAsync(() -> send(agentName, message), executor);
    }

    @Override
    public Flux<Event> stream(String agentName, String message) {
        AgentInstance instance = selectInstance(agentName);
        if (instance == null) {
            return Flux.error(new AgentClientException(agentName, null,
                    "No available instance for agent: " + agentName));
        }

        return streamFromEndpoint(instance.getA2aEndpoint(), message);
    }

    @Override
    public String sendToEndpoint(String endpoint, String message) throws AgentClientException {
        try {
            String requestBody = buildA2aRequest(message, false);
            String response = doHttpPost(endpoint, requestBody);
            return extractResponseText(response);
        }
        catch (Exception e) {
            throw new AgentClientException(null, endpoint, "Failed to send message to " + endpoint, e);
        }
    }

    @Override
    public Flux<Event> streamFromEndpoint(String endpoint, String message) {
        return Flux.create(sink -> {
            executor.submit(() -> {
                try {
                    doStreamRequest(endpoint, message, sink);
                }
                catch (Exception e) {
                    sink.error(new AgentClientException(null, endpoint,
                            "Failed to stream from " + endpoint, e));
                }
            });
        });
    }

    @Override
    public boolean isAvailable(String agentName) {
        try {
            List<AgentInstance> instances = registry.discover(agentName);
            return instances != null && !instances.isEmpty();
        }
        catch (Exception e) {
            logger.warn("Failed to check availability for agent: {}", agentName, e);
            return false;
        }
    }

    @Override
    public void close() {
        executor.shutdown();
    }

    private AgentInstance selectInstance(String agentName) {
        List<AgentInstance> instances = registry.discover(agentName);
        if (instances == null || instances.isEmpty()) {
            return null;
        }
        return loadBalancer.select(instances);
    }

    /**
     * Build A2A request using official A2A SDK.
     * This ensures correct JSON serialization format.
     */
    private String buildA2aRequest(String text, boolean streaming) {
        try {
            // Create TextPart using A2A SDK
            TextPart textPart = new TextPart(text);

            // Create Message using A2A SDK Builder
            Message message = new Message.Builder()
                    .parts(List.of(textPart))
                    .role(Message.Role.USER)
                    .build();

            String requestId = UUID.randomUUID().toString();

            // Create params using Builder
            MessageSendParams params = new MessageSendParams.Builder()
                    .message(message)
                    .build();

            if (streaming) {
                // Create streaming request using Builder
                SendStreamingMessageRequest request = new SendStreamingMessageRequest.Builder()
                        .jsonrpc("2.0")
                        .method(SendStreamingMessageRequest.METHOD)
                        .id(requestId)
                        .params(params)
                        .build();
                return Utils.OBJECT_MAPPER.writeValueAsString(request);
            } else {
                // Create non-streaming request using Builder
                SendMessageRequest request = new SendMessageRequest.Builder()
                        .jsonrpc("2.0")
                        .method(SendMessageRequest.METHOD)
                        .id(requestId)
                        .params(params)
                        .build();
                return Utils.OBJECT_MAPPER.writeValueAsString(request);
            }
        } catch (Exception e) {
            logger.error("Failed to build A2A request", e);
            throw new RuntimeException("Failed to build A2A request", e);
        }
    }

    private String doHttpPost(String endpoint, String body) throws Exception {
        URL url = new URL(endpoint);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setConnectTimeout(connectTimeout);
        conn.setReadTimeout(readTimeout);
        conn.setDoOutput(true);

        try (OutputStream os = conn.getOutputStream()) {
            os.write(body.getBytes(StandardCharsets.UTF_8));
        }

        int responseCode = conn.getResponseCode();
        if (responseCode != 200) {
            throw new AgentClientException("HTTP error: " + responseCode);
        }

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
            return response.toString();
        }
    }

    private void doStreamRequest(String endpoint, String message, FluxSink<Event> sink) throws Exception {
        String requestBody = buildA2aRequest(message, true);

        URL url = new URL(endpoint);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Accept", "text/event-stream");
        conn.setConnectTimeout(connectTimeout);
        conn.setReadTimeout(readTimeout);
        conn.setDoOutput(true);

        try (OutputStream os = conn.getOutputStream()) {
            os.write(requestBody.getBytes(StandardCharsets.UTF_8));
        }

        int responseCode = conn.getResponseCode();
        if (responseCode != 200) {
            sink.error(new AgentClientException("HTTP error: " + responseCode));
            return;
        }

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            StringBuilder eventData = new StringBuilder();

            while ((line = reader.readLine()) != null) {
                if (line.startsWith("data:")) {
                    eventData.append(line.substring(5).trim());
                }
                else if (line.isEmpty() && eventData.length() > 0) {
                    // End of event
                    String data = eventData.toString();
                    eventData.setLength(0);

                    try {
                        Event event = parseEventData(data);
                        if (event != null) {
                            sink.next(event);
                        }
                    }
                    catch (Exception e) {
                        logger.warn("Failed to parse event data: {}", data, e);
                    }
                }
            }
        }

        sink.complete();
    }

    private String extractResponseText(String response) {
        try {
            JsonNode root = Utils.OBJECT_MAPPER.readTree(response);
            JsonNode result = root.get("result");
            if (result != null) {
                // Try to extract text from task artifacts or message parts
                JsonNode status = result.get("status");
                if (status != null && status.has("message")) {
                    JsonNode message = status.get("message");
                    if (message.has("parts")) {
                        JsonNode parts = message.get("parts");
                        if (parts.isArray() && parts.size() > 0) {
                            JsonNode firstPart = parts.get(0);
                            if (firstPart.has("text")) {
                                return firstPart.get("text").asText();
                            }
                        }
                    }
                }
            }
            return response;
        }
        catch (Exception e) {
            logger.warn("Failed to extract response text", e);
            return response;
        }
    }

    private Event parseEventData(String data) {
        try {
            JsonNode root = Utils.OBJECT_MAPPER.readTree(data);
            JsonNode result = root.get("result");
            if (result != null && result.has("artifact")) {
                JsonNode artifact = result.get("artifact");
                if (artifact.has("parts")) {
                    JsonNode parts = artifact.get("parts");
                    if (parts.isArray() && parts.size() > 0) {
                        JsonNode firstPart = parts.get(0);
                        if (firstPart.has("text")) {
                            TextContent textContent = new TextContent();
                            textContent.setText(firstPart.get("text").asText());
                            return textContent;
                        }
                    }
                }
            }
            return null;
        }
        catch (Exception e) {
            logger.warn("Failed to parse event data: {}", data, e);
            return null;
        }
    }
}
