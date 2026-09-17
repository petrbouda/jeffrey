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
import cafe.jeffrey.hub.model.RepositoryInfo;
import cafe.jeffrey.shared.common.model.RepositoryType;
import cafe.jeffrey.hub.model.ProjectInstanceSessionInfo;
import cafe.jeffrey.shared.persistence.client.DatabaseClientProvider;
import cafe.jeffrey.test.DuckDBTest;
import cafe.jeffrey.test.TestUtils;

import javax.sql.DataSource;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DuckDBTest(migration = "classpath:db/migration/hub")
class JdbcProjectRepositoryRepositoryTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2025-01-15T12:00:00Z"), ZoneId.of("UTC"));

    @Nested
    class RepositoryMethods {

        @Test
        void insertsAndRetrievesRepository(DataSource dataSource) throws SQLException {
            var provider = new DatabaseClientProvider(dataSource);
            TestUtils.executeSql(dataSource, "sql/projects/insert-workspace-with-projects.sql");
            JdbcProjectRepositoryRepository repository = new JdbcProjectRepositoryRepository(FIXED_CLOCK, "proj-001", provider);

            RepositoryInfo repoInfo = new RepositoryInfo("repo-new", RepositoryType.ASYNC_PROFILER, "/workspaces", "ws-001", "proj-001");
            repository.insert(repoInfo);

            List<RepositoryInfo> result = repository.getAll();
            assertEquals(1, result.size());
            assertEquals("repo-new", result.get(0).id());
            assertEquals(RepositoryType.ASYNC_PROFILER, result.get(0).repositoryType());
        }

        /**
         * The insert is idempotent under the caller's id. It generated its own before, so the
         * ON CONFLICT it relied on never fired and a replayed reconcile left two rows for one
         * project — of which {@code repositoryInfo()} then took whichever came first.
         */
        @Test
        void insertingTheSameRepositoryTwiceKeepsOneRow(DataSource dataSource) throws SQLException {
            var provider = new DatabaseClientProvider(dataSource);
            TestUtils.executeSql(dataSource, "sql/projects/insert-workspace-with-projects.sql");
            JdbcProjectRepositoryRepository repository = new JdbcProjectRepositoryRepository(FIXED_CLOCK, "proj-001", provider);
            RepositoryInfo repoInfo = new RepositoryInfo("repo-new", RepositoryType.ASYNC_PROFILER, "/workspaces", "ws-001", "proj-001");

            repository.insert(repoInfo);
            repository.insert(repoInfo);

            assertEquals(1, repository.getAll().size());
        }

        @Test
        void returnsEmptyList_whenNoRepositories(DataSource dataSource) throws SQLException {
            var provider = new DatabaseClientProvider(dataSource);
            TestUtils.executeSql(dataSource, "sql/projects/insert-workspace-with-projects.sql");
            JdbcProjectRepositoryRepository repository = new JdbcProjectRepositoryRepository(FIXED_CLOCK, "proj-001", provider);

            List<RepositoryInfo> result = repository.getAll();

            assertTrue(result.isEmpty());
        }

        @Test
        void deletesRepository(DataSource dataSource) throws SQLException {
            var provider = new DatabaseClientProvider(dataSource);
            TestUtils.executeSql(dataSource, "sql/repository/insert-project-with-repository.sql");
            JdbcProjectRepositoryRepository repository = new JdbcProjectRepositoryRepository(FIXED_CLOCK, "proj-001", provider);

            repository.delete("repo-001");

            List<RepositoryInfo> result = repository.getAll();
            assertTrue(result.isEmpty());
        }

        @Test
        void deletesAllRepositories(DataSource dataSource) throws SQLException {
            var provider = new DatabaseClientProvider(dataSource);
            TestUtils.executeSql(dataSource, "sql/repository/insert-project-with-repository.sql");
            JdbcProjectRepositoryRepository repository = new JdbcProjectRepositoryRepository(FIXED_CLOCK, "proj-001", provider);

            repository.deleteAll();

            List<RepositoryInfo> result = repository.getAll();
            assertTrue(result.isEmpty());
        }
    }

    @Nested
    class SessionMethods {

        @Test
        void createsAndFindsSessions(DataSource dataSource) throws SQLException {
            var provider = new DatabaseClientProvider(dataSource);
            TestUtils.executeSql(dataSource, "sql/repository/insert-project-with-repository.sql");
            JdbcProjectRepositoryRepository repository = new JdbcProjectRepositoryRepository(FIXED_CLOCK, "proj-001", provider);

            ProjectInstanceSessionInfo sessionInfo = ProjectInstanceSessionInfo.notRetained(
                    "session-new-001", "repo-001", "inst-001", 1, Path.of("session-test"),
                    Instant.parse("2025-01-15T10:00:00Z"), null, null);

            repository.createSession(sessionInfo);

            List<ProjectInstanceSessionInfo> result = repository.findAllSessions();
            assertEquals(1, result.size());
            assertEquals("session-new-001", result.get(0).sessionId());
        }

        @Test
        void findsSessionById(DataSource dataSource) throws SQLException {
            var provider = new DatabaseClientProvider(dataSource);
            TestUtils.executeSql(dataSource, "sql/repository/insert-project-with-repository-and-sessions.sql");
            JdbcProjectRepositoryRepository repository = new JdbcProjectRepositoryRepository(FIXED_CLOCK, "proj-001", provider);

            Optional<ProjectInstanceSessionInfo> result = repository.findSessionById("session-001");

            assertTrue(result.isPresent());
            assertEquals("session-001", result.get().sessionId());
        }

        @Test
        void newSessionsAreNotRetained(DataSource dataSource) throws SQLException {
            var provider = new DatabaseClientProvider(dataSource);
            TestUtils.executeSql(dataSource, "sql/repository/insert-project-with-repository-and-sessions.sql");
            JdbcProjectRepositoryRepository repository = new JdbcProjectRepositoryRepository(FIXED_CLOCK, "proj-001", provider);

            assertFalse(repository.findSessionById("session-001").orElseThrow().retained());
        }

        @Test
        void setSessionRetainedTogglesTheFlag(DataSource dataSource) throws SQLException {
            var provider = new DatabaseClientProvider(dataSource);
            TestUtils.executeSql(dataSource, "sql/repository/insert-project-with-repository-and-sessions.sql");
            JdbcProjectRepositoryRepository repository = new JdbcProjectRepositoryRepository(FIXED_CLOCK, "proj-001", provider);

            repository.setSessionRetained("session-001", true);
            assertTrue(repository.findSessionById("session-001").orElseThrow().retained());

            repository.setSessionRetained("session-001", false);
            assertFalse(repository.findSessionById("session-001").orElseThrow().retained());
        }

        @Test
        void setSessionRetainedLeavesOtherSessionsUntouched(DataSource dataSource) throws SQLException {
            var provider = new DatabaseClientProvider(dataSource);
            TestUtils.executeSql(dataSource, "sql/repository/insert-project-with-repository-and-sessions.sql");
            JdbcProjectRepositoryRepository repository = new JdbcProjectRepositoryRepository(FIXED_CLOCK, "proj-001", provider);

            repository.setSessionRetained("session-001", true);

            List<ProjectInstanceSessionInfo> others = repository.findAllSessions().stream()
                    .filter(session -> !"session-001".equals(session.sessionId()))
                    .toList();

            assertFalse(others.isEmpty(), "Fixture must contain more than one session for this to be meaningful");
            assertTrue(others.stream().noneMatch(ProjectInstanceSessionInfo::retained));
        }

        @Test
        void returnsEmpty_whenSessionNotExists(DataSource dataSource) throws SQLException {
            var provider = new DatabaseClientProvider(dataSource);
            TestUtils.executeSql(dataSource, "sql/repository/insert-project-with-repository.sql");
            JdbcProjectRepositoryRepository repository = new JdbcProjectRepositoryRepository(FIXED_CLOCK, "proj-001", provider);

            Optional<ProjectInstanceSessionInfo> result = repository.findSessionById("non-existent");

            assertTrue(result.isEmpty());
        }

        @Test
        void findsUnfinishedSessions(DataSource dataSource) throws SQLException {
            var provider = new DatabaseClientProvider(dataSource);
            TestUtils.executeSql(dataSource, "sql/repository/insert-project-with-repository-and-sessions.sql");
            JdbcProjectRepositoryRepository repository = new JdbcProjectRepositoryRepository(FIXED_CLOCK, "proj-001", provider);

            List<ProjectInstanceSessionInfo> result = repository.findUnfinishedSessions();

            assertEquals(1, result.size());
            assertEquals("session-002", result.get(0).sessionId());
            assertNull(result.get(0).finishedAt());
        }

        @Test
        void marksSessionFinished(DataSource dataSource) throws SQLException {
            var provider = new DatabaseClientProvider(dataSource);
            TestUtils.executeSql(dataSource, "sql/repository/insert-project-with-repository-and-sessions.sql");
            JdbcProjectRepositoryRepository repository = new JdbcProjectRepositoryRepository(FIXED_CLOCK, "proj-001", provider);

            Instant finishedAt = Instant.parse("2025-01-15T14:00:00Z");
            repository.markSessionFinished("session-002", finishedAt);

            Optional<ProjectInstanceSessionInfo> result = repository.findSessionById("session-002");
            assertTrue(result.isPresent());
            assertNotNull(result.get().finishedAt());
        }

        @Test
        void deletesSession(DataSource dataSource) throws SQLException {
            var provider = new DatabaseClientProvider(dataSource);
            TestUtils.executeSql(dataSource, "sql/repository/insert-project-with-repository-and-sessions.sql");
            JdbcProjectRepositoryRepository repository = new JdbcProjectRepositoryRepository(FIXED_CLOCK, "proj-001", provider);

            repository.deleteSession("session-001");

            Optional<ProjectInstanceSessionInfo> result = repository.findSessionById("session-001");
            assertTrue(result.isEmpty());
        }

        @Test
        void findsUnfinishedSessionsByInstanceId_returnsMatchingSessions(DataSource dataSource) throws SQLException {
            var provider = new DatabaseClientProvider(dataSource);
            TestUtils.executeSql(dataSource, "sql/repository/insert-project-with-repository-and-sessions.sql");
            JdbcProjectRepositoryRepository repository = new JdbcProjectRepositoryRepository(FIXED_CLOCK, "proj-001", provider);

            // inst-002 has session-002 which is unfinished (finished_at IS NULL)
            List<ProjectInstanceSessionInfo> result = repository.findUnfinishedSessionsByInstanceId("inst-002");

            assertEquals(1, result.size());
            assertEquals("session-002", result.getFirst().sessionId());
        }

        @Test
        void findsUnfinishedSessionsByInstanceId_returnsEmpty_whenInstanceHasNoUnfinished(DataSource dataSource) throws SQLException {
            var provider = new DatabaseClientProvider(dataSource);
            TestUtils.executeSql(dataSource, "sql/repository/insert-project-with-repository-and-sessions.sql");
            JdbcProjectRepositoryRepository repository = new JdbcProjectRepositoryRepository(FIXED_CLOCK, "proj-001", provider);

            // inst-001 has session-001 which is finished
            List<ProjectInstanceSessionInfo> result = repository.findUnfinishedSessionsByInstanceId("inst-001");

            assertTrue(result.isEmpty());
        }
    }
}
