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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.bootstrap.DefaultBootstrapContext;
import org.springframework.boot.context.event.ApplicationContextInitializedEvent;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.mock.env.MockEnvironment;
import cafe.jeffrey.shared.common.config.MicroscopeSettingKeys;
import cafe.jeffrey.shared.common.config.SettingsStore;
import cafe.jeffrey.shared.common.encryption.MachineFingerprint;
import cafe.jeffrey.shared.common.encryption.SecretEncryptor;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the boot path that resolves settings before the application context exists: HOCON defaults,
 * database overrides, secret decryption, and installing the live store into the Environment.
 */
class SettingsApplicationListenerTest {

    private static final String HOME_DIR_PROPERTY = "jeffrey.microscope.home.dir";
    private static final String DB_FILENAME = "jeffrey-data.db";

    @TempDir
    Path homeDir;

    private SettingsApplicationListener listener;

    @BeforeEach
    void setUp() {
        listener = new SettingsApplicationListener();
    }

    private ConfigurableEnvironment prepare() {
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty(HOME_DIR_PROPERTY, homeDir.toString());

        listener.onApplicationEvent(new ApplicationEnvironmentPreparedEvent(
                new DefaultBootstrapContext(), new SpringApplication(), new String[0], environment));

        return environment;
    }

    private void writeSetting(String name, String value, boolean secret) {
        String jdbcUrl = "jdbc:duckdb:" + homeDir.resolve(DB_FILENAME);
        try (Connection conn = DriverManager.getConnection(jdbcUrl);
             Statement stmt = conn.createStatement()) {

            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS settings (
                        category VARCHAR NOT NULL,
                        name     VARCHAR NOT NULL,
                        value    VARCHAR NOT NULL,
                        secret   BOOLEAN NOT NULL DEFAULT false,
                        PRIMARY KEY (category, name))
                    """);
            stmt.execute("INSERT INTO settings VALUES ('ai', '%s', '%s', %s)"
                    .formatted(name, value, secret));
        } catch (Exception e) {
            throw new IllegalStateException("Could not seed the settings table", e);
        }
    }

    @Nested
    class Defaults {

        @Test
        void hoconDefaultsAreVisibleWithoutADatabase() {
            ConfigurableEnvironment environment = prepare();

            assertEquals("none", environment.getProperty(MicroscopeSettingKeys.AI_PROVIDER));
            assertEquals("INFO", environment.getProperty(MicroscopeSettingKeys.LOGGING_LEVEL));
        }

        @Test
        void everyDeclaredSettingIsPresent() {
            ConfigurableEnvironment environment = prepare();

            assertNotNull(environment.getProperty(MicroscopeSettingKeys.AI_MAX_TOKENS));
            assertNotNull(environment.getProperty(MicroscopeSettingKeys.FLAMEGRAPH_MIN_FRAME_THRESHOLD_PCT));
            assertNotNull(environment.getProperty(MicroscopeSettingKeys.AI_EXPORT_MIN_FRAME_THRESHOLD_PCT));
        }

        @Test
        void secretDefaultsToEmpty() {
            ConfigurableEnvironment environment = prepare();

            assertEquals("", environment.getProperty(MicroscopeSettingKeys.AI_API_KEY));
        }
    }

    @Nested
    class DatabaseOverrides {

        @Test
        void storedValueOverridesTheDefault() {
            writeSetting(MicroscopeSettingKeys.AI_PROVIDER, "ollama", false);

            assertEquals("ollama", prepare().getProperty(MicroscopeSettingKeys.AI_PROVIDER));
        }

        @Test
        void unstoredSettingKeepsItsDefault() {
            writeSetting(MicroscopeSettingKeys.AI_PROVIDER, "ollama", false);

            assertEquals("INFO", prepare().getProperty(MicroscopeSettingKeys.LOGGING_LEVEL));
        }

        @Test
        void secretIsDecryptedBeforeItReachesTheEnvironment() {
            SecretEncryptor encryptor = new SecretEncryptor(new MachineFingerprint());
            writeSetting(MicroscopeSettingKeys.AI_API_KEY, encryptor.encrypt("sk-ant-secret"), true);

            assertEquals("sk-ant-secret", prepare().getProperty(MicroscopeSettingKeys.AI_API_KEY));
        }

        @Test
        void settingRemovedFromTheSchemaIsIgnored() {
            writeSetting("jeffrey.microscope.ai.retired", "value", false);

            assertEquals(null, prepare().getProperty("jeffrey.microscope.ai.retired"));
        }
    }

    @Nested
    class PropertySourceInstallation {

        @Test
        void settingsSourceIsInstalledFirstSoStoredValuesWin() {
            ConfigurableEnvironment environment = prepare();

            assertEquals(SettingsPropertySource.NAME,
                    environment.getPropertySources().iterator().next().getName());
        }

        @Test
        void sourceIsBackedByTheLiveStoreSoLaterWritesAreVisible() {
            ConfigurableEnvironment environment = prepare();

            SettingsPropertySource source =
                    (SettingsPropertySource) environment.getPropertySources().get(SettingsPropertySource.NAME);
            source.getSource().put(MicroscopeSettingKeys.AI_PROVIDER, "claude-code");

            assertEquals("claude-code", environment.getProperty(MicroscopeSettingKeys.AI_PROVIDER));
        }

        @Test
        void enumeratesEveryDeclaredSetting() {
            ConfigurableEnvironment environment = prepare();

            SettingsPropertySource source =
                    (SettingsPropertySource) environment.getPropertySources().get(SettingsPropertySource.NAME);

            assertTrue(source.containsProperty(MicroscopeSettingKeys.AI_PROVIDER));
            // Moves by one whenever a setting is declared — deliberately, since a key that never
            // reaches the property source reads to the rest of the app as "not configurable".
            assertEquals(14, source.getPropertyNames().length);
        }
    }

    @Nested
    class BeanRegistration {

        @Test
        void registersTheMetadataAndTheStoreAsSingletons() {
            ConfigurableEnvironment environment = prepare();

            GenericApplicationContext context = new GenericApplicationContext();
            listener.onApplicationEvent(new ApplicationContextInitializedEvent(
                    new SpringApplication(), new String[0], context));

            SettingsStore store = context.getBeanFactory().getBean(SettingsStore.class);
            assertNotNull(context.getBeanFactory().getBean(SettingsMetadata.class));

            SettingsPropertySource source =
                    (SettingsPropertySource) environment.getPropertySources().get(SettingsPropertySource.NAME);
            assertSame(source.getSource(), store);
        }

        @Test
        void descriptorsCarryTheirDeclaredTypes() {
            prepare();

            GenericApplicationContext context = new GenericApplicationContext();
            listener.onApplicationEvent(new ApplicationContextInitializedEvent(
                    new SpringApplication(), new String[0], context));

            SettingsMetadata metadata = context.getBeanFactory().getBean(SettingsMetadata.class);
            SettingDescriptor maxTokens = metadata.find(MicroscopeSettingKeys.AI_MAX_TOKENS).orElseThrow();

            assertTrue(maxTokens.type().isValid("4096"));
            assertTrue(!maxTokens.type().isValid("not-a-number"));
        }
    }
}
