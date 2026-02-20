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
import pbouda.jeffrey.shared.common.model.ImportantMessage;
import pbouda.jeffrey.shared.common.model.Severity;
import pbouda.jeffrey.shared.persistence.client.DatabaseClientProvider;
import pbouda.jeffrey.test.DuckDBTest;
import pbouda.jeffrey.test.TestUtils;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DuckDBTest(migration = "classpath:db/migration/platform")
class JdbcMessageRepositoryTest {

    private static final Instant T1 = Instant.parse("2025-06-01T10:00:00Z");
    private static final Instant T2 = Instant.parse("2025-06-01T11:00:00Z");
    private static final Instant T3 = Instant.parse("2025-06-01T12:00:00Z");

    private static ImportantMessage message(String type, String sessionId, Instant createdAt) {
        return new ImportantMessage(type, "Title", "Message text", Severity.MEDIUM, "PERFORMANCE", "test", false, sessionId, createdAt);
    }

    @Nested
    class InsertMethod {

        @Test
        void insertsMessage(DataSource dataSource) throws SQLException {
            var provider = new DatabaseClientProvider(dataSource);
            TestUtils.executeSql(dataSource, "sql/messages/insert-project-for-messages.sql");
            var repository = new JdbcMessageRepository("proj-001", provider);

            repository.insert(message("HIGH_CPU_USAGE", "session-1", T1));

            List<ImportantMessage> result = repository.findAll(T1.minusSeconds(1), T1.plusSeconds(1));
            assertEquals(1, result.size());
            ImportantMessage msg = result.getFirst();
            assertEquals("HIGH_CPU_USAGE", msg.type());
            assertEquals("Title", msg.title());
            assertEquals("Message text", msg.message());
            assertEquals(Severity.MEDIUM, msg.severity());
            assertEquals("PERFORMANCE", msg.category());
            assertEquals("test", msg.source());
            assertFalse(msg.isAlert());
            assertEquals("session-1", msg.sessionId());
            assertEquals(T1, msg.createdAt());
        }

        @Test
        void idempotentInsert_sameDedupTriple_ignored(DataSource dataSource) throws SQLException {
            var provider = new DatabaseClientProvider(dataSource);
            TestUtils.executeSql(dataSource, "sql/messages/insert-project-for-messages.sql");
            var repository = new JdbcMessageRepository("proj-001", provider);

            repository.insert(message("HIGH_CPU_USAGE", "session-1", T1));
            repository.insert(message("HIGH_CPU_USAGE", "session-1", T1));

            List<ImportantMessage> result = repository.findAll(T1.minusSeconds(1), T1.plusSeconds(1));
            assertEquals(1, result.size());
        }

        @Test
        void differentType_sameSessionAndTimestamp_bothInserted(DataSource dataSource) throws SQLException {
            var provider = new DatabaseClientProvider(dataSource);
            TestUtils.executeSql(dataSource, "sql/messages/insert-project-for-messages.sql");
            var repository = new JdbcMessageRepository("proj-001", provider);

            repository.insert(message("HIGH_CPU_USAGE", "session-1", T1));
            repository.insert(message("LOW_MEMORY", "session-1", T1));

            List<ImportantMessage> result = repository.findAll(T1.minusSeconds(1), T1.plusSeconds(1));
            assertEquals(2, result.size());
        }

        @Test
        void sameType_differentTimestamp_bothInserted(DataSource dataSource) throws SQLException {
            var provider = new DatabaseClientProvider(dataSource);
            TestUtils.executeSql(dataSource, "sql/messages/insert-project-for-messages.sql");
            var repository = new JdbcMessageRepository("proj-001", provider);

            repository.insert(message("HIGH_CPU_USAGE", "session-1", T1));
            repository.insert(message("HIGH_CPU_USAGE", "session-1", T2));

            List<ImportantMessage> result = repository.findAll(T1.minusSeconds(1), T2.plusSeconds(1));
            assertEquals(2, result.size());
        }
    }

    @Nested
    class FindAllMethod {

