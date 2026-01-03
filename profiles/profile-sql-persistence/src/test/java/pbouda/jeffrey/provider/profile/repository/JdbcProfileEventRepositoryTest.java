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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import pbouda.jeffrey.provider.profile.DuckDBSQLFormatter;
import pbouda.jeffrey.provider.profile.model.AllocatingThread;
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
class JdbcProfileEventRepositoryTest {

    private static final DuckDBSQLFormatter SQL_FORMATTER = new DuckDBSQLFormatter();

    @Nested
    class LatestJsonFieldsMethod {

        @Test
        void returnsLatestFields(DatabaseClientProvider provider, DataSource dataSource) throws SQLException {
            TestUtils.executeSql(dataSource, "sql/events/insert-events-with-types.sql");
            JdbcProfileEventRepository repository = new JdbcProfileEventRepository(SQL_FORMATTER, provider);

            Optional<ObjectNode> result = repository.latestJsonFields(Type.fromCode("jdk.ExecutionSample"));

            assertTrue(result.isPresent());
            assertEquals("RUNNABLE", result.get().get("state").asText());
        }

        @Test
        void returnsEmptyWhenNoEvents(DatabaseClientProvider provider) {
            JdbcProfileEventRepository repository = new JdbcProfileEventRepository(SQL_FORMATTER, provider);

            Optional<ObjectNode> result = repository.latestJsonFields(Type.fromCode("jdk.ExecutionSample"));

            assertTrue(result.isEmpty());
        }

        @Test
        void returnsEmptyForNonExistentEventType(DatabaseClientProvider provider, DataSource dataSource) throws SQLException {
            TestUtils.executeSql(dataSource, "sql/events/insert-events-with-types.sql");
            JdbcProfileEventRepository repository = new JdbcProfileEventRepository(SQL_FORMATTER, provider);

            Optional<ObjectNode> result = repository.latestJsonFields(Type.fromCode("jdk.NonExistent"));

            assertTrue(result.isEmpty());
        }
    }

    @Nested
    class AllocatingThreadsMethod {

        @Test
        void returnsThreadsOrderedByWeight(DatabaseClientProvider provider, DataSource dataSource) throws SQLException {
            TestUtils.executeSql(dataSource, "sql/events/insert-allocating-threads.sql");
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
        void respectsLimit(DatabaseClientProvider provider, DataSource dataSource) throws SQLException {
            TestUtils.executeSql(dataSource, "sql/events/insert-allocating-threads.sql");
            JdbcProfileEventRepository repository = new JdbcProfileEventRepository(SQL_FORMATTER, provider);

            List<AllocatingThread> result = repository.allocatingThreads(2);

            assertEquals(2, result.size());
            assertEquals("main", result.get(0).threadInfo().name());
            assertEquals("worker-1", result.get(1).threadInfo().name());
        }

        @Test
        void returnsEmptyListWhenNoData(DatabaseClientProvider provider) {
            JdbcProfileEventRepository repository = new JdbcProfileEventRepository(SQL_FORMATTER, provider);

            List<AllocatingThread> result = repository.allocatingThreads(10);

            assertTrue(result.isEmpty());
        }
    }

    @Nested
    class EventsByTypeWithFieldsMethod {

        @Test
        void returnsEventsWithFields(DatabaseClientProvider provider, DataSource dataSource) throws SQLException {
            TestUtils.executeSql(dataSource, "sql/events/insert-events-with-types.sql");
            JdbcProfileEventRepository repository = new JdbcProfileEventRepository(SQL_FORMATTER, provider);

            List<JsonNode> result = repository.eventsByTypeWithFields(Type.fromCode("jdk.ExecutionSample"));

            assertEquals(3, result.size());
        }

        @Test
        void returnsEmptyListForNonExistentType(DatabaseClientProvider provider, DataSource dataSource) throws SQLException {
            TestUtils.executeSql(dataSource, "sql/events/insert-events-with-types.sql");
            JdbcProfileEventRepository repository = new JdbcProfileEventRepository(SQL_FORMATTER, provider);

            List<JsonNode> result = repository.eventsByTypeWithFields(Type.fromCode("jdk.NonExistent"));

            assertTrue(result.isEmpty());
        }
    }

    @Nested
    class ContainsEventTypeMethod {

        @Test
        void returnsTrueWhenEventTypeExists(DatabaseClientProvider provider, DataSource dataSource) throws SQLException {
            TestUtils.executeSql(dataSource, "sql/events/insert-events-with-types.sql");
            JdbcProfileEventRepository repository = new JdbcProfileEventRepository(SQL_FORMATTER, provider);

            assertTrue(repository.containsEventType(Type.fromCode("jdk.ExecutionSample")));
        }

        @Test
        void returnsFalseWhenEventTypeNotExists(DatabaseClientProvider provider, DataSource dataSource) throws SQLException {
            TestUtils.executeSql(dataSource, "sql/events/insert-events-with-types.sql");
            JdbcProfileEventRepository repository = new JdbcProfileEventRepository(SQL_FORMATTER, provider);

            assertFalse(repository.containsEventType(Type.fromCode("jdk.NonExistent")));
        }

        @Test
        void returnsFalseWhenNoEvents(DatabaseClientProvider provider) {
            JdbcProfileEventRepository repository = new JdbcProfileEventRepository(SQL_FORMATTER, provider);

            assertFalse(repository.containsEventType(Type.fromCode("jdk.ExecutionSample")));
        }
    }
}
