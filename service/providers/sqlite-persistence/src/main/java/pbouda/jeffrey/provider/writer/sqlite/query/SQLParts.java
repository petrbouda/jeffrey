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

import pbouda.jeffrey.common.model.StacktraceTag;
import pbouda.jeffrey.common.model.StacktraceType;
import pbouda.jeffrey.common.model.Type;
import pbouda.jeffrey.common.model.time.RelativeTimeRange;
import pbouda.jeffrey.sql.Condition;
import pbouda.jeffrey.sql.SQLBuilder;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static pbouda.jeffrey.sql.SQLBuilder.*;

public abstract class SQLParts {

    public static SQLBuilder stacktraceTags(List<StacktraceTag> stacktraceTags) {
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

    public static SQLBuilder stacktraceTypes(List<StacktraceType> stacktraceTypes) {
        return new SQLBuilder()
                .merge(stacktraceTypesFilterOnly(stacktraceTypes))
                .join("stacktraces", and(
                        eq("events.profile_id", c("stacktraces.profile_id")),
                        eq("events.stacktrace_id", c("stacktraces.stacktrace_id"))));
    }

    public static SQLBuilder stacktraceTypesFilterOnly(List<StacktraceType> stacktraceTypes) {
        List<Integer> typeIds = stacktraceTypes.stream()
                .map(StacktraceType::id)
                .toList();

        return new SQLBuilder().and(inInts("stacktraces.type_id", typeIds));
    }

    public static SQLBuilder timeRange(RelativeTimeRange timeRange) {
        return new SQLBuilder()
                .and(gte("events.timestamp_from_start", l(timeRange.start().toMillis())))
                .and(lt("events.timestamp_from_start", l(timeRange.end().toMillis())));
    }

    public static SQLBuilder eventFields() {
        return new SQLBuilder()
                .addColumn("event_fields.fields")
                .join("event_fields", and(
                        eq("events.profile_id", c("event_fields.profile_id")),
                        eq("events.event_id", c("event_fields.event_id"))));
    }

    public static Condition profileAndType(String profileId, Type eventType) {
        return and(
                eq("events.profile_id", l(profileId)),
                eq("events.event_type", l(eventType.code())));
    }
}
