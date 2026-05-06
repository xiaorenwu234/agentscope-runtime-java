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
package io.agentscope.runtime.autoconfigure;

/**
 * Configuration properties for cluster functionality.
 */
public class ClusterProperties {

    /**
     * Whether cluster mode is enabled.
     */
    private boolean enabled = false;

    /**
     * Nacos configuration.
     */
    private NacosProperties nacos = new NacosProperties();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public NacosProperties getNacos() {
        return nacos;
    }

    public void setNacos(NacosProperties nacos) {
        this.nacos = nacos;
    }

    /**
     * Nacos-specific properties.
     */
    public static class NacosProperties {

        /**
         * Nacos server address.
         */
        private String serverAddr = "localhost:8848";

        /**
         * Nacos namespace.
         */
        private String namespace = "public";

        /**
         * Nacos group.
         */
        private String group = "DEFAULT_GROUP";

        /**
         * Nacos username (optional).
         */
        private String username;

        /**
         * Nacos password (optional).
         */
        private String password;

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
    }
}
