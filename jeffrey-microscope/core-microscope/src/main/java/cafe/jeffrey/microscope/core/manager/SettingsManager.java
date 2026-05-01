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
import org.springframework.core.env.Environment;
import cafe.jeffrey.microscope.persistence.api.Setting;
import cafe.jeffrey.microscope.persistence.api.SettingsRepository;
import cafe.jeffrey.shared.common.encryption.MachineFingerprint;
import cafe.jeffrey.shared.common.encryption.SecretEncryptor;

/**
 * Business logic for application settings. Handles encryption/decryption of secrets
 * and provides access to resolved setting values from the Spring Environment.
 */
public class SettingsManager {

    private static final Logger LOG = LoggerFactory.getLogger(SettingsManager.class);

    private final SettingsRepository settingsRepository;
    private final SecretEncryptor secretEncryptor;
    private final Environment environment;
    private final MachineFingerprint.BindingMode bindingMode;

    private volatile boolean restartRequired;

    public SettingsManager(
            SettingsRepository settingsRepository,
            SecretEncryptor secretEncryptor,
            MachineFingerprint machineFingerprint,
            Environment environment) {

        this.settingsRepository = settingsRepository;
        this.secretEncryptor = secretEncryptor;
        this.environment = environment;
        this.bindingMode = machineFingerprint.resolve().mode();
    }

    /**
     * Upserts a setting. If the setting is marked as secret, encrypts the value before storing.
     */
    public void upsert(String category, String name, String value, boolean secret) {
        String storedValue = secret ? secretEncryptor.encrypt(value) : value;
        settingsRepository.upsert(new Setting(category, name, storedValue, secret));
        restartRequired = true;

        LOG.info("Setting updated: category={} name={} secret={}", category, name, secret);
    }

    /**
     * Returns the current resolved value of a setting from the Spring Environment.
     */
    public String getResolvedValue(String name) {
        return environment.getProperty(name, "");
    }

    /**
     * Returns whether settings have been modified since startup and a restart is needed.
     */
    public boolean isRestartRequired() {
        return restartRequired;
    }

    /**
     * Returns the encryption binding mode (MACHINE_BOUND or USER_BOUND).
     */
    public MachineFingerprint.BindingMode getBindingMode() {
        return bindingMode;
    }
}
