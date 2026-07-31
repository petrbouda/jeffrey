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

package cafe.jeffrey.profile.ai.duckdb.jfr.config;

import org.springframework.context.annotation.Bean;
import cafe.jeffrey.profile.ai.chat.AiChatBackend;
import cafe.jeffrey.profile.ai.chat.McpToolsetFactory;
import cafe.jeffrey.profile.ai.duckdb.jfr.service.JfrAnalysisAssistantService;
import cafe.jeffrey.profile.ai.duckdb.jfr.service.JfrAnalysisAssistantServiceImpl;
import cafe.jeffrey.provider.profile.api.DatabaseManagerResolver;

/**
 * Spring Boot configuration for DuckDB MCP integration with AI-powered JFR analysis.
 * <p>
 * The service is registered whether or not AI is configured. It reports its availability from the
 * backend on each call, so turning AI on or off is a change of answer rather than a change of wiring.
 */
public class DuckDbMcpConfiguration {

    @Bean
    public JfrAnalysisAssistantService jfrAnalysisAssistantService(
            AiChatBackend chatBackend,
            DatabaseManagerResolver databaseManagerResolver,
            McpToolsetFactory mcpToolsetFactory) {

        return new JfrAnalysisAssistantServiceImpl(chatBackend, databaseManagerResolver, mcpToolsetFactory);
    }
}
