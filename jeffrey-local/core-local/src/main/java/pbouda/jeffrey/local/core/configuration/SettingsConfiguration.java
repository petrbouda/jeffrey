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

package pbouda.jeffrey.local.core.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import pbouda.jeffrey.local.core.manager.SettingsManager;
import pbouda.jeffrey.local.persistence.repository.JdbcSettingsRepository;
import pbouda.jeffrey.shared.common.encryption.MachineFingerprint;
import pbouda.jeffrey.shared.common.encryption.SecretEncryptor;
import pbouda.jeffrey.shared.persistence.client.DatabaseClientProvider;

@Configuration
public class SettingsConfiguration {

    @Bean
    public SettingsManager settingsManager(DatabaseClientProvider databaseClientProvider, Environment environment) {
        var machineFingerprint = new MachineFingerprint();
        var secretEncryptor = new SecretEncryptor(machineFingerprint);
        var settingsRepository = new JdbcSettingsRepository(databaseClientProvider);
        return new SettingsManager(settingsRepository, secretEncryptor, machineFingerprint, environment);
    }
}
