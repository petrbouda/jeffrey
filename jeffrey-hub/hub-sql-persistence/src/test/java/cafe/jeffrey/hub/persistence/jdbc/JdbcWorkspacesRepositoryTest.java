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
import cafe.jeffrey.hub.model.workspace.WorkspaceInfo;
import cafe.jeffrey.shared.persistence.client.DatabaseClientProvider;
import cafe.jeffrey.test.DuckDBTest;
import cafe.jeffrey.test.TestUtils;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

@DuckDBTest(migration = "classpath:db/migration/hub")
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

            assertEquals(2, result.size());
            Set<String> names = result.stream().map(WorkspaceInfo::name).collect(Collectors.toSet());
            assertEquals(Set.of("Workspace One", "Workspace Two"), names);
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
            assertEquals("origin-ws-001", result.get().referenceId());
        }

        @Test
        void returnsEmpty_whenNotExists(DataSource dataSource) {
            var provider = new DatabaseClientProvider(dataSource);
            JdbcWorkspacesRepository repository = new JdbcWorkspacesRepository(provider);

            Optional<WorkspaceInfo> result = repository.find("non-existent-id");

            assertTrue(result.isEmpty());
        }
    }

    @Nested
    class FindByReferenceIdMethod {

        @Test
        void returnsWorkspace_whenReferenceIdExists(DataSource dataSource) throws SQLException {
            var provider = new DatabaseClientProvider(dataSource);
            TestUtils.executeSql(dataSource, "sql/workspaces/insert-workspace.sql");
            JdbcWorkspacesRepository repository = new JdbcWorkspacesRepository(provider);

            Optional<WorkspaceInfo> result = repository.findByReferenceId("origin-ws-001");

            assertTrue(result.isPresent());
            assertEquals("Test Workspace", result.get().name());
            assertEquals("origin-ws-001", result.get().referenceId());
        }

        @Test
        void returnsEmpty_whenReferenceIdNotExists(DataSource dataSource) {
            var provider = new DatabaseClientProvider(dataSource);
            JdbcWorkspacesRepository repository = new JdbcWorkspacesRepository(provider);

            Optional<WorkspaceInfo> result = repository.findByReferenceId("non-existent-origin");

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
                    "new-workspace-ref",
                    null,
                    "New Workspace",
                    null,
                    null,
                    Instant.parse("2025-01-15T12:00:00Z"),
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
    }
}