        @Test
        void returnsMessagesInTimeRange_newestFirst(DataSource dataSource) throws SQLException {
            var provider = new DatabaseClientProvider(dataSource);
            TestUtils.executeSql(dataSource, "sql/messages/insert-project-for-messages.sql");
            var repository = new JdbcMessageRepository("proj-001", provider);

            repository.insert(message("TYPE_A", "session-1", T1));
            repository.insert(message("TYPE_B", "session-1", T2));
            repository.insert(message("TYPE_C", "session-1", T3));

            List<ImportantMessage> result = repository.findAll(T1, T3);
            assertEquals(3, result.size());
            assertEquals(T3, result.get(0).createdAt());
            assertEquals(T2, result.get(1).createdAt());
            assertEquals(T1, result.get(2).createdAt());
        }

        @Test
        void returnsEmpty_whenNoMessagesInRange(DataSource dataSource) throws SQLException {
            var provider = new DatabaseClientProvider(dataSource);
            TestUtils.executeSql(dataSource, "sql/messages/insert-project-for-messages.sql");
            var repository = new JdbcMessageRepository("proj-001", provider);

            repository.insert(message("TYPE_A", "session-1", T1));

            Instant rangeBefore = Instant.parse("2025-01-01T00:00:00Z");
            Instant rangeAfter = Instant.parse("2025-01-01T01:00:00Z");
            List<ImportantMessage> result = repository.findAll(rangeBefore, rangeAfter);
            assertTrue(result.isEmpty());
        }

        @Test
        void includesBoundaryTimestamps(DataSource dataSource) throws SQLException {
            var provider = new DatabaseClientProvider(dataSource);
            TestUtils.executeSql(dataSource, "sql/messages/insert-project-for-messages.sql");
            var repository = new JdbcMessageRepository("proj-001", provider);

            repository.insert(message("TYPE_A", "session-1", T1));
            repository.insert(message("TYPE_B", "session-1", T2));
            repository.insert(message("TYPE_C", "session-1", T3));

            List<ImportantMessage> result = repository.findAll(T1, T3);
            assertEquals(3, result.size());

            List<ImportantMessage> boundaryResult = repository.findAll(T2, T2);
            assertEquals(1, boundaryResult.size());
            assertEquals(T2, boundaryResult.getFirst().createdAt());
        }

        @Test
        void excludesMessagesOutsideRange(DataSource dataSource) throws SQLException {
            var provider = new DatabaseClientProvider(dataSource);
            TestUtils.executeSql(dataSource, "sql/messages/insert-project-for-messages.sql");
            var repository = new JdbcMessageRepository("proj-001", provider);

            repository.insert(message("TYPE_A", "session-1", T1));
            repository.insert(message("TYPE_B", "session-1", T2));
            repository.insert(message("TYPE_C", "session-1", T3));

            List<ImportantMessage> result = repository.findAll(T2, T2);
            assertEquals(1, result.size());
            assertEquals("TYPE_B", result.getFirst().type());
        }

        @Test
        void filtersByProjectId(DataSource dataSource) throws SQLException {
            var provider = new DatabaseClientProvider(dataSource);
            TestUtils.executeSql(dataSource, "sql/messages/insert-project-for-messages.sql");

            try (Connection conn = dataSource.getConnection();
                 Statement stmt = conn.createStatement()) {
                stmt.execute("""
                        INSERT INTO projects (project_id, origin_project_id, project_name, project_label, namespace, workspace_id, created_at, origin_created_at, attributes, graph_visualization)
                        VALUES ('proj-002', NULL, 'Other Project', 'Label 2', NULL, 'ws-001', '2025-01-01T11:00:00Z', NULL, '{}', '{}')
                        """);
            }

            var repo1 = new JdbcMessageRepository("proj-001", provider);
            var repo2 = new JdbcMessageRepository("proj-002", provider);

            repo1.insert(message("TYPE_A", "session-1", T1));
            repo2.insert(message("TYPE_B", "session-2", T2));

            List<ImportantMessage> result1 = repo1.findAll(T1.minusSeconds(1), T2.plusSeconds(1));
            assertEquals(1, result1.size());
            assertEquals("TYPE_A", result1.getFirst().type());

            List<ImportantMessage> result2 = repo2.findAll(T1.minusSeconds(1), T2.plusSeconds(1));
            assertEquals(1, result2.size());
            assertEquals("TYPE_B", result2.getFirst().type());
        }
    }

