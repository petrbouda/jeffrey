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

package cafe.jeffrey.microscope.core.initializer;

import cafe.jeffrey.microscope.core.configuration.properties.ConfiguredRemoteProperties;
import cafe.jeffrey.microscope.model.remote.HubAddress;
import cafe.jeffrey.microscope.model.remote.RemoteAddress;
import cafe.jeffrey.microscope.model.remote.RemoteInfo;
import cafe.jeffrey.microscope.model.remote.RemoteSource;
import cafe.jeffrey.microscope.persistence.api.RemoteRepository;
import cafe.jeffrey.microscope.remote.client.RemoteClients;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfiguredRemoteReconcilerTest {

    private static final Instant NOW = Instant.parse("2026-09-04T10:15:30Z");
    private static final Instant EARLIER = Instant.parse("2026-01-01T00:00:00Z");

    private final StoredRemote repository = new StoredRemote();
    private final RecordingClientsFactory clientsFactory = new RecordingClientsFactory();

    private void reconcile(ConfiguredRemoteProperties properties) {
        new ConfiguredRemoteReconciler(repository, clientsFactory, properties, Clock.fixed(NOW, ZoneOffset.UTC))
                .reconcile();
    }

    private static ConfiguredRemoteProperties declared(String name, String hostname, int port) {
        ConfiguredRemoteProperties properties = new ConfiguredRemoteProperties();
        properties.setName(name);
        properties.setHostname(hostname);
        properties.setPort(port);
        return properties;
    }

    private static RemoteInfo stored(String name, String hostname, int port, RemoteSource source) {
        return new RemoteInfo(name, new HubAddress(hostname, port), EARLIER, source);
    }

    @Nested
    @DisplayName("Configuration declares a remote")
    class Declared {

        @Test
        void connects_it_without_contacting_it() {
            reconcile(declared("Production", "hub.example.com", 443));

            RemoteInfo connected = repository.remote;
            assertEquals("Production", connected.name());
            assertEquals(new HubAddress("hub.example.com", 443), connected.address());
            assertEquals(RemoteSource.CONFIG, connected.source());
            assertEquals(NOW, connected.createdAt());
        }

        @Test
        void writes_nothing_when_it_is_already_connected_as_declared() {
            repository.remote = stored("Production", "hub.example.com", 443, RemoteSource.CONFIG);

            reconcile(declared("Production", "hub.example.com", 443));

            assertEquals(0, repository.saves);
            assertTrue(clientsFactory.evicted.isEmpty());
        }

        /** Renaming it keeps the connection: its clients stay and its connection time is kept. */
        @Test
        void a_rename_updates_it_in_place() {
            repository.remote = stored("prod", "hub.example.com", 443, RemoteSource.CONFIG);

            reconcile(declared("Production", "hub.example.com", 443));

            assertEquals("Production", repository.remote.name());
            assertEquals(EARLIER, repository.remote.createdAt());
            assertTrue(clientsFactory.evicted.isEmpty());
        }

        /** Another place is another connection: the old address's clients go, and the time starts afresh. */
        @Test
        void a_new_address_replaces_it_and_drops_the_old_clients() {
            repository.remote = stored("Production", "old.example.com", 443, RemoteSource.CONFIG);

            reconcile(declared("Production", "hub.example.com", 443));

            assertEquals(new HubAddress("hub.example.com", 443), repository.remote.address());
            assertEquals(NOW, repository.remote.createdAt());
            assertEquals(List.of(new HubAddress("old.example.com", 443)), clientsFactory.evicted);
        }

        /** Configuration always wins over a remote the UI connected. */
        @Test
        void it_replaces_a_remote_the_ui_connected() {
            repository.remote = stored("Mine", "mine.example.com", 9090, RemoteSource.USER);

            reconcile(declared("Production", "hub.example.com", 443));

            assertEquals(RemoteSource.CONFIG, repository.remote.source());
            assertEquals(List.of(new HubAddress("mine.example.com", 9090)), clientsFactory.evicted);
        }
    }

    @Nested
    @DisplayName("Configuration declares no remote")
    class NotDeclared {

        @Test
        void disconnects_the_remote_it_once_declared() {
            repository.remote = stored("Production", "hub.example.com", 443, RemoteSource.CONFIG);

            reconcile(new ConfiguredRemoteProperties());

            assertEquals(null, repository.remote);
            assertEquals(List.of(new HubAddress("hub.example.com", 443)), clientsFactory.evicted);
        }

        @Test
        void leaves_a_remote_the_ui_connected_alone() {
            RemoteInfo mine = stored("Mine", "mine.example.com", 9090, RemoteSource.USER);
            repository.remote = mine;

            reconcile(new ConfiguredRemoteProperties());

            assertEquals(mine, repository.remote);
            assertEquals(0, repository.saves);
        }

        /** An unusable declaration is left out rather than failing startup, so it leaves things as they are. */
        @Test
        void an_unusable_declaration_counts_as_none() {
            ConfiguredRemoteProperties typo = declared("Production", "hub.example.com", 70_000);

            reconcile(typo);

            assertEquals(null, repository.remote);
        }
    }

    private static final class StoredRemote implements RemoteRepository {

        private RemoteInfo remote;
        private int saves;

        @Override
        public Optional<RemoteInfo> find() {
            return Optional.ofNullable(remote);
        }

        @Override
        public void save(RemoteInfo remote) {
            this.remote = remote;
            saves++;
        }

        @Override
        public void delete() {
            remote = null;
        }
    }

    private static final class RecordingClientsFactory implements RemoteClients.Factory {

        private final List<RemoteAddress> evicted = new ArrayList<>();

        @Override
        public RemoteClients apply(RemoteAddress address) {
            throw new UnsupportedOperationException("No remote is contacted during reconciliation");
        }

        @Override
        public void evict(RemoteAddress address) {
            evicted.add(address);
        }
    }
}
