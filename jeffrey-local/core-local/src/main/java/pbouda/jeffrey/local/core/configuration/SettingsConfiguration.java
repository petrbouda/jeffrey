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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import pbouda.jeffrey.local.core.manager.SettingsManager;
import pbouda.jeffrey.local.persistence.repository.JdbcSettingsRepository;
import pbouda.jeffrey.shared.common.encryption.MachineFingerprint;
import pbouda.jeffrey.shared.common.encryption.SecretEncryptor;
import pbouda.jeffrey.shared.persistence.client.DatabaseClientProvider;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Configuration
public class SettingsConfiguration {

    private static final Logger LOG = LoggerFactory.getLogger(SettingsConfiguration.class);

    /**
     * Maps settings (category + key) to Spring property names.
     * Keys marked as secret are decrypted before injection.
     */
    private record SettingsMapping(String category, String key, String property, boolean secret) {
    }

    private static final List<SettingsMapping> SETTINGS_MAPPINGS = List.of(
            new SettingsMapping("ai", "provider", "jeffrey.local.ai.provider", false),
            new SettingsMapping("ai", "model", "jeffrey.local.ai.model", false),
            new SettingsMapping("ai", "max-tokens", "jeffrey.local.ai.max-tokens", false),
            new SettingsMapping("ai", "api-key", "jeffrey.local.ai.api-key", true),
            new SettingsMapping("general", "log-level", "logging.level.pbouda.jeffrey", false)
    );

    @Bean
    public SettingsManager settingsManager(
            DatabaseClientProvider databaseClientProvider,
            ConfigurableEnvironment environment) {

        var machineFingerprint = new MachineFingerprint();
        var secretEncryptor = new SecretEncryptor(machineFingerprint);
        var settingsRepository = new JdbcSettingsRepository(databaseClientProvider);

        SettingsManager manager = new SettingsManager(settingsRepository, secretEncryptor, machineFingerprint);
        injectSettingsIntoEnvironment(manager, environment);

        return manager;
    }

    /**
     * Reads settings from the database and injects them into the Spring Environment
     * as a high-priority PropertySource. This allows {@code @Value} annotations and
     * {@code @ConditionalOnExpression} to pick up DB-stored values transparently,
     * without those classes needing to depend on persistence.
     */
    private void injectSettingsIntoEnvironment(SettingsManager settingsManager, ConfigurableEnvironment environment) {
        Map<String, Object> dbProperties = new HashMap<>();

        for (SettingsMapping mapping : SETTINGS_MAPPINGS) {
            if (mapping.secret()) {
                settingsManager.getDecryptedValue(mapping.category(), mapping.key())
                        .ifPresent(value -> dbProperties.put(mapping.property(), value));
            } else {
                settingsManager.getValue(mapping.category(), mapping.key())
                        .ifPresent(value -> dbProperties.put(mapping.property(), value));
            }
        }

        if (!dbProperties.isEmpty()) {
            environment.getPropertySources()
                    .addFirst(new MapPropertySource("jeffrey-db-settings", dbProperties));
            LOG.info("Injected settings from database into Spring Environment: keys={}",
                    dbProperties.keySet());
        }
    }
}
