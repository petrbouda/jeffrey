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

import cafe.jeffrey.microscope.core.configuration.properties.ConfiguredRemoteProperties.AzureFilesEntry;
import cafe.jeffrey.microscope.model.remote.RemoteInfo;
import cafe.jeffrey.microscope.model.remote.AzureFilesAddress;
import cafe.jeffrey.microscope.model.remote.AzureFilesAuth;
import cafe.jeffrey.microscope.model.remote.AzureFilesProtocol;
import cafe.jeffrey.microscope.model.remote.HubAddress;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfiguredRemotePropertiesTest {

    private static final String ENDPOINT = "https://jeffreyprod.file.core.windows.net";
    private static final String SAS = "sv=2026-06-06&ss=f&srt=sco&sp=rl&sig=c2lnbmF0dXJl";

    private static ConfiguredRemoteProperties hub(String hostname) {
        ConfiguredRemoteProperties properties = new ConfiguredRemoteProperties();
        properties.setHostname(hostname);
        return properties;
    }

    private static ConfiguredRemoteProperties share(String share, String auth, String sasToken) {
        AzureFilesEntry azure = new AzureFilesEntry();
        azure.setEndpoint(ENDPOINT);
        azure.setShare(share);
        azure.setAuth(auth);
        azure.setSasToken(sasToken);
        ConfiguredRemoteProperties properties = new ConfiguredRemoteProperties();
        properties.setAzureFiles(azure);
        return properties;
    }

    @Test
    void nothing_declared_is_no_remote() {
        assertEquals(Optional.empty(), new ConfiguredRemoteProperties().resolve());
        assertEquals(Optional.empty(), new ConfiguredRemoteProperties().sasToken());
    }

    @Nested
    @DisplayName("A Jeffrey Hub")
    class JeffreyHub {

        @Test
        void a_hostname_alone_is_a_complete_declaration_named_by_its_host() {
            RemoteInfo remote = hub(" hub.example.com ").resolve().orElseThrow();

            assertEquals(new HubAddress("hub.example.com", 9090, false), remote.address());
            assertEquals("hub.example.com", remote.name());
        }

        @Test
        void an_explicit_name_and_port_win() {
            ConfiguredRemoteProperties properties = hub("hub.example.com");
            properties.setName(" Production ");
            properties.setPort(443);
            properties.setPlaintext(true);

            RemoteInfo remote = properties.resolve().orElseThrow();

            assertEquals("Production", remote.name());
            assertEquals(new HubAddress("hub.example.com", 443, true), remote.address());
        }

        /** A typo in an optional remote must not cost the user Microscope; it is logged and left out. */
        @Test
        void a_port_out_of_range_is_left_out_rather_than_failing_startup() {
            ConfiguredRemoteProperties properties = hub("hub.example.com");
            properties.setPort(70_000);

            assertEquals(Optional.empty(), properties.resolve());
        }
    }

    @Nested
    @DisplayName("An Azure file share")
    class AzureFiles {

        @Test
        void resolves_writable_over_smb_with_a_sas_by_default_named_by_its_share() {
            RemoteInfo remote = share("recordings", "sas", SAS).resolve().orElseThrow();

            assertEquals(new AzureFilesAddress(ENDPOINT, "recordings", "", AzureFilesProtocol.SMB, AzureFilesAuth.SAS,
                    false), remote.address());
            assertEquals("recordings", remote.name());
            assertFalse(remote.address().readOnly(), "a share is writable unless declared read-only");
        }

        @Test
        void declared_read_only_refuses_changes_and_keeps_its_identity() {
            ConfiguredRemoteProperties readOnly = share("recordings", "sas", SAS);
            readOnly.getAzureFiles().setReadOnly(true);

            RemoteInfo remote = readOnly.resolve().orElseThrow();

            assertTrue(remote.address().readOnly());
            assertEquals(share("recordings", "sas", SAS).resolve().orElseThrow().address().identity(),
                    remote.address().identity());
        }

        /** A portal's SAS URL pasted as the endpoint is refused without being quoted in the log. */
        @Test
        void a_sas_url_as_the_endpoint_is_left_out() {
            ConfiguredRemoteProperties pasted = share("recordings", "sas", SAS);
            pasted.getAzureFiles().setEndpoint(ENDPOINT + "/?sv=2026-06-06&sp=rl&sig=PASTEDSIGNATURE");

            assertEquals(Optional.empty(), pasted.resolve());
            assertEquals(Optional.empty(), pasted.sasToken());
        }

        /** The token reaches whatever opens the share, and never the stored address or a log line. */
        @Test
        void the_sas_token_stays_out_of_the_address() {
            ConfiguredRemoteProperties properties = share("recordings", "sas", " " + SAS + " ");

            assertEquals(Optional.of(SAS), properties.sasToken());
            assertFalse(properties.resolve().orElseThrow().address().toString().contains("sig="));
            assertFalse(properties.resolve().orElseThrow().address().identity().contains("sig="));
        }

        @Test
        void entra_needs_no_token() {
            RemoteInfo remote = share("recordings", "entra", null).resolve().orElseThrow();

            assertEquals(AzureFilesAuth.ENTRA, ((AzureFilesAddress) remote.address()).auth());
            assertEquals(Optional.empty(), share("recordings", "entra", null).sasToken());
        }

        @Test
        void a_sas_without_its_token_is_left_out() {
            assertEquals(Optional.empty(), share("recordings", "sas", " ").resolve());
        }

        @Test
        void an_unknown_protocol_or_sign_in_is_left_out() {
            ConfiguredRemoteProperties ftp = share("recordings", "sas", SAS);
            ftp.getAzureFiles().setProtocol("ftp");

            assertEquals(Optional.empty(), ftp.resolve());
            assertEquals(Optional.empty(), share("recordings", "password", SAS).resolve());
        }

        @Test
        void a_hostname_and_a_share_together_are_left_out() {
            ConfiguredRemoteProperties both = share("recordings", "sas", SAS);
            both.setHostname("hub.example.com");

            assertEquals(Optional.empty(), both.resolve());
        }
    }
}
