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
package io.agentscope.runtime.lifecycle;

import io.agentscope.runtime.autoconfigure.ClusterProperties;
import io.agentscope.runtime.autoconfigure.DeployProperties;
import io.agentscope.runtime.cluster.AgentInstance;
import io.agentscope.runtime.cluster.AgentRegistry;
import io.agentscope.runtime.cluster.AgentStatus;
import io.agentscope.runtime.cluster.nacos.NacosAgentRegistry;
import io.agentscope.runtime.cluster.nacos.NacosRegistryConfig;
import io.agentscope.runtime.engine.Runner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.util.HashMap;
import java.util.Map;

/**
 * Service for managing cluster registration.
 */
public class ClusterService {

    private static final Logger logger = LoggerFactory.getLogger(ClusterService.class);

    private final ClusterProperties clusterProperties;

    private final DeployProperties deployProperties;

    private final Runner runner;

    private AgentRegistry registry;

    private AgentInstance currentInstance;

    public ClusterService(ClusterProperties clusterProperties, DeployProperties deployProperties, Runner runner) {
        this.clusterProperties = clusterProperties;
        this.deployProperties = deployProperties;
        this.runner = runner;
    }

    /**
     * Register this agent instance to the cluster.
     */
    public void register() {
        if (!clusterProperties.isEnabled()) {
            logger.info("[ClusterService] Cluster mode is disabled, skipping registration");
            return;
        }

        try {
            // Create registry
            ClusterProperties.NacosProperties nacos = clusterProperties.getNacos();
            NacosRegistryConfig config = NacosRegistryConfig.builder()
                    .serverAddr(nacos.getServerAddr())
                    .namespace(nacos.getNamespace())
                    .group(nacos.getGroup())
                    .username(nacos.getUsername())
                    .password(nacos.getPassword())
                    .build();

            this.registry = new NacosAgentRegistry(config);

            // Build instance
            String host = deployProperties.getServerAddress();
            if (host == null || host.isEmpty() || "0.0.0.0".equals(host)) {
                host = InetAddress.getLocalHost().getHostAddress();
            }

            String agentName = runner.getAgent().getName();
            if (agentName == null || agentName.isEmpty()) {
                agentName = "agent-" + deployProperties.getServerPort();
            }

            Map<String, String> metadata = new HashMap<>();
            metadata.put("description", runner.getAgent().getDescription());
            metadata.put("frameworkType", runner.getAgent().getFrameworkType());
            metadata.put("startTime", String.valueOf(System.currentTimeMillis()));

            this.currentInstance = AgentInstance.builder()
                    .agentName(agentName)
                    .host(host)
                    .port(deployProperties.getServerPort())
                    .metadata(metadata)
                    .status(AgentStatus.RUNNING)
                    .build();

            registry.register(currentInstance);
            logger.info("[ClusterService] Registered to cluster: {} at {}:{}",
                    agentName, host, deployProperties.getServerPort());
        }
        catch (Exception e) {
            logger.error("[ClusterService] Failed to register to cluster: {}", e.getMessage(), e);
        }
    }

    /**
     * Deregister this agent instance from the cluster.
     */
    public void deregister() {
        if (!clusterProperties.isEnabled() || registry == null || currentInstance == null) {
            return;
        }

        try {
            registry.deregister(currentInstance.getInstanceId());
            logger.info("[ClusterService] Deregistered from cluster: {}", currentInstance.getAgentName());
        }
        catch (Exception e) {
            logger.error("[ClusterService] Failed to deregister from cluster: {}", e.getMessage(), e);
        }
        finally {
            if (registry != null) {
                registry.close();
            }
        }
    }

    /**
     * Get the agent registry.
     */
    public AgentRegistry getRegistry() {
        return registry;
    }

    /**
     * Get the current instance.
     */
    public AgentInstance getCurrentInstance() {
        return currentInstance;
    }
}
