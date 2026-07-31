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

package cafe.jeffrey.microscope.core.configuration;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.logging.LogLevel;
import org.springframework.boot.logging.LoggingSystem;
import cafe.jeffrey.shared.common.config.MicroscopeSettingKeys;
import cafe.jeffrey.shared.common.config.SettingsStore;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class LoggingLevelChangeListenerTest {

    private static final String LOGGER_NAME = "cafe.jeffrey";

    private static final Map<String, String> DEFAULTS =
            Map.of(MicroscopeSettingKeys.LOGGING_LEVEL, "INFO");

    @Mock
    private LoggingSystem loggingSystem;

    private SettingsStore storeWith(String level) {
        return new SettingsStore(DEFAULTS, Map.of(MicroscopeSettingKeys.LOGGING_LEVEL, level));
    }

    @Nested
    class AppliesLevel {

        @Test
        void appliesAnUpperCaseLevel() {
            new LoggingLevelChangeListener(loggingSystem).onChanged(storeWith("DEBUG"));

            verify(loggingSystem).setLogLevel(LOGGER_NAME, LogLevel.DEBUG);
        }

        @Test
        void normalisesALowerCaseLevel() {
            new LoggingLevelChangeListener(loggingSystem).onChanged(storeWith("debug"));

            verify(loggingSystem).setLogLevel(LOGGER_NAME, LogLevel.DEBUG);
        }

        @Test
        void ignoresSurroundingWhitespace() {
            new LoggingLevelChangeListener(loggingSystem).onChanged(storeWith("  warn  "));

            verify(loggingSystem).setLogLevel(LOGGER_NAME, LogLevel.WARN);
        }

        @Test
        void appliesOff() {
            new LoggingLevelChangeListener(loggingSystem).onChanged(storeWith("OFF"));

            verify(loggingSystem).setLogLevel(LOGGER_NAME, LogLevel.OFF);
        }

        @Test
        void appliesTrace() {
            new LoggingLevelChangeListener(loggingSystem).onChanged(storeWith("TRACE"));

            verify(loggingSystem).setLogLevel(LOGGER_NAME, LogLevel.TRACE);
        }
    }

    @Nested
    class RejectsUnknownLevel {

        @Test
        void leavesTheLoggingSystemUntouched() {
            new LoggingLevelChangeListener(loggingSystem).onChanged(storeWith("VERBOSE"));

            verifyNoInteractions(loggingSystem);
        }

        @Test
        void leavesTheLoggingSystemUntouchedForBlank() {
            new LoggingLevelChangeListener(loggingSystem).onChanged(storeWith(""));

            verifyNoInteractions(loggingSystem);
        }
    }

    @Nested
    class Observation {

        @Test
        void observesTheLogLevelSetting() {
            assertTrue(new LoggingLevelChangeListener(loggingSystem)
                    .observedSettings()
                    .contains(MicroscopeSettingKeys.LOGGING_LEVEL));
        }
    }
}
