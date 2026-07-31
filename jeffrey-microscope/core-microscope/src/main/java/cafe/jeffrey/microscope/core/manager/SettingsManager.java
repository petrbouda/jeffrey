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
import cafe.jeffrey.microscope.core.configuration.SettingDescriptor;
import cafe.jeffrey.microscope.core.configuration.SettingsMetadata;
import cafe.jeffrey.microscope.persistence.api.Setting;
import cafe.jeffrey.microscope.persistence.api.SettingsRepository;
import cafe.jeffrey.shared.common.config.SettingsChangeDispatcher;
import cafe.jeffrey.shared.common.config.SettingsStore;
import cafe.jeffrey.shared.common.encryption.MachineFingerprint;
import cafe.jeffrey.shared.common.encryption.SecretEncryptor;
import cafe.jeffrey.shared.common.exception.Exceptions;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Business logic for application settings. Validates incoming values, encrypts secrets, persists the
 * change, and applies it to the running application.
 * <p>
 * A saved setting takes effect immediately: the plaintext value is written into the live
 * {@link SettingsStore} — which backs the Spring {@code Environment} — and the change is handed to the
 * {@link SettingsChangeDispatcher} so listeners that hold expensive derived objects can rebuild them.
 * Nothing here requires a restart.
 * <p>
 * Validation happens <em>before</em> anything is written. Values used to be converted by
 * {@code @Value} at startup, so a malformed one failed the boot; now that they are read lazily, a bad
 * value would instead surface on every request that reads it. Rejecting it at the door keeps that
 * failure at the point where the user can still see and fix it.
 */
public class SettingsManager {

    private static final Logger LOG = LoggerFactory.getLogger(SettingsManager.class);

    private final SettingsRepository settingsRepository;
    private final SecretEncryptor secretEncryptor;
    private final SettingsStore settingsStore;
    private final SettingsMetadata settingsMetadata;
    private final SettingsChangeDispatcher changeDispatcher;
    private final MachineFingerprint.BindingMode bindingMode;

    public SettingsManager(
            SettingsRepository settingsRepository,
            SecretEncryptor secretEncryptor,
            MachineFingerprint machineFingerprint,
            SettingsStore settingsStore,
            SettingsMetadata settingsMetadata,
            SettingsChangeDispatcher changeDispatcher) {

        this.settingsRepository = settingsRepository;
        this.secretEncryptor = secretEncryptor;
        this.settingsStore = settingsStore;
        this.settingsMetadata = settingsMetadata;
        this.changeDispatcher = changeDispatcher;
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
     * application half-updated. Listeners are notified once for the batch rather than once per setting:
     * a settings page saves every field of a tab together, and rebuilding an AI backend against a
     * partially applied batch would use a new provider with a stale API key.
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

        changeDispatcher.changed(changed);
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
