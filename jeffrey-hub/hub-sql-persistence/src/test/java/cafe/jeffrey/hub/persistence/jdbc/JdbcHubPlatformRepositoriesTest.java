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

import org.junit.jupiter.api.Test;
import cafe.jeffrey.hub.persistence.api.SessionWithRepository;
import cafe.jeffrey.hub.model.ProjectInstanceSessionInfo;
import cafe.jeffrey.shared.common.model.RepositoryType;
import cafe.jeffrey.shared.persistence.client.DatabaseClientProvider;
import cafe.jeffrey.test.DuckDBTest;
import cafe.jeffrey.test.TestUtils;

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

@DuckDBTest(migration = "classpath:db/migration/hub")
class JdbcHubPlatformRepositoriesTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-01-15T12:00:00Z"), ZoneId.of("UTC"));

    @Test
    void findSessionWithRepositoryById_returnsJoinedRow_whenSessionExists(DataSource dataSource) throws SQLException {
        var provider = new DatabaseClientProvider(dataSource);
        TestUtils.executeSql(dataSource, "sql/repository/insert-project-with-repository-and-sessions.sql");
        JdbcHubPlatformRepositories repositories = new JdbcHubPlatformRepositories(provider, FIXED_CLOCK);

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
        JdbcHubPlatformRepositories repositories = new JdbcHubPlatformRepositories(provider, FIXED_CLOCK);

        Optional<SessionWithRepository> result = repositories.findSessionWithRepositoryById("non-existent");

        assertTrue(result.isEmpty());
    }

    @Test
    void findSessionsByProjectId_returnsAllSessionsGroupedByInstance(DataSource dataSource) throws SQLException {
        var provider = new DatabaseClientProvider(dataSource);
        TestUtils.executeSql(dataSource, "sql/instances/insert-project-with-instances.sql");
        JdbcHubPlatformRepositories repositories = new JdbcHubPlatformRepositories(provider, FIXED_CLOCK);

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
        JdbcHubPlatformRepositories repositories = new JdbcHubPlatformRepositories(provider, FIXED_CLOCK);

        List<ProjectInstanceSessionInfo> sessions = repositories.findSessionsByProjectId("non-existent");

        assertTrue(sessions.isEmpty());
    }
}
