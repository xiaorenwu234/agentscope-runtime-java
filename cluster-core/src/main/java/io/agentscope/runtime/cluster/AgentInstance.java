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
package io.agentscope.runtime.cluster;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Represents an Agent instance in the cluster.
 * Contains all necessary information for service registration and discovery.
 */
public class AgentInstance {

    /**
     * Unique identifier for this instance.
     */
    private String instanceId;

    /**
     * Name of the agent (service name for discovery).
     */
    private String agentName;

    /**
     * Host address of the instance.
     */
    private String host;

    /**
     * Port number of the instance.
     */
    private int port;

    /**
     * Weight for load balancing (default: 1.0).
     */
    private double weight = 1.0;

    /**
     * Metadata for additional information (capabilities, version, etc.).
     */
    private Map<String, String> metadata;

    /**
     * Current status of the instance.
     */
    private AgentStatus status;

    /**
     * Timestamp when this instance was registered.
     */
    private long registrationTime;

    /**
     * Last heartbeat timestamp.
     */
    private long lastHeartbeat;

    public AgentInstance() {
        this.instanceId = UUID.randomUUID().toString();
        this.metadata = new HashMap<>();
        this.status = AgentStatus.STARTING;
        this.registrationTime = System.currentTimeMillis();
        this.lastHeartbeat = this.registrationTime;
    }

    public AgentInstance(String agentName, String host, int port) {
        this();
        this.agentName = agentName;
        this.host = host;
        this.port = port;
    }

    /**
     * Get the endpoint URL for this instance.
     * @return endpoint URL in format "http://host:port"
     */
    public String getEndpoint() {
        return String.format("http://%s:%d", host, port);
    }

    /**
     * Get the A2A endpoint URL for this instance.
     * @return A2A endpoint URL in format "http://host:port/a2a/"
     */
    public String getA2aEndpoint() {
        return String.format("http://%s:%d/a2a/", host, port);
    }

    // Builder pattern for fluent API
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private final AgentInstance instance = new AgentInstance();

        public Builder instanceId(String instanceId) {
            instance.instanceId = instanceId;
            return this;
        }

        public Builder agentName(String agentName) {
            instance.agentName = agentName;
            return this;
        }

        public Builder host(String host) {
            instance.host = host;
            return this;
        }

        public Builder port(int port) {
            instance.port = port;
            return this;
        }

        public Builder weight(double weight) {
            instance.weight = weight;
            return this;
        }

        public Builder metadata(Map<String, String> metadata) {
            instance.metadata = metadata != null ? new HashMap<>(metadata) : new HashMap<>();
            return this;
        }

        public Builder addMetadata(String key, String value) {
            instance.metadata.put(key, value);
            return this;
        }

        public Builder status(AgentStatus status) {
            instance.status = status;
            return this;
        }

        public AgentInstance build() {
            Objects.requireNonNull(instance.agentName, "agentName is required");
            Objects.requireNonNull(instance.host, "host is required");
            if (instance.port <= 0) {
                throw new IllegalArgumentException("port must be positive");
            }
            return instance;
        }
    }

    // Getters and Setters
    public String getInstanceId() {
        return instanceId;
    }

    public void setInstanceId(String instanceId) {
        this.instanceId = instanceId;
    }

    public String getAgentName() {
        return agentName;
    }

    public void setAgentName(String agentName) {
        this.agentName = agentName;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public double getWeight() {
        return weight;
    }

    public void setWeight(double weight) {
        this.weight = weight;
    }

    public Map<String, String> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, String> metadata) {
        this.metadata = metadata;
    }

    public AgentStatus getStatus() {
        return status;
    }

    public void setStatus(AgentStatus status) {
        this.status = status;
    }

    public long getRegistrationTime() {
        return registrationTime;
    }

    public void setRegistrationTime(long registrationTime) {
        this.registrationTime = registrationTime;
    }

    public long getLastHeartbeat() {
        return lastHeartbeat;
    }

    public void setLastHeartbeat(long lastHeartbeat) {
        this.lastHeartbeat = lastHeartbeat;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        AgentInstance that = (AgentInstance) o;
        return Objects.equals(instanceId, that.instanceId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(instanceId);
    }

    @Override
    public String toString() {
        return "AgentInstance{" +
                "instanceId='" + instanceId + '\'' +
                ", agentName='" + agentName + '\'' +
                ", host='" + host + '\'' +
                ", port=" + port +
                ", status=" + status +
                '}';
    }
}
