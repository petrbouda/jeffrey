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

package pbouda.jeffrey.local.core.manager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pbouda.jeffrey.local.persistence.model.Setting;
import pbouda.jeffrey.local.persistence.repository.SettingsRepository;
import pbouda.jeffrey.shared.common.encryption.EncryptionException;
import pbouda.jeffrey.shared.common.encryption.MachineFingerprint;
import pbouda.jeffrey.shared.common.encryption.SecretEncryptor;

import java.util.List;
import java.util.Optional;

/**
 * Business logic for application settings. Handles encryption/decryption of secrets
 * and masking of secret values in responses.
 */
public class SettingsManager {

    private static final Logger LOG = LoggerFactory.getLogger(SettingsManager.class);

    private static final String MASK = MASK;

    private final SettingsRepository settingsRepository;
    private final SecretEncryptor secretEncryptor;
    private final MachineFingerprint.BindingMode bindingMode;

    public SettingsManager(
            SettingsRepository settingsRepository,
            SecretEncryptor secretEncryptor,
            MachineFingerprint machineFingerprint) {

        this.settingsRepository = settingsRepository;
        this.secretEncryptor = secretEncryptor;
        this.bindingMode = machineFingerprint.resolve().mode();
    }

    /**
     * Returns all settings with secret values masked.
     */
    public List<Setting> findAll() {
        return settingsRepository.findAll().stream()
                .map(this::maskIfSecret)
                .toList();
    }

    /**
     * Returns settings for a category with secret values masked.
     */
    public List<Setting> findByCategory(String category) {
        return settingsRepository.findByCategory(category).stream()
                .map(this::maskIfSecret)
                .toList();
    }

    /**
     * Returns the decrypted value of a setting (for internal use, e.g., AI configuration).
     */
    public Optional<String> getDecryptedValue(String category, String key) {
        return settingsRepository.find(category, key)
                .map(setting -> {
                    if (setting.secret()) {
                        try {
                            return secretEncryptor.decrypt(setting.value());
                        } catch (EncryptionException e) {
                            LOG.warn("Failed to decrypt setting, clearing value: category={} key={}", category, key);
                            settingsRepository.delete(category, key);
                            return null;
                        }
                    }
                    return setting.value();
                });
    }

    /**
     * Returns the plain value of a non-secret setting.
     */
    public Optional<String> getValue(String category, String key) {
        return settingsRepository.find(category, key)
                .map(Setting::value);
    }

    /**
     * Upserts a setting. If the setting is marked as secret, encrypts the value before storing.
     */
    public void upsert(String category, String key, String value, boolean secret) {
        String storedValue = secret ? secretEncryptor.encrypt(value) : value;
        settingsRepository.upsert(new Setting(category, key, storedValue, secret));

        LOG.info("Setting updated: category={} key={} secret={}", category, key, secret);
    }

    /**
     * Deletes a setting.
     */
    public void delete(String category, String key) {
        settingsRepository.delete(category, key);
        LOG.info("Setting deleted: category={} key={}", category, key);
    }

    /**
     * Returns the encryption binding mode (MACHINE_BOUND or USER_BOUND).
     */
    public MachineFingerprint.BindingMode getBindingMode() {
        return bindingMode;
    }

    private Setting maskIfSecret(Setting setting) {
        if (!setting.secret()) {
            return setting;
        }

        try {
            String decrypted = secretEncryptor.decrypt(setting.value());
            String masked = maskValue(decrypted);
            return new Setting(setting.category(), setting.key(), masked, true);
        } catch (EncryptionException e) {
            return new Setting(setting.category(), setting.key(), MASK, true);
        }
    }

    private static String maskValue(String value) {
        if (value.length() <= 8) {
            return MASK;
        }
        return value.substring(0, 4) + MASK + value.substring(value.length() - 4);
    }
}
