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

package cafe.jeffrey.provider.profile.jdbc;

import cafe.jeffrey.provider.profile.api.*;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import cafe.jeffrey.provider.profile.jdbc.DuckDBSQLFormatter;
import cafe.jeffrey.provider.profile.api.AllocatingThread;
import cafe.jeffrey.shared.common.model.Type;
import cafe.jeffrey.shared.persistence.client.DatabaseClientProvider;
import cafe.jeffrey.test.DuckDBTest;
import cafe.jeffrey.test.TestUtils;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DuckDBTest(migration = "classpath:db/migration/profile")
class JdbcProfileEventRepositoryTest {

    private static final DuckDBSQLFormatter SQL_FORMATTER = new DuckDBSQLFormatter();

    @Nested
    class LatestJsonFieldsMethod {

        @Test
        void returnsLatestFields(DataSource dataSource) throws SQLException {
            TestUtils.executeSql(dataSource, "sql/events/insert-events-with-types.sql");
            var provider = new DatabaseClientProvider(dataSource);
            JdbcProfileEventRepository repository = new JdbcProfileEventRepository(SQL_FORMATTER, provider);

            Optional<ObjectNode> result = repository.latestJsonFields(Type.fromCode("jdk.ExecutionSample"));

            assertTrue(result.isPresent());
            assertEquals("RUNNABLE", result.get().get("state").asString());
        }

        @Test
        void returnsEmptyWhenNoEvents(DataSource dataSource) {
            var provider = new DatabaseClientProvider(dataSource);
            JdbcProfileEventRepository repository = new JdbcProfileEventRepository(SQL_FORMATTER, provider);

            Optional<ObjectNode> result = repository.latestJsonFields(Type.fromCode("jdk.ExecutionSample"));

            assertTrue(result.isEmpty());
        }

        @Test
        void returnsEmptyForNonExistentEventType(DataSource dataSource) throws SQLException {
            TestUtils.executeSql(dataSource, "sql/events/insert-events-with-types.sql");
            var provider = new DatabaseClientProvider(dataSource);
            JdbcProfileEventRepository repository = new JdbcProfileEventRepository(SQL_FORMATTER, provider);

            Optional<ObjectNode> result = repository.latestJsonFields(Type.fromCode("jdk.NonExistent"));

            assertTrue(result.isEmpty());
        }
    }

    @Nested
    class AllocatingThreadsMethod {

        @Test
        void returnsThreadsOrderedByWeight(DataSource dataSource) throws SQLException {
            TestUtils.executeSql(dataSource, "sql/events/insert-allocating-threads.sql");
            var provider = new DatabaseClientProvider(dataSource);
            JdbcProfileEventRepository repository = new JdbcProfileEventRepository(SQL_FORMATTER, provider);

            List<AllocatingThread> result = repository.allocatingThreads(10);

            assertEquals(4, result.size());
            // Verify ordering by weight descending
            assertEquals("main", result.get(0).threadInfo().name());
            assertEquals(5000000L, result.get(0).allocatedBytes());
            assertEquals("worker-1", result.get(1).threadInfo().name());
            assertEquals(3000000L, result.get(1).allocatedBytes());
        }

        @Test
        void respectsLimit(DataSource dataSource) throws SQLException {
            TestUtils.executeSql(dataSource, "sql/events/insert-allocating-threads.sql");
            var provider = new DatabaseClientProvider(dataSource);
            JdbcProfileEventRepository repository = new JdbcProfileEventRepository(SQL_FORMATTER, provider);

            List<AllocatingThread> result = repository.allocatingThreads(2);

            assertEquals(2, result.size());
            assertEquals("main", result.get(0).threadInfo().name());
            assertEquals("worker-1", result.get(1).threadInfo().name());
        }

        @Test
        void returnsEmptyListWhenNoData(DataSource dataSource) {
            var provider = new DatabaseClientProvider(dataSource);
            JdbcProfileEventRepository repository = new JdbcProfileEventRepository(SQL_FORMATTER, provider);

            List<AllocatingThread> result = repository.allocatingThreads(10);

            assertTrue(result.isEmpty());
        }
    }

    @Nested
    class EventsByTypeWithFieldsMethod {

        @Test
        void returnsEventsWithFields(DataSource dataSource) throws SQLException {
            TestUtils.executeSql(dataSource, "sql/events/insert-events-with-types.sql");
            var provider = new DatabaseClientProvider(dataSource);
            JdbcProfileEventRepository repository = new JdbcProfileEventRepository(SQL_FORMATTER, provider);

            List<JsonNode> result = repository.eventsByTypeWithFields(Type.fromCode("jdk.ExecutionSample"));

            assertEquals(3, result.size());
        }

        @Test
        void returnsEmptyListForNonExistentType(DataSource dataSource) throws SQLException {
            TestUtils.executeSql(dataSource, "sql/events/insert-events-with-types.sql");
            var provider = new DatabaseClientProvider(dataSource);
            JdbcProfileEventRepository repository = new JdbcProfileEventRepository(SQL_FORMATTER, provider);

            List<JsonNode> result = repository.eventsByTypeWithFields(Type.fromCode("jdk.NonExistent"));

            assertTrue(result.isEmpty());
        }
    }

    @Nested
    class ContainsEventTypeMethod {

        @Test
        void returnsTrueWhenEventTypeExists(DataSource dataSource) throws SQLException {
            TestUtils.executeSql(dataSource, "sql/events/insert-events-with-types.sql");
            var provider = new DatabaseClientProvider(dataSource);
            JdbcProfileEventRepository repository = new JdbcProfileEventRepository(SQL_FORMATTER, provider);

            assertTrue(repository.containsEventType(Type.fromCode("jdk.ExecutionSample")));
        }

