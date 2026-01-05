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
import pbouda.jeffrey.shared.common.model.workspace.WorkspaceInfo;
import pbouda.jeffrey.shared.common.model.workspace.WorkspaceType;
import pbouda.jeffrey.shared.persistence.client.DatabaseClientProvider;
import pbouda.jeffrey.test.DuckDBTest;
import pbouda.jeffrey.test.TestUtils;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

@DuckDBTest(migration = "classpath:db/migration/platform")
class JdbcWorkspacesRepositoryTest {

    @Nested
    class FindAllMethod {

        @Test
        void returnsEmptyList_whenNoWorkspaces(DataSource dataSource) {
            var provider = new DatabaseClientProvider(dataSource);
            JdbcWorkspacesRepository repository = new JdbcWorkspacesRepository(provider);

            List<WorkspaceInfo> result = repository.findAll();

            assertTrue(result.isEmpty());
        }

        @Test
        void returnsWorkspaces_whenWorkspacesExist(DataSource dataSource) throws SQLException {
            var provider = new DatabaseClientProvider(dataSource);
            TestUtils.executeSql(dataSource, "sql/workspaces/insert-multiple-workspaces.sql");
            JdbcWorkspacesRepository repository = new JdbcWorkspacesRepository(provider);

            List<WorkspaceInfo> result = repository.findAll();

            // Should return only non-deleted workspaces
            assertEquals(2, result.size());
            Set<String> names = result.stream().map(WorkspaceInfo::name).collect(Collectors.toSet());
            assertEquals(Set.of("Workspace One", "Workspace Two"), names);
        }

        @Test
        void excludesDeletedWorkspaces(DataSource dataSource) throws SQLException {
            var provider = new DatabaseClientProvider(dataSource);
            TestUtils.executeSql(dataSource, "sql/workspaces/insert-multiple-workspaces.sql");
            JdbcWorkspacesRepository repository = new JdbcWorkspacesRepository(provider);

            List<WorkspaceInfo> result = repository.findAll();

            Set<String> names = result.stream().map(WorkspaceInfo::name).collect(Collectors.toSet());
            assertFalse(names.contains("Deleted Workspace"));
        }
    }

    @Nested
    class FindMethod {

        @Test
        void returnsWorkspace_whenExists(DataSource dataSource) throws SQLException {
            var provider = new DatabaseClientProvider(dataSource);
            TestUtils.executeSql(dataSource, "sql/workspaces/insert-workspace.sql");
            JdbcWorkspacesRepository repository = new JdbcWorkspacesRepository(provider);

            Optional<WorkspaceInfo> result = repository.find("ws-001");

            assertTrue(result.isPresent());
            assertEquals("Test Workspace", result.get().name());
            assertEquals("A test workspace for testing", result.get().description());
            assertEquals(WorkspaceType.LIVE, result.get().type());
        }

        @Test
        void returnsEmpty_whenNotExists(DataSource dataSource) {
            var provider = new DatabaseClientProvider(dataSource);
            JdbcWorkspacesRepository repository = new JdbcWorkspacesRepository(provider);

            Optional<WorkspaceInfo> result = repository.find("non-existent-id");

            assertTrue(result.isEmpty());
        }

        @Test
        void returnsEmpty_whenWorkspaceIsDeleted(DataSource dataSource) throws SQLException {
            var provider = new DatabaseClientProvider(dataSource);
            TestUtils.executeSql(dataSource, "sql/workspaces/insert-multiple-workspaces.sql");
            JdbcWorkspacesRepository repository = new JdbcWorkspacesRepository(provider);

            Optional<WorkspaceInfo> result = repository.find("ws-003");

            assertTrue(result.isEmpty());
        }
    }

    @Nested
    class CreateMethod {

        @Test
        void insertsWorkspace_andReturnsWithGeneratedId(DataSource dataSource) {
            var provider = new DatabaseClientProvider(dataSource);
            JdbcWorkspacesRepository repository = new JdbcWorkspacesRepository(provider);
            WorkspaceInfo input = new WorkspaceInfo(
                    null, // ID will be generated
                    null,
                    null,
                    "New Workspace",
                    "A new workspace",
                    null,
                    null,
                    Instant.parse("2025-01-15T12:00:00Z"),
                    WorkspaceType.LIVE,
                    null,
                    0
            );

            WorkspaceInfo result = repository.create(input);

            assertNotNull(result.id());
            assertEquals("New Workspace", result.name());

            // Verify it was persisted
            Optional<WorkspaceInfo> found = repository.find(result.id());
            assertTrue(found.isPresent());
            assertEquals("New Workspace", found.get().name());
        }
    }

    @Nested
    class ExistsByNameMethod {

        @Test
        void returnsTrue_whenNameExists(DataSource dataSource) throws SQLException {
            var provider = new DatabaseClientProvider(dataSource);
            TestUtils.executeSql(dataSource, "sql/workspaces/insert-workspace.sql");
            JdbcWorkspacesRepository repository = new JdbcWorkspacesRepository(provider);

            boolean result = repository.existsByName("Test Workspace");

            assertTrue(result);
        }

        @Test
        void returnsFalse_whenNameNotExists(DataSource dataSource) {
            var provider = new DatabaseClientProvider(dataSource);
            JdbcWorkspacesRepository repository = new JdbcWorkspacesRepository(provider);

            boolean result = repository.existsByName("Non Existent");

            assertFalse(result);
        }

        @Test
        void returnsFalse_whenWorkspaceIsDeleted(DataSource dataSource) throws SQLException {
            var provider = new DatabaseClientProvider(dataSource);
            TestUtils.executeSql(dataSource, "sql/workspaces/insert-multiple-workspaces.sql");
            JdbcWorkspacesRepository repository = new JdbcWorkspacesRepository(provider);

            boolean result = repository.existsByName("Deleted Workspace");

            assertFalse(result);
        }
    }
}
