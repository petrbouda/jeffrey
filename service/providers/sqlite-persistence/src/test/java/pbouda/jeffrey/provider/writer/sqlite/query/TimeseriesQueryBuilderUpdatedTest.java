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

class TimeseriesQueryBuilderUpdatedTest {

    private static final String PROFILE_ID = "test-profile-123";
    private static final Type EVENT_TYPE = Type.EXECUTION_SAMPLE;

    @Nested
    @DisplayName("Updated Column Addition Tests")
    class UpdatedColumnAdditionTests {

        @Test
        @DisplayName("Should create simple query with new column methods")
        void shouldCreateSimpleQueryWithNewColumnMethods() {
            TimeseriesQueryBuilder builder = new TimeseriesQueryBuilder(false);
            
            String query = builder
                    .withProfileId(PROFILE_ID)
                    .withEventType(EVENT_TYPE)
                    .build();

            String expectedQuery = "SELECT (events.timestamp_from_start / 1000) AS seconds, sum(events.samples) as value FROM events WHERE events.profile_id = 'test-profile-123' AND events.event_type = 'jdk.ExecutionSample' GROUP BY seconds ORDER BY seconds";

            assertEquals(expectedQuery, query);
        }

        @Test
        @DisplayName("Should include event fields when requested")
        void shouldIncludeEventFieldsWhenRequested() {
            TimeseriesQueryBuilder builder = new TimeseriesQueryBuilder(false);
            
            String query = builder
                    .withProfileId(PROFILE_ID)
                    .withEventType(EVENT_TYPE)
                    .withJsonFields(true)
                    .build();

            String expectedQuery = "SELECT (events.timestamp_from_start / 1000) AS seconds, sum(events.samples) as value, GROUP_CONCAT(DISTINCT events.event_id) as event_ids FROM events WHERE events.profile_id = 'test-profile-123' AND events.event_type = 'jdk.ExecutionSample' GROUP BY seconds ORDER BY seconds";

            assertEquals(expectedQuery, query);
        }

        @Test
        @DisplayName("Should create frame-based query with new column methods")
        void shouldCreateFrameBasedQueryWithNewColumnMethods() {
            TimeseriesQueryBuilder builder = new TimeseriesQueryBuilder(true);
            
            String query = builder
                    .withProfileId(PROFILE_ID)
                    .withEventType(EVENT_TYPE)
                    .build();

            String expectedQuery = "SELECT GROUP_CONCAT(pair, ';') AS event_values, stacktrace_id, frames  FROM (SELECT CONCAT((events.timestamp_from_start / 1000), ',', sum(events.samples)) AS pair, stacktraces.stacktrace_id, stacktraces.frames FROM events INNER JOIN stacktraces ON (events.profile_id = stacktraces.profile_id AND events.stacktrace_id = stacktraces.stacktrace_id) WHERE events.profile_id = 'test-profile-123' AND events.event_type = 'jdk.ExecutionSample' GROUP BY (events.timestamp_from_start / 1000), stacktraces.stacktrace_id ORDER BY stacktraces.stacktrace_id) GROUP BY stacktrace_id";

            assertEquals(expectedQuery, query);
        }

        @Test
        @DisplayName("Should use weight with new column methods")
        void shouldUseWeightWithNewColumnMethods() {
            TimeseriesQueryBuilder builder = new TimeseriesQueryBuilder(false);
            
            String query = builder
                    .withWeight(true)
                    .withProfileId(PROFILE_ID)
                    .withEventType(EVENT_TYPE)
                    .build();

            String expectedQuery = "SELECT (events.timestamp_from_start / 1000) AS seconds, sum(events.weight) as value FROM events WHERE events.profile_id = 'test-profile-123' AND events.event_type = 'jdk.ExecutionSample' GROUP BY seconds ORDER BY seconds";

            assertEquals(expectedQuery, query);
        }

