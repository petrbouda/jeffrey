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

package cafe.jeffrey.shared.ui.workspace.controller;

import cafe.jeffrey.hub.client.HubClients;
import cafe.jeffrey.shared.common.exception.JeffreyClientException;
import cafe.jeffrey.shared.common.model.hub.HubAddress;
import cafe.jeffrey.shared.common.model.hub.HubInfo;
import cafe.jeffrey.shared.common.model.hub.HubSource;
import cafe.jeffrey.shared.ui.workspace.bridge.HubRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HubsControllerTest {

    private static final Instant CREATED_AT = Instant.parse("2026-01-01T00:00:00Z");

    private final RecordingHubRegistry registry = new RecordingHubRegistry();

    private final HubClients.Factory clientsFactory = address -> {
        throw new UnsupportedOperationException("No hub is contacted in this test");
    };

    private final HubsController controller = new HubsController(registry, clientsFactory);

    private static HubInfo hub(String hubId, String name, HubSource source) {
        return new HubInfo(hubId, name, new HubAddress("hub.example.com", 443), CREATED_AT, source);
    }

    @Nested
    @DisplayName("Deleting a hub")
    class Delete {

        @Test
        void a_configured_hub_is_refused_and_never_reaches_the_registry() {
            registry.rows.add(hub("cfg-prod", "Production", HubSource.CONFIG));

            JeffreyClientException e = assertThrows(
                    JeffreyClientException.class, () -> controller.delete("cfg-prod"));

            assertTrue(e.getMessage().contains("declared in configuration"));
            // Deleting it would only last until the next startup recreated it.
            assertTrue(registry.deleted.isEmpty());
        }

        @Test
        void a_user_added_hub_is_deleted() {
            registry.rows.add(hub("uuid-1", "Mine", HubSource.USER));

            controller.delete("uuid-1");

            assertEquals(List.of("uuid-1"), registry.deleted);
        }

        @Test
        void an_unknown_hub_is_rejected() {
            assertThrows(JeffreyClientException.class, () -> controller.delete("nope"));
        }
    }

    @Nested
    @DisplayName("Listing hubs")
    class Listing {

        @Test
        void carries_the_source_so_the_ui_can_mark_configured_hubs_read_only() {
            registry.rows.add(hub("cfg-prod", "Production", HubSource.CONFIG));

            assertEquals("CONFIG", controller.list().getFirst().source());
        }
    }

    private static final class RecordingHubRegistry implements HubRegistry {

        private final List<HubInfo> rows = new ArrayList<>();
        private final List<String> deleted = new ArrayList<>();

        @Override
        public List<HubInfo> findAll() {
            return List.copyOf(rows);
        }

        @Override
        public HubInfo create(String name, HubAddress address) {
            throw new UnsupportedOperationException("Not exercised by these tests");
        }

        @Override
        public Optional<HubInfo> findById(String hubId) {
            return rows.stream().filter(row -> row.hubId().equals(hubId)).findFirst();
        }

        @Override
        public void delete(String hubId) {
            deleted.add(hubId);
            rows.removeIf(row -> row.hubId().equals(hubId));
        }
    }
}
