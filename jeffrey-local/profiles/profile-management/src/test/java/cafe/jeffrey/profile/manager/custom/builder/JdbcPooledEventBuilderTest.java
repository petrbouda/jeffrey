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

package cafe.jeffrey.profile.manager.custom.builder;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import cafe.jeffrey.profile.manager.custom.builder.JdbcPooledEventBuilder.Pool;
import cafe.jeffrey.profile.manager.custom.builder.JdbcPooledEventBuilder.PoolEvent;
import cafe.jeffrey.provider.profile.model.GenericRecord;
import cafe.jeffrey.shared.common.model.Type;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("JdbcPooledEventBuilder")
class JdbcPooledEventBuilderTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static GenericRecord createRecord(Type type, String poolName, String elapsedTime) {
        ObjectNode fields = MAPPER.createObjectNode();
        fields.put("poolName", poolName);
        if (elapsedTime != null) {
            fields.put("elapsedTime", elapsedTime);
        }
        return new GenericRecord(
                type,
                type.toString(),
                Instant.EPOCH,
                Duration.ZERO,
                Duration.ZERO,
                null,
                null,
                1,
                0,
                fields
        );
    }

    @Nested
    @DisplayName("Single pool with a single event")
    class SinglePoolSingleEvent {

        @Test
        @DisplayName("One record produces one pool with one event having correct counter, accumulated, min, and max")
        void singleRecordProducesOnePoolWithOneEvent() {
            var builder = new JdbcPooledEventBuilder();
            builder.onRecord(createRecord(Type.POOLED_JDBC_CONNECTION_ACQUIRED, "pool1", "1000000"));

            List<Pool> pools = builder.build();

            assertEquals(1, pools.size());

            Pool pool = pools.getFirst();
            assertEquals("pool1", pool.poolName());
            assertEquals(1, pool.events().size());

            PoolEvent event = pool.events().get(Type.POOLED_JDBC_CONNECTION_ACQUIRED);
            assertNotNull(event);
            assertEquals(1, event.getCounter());
            assertEquals(Duration.ofNanos(1_000_000), event.getAccumulated());
            assertEquals(1_000_000, event.getMin());
            assertEquals(1_000_000, event.getMax());
        }
    }

    @Nested
    @DisplayName("Single pool with multiple event types")
    class SinglePoolMultipleEvents {

        @Test
        @DisplayName("Three records with different types produce one pool with three event entries")
        void differentTypesCreateSeparateEventEntries() {
            var builder = new JdbcPooledEventBuilder();
            builder.onRecord(createRecord(Type.POOLED_JDBC_CONNECTION_ACQUIRED, "pool1", "100"));
            builder.onRecord(createRecord(Type.POOLED_JDBC_CONNECTION_BORROWED, "pool1", "200"));
            builder.onRecord(createRecord(Type.POOLED_JDBC_CONNECTION_CREATED, "pool1", "300"));

            List<Pool> pools = builder.build();

            assertEquals(1, pools.size());

            Map<Type, PoolEvent> events = pools.getFirst().events();
            assertEquals(3, events.size());
            assertNotNull(events.get(Type.POOLED_JDBC_CONNECTION_ACQUIRED));
            assertNotNull(events.get(Type.POOLED_JDBC_CONNECTION_BORROWED));
            assertNotNull(events.get(Type.POOLED_JDBC_CONNECTION_CREATED));
        }
    }

    @Nested
    @DisplayName("Single pool accumulation for same event type")
    class SinglePoolAccumulation {

        @Test
        @DisplayName("Three records of same type accumulate counter, duration, min, and max correctly")
        void accumulatesCounterDurationMinMax() {
            var builder = new JdbcPooledEventBuilder();
            builder.onRecord(createRecord(Type.POOLED_JDBC_CONNECTION_ACQUIRED, "pool1", "100"));
            builder.onRecord(createRecord(Type.POOLED_JDBC_CONNECTION_ACQUIRED, "pool1", "200"));
            builder.onRecord(createRecord(Type.POOLED_JDBC_CONNECTION_ACQUIRED, "pool1", "300"));

            List<Pool> pools = builder.build();

            assertEquals(1, pools.size());

            PoolEvent event = pools.getFirst().events().get(Type.POOLED_JDBC_CONNECTION_ACQUIRED);
            assertNotNull(event);
            assertEquals(3, event.getCounter());
            assertEquals(Duration.ofNanos(600), event.getAccumulated());
            assertEquals(100, event.getMin());
            assertEquals(300, event.getMax());
        }
    }

    @Nested
    @DisplayName("Multiple pools lookup via HashMap")
    class MultiplePoolsLookup {

        @Test
        @DisplayName("Records for three different pools produce three separate pool entries")
        void recordsForDifferentPoolsProduceSeparateEntries() {
            var builder = new JdbcPooledEventBuilder();
            builder.onRecord(createRecord(Type.POOLED_JDBC_CONNECTION_ACQUIRED, "pool-a", "100"));
            builder.onRecord(createRecord(Type.POOLED_JDBC_CONNECTION_ACQUIRED, "pool-b", "200"));
            builder.onRecord(createRecord(Type.POOLED_JDBC_CONNECTION_ACQUIRED, "pool-c", "300"));

            List<Pool> pools = builder.build();

            assertEquals(3, pools.size());

            Map<String, Pool> poolsByName = pools.stream()
                    .collect(java.util.stream.Collectors.toMap(Pool::poolName, p -> p));

            assertTrue(poolsByName.containsKey("pool-a"));
            assertTrue(poolsByName.containsKey("pool-b"));
            assertTrue(poolsByName.containsKey("pool-c"));

            assertEquals(100, poolsByName.get("pool-a").events().get(Type.POOLED_JDBC_CONNECTION_ACQUIRED).getMin());
            assertEquals(200, poolsByName.get("pool-b").events().get(Type.POOLED_JDBC_CONNECTION_ACQUIRED).getMin());
            assertEquals(300, poolsByName.get("pool-c").events().get(Type.POOLED_JDBC_CONNECTION_ACQUIRED).getMin());
        }
    }

    @Nested
    @DisplayName("Null elapsedTime handling")
    class NullElapsedTime {

        @Test
        @DisplayName("Record with no elapsedTime field defaults elapsed to zero")
        void missingElapsedTimeDefaultsToZero() {
            var builder = new JdbcPooledEventBuilder();
            builder.onRecord(createRecord(Type.POOLED_JDBC_CONNECTION_ACQUIRED, "pool1", null));

            List<Pool> pools = builder.build();

            assertEquals(1, pools.size());

            PoolEvent event = pools.getFirst().events().get(Type.POOLED_JDBC_CONNECTION_ACQUIRED);
            assertNotNull(event);
            assertEquals(1, event.getCounter());
            assertEquals(Duration.ofNanos(0), event.getAccumulated());
            assertEquals(0, event.getMin());
            assertEquals(0, event.getMax());
        }
    }
}
