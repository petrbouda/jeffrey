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

package pbouda.jeffrey.server.persistence.sql;

import org.junit.jupiter.api.Test;
import pbouda.jeffrey.server.persistence.model.SessionWithRepository;
import pbouda.jeffrey.shared.common.model.ProjectInstanceSessionInfo;
import pbouda.jeffrey.shared.common.model.RepositoryType;
import pbouda.jeffrey.shared.persistence.client.DatabaseClientProvider;
import pbouda.jeffrey.test.DuckDBTest;
import pbouda.jeffrey.test.TestUtils;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DuckDBTest(migration = "classpath:db/migration/server")
class JdbcServerPlatformRepositoriesTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-01-15T12:00:00Z"), ZoneId.of("UTC"));

    @Test
    void findSessionWithRepositoryById_returnsJoinedRow_whenSessionExists(DataSource dataSource) throws SQLException {
        var provider = new DatabaseClientProvider(dataSource);
        TestUtils.executeSql(dataSource, "sql/repository/insert-project-with-repository-and-sessions.sql");
        JdbcServerPlatformRepositories repositories = new JdbcServerPlatformRepositories(provider, FIXED_CLOCK);

        Optional<SessionWithRepository> result = repositories.findSessionWithRepositoryById("session-002");

        assertTrue(result.isPresent());
        assertEquals("proj-001", result.get().projectId());
        assertEquals("session-002", result.get().sessionInfo().sessionId());
        assertEquals("inst-002", result.get().sessionInfo().instanceId());
        assertNull(result.get().sessionInfo().finishedAt());

        assertEquals("repo-001", result.get().repositoryInfo().id());
        assertEquals(RepositoryType.ASYNC_PROFILER, result.get().repositoryInfo().repositoryType());
        assertEquals("/workspaces", result.get().repositoryInfo().workspacesPath());
        assertEquals("ws-001", result.get().repositoryInfo().relativeWorkspacePath());
        assertEquals("proj-001", result.get().repositoryInfo().relativeProjectPath());
    }

    @Test
    void findSessionWithRepositoryById_returnsEmpty_whenSessionDoesNotExist(DataSource dataSource) throws SQLException {
        var provider = new DatabaseClientProvider(dataSource);
        TestUtils.executeSql(dataSource, "sql/repository/insert-project-with-repository-and-sessions.sql");
        JdbcServerPlatformRepositories repositories = new JdbcServerPlatformRepositories(provider, FIXED_CLOCK);

        Optional<SessionWithRepository> result = repositories.findSessionWithRepositoryById("non-existent");

        assertTrue(result.isEmpty());
    }

    @Test
    void findSessionsByProjectId_returnsAllSessionsGroupedByInstance(DataSource dataSource) throws SQLException {
        var provider = new DatabaseClientProvider(dataSource);
        TestUtils.executeSql(dataSource, "sql/instances/insert-project-with-instances.sql");
        JdbcServerPlatformRepositories repositories = new JdbcServerPlatformRepositories(provider, FIXED_CLOCK);

        List<ProjectInstanceSessionInfo> sessions = repositories.findSessionsByProjectId("proj-001");

        assertEquals(2, sessions.size());
        assertEquals("inst-001", sessions.get(0).instanceId());
        assertEquals("session-001", sessions.get(0).sessionId());
        assertEquals("inst-002", sessions.get(1).instanceId());
        assertEquals("session-002", sessions.get(1).sessionId());
    }

    @Test
    void findSessionsByProjectId_returnsEmpty_whenProjectHasNoInstances(DataSource dataSource) throws SQLException {
        var provider = new DatabaseClientProvider(dataSource);
        TestUtils.executeSql(dataSource, "sql/instances/insert-project-with-instances.sql");
        JdbcServerPlatformRepositories repositories = new JdbcServerPlatformRepositories(provider, FIXED_CLOCK);

        List<ProjectInstanceSessionInfo> sessions = repositories.findSessionsByProjectId("non-existent");

        assertTrue(sessions.isEmpty());
    }
}
