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

class TimeseriesQueryBuilderTest {

    private static final String PROFILE_ID = "test-profile-123";
    private static final Type EVENT_TYPE = Type.EXECUTION_SAMPLE;

    @Nested
    @DisplayName("Constructor Tests")
    class ConstructorTests {

        @Test
        @DisplayName("Should create simple timeseries query when needFrames is false")
        void shouldCreateSimpleTimeseriesQuery() {
            TimeseriesQueryBuilder builder = new TimeseriesQueryBuilder(false);
            
            String query = builder
                    .withProfileId(PROFILE_ID)
                    .withEventType(EVENT_TYPE)
                    .build();

            String expectedQuery = "SELECT (events.timestamp_from_start / 1000) AS seconds, sum(events.samples) as value FROM events WHERE events.profile_id = 'test-profile-123' AND events.event_type = 'jdk.ExecutionSample' GROUP BY seconds ORDER BY seconds";

            assertEquals(expectedQuery, query);
        }

        @Test
        @DisplayName("Should create frame-based timeseries query when needFrames is true")
        void shouldCreateFrameBasedTimeseriesQuery() {
            TimeseriesQueryBuilder builder = new TimeseriesQueryBuilder(true);
            
            String query = builder
                    .withProfileId(PROFILE_ID)
                    .withEventType(EVENT_TYPE)
                    .build();

            String expectedQuery = "SELECT GROUP_CONCAT(pair, ';') AS event_values, stacktrace_id, frames  FROM (SELECT CONCAT((events.timestamp_from_start / 1000), ',', sum(events.samples)) AS pair, stacktraces.stacktrace_id, stacktraces.frames FROM events INNER JOIN stacktraces ON (events.profile_id = stacktraces.profile_id AND events.stacktrace_id = stacktraces.stacktrace_id) WHERE events.profile_id = 'test-profile-123' AND events.event_type = 'jdk.ExecutionSample' GROUP BY (events.timestamp_from_start / 1000), stacktraces.stacktrace_id ORDER BY stacktraces.stacktrace_id) GROUP BY stacktrace_id";

            assertEquals(expectedQuery, query);
        }
    }

    @Nested
    @DisplayName("Weight Configuration Tests")
    class WeightConfigurationTests {

        @Test
        @DisplayName("Should use samples by default")
        void shouldUseSamplesByDefault() {
            TimeseriesQueryBuilder builder = new TimeseriesQueryBuilder(false);
            
            String query = builder
                    .withProfileId(PROFILE_ID)
                    .withEventType(EVENT_TYPE)
                    .build();

            assertTrue(query.contains("events.samples"));
            assertFalse(query.contains("events.weight"));
        }

        @Test
        @DisplayName("Should use weight when explicitly enabled")
        void shouldUseWeightWhenEnabled() {
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
        @DisplayName("Should use samples when weight is explicitly disabled")
        void shouldUseSamplesWhenWeightDisabled() {
            TimeseriesQueryBuilder builder = new TimeseriesQueryBuilder(false);
            
            String query = builder
                    .withWeight(false)
                    .withProfileId(PROFILE_ID)
                    .withEventType(EVENT_TYPE)
                    .build();

            assertTrue(query.contains("events.samples"));
            assertFalse(query.contains("events.weight"));
        }
    }

    @Nested
    @DisplayName("Profile and Event Type Tests")
    class ProfileAndEventTypeTests {

        @Test
        @DisplayName("Should include profile ID in WHERE clause")
        void shouldIncludeProfileIdInWhereClause() {
            TimeseriesQueryBuilder builder = new TimeseriesQueryBuilder(false);
            
            String query = builder
                    .withProfileId(PROFILE_ID)
                    .withEventType(EVENT_TYPE)
                    .build();

            assertTrue(query.contains("events.profile_id = '" + PROFILE_ID + "'"));
        }

        @Test
        @DisplayName("Should include event type in WHERE clause")
        void shouldIncludeEventTypeInWhereClause() {
            TimeseriesQueryBuilder builder = new TimeseriesQueryBuilder(false);
            
            String query = builder
                    .withProfileId(PROFILE_ID)
                    .withEventType(EVENT_TYPE)
                    .build();

            assertTrue(query.contains("events.event_type = '" + EVENT_TYPE.code() + "'"));
        }

