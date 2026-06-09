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

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import cafe.jeffrey.provider.profile.jdbc.DuckDBSQLFormatter;
import cafe.jeffrey.provider.profile.api.EventTypeWithFields;
import cafe.jeffrey.provider.profile.api.FieldDescription;
import cafe.jeffrey.shared.common.model.EventSummary;
import cafe.jeffrey.shared.common.model.SpanInterval;
import cafe.jeffrey.shared.common.model.Type;
import cafe.jeffrey.shared.persistence.client.DatabaseClientProvider;
import cafe.jeffrey.test.DuckDBTest;
import cafe.jeffrey.test.TestUtils;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DuckDBTest(migration = "classpath:db/migration/profile")
class JdbcProfileEventTypeRepositoryTest {

    private static final DuckDBSQLFormatter SQL_FORMATTER = new DuckDBSQLFormatter();

    @Nested
    class SingleFieldsByEventTypeMethod {

        @Test
        void returnsEventTypeWithFields(DataSource dataSource) throws SQLException {
            TestUtils.executeSql(dataSource, "sql/events/insert-events-with-types.sql");
            var provider = new DatabaseClientProvider(dataSource);
            JdbcProfileEventTypeRepository repository = new JdbcProfileEventTypeRepository(SQL_FORMATTER, provider);

            Optional<EventTypeWithFields> result = repository.singleFieldsByEventType(Type.fromCode("jdk.ExecutionSample"));

            assertTrue(result.isPresent());
            assertEquals("jdk.ExecutionSample", result.get().name());
            assertEquals("Execution Sample", result.get().label());
            assertNotNull(result.get().content());
        }

        @Test
        void returnsEmptyWhenNoEventsForType(DataSource dataSource) throws SQLException {
            TestUtils.executeSql(dataSource, "sql/events/insert-events-with-types.sql");
            var provider = new DatabaseClientProvider(dataSource);
            JdbcProfileEventTypeRepository repository = new JdbcProfileEventTypeRepository(SQL_FORMATTER, provider);

            // ThreadAllocationStatistics has no events with fields in insert-events-with-types.sql
            Optional<EventTypeWithFields> result = repository.singleFieldsByEventType(Type.fromCode("jdk.ThreadAllocationStatistics"));

            assertTrue(result.isEmpty());
        }

        @Test
        void returnsEmptyForNonExistentType(DataSource dataSource) {
            var provider = new DatabaseClientProvider(dataSource);
            JdbcProfileEventTypeRepository repository = new JdbcProfileEventTypeRepository(SQL_FORMATTER, provider);

            Optional<EventTypeWithFields> result = repository.singleFieldsByEventType(Type.fromCode("jdk.NonExistent"));

            assertTrue(result.isEmpty());
        }
    }

    @Nested
    class EventColumnsMethod {

        @Test
        void returnsColumnsFromDatabase(DataSource dataSource) throws SQLException {
            TestUtils.executeSql(dataSource, "sql/events/insert-events-with-types.sql");
            var provider = new DatabaseClientProvider(dataSource);
            JdbcProfileEventTypeRepository repository = new JdbcProfileEventTypeRepository(SQL_FORMATTER, provider);

            List<FieldDescription> result = repository.eventColumns(Type.fromCode("jdk.ExecutionSample"));

            assertFalse(result.isEmpty());
            assertEquals("state", result.get(0).field());
            assertEquals("State", result.get(0).header());
        }

        @Test
        void returnsEmptyListWhenNoColumns(DataSource dataSource) throws SQLException {
            TestUtils.executeSql(dataSource, "sql/events/insert-events-with-types.sql");
            var provider = new DatabaseClientProvider(dataSource);
            JdbcProfileEventTypeRepository repository = new JdbcProfileEventTypeRepository(SQL_FORMATTER, provider);

            // ThreadAllocationStatistics has columns = NULL
            List<FieldDescription> result = repository.eventColumns(Type.fromCode("jdk.ThreadAllocationStatistics"));

            assertTrue(result.isEmpty());
        }

        @Test
        void returnsEmptyListForNonExistentType(DataSource dataSource) {
            var provider = new DatabaseClientProvider(dataSource);
            JdbcProfileEventTypeRepository repository = new JdbcProfileEventTypeRepository(SQL_FORMATTER, provider);

            List<FieldDescription> result = repository.eventColumns(Type.fromCode("jdk.NonExistent"));

            assertTrue(result.isEmpty());
        }
    }

