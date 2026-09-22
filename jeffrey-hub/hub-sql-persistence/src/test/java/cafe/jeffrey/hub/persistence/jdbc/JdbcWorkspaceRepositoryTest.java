/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
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

package cafe.jeffrey.hub.persistence.jdbc;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import cafe.jeffrey.hub.model.config.ScopedConfigEntry;
import cafe.jeffrey.hub.model.config.ScopedConfigKey;
import cafe.jeffrey.shared.common.config.ConfigType;
import cafe.jeffrey.hub.model.ProjectInfo;
import cafe.jeffrey.hub.model.workspace.WorkspaceInfo;
import cafe.jeffrey.shared.persistence.client.DatabaseClientProvider;
import cafe.jeffrey.test.DuckDBTest;
import cafe.jeffrey.test.TestUtils;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DuckDBTest(migration = "classpath:db/migration/hub")
class JdbcWorkspaceRepositoryTest {

    private static final Instant FIXED_INSTANT = Instant.parse("2025-01-01T12:00:00Z");

    @Nested
    class DeleteMethod {

        @Test
        void deletesWorkspace_whenExists(DataSource dataSource) throws SQLException {
            var provider = new DatabaseClientProvider(dataSource);
            TestUtils.executeSql(dataSource, "sql/workspaces/insert-workspace.sql");
            JdbcWorkspaceRepository repository = new JdbcWorkspaceRepository("ws-001", provider);

            repository.delete();

            // Verify workspace is gone
            JdbcWorkspacesRepository workspacesRepo = new JdbcWorkspacesRepository(provider);
            Optional<WorkspaceInfo> result = workspacesRepo.find("ws-001");
            assertTrue(result.isEmpty());
        }

        @Test
        void cascadesToAllChildRows_andKeepsOtherWorkspacesIntact(DataSource dataSource) throws SQLException {
            var provider = new DatabaseClientProvider(dataSource);
            TestUtils.executeSql(dataSource, "sql/workspace/insert-workspace-full-graph.sql");
            JdbcWorkspaceRepository repository = new JdbcWorkspaceRepository("ws-001", provider);

            repository.delete();

            // Everything reachable only through ws-001 must be gone, including soft-deleted projects
            assertEquals(0, countRows(dataSource, "SELECT COUNT(*) FROM projects WHERE workspace_id = 'ws-001'"));
            assertEquals(0, countRows(dataSource, "SELECT COUNT(*) FROM repositories WHERE project_id IN ('proj-001', 'proj-002')"));
            assertEquals(0, countRows(dataSource, "SELECT COUNT(*) FROM project_instances WHERE project_id = 'proj-001'"));
            assertEquals(0, countRows(dataSource, "SELECT COUNT(*) FROM project_instance_sessions WHERE repository_id = 'repo-001'"));
            assertEquals(0, countRows(dataSource, "SELECT COUNT(*) FROM scoped_configs WHERE workspace_id = 'ws-001' OR project_id = 'proj-001'"));

            // The sibling workspace and global settings must remain untouched
            assertEquals(1, countRows(dataSource, "SELECT COUNT(*) FROM workspaces WHERE workspace_id = 'ws-002'"));
            assertEquals(1, countRows(dataSource, "SELECT COUNT(*) FROM projects WHERE workspace_id = 'ws-002'"));
            assertEquals(1, countRows(dataSource, "SELECT COUNT(*) FROM repositories WHERE project_id = 'proj-101'"));
            assertEquals(1, countRows(dataSource, "SELECT COUNT(*) FROM project_instances WHERE project_id = 'proj-101'"));
            assertEquals(1, countRows(dataSource, "SELECT COUNT(*) FROM project_instance_sessions WHERE repository_id = 'repo-101'"));
            assertEquals(1, countRows(dataSource, "SELECT COUNT(*) FROM scoped_configs WHERE workspace_id IS NULL AND project_id IS NULL"));
            assertEquals(1, countRows(dataSource, "SELECT COUNT(*) FROM scoped_configs WHERE workspace_id = 'ws-002'"));
        }

        /**
         * Through the repository API rather than a fixture, because the fixture once wrote a
         * shape the code never did: the cascade deleted {@code project_id IS NULL} while
         * {@code upsertSettings} stored a sentinel there, so a workspace's own settings survived
         * its deletion and applied to the next workspace created under the same id.
         */
        @Test
        void deletesTheWorkspaceLevelConfigurationItWrote(DataSource dataSource) throws SQLException {
            var provider = new DatabaseClientProvider(dataSource);
            TestUtils.executeSql(dataSource, "sql/workspaces/insert-workspace.sql");
            upsertConfig(provider, ScopedConfigKey.workspace("ws-001"), "workspace-settings");
            upsertConfig(provider, ScopedConfigKey.global(), "global-settings");
            JdbcWorkspaceRepository repository = new JdbcWorkspaceRepository("ws-001", provider);

            repository.delete();

            assertEquals(List.of(), new JdbcScopedConfigRepository(provider).findForWorkspace("ws-001").stream()
                    .filter(entry -> entry.key().workspaceId() != null).toList());
            assertEquals(1, countRows(dataSource, "SELECT COUNT(*) FROM scoped_configs"));
        }

        @Test
        void treatsWorkspaceIdAsDataNotSql(DataSource dataSource) throws SQLException {
            var provider = new DatabaseClientProvider(dataSource);
            TestUtils.executeSql(dataSource, "sql/workspaces/insert-workspace.sql");
            String maliciousId = "ws-001'; DELETE FROM workspaces; --";
            JdbcWorkspaceRepository repository = new JdbcWorkspaceRepository(maliciousId, provider);

            repository.delete();

            // The malicious value is bound as data: nothing matches, nothing is dropped
            assertEquals(1, countRows(dataSource, "SELECT COUNT(*) FROM workspaces WHERE workspace_id = 'ws-001'"));
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

    @Nested
    class FindAllProjectsMethod {

        @Test
        void returnsEmptyList_whenNoProjects(DataSource dataSource) throws SQLException {
            var provider = new DatabaseClientProvider(dataSource);
            TestUtils.executeSql(dataSource, "sql/workspaces/insert-workspace.sql");
            JdbcWorkspaceRepository repository = new JdbcWorkspaceRepository("ws-001", provider);

            List<ProjectInfo> result = repository.findAllProjects();

            assertTrue(result.isEmpty());
        }

        @Test
        void returnsProjects_whenProjectsExist(DataSource dataSource) throws SQLException {
            var provider = new DatabaseClientProvider(dataSource);
            TestUtils.executeSql(dataSource, "sql/workspace/insert-workspace-with-projects-and-events.sql");
            JdbcWorkspaceRepository repository = new JdbcWorkspaceRepository("ws-001", provider);

            List<ProjectInfo> result = repository.findAllProjects();

            assertEquals(2, result.size());
        }
    }

    private static void upsertConfig(DatabaseClientProvider provider, ScopedConfigKey key, String value) {
        new JdbcScopedConfigRepository(provider).upsert(
                new ScopedConfigEntry(key, ConfigType.ASPROF_SETTINGS, value, FIXED_INSTANT));
    }
}