        @Test
        @DisplayName("Should work with different event types")
        void shouldWorkWithDifferentEventTypes() {
            TimeseriesQueryBuilder builder = new TimeseriesQueryBuilder(false);
            Type customEventType = new Type("custom.event.type");
            
            String query = builder
                    .withProfileId(PROFILE_ID)
                    .withEventType(customEventType)
                    .build();

            assertTrue(query.contains("events.event_type = 'custom.event.type'"));
        }
    }

    @Nested
    @DisplayName("Time Range Tests")
    class TimeRangeTests {

        @Test
        @DisplayName("Should not include time range when not specified")
        void shouldNotIncludeTimeRangeWhenNotSpecified() {
            TimeseriesQueryBuilder builder = new TimeseriesQueryBuilder(false);
            
            String query = builder
                    .withProfileId(PROFILE_ID)
                    .withEventType(EVENT_TYPE)
                    .build();

            assertFalse(query.contains("timestamp_from_start >="));
            assertFalse(query.contains("timestamp_from_start <"));
        }

        @Test
        @DisplayName("Should include time range when specified")
        void shouldIncludeTimeRangeWhenSpecified() {
            TimeseriesQueryBuilder builder = new TimeseriesQueryBuilder(false);
            RelativeTimeRange timeRange = new RelativeTimeRange(
                    Duration.ofSeconds(1), 
                    Duration.ofSeconds(5)
            );
            
            String query = builder
                    .withProfileId(PROFILE_ID)
                    .withEventType(EVENT_TYPE)
                    .withTimeRange(timeRange)
                    .build();

            String expectedQuery = "SELECT (events.timestamp_from_start / 1000) AS seconds, sum(events.samples) as value FROM events WHERE events.profile_id = 'test-profile-123' AND events.event_type = 'jdk.ExecutionSample' AND events.timestamp_from_start >= 1000 AND events.timestamp_from_start < 5000 GROUP BY seconds ORDER BY seconds";

            assertEquals(expectedQuery, query);
        }

        @Test
        @DisplayName("Should handle time range with milliseconds correctly")
        void shouldHandleTimeRangeWithMillisecondsCorrectly() {
            TimeseriesQueryBuilder builder = new TimeseriesQueryBuilder(false);
            RelativeTimeRange timeRange = new RelativeTimeRange(1500L, 3750L);
            
            String query = builder
                    .withProfileId(PROFILE_ID)
                    .withEventType(EVENT_TYPE)
                    .withTimeRange(timeRange)
                    .build();

            assertAll(
                    () -> assertTrue(query.contains("events.timestamp_from_start >= 1500")),
                    () -> assertTrue(query.contains("events.timestamp_from_start < 3750"))
            );
        }
    }

    @Nested
    @DisplayName("Stacktrace Type Filtering Tests")
    class StacktraceTypeFilteringTests {

        @Test
        @DisplayName("Should not include stacktrace joins when no types specified")
        void shouldNotIncludeStacktraceJoinsWhenNoTypesSpecified() {
            TimeseriesQueryBuilder builder = new TimeseriesQueryBuilder(false);
            
            String query = builder
                    .withProfileId(PROFILE_ID)
                    .withEventType(EVENT_TYPE)
                    .build();

            assertFalse(query.contains("INNER JOIN stacktraces"));
        }

