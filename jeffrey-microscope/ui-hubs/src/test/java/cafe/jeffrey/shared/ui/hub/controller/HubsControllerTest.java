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

package cafe.jeffrey.shared.ui.hub.controller;

import cafe.jeffrey.hub.client.HubClients;
import cafe.jeffrey.shared.common.exception.JeffreyClientException;
import cafe.jeffrey.microscope.model.hub.HubAddress;
import cafe.jeffrey.microscope.model.hub.HubInfo;
import cafe.jeffrey.microscope.model.hub.HubSource;
import cafe.jeffrey.shared.ui.hub.bridge.HubRegistry;
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
