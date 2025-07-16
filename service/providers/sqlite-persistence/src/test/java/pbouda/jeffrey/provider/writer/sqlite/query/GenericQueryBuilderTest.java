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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pbouda.jeffrey.common.model.StacktraceTag;
import pbouda.jeffrey.common.model.StacktraceType;
import pbouda.jeffrey.common.model.ThreadInfo;
import pbouda.jeffrey.common.model.Type;
import pbouda.jeffrey.common.model.time.RelativeTimeRange;
import pbouda.jeffrey.provider.api.repository.EventQueryConfigurer;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GenericQueryBuilderTest {

    private static final String PROFILE_ID = "test-profile-123";
    private static final Type EVENT_TYPE = Type.EXECUTION_SAMPLE;
    private static final ThreadInfo TEST_THREAD_INFO = new ThreadInfo(1L, 2L, "test-thread");

    @Test
    @DisplayName("Should create basic query with default fields")
    void shouldCreateBasicQueryWithDefaultFields() {
        EventQueryConfigurer configurer = new EventQueryConfigurer()
                .withEventType(EVENT_TYPE);

        GenericQueryBuilder builder = new GenericQueryBuilder(PROFILE_ID, configurer);
        String query = builder.build();

        String expectedQuery = "SELECT events.event_type, events.start_timestamp, events.start_timestamp_from_beginning, events.duration, events.samples, events.weight, events.weight_entity FROM events WHERE (events.profile_id = 'test-profile-123' AND events.event_type = 'jdk.ExecutionSample')";
        assertEquals(expectedQuery, query);
    }

    @Test
    @DisplayName("Should create query with custom base fields")
    void shouldCreateQueryWithCustomBaseFields() {
        EventQueryConfigurer configurer = new EventQueryConfigurer()
                .withEventType(EVENT_TYPE);

        List<String> customFields = List.of("events.start_timestamp", "events.samples");
        GenericQueryBuilder builder = new GenericQueryBuilder(PROFILE_ID, configurer, configurer.eventTypes(), customFields);
        String query = builder.build();

        String expectedQuery = "SELECT events.start_timestamp, events.samples FROM events WHERE (events.profile_id = 'test-profile-123' AND events.event_type = 'jdk.ExecutionSample')";
        assertEquals(expectedQuery, query);
    }

    @Test
    @DisplayName("Should include time range when specified")
    void shouldIncludeTimeRangeWhenSpecified() {
        RelativeTimeRange timeRange = new RelativeTimeRange(
                Duration.ofSeconds(1),
                Duration.ofSeconds(5)
        );

        EventQueryConfigurer configurer = new EventQueryConfigurer()
                .withEventType(EVENT_TYPE)
                .withTimeRange(timeRange);

        GenericQueryBuilder builder = new GenericQueryBuilder(PROFILE_ID, configurer);
        String query = builder.build();

        String expectedQuery = "SELECT events.event_type, events.start_timestamp, events.start_timestamp_from_beginning, events.duration, events.samples, events.weight, events.weight_entity FROM events WHERE (events.profile_id = 'test-profile-123' AND events.event_type = 'jdk.ExecutionSample') AND (events.start_timestamp_from_beginning >= 1000 AND events.start_timestamp_from_beginning < 5000)";
        assertEquals(expectedQuery, query);
    }

    @Test
    @DisplayName("Should include stacktraces when frames are enabled")
    void shouldIncludeStacktracesWhenFramesEnabled() {
        EventQueryConfigurer configurer = new EventQueryConfigurer()
                .withEventType(EVENT_TYPE)
                .withIncludeFrames();

        GenericQueryBuilder builder = new GenericQueryBuilder(PROFILE_ID, configurer);
        String query = builder.build();

        String expectedQuery = "SELECT events.event_type, events.start_timestamp, events.start_timestamp_from_beginning, events.duration, events.samples, events.weight, events.weight_entity, stacktraces.stacktrace_id, stacktraces.frames FROM events INNER JOIN stacktraces ON (events.profile_id = stacktraces.profile_id AND events.stacktrace_id = stacktraces.stacktrace_id) WHERE (events.profile_id = 'test-profile-123' AND events.event_type = 'jdk.ExecutionSample')";
        assertEquals(expectedQuery, query);
    }

    @Test
    @DisplayName("Should filter by stacktrace types")
    void shouldFilterByStacktraceTypes() {
        EventQueryConfigurer configurer = new EventQueryConfigurer()
                .withEventType(EVENT_TYPE)
                .filterStacktraceTypes(List.of(StacktraceType.APPLICATION, StacktraceType.JVM));

        GenericQueryBuilder builder = new GenericQueryBuilder(PROFILE_ID, configurer);
        String query = builder.build();

        String expectedQuery = "SELECT events.event_type, events.start_timestamp, events.start_timestamp_from_beginning, events.duration, events.samples, events.weight, events.weight_entity FROM events INNER JOIN stacktraces ON (events.profile_id = stacktraces.profile_id AND events.stacktrace_id = stacktraces.stacktrace_id) WHERE (events.profile_id = 'test-profile-123' AND events.event_type = 'jdk.ExecutionSample') AND stacktraces.type_id IN (100, 0)";
        assertEquals(expectedQuery, query);
    }

    @Test
    @DisplayName("Should filter by stacktrace tags")
    void shouldFilterByStacktraceTags() {
        EventQueryConfigurer configurer = new EventQueryConfigurer()
                .withEventType(EVENT_TYPE)
                .filterStacktraceTags(List.of(StacktraceTag.EXCLUDE_IDLE));

        GenericQueryBuilder builder = new GenericQueryBuilder(PROFILE_ID, configurer);
        String query = builder.build();

        String expectedQuery = "SELECT events.event_type, events.start_timestamp, events.start_timestamp_from_beginning, events.duration, events.samples, events.weight, events.weight_entity FROM events LEFT JOIN stacktrace_tags tags ON (events.profile_id = tags.profile_id AND events.stacktrace_id = tags.stacktrace_id) WHERE (events.profile_id = 'test-profile-123' AND events.event_type = 'jdk.ExecutionSample') AND (tags.tag_id NOT IN (0) OR tags.tag_id IS NULL)";
        assertEquals(expectedQuery, query);
    }

    @Test
    @DisplayName("Should include threads when enabled")
    void shouldIncludeThreadsWhenEnabled() {
        EventQueryConfigurer configurer = new EventQueryConfigurer()
                .withEventType(EVENT_TYPE)
                .withThreads();

        GenericQueryBuilder builder = new GenericQueryBuilder(PROFILE_ID, configurer);
        String query = builder.build();

        String expectedQuery = "SELECT events.event_type, events.start_timestamp, events.start_timestamp_from_beginning, events.duration, events.samples, events.weight, events.weight_entity, threads.java_id, threads.os_id, threads.is_virtual, threads.name FROM events INNER JOIN threads ON (events.profile_id = threads.profile_id AND events.thread_id = threads.thread_id) WHERE (events.profile_id = 'test-profile-123' AND events.event_type = 'jdk.ExecutionSample')";
        assertEquals(expectedQuery, query);
    }

    @Test
    @DisplayName("Should filter by specific thread")
    void shouldFilterBySpecificThread() {
        EventQueryConfigurer configurer = new EventQueryConfigurer()
                .withEventType(EVENT_TYPE)
                .withSpecifiedThread(TEST_THREAD_INFO);

        GenericQueryBuilder builder = new GenericQueryBuilder(PROFILE_ID, configurer);
        String query = builder.build();

        String expectedQuery = "SELECT events.event_type, events.start_timestamp, events.start_timestamp_from_beginning, events.duration, events.samples, events.weight, events.weight_entity, threads.java_id, threads.os_id, threads.is_virtual, threads.name FROM events INNER JOIN threads ON (events.profile_id = threads.profile_id AND events.thread_id = threads.thread_id) WHERE (events.profile_id = 'test-profile-123' AND events.event_type = 'jdk.ExecutionSample') AND threads.java_id = 2";
        assertEquals(expectedQuery, query);
    }

    @Test
    @DisplayName("Should include event type info when enabled")
    void shouldIncludeEventTypeInfoWhenEnabled() {
        EventQueryConfigurer configurer = new EventQueryConfigurer()
                .withEventType(EVENT_TYPE)
                .withEventTypeInfo();

        GenericQueryBuilder builder = new GenericQueryBuilder(PROFILE_ID, configurer);
        String query = builder.build();

        String expectedQuery = "SELECT events.event_type, events.start_timestamp, events.start_timestamp_from_beginning, events.duration, events.samples, events.weight, events.weight_entity, event_types.label FROM events INNER JOIN event_types ON (events.profile_id = event_types.profile_id AND events.event_type = event_types.name) WHERE (events.profile_id = 'test-profile-123' AND events.event_type = 'jdk.ExecutionSample')";
        assertEquals(expectedQuery, query);
    }

    @Test
    @DisplayName("Should include JSON fields when enabled")
    void shouldIncludeJsonFieldsWhenEnabled() {
        EventQueryConfigurer configurer = new EventQueryConfigurer()
                .withEventType(EVENT_TYPE)
                .withJsonFields();

        GenericQueryBuilder builder = new GenericQueryBuilder(PROFILE_ID, configurer);
        String query = builder.build();

        String expectedQuery = "SELECT events.event_type, events.start_timestamp, events.start_timestamp_from_beginning, events.duration, events.samples, events.weight, events.weight_entity, json(events.fields) FROM events WHERE (events.profile_id = 'test-profile-123' AND events.event_type = 'jdk.ExecutionSample')";
        assertEquals(expectedQuery, query);
    }

    @Test
    @DisplayName("Should handle multiple event types")
    void shouldHandleMultipleEventTypes() {
        EventQueryConfigurer configurer = new EventQueryConfigurer()
                .withEventTypes(List.of(Type.EXECUTION_SAMPLE, Type.OBJECT_ALLOCATION_IN_NEW_TLAB));

        GenericQueryBuilder builder = new GenericQueryBuilder(PROFILE_ID, configurer);
        String query = builder.build();

        String expectedQuery = "SELECT events.event_type, events.start_timestamp, events.start_timestamp_from_beginning, events.duration, events.samples, events.weight, events.weight_entity FROM events WHERE (events.profile_id = 'test-profile-123' AND events.event_type IN ('jdk.ExecutionSample', 'jdk.ObjectAllocationInNewTLAB'))";
        assertEquals(expectedQuery, query);
    }

    @Test
    @DisplayName("Should add group by clauses")
    void shouldAddGroupByClauses() {
        EventQueryConfigurer configurer = new EventQueryConfigurer()
                .withEventType(EVENT_TYPE);

        GenericQueryBuilder builder = new GenericQueryBuilder(PROFILE_ID, configurer);
        String query = builder
                .addGroupBy("events.start_timestamp")
                .addGroupBy("events.thread_id")
                .build();

        String expectedQuery = "SELECT events.event_type, events.start_timestamp, events.start_timestamp_from_beginning, events.duration, events.samples, events.weight, events.weight_entity FROM events WHERE (events.profile_id = 'test-profile-123' AND events.event_type = 'jdk.ExecutionSample') GROUP BY events.start_timestamp, events.thread_id";
        assertEquals(expectedQuery, query);
    }

    @Test
    @DisplayName("Should add order by clauses")
    void shouldAddOrderByClauses() {
        EventQueryConfigurer configurer = new EventQueryConfigurer()
                .withEventType(EVENT_TYPE);

        GenericQueryBuilder builder = new GenericQueryBuilder(PROFILE_ID, configurer);
        String query = builder
                .addOrderBy("events.start_timestamp ASC")
                .addOrderBy("events.duration DESC")
                .build();

        String expectedQuery = "SELECT events.event_type, events.start_timestamp, events.start_timestamp_from_beginning, events.duration, events.samples, events.weight, events.weight_entity FROM events WHERE (events.profile_id = 'test-profile-123' AND events.event_type = 'jdk.ExecutionSample') ORDER BY events.start_timestamp ASC, events.duration DESC";
        assertEquals(expectedQuery, query);
    }

    @Test
    @DisplayName("Should handle complex configuration combinations")
    void shouldHandleComplexConfigurationCombinations() {
        RelativeTimeRange timeRange = new RelativeTimeRange(
                Duration.ofSeconds(2),
                Duration.ofSeconds(10)
        );

        EventQueryConfigurer configurer = new EventQueryConfigurer()
                .withEventTypes(List.of(Type.EXECUTION_SAMPLE, Type.OBJECT_ALLOCATION_IN_NEW_TLAB))
                .withTimeRange(timeRange)
                .withIncludeFrames()
                .withThreads()
                .withEventTypeInfo()
                .withJsonFields()
                .filterStacktraceTypes(List.of(StacktraceType.APPLICATION))
                .filterStacktraceTags(List.of(StacktraceTag.EXCLUDE_IDLE))
                .withSpecifiedThread(TEST_THREAD_INFO);

        GenericQueryBuilder builder = new GenericQueryBuilder(PROFILE_ID, configurer);
        String query = builder
                .addGroupBy("events.start_timestamp")
                .addOrderBy("events.start_timestamp ASC")
                .build();

        String expectedQuery = "SELECT events.event_type, events.start_timestamp, events.start_timestamp_from_beginning, events.duration, events.samples, events.weight, events.weight_entity, stacktraces.stacktrace_id, stacktraces.frames, threads.java_id, threads.os_id, threads.is_virtual, threads.name, event_types.label, json(events.fields) FROM events INNER JOIN stacktraces ON (events.profile_id = stacktraces.profile_id AND events.stacktrace_id = stacktraces.stacktrace_id) LEFT JOIN stacktrace_tags tags ON (events.profile_id = tags.profile_id AND events.stacktrace_id = tags.stacktrace_id) INNER JOIN threads ON (events.profile_id = threads.profile_id AND events.thread_id = threads.thread_id) INNER JOIN event_types ON (events.profile_id = event_types.profile_id AND events.event_type = event_types.name) WHERE (events.profile_id = 'test-profile-123' AND events.event_type IN ('jdk.ExecutionSample', 'jdk.ObjectAllocationInNewTLAB')) AND (events.start_timestamp_from_beginning >= 2000 AND events.start_timestamp_from_beginning < 10000) AND stacktraces.type_id IN (100) AND (tags.tag_id NOT IN (0) OR tags.tag_id IS NULL) AND threads.java_id = 2 GROUP BY events.start_timestamp ORDER BY events.start_timestamp ASC";
        assertEquals(expectedQuery, query);
    }

    @Test
    @DisplayName("Should handle empty configurer")
    void shouldHandleEmptyConfigurer() {
        EventQueryConfigurer configurer = new EventQueryConfigurer();
        Assertions.assertThrows(IllegalArgumentException.class, () -> new GenericQueryBuilder(PROFILE_ID, configurer));
    }

    @Test
    @DisplayName("Should handle only start time in time range")
    void shouldHandleOnlyStartTimeInTimeRange() {
        RelativeTimeRange timeRange = new RelativeTimeRange(Duration.ofSeconds(1), null);

        EventQueryConfigurer configurer = new EventQueryConfigurer()
                .withEventType(EVENT_TYPE)
                .withTimeRange(timeRange);

        GenericQueryBuilder builder = new GenericQueryBuilder(PROFILE_ID, configurer);
        String query = builder.build();

        String expectedQuery = "SELECT events.event_type, events.start_timestamp, events.start_timestamp_from_beginning, events.duration, events.samples, events.weight, events.weight_entity FROM events WHERE (events.profile_id = 'test-profile-123' AND events.event_type = 'jdk.ExecutionSample') AND events.start_timestamp_from_beginning >= 1000";
        assertEquals(expectedQuery, query);
    }

    @Test
    @DisplayName("Should handle only end time in time range")
    void shouldHandleOnlyEndTimeInTimeRange() {
        RelativeTimeRange timeRange = new RelativeTimeRange(null, Duration.ofSeconds(5));

        EventQueryConfigurer configurer = new EventQueryConfigurer()
                .withEventType(EVENT_TYPE)
                .withTimeRange(timeRange);

        GenericQueryBuilder builder = new GenericQueryBuilder(PROFILE_ID, configurer);
        String query = builder.build();

        String expectedQuery = "SELECT events.event_type, events.start_timestamp, events.start_timestamp_from_beginning, events.duration, events.samples, events.weight, events.weight_entity FROM events WHERE (events.profile_id = 'test-profile-123' AND events.event_type = 'jdk.ExecutionSample') AND events.start_timestamp_from_beginning < 5000";
        assertEquals(expectedQuery, query);
    }

    @Test
    @DisplayName("Should handle builder method chaining")
    void shouldHandleBuilderMethodChaining() {
        EventQueryConfigurer configurer = new EventQueryConfigurer()
                .withEventType(EVENT_TYPE);

        GenericQueryBuilder builder = new GenericQueryBuilder(PROFILE_ID, configurer);
        String query = builder
                .addGroupBy("events.start_timestamp")
                .addOrderBy("events.start_timestamp ASC")
                .addGroupBy("events.thread_id")
                .addOrderBy("events.duration DESC")
                .build();

        String expectedQuery = "SELECT events.event_type, events.start_timestamp, events.start_timestamp_from_beginning, events.duration, events.samples, events.weight, events.weight_entity FROM events WHERE (events.profile_id = 'test-profile-123' AND events.event_type = 'jdk.ExecutionSample') GROUP BY events.start_timestamp, events.thread_id ORDER BY events.start_timestamp ASC, events.duration DESC";
        assertEquals(expectedQuery, query);
    }
}
