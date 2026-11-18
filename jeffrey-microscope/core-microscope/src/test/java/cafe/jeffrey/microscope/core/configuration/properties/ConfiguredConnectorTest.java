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

import cafe.jeffrey.microscope.connector.azure.AzureFilesAddress;
import cafe.jeffrey.microscope.connector.azure.AzureFilesAuth;
import cafe.jeffrey.microscope.connector.azure.AzureFilesProtocol;
import cafe.jeffrey.microscope.connector.local.LocalDirectoryAddress;
import cafe.jeffrey.microscope.connector.spi.ConnectorProvider;
import cafe.jeffrey.microscope.connector.spi.ConnectorProviders;
import cafe.jeffrey.microscope.core.manager.connector.ConnectorInfo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Reads the declaration as Microscope does — every kind {@link ConnectorProviders} finds on the class
 * path, which is what proves each kind's {@code META-INF/services} entry, binding its own block.
 */
class ConfiguredConnectorTest {

    private static final String ENDPOINT = "https://jeffreyprod.file.core.windows.net";
    private static final String SAS = "sv=2026-06-06&ss=f&srt=sco&sp=rl&sig=c2lnbmF0dXJl";
    private static final List<ConnectorProvider<?>> KINDS = ConnectorProviders.load();

    private static ConfiguredConnector read(Map<String, String> properties) {
        return ConfiguredConnector.read(new Binder(new MapConfigurationPropertySource(properties)), KINDS);
    }

    private static Map<String, String> share(String auth, String sasToken) {
        Map<String, String> properties = new HashMap<>();
        properties.put("jeffrey.microscope.connector.azure-files.endpoint", ENDPOINT);
        properties.put("jeffrey.microscope.connector.azure-files.share", "recordings");
        properties.put("jeffrey.microscope.connector.azure-files.auth", auth);
        if (sasToken != null) {
            properties.put("jeffrey.microscope.connector.azure-files.sas-token", sasToken);
        }
        return properties;
    }

    private static Map<String, String> directory(String path) {
        Map<String, String> properties = new HashMap<>();
        properties.put("jeffrey.microscope.connector.local.path", path);
        return properties;
    }

    @Test
    void findsBothKindsOnTheClassPath() {
        assertEquals(List.of(AzureFilesAddress.KIND, LocalDirectoryAddress.KIND),
                KINDS.stream().map(ConnectorProvider::kind).toList());
    }

    @Test
    void nothingDeclaredIsNoConnector() {
        ConfiguredConnector none = read(Map.of());

        assertEquals(Optional.empty(), none.connector());
        assertEquals(Optional.empty(), none.declared());
    }

    @Nested
    @DisplayName("An Azure file share")
    class AzureFiles {

        @Test
        void bindsEveryFieldKebabCaseKeysIncluded() {
            Map<String, String> properties = share("sas", SAS);
            properties.put("jeffrey.microscope.connector.azure-files.root-path", "volume/workspaces");
            properties.put("jeffrey.microscope.connector.azure-files.protocol", "nfs");
            properties.put("jeffrey.microscope.connector.azure-files.read-only", "true");

            assertEquals(new AzureFilesAddress(ENDPOINT, "recordings", "volume/workspaces", AzureFilesProtocol.NFS,
                    AzureFilesAuth.SAS, true), read(properties).connector().orElseThrow().address());
        }

        @Test
        void isWritableOverSmbWithASasByDefaultNamedByItsShare() {
            ConnectorInfo connector = read(share("sas", SAS)).connector().orElseThrow();

            assertEquals(new AzureFilesAddress(ENDPOINT, "recordings", "", AzureFilesProtocol.SMB,
                    AzureFilesAuth.SAS, false), connector.address());
            assertEquals("recordings", connector.name());
        }

        @Test
        void anExplicitNameWinsOverTheShareName() {
            Map<String, String> properties = share("sas", SAS);
            properties.put("jeffrey.microscope.connector.name", " Production ");

            assertEquals("Production", read(properties).connector().orElseThrow().name());
        }