        @Test
        @DisplayName("Should handle complex query with new column methods")
        void shouldHandleComplexQueryWithNewColumnMethods() {
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
                    .withJsonFields(true)
                    .build();

            String expectedQuery = "SELECT (events.timestamp_from_start / 1000) AS seconds, sum(events.samples) as value, GROUP_CONCAT(DISTINCT events.event_id) as event_ids FROM events INNER JOIN stacktraces ON (events.profile_id = stacktraces.profile_id AND events.stacktrace_id = stacktraces.stacktrace_id) LEFT JOIN stacktrace_tags tags ON (events.profile_id = tags.profile_id AND events.stacktrace_id = tags.stacktrace_id) WHERE events.profile_id = 'test-profile-123' AND events.event_type = 'jdk.ExecutionSample' AND stacktraces.type_id IN (100) AND (tags.tag_id NOT IN (0) OR tags.tag_id IS NULL) AND events.timestamp_from_start >= 1000 AND events.timestamp_from_start < 5000 GROUP BY seconds ORDER BY seconds";

            assertEquals(expectedQuery, query);
        }
    }

    @Nested
    @DisplayName("Column Method Benefits Tests")
    class ColumnMethodBenefitsTests {

        @Test
        @DisplayName("Should demonstrate cleaner column building")
        void shouldDemonstrateCleanerColumnBuilding() {
            TimeseriesQueryBuilder builder = new TimeseriesQueryBuilder(false);
            
            String queryWithoutFields = builder
                    .withProfileId(PROFILE_ID)
                    .withEventType(EVENT_TYPE)
                    .withJsonFields(false)
                    .build();

            String queryWithFields = new TimeseriesQueryBuilder(false)
                    .withProfileId(PROFILE_ID)
                    .withEventType(EVENT_TYPE)
                    .withJsonFields(true)
                    .build();

            assertAll(
                    () -> assertFalse(queryWithoutFields.contains("event_ids")),
                    () -> assertTrue(queryWithFields.contains("event_ids")),
                    () -> assertTrue(queryWithFields.contains("GROUP_CONCAT(DISTINCT events.event_id)"))
            );
        }

        @Test
        @DisplayName("Should maintain backward compatibility")
        void shouldMaintainBackwardCompatibility() {
            // The public API should remain exactly the same
            TimeseriesQueryBuilder builder = new TimeseriesQueryBuilder(false);
            
            // All existing methods should work the same way
            String query = builder
                    .withWeight(true)
                    .withProfileId(PROFILE_ID)
                    .withEventType(EVENT_TYPE)
                    .withTimeRange(new RelativeTimeRange(Duration.ofSeconds(1), Duration.ofSeconds(5)))
                    .filterStacktraceTypes(List.of(StacktraceType.APPLICATION))
                    .filterStacktraceTags(List.of(StacktraceTag.EXCLUDE_IDLE))
                    .withJsonFields(false)
                    .build();

            // Should generate valid SQL
            assertAll(
                    () -> assertNotNull(query),
                    () -> assertTrue(query.contains("SELECT")),
                    () -> assertTrue(query.contains("FROM events")),
                    () -> assertTrue(query.contains("WHERE")),
                    () -> assertTrue(query.contains("events.weight")),
                    () -> assertTrue(query.contains("stacktraces.type_id IN (100)")),
                    () -> assertTrue(query.contains("tags.tag_id NOT IN (0)"))
            );
        }

        @Test
        @DisplayName("Should show improved code structure")
        void shouldShowImprovedCodeStructure() {
            // The new implementation separates column building from other logic
            TimeseriesQueryBuilder simpleBuilder = new TimeseriesQueryBuilder(false);
            TimeseriesQueryBuilder frameBuilder = new TimeseriesQueryBuilder(true);
            
            String simpleQuery = simpleBuilder
                    .withProfileId(PROFILE_ID)
                    .withEventType(EVENT_TYPE)
                    .build();

            String frameQuery = frameBuilder
                    .withProfileId(PROFILE_ID)
                    .withEventType(EVENT_TYPE)
                    .build();

            assertAll(
                    () -> assertTrue(simpleQuery.contains("sum(events.samples) as value")),
                    () -> assertTrue(frameQuery.contains("CONCAT((events.timestamp_from_start / 1000)")),
                    () -> assertTrue(frameQuery.contains("stacktraces.stacktrace_id")),
                    () -> assertTrue(frameQuery.contains("stacktraces.frames"))
            );
        }
    }
}
