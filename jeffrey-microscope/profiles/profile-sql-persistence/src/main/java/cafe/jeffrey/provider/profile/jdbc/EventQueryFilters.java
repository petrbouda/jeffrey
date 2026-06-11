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

package cafe.jeffrey.provider.profile.jdbc;

import cafe.jeffrey.provider.profile.api.EventQueryConfigurer;
import cafe.jeffrey.shared.common.model.StacktraceTag;
import cafe.jeffrey.shared.common.model.StacktraceType;
import cafe.jeffrey.shared.common.model.time.RelativeTimeRange;

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
 *   <li>{@code <<span_filter>>} — span-scope semi-join (see {@link SpanIntervalParams})</li>
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
    //language=SQL
    private static final String THREAD_CLAUSES = """
            AND t.java_id = :java_thread_id
            AND t.os_id = :os_thread_id
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
        return template
                .replace(TIME_FILTERS, timeFilters(configurer.timeRange()))
                .replace(SPAN_FILTER, spanFilter(configurer))
                .replace(STACKTRACE_FILTERS, stacktraceFilters(configurer))
                .replace(THREAD_FILTERS, threadFilters(configurer))
                .replace(JSON_FIELD_FILTER, jsonFieldFilter(configurer));
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
        if (!SpanIntervalParams.enabled(configurer.spanIntervals())) {
            return "";
        }
        return SpanIntervalParams.semiJoinFragment(EVENTS_ALIAS);
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
        if (configurer.specifiedThread() == null) {
            return "";
        }
        return THREAD_CLAUSES;
    }

    private static String jsonFieldFilter(EventQueryConfigurer configurer) {
        if (configurer.jsonFieldFilter() == null) {
            return "";
        }
        return JSON_FIELD_CLAUSE;
    }
}