    @Nested
    class EventSummariesWithTypesMethod {

        @Test
        void returnsSummariesForSpecifiedTypes(DataSource dataSource) throws SQLException {
            TestUtils.executeSql(dataSource, "sql/events/insert-events-with-types.sql");
            var provider = new DatabaseClientProvider(dataSource);
            JdbcProfileEventTypeRepository repository = new JdbcProfileEventTypeRepository(SQL_FORMATTER, provider);

            List<EventSummary> result = repository.eventSummaries(List.of(
                    Type.fromCode("jdk.ExecutionSample"),
                    Type.fromCode("jdk.GCPhasePause")
            ));

            assertEquals(2, result.size());
        }

        @Test
        void aggregatesSamplesAndWeight(DataSource dataSource) throws SQLException {
            TestUtils.executeSql(dataSource, "sql/events/insert-events-with-types.sql");
            var provider = new DatabaseClientProvider(dataSource);
            JdbcProfileEventTypeRepository repository = new JdbcProfileEventTypeRepository(SQL_FORMATTER, provider);

            List<EventSummary> result = repository.eventSummaries(List.of(Type.fromCode("jdk.ExecutionSample")));

            assertEquals(1, result.size());
            EventSummary summary = result.get(0);
            assertEquals("jdk.ExecutionSample", summary.name());
            assertEquals(3, summary.samples()); // 3 ExecutionSample events
        }

        @Test
        void returnsEmptyListForNonExistentTypes(DataSource dataSource) throws SQLException {
            TestUtils.executeSql(dataSource, "sql/events/insert-events-with-types.sql");
            var provider = new DatabaseClientProvider(dataSource);
            JdbcProfileEventTypeRepository repository = new JdbcProfileEventTypeRepository(SQL_FORMATTER, provider);

            List<EventSummary> result = repository.eventSummaries(List.of(Type.fromCode("jdk.NonExistent")));

            assertTrue(result.isEmpty());
        }
    }

    @Nested
    class SpanScopedEventSummariesMethod {

        private static final long A_FROM = Instant.parse("2025-01-15T10:00:00.000Z").toEpochMilli();
        private static final long A_TO = Instant.parse("2025-01-15T10:00:00.300Z").toEpochMilli();
        private static final long B_FROM = Instant.parse("2025-01-15T10:00:02.000Z").toEpochMilli();
        private static final long B_TO = Instant.parse("2025-01-15T10:00:02.200Z").toEpochMilli();

        @Test
        void countsOnlySamplesWithinSpanThreadAndWindow(DataSource dataSource) throws SQLException {
            TestUtils.executeSql(dataSource, "sql/events/insert-span-flamegraph.sql");
            JdbcProfileEventTypeRepository repository =
                    new JdbcProfileEventTypeRepository(SQL_FORMATTER, new DatabaseClientProvider(dataSource));

            List<Type> types = List.of(Type.fromCode("jdk.ExecutionSample"));
            List<SpanInterval> intervals = List.of(
                    new SpanInterval(2001, A_FROM, A_TO),
                    new SpanInterval(2002, B_FROM, B_TO));

            // Span-scoped: only the thread-2001 and thread-2002 in-window samples (GC + out-of-window excluded).
            EventSummary scoped = repository.eventSummaries(types, intervals).get(0);
            assertEquals(2, scoped.samples());

            // Profile-wide: all four execution samples.
            EventSummary all = repository.eventSummaries(types).get(0);
            assertEquals(4, all.samples());
        }
    }

    @Nested
    class EventSummariesAllMethod {

        @Test
        void returnsAllEventSummaries(DataSource dataSource) throws SQLException {
            TestUtils.executeSql(dataSource, "sql/events/insert-events-with-types.sql");
            var provider = new DatabaseClientProvider(dataSource);
            JdbcProfileEventTypeRepository repository = new JdbcProfileEventTypeRepository(SQL_FORMATTER, provider);

            List<EventSummary> result = repository.eventSummaries();

            // 4 event types in insert-events-with-types.sql
            assertEquals(4, result.size());
        }

        @Test
        void returnsEmptyListWhenNoEventTypes(DataSource dataSource) {
            var provider = new DatabaseClientProvider(dataSource);
            JdbcProfileEventTypeRepository repository = new JdbcProfileEventTypeRepository(SQL_FORMATTER, provider);

            List<EventSummary> result = repository.eventSummaries();

            assertTrue(result.isEmpty());
        }
    }
}
