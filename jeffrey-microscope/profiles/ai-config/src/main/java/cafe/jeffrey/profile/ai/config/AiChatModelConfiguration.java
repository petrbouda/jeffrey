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

import org.springframework.context.annotation.Bean;
import cafe.jeffrey.profile.ai.chat.AiChatBackend;
import cafe.jeffrey.profile.ai.chat.McpToolsetFactory;
import cafe.jeffrey.profile.ai.chat.SettingsDrivenAiChatBackend;
import cafe.jeffrey.shared.common.config.MicroscopeSettingKeys;
import cafe.jeffrey.shared.common.config.SettingsStore;

import java.util.List;

/**
 * Wires the AI backend.
 * <p>
 * The provider used to decide which beans existed: the backend, the assistant services and even an MCP
 * controller were gated on {@code jeffrey.microscope.ai.provider}, which froze the choice at startup
 * because bean existence cannot be revisited. The wiring is now identical for every provider —
 * including {@code none} — and the provider is looked up per call, so it can be changed while the
 * application runs.
 * <p>
 * The Spring AI providers are declared below; the Claude Code provider contributes its own
 * {@link AiBackendProvider} bean from the module that owns the CLI client, and the factory discovers
 * them all through the injected list.
 */
public class AiChatModelConfiguration {

    private static final String DEFAULT_MCP_URL = "http://127.0.0.1:8080/api/internal/mcp/claude-code";

    @Bean
    public McpToolsetFactory mcpToolsetFactory(SettingsStore settingsStore) {
        return new McpToolsetFactory(
                () -> settingsStore.getString(MicroscopeSettingKeys.AI_MCP_URL, DEFAULT_MCP_URL));
    }

    @Bean
    public AiBackendProvider anthropicBackendProvider() {
        return new SpringAiBackendProvider("claude", "Claude",
                settings -> SpringAiBackendFactory.anthropic(
                        settings.apiKey(), settings.model(), settings.maxTokens()));
    }

    @Bean
    public AiBackendProvider openAiBackendProvider() {
        return new SpringAiBackendProvider("chatgpt", "ChatGPT",
                settings -> SpringAiBackendFactory.openAi(
                        settings.apiKey(), settings.model(), settings.maxTokens()));
    }

    @Bean
    public AiBackendProvider ollamaBackendProvider() {
        return new SpringAiBackendProvider("ollama", "Ollama",
                settings -> SpringAiBackendFactory.ollama(
                        settings.baseUrl(), settings.model(), settings.maxTokens()));
    }

    @Bean
    public AiBackendFactory aiBackendFactory(List<AiBackendProvider> providers) {
        return new AiBackendFactory(providers);
    }

    @Bean
    public AiChatBackend aiChatBackend(AiBackendFactory factory, SettingsStore settingsStore) {
        return new SettingsDrivenAiChatBackend(factory, settingsStore);
    }
}
