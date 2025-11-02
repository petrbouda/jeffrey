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

package pbouda.jeffrey.provider.writer.sql.query.timeseries;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import pbouda.jeffrey.common.model.StacktraceTag;
import pbouda.jeffrey.common.model.StacktraceType;
import pbouda.jeffrey.common.model.Type;
import pbouda.jeffrey.common.model.time.RelativeTimeRange;
import pbouda.jeffrey.provider.writer.sql.query.TestSQLFormatter;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SimpleTimeseriesQueryBuilderTest {

    private static final String PROFILE_ID = "test-profile-123";
    private static final Type EVENT_TYPE = Type.EXECUTION_SAMPLE;

    @Test
    @DisplayName("Should create simple timeseries query")
    void shouldCreateSimpleTimeseriesQuery() {
        SimpleTimeseriesQueryBuilder builder =
                new SimpleTimeseriesQueryBuilder(new TestSQLFormatter(), PROFILE_ID, EVENT_TYPE, false);
        String expectedQuery = "SELECT (events.start_timestamp_from_beginning / 1000) AS seconds, sum(events.samples) as value FROM events WHERE (events.profile_id = 'test-profile-123' AND events.event_type = 'jdk.ExecutionSample') GROUP BY seconds ORDER BY seconds";
        assertEquals(expectedQuery, builder.build());
    }

    @Test
    @DisplayName("Should use weight when enabled")
    void shouldUseWeightWhenEnabled() {
        SimpleTimeseriesQueryBuilder builder =
                new SimpleTimeseriesQueryBuilder(new TestSQLFormatter(), PROFILE_ID, EVENT_TYPE, true);
        String expectedQuery = "SELECT (events.start_timestamp_from_beginning / 1000) AS seconds, sum(events.weight) as value FROM events WHERE (events.profile_id = 'test-profile-123' AND events.event_type = 'jdk.ExecutionSample') GROUP BY seconds ORDER BY seconds";
        assertEquals(expectedQuery, builder.build());
    }

    @Test
    @DisplayName("Should include time range when specified")
    void shouldIncludeTimeRangeWhenSpecified() {
        SimpleTimeseriesQueryBuilder builder =
                new SimpleTimeseriesQueryBuilder(new TestSQLFormatter(), PROFILE_ID, EVENT_TYPE, false);
        RelativeTimeRange timeRange = new RelativeTimeRange(
                Duration.ofSeconds(1), 
                Duration.ofSeconds(5)
        );
        
        String query = builder
                .withTimeRange(timeRange)
                .build();

        String expectedQuery = "SELECT (events.start_timestamp_from_beginning / 1000) AS seconds, sum(events.samples) as value FROM events WHERE (events.profile_id = 'test-profile-123' AND events.event_type = 'jdk.ExecutionSample') AND (events.start_timestamp_from_beginning >= 1000 AND events.start_timestamp_from_beginning < 5000) GROUP BY seconds ORDER BY seconds";

        assertEquals(expectedQuery, query);
    }

    @Test
    @DisplayName("Should include stacktrace joins when types specified")
    void shouldIncludeStacktraceJoinsWhenTypesSpecified() {
        SimpleTimeseriesQueryBuilder builder =
                new SimpleTimeseriesQueryBuilder(new TestSQLFormatter(), PROFILE_ID, EVENT_TYPE, false);
        List<StacktraceType> stacktraceTypes = List.of(StacktraceType.APPLICATION);
        
        String query = builder
                .withStacktraceTypes(stacktraceTypes)
                .build();

        String expectedQuery = "SELECT (events.start_timestamp_from_beginning / 1000) AS seconds, sum(events.samples) as value FROM events INNER JOIN stacktraces ON (events.profile_id = stacktraces.profile_id AND events.stacktrace_hash = stacktraces.stacktrace_hash) WHERE (events.profile_id = 'test-profile-123' AND events.event_type = 'jdk.ExecutionSample') AND stacktraces.type_id IN (100) GROUP BY seconds ORDER BY seconds";

        assertEquals(expectedQuery, query);
    }

    @Test
    @DisplayName("Should handle exclude tags correctly")
    void shouldHandleExcludeTagsCorrectly() {
        SimpleTimeseriesQueryBuilder builder =
                new SimpleTimeseriesQueryBuilder(new TestSQLFormatter(), PROFILE_ID, EVENT_TYPE, false);
        List<StacktraceTag> stacktraceTags = List.of(StacktraceTag.EXCLUDE_IDLE);
        
        String query = builder
                .withStacktraceTags(stacktraceTags)
                .build();

        String expectedQuery = "SELECT (events.start_timestamp_from_beginning / 1000) AS seconds, sum(events.samples) as value FROM events LEFT JOIN stacktrace_tags tags ON (events.profile_id = tags.profile_id AND events.stacktrace_hash = tags.stacktrace_id) WHERE (events.profile_id = 'test-profile-123' AND events.event_type = 'jdk.ExecutionSample') AND (tags.tag_id NOT IN (0) OR tags.tag_id IS NULL) GROUP BY seconds ORDER BY seconds";

        assertEquals(expectedQuery, query);
    }
}
