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

package pbouda.jeffrey.provider.writer.sql.query;

import pbouda.jeffrey.common.model.StacktraceTag;
import pbouda.jeffrey.common.model.StacktraceType;
import pbouda.jeffrey.common.model.ThreadInfo;
import pbouda.jeffrey.common.model.Type;
import pbouda.jeffrey.common.model.time.RelativeTimeRange;
import pbouda.jeffrey.sql.Condition;
import pbouda.jeffrey.sql.SQLBuilder;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

import static pbouda.jeffrey.sql.SQLBuilder.and;
import static pbouda.jeffrey.sql.SQLBuilder.c;
import static pbouda.jeffrey.sql.SQLBuilder.eq;
import static pbouda.jeffrey.sql.SQLBuilder.gte;
import static pbouda.jeffrey.sql.SQLBuilder.in;
import static pbouda.jeffrey.sql.SQLBuilder.inInts;
import static pbouda.jeffrey.sql.SQLBuilder.l;
import static pbouda.jeffrey.sql.SQLBuilder.lt;
import static pbouda.jeffrey.sql.SQLBuilder.notInOrNullInts;

public abstract class SQLFormatter {

    private final BiFunction<String, String, String> jsonbColumnFormatter;

    public SQLFormatter(BiFunction<String, String, String> jsonbColumnFormatter) {
        this.jsonbColumnFormatter = jsonbColumnFormatter;
    }

    /**
     * Formats the given SQL to be compatible with JSON operations of the specific database.
     * The generic format: {column_name}::jsonb {taken from Postgres},
     * and it formats it to the database-specific format.
     *
     * @param sql the SQL string to format
     * @return the formatted SQL string specifically for the given database
     */
    public abstract String formatJson(String sql);

    public SQLBuilder stacktraceTags(List<StacktraceTag> stacktraceTags) {
        Map<Boolean, List<StacktraceTag>> partitioned = stacktraceTags.stream()
                .collect(Collectors.partitioningBy(StacktraceTag::includes));

        SQLBuilder builder = new SQLBuilder()
                .leftJoin("stacktrace_tags tags", and(
                        eq("events.profile_id", c("tags.profile_id")),
                        eq("events.stacktrace_id", c("tags.stacktrace_id"))));

        List<StacktraceTag> included = partitioned.get(true);
        if (!included.isEmpty()) {
            List<Integer> includedIds = included.stream()
                    .map(StacktraceTag::id)
                    .toList();

            builder.and(inInts("tags.tag_id", includedIds));
        }

        List<StacktraceTag> excluded = partitioned.get(false);
        if (!excluded.isEmpty()) {
            List<Integer> excludedIds = excluded.stream()
                    .map(StacktraceTag::id)
                    .toList();

            builder.and(notInOrNullInts("tags.tag_id", excludedIds));
        }

        return builder;
    }

    public SQLBuilder stacktraceTypes(List<StacktraceType> stacktraceTypes, boolean includeStacktraces) {
        SQLBuilder builder = new SQLBuilder()
                .merge(stacktraceTypesFilterOnly(stacktraceTypes));

        if (includeStacktraces) {
            builder.join("stacktraces", and(
                    eq("events.profile_id", c("stacktraces.profile_id")),
                    eq("events.stacktrace_id", c("stacktraces.stacktrace_id"))));
        }

        return builder;
    }

    public SQLBuilder stacktraceTypesFilterOnly(List<StacktraceType> stacktraceTypes) {
        List<Integer> typeIds = stacktraceTypes.stream()
                .map(StacktraceType::id)
                .toList();

        return new SQLBuilder().and(inInts("stacktraces.type_id", typeIds));
    }

    public SQLBuilder timeRange(RelativeTimeRange timeRange) {
        return new SQLBuilder()
                .and(gte("events.start_timestamp_from_beginning", l(timeRange.start().toMillis())))
                .and(lt("events.start_timestamp_from_beginning", l(timeRange.end().toMillis())));
    }

    public SQLBuilder eventFields() {
        return new SQLBuilder()
                .addColumn(jsonbColumnFormatter.apply("events.fields", "event_fields"));
    }

    public Condition profileAndType(String profileId, Type eventType) {
        return and(
                eq("events.profile_id", l(profileId)),
                eq("events.event_type", l(eventType.code())));
    }

    public Condition profileAndTypes(String profileId, List<Type> eventTypes) {
        if (eventTypes.size() == 1) {
            return profileAndType(profileId, eventTypes.getFirst());
        }

        List<String> typeCodes = eventTypes.stream()
                .map(Type::code)
                .toList();

        return and(
                eq("events.profile_id", l(profileId)),
                in("events.event_type", typeCodes));
    }

    public SQLBuilder threads() {
        return new SQLBuilder()
                .addColumn("threads.java_id")
                .addColumn("threads.os_id")
                .addColumn("threads.is_virtual")
                .addColumn("threads.name")
                .join("threads", and(
                        eq("events.profile_id", c("threads.profile_id")),
                        eq("events.thread_id", c("threads.thread_id"))));
    }

    public SQLBuilder eventTypes() {
        return new SQLBuilder()
                .addColumn("event_types.label")
                .join("event_types", and(
                        eq("events.profile_id", c("event_types.profile_id")),
                        eq("events.event_type", c("event_types.name"))));
    }

    public SQLBuilder stacktraces() {
        return new SQLBuilder()
                .addColumn("stacktraces.stacktrace_id")
                .groupBy("stacktraces.stacktrace_id")
                .addColumn("stacktraces.frames")
                .groupBy("stacktraces.frames")
                .join("stacktraces", and(
                        eq("events.profile_id", c("stacktraces.profile_id")),
                        eq("events.stacktrace_id", c("stacktraces.stacktrace_id"))));
    }

    public SQLBuilder timeRangeOptional(Duration from, Duration until) {
        SQLBuilder builder = new SQLBuilder();
        if (from != null) {
            builder.and(gte("events.start_timestamp_from_beginning", l(from.toMillis())));
        }
        if (until != null) {
            builder.and(lt("events.start_timestamp_from_beginning", l(until.toMillis())));
        }
        return builder;
    }

    public SQLBuilder threadInfo(ThreadInfo threadInfo) {
        if (threadInfo == null) {
            return new SQLBuilder();
        }
        return new SQLBuilder().where("threads.java_id", "=", l(threadInfo.javaId()));
    }
}
