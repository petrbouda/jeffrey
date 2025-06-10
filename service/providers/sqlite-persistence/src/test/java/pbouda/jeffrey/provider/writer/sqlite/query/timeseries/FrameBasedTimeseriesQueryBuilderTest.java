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

package pbouda.jeffrey.provider.writer.sqlite.query.timeseries;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pbouda.jeffrey.common.model.StacktraceTag;
import pbouda.jeffrey.common.model.StacktraceType;
import pbouda.jeffrey.common.model.Type;
import pbouda.jeffrey.common.model.time.RelativeTimeRange;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FrameBasedTimeseriesQueryBuilderTest {

    private static final String PROFILE_ID = "test-profile-123";
    private static final Type EVENT_TYPE = Type.EXECUTION_SAMPLE;

    @Test
    @DisplayName("Should create frame-based timeseries query")
    void shouldCreateFrameBasedTimeseriesQuery() {
        String query = new FrameBasedTimeseriesQueryBuilder(PROFILE_ID, EVENT_TYPE, false)
                .build();

        String expectedQuery = "SELECT GROUP_CONCAT(pair, ';') AS event_values, stacktrace_id, frames  FROM (SELECT CONCAT((events.timestamp_from_start / 1000), ',', sum(events.samples)) AS pair, stacktraces.stacktrace_id, stacktraces.frames FROM events INNER JOIN stacktraces ON (events.profile_id = stacktraces.profile_id AND events.stacktrace_id = stacktraces.stacktrace_id) WHERE (events.profile_id = 'test-profile-123' AND events.event_type = 'jdk.ExecutionSample') GROUP BY (events.timestamp_from_start / 1000), stacktraces.stacktrace_id ORDER BY stacktraces.stacktrace_id) GROUP BY stacktrace_id";
        assertEquals(expectedQuery, query);
    }

    @Test
    @DisplayName("Should use weight when enabled")
    void shouldUseWeightWhenEnabled() {
        String query = new FrameBasedTimeseriesQueryBuilder(PROFILE_ID, EVENT_TYPE, true)
                .build();

        assertTrue(query.contains("sum(events.weight)"));
        assertFalse(query.contains("sum(events.samples)"));
    }

    @Test
    @DisplayName("Should include time range when specified")
    void shouldIncludeTimeRangeWhenSpecified() {
        RelativeTimeRange timeRange = new RelativeTimeRange(
                Duration.ofSeconds(2),
                Duration.ofSeconds(10));

        String query = new FrameBasedTimeseriesQueryBuilder(PROFILE_ID, EVENT_TYPE, false)
                .withTimeRange(timeRange)
                .build();

        assertTrue(query.contains("events.timestamp_from_start >= 2000"));
        assertTrue(query.contains("events.timestamp_from_start < 10000"));
    }

    @Test
    @DisplayName("Should filter by stacktrace types")
    void shouldFilterByStacktraceTypes() {
        String query = new FrameBasedTimeseriesQueryBuilder(PROFILE_ID, EVENT_TYPE, false)
                .withStacktraceTypes(List.of(StacktraceType.APPLICATION, StacktraceType.JVM))
                .build();

        assertTrue(query.contains("stacktraces.type_id IN (100, 0)"));
    }

    @Test
    @DisplayName("Should filter by stacktrace tags")
    void shouldFilterByStacktraceTags() {
        String query = new FrameBasedTimeseriesQueryBuilder(PROFILE_ID, EVENT_TYPE, false)
                .withStacktraceTags(List.of(StacktraceTag.EXCLUDE_IDLE))
                .build();

        assertTrue(query.contains("LEFT JOIN stacktrace_tags tags"));
        assertTrue(query.contains("(tags.tag_id NOT IN (0) OR tags.tag_id IS NULL)"));
    }

    @Test
    @DisplayName("Should handle complex parameter combinations")
    void shouldHandleComplexParameterCombinations() {
        RelativeTimeRange timeRange = new RelativeTimeRange(Duration.ofSeconds(2), Duration.ofSeconds(10));
        List<StacktraceType> stacktraceTypes = List.of(StacktraceType.APPLICATION, StacktraceType.JVM);
        List<StacktraceTag> stacktraceTags = List.of(StacktraceTag.EXCLUDE_IDLE);

        String query = new FrameBasedTimeseriesQueryBuilder(PROFILE_ID, EVENT_TYPE, true)
                .withTimeRange(timeRange)
                .withStacktraceTypes(stacktraceTypes)
                .withStacktraceTags(stacktraceTags)
                .build();

        String expectedQuery =
                "SELECT GROUP_CONCAT(pair, ';') AS event_values, stacktrace_id, frames  FROM (SELECT CONCAT((events.timestamp_from_start / 1000), ',', sum(events.weight)) AS pair, stacktraces.stacktrace_id, stacktraces.frames FROM events INNER JOIN stacktraces ON (events.profile_id = stacktraces.profile_id AND events.stacktrace_id = stacktraces.stacktrace_id) LEFT JOIN stacktrace_tags tags ON (events.profile_id = tags.profile_id AND events.stacktrace_id = tags.stacktrace_id) WHERE (events.profile_id = 'test-profile-123' AND events.event_type = 'jdk.ExecutionSample') AND (events.timestamp_from_start >= 2000 AND events.timestamp_from_start < 10000) AND stacktraces.type_id IN (100, 0) AND (tags.tag_id NOT IN (0) OR tags.tag_id IS NULL) GROUP BY (events.timestamp_from_start / 1000), stacktraces.stacktrace_id ORDER BY stacktraces.stacktrace_id) GROUP BY stacktrace_id";
        assertEquals(expectedQuery, query);
    }

    @Test
    @DisplayName("Should always include stacktraces join")
    void shouldAlwaysIncludeStacktracesJoin() {
        String query = new FrameBasedTimeseriesQueryBuilder(PROFILE_ID, EVENT_TYPE, false)
                .build();

        assertTrue(query.contains("INNER JOIN stacktraces"));
    }
}
