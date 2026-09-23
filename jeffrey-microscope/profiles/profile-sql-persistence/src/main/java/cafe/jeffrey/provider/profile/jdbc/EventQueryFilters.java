/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package cafe.jeffrey.provider.profile.jdbc;

import cafe.jeffrey.provider.profile.api.EventQueryConfigurer;
import cafe.jeffrey.microscope.model.StacktraceTag;
import cafe.jeffrey.microscope.model.StacktraceType;
import cafe.jeffrey.microscope.model.ThreadInfo;
import cafe.jeffrey.microscope.model.time.RelativeTimeRange;

import java.util.List;

/**
 * Per-request splicing of the optional filter clauses shared by the flamegraph, timeseries and
 * sub-second query templates. A disabled filter is ABSENT from the rendered SQL — instead of the
 * former {@code (:param IS NULL OR ...)} guards that DuckDB had to re-evaluate for every row and
 * that defeated predicate pushdown.
 *
 * <p>Placeholder conventions (the templates use the {@code e} alias for the events table, {@code s}
 * for stacktraces and {@code t} for threads):
 * <ul>
 *   <li>{@code <<time_filters>>} — sargable bounds on {@code e.start_timestamp_from_beginning}</li>
 *   <li>{@code <<span_filter>>} — span-scope semi-join (see {@link SpanScopeSql})</li>
 *   <li>{@code <<stacktrace_filters>>} — stacktrace type and tag predicates on {@code s}</li>
 *   <li>{@code <<thread_filters>>} — specified-thread predicates on {@code t}</li>
 *   <li>{@code <<json_field_filter>>} — single JSON field equality on {@code e.fields}</li>
 * </ul>
 */
final class EventQueryFilters {

    static final String TIME_FILTERS = "<<time_filters>>";
    static final String SPAN_FILTER = "<<span_filter>>";
    static final String STACKTRACE_FILTERS = "<<stacktrace_filters>>";
    static final String THREAD_FILTERS = "<<thread_filters>>";
    static final String JSON_FIELD_FILTER = "<<json_field_filter>>";

    private static final String EVENTS_ALIAS = "e";

    //language=SQL
    private static final String FROM_TIME_CLAUSE = "AND e.start_timestamp_from_beginning >= :from_time\n";
    //language=SQL
    private static final String TO_TIME_CLAUSE = "AND e.start_timestamp_from_beginning <= :to_time\n";
    //language=SQL
    private static final String STACKTRACE_TYPES_CLAUSE = "AND s.type_id IN (:stacktrace_types)\n";
    //language=SQL
    private static final String INCLUDED_TAGS_CLAUSE = "AND list_has_any(s.tag_ids, [:included_tags])\n";
    //language=SQL
    private static final String EXCLUDED_TAGS_CLAUSE = "AND NOT list_has_any(s.tag_ids, [:excluded_tags])\n";
    /**
     * Keeps events taken on any of the requested threads. The two id columns are matched separately
     * because a lane standing for a group can hold both threads that have a Java id and threads that
     * only have an OS id; each side is spliced in only when it has members, so neither list is ever
     * empty at query time.
     */
    //language=SQL
    private static final String THREAD_JAVA_ID_CLAUSE =
            "AND list_contains([:java_thread_ids], t.java_id)\n";
    //language=SQL
    private static final String THREAD_OS_ID_CLAUSE =
            "AND list_contains([:os_thread_ids], t.os_id)\n";
    //language=SQL
    private static final String THREAD_EITHER_ID_CLAUSE = """
            AND (list_contains([:java_thread_ids], t.java_id)
                 OR list_contains([:os_thread_ids], t.os_id))
            """;
    //language=SQL
    private static final String JSON_FIELD_CLAUSE =
            "AND json_extract_string(e.fields, :json_field_path) = :json_field_value\n";

    private EventQueryFilters() {
    }

    /**
     * Replaces every filter placeholder in the template with either the active predicate or an
     * empty string, according to what the configurer requests for this particular query execution.
     */
    static String splice(String template, EventQueryConfigurer configurer) {
        String spliced = template
                .replace(TIME_FILTERS, timeFilters(configurer.timeRange()))
                .replace(SPAN_FILTER, spanFilter(configurer))
                .replace(STACKTRACE_FILTERS, stacktraceFilters(configurer))
                .replace(THREAD_FILTERS, threadFilters(configurer))
                .replace(JSON_FIELD_FILTER, jsonFieldFilter(configurer));

        // Last, and on the whole statement: the predicate above reads a relation that has to be
        // declared in front of whatever the template opens with.
        return SpanScopeSql.withScopeCte(spliced, configurer.spanScope());
    }

    private static String timeFilters(RelativeTimeRange timeRange) {
        if (timeRange == null) {
            return "";
        }

        StringBuilder clauses = new StringBuilder();
        if (timeRange.start() != null) {
            clauses.append(FROM_TIME_CLAUSE);
        }
        if (timeRange.end() != null) {
            clauses.append(TO_TIME_CLAUSE);
        }
        return clauses.toString();
    }

    private static String spanFilter(EventQueryConfigurer configurer) {
        return SpanScopeSql.predicate(configurer.spanScope(), EVENTS_ALIAS);
    }

    private static String stacktraceFilters(EventQueryConfigurer configurer) {
        StringBuilder clauses = new StringBuilder();

        List<StacktraceType> stacktraceTypes = configurer.filterStacktraceTypes();
        if (stacktraceTypes != null && !stacktraceTypes.isEmpty()) {
            clauses.append(STACKTRACE_TYPES_CLAUSE);
        }

        List<StacktraceTag> stacktraceTags = configurer.filterStacktraceTags();
        if (stacktraceTags != null && !stacktraceTags.isEmpty()) {
            if (stacktraceTags.stream().anyMatch(StacktraceTag::includes)) {
                clauses.append(INCLUDED_TAGS_CLAUSE);
            }
            if (stacktraceTags.stream().anyMatch(tag -> !tag.includes())) {
                clauses.append(EXCLUDED_TAGS_CLAUSE);
            }
        }
        return clauses.toString();
    }

    private static String threadFilters(EventQueryConfigurer configurer) {
        List<ThreadInfo> threads = configurer.specifiedThreads();
        if (threads.isEmpty()) {
            return "";
        }
        if (ThreadIdParams.osIds(threads).isEmpty()) {
            return THREAD_JAVA_ID_CLAUSE;
        }
        if (ThreadIdParams.javaIds(threads).isEmpty()) {
            return THREAD_OS_ID_CLAUSE;
        }
        return THREAD_EITHER_ID_CLAUSE;
    }

    private static String jsonFieldFilter(EventQueryConfigurer configurer) {
        if (configurer.jsonFieldFilter() == null) {
            return "";
        }
        return JSON_FIELD_CLAUSE;
    }
}
