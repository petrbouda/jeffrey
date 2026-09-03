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

import cafe.jeffrey.microscope.runtime.settings.SettingsMetadata;
import org.springframework.boot.logging.LoggingSystem;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import cafe.jeffrey.microscope.core.manager.SettingsManager;
import cafe.jeffrey.microscope.persistence.api.MicroscopeCorePersistenceProvider;
import cafe.jeffrey.microscope.persistence.jdbc.JdbcSettingsRepository;
import cafe.jeffrey.shared.common.config.SettingsStore;
import cafe.jeffrey.shared.common.encryption.MachineFingerprint;
import cafe.jeffrey.shared.common.encryption.SecretEncryptor;

/**
 * Wires the settings write path.
 * <p>
 * The {@link SettingsStore} itself is not created here: it is built and registered as a singleton by
 * {@code SettingsApplicationListener}, which runs before the application context exists because the
 * resolved settings must reach the {@code Environment} in time for log-level configuration.
 */
@Configuration
public class SettingsConfiguration {

    @Bean
    public SettingsManager settingsManager(
            MicroscopeCorePersistenceProvider localCorePersistenceProvider,
            SettingsStore settingsStore,
            SettingsMetadata settingsMetadata,
            LoggingSystem loggingSystem) {

        var machineFingerprint = new MachineFingerprint();
        var secretEncryptor = new SecretEncryptor(machineFingerprint);
        var settingsRepository = new JdbcSettingsRepository(localCorePersistenceProvider.databaseClientProvider());
        return new SettingsManager(
                settingsRepository,
                secretEncryptor,
                machineFingerprint,
                settingsStore,
                settingsMetadata,
                loggingSystem);
    }
}
