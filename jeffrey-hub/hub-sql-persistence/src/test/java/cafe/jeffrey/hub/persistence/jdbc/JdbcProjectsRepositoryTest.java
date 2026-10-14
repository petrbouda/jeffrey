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
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

@DuckDBTest(migration = "classpath:db/migration/hub")
class JdbcProjectsRepositoryTest {

    @Nested
    class FindAllProjectsMethod {

        @Test
        void returnsEmptyList_whenNoProjects(DataSource dataSource) {
            var provider = new DatabaseClientProvider(dataSource);
            JdbcProjectsRepository repository = new JdbcProjectsRepository(provider);

            List<ProjectInfo> result = repository.findAllProjects();

            assertTrue(result.isEmpty());
        }

        @Test
        void returnsAllProjects_whenProjectsExist(DataSource dataSource) throws SQLException {
            var provider = new DatabaseClientProvider(dataSource);
            TestUtils.executeSql(dataSource, "sql/projects/insert-workspace-with-projects.sql");
            JdbcProjectsRepository repository = new JdbcProjectsRepository(provider);

            List<ProjectInfo> result = repository.findAllProjects();

            assertEquals(2, result.size());
            Set<String> names = result.stream().map(ProjectInfo::name).collect(Collectors.toSet());
            assertEquals(Set.of("Project One", "Project Two"), names);
        }
    }

    @Nested
    class FindAllProjectsByWorkspaceMethod {

        @Test
        void returnsEmptyList_whenNoProjectsInWorkspace(DataSource dataSource) throws SQLException {
            var provider = new DatabaseClientProvider(dataSource);
            // Insert workspace without projects
            TestUtils.executeSql(dataSource, "sql/workspaces/insert-workspace.sql");
            JdbcProjectsRepository repository = new JdbcProjectsRepository(provider);

            List<ProjectInfo> result = repository.findAllProjects("ws-001");

            assertTrue(result.isEmpty());
        }

        @Test
        void returnsProjectsForWorkspace_whenProjectsExist(DataSource dataSource) throws SQLException {
            var provider = new DatabaseClientProvider(dataSource);
            TestUtils.executeSql(dataSource, "sql/projects/insert-multiple-workspaces-with-projects.sql");
            JdbcProjectsRepository repository = new JdbcProjectsRepository(provider);

            List<ProjectInfo> result = repository.findAllProjects("ws-001");

            assertEquals(2, result.size());
            Set<String> names = result.stream().map(ProjectInfo::name).collect(Collectors.toSet());
            assertEquals(Set.of("Project A in WS1", "Project B in WS1"), names);
        }

        @Test
        void filtersCorrectly_byWorkspaceId(DataSource dataSource) throws SQLException {
            var provider = new DatabaseClientProvider(dataSource);
            TestUtils.executeSql(dataSource, "sql/projects/insert-multiple-workspaces-with-projects.sql");
            JdbcProjectsRepository repository = new JdbcProjectsRepository(provider);

            List<ProjectInfo> result = repository.findAllProjects("ws-002");

            assertEquals(1, result.size());
            assertEquals("Project C in WS2", result.getFirst().name());
        }
    }

    @Nested
    class CreateMethod {

        @Test
        void insertsProject_andReturnsProjectInfo(DataSource dataSource) throws SQLException {
            var provider = new DatabaseClientProvider(dataSource);
            // Need to insert workspace first
            TestUtils.executeSql(dataSource, "sql/workspaces/insert-workspace.sql");
            JdbcProjectsRepository repository = new JdbcProjectsRepository(provider);

            ProjectInfo projectInfo = new ProjectInfo(
                    "new-proj-001",
                    null,
                    "New Project",
                    "ws-001",
                    Instant.parse("2025-01-15T12:00:00Z"),
                    null,
                    Map.of());

            ProjectInfo result = repository.create(projectInfo);

            assertEquals("new-proj-001", result.id());
            assertEquals("New Project", result.name());

            // Verify it was persisted
            List<ProjectInfo> all = repository.findAllProjects("ws-001");
            assertEquals(1, all.size());
            assertEquals("New Project", all.getFirst().name());
        }

        @Test
        void doesNotInsertDuplicate_whenOriginIdExists(DataSource dataSource) throws SQLException {
            var provider = new DatabaseClientProvider(dataSource);
            TestUtils.executeSql(dataSource, "sql/projects/insert-workspace-with-projects.sql");
            JdbcProjectsRepository repository = new JdbcProjectsRepository(provider);

            // Try to insert project with same origin_project_id as existing "origin-002"
            ProjectInfo projectInfo = new ProjectInfo(
                    "duplicate-proj",
                    "origin-002",  // Same origin_project_id as Project Two
                    "Duplicate Project",
                    "ws-001",
                    Instant.parse("2025-01-15T12:00:00Z"),
                    null,
                    Map.of());

            repository.create(projectInfo);

            // Should still have only 2 projects
            List<ProjectInfo> all = repository.findAllProjects();
            assertEquals(2, all.size());
        }
    }

    @Nested
    class FindByOriginProjectIdMethod {

        @Test
        void returnsProject_whenOriginIdExists(DataSource dataSource) throws SQLException {
            var provider = new DatabaseClientProvider(dataSource);
            TestUtils.executeSql(dataSource, "sql/projects/insert-workspace-with-projects.sql");
            JdbcProjectsRepository repository = new JdbcProjectsRepository(provider);

            Optional<ProjectInfo> result = repository.findByOriginProjectId("origin-002");

            assertTrue(result.isPresent());
            assertEquals("Project Two", result.get().name());
            assertEquals("proj-002", result.get().id());
        }

        @Test
        void returnsEmpty_whenOriginIdNotExists(DataSource dataSource) throws SQLException {
            var provider = new DatabaseClientProvider(dataSource);
            TestUtils.executeSql(dataSource, "sql/projects/insert-workspace-with-projects.sql");
            JdbcProjectsRepository repository = new JdbcProjectsRepository(provider);

            Optional<ProjectInfo> result = repository.findByOriginProjectId("non-existent-origin");

            assertTrue(result.isEmpty());
        }

        @Test
        void returnsEmpty_whenNoProjects(DataSource dataSource) {
            var provider = new DatabaseClientProvider(dataSource);
            JdbcProjectsRepository repository = new JdbcProjectsRepository(provider);

            Optional<ProjectInfo> result = repository.findByOriginProjectId("any-origin");

            assertTrue(result.isEmpty());
        }
    }
}
