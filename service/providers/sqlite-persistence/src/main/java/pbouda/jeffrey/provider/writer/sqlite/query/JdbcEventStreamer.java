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
import pbouda.jeffrey.provider.api.query.QueryBuilder;
import pbouda.jeffrey.provider.api.query.RecordQuery;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collector;
import java.util.stream.Collectors;

public class JdbcQueryBuilder implements QueryBuilder {

    private static final Supplier<Collector<CharSequence, ?, String>> JOINING_SUPPLIER =
            () -> Collectors.joining(", ", "(", ")");

    private static final List<String> EVENT_FIELDS = List.of(
            "events.event_name",
            "events.timestamp",
            "events.timestamp_from_start",
            "events.samples",
            "events.weight",
            "events.weight_entity");

    private static final List<String> EVENT_JSON_FIELDS = List.of(
            "events.fields");

    private static final List<String> STACKTRACE_FIELDS = List.of(
            "stacktraces.stacktrace_id",
            "stacktraces.frames");

    private static final List<String> THREAD_FIELDS = List.of(
            "threads.java_id",
            "threads.os_id",
            "threads.name");

    private static final List<String> EVENT_TYPES_FIELDS = List.of(
            "event_types.label");

    private final List<String> fields = new ArrayList<>();
    private final String profileId;
    private boolean stacktracesIncluded = false;
    private boolean threadsIncluded = false;
    private boolean eventTypeInfoIncluded = false;
    private List<StacktraceType> stacktraceTypes = List.of();
    private List<Type> types = List.of();
    private List<StacktraceTag> tags = List.of();
    private Duration from;
    private Duration until;
    private ThreadInfo threadInfo;
    private boolean groupByStacktraces = false;

    public static JdbcQueryBuilder events(String profileId, List<Type> types) {
        return events(profileId, types, EVENT_FIELDS);
    }

    public static JdbcQueryBuilder events(String profileId, List<Type> types, List<String> eventFields) {
        JdbcQueryBuilder builder = new JdbcQueryBuilder(profileId);
        builder.fields.addAll(eventFields);
        builder.types = types;
        return builder;
    }

    private JdbcQueryBuilder(String profileId) {
        this.profileId = profileId;
    }

    public JdbcQueryBuilder groupByStacktraces() {
        this.groupByStacktraces = true;
        return this;
    }

    @Override
    public JdbcQueryBuilder stacktraces(List<StacktraceType> types) {
        this.fields.addAll(STACKTRACE_FIELDS);
        this.stacktracesIncluded = true;
        this.stacktraceTypes = types;
        return this;
    }

    @Override
    public JdbcQueryBuilder stacktraceTags(List<StacktraceTag> tags) {
        this.tags = tags;
        return this;
    }

    @Override
    public JdbcQueryBuilder threads(boolean threadsIncluded, ThreadInfo threadInfo) {
        if (threadsIncluded) {
            this.fields.addAll(THREAD_FIELDS);
            this.threadsIncluded = true;
        }
        this.threadInfo = threadInfo;
        return this;
    }

    @Override
    public JdbcQueryBuilder withEventTypeInfo() {
        this.fields.addAll(EVENT_TYPES_FIELDS);
        this.eventTypeInfoIncluded = true;
        return this;
    }

    @Override
    public JdbcQueryBuilder withJsonFields() {
        this.fields.addAll(EVENT_JSON_FIELDS);
        return this;
    }

    @Override
    public JdbcQueryBuilder from(Duration timestamp) {
        this.from = timestamp;
        return this;
    }

    @Override
    public JdbcQueryBuilder until(Duration timestamp) {
        this.until = timestamp;
        return this;
    }

    @Override
    //language=SQL
    public RecordQuery build() {
        String fields = String.join(", ", this.fields);

        StringBuilder query = new StringBuilder()
                .append("SELECT ")
                .append(fields)
                .append(" FROM events");

        if (this.eventTypeInfoIncluded) {
            query.append(" INNER JOIN event_types ON events.profile_id = event_types.profile_id " +
                    "AND events.event_name = event_types.name");
        }

        if (this.stacktracesIncluded) {
            query.append(" INNER JOIN stacktraces ON events.profile_id = stacktraces.profile_id " +
                    "AND events.stacktrace_id = stacktraces.stacktrace_id");
        }

        if (this.threadsIncluded) {
            query.append(" INNER JOIN threads ON events.thread_id = threads.thread_id");
        }

        if (!this.tags.isEmpty()) {
            query.append(" LEFT JOIN main.stacktrace_tags st ON events.profile_id = st.profile_id " +
                    "AND events.stacktrace_id = st.stacktrace_id");
        }

        // Always be included
        query.append(" WHERE events.profile_id = ")
                .append(profileIdInClause());

        query.append(" AND events.event_name IN ")
                .append(QueryBuilderUtils.eventTypesIn(types));

        if (this.from != null) {
            query.append(" AND events.timestamp_from_start >= ")
                    .append(this.from.toMillis());
        }

        if (this.until != null) {
            query.append(" AND events.timestamp_from_start < ")
                    .append(this.until.toMillis());
        }

        if (this.threadInfo != null) {
            query.append(" AND threads.name = '")
                    .append(this.threadInfo.name())
                    .append("'");
        }

        if (!this.stacktraceTypes.isEmpty()) {
            query.append(" AND stacktraces.type_id IN ")
                    .append(stacktraceTypesInClause());
        }

        if (!this.tags.isEmpty()) {
            Map<Boolean, List<StacktraceTag>> partitioned = tags.stream()
                    .collect(Collectors.partitioningBy(StacktraceTag::includes));

            List<StacktraceTag> included = partitioned.get(true);
            if (!included.isEmpty()) {
                String includedInString = tagsToInClause(included);
                query.append(" AND st.tag_id IN ")
                        .append(includedInString);
            }

            List<StacktraceTag> excluded = partitioned.get(false);
            if (!excluded.isEmpty()) {
                String excludedInString = tagsToInClause(excluded);
                query.append(" AND (st.tag_id NOT IN ")
                        .append(excludedInString)
                        .append(" OR st.tag_id IS NULL)");
            }
        }

        if (this.groupByStacktraces) {
            query.append(" GROUP BY events.stacktrace_id");
        }

        return new RecordQuery(query.toString(), threadsIncluded, stacktracesIncluded, eventTypeInfoIncluded);
    }

    private String profileIdInClause() {
        return "'" + profileId + "'";
    }

    private String stacktraceTypesInClause() {
        return stacktraceTypes.stream()
                .map(StacktraceType::id)
                .map(String::valueOf)
                .collect(JOINING_SUPPLIER.get());
    }

    private String tagsToInClause(List<StacktraceTag> tags) {
        return tags.stream()
                .map(StacktraceTag::id)
                .map(String::valueOf)
                .collect(JOINING_SUPPLIER.get());
    }
}
