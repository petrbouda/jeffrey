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

package cafe.jeffrey.microscope.persistence.jdbc;

import cafe.jeffrey.microscope.model.hub.HubAddress;
import cafe.jeffrey.microscope.model.hub.HubInfo;
import cafe.jeffrey.microscope.model.hub.HubSource;
import cafe.jeffrey.shared.persistence.client.DatabaseClientProvider;
import cafe.jeffrey.test.DuckDBTest;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DuckDBTest(migration = "classpath:db/migration/microscope/core")
class JdbcHubsRepositoryTest {

    private static final Instant CREATED_AT = Instant.parse("2026-01-01T00:00:00Z");

    private static JdbcHubsRepository repository(DataSource dataSource) {
        return new JdbcHubsRepository(new DatabaseClientProvider(dataSource));
    }

    private static HubInfo hub(String hubId, String name, String hostname, int port, HubSource source) {
        return new HubInfo(hubId, name, new HubAddress(hostname, port), CREATED_AT, source);
    }

    @Nested
    class Create {

        @Test
        void roundTripsAConfiguredHub(DataSource dataSource) {
            JdbcHubsRepository repository = repository(dataSource);

            repository.create(hub("cfg-prod", "Production", "hub.example.com", 443, HubSource.CONFIG));

            Optional<HubInfo> found = repository.find("cfg-prod");
            assertTrue(found.isPresent());
            assertEquals("Production", found.get().name());
            assertEquals("hub.example.com", found.get().address().hostname());
            assertEquals(443, found.get().address().port());
            assertEquals(HubSource.CONFIG, found.get().source());
        }

        @Test
        void roundTripsAUserAddedHub(DataSource dataSource) {
            JdbcHubsRepository repository = repository(dataSource);

            repository.create(hub("uuid-1", "Mine", "mine.example.com", 9090, HubSource.USER));

            assertEquals(HubSource.USER, repository.find("uuid-1").orElseThrow().source());
        }

        @Test
        void plaintextSurvivesTheRoundTrip(DataSource dataSource) {
            JdbcHubsRepository repository = repository(dataSource);
            HubInfo plaintext = new HubInfo(
                    "cfg-lan", "LAN", new HubAddress("lan.internal", 9090, true), CREATED_AT, HubSource.CONFIG);

            repository.create(plaintext);

            assertTrue(repository.find("cfg-lan").orElseThrow().address().plaintext());
        }
    }

    @Nested
    class Update {

        @Test
        void changesNameAndAddressButKeepsTheCreationTime(DataSource dataSource) {
            JdbcHubsRepository repository = repository(dataSource);
            repository.create(hub("cfg-prod", "Production", "hub.example.com", 443, HubSource.CONFIG));

            repository.update(new HubInfo(
                    "cfg-prod",
                    "Renamed",
                    new HubAddress("moved.example.com", 8443),
                    CREATED_AT,
                    HubSource.CONFIG));

            HubInfo updated = repository.find("cfg-prod").orElseThrow();
            assertEquals("Renamed", updated.name());
            assertEquals("moved.example.com", updated.address().hostname());
            assertEquals(8443, updated.address().port());
            // The id and creation time are what the origin.hubId recording tags hang off.
            assertEquals("cfg-prod", updated.hubId());
            assertEquals(CREATED_AT, updated.createdAt());
        }

        @Test
        void promotesAUserAddedHubToConfigurationOwned(DataSource dataSource) {
            JdbcHubsRepository repository = repository(dataSource);
            repository.create(hub("uuid-1", "Mine", "mine.example.com", 9090, HubSource.USER));

            repository.update(hub("uuid-1", "Adopted", "mine.example.com", 9090, HubSource.CONFIG));

            assertEquals(HubSource.CONFIG, repository.find("uuid-1").orElseThrow().source());
        }

        @Test
        void leavesOtherRowsAlone(DataSource dataSource) {
            JdbcHubsRepository repository = repository(dataSource);
            repository.create(hub("cfg-prod", "Production", "hub.example.com", 443, HubSource.CONFIG));
            repository.create(hub("uuid-1", "Mine", "mine.example.com", 9090, HubSource.USER));

            repository.update(hub("cfg-prod", "Renamed", "hub.example.com", 443, HubSource.CONFIG));

            HubInfo untouched = repository.find("uuid-1").orElseThrow();
            assertEquals("Mine", untouched.name());
            assertEquals(HubSource.USER, untouched.source());
        }
    }

    @Nested
    class Delete {

        @Test
        void removesOnlyTheNamedRow(DataSource dataSource) {
            JdbcHubsRepository repository = repository(dataSource);
            repository.create(hub("cfg-prod", "Production", "hub.example.com", 443, HubSource.CONFIG));
            repository.create(hub("uuid-1", "Mine", "mine.example.com", 9090, HubSource.USER));

            repository.delete("cfg-prod");

            List<HubInfo> remaining = repository.findAll();
            assertEquals(1, remaining.size());
            assertEquals("uuid-1", remaining.getFirst().hubId());
        }
    }
}
