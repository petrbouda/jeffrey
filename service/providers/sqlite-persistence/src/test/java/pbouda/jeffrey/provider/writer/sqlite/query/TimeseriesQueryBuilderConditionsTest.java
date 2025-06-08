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

package pbouda.jeffrey.provider.writer.sqlite.query;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import pbouda.jeffrey.common.model.StacktraceTag;
import pbouda.jeffrey.common.model.StacktraceType;
import pbouda.jeffrey.common.model.Type;
import pbouda.jeffrey.common.model.time.RelativeTimeRange;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TimeseriesQueryBuilderConditionsTest {

    private static final String PROFILE_ID = "test-profile-123";
    private static final Type EVENT_TYPE = Type.EXECUTION_SAMPLE;

    @Nested
    @DisplayName("Condition Objects Tests")
    class ConditionObjectsTests {

        @Test
        @DisplayName("Should use eq() condition for basic where clauses")
        void shouldUseEqConditionForBasicWhereClauses() {
            TimeseriesQueryBuilder builder = new TimeseriesQueryBuilder(false);
            
            String query = builder
                    .withProfileId(PROFILE_ID)
                    .withEventType(EVENT_TYPE)
                    .build();

            String expectedQuery = "SELECT (events.timestamp_from_start / 1000) AS seconds, sum(events.samples) as value FROM events WHERE events.profile_id = 'test-profile-123' AND events.event_type = 'jdk.ExecutionSample' GROUP BY seconds ORDER BY seconds";

            assertEquals(expectedQuery, query);
        }

        @Test
        @DisplayName("Should use gte() and lt() conditions for time range")
        void shouldUseGteAndLtConditionsForTimeRange() {
            TimeseriesQueryBuilder builder = new TimeseriesQueryBuilder(false);
            RelativeTimeRange timeRange = new RelativeTimeRange(Duration.ofSeconds(1), Duration.ofSeconds(5));
            
            String query = builder
                    .withProfileId(PROFILE_ID)
                    .withEventType(EVENT_TYPE)
                    .withTimeRange(timeRange)
                    .build();

            String expectedQuery = "SELECT (events.timestamp_from_start / 1000) AS seconds, sum(events.samples) as value FROM events WHERE events.profile_id = 'test-profile-123' AND events.event_type = 'jdk.ExecutionSample' AND events.timestamp_from_start >= 1000 AND events.timestamp_from_start < 5000 GROUP BY seconds ORDER BY seconds";

            assertEquals(expectedQuery, query);
        }

        @Test
        @DisplayName("Should use in() condition for stacktrace types")
        void shouldUseInConditionForStacktraceTypes() {
            TimeseriesQueryBuilder builder = new TimeseriesQueryBuilder(false);
            List<StacktraceType> stacktraceTypes = List.of(StacktraceType.APPLICATION);
            
            String query = builder
                    .withProfileId(PROFILE_ID)
                    .withEventType(EVENT_TYPE)
                    .filterStacktraceTypes(stacktraceTypes)
                    .build();

            String expectedQuery = "SELECT (events.timestamp_from_start / 1000) AS seconds, sum(events.samples) as value FROM events INNER JOIN stacktraces ON (events.profile_id = stacktraces.profile_id AND events.stacktrace_id = stacktraces.stacktrace_id) WHERE events.profile_id = 'test-profile-123' AND events.event_type = 'jdk.ExecutionSample' AND stacktraces.type_id IN (100) GROUP BY seconds ORDER BY seconds";

            assertEquals(expectedQuery, query);
        }

        @Test
        @DisplayName("Should use notInOrNull() condition for excluded stacktrace tags")
        void shouldUseNotInOrNullConditionForExcludedStacktraceTags() {
            TimeseriesQueryBuilder builder = new TimeseriesQueryBuilder(false);
            List<StacktraceTag> stacktraceTags = List.of(StacktraceTag.EXCLUDE_IDLE);
            
            String query = builder
                    .withProfileId(PROFILE_ID)
                    .withEventType(EVENT_TYPE)
                    .filterStacktraceTags(stacktraceTags)
                    .build();

            String expectedQuery = "SELECT (events.timestamp_from_start / 1000) AS seconds, sum(events.samples) as value FROM events LEFT JOIN stacktrace_tags tags ON (events.profile_id = tags.profile_id AND events.stacktrace_id = tags.stacktrace_id) WHERE events.profile_id = 'test-profile-123' AND events.event_type = 'jdk.ExecutionSample' AND (tags.tag_id NOT IN (0) OR tags.tag_id IS NULL) GROUP BY seconds ORDER BY seconds";

            assertEquals(expectedQuery, query);
        }

        @Test
        @DisplayName("Should combine multiple condition types correctly")
        void shouldCombineMultipleConditionTypesCorrectly() {
            TimeseriesQueryBuilder builder = new TimeseriesQueryBuilder(false);
            RelativeTimeRange timeRange = new RelativeTimeRange(Duration.ofSeconds(1), Duration.ofSeconds(5));
            List<StacktraceType> stacktraceTypes = List.of(StacktraceType.APPLICATION);
            List<StacktraceTag> stacktraceTags = List.of(StacktraceTag.EXCLUDE_IDLE);
            
            String query = builder
                    .withProfileId(PROFILE_ID)
                    .withEventType(EVENT_TYPE)
                    .withTimeRange(timeRange)
                    .filterStacktraceTypes(stacktraceTypes)
                    .filterStacktraceTags(stacktraceTags)
                    .build();

            String expectedQuery = "SELECT (events.timestamp_from_start / 1000) AS seconds, sum(events.samples) as value FROM events INNER JOIN stacktraces ON (events.profile_id = stacktraces.profile_id AND events.stacktrace_id = stacktraces.stacktrace_id) LEFT JOIN stacktrace_tags tags ON (events.profile_id = tags.profile_id AND events.stacktrace_id = tags.stacktrace_id) WHERE events.profile_id = 'test-profile-123' AND events.event_type = 'jdk.ExecutionSample' AND stacktraces.type_id IN (100) AND (tags.tag_id NOT IN (0) OR tags.tag_id IS NULL) AND events.timestamp_from_start >= 1000 AND events.timestamp_from_start < 5000 GROUP BY seconds ORDER BY seconds";

            assertEquals(expectedQuery, query);
        }

        @Test
        @DisplayName("Should work with frame-based queries using conditions")
        void shouldWorkWithFrameBasedQueriesUsingConditions() {
            TimeseriesQueryBuilder builder = new TimeseriesQueryBuilder(true);
            
            String query = builder
                    .withProfileId(PROFILE_ID)
                    .withEventType(EVENT_TYPE)
                    .build();

            String expectedQuery = "SELECT GROUP_CONCAT(pair, ';') AS event_values, stacktrace_id, frames  FROM (SELECT CONCAT((events.timestamp_from_start / 1000), ',', sum(events.samples)) AS pair, stacktraces.stacktrace_id, stacktraces.frames FROM events INNER JOIN stacktraces ON (events.profile_id = stacktraces.profile_id AND events.stacktrace_id = stacktraces.stacktrace_id) WHERE events.profile_id = 'test-profile-123' AND events.event_type = 'jdk.ExecutionSample' GROUP BY (events.timestamp_from_start / 1000), stacktraces.stacktrace_id ORDER BY stacktraces.stacktrace_id) GROUP BY stacktrace_id";

            assertEquals(expectedQuery, query);
        }

        @Test
        @DisplayName("Should use weight field with conditions correctly")
        void shouldUseWeightFieldWithConditionsCorrectly() {
            TimeseriesQueryBuilder builder = new TimeseriesQueryBuilder(false);
            
            String query = builder
                    .withWeight(true)
                    .withProfileId(PROFILE_ID)
                    .withEventType(EVENT_TYPE)
                    .build();

            String expectedQuery = "SELECT (events.timestamp_from_start / 1000) AS seconds, sum(events.weight) as value FROM events WHERE events.profile_id = 'test-profile-123' AND events.event_type = 'jdk.ExecutionSample' GROUP BY seconds ORDER BY seconds";

            assertEquals(expectedQuery, query);
        }
    }

    @Nested
    @DisplayName("Backward Compatibility Tests")
    class BackwardCompatibilityTests {

        @Test
        @DisplayName("Should maintain same output as string-based implementation")
        void shouldMaintainSameOutputAsStringBasedImplementation() {
            // This verifies that using Condition objects produces identical SQL
            // to the previous string-based approach
            TimeseriesQueryBuilder builder = new TimeseriesQueryBuilder(false);
            RelativeTimeRange timeRange = new RelativeTimeRange(Duration.ofSeconds(10), Duration.ofSeconds(20));
            
            String query = builder
                    .withWeight(true)
                    .withProfileId("complex-profile-456")
                    .withEventType(Type.OBJECT_ALLOCATION_IN_NEW_TLAB)
                    .withTimeRange(timeRange)
                    .withJsonFields(true)
                    .build();

            // This should be exactly the same as before, just using Condition objects internally
            String expectedQuery = "SELECT (events.timestamp_from_start / 1000) AS seconds, sum(events.weight) as value, GROUP_CONCAT(DISTINCT events.event_id) as event_ids FROM events WHERE events.profile_id = 'complex-profile-456' AND events.event_type = 'jdk.ObjectAllocationInNewTLAB' AND events.timestamp_from_start >= 10000 AND events.timestamp_from_start < 20000 GROUP BY seconds ORDER BY seconds";

            assertEquals(expectedQuery, query);
        }

        @Test
        @DisplayName("Should handle null values correctly with conditions")
        void shouldHandleNullValuesCorrectlyWithConditions() {
            TimeseriesQueryBuilder builder = new TimeseriesQueryBuilder(false);
            
            String query = builder
                    .withProfileId(PROFILE_ID)
                    .withEventType(EVENT_TYPE)
                    .withTimeRange(null)  // null time range
                    .filterStacktraceTypes(null)  // null stacktrace types
                    .filterStacktraceTags(null)  // null stacktrace tags
                    .build();

            String expectedQuery = "SELECT (events.timestamp_from_start / 1000) AS seconds, sum(events.samples) as value FROM events WHERE events.profile_id = 'test-profile-123' AND events.event_type = 'jdk.ExecutionSample' GROUP BY seconds ORDER BY seconds";

            assertEquals(expectedQuery, query);
        }

        @Test
        @DisplayName("Should handle empty collections correctly with conditions")
        void shouldHandleEmptyCollectionsCorrectlyWithConditions() {
            TimeseriesQueryBuilder builder = new TimeseriesQueryBuilder(false);
            
            String query = builder
                    .withProfileId(PROFILE_ID)
                    .withEventType(EVENT_TYPE)
                    .filterStacktraceTypes(List.of())  // empty list
                    .filterStacktraceTags(List.of())  // empty list
                    .build();

            String expectedQuery = "SELECT (events.timestamp_from_start / 1000) AS seconds, sum(events.samples) as value FROM events WHERE events.profile_id = 'test-profile-123' AND events.event_type = 'jdk.ExecutionSample' GROUP BY seconds ORDER BY seconds";

            assertEquals(expectedQuery, query);
        }
    }

    @Nested
    @DisplayName("Code Quality Tests")
    class CodeQualityTests {

        @Test
        @DisplayName("Should demonstrate improved code structure with conditions")
        void shouldDemonstrateImprovedCodeStructureWithConditions() {
            // Using Condition objects provides better type safety and readability
            TimeseriesQueryBuilder builder = new TimeseriesQueryBuilder(false);
            
            String query = builder
                    .withProfileId(PROFILE_ID)
                    .withEventType(EVENT_TYPE)
                    .build();

            assertAll(
                    () -> assertNotNull(query),
                    () -> assertTrue(query.contains("WHERE")),
                    () -> assertTrue(query.contains("events.profile_id = 'test-profile-123'")),
                    () -> assertTrue(query.contains("events.event_type = 'jdk.ExecutionSample'")),
                    () -> assertTrue(query.contains("SELECT")),
                    () -> assertTrue(query.contains("FROM events")),
                    () -> assertTrue(query.contains("GROUP BY seconds")),
                    () -> assertTrue(query.contains("ORDER BY seconds"))
            );
        }

        @Test
        @DisplayName("Should support method chaining with condition objects")
        void shouldSupportMethodChainingWithConditionObjects() {
            // Verify that the fluent API still works correctly
            TimeseriesQueryBuilder builder = new TimeseriesQueryBuilder(false);
            
            String query = builder
                    .withWeight(false)
                    .withProfileId(PROFILE_ID)
                    .withEventType(EVENT_TYPE)
                    .withJsonFields(false)
                    .filterStacktraceTypes(List.of(StacktraceType.APPLICATION, StacktraceType.JVM))
                    .build();

            assertTrue(query.contains("stacktraces.type_id IN (100, 0)"));
        }
    }
}
