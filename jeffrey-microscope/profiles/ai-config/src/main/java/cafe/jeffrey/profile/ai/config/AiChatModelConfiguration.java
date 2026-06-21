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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import cafe.jeffrey.profile.ai.chat.AiChatBackend;
import cafe.jeffrey.profile.ai.chat.McpToolsetFactory;
import cafe.jeffrey.profile.ai.chat.SpringAiChatBackend;
import cafe.jeffrey.profile.ai.claudecode.ClaudeCodeChatBackend;
import cafe.jeffrey.profile.ai.claudecode.ClaudeCodeCliClient;

import java.time.Duration;
import java.util.List;

/**
 * Manual configuration of the AI backend. Produces a single provider-agnostic {@link AiChatBackend}
 * bean from the {@code jeffrey.microscope.ai.*} settings.
 * <p>
 * The API-key providers (Claude via the Anthropic API, ChatGPT, Ollama) are backed by Spring AI.
 * The {@code claude-code} provider is backed by the Claude Code CLI in headless mode and authenticates
 * with the host's Claude subscription, so it requires no API key.
 * <p>
 * Active when {@code jeffrey.microscope.ai.provider} is set to a real provider (not {@code none}).
 * Database-stored settings are injected into the Spring Environment on startup by
 * {@code SettingsConfiguration}, so they are available via {@code @Value} and
 * {@code @ConditionalOnExpression}.
 */
@ConditionalOnExpression("'${jeffrey.microscope.ai.provider:none}' != 'none'")
public class AiChatModelConfiguration {

    private static final Logger LOG = LoggerFactory.getLogger(AiChatModelConfiguration.class);

    private static final Duration OPENAI_CLIENT_TIMEOUT = Duration.ofMinutes(10);
    private static final int OPENAI_CLIENT_MAX_RETRIES = 2;
    private static final String DEFAULT_OLLAMA_BASE_URL = "http://localhost:11434";

    private static final String PROVIDER_CLAUDE = "claude";
    private static final String PROVIDER_CHATGPT = "chatgpt";
    private static final String PROVIDER_OLLAMA = "ollama";
    private static final String PROVIDER_CLAUDE_CODE = "claude-code";

    private static final String DISPLAY_CLAUDE = "Claude";
    private static final String DISPLAY_CHATGPT = "ChatGPT";
    private static final String DISPLAY_OLLAMA = "Ollama";

    @Bean
    public McpToolsetFactory mcpToolsetFactory(
            @Value("${jeffrey.microscope.ai.mcp-url:http://127.0.0.1:8080/api/internal/mcp/claude-code}") String mcpUrl) {
        return new McpToolsetFactory(mcpUrl);
    }

    @Bean
    public AiChatBackend aiChatBackend(
            @Value("${jeffrey.microscope.ai.provider}") String provider,
            @Value("${jeffrey.microscope.ai.model:}") String modelName,
            @Value("${jeffrey.microscope.ai.max-tokens:4096}") int maxTokens,
            @Value("${jeffrey.microscope.ai.api-key:}") String apiKey,
            @Value("${jeffrey.microscope.ai.base-url:" + DEFAULT_OLLAMA_BASE_URL + "}") String baseUrl,
            @Value("${jeffrey.microscope.ai.cli-path:claude}") String cliPath,
            @Value("${jeffrey.microscope.ai.timeout-seconds:120}") int timeoutSeconds) {

        return switch (provider.toLowerCase()) {
            case PROVIDER_CLAUDE -> springAiBackend(
                    createAnthropicChatModel(apiKey, modelName, maxTokens), DISPLAY_CLAUDE, modelName);
            case PROVIDER_CHATGPT -> springAiBackend(
                    createOpenAiChatModel(apiKey, modelName, maxTokens), DISPLAY_CHATGPT, modelName);
            case PROVIDER_OLLAMA -> springAiBackend(
                    createOllamaChatModel(baseUrl, modelName, maxTokens), DISPLAY_OLLAMA, modelName);
            case PROVIDER_CLAUDE_CODE -> createClaudeCodeBackend(cliPath, modelName, timeoutSeconds);
            default -> throw new IllegalArgumentException("Unknown AI provider: " + provider
                    + ". Supported providers: claude, chatgpt, ollama, claude-code");
        };
    }

    private AiChatBackend springAiBackend(ChatModel chatModel, String providerDisplayName, String modelName) {
        ChatClient chatClient = ChatClient.builder(chatModel).build();
        return new SpringAiChatBackend(chatClient, providerDisplayName, modelName);
    }

    private AiChatBackend createClaudeCodeBackend(String cliPath, String modelName, int timeoutSeconds) {
        LOG.info("Creating Claude Code backend: cli_path={} model={} timeout_in_sec={}",
                cliPath, modelName.isBlank() ? "<cli-default>" : modelName, timeoutSeconds);
        ClaudeCodeCliClient cliClient = new ClaudeCodeCliClient(cliPath, Duration.ofSeconds(timeoutSeconds));
        return new ClaudeCodeChatBackend(cliClient, modelName);
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
