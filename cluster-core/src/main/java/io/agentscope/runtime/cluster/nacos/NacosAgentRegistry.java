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

import com.alibaba.nacos.api.NacosFactory;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.NamingService;
import com.alibaba.nacos.api.naming.listener.EventListener;
import com.alibaba.nacos.api.naming.listener.NamingEvent;
import com.alibaba.nacos.api.naming.pojo.Instance;
import io.agentscope.runtime.cluster.AgentInstance;
import io.agentscope.runtime.cluster.AgentRegistry;
import io.agentscope.runtime.cluster.AgentStatus;
import io.agentscope.runtime.cluster.RegistryException;
import io.agentscope.runtime.cluster.RegistryListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Nacos implementation of AgentRegistry.
 * Provides service registration and discovery using Nacos naming service.
 */
public class NacosAgentRegistry implements AgentRegistry {

    private static final Logger logger = LoggerFactory.getLogger(NacosAgentRegistry.class);

    private static final String METADATA_INSTANCE_ID = "instanceId";

    private static final String METADATA_STATUS = "status";

    private static final String METADATA_REGISTRATION_TIME = "registrationTime";

    private final NacosRegistryConfig config;

    private final NamingService namingService;

    private final String group;

    /**
     * Map of registered instances: instanceId -> AgentInstance.
     */
    private final Map<String, AgentInstance> registeredInstances = new ConcurrentHashMap<>();

    /**
     * Map of listeners: agentName -> List of listeners.
     */
    private final Map<String, List<RegistryListener>> listeners = new ConcurrentHashMap<>();

    /**
     * Map of Nacos event listeners for unsubscription.
     */
    private final Map<String, EventListener> nacosListeners = new ConcurrentHashMap<>();

    public NacosAgentRegistry(NacosRegistryConfig config) {
        this.config = config;
        this.group = config.getGroup();
        this.namingService = createNamingService(config);
    }

    private NamingService createNamingService(NacosRegistryConfig config) {
        try {
            Properties properties = new Properties();
            properties.setProperty("serverAddr", config.getServerAddr());
            if (config.getNamespace() != null && !config.getNamespace().isEmpty()) {
                properties.setProperty("namespace", config.getNamespace());
            }
            if (config.getUsername() != null && !config.getUsername().isEmpty()) {
                properties.setProperty("username", config.getUsername());
            }
            if (config.getPassword() != null && !config.getPassword().isEmpty()) {
                properties.setProperty("password", config.getPassword());
            }
            return NacosFactory.createNamingService(properties);
        }
        catch (NacosException e) {
            throw new RegistryException("Failed to create Nacos naming service", e);
        }
    }