        @Test
        @DisplayName("Should include stacktrace joins when types specified")
        void shouldIncludeStacktraceJoinsWhenTypesSpecified() {
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
        @DisplayName("Should handle multiple stacktrace types")
        void shouldHandleMultipleStacktraceTypes() {
            TimeseriesQueryBuilder builder = new TimeseriesQueryBuilder(false);
            List<StacktraceType> stacktraceTypes = List.of(
                    StacktraceType.APPLICATION, 
                    StacktraceType.JVM
            );
            
            String query = builder
                    .withProfileId(PROFILE_ID)
                    .withEventType(EVENT_TYPE)
                    .filterStacktraceTypes(stacktraceTypes)
                    .build();

            assertTrue(query.contains("stacktraces.type_id IN (100, 0)"));
        }

        @Test
        @DisplayName("Should ignore null stacktrace types")
        void shouldIgnoreNullStacktraceTypes() {
            TimeseriesQueryBuilder builder = new TimeseriesQueryBuilder(false);
            
            String query = builder
                    .withProfileId(PROFILE_ID)
                    .withEventType(EVENT_TYPE)
                    .filterStacktraceTypes(null)
                    .build();

            assertFalse(query.contains("INNER JOIN stacktraces"));
            assertFalse(query.contains("stacktraces.type_id"));
        }

        @Test
        @DisplayName("Should ignore empty stacktrace types list")
        void shouldIgnoreEmptyStacktraceTypesList() {
            TimeseriesQueryBuilder builder = new TimeseriesQueryBuilder(false);
            
            String query = builder
                    .withProfileId(PROFILE_ID)
                    .withEventType(EVENT_TYPE)
                    .filterStacktraceTypes(List.of())
                    .build();

            assertFalse(query.contains("INNER JOIN stacktraces"));
            assertFalse(query.contains("stacktraces.type_id"));
        }
    }

    @Nested
    @DisplayName("Stacktrace Tag Filtering Tests")
    class StacktraceTagFilteringTests {

        @Test
        @DisplayName("Should not include stacktrace tag joins when no tags specified")
        void shouldNotIncludeStacktraceTagJoinsWhenNoTagsSpecified() {
            TimeseriesQueryBuilder builder = new TimeseriesQueryBuilder(false);
            
            String query = builder
                    .withProfileId(PROFILE_ID)
                    .withEventType(EVENT_TYPE)
                    .build();

            assertFalse(query.contains("LEFT JOIN stacktrace_tags"));
        }

        @Test
        @DisplayName("Should include stacktrace tag joins when tags specified")
        void shouldIncludeStacktraceTagJoinsWhenTagsSpecified() {
            TimeseriesQueryBuilder builder = new TimeseriesQueryBuilder(false);
            List<StacktraceTag> stacktraceTags = List.of(StacktraceTag.EXCLUDE_IDLE);
            
            String query = builder
                    .withProfileId(PROFILE_ID)
                    .withEventType(EVENT_TYPE)
                    .filterStacktraceTags(stacktraceTags)
                    .build();

            assertTrue(query.contains("LEFT JOIN stacktrace_tags tags"));
        }

        @Test
        @DisplayName("Should handle exclude tags correctly")
        void shouldHandleExcludeTagsCorrectly() {
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
        @DisplayName("Should handle include tags correctly")
        void shouldHandleIncludeTagsCorrectly() {
            TimeseriesQueryBuilder builder = new TimeseriesQueryBuilder(false);
            List<StacktraceTag> stacktraceTags = List.of(StacktraceTag.UNSAFE_ALLOCATION);
            
            String query = builder
                    .withProfileId(PROFILE_ID)
                    .withEventType(EVENT_TYPE)
                    .filterStacktraceTags(stacktraceTags)
                    .build();

            String expectedQuery = "SELECT (events.timestamp_from_start / 1000) AS seconds, sum(events.samples) as value FROM events LEFT JOIN stacktrace_tags tags ON (events.profile_id = tags.profile_id AND events.stacktrace_id = tags.stacktrace_id) WHERE events.profile_id = 'test-profile-123' AND events.event_type = 'jdk.ExecutionSample' AND tags.tag_id IN (1) GROUP BY seconds ORDER BY seconds";

            assertEquals(expectedQuery, query);
        }

        @Test
        @DisplayName("Should ignore null stacktrace tags")
        void shouldIgnoreNullStacktraceTags() {
            TimeseriesQueryBuilder builder = new TimeseriesQueryBuilder(false);
            
            String query = builder
                    .withProfileId(PROFILE_ID)
                    .withEventType(EVENT_TYPE)
                    .filterStacktraceTags(null)
                    .build();

            assertFalse(query.contains("LEFT JOIN stacktrace_tags"));
        }

        @Test
        @DisplayName("Should ignore empty stacktrace tags list")
        void shouldIgnoreEmptyStacktraceTagsList() {
            TimeseriesQueryBuilder builder = new TimeseriesQueryBuilder(false);
            
            String query = builder
                    .withProfileId(PROFILE_ID)
                    .withEventType(EVENT_TYPE)
                    .filterStacktraceTags(List.of())
                    .build();

            assertFalse(query.contains("LEFT JOIN stacktrace_tags"));
        }
    }

