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

import io.micrometer.observation.ObservationRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.anthropic.AnthropicSetup;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.setup.OpenAiSetup;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;

import java.util.List;

/**
 * Manual configuration of Spring AI ChatModel and ChatClient beans.
 * Replaces the auto-configuration starters with explicit bean creation
 * based on {@code jeffrey.ai.provider} property.
 * <p>
 * Active when {@code jeffrey.microscope.ai.provider} is set to a real provider (not {@code none}).
 * <p>
 * Database-stored settings are injected into the Spring Environment on startup
 * by {@code SettingsConfiguration}, so they are available via {@code @Value} annotations
 * and {@code @ConditionalOnExpression} without this class needing to depend on persistence.
 */
@ConditionalOnExpression("'${jeffrey.microscope.ai.provider:none}' != 'none'")
public class AiChatModelConfiguration {

    private static final Logger LOG = LoggerFactory.getLogger(AiChatModelConfiguration.class);

    private static final int DEFAULT_MAX_RETRIES = 2;

    @Bean
    public ChatModel chatModel(
            @Value("${jeffrey.microscope.ai.provider}") String provider,
            @Value("${jeffrey.microscope.ai.model}") String modelName,
            @Value("${jeffrey.microscope.ai.max-tokens:4096}") int maxTokens,
            @Value("${jeffrey.microscope.ai.api-key}") String apiKey) {

        return switch (provider.toLowerCase()) {
            case "claude" -> createAnthropicChatModel(apiKey, modelName, maxTokens);
            case "chatgpt" -> createOpenAiChatModel(apiKey, modelName, maxTokens);
            default -> throw new IllegalArgumentException("Unknown AI provider: " + provider
                    + ". Supported providers: claude, chatgpt");
        };
    }

    @Bean
    public ChatClient.Builder chatClientBuilder(ChatModel chatModel) {
        return ChatClient.builder(chatModel);
    }

    private ChatModel createAnthropicChatModel(String apiKey, String modelName, int maxTokens) {
        LOG.info("Creating Anthropic ChatModel: model={} maxTokens={}", modelName, maxTokens);

        var client = AnthropicSetup.setupSyncClient(null, apiKey, null, null, null, null);

        AnthropicChatOptions options = AnthropicChatOptions.builder()
                .model(modelName)
                .maxTokens(maxTokens)
                .build();

        return AnthropicChatModel.builder()
                .anthropicClient(client)
                .options(options)
                .build();
    }

    private ChatModel createOpenAiChatModel(String apiKey, String modelName, int maxTokens) {
        LOG.info("Creating OpenAI ChatModel: model={} maxTokens={}", modelName, maxTokens);

        var openAiClient = OpenAiSetup.setupSyncClient(
                null, apiKey, null, null, null, null,
                false, false, null,
                null, DEFAULT_MAX_RETRIES, null, null,
                ObservationRegistry.NOOP, null, List.of());

        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .model(modelName)
                .maxCompletionTokens(maxTokens)
                .build();

        return OpenAiChatModel.builder()
                .openAiClient(openAiClient)
                .options(options)
                .build();
    }
}
