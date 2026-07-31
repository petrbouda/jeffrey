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

package cafe.jeffrey.profile.ai.chat;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import cafe.jeffrey.profile.ai.config.AiBackendFactory;
import cafe.jeffrey.profile.ai.config.AiBackendProvider;
import cafe.jeffrey.profile.ai.config.AiSettings;
import cafe.jeffrey.shared.common.config.MicroscopeSettingKeys;
import cafe.jeffrey.shared.common.config.SettingsStore;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SettingsDrivenAiChatBackendTest {

    private static final Map<String, String> DEFAULTS = Map.of(
            MicroscopeSettingKeys.AI_PROVIDER, MicroscopeSettingKeys.PROVIDER_NONE,
            MicroscopeSettingKeys.AI_MODEL, "some-model",
            MicroscopeSettingKeys.AI_MAX_TOKENS, "4096",
            MicroscopeSettingKeys.AI_API_KEY, "",
            MicroscopeSettingKeys.AI_BASE_URL, "http://localhost:11434",
            MicroscopeSettingKeys.AI_CLI_PATH, "claude",
            MicroscopeSettingKeys.AI_TIMEOUT_SECONDS, "120");

    private static final ChatExchange EXCHANGE = new ChatExchange("system", List.of(), "hello");

    /** Records how many were built and how many were closed, so lifetime is observable. */
    private static final class CountingProvider implements AiBackendProvider {

        private final String id;
        private final String display;
        private final boolean available;
        private final AtomicInteger built = new AtomicInteger();
        private final AtomicInteger closed = new AtomicInteger();

        private CountingProvider(String id, String display, boolean available) {
            this.id = id;
            this.display = display;
            this.available = available;
        }

        @Override
        public String providerId() {
            return id;
        }

        @Override
        public String displayName() {
            return display;
        }

        @Override
        public boolean isAvailable(AiSettings settings) {
            return available;
        }

        @Override
        public AiChatBackend create(AiSettings settings) {
            built.incrementAndGet();
            return new StubBackend(display, settings.model(), closed);
        }
    }

    private static class StubBackend implements AiChatBackend {

        private final String display;
        private final String model;
        private final AtomicInteger closed;

        private StubBackend(String display, String model, AtomicInteger closed) {
            this.display = display;
            this.model = model;
            this.closed = closed;
        }

        @Override
        public boolean isAvailable() {
            return true;
        }

        @Override
        public String providerName() {
            return display;
        }

        @Override
        public String modelName() {
            return model;
        }

        @Override
        public String chat(ChatExchange exchange, String spanName) {
            return display + ":" + model;
        }

        @Override
        public ToolCallResult analyze(ToolExchange exchange) {
            return new ToolCallResult(display + ":" + model, List.of());
        }

        @Override
        public void close() {
            closed.incrementAndGet();
        }
    }

    private static SettingsStore store(String provider) {
        return new SettingsStore(DEFAULTS, Map.of(MicroscopeSettingKeys.AI_PROVIDER, provider));
    }

    @Nested
    class ReadsSettingsPerCall {

        @Test
        void aChangedProviderIsVisibleToTheNextCallWithoutAnyNotification() {
            SettingsStore store = store(MicroscopeSettingKeys.PROVIDER_NONE);
            AiBackendFactory factory = new AiBackendFactory(List.of(
                    new CountingProvider("ollama", "Ollama", true)));
            SettingsDrivenAiChatBackend backend = new SettingsDrivenAiChatBackend(factory, store);

            assertFalse(backend.isAvailable());

            store.put(MicroscopeSettingKeys.AI_PROVIDER, "ollama");

            assertTrue(backend.isAvailable());
            assertEquals("Ollama", backend.providerName());
        }

        @Test
        void aChangedModelIsVisibleToTheNextCall() {
            SettingsStore store = store("ollama");
            AiBackendFactory factory = new AiBackendFactory(List.of(
                    new CountingProvider("ollama", "Ollama", true)));
            SettingsDrivenAiChatBackend backend = new SettingsDrivenAiChatBackend(factory, store);

            assertEquals("Ollama:some-model", backend.chat(EXCHANGE, "span"));

            store.put(MicroscopeSettingKeys.AI_MODEL, "llama4");

            assertEquals("Ollama:llama4", backend.chat(EXCHANGE, "span"));
        }

        @Test
        void switchingProviderIsVisibleToTheNextCall() {
            SettingsStore store = store("ollama");
            AiBackendFactory factory = new AiBackendFactory(List.of(
                    new CountingProvider("ollama", "Ollama", true),
                    new CountingProvider("claude", "Claude", true)));
            SettingsDrivenAiChatBackend backend = new SettingsDrivenAiChatBackend(factory, store);

            assertEquals("Ollama", backend.providerName());

            store.put(MicroscopeSettingKeys.AI_PROVIDER, "claude");

            assertEquals("Claude", backend.providerName());
        }
    }

    @Nested
    class BackendLifetimeIsTheCall {

        @Test
        void chatBuildsAndClosesExactlyOneBackend() {
            CountingProvider provider = new CountingProvider("ollama", "Ollama", true);
            SettingsDrivenAiChatBackend backend =
                    new SettingsDrivenAiChatBackend(new AiBackendFactory(List.of(provider)), store("ollama"));

            backend.chat(EXCHANGE, "span");

            assertEquals(1, provider.built.get());
            assertEquals(1, provider.closed.get());
        }

        @Test
        void analyzeBuildsAndClosesExactlyOneBackend() {
            CountingProvider provider = new CountingProvider("ollama", "Ollama", true);
            SettingsDrivenAiChatBackend backend =
                    new SettingsDrivenAiChatBackend(new AiBackendFactory(List.of(provider)), store("ollama"));

            backend.analyze(new ToolExchange("system", List.of(), "hello", null, "span"));

            assertEquals(1, provider.built.get());
            assertEquals(1, provider.closed.get());
        }

        @Test
        void eachCallGetsItsOwnBackend() {
            CountingProvider provider = new CountingProvider("ollama", "Ollama", true);
            SettingsDrivenAiChatBackend backend =
                    new SettingsDrivenAiChatBackend(new AiBackendFactory(List.of(provider)), store("ollama"));

            backend.chat(EXCHANGE, "span");
            backend.chat(EXCHANGE, "span");

            assertEquals(2, provider.built.get());
            assertEquals(2, provider.closed.get());
        }

        @Test
        void theBackendIsClosedEvenWhenTheCallThrows() {
            AtomicInteger closed = new AtomicInteger();
            AiBackendProvider provider = new AiBackendProvider() {
                @Override
                public String providerId() {
                    return "ollama";
                }

                @Override
                public String displayName() {
                    return "Ollama";
                }

                @Override
                public AiChatBackend create(AiSettings settings) {
                    return new StubBackend("Ollama", "m", closed) {
                        @Override
                        public String chat(ChatExchange exchange, String spanName) {
                            throw new IllegalStateException("call failed");
                        }
                    };
                }
            };
            SettingsDrivenAiChatBackend backend =
                    new SettingsDrivenAiChatBackend(new AiBackendFactory(List.of(provider)), store("ollama"));

            assertThrows(IllegalStateException.class, () -> backend.chat(EXCHANGE, "span"));

            assertEquals(1, closed.get());
        }
    }

    @Nested
    class ReadOnlyMethodsNeverBuild {

        @Test
        void isAvailableBuildsNothing() {
            CountingProvider provider = new CountingProvider("ollama", "Ollama", true);
            SettingsDrivenAiChatBackend backend =
                    new SettingsDrivenAiChatBackend(new AiBackendFactory(List.of(provider)), store("ollama"));

            backend.isAvailable();

            assertEquals(0, provider.built.get());
        }

        @Test
        void providerAndModelNamesBuildNothing() {
            CountingProvider provider = new CountingProvider("ollama", "Ollama", true);
            SettingsDrivenAiChatBackend backend =
                    new SettingsDrivenAiChatBackend(new AiBackendFactory(List.of(provider)), store("ollama"));

            assertEquals("Ollama", backend.providerName());
            assertEquals("some-model", backend.modelName());
            assertEquals(0, provider.built.get());
        }

        @Test
        void availabilityFollowsTheProvidersOwnAnswer() {
            SettingsDrivenAiChatBackend backend = new SettingsDrivenAiChatBackend(
                    new AiBackendFactory(List.of(new CountingProvider("claude-code", "Claude Code", false))),
                    store("claude-code"));

            assertFalse(backend.isAvailable());
        }
    }

    @Nested
    class NotConfigured {

        @Test
        void reportsUnavailable() {
            assertFalse(new SettingsDrivenAiChatBackend(
                    new AiBackendFactory(List.of()), store(MicroscopeSettingKeys.PROVIDER_NONE)).isAvailable());
        }

        @Test
        void reportsNullNamesSoStatusPayloadsAreUnchanged() {
            SettingsDrivenAiChatBackend backend = new SettingsDrivenAiChatBackend(
                    new AiBackendFactory(List.of()), store(MicroscopeSettingKeys.PROVIDER_NONE));

            assertNull(backend.providerName());
            assertNull(backend.modelName());
        }

        @Test
        void refusesToChat() {
            SettingsDrivenAiChatBackend backend = new SettingsDrivenAiChatBackend(
                    new AiBackendFactory(List.of()), store(MicroscopeSettingKeys.PROVIDER_NONE));

            assertThrows(IllegalStateException.class, () -> backend.chat(EXCHANGE, "span"));
        }

        @Test
        void anUnknownProviderIsTreatedAsNotConfigured() {
            SettingsDrivenAiChatBackend backend = new SettingsDrivenAiChatBackend(
                    new AiBackendFactory(List.of(new CountingProvider("ollama", "Ollama", true))),
                    store("gemini"));

            assertFalse(backend.isAvailable());
            assertNull(backend.providerName());
        }

        @Test
        void aBuildFailurePropagatesToTheCaller() {
            AiBackendProvider failing = new AiBackendProvider() {
                @Override
                public String providerId() {
                    return "claude";
                }

                @Override
                public String displayName() {
                    return "Claude";
                }

                @Override
                public AiChatBackend create(AiSettings settings) {
                    throw new IllegalStateException("bad api key");
                }
            };
            SettingsDrivenAiChatBackend backend =
                    new SettingsDrivenAiChatBackend(new AiBackendFactory(List.of(failing)), store("claude"));

            assertThrows(IllegalStateException.class, () -> backend.chat(EXCHANGE, "span"));
        }
    }
}