    @Nested
    @DisplayName("Builder Chain Tests")
    class BuilderChainTests {

        @Test
        @DisplayName("Should return same instance for method chaining")
        void shouldReturnSameInstanceForMethodChaining() {
            TimeseriesQueryBuilder builder = new TimeseriesQueryBuilder(false);
            
            TimeseriesQueryBuilder result = builder
                    .withWeight(true)
                    .withProfileId(PROFILE_ID)
                    .withEventType(EVENT_TYPE);

            assertSame(builder, result);
        }

        @Test
        @DisplayName("Should handle complex parameter combinations")
        void shouldHandleComplexParameterCombinations() {
            TimeseriesQueryBuilder builder = new TimeseriesQueryBuilder(true);
            RelativeTimeRange timeRange = new RelativeTimeRange(Duration.ofSeconds(2), Duration.ofSeconds(10));
            List<StacktraceType> stacktraceTypes = List.of(StacktraceType.APPLICATION, StacktraceType.JVM);
            List<StacktraceTag> stacktraceTags = List.of(StacktraceTag.EXCLUDE_IDLE);
            
            String query = builder
                    .withWeight(true)
                    .withProfileId(PROFILE_ID)
                    .withEventType(EVENT_TYPE)
                    .withTimeRange(timeRange)
                    .filterStacktraceTypes(stacktraceTypes)
                    .filterStacktraceTags(stacktraceTags)
                    .build();

            String expectedQuery = "SELECT GROUP_CONCAT(pair, ';') AS event_values, stacktrace_id, frames  FROM (SELECT CONCAT((events.timestamp_from_start / 1000), ',', sum(events.weight)) AS pair, stacktraces.stacktrace_id, stacktraces.frames FROM events INNER JOIN stacktraces ON (events.profile_id = stacktraces.profile_id AND events.stacktrace_id = stacktraces.stacktrace_id) LEFT JOIN stacktrace_tags tags ON (events.profile_id = tags.profile_id AND events.stacktrace_id = tags.stacktrace_id) WHERE events.profile_id = 'test-profile-123' AND events.event_type = 'jdk.ExecutionSample' AND stacktraces.type_id IN (100, 0) AND (tags.tag_id NOT IN (0) OR tags.tag_id IS NULL) AND events.timestamp_from_start >= 2000 AND events.timestamp_from_start < 10000 GROUP BY (events.timestamp_from_start / 1000), stacktraces.stacktrace_id ORDER BY stacktraces.stacktrace_id) GROUP BY stacktrace_id";

            assertEquals(expectedQuery, query);
        }
    }

    @Nested
    @DisplayName("JSON Fields Tests")
    class JsonFieldsTests {

        @Test
        @DisplayName("Should handle JSON fields inclusion flag")
        void shouldHandleJsonFieldsInclusionFlag() {
            TimeseriesQueryBuilder builder = new TimeseriesQueryBuilder(false);
            
            TimeseriesQueryBuilder result = builder.withJsonFields(true);
            
            assertSame(builder, result);
        }

        @Test
        @DisplayName("Should build query when JSON fields are enabled")
        void shouldBuildQueryWhenJsonFieldsEnabled() {
            TimeseriesQueryBuilder builder = new TimeseriesQueryBuilder(false);
            
            String query = builder
                    .withProfileId(PROFILE_ID)
                    .withEventType(EVENT_TYPE)
                    .withJsonFields(true)
                    .build();

            assertNotNull(query);
        }
    }

    @Nested
    @DisplayName("Edge Cases and Error Handling")
    class EdgeCasesAndErrorHandling {

