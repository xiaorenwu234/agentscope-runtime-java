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

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Auto-configuration for cluster functionality.
 */
@Configuration
public class ClusterAutoConfiguration {

    /**
     * Create ClusterProperties bean only if not already defined.
     * This allows manual registration via LocalDeployManager to take precedence.
     */
    @Bean
    @ConditionalOnMissingBean(ClusterProperties.class)
    @ConfigurationProperties(prefix = "agentscope.cluster")
    public ClusterProperties clusterProperties() {
        return new ClusterProperties();
    }
}
