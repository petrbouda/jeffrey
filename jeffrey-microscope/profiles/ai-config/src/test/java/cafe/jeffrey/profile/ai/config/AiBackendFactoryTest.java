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

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import cafe.jeffrey.profile.ai.chat.AiChatBackend;
import cafe.jeffrey.profile.ai.chat.ChatExchange;
import cafe.jeffrey.profile.ai.chat.ToolCallResult;
import cafe.jeffrey.profile.ai.chat.ToolExchange;
import cafe.jeffrey.shared.common.config.MicroscopeSettingKeys;
import cafe.jeffrey.shared.common.config.SettingsStore;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiBackendFactoryTest {

    private static final Map<String, String> DEFAULTS = Map.of(
            MicroscopeSettingKeys.AI_PROVIDER, MicroscopeSettingKeys.PROVIDER_NONE,
            MicroscopeSettingKeys.AI_MODEL, "claude-opus-4-8",
            MicroscopeSettingKeys.AI_MAX_TOKENS, "128000",
            MicroscopeSettingKeys.AI_API_KEY, "",
            MicroscopeSettingKeys.AI_BASE_URL, "http://localhost:11434",
            MicroscopeSettingKeys.AI_CLI_PATH, "claude",
            MicroscopeSettingKeys.AI_TIMEOUT_SECONDS, "120");

    /** Stands in for a real provider without building any SDK client. */
    private record StubProvider(String providerId) implements AiBackendProvider {

        @Override
        public String displayName() {
            return providerId;
        }

        @Override
        public AiChatBackend create(AiSettings settings) {
            return new StubBackend(providerId);
        }
    }

    private record StubBackend(String name) implements AiChatBackend {

        @Override
        public boolean isAvailable() {
            return true;
        }

        @Override
        public String providerName() {
            return name;
        }

        @Override
        public String modelName() {
            return name;
        }

        @Override
        public String chat(ChatExchange exchange, String spanName) {
            return name;
        }

        @Override
        public ToolCallResult analyze(ToolExchange exchange) {
            return new ToolCallResult(name, List.of());
        }
    }

    private static AiBackendFactory factory() {
        return new AiBackendFactory(List.of(
                new StubProvider("claude"),
                new StubProvider("chatgpt"),
                new StubProvider("ollama"),
                new StubProvider("claude-code")));
    }

    private static AiSettings settingsFor(String provider) {
        SettingsStore store = new SettingsStore(DEFAULTS, Map.of(MicroscopeSettingKeys.AI_PROVIDER, provider));
        return AiSettings.from(store);
    }

    @Nested
    class ProviderLookup {

        @Test
        void selectsTheMatchingProvider() {
            assertEquals("claude", factory().find(settingsFor("claude")).orElseThrow().providerId());
        }

        @Test
        void selectsAProviderContributedByAnotherModule() {
            assertEquals("claude-code", factory().find(settingsFor("claude-code")).orElseThrow().providerId());
        }

        @Test
        void isCaseInsensitive() {
            assertEquals("ollama", factory().find(settingsFor("OLLAMA")).orElseThrow().providerId());
        }

        @Test
        void ignoresSurroundingWhitespace() {
            assertEquals("chatgpt", factory().find(settingsFor("  chatgpt  ")).orElseThrow().providerId());
        }

        @Test
        void exposesEveryKnownProvider() {
            assertEquals(Set.of("claude", "chatgpt", "ollama", "claude-code"), factory().knownProviders());
        }
    }

    @Nested
    class NoProvider {

        @Test
        void noneResolvesToEmpty() {
            assertTrue(factory().find(settingsFor("none")).isEmpty());
        }

        @Test
        void anUnknownProviderResolvesToEmptyRatherThanFailing() {
            assertTrue(factory().find(settingsFor("gemini")).isEmpty());
        }

        @Test
        void anEmptyProviderResolvesToEmpty() {
            assertTrue(factory().find(settingsFor("")).isEmpty());
        }

        @Test
        void anEmptyRegistryResolvesToEmpty() {
            assertTrue(new AiBackendFactory(List.of()).find(settingsFor("claude")).isEmpty());
        }
    }

    @Nested
    class SettingsSnapshot {

        @Test
        void readsEveryFieldFromTheStore() {
            SettingsStore store = new SettingsStore(DEFAULTS, Map.of(
                    MicroscopeSettingKeys.AI_PROVIDER, "ollama",
                    MicroscopeSettingKeys.AI_MODEL, "llama4",
                    MicroscopeSettingKeys.AI_MAX_TOKENS, "8192",
                    MicroscopeSettingKeys.AI_BASE_URL, "http://ollama:11434",
                    MicroscopeSettingKeys.AI_API_KEY, "sk-test",
                    MicroscopeSettingKeys.AI_CLI_PATH, "/usr/bin/claude",
                    MicroscopeSettingKeys.AI_TIMEOUT_SECONDS, "300"));

            AiSettings settings = AiSettings.from(store);

            assertEquals("ollama", settings.provider());
            assertEquals("llama4", settings.model());
            assertEquals(8192, settings.maxTokens());
            assertEquals("http://ollama:11434", settings.baseUrl());
            assertEquals("sk-test", settings.apiKey());
            assertEquals("/usr/bin/claude", settings.cliPath());
            assertEquals(Duration.ofSeconds(300), settings.timeout());
        }

        @Test
        void lowerCasesTheProvider() {
            assertEquals("claude", settingsFor("CLAUDE").provider());
        }

        @Test
        void blankProviderBecomesNone() {
            assertEquals(MicroscopeSettingKeys.PROVIDER_NONE, settingsFor("   ").provider());
        }

        @Test
        void malformedTimeoutFallsBackInsteadOfFailing() {
            SettingsStore store = new SettingsStore(
                    DEFAULTS, Map.of(MicroscopeSettingKeys.AI_TIMEOUT_SECONDS, "not-a-number"));

            assertEquals(Duration.ofSeconds(120), AiSettings.from(store).timeout());
        }

        @Test
        void rejectsANonPositiveTimeout() {
            assertThrows(IllegalArgumentException.class, () -> new AiSettings(
                    "claude", "model", 100, "", "url", "claude", Duration.ZERO));
        }

        @Test
        void rejectsABlankProvider() {
            assertThrows(IllegalArgumentException.class, () -> new AiSettings(
                    "", "model", 100, "", "url", "claude", Duration.ofSeconds(1)));
        }
    }
}
