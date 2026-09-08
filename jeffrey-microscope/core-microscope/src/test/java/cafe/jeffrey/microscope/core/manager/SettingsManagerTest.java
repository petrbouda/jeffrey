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
import cafe.jeffrey.shared.common.exception.JeffreyClientException;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SettingsManagerTest {

    private static final String VISUALIZATION_CATEGORY = "visualization";
    private static final String LOGGING_CATEGORY = "logging";
    private static final String FRAME_TEXT_MODE = "jeffrey.microscope.visualization.flamegraph.frame-text-mode";
    private static final String MIN_FRAME_THRESHOLD = "jeffrey.microscope.visualization.flamegraph.min-frame-threshold-pct";
    private static final String LOG_LEVEL = "logging.level.cafe.jeffrey";
    private static final String JEFFREY_LOGGER = "cafe.jeffrey";

    private static final SettingsMetadata METADATA = new SettingsMetadata(List.of(
            SettingDescriptor.of(VISUALIZATION_CATEGORY, FRAME_TEXT_MODE, "single-line"),
            SettingDescriptor.of(VISUALIZATION_CATEGORY, MIN_FRAME_THRESHOLD, "0.05"),
            SettingDescriptor.of(LOGGING_CATEGORY, LOG_LEVEL, "INFO")));

    @Mock
    private SettingsRepository settingsRepository;

    @Mock
    private LoggingSystem loggingSystem;

    private SettingsStore store;
    private SettingsManager manager;

    @BeforeEach
    void setUp() {
        store = new SettingsStore(METADATA.defaults(), Map.of());
        manager = new SettingsManager(settingsRepository, store, METADATA, loggingSystem);
    }

    @Nested
    class UpsertSetting {

        @Test
        void storesTheValue() {
            manager.upsert(VISUALIZATION_CATEGORY, FRAME_TEXT_MODE, "two-line");

            verify(settingsRepository).upsert(new Setting(VISUALIZATION_CATEGORY, FRAME_TEXT_MODE, "two-line"));
        }
    }

    @Nested
    class UpsertAppliesToStore {

        @Test
        void newValueIsImmediatelyReadable() {
            manager.upsert(VISUALIZATION_CATEGORY, FRAME_TEXT_MODE, "two-line");

            assertEquals("two-line", manager.getResolvedValue(FRAME_TEXT_MODE));
        }

        @Test
        void unwrittenSettingKeepsItsDefault() {
            manager.upsert(VISUALIZATION_CATEGORY, FRAME_TEXT_MODE, "two-line");

            assertEquals("0.05", manager.getResolvedValue(MIN_FRAME_THRESHOLD));
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
            manager.upsert(LOGGING_CATEGORY, LOG_LEVEL, "DEBUG");

            verify(loggingSystem).setLogLevel(JEFFREY_LOGGER, LogLevel.DEBUG);
        }

        @Test
        void normalisesCase() {
            manager.upsert(LOGGING_CATEGORY, LOG_LEVEL, "debug");

            verify(loggingSystem).setLogLevel(JEFFREY_LOGGER, LogLevel.DEBUG);
        }

        @Test
        void isAppliedBeforeTheCallReturns() {
            manager.upsert(LOGGING_CATEGORY, LOG_LEVEL, "WARN");

            // No awaiting: the apply is synchronous, so a plain verify is enough.
            verify(loggingSystem).setLogLevel(JEFFREY_LOGGER, LogLevel.WARN);
        }

        @Test
        void anUnrelatedSettingLeavesTheLoggingSystemAlone() {
            manager.upsert(VISUALIZATION_CATEGORY, FRAME_TEXT_MODE, "two-line");

            verifyNoInteractions(loggingSystem);
        }

        @Test
        void rewritingTheSameLevelChangesNothing() {
            manager.upsert(LOGGING_CATEGORY, LOG_LEVEL, "INFO");

            verify(settingsRepository).upsert(new Setting(LOGGING_CATEGORY, LOG_LEVEL, "INFO"));
            verifyNoInteractions(loggingSystem);
        }
    }

    @Nested
    class Validation {

        @Test
        void unknownSettingIsRejected() {
            assertThrows(JeffreyClientException.class,
                    () -> manager.upsert(VISUALIZATION_CATEGORY, "jeffrey.microscope.visualization.unknown", "x"));

            verifyNoInteractions(settingsRepository);
        }

        @Test
        void malformedPercentageIsRejected() {
            assertThrows(JeffreyClientException.class,
                    () -> manager.upsert(VISUALIZATION_CATEGORY, MIN_FRAME_THRESHOLD, "not-a-number"));

            verifyNoInteractions(settingsRepository);
        }

        @Test
        void unknownFrameTextModeIsRejected() {
            assertThrows(JeffreyClientException.class,
                    () -> manager.upsert(VISUALIZATION_CATEGORY, FRAME_TEXT_MODE, "three-line"));

            verifyNoInteractions(settingsRepository);
        }

        @Test
        void unknownLogLevelIsRejected() {
            assertThrows(JeffreyClientException.class,
                    () -> manager.upsert(LOGGING_CATEGORY, LOG_LEVEL, "VERBOSE"));

            verifyNoInteractions(settingsRepository);
            verifyNoInteractions(loggingSystem);
        }

        @Test
        void rejectedValueDoesNotReachTheStore() {
            assertThrows(JeffreyClientException.class,
                    () -> manager.upsert(VISUALIZATION_CATEGORY, MIN_FRAME_THRESHOLD, "not-a-number"));

            assertEquals("0.05", store.get(MIN_FRAME_THRESHOLD));
        }

        @Test
        void oneBadValueRejectsTheWholeBatch() {
            List<SettingUpdate> updates = List.of(
                    new SettingUpdate(VISUALIZATION_CATEGORY, FRAME_TEXT_MODE, "two-line"),
                    new SettingUpdate(VISUALIZATION_CATEGORY, MIN_FRAME_THRESHOLD, "not-a-number"));

            assertThrows(JeffreyClientException.class, () -> manager.upsertAll(updates));

            verifyNoInteractions(settingsRepository);
            assertEquals("single-line", store.get(FRAME_TEXT_MODE));
        }
    }

    @Nested
    class BatchUpsert {

        @Test
        void writesEverySettingToTheRepository() {
            manager.upsertAll(List.of(
                    new SettingUpdate(VISUALIZATION_CATEGORY, FRAME_TEXT_MODE, "two-line"),
                    new SettingUpdate(VISUALIZATION_CATEGORY, MIN_FRAME_THRESHOLD, "1.5")));

            verify(settingsRepository).upsert(new Setting(VISUALIZATION_CATEGORY, FRAME_TEXT_MODE, "two-line"));
            verify(settingsRepository).upsert(new Setting(VISUALIZATION_CATEGORY, MIN_FRAME_THRESHOLD, "1.5"));
        }

        @Test
        void everyValueIsInTheStoreWhenTheCallReturns() {
            manager.upsertAll(List.of(
                    new SettingUpdate(VISUALIZATION_CATEGORY, FRAME_TEXT_MODE, "two-line"),
                    new SettingUpdate(VISUALIZATION_CATEGORY, MIN_FRAME_THRESHOLD, "1.5")));

            assertEquals("two-line", store.get(FRAME_TEXT_MODE));
            assertEquals("1.5", store.get(MIN_FRAME_THRESHOLD));
        }

        @Test
        void appliesTheLogLevelOnceWhenItIsPartOfTheBatch() {
            manager.upsertAll(List.of(
                    new SettingUpdate(VISUALIZATION_CATEGORY, FRAME_TEXT_MODE, "two-line"),
                    new SettingUpdate(LOGGING_CATEGORY, LOG_LEVEL, "ERROR")));

            verify(loggingSystem).setLogLevel(JEFFREY_LOGGER, LogLevel.ERROR);
        }

        @Test
        void emptyBatchIsANoOp() {
            manager.upsertAll(List.of());

            verifyNoInteractions(settingsRepository);
        }
    }
}
