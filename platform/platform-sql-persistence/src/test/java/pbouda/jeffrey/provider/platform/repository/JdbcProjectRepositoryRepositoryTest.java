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
import pbouda.jeffrey.shared.common.model.RepositoryInfo;
import pbouda.jeffrey.shared.common.model.RepositoryType;
import pbouda.jeffrey.shared.common.model.ProjectInstanceSessionInfo;
import pbouda.jeffrey.shared.persistence.client.DatabaseClientProvider;
import pbouda.jeffrey.test.DuckDBTest;
import pbouda.jeffrey.test.TestUtils;

import javax.sql.DataSource;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DuckDBTest(migration = "classpath:db/migration/platform")
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

            RepositoryInfo repoInfo = new RepositoryInfo(null, RepositoryType.ASYNC_PROFILER, "/workspaces", "ws-001", "proj-001");
            repository.insert(repoInfo);

            List<RepositoryInfo> result = repository.getAll();
            assertEquals(1, result.size());
            assertEquals(RepositoryType.ASYNC_PROFILER, result.get(0).repositoryType());
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

            ProjectInstanceSessionInfo sessionInfo = new ProjectInstanceSessionInfo(
                    "session-new-001", "repo-001", "inst-001", 1, Path.of("session-test"), "cpu=true",
                    Instant.parse("2025-01-15T10:00:00Z"), null, null);

            repository.createSession(sessionInfo);

            List<ProjectInstanceSessionInfo> result = repository.findAllSessions();
            assertEquals(1, result.size());
            assertEquals("cpu=true", result.get(0).profilerSettings());
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

        @Test
        void findLatestSessionId_returnsLatestSession(DataSource dataSource) throws SQLException {
            var provider = new DatabaseClientProvider(dataSource);
            TestUtils.executeSql(dataSource, "sql/repository/insert-project-with-repository-and-sessions.sql");
            JdbcProjectRepositoryRepository repository = new JdbcProjectRepositoryRepository(FIXED_CLOCK, "proj-001", provider);

            // session-002 has origin_created_at 2025-01-02 > session-001's 2025-01-01
            Optional<String> result = repository.findLatestSessionId();

            assertTrue(result.isPresent());
            assertEquals("session-002", result.get());
        }

        @Test
        void findLatestSessionId_returnsEmpty_whenNoSessions(DataSource dataSource) throws SQLException {
            var provider = new DatabaseClientProvider(dataSource);
            TestUtils.executeSql(dataSource, "sql/repository/insert-project-with-repository.sql");
            JdbcProjectRepositoryRepository repository = new JdbcProjectRepositoryRepository(FIXED_CLOCK, "proj-001", provider);

            Optional<String> result = repository.findLatestSessionId();

            assertTrue(result.isEmpty());
        }
    }
}
