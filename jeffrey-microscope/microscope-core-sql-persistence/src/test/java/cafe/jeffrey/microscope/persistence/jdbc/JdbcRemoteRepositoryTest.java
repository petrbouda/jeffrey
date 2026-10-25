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

import cafe.jeffrey.microscope.model.remote.AzureFilesAddress;
import cafe.jeffrey.microscope.model.remote.AzureFilesAuth;
import cafe.jeffrey.microscope.model.remote.AzureFilesProtocol;
import cafe.jeffrey.microscope.model.remote.HubAddress;
import cafe.jeffrey.microscope.model.remote.RemoteInfo;
import cafe.jeffrey.microscope.model.remote.RemoteSource;
import cafe.jeffrey.shared.persistence.client.DatabaseClientProvider;
import cafe.jeffrey.test.DuckDBTest;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DuckDBTest(migration = "classpath:db/migration/microscope/core")
class JdbcRemoteRepositoryTest {

    private static final Instant CREATED_AT = Instant.parse("2026-01-01T00:00:00Z");

    private static final AzureFilesAddress SHARE = new AzureFilesAddress(
            "https://jeffreyprod.file.core.windows.net", "recordings", "volume/workspaces",
            AzureFilesProtocol.NFS, AzureFilesAuth.ENTRA, true);

    private static JdbcRemoteRepository repository(DataSource dataSource) {
        return new JdbcRemoteRepository(new DatabaseClientProvider(dataSource));
    }

    private static RemoteInfo hub(String name, String hostname, int port, RemoteSource source) {
        return new RemoteInfo(name, new HubAddress(hostname, port), CREATED_AT, source);
    }

    @Test
    void nothingIsConnectedUntilARemoteIsSaved(DataSource dataSource) {
        assertEquals(Optional.empty(), repository(dataSource).find());
    }

    @Nested
    class Saving {

        @Test
        void roundTripsAConfiguredJeffreyHub(DataSource dataSource) {
            JdbcRemoteRepository repository = repository(dataSource);

            repository.save(hub("Production", "hub.example.com", 443, RemoteSource.CONFIG));

            RemoteInfo found = repository.find().orElseThrow();
            assertEquals("Production", found.name());
            assertEquals(new HubAddress("hub.example.com", 443), found.address());
            assertEquals(RemoteSource.CONFIG, found.source());
            assertEquals(CREATED_AT, found.createdAt());
        }

        @Test
        void plaintextSurvivesTheRoundTrip(DataSource dataSource) {
            JdbcRemoteRepository repository = repository(dataSource);
            RemoteInfo plaintext = new RemoteInfo(
                    "In cluster", new HubAddress("jeffrey-hub", 9090, true), CREATED_AT, RemoteSource.USER);

            repository.save(plaintext);

            assertEquals(plaintext, repository.find().orElseThrow());
        }

        /** The share, its directory, how it signs in and whether it may be changed; never a token. */
        @Test
        void roundTripsAnAzureFileShare(DataSource dataSource) {
            JdbcRemoteRepository repository = repository(dataSource);
            RemoteInfo share = new RemoteInfo("Azure prod", SHARE, CREATED_AT, RemoteSource.CONFIG);

            repository.save(share);

            assertEquals(share, repository.find().orElseThrow());
        }

        /** There is one remote: connecting another replaces it, whichever kind either is. */
        @Test
        void replacesWhatWasConnected(DataSource dataSource) {
            JdbcRemoteRepository repository = repository(dataSource);
            repository.save(new RemoteInfo("Azure prod", SHARE, CREATED_AT, RemoteSource.CONFIG));

            RemoteInfo hub = hub("Mine", "mine.example.com", 9090, RemoteSource.USER);
            repository.save(hub);

            assertEquals(hub, repository.find().orElseThrow());
        }
    }

    @Test
    void deletingForgetsTheRemote(DataSource dataSource) {
        JdbcRemoteRepository repository = repository(dataSource);
        repository.save(hub("Production", "hub.example.com", 443, RemoteSource.CONFIG));

        repository.delete();

        assertTrue(repository.find().isEmpty());
    }
}
