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

package cafe.jeffrey.profile.ai.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import cafe.jeffrey.profile.ai.chat.AiChatBackend;
import cafe.jeffrey.profile.ai.chat.McpToolsetFactory;

/**
 * Manual configuration of the AI backend. Produces a single provider-agnostic {@link AiChatBackend}
 * bean from the {@code jeffrey.microscope.ai.*} settings.
 * <p>
 * The API-key providers (Claude via the Anthropic API, ChatGPT, Ollama) are built by the shared
 * {@link SpringAiBackendFactory}. The {@code claude-code} provider is wired separately by
 * {@code ClaudeCodeConfiguration} (in the {@code claude-code-headless} module), since it drives the
 * Claude Code CLI rather than a Spring AI chat model.
 * <p>
 * The {@code mcpToolsetFactory} bean is active for any real provider (not {@code none}), while the
 * Spring AI {@code aiChatBackend} bean is active only for the API-key providers. Database-stored
 * settings are injected into the Spring Environment on startup by {@code SettingsConfiguration}, so
 * they are available via {@code @Value} and {@code @ConditionalOnExpression}.
 */
@ConditionalOnExpression("'${jeffrey.microscope.ai.provider:none}' != 'none'")
public class AiChatModelConfiguration {

    private static final String PROVIDER_CLAUDE = "claude";
    private static final String PROVIDER_CHATGPT = "chatgpt";
    private static final String PROVIDER_OLLAMA = "ollama";

    @Bean
    public McpToolsetFactory mcpToolsetFactory(
            @Value("${jeffrey.microscope.ai.mcp-url:http://127.0.0.1:8080/api/internal/mcp/claude-code}") String mcpUrl) {
        return new McpToolsetFactory(mcpUrl);
    }

    @Bean
    @ConditionalOnExpression("'${jeffrey.microscope.ai.provider:none}' == 'claude'"
            + " or '${jeffrey.microscope.ai.provider:none}' == 'chatgpt'"
            + " or '${jeffrey.microscope.ai.provider:none}' == 'ollama'")
    public AiChatBackend aiChatBackend(
            @Value("${jeffrey.microscope.ai.provider}") String provider,
            @Value("${jeffrey.microscope.ai.model:}") String modelName,
            @Value("${jeffrey.microscope.ai.max-tokens:4096}") int maxTokens,
            @Value("${jeffrey.microscope.ai.api-key:}") String apiKey,
            @Value("${jeffrey.microscope.ai.base-url:" + SpringAiBackendFactory.DEFAULT_OLLAMA_BASE_URL + "}") String baseUrl) {

        return switch (provider.toLowerCase()) {
            case PROVIDER_CLAUDE -> SpringAiBackendFactory.anthropic(apiKey, modelName, maxTokens);
            case PROVIDER_CHATGPT -> SpringAiBackendFactory.openAi(apiKey, modelName, maxTokens);
            case PROVIDER_OLLAMA -> SpringAiBackendFactory.ollama(baseUrl, modelName, maxTokens);
            default -> throw new IllegalArgumentException("Unknown AI provider: " + provider
                    + ". Supported providers: claude, chatgpt, ollama");
        };
    }
}
