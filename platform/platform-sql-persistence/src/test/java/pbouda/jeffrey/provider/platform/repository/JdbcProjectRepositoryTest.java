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
import pbouda.jeffrey.shared.common.model.ProjectInfo;
import pbouda.jeffrey.shared.persistence.client.DatabaseClientProvider;
import pbouda.jeffrey.test.DuckDBTest;
import pbouda.jeffrey.test.TestUtils;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DuckDBTest(migration = "classpath:db/migration/platform")
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
    class FindAllProfilesMethod {

        @Test
        void returnsEmptyList_whenNoProfiles(DataSource dataSource) throws SQLException {
            var provider = new DatabaseClientProvider(dataSource);
            TestUtils.executeSql(dataSource, "sql/projects/insert-workspace-with-projects.sql");
            JdbcProjectRepository repository = new JdbcProjectRepository("proj-001", provider);

            List<ProfileInfo> result = repository.findAllProfiles();

            assertTrue(result.isEmpty());
        }

        @Test
        void returnsProfiles_whenProfilesExist(DataSource dataSource) throws SQLException {
            var provider = new DatabaseClientProvider(dataSource);
            TestUtils.executeSql(dataSource, "sql/project/insert-project-with-profiles.sql");
            JdbcProjectRepository repository = new JdbcProjectRepository("proj-001", provider);

            List<ProfileInfo> result = repository.findAllProfiles();

            assertEquals(2, result.size());
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
        void deletesProject_andRelatedData(DataSource dataSource) throws SQLException {
            var provider = new DatabaseClientProvider(dataSource);
            TestUtils.executeSql(dataSource, "sql/project/insert-project-with-profiles.sql");
            JdbcProjectRepository repository = new JdbcProjectRepository("proj-001", provider);

            repository.delete();

            Optional<ProjectInfo> result = repository.find();
            assertTrue(result.isEmpty());
        }
    }
}