        @Test
        @DisplayName("Should build query with minimal required parameters")
        void shouldBuildQueryWithMinimalRequiredParameters() {
            TimeseriesQueryBuilder builder = new TimeseriesQueryBuilder(false);
            
            String query = builder
                    .withProfileId(PROFILE_ID)
                    .withEventType(EVENT_TYPE)
                    .build();

            assertAll(
                    () -> assertNotNull(query),
                    () -> assertFalse(query.trim().isEmpty()),
                    () -> assertTrue(query.contains("SELECT")),
                    () -> assertTrue(query.contains("FROM events")),
                    () -> assertTrue(query.contains("WHERE"))
            );
        }

        @Test
        @DisplayName("Should handle special characters in profile ID")
        void shouldHandleSpecialCharactersInProfileId() {
            TimeseriesQueryBuilder builder = new TimeseriesQueryBuilder(false);
            String specialProfileId = "test-profile-with-special-chars_123";
            
            String query = builder
                    .withProfileId(specialProfileId)
                    .withEventType(EVENT_TYPE)
                    .build();

            assertTrue(query.contains("events.profile_id = '" + specialProfileId + "'"));
        }

        @Test
        @DisplayName("Should maintain query structure with all optional parameters null")
        void shouldMaintainQueryStructureWithAllOptionalParametersNull() {
            TimeseriesQueryBuilder builder = new TimeseriesQueryBuilder(false);
            
            String query = builder
                    .withWeight(false)
                    .withProfileId(PROFILE_ID)
                    .withEventType(EVENT_TYPE)
                    .withTimeRange(null)
                    .filterStacktraceTypes(null)
                    .filterStacktraceTags(null)
                    .withJsonFields(false)
                    .build();

            assertAll(
                    () -> assertNotNull(query),
                    () -> assertTrue(query.contains("SELECT")),
                    () -> assertTrue(query.contains("FROM events")),
                    () -> assertTrue(query.contains("WHERE")),
                    () -> assertTrue(query.contains("GROUP BY")),
                    () -> assertTrue(query.contains("ORDER BY"))
            );
        }
    }

    @Nested
    @DisplayName("Query Structure Validation")
    class QueryStructureValidation {

        @Test
        @DisplayName("Should generate valid SQL structure for simple query")
        void shouldGenerateValidSqlStructureForSimpleQuery() {
            TimeseriesQueryBuilder builder = new TimeseriesQueryBuilder(false);
            
            String query = builder
                    .withProfileId(PROFILE_ID)
                    .withEventType(EVENT_TYPE)
                    .build();

            assertAll(
                    () -> assertTrue(query.contains("SELECT")),
                    () -> assertTrue(query.contains("FROM events")),
                    () -> assertTrue(query.contains("WHERE")),
                    () -> assertTrue(query.contains("GROUP BY seconds")),
                    () -> assertTrue(query.contains("ORDER BY seconds")),
                    () -> assertEquals(1, countOccurrences(query, "SELECT")),
                    () -> assertEquals(1, countOccurrences(query, "FROM events"))
            );
        }

        @Test
        @DisplayName("Should generate valid SQL structure for frame-based query")
        void shouldGenerateValidSqlStructureForFrameBasedQuery() {
            TimeseriesQueryBuilder builder = new TimeseriesQueryBuilder(true);
            
            String query = builder
                    .withProfileId(PROFILE_ID)
                    .withEventType(EVENT_TYPE)
                    .build();

            assertAll(
                    () -> assertTrue(query.contains("SELECT GROUP_CONCAT")),
                    () -> assertTrue(query.contains("FROM events")),
                    () -> assertTrue(query.contains("WHERE")),
                    () -> assertTrue(query.contains("GROUP BY")),
                    () -> assertTrue(query.contains("ORDER BY")),
                    () -> assertEquals(2, countOccurrences(query, "SELECT")),
                    () -> assertEquals(1, countOccurrences(query, "FROM events"))
            );
        }

        private int countOccurrences(String text, String pattern) {
            int count = 0;
            int index = 0;
            while ((index = text.indexOf(pattern, index)) != -1) {
                count++;
                index += pattern.length();
            }
            return count;
        }
    }
}
