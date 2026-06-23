/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
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

package cafe.jeffrey.performance.analyst.configuration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import cafe.jeffrey.profile.ai.chat.AiChatBackend;
import cafe.jeffrey.profile.ai.claudecode.ClaudeCodeChatBackend;
import cafe.jeffrey.profile.ai.claudecode.ClaudeCodeCliClient;
import cafe.jeffrey.profile.ai.config.SpringAiBackendFactory;

import java.time.Duration;

/**
 * Static-properties AI wiring for the performance-analyst. Builds a single provider-agnostic
 * {@link AiChatBackend} bean from {@code jeffrey.performance-analyst.ai.*} properties — no
 * database-backed settings or encryption.
 *
 * <p>The API-key providers (Claude via the Anthropic API, ChatGPT, Ollama) are built by the shared
 * {@link SpringAiBackendFactory}. The {@code claude-code} provider drives the Claude Code CLI in
 * headless mode via {@link ClaudeCodeChatBackend} — authentication reuses the host's Claude subscription,
 * so no API key is required.</p>
 *
 * <p>Active only when {@code jeffrey.performance-analyst.ai.provider} is set to a real provider
 * (not {@code none}, the default), so the app starts fine with AI disabled.</p>
 */
@Configuration
@ConditionalOnExpression("'${jeffrey.performance-analyst.ai.provider:none}' != 'none'")
public class PerformanceAnalystAiConfiguration {

    private static final Logger LOG = LoggerFactory.getLogger(PerformanceAnalystAiConfiguration.class);

    private static final String PROVIDER_CLAUDE = "claude";
    private static final String PROVIDER_CHATGPT = "chatgpt";
    private static final String PROVIDER_OLLAMA = "ollama";
    private static final String PROVIDER_CLAUDE_CODE = "claude-code";

    private static final String CLI_DEFAULT_MODEL = "<cli-default>";

    @Bean
    public AiChatBackend aiChatBackend(
            @Value("${jeffrey.performance-analyst.ai.provider}") String provider,
            @Value("${jeffrey.performance-analyst.ai.model:}") String modelName,
            @Value("${jeffrey.performance-analyst.ai.max-tokens:4096}") int maxTokens,
            @Value("${jeffrey.performance-analyst.ai.api-key:}") String apiKey,
            @Value("${jeffrey.performance-analyst.ai.base-url:" + SpringAiBackendFactory.DEFAULT_OLLAMA_BASE_URL + "}") String baseUrl,
            @Value("${jeffrey.performance-analyst.ai.cli-path:claude}") String cliPath,
            @Value("${jeffrey.performance-analyst.ai.timeout-seconds:600}") int timeoutSeconds) {

        return switch (provider.toLowerCase()) {
            case PROVIDER_CLAUDE -> SpringAiBackendFactory.anthropic(apiKey, modelName, maxTokens);
            case PROVIDER_CHATGPT -> SpringAiBackendFactory.openAi(apiKey, modelName, maxTokens);
            case PROVIDER_OLLAMA -> SpringAiBackendFactory.ollama(baseUrl, modelName, maxTokens);
            case PROVIDER_CLAUDE_CODE -> createClaudeCodeBackend(cliPath, modelName, timeoutSeconds);
            default -> throw new IllegalArgumentException("Unknown AI provider: " + provider
                    + ". Supported providers: claude, chatgpt, ollama, claude-code");
        };
    }

    private AiChatBackend createClaudeCodeBackend(String cliPath, String modelName, int timeoutSeconds) {
        LOG.info("Creating Claude Code backend: cli_path={} model={} timeout_in_sec={}",
                cliPath, modelName.isBlank() ? CLI_DEFAULT_MODEL : modelName, timeoutSeconds);
        ClaudeCodeCliClient cliClient = new ClaudeCodeCliClient(cliPath, Duration.ofSeconds(timeoutSeconds));
        return new ClaudeCodeChatBackend(cliClient, modelName);
    }
}
