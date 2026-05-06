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

import java.util.List;

/**
 * Interface for Agent service registry.
 * Provides methods for registering, discovering, and subscribing to agent instances.
 */
public interface AgentRegistry {

    /**
     * Register an agent instance to the registry.
     *
     * @param instance the agent instance to register
     * @throws RegistryException if registration fails
     */
    void register(AgentInstance instance) throws RegistryException;

    /**
     * Deregister an agent instance from the registry.
     *
     * @param instanceId the instance ID to deregister
     * @throws RegistryException if deregistration fails
     */
    void deregister(String instanceId) throws RegistryException;

    /**
     * Discover all healthy instances of an agent by name.
     *
     * @param agentName the name of the agent to discover
     * @return list of healthy agent instances
     * @throws RegistryException if discovery fails
     */
    List<AgentInstance> discover(String agentName) throws RegistryException;

    /**
     * Subscribe to changes for a specific agent.
     *
     * @param agentName the name of the agent to subscribe to
     * @param listener  the listener to notify on changes
     * @throws RegistryException if subscription fails
     */
    void subscribe(String agentName, RegistryListener listener) throws RegistryException;

    /**
     * Unsubscribe from changes for a specific agent.
     *
     * @param agentName the name of the agent to unsubscribe from
     * @param listener  the listener to remove
     */
    void unsubscribe(String agentName, RegistryListener listener);

    /**
     * Update heartbeat for an instance.
     *
     * @param instanceId the instance ID
     * @throws RegistryException if heartbeat update fails
     */
    void heartbeat(String instanceId) throws RegistryException;

    /**
     * Check if the registry is connected and healthy.
     *
     * @return true if connected and healthy
     */
    boolean isHealthy();

    /**
     * Close the registry and release resources.
     */
    void close();
}
