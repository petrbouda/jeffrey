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

import cafe.jeffrey.microscope.core.configuration.properties.ConfiguredConnectorProperties.AzureFilesEntry;
import cafe.jeffrey.microscope.core.configuration.properties.ConfiguredConnectorProperties.LocalEntry;
import cafe.jeffrey.microscope.model.connector.ConnectorInfo;
import cafe.jeffrey.microscope.model.connector.ConnectorKind;
import cafe.jeffrey.microscope.model.connector.AzureFilesAddress;
import cafe.jeffrey.microscope.model.connector.AzureFilesAuth;
import cafe.jeffrey.microscope.model.connector.AzureFilesProtocol;
import cafe.jeffrey.microscope.model.connector.LocalDirectoryAddress;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfiguredConnectorPropertiesTest {

    private static final String ENDPOINT = "https://jeffreyprod.file.core.windows.net";
    private static final String SAS = "sv=2026-06-06&ss=f&srt=sco&sp=rl&sig=c2lnbmF0dXJl";

    private static ConfiguredConnectorProperties share(String share, String auth, String sasToken) {
        AzureFilesEntry azure = new AzureFilesEntry();
        azure.setEndpoint(ENDPOINT);
        azure.setShare(share);
        azure.setAuth(auth);
        azure.setSasToken(sasToken);
        ConfiguredConnectorProperties properties = new ConfiguredConnectorProperties();
        properties.setAzureFiles(azure);
        return properties;
    }

    private static ConfiguredConnectorProperties directory(String path) {
        LocalEntry local = new LocalEntry();
        local.setPath(path);
        ConfiguredConnectorProperties properties = new ConfiguredConnectorProperties();
        properties.setLocal(local);
        return properties;
    }

    @Test
    void nothing_declared_is_no_connector() {
        assertEquals(Optional.empty(), new ConfiguredConnectorProperties().resolve());
        assertEquals(Optional.empty(), new ConfiguredConnectorProperties().sasToken());
    }

    @Nested
    @DisplayName("An Azure file share")
    class AzureFiles {

        @Test
        void resolves_writable_over_smb_with_a_sas_by_default_named_by_its_share() {
            ConnectorInfo connector = share("recordings", "sas", SAS).resolve().orElseThrow();

            assertEquals(new AzureFilesAddress(ENDPOINT, "recordings", "", AzureFilesProtocol.SMB, AzureFilesAuth.SAS,
                    false), connector.address());
            assertEquals("recordings", connector.name());
            assertFalse(connector.address().readOnly(), "a share is writable unless declared read-only");
        }

        @Test
        void declared_read_only_refuses_changes() {
            ConfiguredConnectorProperties readOnly = share("recordings", "sas", SAS);
            readOnly.getAzureFiles().setReadOnly(true);

            assertTrue(readOnly.resolve().orElseThrow().address().readOnly());
        }

        /** A portal's SAS URL pasted as the endpoint is refused without being quoted in the log. */
        @Test
        void a_sas_url_as_the_endpoint_is_left_out() {
            ConfiguredConnectorProperties pasted = share("recordings", "sas", SAS);
            pasted.getAzureFiles().setEndpoint(ENDPOINT + "/?sv=2026-06-06&sp=rl&sig=PASTEDSIGNATURE");

            assertEquals(Optional.empty(), pasted.resolve());
            assertEquals(Optional.empty(), pasted.sasToken());
        }

        /** The token reaches whatever opens the share, and never the address or a log line. */
        @Test
        void reads_the_connector_and_its_token_together_once() {
            ConfiguredConnectorProperties.Declared declared = share("recordings", "sas", SAS).read();

            assertEquals("recordings", declared.connector().orElseThrow().name());
            assertEquals(Optional.of(SAS), declared.sasToken());
        }

        @Test
        void the_sas_token_stays_out_of_the_address() {
            ConfiguredConnectorProperties properties = share("recordings", "sas", " " + SAS + " ");

            assertEquals(Optional.of(SAS), properties.sasToken());
            assertFalse(properties.resolve().orElseThrow().address().toString().contains("sig="));
            assertFalse(properties.resolve().orElseThrow().address().identity().contains("sig="));
        }

        @Test
        void entra_needs_no_token() {
            ConnectorInfo connector = share("recordings", "entra", null).resolve().orElseThrow();

            assertEquals(AzureFilesAuth.ENTRA, ((AzureFilesAddress) connector.address()).auth());
            assertEquals(Optional.empty(), share("recordings", "entra", null).sasToken());
        }

        @Test
        void a_sas_without_its_token_is_left_out() {
            assertEquals(Optional.empty(), share("recordings", "sas", " ").resolve());
        }

        @Test
        void an_explicit_name_wins_over_the_share_name() {
            ConfiguredConnectorProperties properties = share("recordings", "sas", SAS);
            properties.setName(" Production ");

            assertEquals("Production", properties.resolve().orElseThrow().name());
        }

        @Test
        void an_unknown_protocol_or_sign_in_is_left_out() {
            ConfiguredConnectorProperties ftp = share("recordings", "sas", SAS);
            ftp.getAzureFiles().setProtocol("ftp");

            assertEquals(Optional.empty(), ftp.resolve());
            assertEquals(Optional.empty(), share("recordings", "password", SAS).resolve());
        }
    }

    @Nested
    @DisplayName("A local directory")
    class Local {

        @TempDir
        Path root;

        @Test
        void resolves_writable_named_by_its_directory() {
            ConnectorInfo connector = directory(root.toString()).resolve().orElseThrow();

            assertEquals(new LocalDirectoryAddress(root, false), connector.address());
            assertEquals(root.getFileName().toString(), connector.name());
            assertEquals(ConnectorKind.LOCAL, connector.address().kind());
        }

        @Test
        void declared_read_only_refuses_changes() {
            ConfiguredConnectorProperties readOnly = directory(root.toString());
            readOnly.getLocal().setReadOnly(true);

            assertTrue(readOnly.resolve().orElseThrow().address().readOnly());
        }

        /** Nothing to sign in with: no token is ever handed on for a directory. */
        @Test
        void carries_no_token() {
            assertEquals(Optional.empty(), directory(root.toString()).read().sasToken());
        }

        /** Not there yet is still a declaration: the connector reads as unreachable until it appears. */
        @Test
        void a_directory_not_there_yet_is_still_declared() {
            Path later = root.resolve("later");

            ConnectorInfo connector = directory(later.toString()).resolve().orElseThrow();

            assertEquals(new LocalDirectoryAddress(later, false), connector.address());
        }

        @Test
        void a_blank_or_relative_path_is_left_out() {
            assertEquals(Optional.empty(), directory(" ").resolve());
            assertEquals(Optional.empty(), directory(null).resolve());
            assertEquals(Optional.empty(), directory("relative/volume").resolve());
        }

        @Test
        void an_explicit_name_wins_over_the_directory_name() {
            ConfiguredConnectorProperties properties = directory(root.toString());
            properties.setName("Scratch");

            assertEquals("Scratch", properties.resolve().orElseThrow().name());
        }
    }

    /** Microscope reaches exactly one connector: two kinds declared at once is no connector. */
    @Test
    void both_kinds_declared_is_no_connector(@TempDir Path root) {
        ConfiguredConnectorProperties both = share("recordings", "sas", SAS);
        both.setLocal(directory(root.toString()).getLocal());

        assertEquals(Optional.empty(), both.resolve());
        assertEquals(Optional.empty(), both.sasToken());
    }
}
