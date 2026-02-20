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
import pbouda.jeffrey.provider.platform.model.CreateProject;
import pbouda.jeffrey.shared.common.model.GraphVisualization;
import pbouda.jeffrey.shared.common.model.ProjectInfo;
import pbouda.jeffrey.shared.common.model.workspace.WorkspaceType;
import pbouda.jeffrey.shared.persistence.client.DatabaseClientProvider;
import pbouda.jeffrey.test.DuckDBTest;
import pbouda.jeffrey.test.TestUtils;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

@DuckDBTest(migration = "classpath:db/migration/platform")
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
                    null,
                    null, // namespace
                    "ws-001",
                    WorkspaceType.LIVE,
                    Instant.parse("2025-01-15T12:00:00Z"),
                    null,
                    Map.of()
            );
            CreateProject createProject = new CreateProject(projectInfo, new GraphVisualization(0.1));

            ProjectInfo result = repository.create(createProject);

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
                    null,
                    null, // namespace
                    "ws-001",
                    WorkspaceType.LIVE,
                    Instant.parse("2025-01-15T12:00:00Z"),
                    null,
                    Map.of()
            );
            CreateProject createProject = new CreateProject(projectInfo, new GraphVisualization(0.1));

            repository.create(createProject);

            // Should still have only 2 projects
            List<ProjectInfo> all = repository.findAllProjects();
            assertEquals(2, all.size());
        }
    }

    @Nested
    class FindAllNamespacesMethod {

        @Test
        void returnsDistinctNamespaces_excludingNull(DataSource dataSource) throws SQLException {
            var provider = new DatabaseClientProvider(dataSource);
            TestUtils.executeSql(dataSource, "sql/projects/insert-projects-with-namespaces.sql");
            JdbcProjectsRepository repository = new JdbcProjectsRepository(provider);

            List<String> result = repository.findAllNamespaces();

            assertEquals(2, result.size());
            assertEquals(List.of("backend", "frontend"), result);
        }

        @Test
        void returnsEmpty_whenAllNamespacesAreNull(DataSource dataSource) throws SQLException {
            var provider = new DatabaseClientProvider(dataSource);
            TestUtils.executeSql(dataSource, "sql/projects/insert-workspace-with-projects.sql");
            JdbcProjectsRepository repository = new JdbcProjectsRepository(provider);

            // Both projects in this fixture have NULL namespace
            List<String> result = repository.findAllNamespaces();

            assertTrue(result.isEmpty());
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
