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

package cafe.jeffrey.hub.persistence.jdbc;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import cafe.jeffrey.hub.model.ProjectInfo;
import cafe.jeffrey.shared.persistence.client.DatabaseClientProvider;
import cafe.jeffrey.test.DuckDBTest;
import cafe.jeffrey.test.TestUtils;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DuckDBTest(migration = "classpath:db/migration/hub")
class JdbcProjectRepositoryTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-01-15T10:00:00Z"), ZoneOffset.UTC);

    @Nested
    class FindMethod {

        @Test
        void returnsProject_whenExists(DataSource dataSource) throws SQLException {
            var provider = new DatabaseClientProvider(dataSource);
            TestUtils.executeSql(dataSource, "sql/projects/insert-workspace-with-projects.sql");
            JdbcProjectRepository repository = new JdbcProjectRepository(FIXED_CLOCK, "proj-001", provider);

            Optional<ProjectInfo> result = repository.find();

            assertTrue(result.isPresent());
            assertEquals("Project One", result.get().name());
        }

        @Test
        void returnsEmpty_whenNotExists(DataSource dataSource) {
            var provider = new DatabaseClientProvider(dataSource);
            JdbcProjectRepository repository = new JdbcProjectRepository(FIXED_CLOCK, "non-existent", provider);

            Optional<ProjectInfo> result = repository.find();

            assertTrue(result.isEmpty());
        }
    }

    @Nested
    class FindIncludingDeletedMethod {

        @Test
        void returnsSoftDeletedProject(DataSource dataSource) throws SQLException {
            var provider = new DatabaseClientProvider(dataSource);
            TestUtils.executeSql(dataSource, "sql/project/insert-project-with-profiles.sql");
            JdbcProjectRepository repository = new JdbcProjectRepository(FIXED_CLOCK, "proj-001", provider);

            repository.delete();

            // Active-only find() filters the soft-deleted row out, the deleted-inclusive one must not
            assertTrue(repository.find().isEmpty());
            Optional<ProjectInfo> result = repository.findIncludingDeleted();
            assertTrue(result.isPresent());
            assertNotNull(result.get().deletedAt(), "deletedAt should be populated for a soft-deleted project");
        }

        @Test
        void returnsEmpty_whenNotExists(DataSource dataSource) {
            var provider = new DatabaseClientProvider(dataSource);
            JdbcProjectRepository repository = new JdbcProjectRepository(FIXED_CLOCK, "non-existent", provider);

            assertTrue(repository.findIncludingDeleted().isEmpty());
        }
    }

    @Nested
    class RestoreMethod {

        @Test
        void restoresSoftDeletedProject(DataSource dataSource) throws SQLException {
            var provider = new DatabaseClientProvider(dataSource);
            TestUtils.executeSql(dataSource, "sql/project/insert-project-with-profiles.sql");
            JdbcProjectRepository repository = new JdbcProjectRepository(FIXED_CLOCK, "proj-001", provider);

            repository.delete();
            assertTrue(repository.find().isEmpty());

            repository.restore();

            Optional<ProjectInfo> result = repository.find();
            assertTrue(result.isPresent(), "Restored project should be visible via the active-only find()");
            assertNull(result.get().deletedAt(), "deletedAt should be cleared after restore");
        }
    }

    @Nested
    class DeleteMethod {

        @Test
        void softDeletesProject_andHardDeletesRelatedData(DataSource dataSource) throws SQLException {
            var provider = new DatabaseClientProvider(dataSource);
            TestUtils.executeSql(dataSource, "sql/project/insert-project-with-profiles.sql");
            JdbcProjectRepository repository = new JdbcProjectRepository(FIXED_CLOCK, "proj-001", provider);

            repository.delete();

            // Project not visible via find() (filtered by deleted_at IS NULL)
            assertTrue(repository.find().isEmpty());

            // But the row still exists in the database with deleted_at set
            try (Connection conn = dataSource.getConnection();
                 Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(
                         "SELECT deleted_at FROM projects WHERE project_id = 'proj-001'")) {
                assertTrue(rs.next(), "Soft-deleted project row should still exist");
                assertNotNull(rs.getTimestamp("deleted_at"), "deleted_at should be set");
            }
        }
    }
}
