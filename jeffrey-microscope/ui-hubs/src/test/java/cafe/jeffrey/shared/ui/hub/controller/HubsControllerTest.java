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

import cafe.jeffrey.hub.client.DiscoveryClient;
import cafe.jeffrey.hub.client.GrpcDiscoveryClient;
import cafe.jeffrey.hub.client.HubClients;
import cafe.jeffrey.microscope.grpc.client.GrpcHubConnection;
import cafe.jeffrey.shared.ui.hub.dto.HubResponse;
import cafe.jeffrey.shared.ui.hub.request.AddHubRequest;
import io.grpc.Status;
import cafe.jeffrey.shared.common.exception.JeffreyClientException;
import cafe.jeffrey.microscope.model.hub.AzureFilesAddress;
import cafe.jeffrey.microscope.model.hub.AzureFilesAuth;
import cafe.jeffrey.microscope.model.hub.AzureFilesProtocol;
import cafe.jeffrey.microscope.model.hub.GrpcHubAddress;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
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
        return new HubInfo(hubId, name, new GrpcHubAddress("hub.example.com", 443), CREATED_AT, source);
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
    @DisplayName("Adding a hub")
    class Add {

        @Test
        void an_unreachable_address_is_refused_and_its_probe_connection_let_go() {
            GrpcHubAddress address = new GrpcHubAddress("hub.invalid", 9090, true);
            List<HubAddress> evicted = new ArrayList<>();
            GrpcHubConnection connection = new GrpcHubConnection(address);
            DiscoveryClient unreachable = new GrpcDiscoveryClient(connection) {
                @Override
                public PublicApiInfo info() {
                    throw Status.UNAVAILABLE.asRuntimeException();
                }
            };
            HubClients.Factory probing = new HubClients.Factory() {
                @Override
                public HubClients apply(HubAddress requested) {
                    return new HubClients(unreachable, null, null, null, null);
                }

                @Override
                public void evict(HubAddress requested) {
                    evicted.add(requested);
                }
            };

            try {
                HubsController adding = new HubsController(registry, probing);
                assertThrows(JeffreyClientException.class, () -> adding.add(
                        new AddHubRequest("Typo", address.hostname(), address.port(), address.plaintext())));

                assertEquals(List.of(address), evicted);
            } finally {
                connection.close();
            }
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

        @Test
        void a_grpc_hub_is_reported_by_its_host_and_port() {
            registry.rows.add(hub("uuid-1", "Mine", HubSource.USER));

            HubResponse response = controller.info("uuid-1");

            assertEquals("GRPC", response.kind());
            assertEquals("hub.example.com:443", response.location());
            assertEquals(443, response.port());
            assertFalse(response.readOnly());
        }

        /** A share read without a hub is read-only, and named by its endpoint, share and directory. */
        @Test
        void an_azure_files_share_is_reported_read_only_by_its_location() {
            registry.rows.add(new HubInfo("cfg-share", "Prod share", new AzureFilesAddress(
                    "https://jeffreyprod.file.core.windows.net", "recordings", "volume",
                    AzureFilesProtocol.SMB, AzureFilesAuth.SAS, true), CREATED_AT, HubSource.CONFIG));

            HubResponse response = controller.info("cfg-share");

            assertEquals("AZURE_FILES", response.kind());
            assertEquals("https://jeffreyprod.file.core.windows.net/recordings/volume", response.location());
            assertTrue(response.readOnly());
            assertNull(response.hostname());
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
