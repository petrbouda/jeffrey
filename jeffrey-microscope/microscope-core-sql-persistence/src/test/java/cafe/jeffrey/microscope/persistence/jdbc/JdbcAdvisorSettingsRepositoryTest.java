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

package cafe.jeffrey.microscope.persistence.jdbc;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import cafe.jeffrey.microscope.persistence.api.AdvisorSettingsRow;
import cafe.jeffrey.shared.persistence.client.DatabaseClientProvider;
import cafe.jeffrey.test.DuckDBTest;

import javax.sql.DataSource;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DuckDBTest(migration = "classpath:db/migration/microscope/core")
class JdbcAdvisorSettingsRepositoryTest {

    private static final String PROFILE_ID = "019fb9d3-1da5-7794-b950-ccc7d236db11";
    private static final Instant MODIFIED_AT = Instant.parse("2026-07-31T20:00:00Z");

    @Nested
    class Upsert {

        // The whole point of keying by profile_id: a locally-uploaded profile has no project, and must
        // still be able to store a source folder.
        @Test
        void storesAndReadsBackASourceFolderForAProjectlessProfile(DataSource dataSource) {
            JdbcAdvisorSettingsRepository repository = createRepository(dataSource);

            repository.upsert(new AdvisorSettingsRow(PROFILE_ID, "/home/me/order-service", MODIFIED_AT));

            Optional<AdvisorSettingsRow> result = repository.find(PROFILE_ID);
            assertTrue(result.isPresent());
            assertEquals("/home/me/order-service", result.get().sourcePath());
            assertEquals(PROFILE_ID, result.get().profileId());
        }

        @Test
        void updatesTheSourceFolderOnConflict(DataSource dataSource) {
            JdbcAdvisorSettingsRepository repository = createRepository(dataSource);

            repository.upsert(new AdvisorSettingsRow(PROFILE_ID, "/home/me/old", MODIFIED_AT));
            repository.upsert(new AdvisorSettingsRow(PROFILE_ID, "/home/me/new", MODIFIED_AT));

            Optional<AdvisorSettingsRow> result = repository.find(PROFILE_ID);
            assertTrue(result.isPresent());
            assertEquals("/home/me/new", result.get().sourcePath());
        }
    }

    @Nested
    class Find {

        @Test
        void returnsEmptyForAnUnconfiguredProfile(DataSource dataSource) {
            JdbcAdvisorSettingsRepository repository = createRepository(dataSource);

            assertTrue(repository.find("no-such-profile").isEmpty());
        }
    }

    private static JdbcAdvisorSettingsRepository createRepository(DataSource dataSource) {
        return new JdbcAdvisorSettingsRepository(new DatabaseClientProvider(dataSource));
    }
}
