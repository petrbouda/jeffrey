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

import cafe.jeffrey.microscope.model.remote.AzureFilesAddress;
import cafe.jeffrey.microscope.model.remote.AzureFilesAuth;
import cafe.jeffrey.microscope.model.remote.AzureFilesProtocol;
import cafe.jeffrey.microscope.model.remote.HubAddress;
import cafe.jeffrey.microscope.model.remote.RemoteInfo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.SystemEnvironmentPropertySource;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Proves the declared properties actually reach {@link ConfiguredRemoteProperties} — a prefix one
 * level off binds nothing and fails silently, so the remote would simply never be reached.
 */
class ConfiguredRemoteBindingTest {

    @Configuration
    @EnableConfigurationProperties(ConfiguredRemoteProperties.class)
    static class BindingTestConfiguration {
    }

    private static ConfiguredRemoteProperties bind(String... properties) {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            TestPropertyValues.of(properties).applyTo(context);
            context.register(BindingTestConfiguration.class);
            context.refresh();
            return context.getBean(ConfiguredRemoteProperties.class);
        }
    }

    @Test
    @DisplayName("A Jeffrey Hub binds every field")
    void bindsAJeffreyHub() {
        ConfiguredRemoteProperties properties = bind(
                "jeffrey.microscope.remote.name=Production",
                "jeffrey.microscope.remote.hostname=hub.example.com",
                "jeffrey.microscope.remote.port=443",
                "jeffrey.microscope.remote.plaintext=true");

        RemoteInfo remote = properties.resolve().orElseThrow();

        assertEquals("Production", remote.name());
        assertEquals(new HubAddress("hub.example.com", 443, true), remote.address());
    }

    @Test
    @DisplayName("An azure-files block binds every field, kebab-case keys included")
    void bindsAnAzureFilesShare() {
        ConfiguredRemoteProperties properties = bind(
                "jeffrey.microscope.remote.azure-files.endpoint=https://jeffreyprod.file.core.windows.net",
                "jeffrey.microscope.remote.azure-files.share=recordings",
                "jeffrey.microscope.remote.azure-files.root-path=volume/workspaces",
                "jeffrey.microscope.remote.azure-files.protocol=nfs",
                "jeffrey.microscope.remote.azure-files.auth=sas",
                "jeffrey.microscope.remote.azure-files.sas-token=sv=2026-06-06&sig=c2ln",
                "jeffrey.microscope.remote.azure-files.read-only=true");

        assertEquals(new AzureFilesAddress("https://jeffreyprod.file.core.windows.net", "recordings",
                "volume/workspaces", AzureFilesProtocol.NFS, AzureFilesAuth.SAS, true),
                properties.resolve().orElseThrow().address());
        assertEquals(Optional.of("sv=2026-06-06&sig=c2ln"), properties.sasToken());
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
                                    "JEFFREY_MICROSCOPE_REMOTE_HOSTNAME", "hub.example.com",
                                    "JEFFREY_MICROSCOPE_REMOTE_PORT", "443")));
            context.register(BindingTestConfiguration.class);
            context.refresh();

            assertEquals(new HubAddress("hub.example.com", 443, false),
                    context.getBean(ConfiguredRemoteProperties.class).resolve().orElseThrow().address());
        }
    }

    @Test
    @DisplayName("Declaring nothing binds no remote rather than failing")
    void bindsNothingWhenNoRemoteIsDeclared() {
        assertEquals(Optional.empty(), bind().resolve());
    }
}
