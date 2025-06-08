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

class TimeseriesQueryBuilderJoinConditionsTest {

    private static final String PROFILE_ID = "test-profile-123";
    private static final Type EVENT_TYPE = Type.EXECUTION_SAMPLE;

    @Nested
    @DisplayName("Condition-Based Join Tests")
    class ConditionBasedJoinTests {

        @Test
        @DisplayName("Should use AND condition for stacktraces join")
        void shouldUseAndConditionForStacktracesJoin() {
            TimeseriesQueryBuilder builder = new TimeseriesQueryBuilder(false);
            List<StacktraceType> stacktraceTypes = List.of(StacktraceType.APPLICATION);
            
            String query = builder
                    .withProfileId(PROFILE_ID)
                    .withEventType(EVENT_TYPE)
                    .filterStacktraceTypes(stacktraceTypes)
                    .build();

            // Should contain join with parentheses around the condition (indicating Condition object was used)
            String expectedQuery = "SELECT (events.timestamp_from_start / 1000) AS seconds, sum(events.samples) as value FROM events INNER JOIN stacktraces ON (events.profile_id = stacktraces.profile_id AND events.stacktrace_id = stacktraces.stacktrace_id) WHERE events.profile_id = 'test-profile-123' AND events.event_type = 'jdk.ExecutionSample' AND stacktraces.type_id IN (100) GROUP BY seconds ORDER BY seconds";

            assertEquals(expectedQuery, query);
        }

        @Test
        @DisplayName("Should use AND condition for stacktrace tags left join")
        void shouldUseAndConditionForStacktraceTagsLeftJoin() {
            TimeseriesQueryBuilder builder = new TimeseriesQueryBuilder(false);
            List<StacktraceTag> stacktraceTags = List.of(StacktraceTag.EXCLUDE_IDLE);
            
            String query = builder
                    .withProfileId(PROFILE_ID)
                    .withEventType(EVENT_TYPE)
                    .filterStacktraceTags(stacktraceTags)
                    .build();

            // Should contain left join with parentheses around the condition
            String expectedQuery = "SELECT (events.timestamp_from_start / 1000) AS seconds, sum(events.samples) as value FROM events LEFT JOIN stacktrace_tags tags ON (events.profile_id = tags.profile_id AND events.stacktrace_id = tags.stacktrace_id) WHERE events.profile_id = 'test-profile-123' AND events.event_type = 'jdk.ExecutionSample' AND (tags.tag_id NOT IN (0) OR tags.tag_id IS NULL) GROUP BY seconds ORDER BY seconds";

            assertEquals(expectedQuery, query);
        }

        @Test
        @DisplayName("Should use conditions for both stacktraces and tags joins")
        void shouldUseConditionsForBothStacktracesAndTagsJoins() {
            TimeseriesQueryBuilder builder = new TimeseriesQueryBuilder(false);
            List<StacktraceType> stacktraceTypes = List.of(StacktraceType.APPLICATION);
            List<StacktraceTag> stacktraceTags = List.of(StacktraceTag.EXCLUDE_IDLE);
            
            String query = builder
                    .withProfileId(PROFILE_ID)
                    .withEventType(EVENT_TYPE)
                    .filterStacktraceTypes(stacktraceTypes)
                    .filterStacktraceTags(stacktraceTags)
                    .build();

            // Should contain both joins with parentheses
            String expectedQuery = "SELECT (events.timestamp_from_start / 1000) AS seconds, sum(events.samples) as value FROM events INNER JOIN stacktraces ON (events.profile_id = stacktraces.profile_id AND events.stacktrace_id = stacktraces.stacktrace_id) LEFT JOIN stacktrace_tags tags ON (events.profile_id = tags.profile_id AND events.stacktrace_id = tags.stacktrace_id) WHERE events.profile_id = 'test-profile-123' AND events.event_type = 'jdk.ExecutionSample' AND stacktraces.type_id IN (100) AND (tags.tag_id NOT IN (0) OR tags.tag_id IS NULL) GROUP BY seconds ORDER BY seconds";

            assertEquals(expectedQuery, query);
        }