        @Test
        void returnsFalseWhenEventTypeNotExists(DataSource dataSource) throws SQLException {
            TestUtils.executeSql(dataSource, "sql/events/insert-events-with-types.sql");
            var provider = new DatabaseClientProvider(dataSource);
            JdbcProfileEventRepository repository = new JdbcProfileEventRepository(SQL_FORMATTER, provider);

            assertFalse(repository.containsEventType(Type.fromCode("jdk.NonExistent")));
        }

        @Test
        void returnsFalseWhenNoEvents(DataSource dataSource) {
            var provider = new DatabaseClientProvider(dataSource);
            JdbcProfileEventRepository repository = new JdbcProfileEventRepository(SQL_FORMATTER, provider);

            assertFalse(repository.containsEventType(Type.fromCode("jdk.ExecutionSample")));
        }
    }

    @Nested
    class DurationStatsByTypeMethod {

        // Fixture: 10 jdk.SafepointBegin events with durations (ms): 1,2,3,4,5,6,7,8,9,500
        // plus 2 NULL-duration rows that must be excluded
        // plus 1 jdk.ExecutionSample row at 999 ms that must NOT leak into SafepointBegin stats.
        private static final long MS = 1_000_000L;

        @Test
        void returnsEmptyStatsWhenNoEvents(DataSource dataSource) {
            var provider = new DatabaseClientProvider(dataSource);
            JdbcProfileEventRepository repository = new JdbcProfileEventRepository(SQL_FORMATTER, provider);

            var stats = repository.durationStatsByType(Type.fromCode("jdk.SafepointBegin"));

            assertEquals(0, stats.count());
            assertEquals(0, stats.totalDurationNs());
            assertEquals(0, stats.maxDurationNs());
            assertEquals(0, stats.p99DurationNs());
        }

        @Test
        void countExcludesNullDurationEvents(DataSource dataSource) throws SQLException {
            TestUtils.executeSql(dataSource, "sql/events/insert-events-with-duration.sql");
            var provider = new DatabaseClientProvider(dataSource);
            JdbcProfileEventRepository repository = new JdbcProfileEventRepository(SQL_FORMATTER, provider);

            var stats = repository.durationStatsByType(Type.fromCode("jdk.SafepointBegin"));

            // 12 total safepoint rows in fixture, 2 with NULL duration → 10 counted.
            assertEquals(10, stats.count(),
                    "Rows with NULL duration must be excluded from the count");
        }

        @Test
        void totalAndMaxMatchFixture(DataSource dataSource) throws SQLException {
            TestUtils.executeSql(dataSource, "sql/events/insert-events-with-duration.sql");
            var provider = new DatabaseClientProvider(dataSource);
            JdbcProfileEventRepository repository = new JdbcProfileEventRepository(SQL_FORMATTER, provider);

            var stats = repository.durationStatsByType(Type.fromCode("jdk.SafepointBegin"));

            // Durations sum: (1+2+3+4+5+6+7+8+9+500) ms = 545 ms.
            assertEquals(545 * MS, stats.totalDurationNs());
            assertEquals(500 * MS, stats.maxDurationNs());
        }

        @Test
        void p99IsComputedViaQuantileCont(DataSource dataSource) throws SQLException {
            TestUtils.executeSql(dataSource, "sql/events/insert-events-with-duration.sql");
            var provider = new DatabaseClientProvider(dataSource);
            JdbcProfileEventRepository repository = new JdbcProfileEventRepository(SQL_FORMATTER, provider);

            var stats = repository.durationStatsByType(Type.fromCode("jdk.SafepointBegin"));

            // quantile_cont(0.99) on sorted [1,2,3,4,5,6,7,8,9,500] ms interpolates at index 8.91:
            //   9 ms + 0.91 * (500 - 9) ms = 9 ms + 446.81 ms = 455.81 ms
            // The exact interpolation formula DuckDB uses is index = 0.99 * (n-1) = 8.91.
            // Allow ±5 ms tolerance to survive tiny implementation tweaks across DuckDB versions.
            long expectedP99Ns = 455_810_000L;
            long toleranceNs = 5 * MS;
            long actual = stats.p99DurationNs();
            assertTrue(Math.abs(actual - expectedP99Ns) <= toleranceNs,
                    "Expected p99 near " + expectedP99Ns + " ns (±" + toleranceNs + "), got " + actual);
            assertTrue(actual > stats.maxDurationNs() / 2,
                    "p99 should be dominated by the 500 ms outlier, not the tight 1–9 ms cluster");
            assertTrue(actual < stats.maxDurationNs(),
                    "p99 must be strictly below max for this fixture");
        }

        @Test
        void statsForDifferentTypeAreIsolated(DataSource dataSource) throws SQLException {
            TestUtils.executeSql(dataSource, "sql/events/insert-events-with-duration.sql");
            var provider = new DatabaseClientProvider(dataSource);
            JdbcProfileEventRepository repository = new JdbcProfileEventRepository(SQL_FORMATTER, provider);

            var execStats = repository.durationStatsByType(Type.fromCode("jdk.ExecutionSample"));
            assertEquals(1, execStats.count(),
                    "ExecutionSample fixture has a single row — must not leak safepoint data");
            assertEquals(999_999_999L, execStats.totalDurationNs());
        }

        @Test
        void unknownEventType_returnsEmpty(DataSource dataSource) throws SQLException {
            TestUtils.executeSql(dataSource, "sql/events/insert-events-with-duration.sql");
            var provider = new DatabaseClientProvider(dataSource);
            JdbcProfileEventRepository repository = new JdbcProfileEventRepository(SQL_FORMATTER, provider);

            var stats = repository.durationStatsByType(Type.fromCode("jdk.VirtualThreadPinned"));

            assertEquals(0, stats.count());
        }
    }
}
