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
package io.agentscope.runtime.cluster.nacos;

/**
 * Configuration properties for Nacos registry.
 */
public class NacosRegistryConfig {

    /**
     * Nacos server address (e.g., "localhost:8848").
     */
    private String serverAddr = "localhost:8848";

    /**
     * Nacos namespace (default: public).
     */
    private String namespace = "public";

    /**
     * Nacos group name.
     */
    private String group = "DEFAULT_GROUP";

    /**
     * Username for authentication (optional).
     */
    private String username;

    /**
     * Password for authentication (optional).
     */
    private String password;

    /**
     * Heartbeat interval in milliseconds.
     */
    private long heartbeatInterval = 5000;

    /**
     * Instance timeout in milliseconds.
     */
    private long instanceTimeout = 15000;

    public static NacosRegistryConfig defaultConfig() {
        return new NacosRegistryConfig();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private final NacosRegistryConfig config = new NacosRegistryConfig();

        public Builder serverAddr(String serverAddr) {
            config.serverAddr = serverAddr;
            return this;
        }

        public Builder namespace(String namespace) {
            config.namespace = namespace;
            return this;
        }

        public Builder group(String group) {
            config.group = group;
            return this;
        }

        public Builder username(String username) {
            config.username = username;
            return this;
        }

        public Builder password(String password) {
            config.password = password;
            return this;
        }

        public Builder heartbeatInterval(long heartbeatInterval) {
            config.heartbeatInterval = heartbeatInterval;
            return this;
        }

        public Builder instanceTimeout(long instanceTimeout) {
            config.instanceTimeout = instanceTimeout;
            return this;
        }

        public NacosRegistryConfig build() {
            return config;
        }
    }

    // Getters and Setters
    public String getServerAddr() {
        return serverAddr;
    }

    public void setServerAddr(String serverAddr) {
        this.serverAddr = serverAddr;
    }

    public String getNamespace() {
        return namespace;
    }

    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }

    public String getGroup() {
        return group;
    }

    public void setGroup(String group) {
        this.group = group;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public long getHeartbeatInterval() {
        return heartbeatInterval;
    }

    public void setHeartbeatInterval(long heartbeatInterval) {
        this.heartbeatInterval = heartbeatInterval;
    }

    public long getInstanceTimeout() {
        return instanceTimeout;
    }

    public void setInstanceTimeout(long instanceTimeout) {
        this.instanceTimeout = instanceTimeout;
    }
}
