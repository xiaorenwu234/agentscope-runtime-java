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
 * Random load balancer implementation.
 * Randomly selects an instance from available instances.
 */
public class RandomLoadBalancer implements LoadBalancer {

    @Override
    public AgentInstance select(List<AgentInstance> instances) {
        if (instances == null || instances.isEmpty()) {
            return null;
        }

        int index = ThreadLocalRandom.current().nextInt(instances.size());
        return instances.get(index);
    }

    @Override
    public String getName() {
        return "random";
    }
}
