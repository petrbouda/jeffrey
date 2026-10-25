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

package cafe.jeffrey.shared.ui.remote.controller;

import cafe.jeffrey.microscope.grpc.client.GrpcHubConnection;
import cafe.jeffrey.microscope.model.remote.AzureFilesAddress;
import cafe.jeffrey.microscope.model.remote.AzureFilesAuth;
import cafe.jeffrey.microscope.model.remote.AzureFilesProtocol;
import cafe.jeffrey.microscope.model.remote.HubAddress;
import cafe.jeffrey.microscope.model.remote.RemoteAddress;
import cafe.jeffrey.microscope.model.remote.RemoteInfo;
import cafe.jeffrey.microscope.model.remote.RemoteSource;
import cafe.jeffrey.microscope.remote.client.DiscoveryClient;
import cafe.jeffrey.microscope.remote.client.GrpcDiscoveryClient;
import cafe.jeffrey.microscope.remote.client.RemoteClients;
import cafe.jeffrey.shared.common.exception.JeffreyClientException;
import cafe.jeffrey.shared.ui.remote.bridge.RemoteRegistry;
import cafe.jeffrey.shared.ui.remote.dto.RemoteResponse;
import cafe.jeffrey.shared.ui.remote.request.ConnectHubRequest;
import io.grpc.Status;
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

class RemoteControllerTest {

    private static final Instant CREATED_AT = Instant.parse("2026-01-01T00:00:00Z");

    private final StoredRemote registry = new StoredRemote();

    private final RemoteClients.Factory clientsFactory = address -> {
        throw new UnsupportedOperationException("No remote is contacted in this test");
    };

    private final RemoteController controller = new RemoteController(registry, clientsFactory);

    private static RemoteInfo hub(String name, RemoteSource source) {
        return new RemoteInfo(name, new HubAddress("hub.example.com", 443), CREATED_AT, source);
    }

    @Nested
    @DisplayName("Disconnecting")
    class Disconnect {

        @Test
        void a_configured_remote_is_refused_and_stays_connected() {
            registry.remote = hub("Production", RemoteSource.CONFIG);

            JeffreyClientException e = assertThrows(JeffreyClientException.class, controller::disconnect);

            assertTrue(e.getMessage().contains("declared in configuration"));
            // Disconnecting it would only last until the next startup connected it again.
            assertFalse(registry.disconnected);
        }

        @Test
        void a_remote_the_ui_connected_is_disconnected() {
            registry.remote = hub("Mine", RemoteSource.USER);

            controller.disconnect();

            assertTrue(registry.disconnected);
        }

        @Test
        void nothing_connected_is_not_found() {
            JeffreyClientException e = assertThrows(JeffreyClientException.class, controller::disconnect);

            assertTrue(e.getCode().isNotFound());
        }
    }

    @Nested
    @DisplayName("Connecting a Jeffrey Hub")
    class Connect {

        @Test
        void a_second_remote_is_refused_before_anything_is_probed() {
            registry.remote = hub("Production", RemoteSource.CONFIG);

            JeffreyClientException e = assertThrows(JeffreyClientException.class, () -> controller.connect(
                    new ConnectHubRequest("Other", "other.example.com", 443, false)));

            assertTrue(e.getMessage().contains("Already connected"), e.getMessage());
        }

        @Test
        void an_unreachable_address_is_refused_and_its_probe_connection_let_go() {
            HubAddress address = new HubAddress("hub.invalid", 9090, true);
            List<RemoteAddress> evicted = new ArrayList<>();
            GrpcHubConnection connection = new GrpcHubConnection(address);
            DiscoveryClient unreachable = new GrpcDiscoveryClient(connection) {
                @Override
                public PublicApiInfo info() {
                    throw Status.UNAVAILABLE.asRuntimeException();
                }
            };
            RemoteClients.Factory probing = new RemoteClients.Factory() {
                @Override
                public RemoteClients apply(RemoteAddress requested) {
                    return new RemoteClients(unreachable, null, null, null, null);
                }

                @Override
                public void evict(RemoteAddress requested) {
                    evicted.add(requested);
                }
            };

            try {
                RemoteController connecting = new RemoteController(registry, probing);
                assertThrows(JeffreyClientException.class, () -> connecting.connect(
                        new ConnectHubRequest("Typo", address.hostname(), address.port(), address.plaintext())));

                assertEquals(List.of(address), evicted);
                assertNull(registry.remote);
            } finally {
                connection.close();
            }
        }
    }

    @Nested
    @DisplayName("Reading the remote")
    class Current {

        @Test
        void nothing_connected_reads_as_no_remote_rather_than_an_error() {
            assertNull(controller.current().remote());
        }

        @Test
        void carries_the_source_so_the_ui_can_refuse_to_disconnect_a_configured_remote() {
            registry.remote = hub("Production", RemoteSource.CONFIG);

            assertEquals("CONFIG", controller.current().remote().source());
        }

        @Test
        void a_jeffrey_hub_is_reported_by_its_host_and_port() {
            registry.remote = hub("Mine", RemoteSource.USER);

            RemoteResponse response = controller.current().remote();

            assertEquals("GRPC", response.kind());
            assertEquals("hub.example.com:443", response.location());
            assertEquals(443, response.port());
            assertFalse(response.readOnly());
        }

        /** A share declared read-only says so, and is named by its endpoint, share and directory. */
        @Test
        void an_azure_files_share_is_reported_by_its_location() {
            registry.remote = new RemoteInfo("Prod share", new AzureFilesAddress(
                    "https://jeffreyprod.file.core.windows.net", "recordings", "volume",
                    AzureFilesProtocol.SMB, AzureFilesAuth.SAS, true), CREATED_AT, RemoteSource.CONFIG);

            RemoteResponse response = controller.current().remote();

            assertEquals("AZURE_FILES", response.kind());
            assertEquals("https://jeffreyprod.file.core.windows.net/recordings/volume", response.location());
            assertTrue(response.readOnly());
            assertNull(response.hostname());
        }
    }

    private static final class StoredRemote implements RemoteRegistry {

        private RemoteInfo remote;
        private boolean disconnected;

        @Override
        public Optional<RemoteInfo> find() {
            return Optional.ofNullable(remote);
        }

        @Override
        public RemoteInfo connect(String name, HubAddress address) {
            throw new UnsupportedOperationException("Not exercised by these tests");
        }

        @Override
        public void disconnect() {
            disconnected = true;
            remote = null;
        }
    }
}
