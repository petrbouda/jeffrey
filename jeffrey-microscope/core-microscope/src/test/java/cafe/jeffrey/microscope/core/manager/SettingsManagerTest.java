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

package cafe.jeffrey.microscope.core.manager;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import cafe.jeffrey.microscope.core.configuration.SettingDescriptor;
import cafe.jeffrey.microscope.core.configuration.SettingsMetadata;
import cafe.jeffrey.microscope.persistence.api.Setting;
import cafe.jeffrey.microscope.persistence.api.SettingsRepository;
import cafe.jeffrey.shared.common.config.SettingsChangeDispatcher;
import cafe.jeffrey.shared.common.config.SettingsChangeListener;
import cafe.jeffrey.shared.common.config.SettingsStore;
import cafe.jeffrey.shared.common.encryption.MachineFingerprint;
import cafe.jeffrey.shared.common.encryption.SecretEncryptor;
import cafe.jeffrey.shared.common.exception.JeffreyClientException;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SettingsManagerTest {

    private static final String AI_CATEGORY = "ai";
    private static final String PROVIDER = "jeffrey.microscope.ai.provider";
    private static final String API_KEY = "jeffrey.microscope.ai.api-key";
    private static final String MAX_TOKENS = "jeffrey.microscope.ai.max-tokens";
    private static final String LOG_LEVEL = "logging.level.cafe.jeffrey";

    private static final SettingsMetadata METADATA = new SettingsMetadata(List.of(
            SettingDescriptor.of(AI_CATEGORY, PROVIDER, "none", false),
            SettingDescriptor.of(AI_CATEGORY, API_KEY, "", true),
            SettingDescriptor.of(AI_CATEGORY, MAX_TOKENS, "128000", false),
            SettingDescriptor.of("logging", LOG_LEVEL, "INFO", false)));

    @Mock
    private SettingsRepository settingsRepository;

    @Mock
    private SecretEncryptor secretEncryptor;

    @Mock
    private MachineFingerprint machineFingerprint;

    /** Counts how many times listeners were applied, so batching behaviour is observable. */
    private final AtomicInteger applies = new AtomicInteger();

    private SettingsStore store;
    private SettingsChangeDispatcher dispatcher;
    private SettingsManager manager;

    @BeforeEach
    void setUp() {
        when(machineFingerprint.resolve()).thenReturn(
                new MachineFingerprint.Result("test-fingerprint", MachineFingerprint.BindingMode.MACHINE_BOUND));

        store = new SettingsStore(METADATA.defaults(), Map.of());
        dispatcher = new SettingsChangeDispatcher(store, () -> List.of(new CountingListener()));
        manager = new SettingsManager(
                settingsRepository, secretEncryptor, machineFingerprint, store, METADATA, dispatcher);
    }

    @AfterEach
    void tearDown() {
        dispatcher.close();
    }

    private final class CountingListener implements SettingsChangeListener {

        @Override
        public Set<String> observedSettings() {
            return Set.of(PROVIDER, API_KEY, MAX_TOKENS, LOG_LEVEL);
        }

        @Override
        public void onChanged(SettingsStore ignored) {
            applies.incrementAndGet();
        }
    }

    @Nested
    class UpsertSetting {

        @Test
        void storesPlainValueForNonSecret() {
            manager.upsert(AI_CATEGORY, PROVIDER, "claude", false);

            verify(settingsRepository).upsert(new Setting(AI_CATEGORY, PROVIDER, "claude", false));
            verifyNoInteractions(secretEncryptor);
        }

        @Test
        void encryptsValueForSecret() {
            when(secretEncryptor.encrypt("sk-ant-api03-key")).thenReturn("encrypted-base64");

            manager.upsert(AI_CATEGORY, API_KEY, "sk-ant-api03-key", true);

            verify(secretEncryptor).encrypt("sk-ant-api03-key");
            verify(settingsRepository).upsert(new Setting(AI_CATEGORY, API_KEY, "encrypted-base64", true));
        }
    }

    @Nested
    class UpsertAppliesToStore {

        @Test
        void newValueIsImmediatelyReadable() {
            manager.upsert(AI_CATEGORY, PROVIDER, "ollama", false);

            assertEquals("ollama", manager.getResolvedValue(PROVIDER));
        }

        @Test
        void storeHoldsPlaintextWhileTheRepositoryHoldsCiphertext() {
            when(secretEncryptor.encrypt("sk-ant-api03-key")).thenReturn("encrypted-base64");

            manager.upsert(AI_CATEGORY, API_KEY, "sk-ant-api03-key", true);

            verify(settingsRepository).upsert(new Setting(AI_CATEGORY, API_KEY, "encrypted-base64", true));
            assertEquals("sk-ant-api03-key", store.get(API_KEY));
        }

        @Test
        void unwrittenSettingKeepsItsDefault() {
            manager.upsert(AI_CATEGORY, PROVIDER, "ollama", false);

            assertEquals("128000", manager.getResolvedValue(MAX_TOKENS));
        }

        @Test
        void changeIsAppliedToListeners() {
            manager.upsert(AI_CATEGORY, PROVIDER, "ollama", false);

            await().atMost(5, SECONDS).untilAsserted(() -> assertEquals(1, applies.get()));
        }

        @Test
        void rewritingTheSameValueAppliesNothing() {
            manager.upsert(AI_CATEGORY, PROVIDER, "none", false);

            verify(settingsRepository).upsert(new Setting(AI_CATEGORY, PROVIDER, "none", false));
            await().during(Duration.ofMillis(500))
                    .atMost(5, SECONDS)
                    .untilAsserted(() -> assertEquals(0, applies.get()));
        }
    }

    @Nested
    class Validation {

        @Test
        void unknownSettingIsRejected() {
            assertThrows(JeffreyClientException.class,
                    () -> manager.upsert(AI_CATEGORY, "jeffrey.microscope.ai.unknown", "x", false));

            verifyNoInteractions(settingsRepository);
        }

        @Test
        void malformedIntIsRejected() {
            assertThrows(JeffreyClientException.class,
                    () -> manager.upsert(AI_CATEGORY, MAX_TOKENS, "not-a-number", false));

            verifyNoInteractions(settingsRepository);
        }

        @Test
        void unknownProviderIsRejected() {
            assertThrows(JeffreyClientException.class,
                    () -> manager.upsert(AI_CATEGORY, PROVIDER, "gemini", false));

            verifyNoInteractions(settingsRepository);
        }

        @Test
        void unknownLogLevelIsRejected() {
            assertThrows(JeffreyClientException.class,
                    () -> manager.upsert("logging", LOG_LEVEL, "VERBOSE", false));

            verifyNoInteractions(settingsRepository);
        }

        @Test
        void rejectedValueDoesNotReachTheStore() {
            assertThrows(JeffreyClientException.class,
                    () -> manager.upsert(AI_CATEGORY, MAX_TOKENS, "not-a-number", false));

            assertEquals("128000", store.get(MAX_TOKENS));
        }

        @Test
        void oneBadValueRejectsTheWholeBatch() {
            List<SettingUpdate> updates = List.of(
                    new SettingUpdate(AI_CATEGORY, PROVIDER, "ollama", false),
                    new SettingUpdate(AI_CATEGORY, MAX_TOKENS, "not-a-number", false));

            assertThrows(JeffreyClientException.class, () -> manager.upsertAll(updates));

            verifyNoInteractions(settingsRepository);
            assertEquals("none", store.get(PROVIDER));
        }
    }

    @Nested
    class BatchUpsert {

        @Test
        void writesEverySettingToTheRepository() {
            manager.upsertAll(List.of(
                    new SettingUpdate(AI_CATEGORY, PROVIDER, "ollama", false),
                    new SettingUpdate(AI_CATEGORY, MAX_TOKENS, "4096", false)));

            verify(settingsRepository).upsert(new Setting(AI_CATEGORY, PROVIDER, "ollama", false));
            verify(settingsRepository).upsert(new Setting(AI_CATEGORY, MAX_TOKENS, "4096", false));
        }

        @Test
        void appliesListenersOnceForTheWholeBatch() {
            manager.upsertAll(List.of(
                    new SettingUpdate(AI_CATEGORY, PROVIDER, "ollama", false),
                    new SettingUpdate(AI_CATEGORY, MAX_TOKENS, "4096", false)));

            await().atMost(5, SECONDS).untilAsserted(() -> assertEquals(1, applies.get()));
        }

        @Test
        void listenersObserveEveryValueOfTheBatch() {
            manager.upsertAll(List.of(
                    new SettingUpdate(AI_CATEGORY, PROVIDER, "ollama", false),
                    new SettingUpdate(AI_CATEGORY, MAX_TOKENS, "4096", false)));

            await().atMost(5, SECONDS).untilAsserted(() -> assertEquals(1, applies.get()));
            assertEquals("ollama", store.get(PROVIDER));
            assertEquals("4096", store.get(MAX_TOKENS));
        }

        @Test
        void emptyBatchIsANoOp() {
            manager.upsertAll(List.of());

            verifyNoInteractions(settingsRepository);
        }
    }

    @Nested
    class BindingMode {

        @Test
        void returnsMachineBound() {
            assertEquals(MachineFingerprint.BindingMode.MACHINE_BOUND, manager.getBindingMode());
        }

        @Test
        void returnsUserBoundWhenFallback() {
            when(machineFingerprint.resolve()).thenReturn(
                    new MachineFingerprint.Result("user-only", MachineFingerprint.BindingMode.USER_BOUND));

            SettingsManager fallbackManager = new SettingsManager(
                    settingsRepository, secretEncryptor, machineFingerprint, store, METADATA, dispatcher);

            assertEquals(MachineFingerprint.BindingMode.USER_BOUND, fallbackManager.getBindingMode());
        }
    }
}
