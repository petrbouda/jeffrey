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

package cafe.jeffrey.microscope.core.configuration.properties;

import cafe.jeffrey.microscope.core.configuration.properties.ConfiguredHubsProperties.DesiredHub;
import cafe.jeffrey.microscope.core.configuration.properties.ConfiguredHubsProperties.HubEntry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfiguredHubsPropertiesTest {

    private static final int DEFAULT_PORT = 9090;

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

    @Nested
    @DisplayName("Defaults")
    class Defaults {

        @Test
        void a_hostname_alone_is_a_complete_declaration() {
            List<DesiredHub> resolved = properties(Map.of("prod", entry("hub.example.com"))).resolve();

            assertEquals(1, resolved.size());
            DesiredHub hub = resolved.getFirst();
            assertEquals("prod", hub.name());
            assertEquals(DEFAULT_PORT, hub.address().port());
            assertFalse(hub.address().plaintext());
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
