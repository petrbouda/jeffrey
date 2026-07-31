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
import cafe.jeffrey.profile.ai.chat.ReloadableAiChatBackend;
import cafe.jeffrey.shared.common.config.MicroscopeSettingKeys;
import cafe.jeffrey.shared.common.config.SettingsChangeDispatcher;
import cafe.jeffrey.shared.common.config.SettingsStore;

import java.time.Duration;
import java.util.List;

/**
 * Wires the AI backend as a single, always-present {@link ReloadableAiChatBackend}.
 * <p>
 * The provider used to decide which beans existed: the backend, the assistant services and even an MCP
 * controller were gated on {@code jeffrey.microscope.ai.provider}, which froze the choice at startup
 * because bean existence cannot be revisited. The wiring is now identical for every provider —
 * including {@code none} — and the provider only decides what the backend delegates to, so it can be
 * changed while the application runs.
 * <p>
 * The Spring AI providers (Claude, ChatGPT, Ollama) are declared here; the Claude Code provider
 * contributes its own {@link AiBackendProvider} bean from the module that owns the CLI client. The
 * factory discovers them all through the injected list.
 */
public class AiChatModelConfiguration {

    /**
     * Lower bound on how long a replaced backend is kept alive before its SDK client is closed. The AI
     * timeout is the real bound on an in-flight call; this floor covers a very short configured timeout.
     */
    private static final Duration MINIMUM_REPLACED_BACKEND_GRACE = Duration.ofSeconds(60);

    private static final String DEFAULT_MCP_URL = "http://127.0.0.1:8080/api/internal/mcp/claude-code";

    @Bean
    public McpToolsetFactory mcpToolsetFactory(SettingsStore settingsStore) {
        return new McpToolsetFactory(
                () -> settingsStore.getString(MicroscopeSettingKeys.AI_MCP_URL, DEFAULT_MCP_URL));
    }

    @Bean
    public AiBackendProvider anthropicBackendProvider() {
        return new AnthropicBackendProvider();
    }

    @Bean
    public AiBackendProvider openAiBackendProvider() {
        return new OpenAiBackendProvider();
    }

    @Bean
    public AiBackendProvider ollamaBackendProvider() {
        return new OllamaBackendProvider();
    }

    @Bean
    public AiBackendFactory aiBackendFactory(List<AiBackendProvider> providers) {
        return new AiBackendFactory(providers);
    }

    /**
     * Declared with the concrete type so this one bean satisfies both the {@code AiChatBackend}
     * injection points of the assistants and the {@code SettingsChangeListener} collected by the
     * settings dispatcher.
     */
    @Bean
    public ReloadableAiChatBackend aiChatBackend(
            AiBackendFactory factory,
            SettingsStore settingsStore,
            SettingsChangeDispatcher changeDispatcher) {

        return new ReloadableAiChatBackend(
                store -> factory.create(AiSettings.from(store)),
                replaced -> scheduleClose(replaced, settingsStore, changeDispatcher),
                factory.create(AiSettings.from(settingsStore)));
    }

    /**
     * Closing a replaced backend immediately would break calls that resolved it just before the swap.
     * Deferring past the configured AI timeout — the hard upper bound on any single call — is enough to
     * be sure none is still running.
     */
    private static void scheduleClose(
            AiChatBackend replaced,
            SettingsStore settingsStore,
            SettingsChangeDispatcher changeDispatcher) {

        Duration grace = AiSettings.from(settingsStore).timeout();
        if (grace.compareTo(MINIMUM_REPLACED_BACKEND_GRACE) < 0) {
            grace = MINIMUM_REPLACED_BACKEND_GRACE;
        }

        changeDispatcher.scheduleCleanup(replaced::close, grace);
    }
}
