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

import cafe.jeffrey.microscope.persistence.api.*;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import cafe.jeffrey.microscope.model.ProfileInfo;
import cafe.jeffrey.microscope.model.RecordingEventSource;
import cafe.jeffrey.shared.persistence.client.DatabaseClientProvider;
import cafe.jeffrey.test.DuckDBTest;
import cafe.jeffrey.test.TestUtils;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DuckDBTest(migration = "classpath:db/migration/microscope/core")
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
            TestUtils.executeSql(dataSource, "sql/recording/insert-recordings.sql");
            JdbcProfileRepository repository = new JdbcProfileRepository("new-profile-001", provider);

            ProfileRepository.InsertProfile insertProfile = new ProfileRepository.InsertProfile(
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
