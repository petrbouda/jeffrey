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

import cafe.jeffrey.microscope.model.hub.HubAddress;
import cafe.jeffrey.microscope.model.hub.GrpcHubAddress;
import cafe.jeffrey.microscope.core.configuration.properties.ConfiguredHubsProperties.DesiredHub;
import cafe.jeffrey.microscope.core.configuration.properties.ConfiguredHubsProperties.AzureFilesEntry;
import cafe.jeffrey.microscope.core.configuration.properties.ConfiguredHubsProperties.HubEntry;
import cafe.jeffrey.microscope.model.hub.AzureFilesAddress;
import cafe.jeffrey.microscope.model.hub.AzureFilesAuth;
import cafe.jeffrey.microscope.model.hub.AzureFilesProtocol;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfiguredHubsPropertiesTest {

    private static final int DEFAULT_PORT = 9090;
    private static final String ENDPOINT = "https://jeffreyprod.file.core.windows.net";
    private static final String SAS = "sv=2026-06-06&sp=rl&sig=c2lnbmF0dXJl";

    /** A gRPC hub's address, failing the test for any other kind. */
    private static GrpcHubAddress grpc(HubAddress address) {
        return assertInstanceOf(GrpcHubAddress.class, address);
    }

    private static HubEntry share(String share, String auth, String sasToken) {
        AzureFilesEntry azure = new AzureFilesEntry();
        azure.setEndpoint(ENDPOINT);
        azure.setShare(share);
        azure.setAuth(auth);
        azure.setSasToken(sasToken);
        HubEntry entry = new HubEntry();
        entry.setAzureFiles(azure);
        return entry;
    }

    private static HubEntry entry(String hostname) {
        HubEntry entry = new HubEntry();
        entry.setHostname(hostname);
        return entry;
    }

    private static ConfiguredHubsProperties properties(Map<String, HubEntry> hubs) {
        ConfiguredHubsProperties properties = new ConfiguredHubsProperties();
        properties.setHubs(new LinkedHashMap<>(hubs));
        return properties;
    }

    /** The recording volume reached straight on an Azure file share, with no hub between. */
    @Nested
    @DisplayName("Azure Files")
    class AzureFiles {

        @Test
        void a_share_resolves_with_smb_and_a_sas_by_default() {
            DesiredHub hub = properties(Map.of("prod-share", share("recordings", "sas", SAS))).resolve().getFirst();

            assertEquals(new AzureFilesAddress(ENDPOINT, "recordings", "", AzureFilesProtocol.SMB, AzureFilesAuth.SAS, false),
                    hub.address());
            assertFalse(hub.address().readOnly(), "a share is writable unless declared read-only");
        }

        @Test
        void a_share_declared_read_only_refuses_changes_and_keeps_its_identity() {
            HubEntry readOnly = share("recordings", "sas", SAS);
            readOnly.getAzureFiles().setReadOnly(true);

            HubAddress address = properties(Map.of("prod-share", readOnly)).resolve().getFirst().address();

            assertTrue(address.readOnly());
            assertEquals(properties(Map.of("prod-share", share("recordings", "sas", SAS))).resolve().getFirst()
                    .address().identity(), address.identity());
        }

        /** A portal's SAS URL pasted as the endpoint is refused without being quoted in the log. */
        @Test
        void a_sas_url_as_the_endpoint_is_skipped_without_quoting_it() {
            HubEntry pasted = share("recordings", "sas", SAS);
            pasted.getAzureFiles().setEndpoint(ENDPOINT + "/?sv=2026-06-06&sp=rl&sig=PASTEDSIGNATURE");

            assertEquals(List.of(), properties(Map.of("prod-share", pasted)).resolve());
            assertEquals(Map.of(), properties(Map.of("prod-share", pasted)).sasTokens());
        }

        /** The token reaches whatever opens the share by its identity, and never the registry or a log line. */
        @Test
        void the_sas_token_stays_out_of_the_address() {
            ConfiguredHubsProperties properties = properties(Map.of("prod-share", share("recordings", "sas", SAS)));
            HubAddress address = properties.resolve().getFirst().address();

            assertEquals(Map.of(address.identity(), SAS), properties.sasTokens());
            assertFalse(address.toString().contains("sig="));
            assertFalse(address.identity().contains("sig="));
        }

        @Test
        void entra_needs_no_token() {
            ConfiguredHubsProperties properties = properties(Map.of("prod-share", share("recordings", "entra", null)));

            assertEquals(AzureFilesAuth.ENTRA,
                    assertInstanceOf(AzureFilesAddress.class, properties.resolve().getFirst().address()).auth());
            assertEquals(Map.of(), properties.sasTokens());
        }

        @Test
        void a_sas_without_its_token_is_skipped() {
            assertEquals(List.of(), properties(Map.of("prod-share", share("recordings", "sas", " "))).resolve());
        }

        @Test
        void an_unknown_protocol_or_sign_in_is_skipped() {
            HubEntry smbx = share("recordings", "sas", SAS);
            smbx.getAzureFiles().setProtocol("smbx");

            assertEquals(List.of(), properties(Map.of("a", smbx, "b", share("other", "oauth", SAS))).resolve());
        }

        @Test
        void a_hostname_and_a_share_together_are_skipped() {
            HubEntry both = share("recordings", "sas", SAS);
            both.setHostname("hub.example.com");

            assertEquals(List.of(), properties(Map.of("prod", both)).resolve());
        }

        /** One share declared twice has no correct resolution, whatever each declaration signs in with. */
        @Test
        void one_share_declared_twice_is_ambiguous() {
            Map<String, HubEntry> twice = new LinkedHashMap<>();
            twice.put("prod-a", share("recordings", "sas", SAS));
            twice.put("prod-b", share("recordings", "entra", null));

            assertThrows(IllegalArgumentException.class, () -> properties(twice).resolve());
        }
    }

    @Nested
    @DisplayName("Defaults")
    class Defaults {

        @Test
        void a_hostname_alone_is_a_complete_declaration() {
            List<DesiredHub> resolved = properties(Map.of("prod", entry("hub.example.com"))).resolve();

            assertEquals(1, resolved.size());
            DesiredHub hub = resolved.getFirst();
            assertEquals("prod", hub.name());
            assertEquals(DEFAULT_PORT, grpc(hub.address()).port());
            assertFalse(grpc(hub.address()).plaintext());
        }

        @Test
        void an_explicit_name_wins_over_the_key() {
            HubEntry entry = entry("hub.example.com");
            entry.setName("  Production  ");

            assertEquals("Production", properties(Map.of("prod", entry)).resolve().getFirst().name());
        }
    }

    @Nested
    @DisplayName("Hub id derivation")
    class HubIds {

        @Test
        void is_prefixed_and_normalised() {
            assertEquals("cfg-prod", ConfiguredHubsProperties.hubIdFor("PROD"));
        }

        @Test
        void a_dashed_key_and_its_environment_variable_spelling_resolve_to_the_same_hub() {
            // An environment variable cannot carry a dash, so JEFFREY_MICROSCOPE_HUBS_PRODEU_...
            // binds as "prodeu" while a properties file spells the same hub "prod-eu". Both must
            // land on one id, or moving a configuration between the two forms would orphan the
            // origin.hubId tags already written onto downloaded recordings.
            assertEquals(
                    ConfiguredHubsProperties.hubIdFor("prodeu"),
                    ConfiguredHubsProperties.hubIdFor("prod-eu"));
        }
    }

    @Nested
    @DisplayName("An invalid declaration")
    class InvalidEntries {

        @Test
        void a_blank_hostname_is_skipped_rather_than_failing_startup() {
            Map<String, HubEntry> hubs = new LinkedHashMap<>();
            hubs.put("broken", entry("   "));
            hubs.put("good", entry("hub.example.com"));

            List<DesiredHub> resolved = properties(hubs).resolve();

            assertEquals(1, resolved.size());
            assertEquals("good", resolved.getFirst().key());
        }

        @Test
        void a_port_out_of_range_is_skipped() {
            HubEntry entry = entry("hub.example.com");
            entry.setPort(0);

            assertTrue(properties(Map.of("broken", entry)).resolve().isEmpty());
        }

        @Test
        void an_entry_with_no_properties_at_all_is_skipped() {
            Map<String, HubEntry> hubs = new LinkedHashMap<>();
            hubs.put("empty", null);

            assertTrue(properties(hubs).resolve().isEmpty());
        }
    }

    @Nested
    @DisplayName("An ambiguous declaration")
    class Ambiguity {

        @Test
        void two_keys_resolving_to_one_id_fail_fast() {
            Map<String, HubEntry> hubs = new LinkedHashMap<>();
            hubs.put("prod-eu", entry("a.example.com"));
            hubs.put("prod_eu", entry("b.example.com"));

            IllegalArgumentException e = assertThrows(
                    IllegalArgumentException.class, () -> properties(hubs).resolve());
            assertTrue(e.getMessage().contains("same identity"));
        }

        @Test
        void two_entries_on_one_address_fail_fast() {
            Map<String, HubEntry> hubs = new LinkedHashMap<>();
            hubs.put("prod", entry("hub.example.com"));
            hubs.put("backup", entry("hub.example.com"));

            IllegalArgumentException e = assertThrows(
                    IllegalArgumentException.class, () -> properties(hubs).resolve());
            assertTrue(e.getMessage().contains("Duplicate hub address"));
        }

        @Test
        void the_address_clash_ignores_plaintext_just_as_the_unique_constraint_does() {
            HubEntry plaintext = entry("hub.example.com");
            plaintext.setPlaintext(true);

            Map<String, HubEntry> hubs = new LinkedHashMap<>();
            hubs.put("prod", entry("hub.example.com"));
            hubs.put("backup", plaintext);

            assertThrows(IllegalArgumentException.class, () -> properties(hubs).resolve());
        }
    }
}