        @Test
        @DisplayName("Should use conditions in frame-based query joins")
        void shouldUseConditionsInFrameBasedQueryJoins() {
            TimeseriesQueryBuilder builder = new TimeseriesQueryBuilder(true);
            List<StacktraceTag> stacktraceTags = List.of(StacktraceTag.EXCLUDE_IDLE);
            
            String query = builder
                    .withProfileId(PROFILE_ID)
                    .withEventType(EVENT_TYPE)
                    .filterStacktraceTags(stacktraceTags)
                    .build();

            // Should contain joins with parentheses in the inner query
            String expectedQuery = "SELECT GROUP_CONCAT(pair, ';') AS event_values, stacktrace_id, frames  FROM (SELECT CONCAT((events.timestamp_from_start / 1000), ',', sum(events.samples)) AS pair, stacktraces.stacktrace_id, stacktraces.frames FROM events INNER JOIN stacktraces ON (events.profile_id = stacktraces.profile_id AND events.stacktrace_id = stacktraces.stacktrace_id) LEFT JOIN stacktrace_tags tags ON (events.profile_id = tags.profile_id AND events.stacktrace_id = tags.stacktrace_id) WHERE events.profile_id = 'test-profile-123' AND events.event_type = 'jdk.ExecutionSample' AND (tags.tag_id NOT IN (0) OR tags.tag_id IS NULL) GROUP BY (events.timestamp_from_start / 1000), stacktraces.stacktrace_id ORDER BY stacktraces.stacktrace_id) GROUP BY stacktrace_id";

            assertEquals(expectedQuery, query);
        }

        @Test
        @DisplayName("Should combine condition-based joins with all other features")
        void shouldCombineConditionBasedJoinsWithAllOtherFeatures() {
            TimeseriesQueryBuilder builder = new TimeseriesQueryBuilder(false);
            RelativeTimeRange timeRange = new RelativeTimeRange(Duration.ofSeconds(1), Duration.ofSeconds(5));
            List<StacktraceType> stacktraceTypes = List.of(StacktraceType.APPLICATION, StacktraceType.JVM);
            List<StacktraceTag> stacktraceTags = List.of(StacktraceTag.EXCLUDE_IDLE);
            
            String query = builder
                    .withWeight(true)
                    .withProfileId(PROFILE_ID)
                    .withEventType(EVENT_TYPE)
                    .withTimeRange(timeRange)
                    .filterStacktraceTypes(stacktraceTypes)
                    .filterStacktraceTags(stacktraceTags)
                    .withJsonFields(true)
                    .build();

            String expectedQuery = "SELECT (events.timestamp_from_start / 1000) AS seconds, sum(events.weight) as value, GROUP_CONCAT(DISTINCT events.event_id) as event_ids FROM events INNER JOIN stacktraces ON (events.profile_id = stacktraces.profile_id AND events.stacktrace_id = stacktraces.stacktrace_id) LEFT JOIN stacktrace_tags tags ON (events.profile_id = tags.profile_id AND events.stacktrace_id = tags.stacktrace_id) WHERE events.profile_id = 'test-profile-123' AND events.event_type = 'jdk.ExecutionSample' AND stacktraces.type_id IN (100, 0) AND (tags.tag_id NOT IN (0) OR tags.tag_id IS NULL) AND events.timestamp_from_start >= 1000 AND events.timestamp_from_start < 5000 GROUP BY seconds ORDER BY seconds";

            assertEquals(expectedQuery, query);
        }
    }

    @Nested
    @DisplayName("Join Condition Benefits Tests")
    class JoinConditionBenefitsTests {

        @Test
        @DisplayName("Should demonstrate improved join readability")
        void shouldDemonstrateImprovedJoinReadability() {
            TimeseriesQueryBuilder builder = new TimeseriesQueryBuilder(false);
            List<StacktraceType> stacktraceTypes = List.of(StacktraceType.APPLICATION);
            
            String query = builder
                    .withProfileId(PROFILE_ID)
                    .withEventType(EVENT_TYPE)
                    .filterStacktraceTypes(stacktraceTypes)
                    .build();

            assertAll(
                    () -> assertNotNull(query),
                    () -> assertTrue(query.contains("INNER JOIN stacktraces ON (")),
                    () -> assertTrue(query.contains("events.profile_id = stacktraces.profile_id")),
                    () -> assertTrue(query.contains("events.stacktrace_id = stacktraces.stacktrace_id")),
                    () -> assertTrue(query.contains(" AND ")),
                    () -> assertTrue(query.contains(")")),
                    // The parentheses indicate structured Condition objects were used
                    () -> assertTrue(query.matches(".*INNER JOIN stacktraces ON \\([^)]+\\).*"))
            );
        }

        @Test
        @DisplayName("Should maintain type safety with condition objects")
        void shouldMaintainTypeSafetyWithConditionObjects() {
            // This test verifies that the code compiles and runs without runtime errors
            // when using Condition objects in joins, demonstrating type safety
            TimeseriesQueryBuilder builder = new TimeseriesQueryBuilder(false);
            List<StacktraceType> stacktraceTypes = List.of(StacktraceType.APPLICATION);
            List<StacktraceTag> stacktraceTags = List.of(StacktraceTag.EXCLUDE_IDLE);
            
            assertDoesNotThrow(() -> {
                String query = builder
                        .withProfileId(PROFILE_ID)
                        .withEventType(EVENT_TYPE)
                        .filterStacktraceTypes(stacktraceTypes)
                        .filterStacktraceTags(stacktraceTags)
                        .build();
                
                assertNotNull(query);
                assertTrue(query.length() > 0);
            });
        }

