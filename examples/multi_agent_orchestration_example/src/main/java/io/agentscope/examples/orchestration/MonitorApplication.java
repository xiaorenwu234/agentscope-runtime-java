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
package io.agentscope.examples.orchestration;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Monitor Application for Multi-Agent Orchestration visualization.
 * Provides REST API for frontend to interact with the orchestration system.
 */
@SpringBootApplication
public class MonitorApplication {

    public static void main(String[] args) {
        // Set default port to 8080
        System.setProperty("server.port", System.getProperty("server.port", "8080"));
        SpringApplication.run(MonitorApplication.class, args);
    }
}
