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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.logging.LogLevel;
import org.springframework.boot.logging.LoggingSystem;
import cafe.jeffrey.microscope.core.configuration.SettingDescriptor;
import cafe.jeffrey.microscope.core.configuration.SettingsMetadata;
import cafe.jeffrey.microscope.persistence.api.Setting;
import cafe.jeffrey.microscope.persistence.api.SettingsRepository;
import cafe.jeffrey.shared.common.config.MicroscopeSettingKeys;
import cafe.jeffrey.shared.common.config.SettingsStore;
import cafe.jeffrey.shared.common.encryption.MachineFingerprint;
import cafe.jeffrey.shared.common.encryption.SecretEncryptor;
import cafe.jeffrey.shared.common.exception.Exceptions;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Business logic for application settings. Validates incoming values, encrypts secrets, persists the
 * change, and applies it to the running application.
 * <p>
 * A saved setting takes effect immediately, and by the time this method returns: the plaintext value
 * is written into the live {@link SettingsStore}, and everything that consumes a setting reads the
 * store at the point of use. There is nothing to notify and nothing to rebuild.
 * <p>
 * The one exception is the log level. Nothing reads it — {@link LoggingSystem#setLogLevel} has to be
 * called — so it is applied here directly.
 * <p>
 * Validation happens <em>before</em> anything is written. Values used to be converted by
 * {@code @Value} at startup, so a malformed one failed the boot; now that they are read lazily, a bad
 * value would instead surface on every request that reads it. Rejecting it at the door keeps that
 * failure at the point where the user can still see and fix it.
 */
public class SettingsManager {

    private static final Logger LOG = LoggerFactory.getLogger(SettingsManager.class);

    private static final String JEFFREY_LOGGER = "cafe.jeffrey";

    private static final Map<String, LogLevel> LOG_LEVELS = Map.of(
            "TRACE", LogLevel.TRACE,
            "DEBUG", LogLevel.DEBUG,
            "INFO", LogLevel.INFO,
            "WARN", LogLevel.WARN,
            "ERROR", LogLevel.ERROR,
            "OFF", LogLevel.OFF);

    private final SettingsRepository settingsRepository;
    private final SecretEncryptor secretEncryptor;
    private final SettingsStore settingsStore;
    private final SettingsMetadata settingsMetadata;
    private final LoggingSystem loggingSystem;
    private final MachineFingerprint.BindingMode bindingMode;

    public SettingsManager(
            SettingsRepository settingsRepository,
            SecretEncryptor secretEncryptor,
            MachineFingerprint machineFingerprint,
            SettingsStore settingsStore,
            SettingsMetadata settingsMetadata,
            LoggingSystem loggingSystem) {

        this.settingsRepository = settingsRepository;
        this.secretEncryptor = secretEncryptor;
        this.settingsStore = settingsStore;
        this.settingsMetadata = settingsMetadata;
        this.loggingSystem = loggingSystem;
        this.bindingMode = machineFingerprint.resolve().mode();
    }

    /**
     * Validates, persists, and applies a single setting.
     */
    public void upsert(String category, String name, String value, boolean secret) {
        upsertAll(List.of(new SettingUpdate(category, name, value, secret)));
    }

    /**
     * Validates, persists, and applies a batch of settings.
     * <p>
     * The whole batch is validated before any of it is written, so a single bad value cannot leave the
     * application half-updated.
     *
     * @throws cafe.jeffrey.shared.common.exception.JeffreyClientException when a name is unknown or a
     *                                                                     value is outside its domain
     */
    public void upsertAll(List<SettingUpdate> updates) {
        if (updates.isEmpty()) {
            return;
        }

        for (SettingUpdate update : updates) {
            validate(update);
        }

        Set<String> changed = new HashSet<>();
        for (SettingUpdate update : updates) {
            String storedValue = update.secret() ? secretEncryptor.encrypt(update.value()) : update.value();
            settingsRepository.upsert(new Setting(update.category(), update.name(), storedValue, update.secret()));

            if (settingsStore.put(update.name(), update.value())) {
                changed.add(update.name());
            }

            LOG.info("Setting updated: category={} name={} secret={}",
                    update.category(), update.name(), update.secret());
        }

        if (changed.contains(MicroscopeSettingKeys.LOGGING_LEVEL)) {
            applyLogLevel();
        }
    }

    /**
     * The log level is the only setting nobody reads on demand, so it is pushed into the logging
     * system here. Every other setting is picked up by whoever next reads the store.
     */
    private void applyLogLevel() {
        String value = settingsStore.getString(MicroscopeSettingKeys.LOGGING_LEVEL, "");
        LogLevel level = LOG_LEVELS.get(value.trim().toUpperCase(Locale.ROOT));

        if (level == null) {
            LOG.warn("Unknown log level, keeping the current one: value={}", value);
            return;
        }

        loggingSystem.setLogLevel(JEFFREY_LOGGER, level);
        LOG.info("Log level applied: logger={} level={}", JEFFREY_LOGGER, level);
    }

    /**
     * Returns the current resolved value of a setting.
     */
    public String getResolvedValue(String name) {
        return settingsStore.getString(name, "");
    }

    /**
     * Returns the encryption binding mode (MACHINE_BOUND or USER_BOUND).
     */
    public MachineFingerprint.BindingMode getBindingMode() {
        return bindingMode;
    }

    private void validate(SettingUpdate update) {
        SettingDescriptor descriptor = settingsMetadata.find(update.name())
                .orElseThrow(() -> Exceptions.invalidRequest("Unknown setting: " + update.name()));

        if (!descriptor.type().isValid(update.value())) {
            throw Exceptions.invalidRequest(
                    "Invalid value for setting %s: %s".formatted(update.name(), update.value()));
        }
    }
}