    @Nested
    class DeleteByProjectMethod {

        @Test
        void deletesAllForProject(DataSource dataSource) throws SQLException {
            var provider = new DatabaseClientProvider(dataSource);
            TestUtils.executeSql(dataSource, "sql/messages/insert-project-for-messages.sql");
            var repository = new JdbcMessageRepository("proj-001", provider);

            repository.insert(message("TYPE_A", "session-1", T1));
            repository.insert(message("TYPE_B", "session-1", T2));

            repository.deleteByProject();

            List<ImportantMessage> result = repository.findAll(T1.minusSeconds(1), T2.plusSeconds(1));
            assertTrue(result.isEmpty());
        }

        @Test
        void doesNotAffectOtherProjects(DataSource dataSource) throws SQLException {
            var provider = new DatabaseClientProvider(dataSource);
            TestUtils.executeSql(dataSource, "sql/messages/insert-project-for-messages.sql");

            try (Connection conn = dataSource.getConnection();
                 Statement stmt = conn.createStatement()) {
                stmt.execute("""
                        INSERT INTO projects (project_id, origin_project_id, project_name, project_label, namespace, workspace_id, created_at, origin_created_at, attributes, graph_visualization)
                        VALUES ('proj-002', NULL, 'Other Project', 'Label 2', NULL, 'ws-001', '2025-01-01T11:00:00Z', NULL, '{}', '{}')
                        """);
            }

            var repo1 = new JdbcMessageRepository("proj-001", provider);
            var repo2 = new JdbcMessageRepository("proj-002", provider);

            repo1.insert(message("TYPE_A", "session-1", T1));
            repo2.insert(message("TYPE_B", "session-2", T2));

            repo1.deleteByProject();

            List<ImportantMessage> result1 = repo1.findAll(T1.minusSeconds(1), T2.plusSeconds(1));
            assertTrue(result1.isEmpty());

            List<ImportantMessage> result2 = repo2.findAll(T1.minusSeconds(1), T2.plusSeconds(1));
            assertEquals(1, result2.size());
            assertEquals("TYPE_B", result2.getFirst().type());
        }
    }

    @Nested
    class DeleteOlderThanMethod {

        private static final Instant CUTOFF = Instant.parse("2025-05-30T00:00:00Z");

        private static long countMessages(DataSource dataSource) {
            var jdbc = new NamedParameterJdbcTemplate(dataSource);
            Long count = jdbc.queryForObject("SELECT COUNT(*) FROM messages", Map.of(), Long.class);
            return count != null ? count : 0;
        }

        @Test
        void deletesOldMessages_andReturnsCount(DataSource dataSource) throws SQLException {
            var provider = new DatabaseClientProvider(dataSource);
            TestUtils.executeSql(dataSource, "sql/retention/insert-retention-test-data.sql");
            var repository = new JdbcMessageRepository("", provider);

            int deleted = repository.deleteOlderThan(CUTOFF);

            assertEquals(2, deleted);
            assertEquals(1, countMessages(dataSource));
        }

        @Test
        void returnsZero_whenNoOldMessages(DataSource dataSource) throws SQLException {
            var provider = new DatabaseClientProvider(dataSource);
            TestUtils.executeSql(dataSource, "sql/messages/insert-project-for-messages.sql");
            var msgRepo = new JdbcMessageRepository("proj-001", provider);
            msgRepo.insert(message("TYPE_A", "session-1", Instant.parse("2025-06-25T10:00:00Z")));

            var repository = new JdbcMessageRepository("", provider);
            int deleted = repository.deleteOlderThan(CUTOFF);

            assertEquals(0, deleted);
        }

        @Test
        void returnsZero_whenTableEmpty(DataSource dataSource) {
            var provider = new DatabaseClientProvider(dataSource);
            var repository = new JdbcMessageRepository("", provider);

            int deleted = repository.deleteOlderThan(CUTOFF);

            assertEquals(0, deleted);
        }
    }
}