        /** The token reaches whatever opens the share, and never the address. */
        @Test
        void theSasTokenStaysOutOfTheAddress() {
            ConnectorInfo connector = read(share("sas", " " + SAS + " ")).connector().orElseThrow();

            assertFalse(connector.address().toString().contains("sig="));
            assertFalse(connector.address().identity().contains("sig="));
        }

        @Test
        void entraNeedsNoToken() {
            assertEquals(AzureFilesAuth.ENTRA,
                    ((AzureFilesAddress) read(share("entra", null)).connector().orElseThrow().address()).auth());
        }

        @Test
        void anUnusableShareIsLeftOut() {
            Map<String, String> ftp = share("sas", SAS);
            ftp.put("jeffrey.microscope.connector.azure-files.protocol", "ftp");
            Map<String, String> pasted = share("sas", SAS);
            pasted.put("jeffrey.microscope.connector.azure-files.endpoint", ENDPOINT + "/?sv=2026-06-06&sig=PASTED");

            assertEquals(Optional.empty(), read(share("sas", " ")).connector());
            assertEquals(Optional.empty(), read(share("password", SAS)).connector());
            assertEquals(Optional.empty(), read(ftp).connector());
            assertEquals(Optional.empty(), read(pasted).connector());
        }

        @Test
        void bindsFromEnvironmentVariables() {
            StandardEnvironment environment = new StandardEnvironment();
            environment.getPropertySources().addFirst(new SystemEnvironmentPropertySource(
                    // Spring applies the underscore-to-dot relaxed mapping only to a
                    // SystemEnvironmentPropertySource whose name is "systemEnvironment" or
                    // ends with "-systemEnvironment"; any other name silently binds nothing.
                    "test-systemEnvironment",
                    Map.of(
                            "JEFFREY_MICROSCOPE_CONNECTOR_AZUREFILES_ENDPOINT", ENDPOINT,
                            "JEFFREY_MICROSCOPE_CONNECTOR_AZUREFILES_SHARE", "recordings",
                            "JEFFREY_MICROSCOPE_CONNECTOR_AZUREFILES_AUTH", "entra",
                            "JEFFREY_MICROSCOPE_CONNECTOR_AZUREFILES_READONLY", "true")));

            assertEquals(new AzureFilesAddress(ENDPOINT, "recordings", "", AzureFilesProtocol.SMB,
                            AzureFilesAuth.ENTRA, true),
                    ConfiguredConnector.read(Binder.get(environment), KINDS).connector().orElseThrow().address());
        }
    }

    @Nested
    @DisplayName("A local directory")
    class Local {

        @TempDir
        Path root;

        @Test
        void isWritableNamedByItsDirectory() {
            ConnectorInfo connector = read(directory(root.toString())).connector().orElseThrow();

            assertEquals(new LocalDirectoryAddress(root, false), connector.address());
            assertEquals(root.getFileName().toString(), connector.name());
        }

        @Test
        void bindsItsReadOnlyFlag() {
            Map<String, String> properties = directory(root.toString());
            properties.put("jeffrey.microscope.connector.local.read-only", "true");

            assertTrue(read(properties).connector().orElseThrow().address().readOnly());
        }

        @Test
        void aBlankOrRelativePathIsLeftOut() {
            assertEquals(Optional.empty(), read(directory(" ")).connector());
            assertEquals(Optional.empty(), read(directory("relative/volume")).connector());
        }

        @Test
        void anExplicitNameWinsOverTheDirectoryName() {
            Map<String, String> properties = directory(root.toString());
            properties.put("jeffrey.microscope.connector.name", "Scratch");

            assertEquals("Scratch", read(properties).connector().orElseThrow().name());
        }
    }

    /** Microscope reaches exactly one connector: two kinds declared at once is no connector. */
    @Test
    void bothKindsDeclaredIsNoConnector(@TempDir Path root) {
        Map<String, String> both = share("sas", SAS);
        both.putAll(directory(root.toString()));

        assertEquals(Optional.empty(), read(both).connector());
        assertEquals(Optional.empty(), read(both).declared());
    }
}
