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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.logging.LogLevel;
import org.springframework.boot.logging.LoggingSystem;
import cafe.jeffrey.shared.common.config.MicroscopeSettingKeys;
import cafe.jeffrey.shared.common.config.SettingsChangeListener;
import cafe.jeffrey.shared.common.config.SettingsStore;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Reconfigures Jeffrey's log level when the setting changes.
 * <p>
 * The level is normally applied once at boot by Spring Boot's {@code LoggingApplicationListener}.
 * Re-applying it through the {@link LoggingSystem} is what lets a user raise the level to investigate
 * something and lower it again without restarting.
 */
public class LoggingLevelChangeListener implements SettingsChangeListener {

    private static final Logger LOG = LoggerFactory.getLogger(LoggingLevelChangeListener.class);

    private static final String LOGGER_NAME = "cafe.jeffrey";

    private static final Map<String, LogLevel> LEVELS = Map.of(
            "TRACE", LogLevel.TRACE,
            "DEBUG", LogLevel.DEBUG,
            "INFO", LogLevel.INFO,
            "WARN", LogLevel.WARN,
            "ERROR", LogLevel.ERROR,
            "OFF", LogLevel.OFF);

    private static final Set<String> OBSERVED = Set.of(MicroscopeSettingKeys.LOGGING_LEVEL);

    private final LoggingSystem loggingSystem;

    public LoggingLevelChangeListener(LoggingSystem loggingSystem) {
        this.loggingSystem = loggingSystem;
    }

    @Override
    public Set<String> observedSettings() {
        return OBSERVED;
    }

    @Override
    public void onChanged(SettingsStore store) {
        String value = store.getString(MicroscopeSettingKeys.LOGGING_LEVEL, "");
        LogLevel level = LEVELS.get(value.trim().toUpperCase(Locale.ROOT));

        if (level == null) {
            LOG.warn("Unknown log level, keeping the current one: value={}", value);
            return;
        }

        loggingSystem.setLogLevel(LOGGER_NAME, level);
        LOG.info("Log level applied: logger={} level={}", LOGGER_NAME, level);
    }
}
