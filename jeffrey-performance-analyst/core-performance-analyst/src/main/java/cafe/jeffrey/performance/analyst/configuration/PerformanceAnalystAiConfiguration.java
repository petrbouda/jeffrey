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

import com.openai.client.OpenAIClient;
import io.micrometer.observation.ObservationRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.anthropic.AnthropicSetup;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.setup.OpenAiSetup;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.List;

/**
 * Static-properties Spring AI wiring for the performance-analyst. Builds {@link ChatModel} and
 * {@link ChatClient.Builder} beans from {@code jeffrey.performance-analyst.ai.*} properties — no
 * database-backed settings or encryption. Adapted from microscope's {@code AiChatModelConfiguration}.
 *
 * <p>Active only when {@code jeffrey.performance-analyst.ai.provider} is set to a real provider
 * (not {@code none}, the default), so the app starts fine with AI disabled.</p>
 */
@Configuration
@ConditionalOnExpression("'${jeffrey.performance-analyst.ai.provider:none}' != 'none'")
public class PerformanceAnalystAiConfiguration {

    private static final Logger LOG = LoggerFactory.getLogger(PerformanceAnalystAiConfiguration.class);

    private static final Duration OPENAI_CLIENT_TIMEOUT = Duration.ofMinutes(10);
    private static final int OPENAI_CLIENT_MAX_RETRIES = 2;
    private static final String DEFAULT_OLLAMA_BASE_URL = "http://localhost:11434";

    @Bean
    public ChatModel chatModel(
            @Value("${jeffrey.performance-analyst.ai.provider}") String provider,
            @Value("${jeffrey.performance-analyst.ai.model}") String modelName,
            @Value("${jeffrey.performance-analyst.ai.max-tokens:4096}") int maxTokens,
            @Value("${jeffrey.performance-analyst.ai.api-key:}") String apiKey,
            @Value("${jeffrey.performance-analyst.ai.base-url:" + DEFAULT_OLLAMA_BASE_URL + "}") String baseUrl) {

        return switch (provider.toLowerCase()) {
            case "claude" -> createAnthropicChatModel(apiKey, modelName, maxTokens);
            case "chatgpt" -> createOpenAiChatModel(apiKey, modelName, maxTokens);
            case "ollama" -> createOllamaChatModel(baseUrl, modelName, maxTokens);
            default -> throw new IllegalArgumentException("Unknown AI provider: " + provider
                    + ". Supported providers: claude, chatgpt, ollama");
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

        OpenAIClient client = OpenAiSetup.setupSyncClient(
                null,                       // baseUrl (default OpenAI endpoint)
                apiKey,                     // apiKey
                null,                       // credential
                null,                       // azureDeploymentName
                null,                       // azureOpenAiServiceVersion
                null,                       // organizationId
                false,                      // isAzure
                false,                      // isGitHubModels
                modelName,                  // modelName
                OPENAI_CLIENT_TIMEOUT,      // timeout
                OPENAI_CLIENT_MAX_RETRIES,  // maxRetries
                null,                       // proxy
                null,                       // customHeaders
                ObservationRegistry.NOOP,   // observationRegistry
                null,                       // meterRegistry
                List.of());                 // httpClientCustomizers

        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .model(modelName)
                .maxCompletionTokens(maxTokens)
                .build();

        return OpenAiChatModel.builder()
                .openAiClient(client)
                .options(options)
                .build();
    }

    private ChatModel createOllamaChatModel(String baseUrl, String modelName, int maxTokens) {
        LOG.info("Creating Ollama ChatModel: baseUrl={} model={} maxTokens={}", baseUrl, modelName, maxTokens);

        OllamaApi api = OllamaApi.builder()
                .baseUrl(baseUrl)
                .build();

        // The Ollama-specific builder.model(...) accepts only the OllamaModel enum, so the
        // free-form, user-configured model name is set via the inherited model(String) method.
        // numPredict maps to the maximum number of output tokens to generate.
        OllamaChatOptions options = OllamaChatOptions.builder()
                .model(modelName)
                .numPredict(maxTokens)
                .build();

        return OllamaChatModel.builder()
                .ollamaApi(api)
                .options(options)
                .build();
    }
}
