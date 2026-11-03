/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package cafe.jeffrey.microscope.core.configuration.properties;

import cafe.jeffrey.microscope.model.connector.AzureFilesAddress;
import cafe.jeffrey.microscope.model.connector.AzureFilesAuth;
import cafe.jeffrey.microscope.model.connector.AzureFilesProtocol;
import cafe.jeffrey.microscope.model.connector.ConnectorInfo;
import cafe.jeffrey.microscope.model.connector.LocalDirectoryAddress;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.SystemEnvironmentPropertySource;

import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Proves the declared properties actually reach {@link ConfiguredConnectorProperties} — a prefix one
 * level off binds nothing and fails silently, so the connector would simply never be reached.
 */
class ConfiguredConnectorBindingTest {

    @Configuration
    @EnableConfigurationProperties(ConfiguredConnectorProperties.class)
    static class BindingTestConfiguration {
    }

    private static ConfiguredConnectorProperties bind(String... properties) {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            TestPropertyValues.of(properties).applyTo(context);
            context.register(BindingTestConfiguration.class);
            context.refresh();
            return context.getBean(ConfiguredConnectorProperties.class);
        }
    }

    @Test
    @DisplayName("A name binds beside the share it names")
    void bindsTheName() {
        ConfiguredConnectorProperties properties = bind(
                "jeffrey.microscope.connector.name=Production",
                "jeffrey.microscope.connector.azure-files.endpoint=https://jeffreyprod.file.core.windows.net",
                "jeffrey.microscope.connector.azure-files.share=recordings",
                "jeffrey.microscope.connector.azure-files.sas-token=sv=2026-06-06&sig=c2ln");

        ConnectorInfo connector = properties.resolve().orElseThrow();

        assertEquals("Production", connector.name());
        assertEquals("recordings", ((AzureFilesAddress) connector.address()).share());
    }

    @Test
    @DisplayName("An azure-files block binds every field, kebab-case keys included")
    void bindsAnAzureFilesShare() {
        ConfiguredConnectorProperties properties = bind(
                "jeffrey.microscope.connector.azure-files.endpoint=https://jeffreyprod.file.core.windows.net",
                "jeffrey.microscope.connector.azure-files.share=recordings",
                "jeffrey.microscope.connector.azure-files.root-path=volume/workspaces",
                "jeffrey.microscope.connector.azure-files.protocol=nfs",
                "jeffrey.microscope.connector.azure-files.auth=sas",
                "jeffrey.microscope.connector.azure-files.sas-token=sv=2026-06-06&sig=c2ln",
                "jeffrey.microscope.connector.azure-files.read-only=true");

        assertEquals(new AzureFilesAddress("https://jeffreyprod.file.core.windows.net", "recordings",
                "volume/workspaces", AzureFilesProtocol.NFS, AzureFilesAuth.SAS, true),
                properties.resolve().orElseThrow().address());
        assertEquals(Optional.of("sv=2026-06-06&sig=c2ln"), properties.sasToken());
    }

    @Test
    @DisplayName("A local block binds its path and read-only flag")
    void bindsALocalDirectory() {
        ConfiguredConnectorProperties properties = bind(
                "jeffrey.microscope.connector.local.path=/tmp/jeffrey-volume",
                "jeffrey.microscope.connector.local.read-only=true");

        assertEquals(new LocalDirectoryAddress(Path.of("/tmp/jeffrey-volume"), true),
                properties.resolve().orElseThrow().address());
        assertEquals(Optional.empty(), properties.sasToken());
    }

    @Test
    @DisplayName("Environment-variable spelling binds the same fields")
    void bindsFromEnvironmentVariables() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().getPropertySources().addFirst(
                    new SystemEnvironmentPropertySource(
                            // Spring applies the underscore-to-dot relaxed mapping only to a
                            // SystemEnvironmentPropertySource whose name is "systemEnvironment" or
                            // ends with "-systemEnvironment"; any other name silently binds nothing.
                            "test-systemEnvironment",
                            Map.of(
                                    "JEFFREY_MICROSCOPE_CONNECTOR_AZUREFILES_ENDPOINT", "https://jeffreyprod.file.core.windows.net",
                                    "JEFFREY_MICROSCOPE_CONNECTOR_AZUREFILES_SHARE", "recordings",
                                    "JEFFREY_MICROSCOPE_CONNECTOR_AZUREFILES_AUTH", "entra")));
            context.register(BindingTestConfiguration.class);
            context.refresh();

            assertEquals(new AzureFilesAddress("https://jeffreyprod.file.core.windows.net", "recordings", "",
                            AzureFilesProtocol.SMB, AzureFilesAuth.ENTRA, false),
                    context.getBean(ConfiguredConnectorProperties.class).resolve().orElseThrow().address());
        }
    }

    @Test
    @DisplayName("Declaring nothing binds no connector rather than failing")
    void bindsNothingWhenNoConnectorIsDeclared() {
        assertEquals(Optional.empty(), bind().resolve());
    }
}
