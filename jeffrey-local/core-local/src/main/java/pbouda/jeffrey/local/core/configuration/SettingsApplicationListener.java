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

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationContextInitializedEvent;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.event.GenericApplicationListener;
import org.springframework.core.Ordered;
import org.springframework.core.ResolvableType;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import pbouda.jeffrey.shared.common.encryption.EncryptionException;
import pbouda.jeffrey.shared.common.encryption.MachineFingerprint;
import pbouda.jeffrey.shared.common.encryption.SecretEncryptor;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Resolves all application settings and injects them into the Spring Environment
 * before {@code LoggingApplicationListener} configures log levels.
 *
 * <p>Listens to two events:
 * <ol>
 *   <li>{@code ApplicationEnvironmentPreparedEvent} — injects settings into the Environment
 *       (runs before {@code LoggingApplicationListener} at order {@code HIGHEST_PRECEDENCE + 20})</li>
 *   <li>{@code ApplicationContextInitializedEvent} — registers {@code SettingsMetadata} as a singleton bean</li>
 * </ol>
 *
 * <p>Resolution order:
 * <ol>
 *   <li>HOCON defaults from {@code settings-mappings.conf} (properties + empty-string secret defaults)</li>
 *   <li>Database overrides for all settings (both properties and secrets)</li>
 *   <li>Secret values are decrypted before injection</li>
 * </ol>
 */
public class SettingsApplicationListener implements GenericApplicationListener {

    private static final Logger LOG = LoggerFactory.getLogger(SettingsApplicationListener.class);

    private static final int ORDER = Ordered.HIGHEST_PRECEDENCE + 15;
    private static final String DEFAULT_HOME_DIR = System.getProperty("user.home") + "/.jeffrey-local";
    private static final String DB_FILENAME = "jeffrey-data.db";

    private SettingsMetadata settingsMetadata;

    private record DbSetting(String name, String value, boolean secret) {
    }

    @Override
    public int getOrder() {
        return ORDER;
    }

    @Override
    public boolean supportsEventType(ResolvableType eventType) {
        Class<?> type = eventType.getRawClass();
        return type != null && (
                ApplicationEnvironmentPreparedEvent.class.isAssignableFrom(type) ||
                ApplicationContextInitializedEvent.class.isAssignableFrom(type));
    }

    @Override
    public void onApplicationEvent(ApplicationEvent event) {
        if (event instanceof ApplicationEnvironmentPreparedEvent envEvent) {
            onEnvironmentPrepared(envEvent.getEnvironment());
        } else if (event instanceof ApplicationContextInitializedEvent ctxEvent) {
            onContextInitialized(ctxEvent);
        }
    }

    private void onEnvironmentPrepared(ConfigurableEnvironment environment) {
        settingsMetadata = loadSettingsMetadata();

        if (settingsMetadata.descriptors().isEmpty()) {
            return;
        }

        String homeDir = environment.getProperty("jeffrey.local.home.dir", DEFAULT_HOME_DIR);
        Path dbPath = Path.of(homeDir).resolve(DB_FILENAME);

        Map<String, Object> properties = new HashMap<>();

        // Start with HOCON defaults (properties + empty-string secret defaults)
        for (SettingDescriptor descriptor : settingsMetadata.descriptors()) {
            properties.put(descriptor.name(), descriptor.defaultValue());
        }

        // Override with DB values (all settings, including secrets)
        if (Files.exists(dbPath)) {
            List<DbSetting> dbSettings = loadFromDatabase(dbPath);
            SecretEncryptor encryptor = createEncryptorIfNeeded(dbSettings);

            for (DbSetting setting : dbSettings) {
                if (setting.secret()) {
                    if (encryptor != null) {
                        try {
                            properties.put(setting.name(), encryptor.decrypt(setting.value()));
                        } catch (EncryptionException e) {
                            LOG.warn("Failed to decrypt setting '{}', keeping default: {}",
                                    setting.name(), e.getMessage());
                        }
                    }
                } else {
                    properties.put(setting.name(), setting.value());
                }
            }
        }

        if (!properties.isEmpty()) {
            environment.getPropertySources()
                    .addFirst(new MapPropertySource("jeffrey-db-settings", properties));
            LOG.info("Injected settings into Spring Environment: keys={}", properties.keySet());
        }
    }

    private void onContextInitialized(ApplicationContextInitializedEvent event) {
        if (settingsMetadata != null) {
            event.getApplicationContext().getBeanFactory()
                    .registerSingleton("settingsMetadata", settingsMetadata);
        }
    }

    private static SettingsMetadata loadSettingsMetadata() {
        Config config = ConfigFactory.parseResources("settings-mappings.conf").resolve();
        List<SettingDescriptor> descriptors = new ArrayList<>();

        if (config.hasPath("settings.properties")) {
            collectDescriptors(config.getConfig("settings.properties"), false, descriptors);
        }
        if (config.hasPath("settings.secrets")) {
            collectDescriptors(config.getConfig("settings.secrets"), true, descriptors);
        }

        return new SettingsMetadata(List.copyOf(descriptors));
    }

    private static void collectDescriptors(Config section, boolean secret, List<SettingDescriptor> descriptors) {
        for (Map.Entry<String, ConfigValue> categoryEntry : section.root().entrySet()) {
            String category = categoryEntry.getKey();
            Config categoryConfig = section.getConfig(category);
            for (Map.Entry<String, ConfigValue> entry : categoryConfig.entrySet()) {
                String propertyName = entry.getKey();
                String defaultValue = categoryConfig.getString(propertyName);
                descriptors.add(new SettingDescriptor(category, propertyName, defaultValue, secret));
            }
        }
    }

    private SecretEncryptor createEncryptorIfNeeded(List<DbSetting> dbSettings) {
        boolean hasSecrets = dbSettings.stream().anyMatch(DbSetting::secret);
        if (!hasSecrets) {
            return null;
        }
        try {
            return new SecretEncryptor(new MachineFingerprint());
        } catch (Exception e) {
            LOG.warn("Could not initialize encryption, secret settings will use defaults: {}",
                    e.getMessage());
            return null;
        }
    }

    private List<DbSetting> loadFromDatabase(Path dbPath) {
        List<DbSetting> settings = new ArrayList<>();
        String jdbcUrl = "jdbc:duckdb:" + dbPath;

        try (Connection conn = DriverManager.getConnection(jdbcUrl);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT name, value, secret FROM settings")) {

            while (rs.next()) {
                settings.add(new DbSetting(
                        rs.getString("name"),
                        rs.getString("value"),
                        rs.getBoolean("secret")));
            }
        } catch (Exception e) {
            LOG.debug("Could not load settings from database (may not exist yet): {}", e.getMessage());
        }

        return settings;
    }
}
