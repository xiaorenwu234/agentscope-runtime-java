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
import java.util.concurrent.ThreadLocalRandom;

/**
 * Weighted random load balancer implementation.
 * Selects instances based on their weight.
 */
public class WeightedRandomLoadBalancer implements LoadBalancer {

    @Override
    public AgentInstance select(List<AgentInstance> instances) {
        if (instances == null || instances.isEmpty()) {
            return null;
        }

        // Calculate total weight
        double totalWeight = instances.stream()
                .mapToDouble(AgentInstance::getWeight)
                .sum();

        if (totalWeight <= 0) {
            // Fall back to random selection
            return instances.get(ThreadLocalRandom.current().nextInt(instances.size()));
        }

        // Generate random value
        double randomValue = ThreadLocalRandom.current().nextDouble() * totalWeight;

        // Select based on weight
        double currentWeight = 0;
        for (AgentInstance instance : instances) {
            currentWeight += instance.getWeight();
            if (randomValue < currentWeight) {
                return instance;
            }
        }

        // Fallback to last instance
        return instances.get(instances.size() - 1);
    }

    @Override
    public String getName() {
        return "weighted-random";
    }
}
