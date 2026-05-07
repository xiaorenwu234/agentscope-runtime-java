/*
 * Copyright 2024-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.agentscope.runtime.cluster.nacos;

import com.alibaba.nacos.api.NacosFactory;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.NamingService;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.api.naming.pojo.ListView;
import io.agentscope.core.a2a.agent.card.AgentCardResolver;
import io.a2a.A2A;
import io.a2a.spec.AgentCard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * Nacos-based AgentCardResolver that discovers service addresses through Nacos.
 *
 * <p>This resolver uses Nacos service discovery to find the actual service address
 * based on service name, then fetches the AgentCard from the discovered instance
 * using the A2A protocol's well-known URI mechanism.</p>
 *
 * <p>It also provides the ability to list all available services registered in Nacos
 * via {@link #discoverServices()}.</p>
 *
 * @author Agentscope Team
 */
public class NacosAgentCardResolver implements AgentCardResolver {

    private static final Logger logger = LoggerFactory.getLogger(NacosAgentCardResolver.class);

    private final NamingService namingService;

    private final String group;

    private final String relativeCardPath;

    private final Map<String, String> authHeaders;

    /**
     * Create a new NacosAgentCardResolver.
     *
     * @param namingService Nacos NamingService instance
     * @param group Nacos group name
     * @param relativeCardPath relative path to agent card endpoint
     * @param authHeaders authentication headers
     */
    public NacosAgentCardResolver(
            NamingService namingService,
            String group,
            String relativeCardPath,
            Map<String, String> authHeaders) {
        this.namingService = namingService;
        this.group = group;
        this.relativeCardPath = relativeCardPath;
        this.authHeaders = authHeaders != null ? authHeaders : Map.of();
    }

    @Override
    public AgentCard getAgentCard(String agentName) {
        try {
            // Discover healthy instances from Nacos
            List<Instance> instances = namingService.selectInstances(agentName, group, true);

            if (instances == null || instances.isEmpty()) {
                throw new RuntimeException("No healthy instances found for service: " + agentName);
            }

            // Use the first available instance (can be enhanced with load balancing)
            Instance instance = instances.get(0);
            String baseUrl = String.format("http://%s:%d", instance.getIp(), instance.getPort());

            logger.info("Discovered service '{}' at {}:{}", agentName, instance.getIp(), instance.getPort());

            // Fetch AgentCard from the discovered service
            return A2A.getAgentCard(baseUrl, relativeCardPath, authHeaders);

        }
        catch (NacosException e) {
            throw new RuntimeException("Failed to discover service from Nacos: " + agentName, e);
        }
    }

    /**
     * Discover all available service names registered in Nacos.
     *
     * @return list of service names, or empty list if discovery fails
     */
    public List<String> discoverServices() {
        try {
            ListView<String> serviceListView = namingService.getServicesOfServer(1, Integer.MAX_VALUE, group);
            List<String> services = serviceListView.getData();
            logger.info("Discovered {} service(s) in Nacos", services.size());
            return Collections.unmodifiableList(services);
        }
        catch (NacosException e) {
            logger.error("Failed to discover services from Nacos: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Get the underlying Nacos NamingService.
     *
     * @return NamingService instance
     */
    public NamingService getNamingService() {
        return namingService;
    }

    /**
     * Create a new Builder instance.
     *
     * @return new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for NacosAgentCardResolver.
     */
    public static class Builder {

        private String serverAddr = "localhost:8848";

        private String namespace = "public";

        private String group = "DEFAULT_GROUP";

        private String username;

        private String password;

        private String relativeCardPath = "/.well-known/agent.json";

        private Map<String, String> authHeaders = Map.of();

        /**
         * Set Nacos server address.
         *
         * @param serverAddr server address (e.g., "localhost:8848")
         * @return builder instance
         */
        public Builder serverAddr(String serverAddr) {
            this.serverAddr = serverAddr;
            return this;
        }

        /**
         * Set Nacos namespace.
         *
         * @param namespace namespace
         * @return builder instance
         */
        public Builder namespace(String namespace) {
            this.namespace = namespace;
            return this;
        }

        /**
         * Set Nacos group.
         *
         * @param group group name
         * @return builder instance
         */
        public Builder group(String group) {
            this.group = group;
            return this;
        }

        /**
         * Set Nacos username for authentication.
         *
         * @param username username
         * @return builder instance
         */
        public Builder username(String username) {
            this.username = username;
            return this;
        }

        /**
         * Set Nacos password for authentication.
         *
         * @param password password
         * @return builder instance
         */
        public Builder password(String password) {
            this.password = password;
            return this;
        }

        /**
         * Set relative card path.
         *
         * @param relativeCardPath relative path to agent card endpoint
         * @return builder instance
         */
        public Builder relativeCardPath(String relativeCardPath) {
            this.relativeCardPath = relativeCardPath;
            return this;
        }

        /**
         * Set authentication headers.
         *
         * @param authHeaders authentication headers map
         * @return builder instance
         */
        public Builder authHeaders(Map<String, String> authHeaders) {
            this.authHeaders = authHeaders;
            return this;
        }

        /**
         * Build the NacosAgentCardResolver instance.
         *
         * @return built resolver instance
         */
        public NacosAgentCardResolver build() {
            try {
                Properties properties = new Properties();
                properties.setProperty("serverAddr", serverAddr);
                if (namespace != null && !namespace.isEmpty()) {
                    properties.setProperty("namespace", namespace);
                }
                if (username != null && !username.isEmpty()) {
                    properties.setProperty("username", username);
                }
                if (password != null && !password.isEmpty()) {
                    properties.setProperty("password", password);
                }

                NamingService namingService = NacosFactory.createNamingService(properties);

                return new NacosAgentCardResolver(namingService, group, relativeCardPath, authHeaders);
            }
            catch (NacosException e) {
                throw new RuntimeException("Failed to create Nacos naming service", e);
            }
        }
    }
}
