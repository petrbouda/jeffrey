/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package pbouda.jeffrey.profile.ai.heapmcp.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import pbouda.jeffrey.profile.ai.heapmcp.service.HeapDumpAnalysisAssistantService;
import pbouda.jeffrey.profile.ai.heapmcp.service.HeapDumpAnalysisAssistantServiceImpl;
import pbouda.jeffrey.profile.ai.heapmcp.service.NoOpHeapDumpAnalysisAssistantService;

/**
 * Spring Boot configuration for heap dump MCP integration with AI-powered analysis.
 */
public class HeapDumpMcpConfiguration {

    private static final Logger LOG = LoggerFactory.getLogger(HeapDumpMcpConfiguration.class);

    @Bean
    @ConditionalOnProperty(name = "jeffrey.ai.enabled", havingValue = "true")
    public HeapDumpAnalysisAssistantService heapDumpAnalysisAssistantService(
            ChatClient.Builder chatClientBuilder,
            @Value("${spring.ai.anthropic.chat.options.model:}") String modelName) {
        LOG.info("Creating Heap Dump Analysis Assistant Service with MCP tools: model={}", modelName);
        return new HeapDumpAnalysisAssistantServiceImpl(chatClientBuilder, modelName);
    }

    @Bean
    @ConditionalOnMissingBean(HeapDumpAnalysisAssistantService.class)
    public HeapDumpAnalysisAssistantService noOpHeapDumpAnalysisAssistantService() {
        LOG.info("Creating No-Op Heap Dump Analysis Assistant Service (AI not configured)");
        return new NoOpHeapDumpAnalysisAssistantService();
    }
}
