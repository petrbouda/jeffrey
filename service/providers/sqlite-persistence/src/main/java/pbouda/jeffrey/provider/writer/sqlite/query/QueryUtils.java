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

import pbouda.jeffrey.common.ThreadInfo;
import pbouda.jeffrey.common.Type;
import pbouda.jeffrey.common.model.profile.StacktraceTag;
import pbouda.jeffrey.common.model.profile.StacktraceType;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collector;
import java.util.stream.Collectors;

public abstract class QueryUtils {

    public static final Supplier<Collector<CharSequence, ?, String>> JOINING_SUPPLIER =
            () -> Collectors.joining(", ", "(", ")");

    public static StringBuilder selectFromEvents(List<String> fields) {
        return new StringBuilder()
                .append("SELECT ")
                .append(String.join(", ", fields))
                .append(" FROM events");
    }

    public static void includeEventTypeInfo(StringBuilder queryBuilder) {
        queryBuilder.append(" INNER JOIN event_types ON events.profile_id = event_types.profile_id " +
                "AND events.event_type = event_types.name");
    }

    public static void includeThreads(StringBuilder queryBuilder) {
        queryBuilder.append(" INNER JOIN threads ON events.profile_id = threads.profile_id " +
                "AND events.thread_id = threads.thread_id");
    }

    public static void includeStacktraces(StringBuilder queryBuilder) {
        queryBuilder.append(" INNER JOIN stacktraces ON events.profile_id = stacktraces.profile_id " +
                "AND events.stacktrace_id = stacktraces.stacktrace_id");
    }

    public static void includeStacktraceTags(StringBuilder queryBuilder) {
        queryBuilder.append(" LEFT JOIN stacktrace_tags tags ON events.profile_id = tags.profile_id " +
                "AND events.stacktrace_id = tags.stacktrace_id");
    }

    public static void appendStacktraceTagsInWhere(StringBuilder queryBuilder, List<StacktraceTag> tags) {
        Map<Boolean, List<StacktraceTag>> partitioned = tags.stream()
                .collect(Collectors.partitioningBy(StacktraceTag::includes));

        List<StacktraceTag> included = partitioned.get(true);
        if (!included.isEmpty()) {
            String includedInString = tagsToInClause(included);
            queryBuilder.append(" AND tags.tag_id IN ")
                    .append(includedInString);
        }

        List<StacktraceTag> excluded = partitioned.get(false);
        if (!excluded.isEmpty()) {
            String excludedInString = tagsToInClause(excluded);
            queryBuilder.append(" AND (tags.tag_id NOT IN ")
                    .append(excludedInString)
                    .append(" OR tags.tag_id IS NULL)");
        }
    }

    public static void appendThreadInfo(StringBuilder queryBuilder, ThreadInfo threadInfo) {
        if (threadInfo != null) {
            queryBuilder.append(" AND threads.name = ")
                    .append(brackets(threadInfo.name()));
        }
    }

    public static void appendStacktraceTypes(StringBuilder queryBuilder, List<StacktraceType> stacktraceTypes) {
        if (stacktraceTypes != null && !stacktraceTypes.isEmpty()) {
            queryBuilder.append(" AND stacktraces.type_id IN ")
                    .append(stacktraceTypesInClause(stacktraceTypes));
        }
    }

    public static void appendStacktraceTags(StringBuilder queryBuilder, List<StacktraceTag> stacktraceTags) {
        if (stacktraceTags != null && !stacktraceTags.isEmpty()) {
            QueryUtils.appendStacktraceTagsInWhere(queryBuilder, stacktraceTags);
        }
    }

    public static void appendTimeRange(StringBuilder queryBuilder, Duration from, Duration until) {
        if (from != null) {
            queryBuilder.append(" AND events.timestamp_from_start >= ")
                    .append(from.toMillis());
        }

        if (until != null) {
            queryBuilder.append(" AND events.timestamp_from_start < ")
                    .append(until.toMillis());
        }
    }

    public static void appendGroupBy(StringBuilder queryBuilder, List<String> groupBy) {
        if (groupBy != null && !groupBy.isEmpty()) {
            queryBuilder.append(" GROUP BY ")
                    .append(String.join(", ", groupBy));
        }
    }

    public static void appendOrderBy(StringBuilder queryBuilder, List<String> orderBy) {
        if (orderBy != null && !orderBy.isEmpty()) {
            queryBuilder.append(" ORDER BY ")
                    .append(String.join(", ", orderBy));
        }
    }

    public static void appendProfileIdAndEventType(StringBuilder queryBuilder, String profileId, List<Type> types) {
        queryBuilder.append(" WHERE events.profile_id = ")
                .append(QueryUtils.brackets(profileId));

        queryBuilder.append(" AND events.event_type IN ")
                .append(QueryUtils.eventTypesIn(types));
    }

    private static String tagsToInClause(List<StacktraceTag> tags) {
        return tags.stream()
                .map(StacktraceTag::id)
                .map(String::valueOf)
                .collect(QueryUtils.JOINING_SUPPLIER.get());
    }

    public static String eventTypesIn(List<Type> types) {
        return types.stream()
                .map(Type::code)
                .map(code -> "'" + code + "'")
                .collect(JOINING_SUPPLIER.get());
    }

    private static String stacktraceTypesInClause(List<StacktraceType> stacktraceTypes) {
        return stacktraceTypes.stream()
                .map(StacktraceType::id)
                .map(String::valueOf)
                .collect(QueryUtils.JOINING_SUPPLIER.get());
    }

    public static String brackets(String value) {
        return "'" + value + "'";
    }
}
