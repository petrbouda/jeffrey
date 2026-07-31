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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.boot.logging.LogLevel;
import org.springframework.boot.logging.LoggingSystem;
import cafe.jeffrey.microscope.core.configuration.SettingDescriptor;
import cafe.jeffrey.microscope.core.configuration.SettingsMetadata;
import cafe.jeffrey.microscope.persistence.api.Setting;
import cafe.jeffrey.microscope.persistence.api.SettingsRepository;
import cafe.jeffrey.shared.common.config.SettingsStore;
import cafe.jeffrey.shared.common.encryption.MachineFingerprint;
import cafe.jeffrey.shared.common.encryption.SecretEncryptor;
import cafe.jeffrey.shared.common.exception.JeffreyClientException;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SettingsManagerTest {

    private static final String AI_CATEGORY = "ai";
    private static final String LOGGING_CATEGORY = "logging";
    private static final String PROVIDER = "jeffrey.microscope.ai.provider";
    private static final String API_KEY = "jeffrey.microscope.ai.api-key";
    private static final String MAX_TOKENS = "jeffrey.microscope.ai.max-tokens";
    private static final String LOG_LEVEL = "logging.level.cafe.jeffrey";
    private static final String JEFFREY_LOGGER = "cafe.jeffrey";

    private static final SettingsMetadata METADATA = new SettingsMetadata(List.of(
            SettingDescriptor.of(AI_CATEGORY, PROVIDER, "none", false),
            SettingDescriptor.of(AI_CATEGORY, API_KEY, "", true),
            SettingDescriptor.of(AI_CATEGORY, MAX_TOKENS, "128000", false),
            SettingDescriptor.of(LOGGING_CATEGORY, LOG_LEVEL, "INFO", false)));

    @Mock
    private SettingsRepository settingsRepository;

    @Mock
    private SecretEncryptor secretEncryptor;

    @Mock
    private MachineFingerprint machineFingerprint;

    @Mock
    private LoggingSystem loggingSystem;

    private SettingsStore store;
    private SettingsManager manager;

    @BeforeEach
    void setUp() {
        when(machineFingerprint.resolve()).thenReturn(
                new MachineFingerprint.Result("test-fingerprint", MachineFingerprint.BindingMode.MACHINE_BOUND));

        store = new SettingsStore(METADATA.defaults(), Map.of());
        manager = new SettingsManager(
                settingsRepository, secretEncryptor, machineFingerprint, store, METADATA, loggingSystem);
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
    }

    /**
     * The log level is the only setting that has to be pushed somewhere; everything else is picked up
     * by whoever next reads the store.
     */
    @Nested
    class LogLevelIsAppliedDirectly {

        @Test
        void appliesTheNewLevel() {
            manager.upsert(LOGGING_CATEGORY, LOG_LEVEL, "DEBUG", false);

            verify(loggingSystem).setLogLevel(JEFFREY_LOGGER, LogLevel.DEBUG);
        }

        @Test
        void normalisesCase() {
            manager.upsert(LOGGING_CATEGORY, LOG_LEVEL, "debug", false);

            verify(loggingSystem).setLogLevel(JEFFREY_LOGGER, LogLevel.DEBUG);
        }

        @Test
        void isAppliedBeforeTheCallReturns() {
            manager.upsert(LOGGING_CATEGORY, LOG_LEVEL, "WARN", false);

            // No awaiting: the apply is synchronous, so a plain verify is enough.
            verify(loggingSystem).setLogLevel(JEFFREY_LOGGER, LogLevel.WARN);
        }

        @Test
        void anUnrelatedSettingLeavesTheLoggingSystemAlone() {
            manager.upsert(AI_CATEGORY, PROVIDER, "ollama", false);

            verifyNoInteractions(loggingSystem);
        }

        @Test
        void rewritingTheSameLevelChangesNothing() {
            manager.upsert(LOGGING_CATEGORY, LOG_LEVEL, "INFO", false);

            verify(settingsRepository).upsert(new Setting(LOGGING_CATEGORY, LOG_LEVEL, "INFO", false));
            verifyNoInteractions(loggingSystem);
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
                    () -> manager.upsert(LOGGING_CATEGORY, LOG_LEVEL, "VERBOSE", false));

            verifyNoInteractions(settingsRepository);
            verifyNoInteractions(loggingSystem);
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
        void everyValueIsInTheStoreWhenTheCallReturns() {
            manager.upsertAll(List.of(
                    new SettingUpdate(AI_CATEGORY, PROVIDER, "ollama", false),
                    new SettingUpdate(AI_CATEGORY, MAX_TOKENS, "4096", false)));

            assertEquals("ollama", store.get(PROVIDER));
            assertEquals("4096", store.get(MAX_TOKENS));
        }

        @Test
        void appliesTheLogLevelOnceWhenItIsPartOfTheBatch() {
            manager.upsertAll(List.of(
                    new SettingUpdate(AI_CATEGORY, PROVIDER, "ollama", false),
                    new SettingUpdate(LOGGING_CATEGORY, LOG_LEVEL, "ERROR", false)));

            verify(loggingSystem).setLogLevel(JEFFREY_LOGGER, LogLevel.ERROR);
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
                    settingsRepository, secretEncryptor, machineFingerprint, store, METADATA, loggingSystem);

            assertEquals(MachineFingerprint.BindingMode.USER_BOUND, fallbackManager.getBindingMode());
        }
    }
}
