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

package pbouda.jeffrey.provider.profile.repository;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import pbouda.jeffrey.provider.profile.DuckDBSQLFormatter;
import pbouda.jeffrey.provider.profile.model.EventTypeWithFields;
import pbouda.jeffrey.provider.profile.model.FieldDescription;
import pbouda.jeffrey.shared.common.model.EventSummary;
import pbouda.jeffrey.shared.common.model.Type;
import pbouda.jeffrey.shared.persistence.client.DatabaseClientProvider;
import pbouda.jeffrey.test.DuckDBTest;
import pbouda.jeffrey.test.TestUtils;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DuckDBTest(migration = "classpath:db/migration/profile")
class JdbcProfileEventTypeRepositoryTest {

    private static final DuckDBSQLFormatter SQL_FORMATTER = new DuckDBSQLFormatter();

    @Nested
    class SingleFieldsByEventTypeMethod {

        @Test
        void returnsEventTypeWithFields(DatabaseClientProvider provider, DataSource dataSource) throws SQLException {
            TestUtils.executeSql(dataSource, "sql/events/insert-events-with-types.sql");
            JdbcProfileEventTypeRepository repository = new JdbcProfileEventTypeRepository(SQL_FORMATTER, provider);

            Optional<EventTypeWithFields> result = repository.singleFieldsByEventType(Type.fromCode("jdk.ExecutionSample"));

            assertTrue(result.isPresent());
            assertEquals("jdk.ExecutionSample", result.get().name());
            assertEquals("Execution Sample", result.get().label());
            assertNotNull(result.get().content());
        }

        @Test
        void returnsEmptyWhenNoEventsForType(DatabaseClientProvider provider, DataSource dataSource) throws SQLException {
            TestUtils.executeSql(dataSource, "sql/events/insert-events-with-types.sql");
            JdbcProfileEventTypeRepository repository = new JdbcProfileEventTypeRepository(SQL_FORMATTER, provider);

            // ThreadAllocationStatistics has no events with fields in insert-events-with-types.sql
            Optional<EventTypeWithFields> result = repository.singleFieldsByEventType(Type.fromCode("jdk.ThreadAllocationStatistics"));

            assertTrue(result.isEmpty());
        }

        @Test
        void returnsEmptyForNonExistentType(DatabaseClientProvider provider) {
            JdbcProfileEventTypeRepository repository = new JdbcProfileEventTypeRepository(SQL_FORMATTER, provider);

            Optional<EventTypeWithFields> result = repository.singleFieldsByEventType(Type.fromCode("jdk.NonExistent"));

            assertTrue(result.isEmpty());
        }
    }

    @Nested
    class EventColumnsMethod {

        @Test
        void returnsColumnsFromDatabase(DatabaseClientProvider provider, DataSource dataSource) throws SQLException {
            TestUtils.executeSql(dataSource, "sql/events/insert-events-with-types.sql");
            JdbcProfileEventTypeRepository repository = new JdbcProfileEventTypeRepository(SQL_FORMATTER, provider);

            List<FieldDescription> result = repository.eventColumns(Type.fromCode("jdk.ExecutionSample"));

            assertFalse(result.isEmpty());
            assertEquals("state", result.get(0).field());
            assertEquals("State", result.get(0).header());
        }

        @Test
        void returnsEmptyListWhenNoColumns(DatabaseClientProvider provider, DataSource dataSource) throws SQLException {
            TestUtils.executeSql(dataSource, "sql/events/insert-events-with-types.sql");
            JdbcProfileEventTypeRepository repository = new JdbcProfileEventTypeRepository(SQL_FORMATTER, provider);

            // ThreadAllocationStatistics has columns = NULL
            List<FieldDescription> result = repository.eventColumns(Type.fromCode("jdk.ThreadAllocationStatistics"));

            assertTrue(result.isEmpty());
        }

        @Test
        void returnsEmptyListForNonExistentType(DatabaseClientProvider provider) {
            JdbcProfileEventTypeRepository repository = new JdbcProfileEventTypeRepository(SQL_FORMATTER, provider);

            List<FieldDescription> result = repository.eventColumns(Type.fromCode("jdk.NonExistent"));

            assertTrue(result.isEmpty());
        }
    }

    @Nested
    class EventSummariesWithTypesMethod {

        @Test
        void returnsSummariesForSpecifiedTypes(DatabaseClientProvider provider, DataSource dataSource) throws SQLException {
            TestUtils.executeSql(dataSource, "sql/events/insert-events-with-types.sql");
            JdbcProfileEventTypeRepository repository = new JdbcProfileEventTypeRepository(SQL_FORMATTER, provider);

            List<EventSummary> result = repository.eventSummaries(List.of(
                    Type.fromCode("jdk.ExecutionSample"),
                    Type.fromCode("jdk.GCPhasePause")
            ));

            assertEquals(2, result.size());
        }

        @Test
        void aggregatesSamplesAndWeight(DatabaseClientProvider provider, DataSource dataSource) throws SQLException {
            TestUtils.executeSql(dataSource, "sql/events/insert-events-with-types.sql");
            JdbcProfileEventTypeRepository repository = new JdbcProfileEventTypeRepository(SQL_FORMATTER, provider);

            List<EventSummary> result = repository.eventSummaries(List.of(Type.fromCode("jdk.ExecutionSample")));

            assertEquals(1, result.size());
            EventSummary summary = result.get(0);
            assertEquals("jdk.ExecutionSample", summary.name());
            assertEquals(3, summary.samples()); // 3 ExecutionSample events
        }

        @Test
        void returnsEmptyListForNonExistentTypes(DatabaseClientProvider provider, DataSource dataSource) throws SQLException {
            TestUtils.executeSql(dataSource, "sql/events/insert-events-with-types.sql");
            JdbcProfileEventTypeRepository repository = new JdbcProfileEventTypeRepository(SQL_FORMATTER, provider);

            List<EventSummary> result = repository.eventSummaries(List.of(Type.fromCode("jdk.NonExistent")));

            assertTrue(result.isEmpty());
        }
    }

    @Nested
    class EventSummariesAllMethod {

        @Test
        void returnsAllEventSummaries(DatabaseClientProvider provider, DataSource dataSource) throws SQLException {
            TestUtils.executeSql(dataSource, "sql/events/insert-events-with-types.sql");
            JdbcProfileEventTypeRepository repository = new JdbcProfileEventTypeRepository(SQL_FORMATTER, provider);

            List<EventSummary> result = repository.eventSummaries();

            // 4 event types in insert-events-with-types.sql
            assertEquals(4, result.size());
        }

        @Test
        void returnsEmptyListWhenNoEventTypes(DatabaseClientProvider provider) {
            JdbcProfileEventTypeRepository repository = new JdbcProfileEventTypeRepository(SQL_FORMATTER, provider);

            List<EventSummary> result = repository.eventSummaries();

            assertTrue(result.isEmpty());
        }
    }
}