        @Test
        @DisplayName("Should show consistent join condition formatting")
        void shouldShowConsistentJoinConditionFormatting() {
            TimeseriesQueryBuilder builder = new TimeseriesQueryBuilder(false);
            List<StacktraceType> stacktraceTypes = List.of(StacktraceType.APPLICATION);
            List<StacktraceTag> stacktraceTags = List.of(StacktraceTag.EXCLUDE_IDLE);
            
            String query = builder
                    .withProfileId(PROFILE_ID)
                    .withEventType(EVENT_TYPE)
                    .filterStacktraceTypes(stacktraceTypes)
                    .filterStacktraceTags(stacktraceTags)
                    .build();

            // Both joins should use the same consistent format with parentheses
            int innerJoinIndex = query.indexOf("INNER JOIN");
            int leftJoinIndex = query.indexOf("LEFT JOIN");
            
            assertAll(
                    () -> assertTrue(innerJoinIndex > -1),
                    () -> assertTrue(leftJoinIndex > -1),
                    () -> assertTrue(leftJoinIndex > innerJoinIndex),
                    // Both should have ON ( pattern
                    () -> assertTrue(query.substring(innerJoinIndex).contains("ON (")),
                    () -> assertTrue(query.substring(leftJoinIndex).contains("ON ("))
            );
        }
    }

    @Nested
    @DisplayName("Backward Compatibility Tests")
    class BackwardCompatibilityTests {

        @Test
        @DisplayName("Should produce functionally equivalent SQL")
        void shouldProduceFunctionallyEquivalentSql() {
            // The SQL should be functionally equivalent to the previous string-based approach
            // The main difference is the addition of parentheses around join conditions
            TimeseriesQueryBuilder builder = new TimeseriesQueryBuilder(false);
            List<StacktraceType> stacktraceTypes = List.of(StacktraceType.APPLICATION);
            
            String query = builder
                    .withProfileId(PROFILE_ID)
                    .withEventType(EVENT_TYPE)
                    .filterStacktraceTypes(stacktraceTypes)
                    .build();

            // Should contain all the same essential components
            assertAll(
                    () -> assertTrue(query.contains("SELECT (events.timestamp_from_start / 1000) AS seconds")),
                    () -> assertTrue(query.contains("FROM events")),
                    () -> assertTrue(query.contains("INNER JOIN stacktraces")),
                    () -> assertTrue(query.contains("events.profile_id = stacktraces.profile_id")),
                    () -> assertTrue(query.contains("events.stacktrace_id = stacktraces.stacktrace_id")),
                    () -> assertTrue(query.contains("WHERE events.profile_id = 'test-profile-123'")),
                    () -> assertTrue(query.contains("stacktraces.type_id IN (100)")),
                    () -> assertTrue(query.contains("GROUP BY seconds")),
                    () -> assertTrue(query.contains("ORDER BY seconds"))
            );
        }

        @Test
        @DisplayName("Should maintain all existing functionality")
        void shouldMaintainAllExistingFunctionality() {
            // Verify that all builder methods still work correctly
            TimeseriesQueryBuilder builder = new TimeseriesQueryBuilder(false);
            RelativeTimeRange timeRange = new RelativeTimeRange(Duration.ofSeconds(10), Duration.ofSeconds(20));
            
            String query = builder
                    .withWeight(true)
                    .withProfileId("profile-456")
                    .withEventType(Type.OBJECT_ALLOCATION_IN_NEW_TLAB)
                    .withTimeRange(timeRange)
                    .filterStacktraceTypes(List.of(StacktraceType.APPLICATION))
                    .filterStacktraceTags(List.of(StacktraceTag.EXCLUDE_IDLE))
                    .withJsonFields(true)
                    .build();

            assertAll(
                    () -> assertNotNull(query),
                    () -> assertTrue(query.contains("sum(events.weight)")),
                    () -> assertTrue(query.contains("profile-456")),
                    () -> assertTrue(query.contains("jdk.ObjectAllocationInNewTLAB")),
                    () -> assertTrue(query.contains("events.timestamp_from_start >= 10000")),
                    () -> assertTrue(query.contains("events.timestamp_from_start < 20000")),
                    () -> assertTrue(query.contains("stacktraces.type_id IN (100)")),
                    () -> assertTrue(query.contains("tags.tag_id NOT IN (0) OR tags.tag_id IS NULL")),
                    () -> assertTrue(query.contains("GROUP_CONCAT(DISTINCT events.event_id)"))
            );
        }
    }
}