    @Override
    public void register(AgentInstance instance) throws RegistryException {
        int maxRetries = 10;
        int retryInterval = 1000; // 1 second
        
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                // Wait for Nacos client to be ready
                String serverStatus = namingService.getServerStatus();
                if (!"UP".equals(serverStatus)) {
                    if (attempt < maxRetries) {
                        logger.info("Nacos client not ready (status: {}), waiting... (attempt {}/{})", 
                                serverStatus, attempt, maxRetries);
                        Thread.sleep(retryInterval);
                        continue;
                    } else {
                        throw new RegistryException("Nacos client not ready after " + maxRetries + " attempts");
                    }
                }
                
                Instance nacosInstance = toNacosInstance(instance);
                namingService.registerInstance(instance.getAgentName(), group, nacosInstance);
                registeredInstances.put(instance.getInstanceId(), instance);
                instance.setStatus(AgentStatus.RUNNING);
                logger.info("Registered agent instance: {} at {}:{}",
                        instance.getAgentName(), instance.getHost(), instance.getPort());
                return; // Success, exit the retry loop
            }
            catch (NacosException e) {
                if (attempt < maxRetries && e.getMessage() != null && 
                        e.getMessage().contains("Client not connected")) {
                    logger.info("Nacos client connecting... (attempt {}/{})", attempt, maxRetries);
                    try {
                        Thread.sleep(retryInterval);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RegistryException("Registration interrupted", ie);
                    }
                } else {
                    throw new RegistryException("Failed to register instance: " + instance.getInstanceId(), e);
                }
            }
            catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RegistryException("Registration interrupted", e);
            }
        }
    }

    @Override
    public void deregister(String instanceId) throws RegistryException {
        AgentInstance instance = registeredInstances.get(instanceId);
        if (instance == null) {
            logger.warn("Instance not found for deregistration: {}", instanceId);
            return;
        }

        try {
            namingService.deregisterInstance(instance.getAgentName(), group,
                    instance.getHost(), instance.getPort());
            registeredInstances.remove(instanceId);
            instance.setStatus(AgentStatus.STOPPED);
            logger.info("Deregistered agent instance: {}", instanceId);
        }
        catch (NacosException e) {
            throw new RegistryException("Failed to deregister instance: " + instanceId, e);
        }
    }

    @Override
    public List<AgentInstance> discover(String agentName) throws RegistryException {
        try {
            List<Instance> instances = namingService.selectInstances(agentName, group, true);
            return instances.stream()
                    .map(this::fromNacosInstance)
                    .collect(Collectors.toList());
        }
        catch (NacosException e) {
            throw new RegistryException("Failed to discover instances for: " + agentName, e);
        }
    }

    @Override
    public void subscribe(String agentName, RegistryListener listener) throws RegistryException {
        listeners.computeIfAbsent(agentName, k -> new ArrayList<>()).add(listener);

        // Create Nacos event listener if not exists
        if (!nacosListeners.containsKey(agentName)) {
            EventListener eventListener = event -> {
                if (event instanceof NamingEvent namingEvent) {
                    List<AgentInstance> instances = namingEvent.getInstances().stream()
                            .filter(Instance::isHealthy)
                            .map(this::fromNacosInstance)
                            .collect(Collectors.toList());

                    // Notify all listeners
                    List<RegistryListener> agentListeners = listeners.get(agentName);
                    if (agentListeners != null) {
                        for (RegistryListener l : agentListeners) {
                            try {
                                l.onInstancesChanged(agentName, instances);
                            }
                            catch (Exception ex) {
                                logger.error("Error notifying listener for {}: {}", agentName, ex.getMessage());
                            }
                        }
                    }
                }
            };

            try {
                namingService.subscribe(agentName, group, eventListener);
                nacosListeners.put(agentName, eventListener);
                logger.info("Subscribed to agent: {}", agentName);
            }
            catch (NacosException e) {
                throw new RegistryException("Failed to subscribe to: " + agentName, e);
            }
        }
    }

    @Override
    public void unsubscribe(String agentName, RegistryListener listener) {
        List<RegistryListener> agentListeners = listeners.get(agentName);
        if (agentListeners != null) {
            agentListeners.remove(listener);

            // If no more listeners, unsubscribe from Nacos
            if (agentListeners.isEmpty()) {
                EventListener eventListener = nacosListeners.remove(agentName);
                if (eventListener != null) {
                    try {
                        namingService.unsubscribe(agentName, group, eventListener);
                        logger.info("Unsubscribed from agent: {}", agentName);
                    }
                    catch (NacosException e) {
                        logger.error("Failed to unsubscribe from: {}", agentName, e);
                    }
                }
                listeners.remove(agentName);
            }
        }
    }

    @Override
    public void heartbeat(String instanceId) throws RegistryException {
        AgentInstance instance = registeredInstances.get(instanceId);
        if (instance != null) {
            instance.setLastHeartbeat(System.currentTimeMillis());
            // Nacos handles heartbeat automatically through its client
        }
    }

    @Override
    public boolean isHealthy() {
        try {
            return "UP".equals(namingService.getServerStatus());
        }
        catch (Exception e) {
            return false;
        }
    }

    @Override
    public void close() {
        // Deregister all instances
        for (String instanceId : new ArrayList<>(registeredInstances.keySet())) {
            try {
                deregister(instanceId);
            }
            catch (Exception e) {
                logger.error("Failed to deregister instance on close: {}", instanceId, e);
            }
        }

        // Shutdown naming service
        try {
            namingService.shutDown();
            logger.info("Nacos registry closed");
        }
        catch (NacosException e) {
            logger.error("Failed to shutdown Nacos naming service", e);
        }
    }

    /**
     * Convert AgentInstance to Nacos Instance.
     */
    private Instance toNacosInstance(AgentInstance agentInstance) {
        Instance instance = new Instance();
        instance.setIp(agentInstance.getHost());
        instance.setPort(agentInstance.getPort());
        instance.setWeight(agentInstance.getWeight());
        instance.setHealthy(true);
        instance.setEnabled(true);

        Map<String, String> metadata = new HashMap<>();
        if (agentInstance.getMetadata() != null) {
            metadata.putAll(agentInstance.getMetadata());
        }
        metadata.put(METADATA_INSTANCE_ID, agentInstance.getInstanceId());
        metadata.put(METADATA_STATUS, agentInstance.getStatus().name());
        metadata.put(METADATA_REGISTRATION_TIME, String.valueOf(agentInstance.getRegistrationTime()));
        instance.setMetadata(metadata);

        return instance;
    }

    /**
     * Convert Nacos Instance to AgentInstance.
     */
    private AgentInstance fromNacosInstance(Instance nacosInstance) {
        Map<String, String> metadata = nacosInstance.getMetadata();
        String instanceId = metadata.getOrDefault(METADATA_INSTANCE_ID,
                nacosInstance.getIp() + ":" + nacosInstance.getPort());

        AgentInstance instance = AgentInstance.builder()
                .instanceId(instanceId)
                .agentName(nacosInstance.getServiceName())
                .host(nacosInstance.getIp())
                .port(nacosInstance.getPort())
                .weight(nacosInstance.getWeight())
                .metadata(new HashMap<>(metadata))
                .status(AgentStatus.valueOf(metadata.getOrDefault(METADATA_STATUS, "RUNNING")))
                .build();

        String regTime = metadata.get(METADATA_REGISTRATION_TIME);
        if (regTime != null) {
            instance.setRegistrationTime(Long.parseLong(regTime));
        }

        return instance;
    }

    /**
     * Get the underlying Nacos NamingService.
     * @return NamingService instance
     */
    public NamingService getNamingService() {
        return namingService;
    }
}
