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
import cafe.jeffrey.shared.common.config.MicroscopeSettingKeys;
import cafe.jeffrey.shared.common.config.SettingsStore;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReloadableAiChatBackendTest {

    private static final Map<String, String> DEFAULTS =
            Map.of(MicroscopeSettingKeys.AI_PROVIDER, MicroscopeSettingKeys.PROVIDER_NONE);

    private static final ChatExchange EXCHANGE = new ChatExchange("system", List.of(), "hello");

    private static SettingsStore store() {
        return new SettingsStore(DEFAULTS, Map.of());
    }

    /** A backend that reports a fixed name, so a swap is observable through the delegate's identity. */
    private static class NamedBackend implements AiChatBackend {

        private final String name;
        private final AtomicInteger closes = new AtomicInteger();

        private NamedBackend(String name) {
            this.name = name;
        }

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
            return name + "-model";
        }

        @Override
        public String chat(ChatExchange exchange, String spanName) {
            return name;
        }

        @Override
        public ToolCallResult analyze(ToolExchange exchange) {
            return new ToolCallResult(name, List.of());
        }

        @Override
        public void close() {
            closes.incrementAndGet();
        }
    }

    private static final Consumer<AiChatBackend> CLOSE_IMMEDIATELY = AiChatBackend::close;

    @Nested
    class Swap {

        @Test
        void startsWithTheInitialBackend() {
            NamedBackend initial = new NamedBackend("ollama");
            ReloadableAiChatBackend backend =
                    new ReloadableAiChatBackend(ignored -> initial, CLOSE_IMMEDIATELY, initial);

            assertEquals("ollama", backend.providerName());
        }

        @Test
        void delegatesToTheRebuiltBackendAfterAChange() {
            NamedBackend rebuilt = new NamedBackend("claude");
            ReloadableAiChatBackend backend = new ReloadableAiChatBackend(
                    ignored -> rebuilt, CLOSE_IMMEDIATELY, new NamedBackend("ollama"));

            backend.onChanged(store());

            assertEquals("claude", backend.providerName());
            assertEquals("claude-model", backend.modelName());
            assertEquals("claude", backend.chat(EXCHANGE, "span"));
        }

        @Test
        void availabilityFollowsTheNewBackend() {
            ReloadableAiChatBackend backend = new ReloadableAiChatBackend(
                    ignored -> new DisabledAiChatBackend(), CLOSE_IMMEDIATELY, new NamedBackend("claude"));

            assertTrue(backend.isAvailable());
            backend.onChanged(store());
            assertFalse(backend.isAvailable());
        }

        @Test
        void replacedBackendIsHandedToCleanup() {
            NamedBackend initial = new NamedBackend("ollama");
            AtomicReference<AiChatBackend> cleaned = new AtomicReference<>();

            ReloadableAiChatBackend backend = new ReloadableAiChatBackend(
                    ignored -> new NamedBackend("claude"), cleaned::set, initial);

            backend.onChanged(store());

            assertSame(initial, cleaned.get());
        }

        @Test
        void observesEverySettingThatAffectsTheBackend() {
            ReloadableAiChatBackend backend = new ReloadableAiChatBackend(
                    ignored -> new NamedBackend("claude"), CLOSE_IMMEDIATELY, new NamedBackend("claude"));

            assertTrue(backend.observedSettings().containsAll(List.of(
                    MicroscopeSettingKeys.AI_PROVIDER,
                    MicroscopeSettingKeys.AI_MODEL,
                    MicroscopeSettingKeys.AI_MAX_TOKENS,
                    MicroscopeSettingKeys.AI_API_KEY,
                    MicroscopeSettingKeys.AI_BASE_URL,
                    MicroscopeSettingKeys.AI_CLI_PATH,
                    MicroscopeSettingKeys.AI_TIMEOUT_SECONDS)));
        }
    }

    @Nested
    class SwapFailure {

        @Test
        void keepsTheWorkingBackendWhenTheRebuildThrows() {
            NamedBackend initial = new NamedBackend("ollama");
            ReloadableAiChatBackend backend = new ReloadableAiChatBackend(
                    ignored -> {
                        throw new IllegalStateException("bad api key");
                    },
                    CLOSE_IMMEDIATELY,
                    initial);

            assertThrows(IllegalStateException.class, () -> backend.onChanged(store()));

            assertEquals("ollama", backend.providerName());
            assertTrue(backend.isAvailable());
        }

        @Test
        void doesNotCloseTheStillActiveBackendWhenTheRebuildThrows() {
            NamedBackend initial = new NamedBackend("ollama");
            ReloadableAiChatBackend backend = new ReloadableAiChatBackend(
                    ignored -> {
                        throw new IllegalStateException("bad api key");
                    },
                    CLOSE_IMMEDIATELY,
                    initial);

            assertThrows(IllegalStateException.class, () -> backend.onChanged(store()));

            assertEquals(0, initial.closes.get());
        }
    }

    @Nested
    class InFlightCalls {

        /**
         * A call that started before the swap must finish against the backend it began with. Otherwise a
         * provider change could tear a request in half — the reason the delegate is read once per call.
         */
        @Test
        void callInProgressCompletesAgainstTheBackendItStartedOn() throws Exception {
            CountDownLatch callStarted = new CountDownLatch(1);
            CountDownLatch swapDone = new CountDownLatch(1);
            AtomicReference<String> observed = new AtomicReference<>();

            AiChatBackend slowInitial = new NamedBackend("ollama") {
                @Override
                public String chat(ChatExchange exchange, String spanName) {
                    callStarted.countDown();
                    try {
                        swapDone.await(5, TimeUnit.SECONDS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    return "ollama";
                }
            };

            ReloadableAiChatBackend backend = new ReloadableAiChatBackend(
                    ignored -> new NamedBackend("claude"), CLOSE_IMMEDIATELY, slowInitial);

            Thread caller = new Thread(() -> observed.set(backend.chat(EXCHANGE, "span")));
            caller.start();

            assertTrue(callStarted.await(5, TimeUnit.SECONDS));
            backend.onChanged(store());
            swapDone.countDown();
            caller.join(5_000);

            assertEquals("ollama", observed.get());
            assertEquals("claude", backend.providerName());
        }
    }

    @Nested
    class Disabled {

        @Test
        void reportsUnavailable() {
            assertFalse(new DisabledAiChatBackend().isAvailable());
        }

        @Test
        void reportsNullProviderSoStatusPayloadsStayUnchanged() {
            assertEquals(null, new DisabledAiChatBackend().providerName());
            assertEquals(null, new DisabledAiChatBackend().modelName());
        }

        @Test
        void refusesToChat() {
            assertThrows(IllegalStateException.class,
                    () -> new DisabledAiChatBackend().chat(EXCHANGE, "span"));
        }

        @Test
        void refusesToAnalyze() {
            ToolExchange exchange = new ToolExchange("system", List.of(), "hello", null, "span");
            assertThrows(IllegalStateException.class, () -> new DisabledAiChatBackend().analyze(exchange));
        }
    }

    /** Guards the contract the configuration relies on: the builder receives the live store. */
    @Nested
    class BuilderInput {

        @Test
        void rebuildSeesTheCurrentSettings() {
            SettingsStore store = store();
            store.put(MicroscopeSettingKeys.AI_PROVIDER, "claude");
            AtomicReference<String> seen = new AtomicReference<>();

            Function<SettingsStore, AiChatBackend> builder = s -> {
                seen.set(s.get(MicroscopeSettingKeys.AI_PROVIDER));
                return new NamedBackend("claude");
            };

            new ReloadableAiChatBackend(builder, CLOSE_IMMEDIATELY, new NamedBackend("ollama"))
                    .onChanged(store);

            assertEquals("claude", seen.get());
        }
    }
}
