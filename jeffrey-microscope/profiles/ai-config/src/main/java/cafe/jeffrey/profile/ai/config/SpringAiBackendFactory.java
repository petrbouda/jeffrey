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
import cafe.jeffrey.profile.ai.chat.AiChatBackend;
import cafe.jeffrey.profile.ai.chat.SpringAiChatBackend;

import java.time.Duration;
import java.util.List;

/**
 * Builds the Spring AI–backed {@link AiChatBackend} implementations shared by every deployment for the
 * API-key providers (Claude via the Anthropic API, ChatGPT, Ollama). Each {@code @Configuration} keeps
 * its own provider switch and property wiring; this factory owns the heavy, identical SDK setup so it
 * lives in exactly one place. The {@code claude-code} provider is wired separately (it drives the CLI,
 * not Spring AI).
 */
public final class SpringAiBackendFactory {

    /** Default Ollama endpoint; exposed so callers can reuse it as a {@code @Value} default. */
    public static final String DEFAULT_OLLAMA_BASE_URL = "http://localhost:11434";

    private static final Logger LOG = LoggerFactory.getLogger(SpringAiBackendFactory.class);

    private static final Duration OPENAI_CLIENT_TIMEOUT = Duration.ofMinutes(10);
    private static final int OPENAI_CLIENT_MAX_RETRIES = 2;

    private static final String DISPLAY_CLAUDE = "Claude";
    private static final String DISPLAY_CHATGPT = "ChatGPT";
    private static final String DISPLAY_OLLAMA = "Ollama";

    private SpringAiBackendFactory() {
    }

    public static AiChatBackend anthropic(String apiKey, String modelName, int maxTokens) {
        return wrap(createAnthropicChatModel(apiKey, modelName, maxTokens), DISPLAY_CLAUDE, modelName);
    }

    public static AiChatBackend openAi(String apiKey, String modelName, int maxTokens) {
        return wrap(createOpenAiChatModel(apiKey, modelName, maxTokens), DISPLAY_CHATGPT, modelName);
    }

    public static AiChatBackend ollama(String baseUrl, String modelName, int maxTokens) {
        return wrap(createOllamaChatModel(baseUrl, modelName, maxTokens), DISPLAY_OLLAMA, modelName);
    }

    private static AiChatBackend wrap(ChatModel chatModel, String providerDisplayName, String modelName) {
        ChatClient chatClient = ChatClient.builder(chatModel).build();
        return new SpringAiChatBackend(chatClient, providerDisplayName, modelName);
    }

    private static ChatModel createAnthropicChatModel(String apiKey, String modelName, int maxTokens) {
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

    private static ChatModel createOpenAiChatModel(String apiKey, String modelName, int maxTokens) {
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

    private static ChatModel createOllamaChatModel(String baseUrl, String modelName, int maxTokens) {
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
