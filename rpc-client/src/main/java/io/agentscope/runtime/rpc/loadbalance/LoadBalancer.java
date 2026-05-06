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
package io.agentscope.runtime.rpc.loadbalance;

import io.agentscope.runtime.cluster.AgentInstance;

import java.util.List;

/**
 * Load balancer interface for selecting an instance from a list of available instances.
 */
public interface LoadBalancer {

    /**
     * Select an instance from the list of available instances.
     *
     * @param instances the list of available instances
     * @return the selected instance, or null if no instance is available
     */
    AgentInstance select(List<AgentInstance> instances);

    /**
     * Get the name of this load balancer strategy.
     *
     * @return the strategy name
     */
    String getName();
}
