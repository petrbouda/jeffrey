/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
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

package pbouda.jeffrey.provider.platform.repository;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import pbouda.jeffrey.shared.common.model.ProfileInfo;
import pbouda.jeffrey.shared.common.model.RecordingEventSource;
import pbouda.jeffrey.shared.persistence.client.DatabaseClientProvider;
import pbouda.jeffrey.test.DuckDBTest;
import pbouda.jeffrey.test.TestUtils;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DuckDBTest(migration = "classpath:db/migration/platform")
class JdbcProfileRepositoryTest {

    @Nested
    class FindMethod {

        @Test
        void returnsProfile_whenExists(DataSource dataSource) throws SQLException {
            var provider = new DatabaseClientProvider(dataSource);
            TestUtils.executeSql(dataSource, "sql/project/insert-project-with-profiles.sql");
            JdbcProfileRepository repository = new JdbcProfileRepository("profile-001", provider);

            Optional<ProfileInfo> result = repository.find();

            assertTrue(result.isPresent());
            assertEquals("Profile One", result.get().name());
        }

        @Test
        void returnsEmpty_whenNotExists(DataSource dataSource) {
            var provider = new DatabaseClientProvider(dataSource);
            JdbcProfileRepository repository = new JdbcProfileRepository("non-existent", provider);

            Optional<ProfileInfo> result = repository.find();

            assertTrue(result.isEmpty());
        }
    }

    @Nested
    class InsertMethod {

        @Test
        void insertsProfile(DataSource dataSource) throws SQLException {
            var provider = new DatabaseClientProvider(dataSource);
            TestUtils.executeSql(dataSource, "sql/recording/insert-project-with-recordings.sql");
            JdbcProfileRepository repository = new JdbcProfileRepository("new-profile-001", provider);

            ProfileRepository.InsertProfile insertProfile = new ProfileRepository.InsertProfile(
                    "proj-001",
                    "New Profile",
                    RecordingEventSource.JDK,
                    Instant.parse("2025-01-15T12:00:00Z"),
                    "rec-001",
                    Instant.parse("2025-01-15T11:00:00Z"),
                    Instant.parse("2025-01-15T11:30:00Z")
            );

            repository.insert(insertProfile);

            Optional<ProfileInfo> result = repository.find();
            assertTrue(result.isPresent());
            assertEquals("New Profile", result.get().name());
            assertFalse(result.get().enabled()); // newly created profile is not enabled
        }
    }

    @Nested
    class UpdateMethod {

        @Test
        void updatesName(DataSource dataSource) throws SQLException {
            var provider = new DatabaseClientProvider(dataSource);
            TestUtils.executeSql(dataSource, "sql/project/insert-project-with-profiles.sql");
            JdbcProfileRepository repository = new JdbcProfileRepository("profile-001", provider);

            ProfileInfo result = repository.update("Updated Profile Name");

            assertEquals("Updated Profile Name", result.name());
        }
    }

    @Nested
    class EnableProfileMethod {

        @Test
        void enablesProfile(DataSource dataSource) throws SQLException {
            var provider = new DatabaseClientProvider(dataSource);
            TestUtils.executeSql(dataSource, "sql/project/insert-project-with-profiles.sql");
            JdbcProfileRepository repository = new JdbcProfileRepository("profile-002", provider);

            // Profile-002 has enabled_at = NULL initially
            repository.enableProfile(Instant.parse("2025-01-15T12:00:00Z"));

            Optional<ProfileInfo> result = repository.find();
            assertTrue(result.isPresent());
            assertTrue(result.get().enabled());
        }
    }

    @Nested
    class DeleteMethod {

        @Test
        void deletesProfile(DataSource dataSource) throws SQLException {
            var provider = new DatabaseClientProvider(dataSource);
            TestUtils.executeSql(dataSource, "sql/project/insert-project-with-profiles.sql");
            JdbcProfileRepository repository = new JdbcProfileRepository("profile-001", provider);

            // Verify profile exists before deletion
            assertTrue(repository.find().isPresent());

            repository.delete();

            // Verify profile no longer exists
            assertTrue(repository.find().isEmpty());
        }

        @Test
        void deletesNothingWhenProfileNotExists(DataSource dataSource) throws SQLException {
            var provider = new DatabaseClientProvider(dataSource);
            TestUtils.executeSql(dataSource, "sql/project/insert-project-with-profiles.sql");
            JdbcProfileRepository repository = new JdbcProfileRepository("non-existent", provider);

            // Should not throw, just delete 0 rows
            repository.delete();

            // Other profiles should still exist
            JdbcProfileRepository existingRepo = new JdbcProfileRepository("profile-001", provider);
            assertTrue(existingRepo.find().isPresent());
        }
    }
}
