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

package cafe.jeffrey.server.persistence.jdbc;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import cafe.jeffrey.shared.common.model.ProjectInfo;
import cafe.jeffrey.shared.persistence.client.DatabaseClientProvider;
import cafe.jeffrey.test.DuckDBTest;
import cafe.jeffrey.test.TestUtils;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DuckDBTest(migration = "classpath:db/migration/server")
class JdbcProjectRepositoryTest {

    @Nested
    class FindMethod {

        @Test
        void returnsProject_whenExists(DataSource dataSource) throws SQLException {
            var provider = new DatabaseClientProvider(dataSource);
            TestUtils.executeSql(dataSource, "sql/projects/insert-workspace-with-projects.sql");
            JdbcProjectRepository repository = new JdbcProjectRepository("proj-001", provider);

            Optional<ProjectInfo> result = repository.find();

            assertTrue(result.isPresent());
            assertEquals("Project One", result.get().name());
        }

        @Test
        void returnsEmpty_whenNotExists(DataSource dataSource) {
            var provider = new DatabaseClientProvider(dataSource);
            JdbcProjectRepository repository = new JdbcProjectRepository("non-existent", provider);

            Optional<ProjectInfo> result = repository.find();

            assertTrue(result.isEmpty());
        }
    }

    @Nested
    class UpdateProjectNameMethod {

        @Test
        void updatesName(DataSource dataSource) throws SQLException {
            var provider = new DatabaseClientProvider(dataSource);
            TestUtils.executeSql(dataSource, "sql/projects/insert-workspace-with-projects.sql");
            JdbcProjectRepository repository = new JdbcProjectRepository("proj-001", provider);

            repository.updateProjectName("Updated Name");

            Optional<ProjectInfo> result = repository.find();
            assertTrue(result.isPresent());
            assertEquals("Updated Name", result.get().name());
        }
    }

    @Nested
    class DeleteMethod {

        @Test
        void softDeletesProject_andHardDeletesRelatedData(DataSource dataSource) throws SQLException {
            var provider = new DatabaseClientProvider(dataSource);
            TestUtils.executeSql(dataSource, "sql/project/insert-project-with-profiles.sql");
            JdbcProjectRepository repository = new JdbcProjectRepository("proj-001", provider);

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

            // Related data should be hard-deleted
            try (Connection conn = dataSource.getConnection();
                 Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(
                         "SELECT COUNT(*) FROM profiler_settings WHERE project_id = 'proj-001'")) {
                rs.next();
                assertEquals(0, rs.getInt(1), "Profiler settings should be hard-deleted");
            }
        }
    }
}
