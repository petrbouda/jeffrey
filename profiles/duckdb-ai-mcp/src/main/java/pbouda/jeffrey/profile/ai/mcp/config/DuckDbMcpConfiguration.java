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

package pbouda.jeffrey.profile.ai.mcp.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import pbouda.jeffrey.profile.ai.mcp.service.JfrAnalysisAssistantService;
import pbouda.jeffrey.profile.ai.mcp.service.JfrAnalysisAssistantServiceImpl;
import pbouda.jeffrey.profile.ai.mcp.service.NoOpJfrAnalysisAssistantService;
import pbouda.jeffrey.provider.profile.DatabaseManagerResolver;

/**
 * Spring Boot configuration for DuckDB MCP integration with AI-powered JFR analysis.
 */
public class DuckDbMcpConfiguration {

    private static final Logger LOG = LoggerFactory.getLogger(DuckDbMcpConfiguration.class);

    /**
     * Create the JFR Analysis Assistant Service when AI is enabled.
     * Requires a ChatClient.Builder and DatabaseManagerResolver to be available.
     */
    @Bean
    @ConditionalOnProperty(name = "jeffrey.ai.enabled", havingValue = "true")
    public JfrAnalysisAssistantService jfrAnalysisAssistantService(
            ChatClient.Builder chatClientBuilder,
            DatabaseManagerResolver databaseManagerResolver,
            @Value("${spring.ai.anthropic.chat.options.model:}") String modelName) {
        LOG.info("Creating JFR Analysis Assistant Service with MCP tools: model={}", modelName);
        return new JfrAnalysisAssistantServiceImpl(chatClientBuilder, databaseManagerResolver, modelName);
    }

    /**
     * Create a no-op service when AI is not configured.
     */
    @Bean
    @ConditionalOnMissingBean(JfrAnalysisAssistantService.class)
    public JfrAnalysisAssistantService noOpJfrAnalysisAssistantService() {
        LOG.info("Creating No-Op JFR Analysis Assistant Service (AI not configured)");
        return new NoOpJfrAnalysisAssistantService();
    }
}
