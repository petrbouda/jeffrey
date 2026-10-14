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
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DuckDBTest(migration = "classpath:db/migration/hub")
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
    class DeleteMethod {

        @Test
        void deletesProjectRowAndRelatedData(DataSource dataSource) throws SQLException {
            var provider = new DatabaseClientProvider(dataSource);
            TestUtils.executeSql(dataSource, "sql/project/insert-project-with-profiles.sql");
            JdbcProjectRepository repository = new JdbcProjectRepository("proj-001", provider);

            repository.delete();

            assertTrue(repository.find().isEmpty());
            assertEquals(0, countRows(dataSource, "SELECT COUNT(*) FROM projects WHERE project_id = 'proj-001'"));
            assertEquals(0, countRows(dataSource, "SELECT COUNT(*) FROM repositories WHERE project_id = 'proj-001'"));
            assertEquals(0, countRows(dataSource, "SELECT COUNT(*) FROM project_instances WHERE project_id = 'proj-001'"));
        }

        private int countRows(DataSource dataSource, String sql) throws SQLException {
            try (Connection conn = dataSource.getConnection();
                 Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(sql)) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }
}
