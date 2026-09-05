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

package cafe.jeffrey.microscope.core.initializer;

import cafe.jeffrey.hub.client.CachedHubClientsFactory;
import cafe.jeffrey.microscope.core.configuration.properties.ConfiguredHubsProperties;
import cafe.jeffrey.microscope.core.configuration.properties.ConfiguredHubsProperties.HubEntry;
import cafe.jeffrey.microscope.persistence.api.HubsRepository;
import cafe.jeffrey.shared.common.model.hub.HubAddress;
import cafe.jeffrey.shared.common.model.hub.HubInfo;
import cafe.jeffrey.shared.common.model.hub.HubSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfiguredHubsReconcilerTest {

    private static final Instant NOW = Instant.parse("2026-09-04T10:15:30Z");
    private static final Instant EARLIER = Instant.parse("2026-01-01T00:00:00Z");

    private final InMemoryHubsRepository repository = new InMemoryHubsRepository();
    private final RecordingClientsFactory clientsFactory = new RecordingClientsFactory();

    private void reconcile(Map<String, HubEntry> hubs) {
        ConfiguredHubsProperties properties = new ConfiguredHubsProperties();
        properties.setHubs(new LinkedHashMap<>(hubs));

        new ConfiguredHubsReconciler(
                repository,
                clientsFactory,
                properties,
                new ConfiguredHubsPlanner(Clock.fixed(NOW, ZoneOffset.UTC)))
                .reconcile();
    }

    private static HubEntry entry(String hostname, int port) {
        HubEntry entry = new HubEntry();
        entry.setHostname(hostname);
        entry.setPort(port);
        return entry;
    }

    private static HubInfo stored(String hubId, String name, String hostname, int port, HubSource source) {
        return new HubInfo(hubId, name, new HubAddress(hostname, port), EARLIER, source);
    }

    @Nested
    @DisplayName("With nothing stored")
    class FreshRegistry {

        @Test
        void registers_each_declared_hub_without_contacting_it() {
            reconcile(Map.of("prod", entry("hub.example.com", 443)));

            assertEquals(1, repository.findAll().size());
            HubInfo created = repository.findAll().getFirst();
            assertEquals("cfg-prod", created.hubId());
            assertEquals(HubSource.CONFIG, created.source());
            assertEquals(NOW, created.createdAt());
        }
    }

    @Nested
    @DisplayName("Running twice over an unchanged configuration")
    class Idempotence {

        @Test
        void writes_nothing_the_second_time() {
            Map<String, HubEntry> hubs = Map.of("prod", entry("hub.example.com", 443));
            reconcile(hubs);

            int writesAfterFirstRun = repository.writes;
            reconcile(hubs);

            assertEquals(writesAfterFirstRun, repository.writes);
            assertEquals(1, repository.findAll().size());
        }
    }

    @Nested
    @DisplayName("When a declaration changes")
    class Changes {

        @Test
        void a_rename_keeps_the_id_creation_time_and_channel() {
            repository.seed(stored("cfg-prod", "prod", "hub.example.com", 443, HubSource.CONFIG));

            HubEntry renamed = entry("hub.example.com", 443);
            renamed.setName("Production");
            reconcile(Map.of("prod", renamed));

            HubInfo updated = repository.findAll().getFirst();
            assertEquals("Production", updated.name());
            assertEquals("cfg-prod", updated.hubId());
            assertEquals(EARLIER, updated.createdAt());
            // The address did not move, so the cached channel is still the right one.
            assertTrue(clientsFactory.evicted.isEmpty());
        }

        @Test
        void a_new_address_drops_the_channel_cached_for_the_old_one() {
            HubAddress oldAddress = new HubAddress("hub.example.com", 443);
            repository.seed(new HubInfo("cfg-prod", "prod", oldAddress, EARLIER, HubSource.CONFIG));

            reconcile(Map.of("prod", entry("moved.example.com", 443)));

            assertTrue(clientsFactory.evicted.contains(oldAddress));
            HubInfo moved = repository.findAll().getFirst();
            assertEquals("cfg-prod", moved.hubId());
            assertEquals("moved.example.com", moved.address().hostname());
            assertEquals(EARLIER, moved.createdAt());
        }
    }

    @Nested
    @DisplayName("When a hub is dropped from the configuration")
    class Retirement {

        @Test
        void the_row_and_its_channel_are_removed() {
            HubAddress address = new HubAddress("old.example.com", 443);
            repository.seed(new HubInfo("cfg-old", "old", address, EARLIER, HubSource.CONFIG));

            reconcile(Map.of());

            assertTrue(repository.findAll().isEmpty());
            assertTrue(clientsFactory.evicted.contains(address));
        }

        @Test
        void a_user_added_hub_survives_an_empty_configuration() {
            HubInfo userHub = stored("uuid-1", "Mine", "mine.example.com", 9090, HubSource.USER);
            repository.seed(userHub);

            reconcile(Map.of());

            assertEquals(1, repository.findAll().size());
            assertSame(HubSource.USER, repository.findAll().getFirst().source());
        }
    }

    @Nested
    @DisplayName("When configuration claims a user-added hub's address")
    class Adoption {

        @Test
        void the_row_is_adopted_rather_than_recreated() {
            repository.seed(stored("uuid-1", "Mine", "mine.example.com", 9090, HubSource.USER));

            reconcile(Map.of("prod", entry("mine.example.com", 9090)));

            assertEquals(1, repository.findAll().size());
            HubInfo adopted = repository.findAll().getFirst();
            // Keeping the generated id matters: it is referenced by the origin.hubId tag on every
            // recording already downloaded from this hub.
            assertEquals("uuid-1", adopted.hubId());
            assertEquals(EARLIER, adopted.createdAt());
            assertEquals(HubSource.CONFIG, adopted.source());
        }
    }

    /**
     * Records evictions instead of touching real gRPC channels. Subclassing rather than mocking
     * keeps the test free of any stubbing framework.
     */
    private static final class RecordingClientsFactory extends CachedHubClientsFactory {

        private final List<HubAddress> evicted = new ArrayList<>();

        private RecordingClientsFactory() {
            super(() -> {
                throw new UnsupportedOperationException("No temp directory is needed in this test");
            });
        }

        @Override
        public void evict(HubAddress address) {
            evicted.add(address);
        }
    }

    private static final class InMemoryHubsRepository implements HubsRepository {

        private final List<HubInfo> rows = new ArrayList<>();

        private int writes;

        private void seed(HubInfo hub) {
            rows.add(hub);
        }

        @Override
        public List<HubInfo> findAll() {
            return List.copyOf(rows);
        }

        @Override
        public Optional<HubInfo> find(String hubId) {
            return rows.stream().filter(row -> row.hubId().equals(hubId)).findFirst();
        }

        @Override
        public HubInfo create(HubInfo serverInfo) {
            writes++;
            rows.add(serverInfo);
            return serverInfo;
        }

        @Override
        public void update(HubInfo serverInfo) {
            writes++;
            rows.replaceAll(row -> row.hubId().equals(serverInfo.hubId()) ? serverInfo : row);
        }

        @Override
        public void delete(String hubId) {
            writes++;
            rows.removeIf(row -> row.hubId().equals(hubId));
        }
    }
}
