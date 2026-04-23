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

package pbouda.jeffrey.profile.manager.custom.builder;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import pbouda.jeffrey.provider.profile.model.GenericRecord;
import pbouda.jeffrey.shared.common.model.Type;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("JdbcPoolStatisticsBuilder")
class JdbcPoolStatisticsBuilderTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static GenericRecord createRecord(String poolName, int active, int idle, int pendingThreads, int max, int min) {
        ObjectNode fields = MAPPER.createObjectNode();
        fields.put("poolName", poolName);
        fields.put("active", String.valueOf(active));
        fields.put("idle", String.valueOf(idle));
        fields.put("pendingThreads", String.valueOf(pendingThreads));
        fields.put("max", String.valueOf(max));
        fields.put("min", String.valueOf(min));

        return new GenericRecord(
                Type.JDBC_POOL_STATISTICS,
                "JdbcPoolStatistics",
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
    @DisplayName("Single pool with a single record")
    class SinglePoolSingleRecord {

        @Test
        @DisplayName("Pool is created with correct initial values from one record")
        void poolCreatedWithCorrectValues() {
            var builder = new JdbcPoolStatisticsBuilder();
            builder.onRecord(createRecord("HikariPool-1", 5, 10, 0, 20, 2));

            List<JdbcPoolStatisticsBuilder.PoolStats> result = builder.build();

            assertEquals(1, result.size());

            JdbcPoolStatisticsBuilder.PoolStats pool = result.getFirst();
            assertEquals("HikariPool-1", pool.poolName());
            assertEquals(1, pool.counter().get());
            assertEquals(5, pool.maxActive().get());
            assertEquals(5, pool.cumulatedActive().get());
            assertEquals(15, pool.maxConnections().get(), "maxConnections should be active + idle");
            assertEquals(0, pool.maxPendingThreads().get());
            assertEquals(20, pool.maxConfigConnections());
            assertEquals(2, pool.minConfigConnections());
            assertEquals(0, pool.pendingThreadsPeriods().get());
        }
    }

    @Nested
    @DisplayName("Single pool with multiple records")
    class SinglePoolMultipleRecords {

        @Test
        @DisplayName("Counter, maxActive, and cumulatedActive are correctly aggregated across records")
        void aggregatesAcrossMultipleRecords() {
            var builder = new JdbcPoolStatisticsBuilder();
            builder.onRecord(createRecord("HikariPool-1", 3, 7, 0, 20, 2));
            builder.onRecord(createRecord("HikariPool-1", 8, 2, 0, 20, 2));
            builder.onRecord(createRecord("HikariPool-1", 5, 5, 0, 20, 2));

            List<JdbcPoolStatisticsBuilder.PoolStats> result = builder.build();

            assertEquals(1, result.size());

            JdbcPoolStatisticsBuilder.PoolStats pool = result.getFirst();
            assertEquals("HikariPool-1", pool.poolName());
            assertEquals(3, pool.counter().get());
            assertEquals(8, pool.maxActive().get(), "maxActive should be the highest active value across all records");
            assertEquals(16, pool.cumulatedActive().get(), "cumulatedActive should be the sum of all active values: 3 + 8 + 5");
            assertEquals(10, pool.maxConnections().get(), "maxConnections should be the max of (active + idle) across all records");
        }
    }

    @Nested
    @DisplayName("Multiple pools lookup")
    class MultiplePoolsLookup {

        @Test
        @DisplayName("Records for different pools are correctly separated into distinct PoolStats entries")
        void poolsAreSeparated() {
            var builder = new JdbcPoolStatisticsBuilder();
            builder.onRecord(createRecord("HikariPool-1", 4, 6, 0, 20, 2));
            builder.onRecord(createRecord("HikariPool-2", 7, 3, 1, 30, 5));
            builder.onRecord(createRecord("HikariPool-1", 6, 4, 0, 20, 2));

            List<JdbcPoolStatisticsBuilder.PoolStats> result = builder.build();

            assertEquals(2, result.size());

            JdbcPoolStatisticsBuilder.PoolStats pool1 = result.stream()
                    .filter(p -> "HikariPool-1".equals(p.poolName()))
                    .findFirst()
                    .orElseThrow();

            JdbcPoolStatisticsBuilder.PoolStats pool2 = result.stream()
                    .filter(p -> "HikariPool-2".equals(p.poolName()))
                    .findFirst()
                    .orElseThrow();

            assertEquals(2, pool1.counter().get());
            assertEquals(6, pool1.maxActive().get());
            assertEquals(10, pool1.cumulatedActive().get(), "Pool-1 cumulatedActive: 4 + 6");
            assertEquals(20, pool1.maxConfigConnections());

            assertEquals(1, pool2.counter().get());
            assertEquals(7, pool2.maxActive().get());
            assertEquals(7, pool2.cumulatedActive().get());
            assertEquals(30, pool2.maxConfigConnections());
            assertEquals(5, pool2.minConfigConnections());
        }
    }

    @Nested
    @DisplayName("Pending threads tracking")
    class PendingThreadsTracking {

        @Test
        @DisplayName("pendingThreadsPeriods counts only records where pendingThreads > 0")
        void countsOnlyNonZeroPendingThreads() {
            var builder = new JdbcPoolStatisticsBuilder();
            builder.onRecord(createRecord("HikariPool-1", 5, 5, 2, 20, 2));
            builder.onRecord(createRecord("HikariPool-1", 6, 4, 0, 20, 2));
            builder.onRecord(createRecord("HikariPool-1", 7, 3, 3, 20, 2));
            builder.onRecord(createRecord("HikariPool-1", 4, 6, 0, 20, 2));

            List<JdbcPoolStatisticsBuilder.PoolStats> result = builder.build();

            assertEquals(1, result.size());

            JdbcPoolStatisticsBuilder.PoolStats pool = result.getFirst();
            assertEquals(4, pool.counter().get());
            assertEquals(2, pool.pendingThreadsPeriods().get(),
                    "Only the first record (pendingThreads=2) and third record (pendingThreads=3) should count");
            assertEquals(3, pool.maxPendingThreads().get(),
                    "maxPendingThreads should be the highest pendingThreads value across all records");
        }
    }
}
