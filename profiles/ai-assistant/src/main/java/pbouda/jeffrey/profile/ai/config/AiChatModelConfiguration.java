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

package pbouda.jeffrey.profile.ai.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.anthropic.api.AnthropicApi;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;

/**
 * Manual configuration of Spring AI ChatModel and ChatClient beans.
 * Replaces the auto-configuration starters with explicit bean creation
 * based on {@code jeffrey.ai.provider} property.
 * <p>
 * Active when {@code jeffrey.ai.provider} is set to a real provider (not {@code none}).
 */
@ConditionalOnExpression("'${jeffrey.ai.provider:none}' != 'none'")
public class AiChatModelConfiguration {

    private static final Logger LOG = LoggerFactory.getLogger(AiChatModelConfiguration.class);

    @Bean
    public ChatModel chatModel(
            @Value("${jeffrey.ai.provider}") String provider,
            @Value("${jeffrey.ai.model}") String modelName,
            @Value("${jeffrey.ai.max-tokens:4096}") int maxTokens,
            @Value("${jeffrey.ai.api-key}") String apiKey) {

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

        AnthropicApi api = AnthropicApi.builder()
                .apiKey(apiKey)
                .build();

        AnthropicChatOptions options = AnthropicChatOptions.builder()
                .model(modelName)
                .maxTokens(maxTokens)
                .build();

        return AnthropicChatModel.builder()
                .anthropicApi(api)
                .defaultOptions(options)
                .toolCallingManager(ToolCallingManager.builder().build())
                .build();
    }

    private ChatModel createOpenAiChatModel(String apiKey, String modelName, int maxTokens) {
        LOG.info("Creating OpenAI ChatModel: model={} maxTokens={}", modelName, maxTokens);

        OpenAiApi api = OpenAiApi.builder()
                .apiKey(apiKey)
                .build();

        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .model(modelName)
                .maxTokens(maxTokens)
                .build();

        return OpenAiChatModel.builder()
                .openAiApi(api)
                .defaultOptions(options)
                .toolCallingManager(ToolCallingManager.builder().build())
                .build();
    }
}
