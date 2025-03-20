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

import pbouda.jeffrey.common.model.ThreadInfo;
import pbouda.jeffrey.common.model.StacktraceTag;
import pbouda.jeffrey.common.model.StacktraceType;
import pbouda.jeffrey.common.model.Type;
import pbouda.jeffrey.common.model.time.RelativeTimeRange;
import pbouda.jeffrey.provider.api.streamer.EventStreamConfigurer;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public class GenericQueryBuilder implements QueryBuilder {

    private static final List<String> BASE_FIELDS = List.of(
            "events.event_type",
            "events.timestamp",
            "events.timestamp_from_start",
            "events.duration",
            "events.samples",
            "events.weight",
            "events.weight_entity");

    private static final List<String> EVENT_JSON_FIELDS = List.of(
            "event_fields.fields");

    private static final List<String> STACKTRACE_FIELDS = List.of(
            "stacktraces.stacktrace_id",
            "stacktraces.frames");

    private static final List<String> THREAD_FIELDS = List.of(
            "threads.java_id",
            "threads.os_id",
            "threads.name");

    private static final List<String> EVENT_TYPES_FIELDS = List.of(
            "event_types.label");

    private final List<String> fields;
    private final List<Type> eventTypes;
    private final String profileId;

    private boolean threadsIncluded = false;
    private boolean eventTypeInfoIncluded = false;
    private boolean stacktraceTagsIncluded = false;
    private boolean stacktracesIncluded = false;
    private boolean eventFieldsIncluded = false;
    private List<StacktraceType> stacktraceTypes;
    private List<StacktraceTag> stacktraceTags;
    private Duration from;
    private Duration until;
    private ThreadInfo threadInfo;
    private final List<String> groupBy = new ArrayList<>();
    private final List<String> orderBy = new ArrayList<>();

    public GenericQueryBuilder(String profileId, EventStreamConfigurer configurer) {
        this(profileId, configurer, BASE_FIELDS);
    }

    public GenericQueryBuilder(String profileId, EventStreamConfigurer configurer, List<String> baseFields) {
        this.fields = new ArrayList<>(baseFields);
        this.profileId = profileId;
        this.eventTypes = configurer.eventTypes();
        applyConfigurer(configurer);
    }

    private void applyConfigurer(EventStreamConfigurer configurer) {
        RelativeTimeRange timeRange = configurer.timeRange();
        if (timeRange != null) {
            if (timeRange.isStartUsed()) {
                this.from = timeRange.start();
            }

            if (timeRange.isEndUsed()) {
                this.until = timeRange.end();
            }
        }

        if (configurer.includeFrames()) {
            this.fields.addAll(STACKTRACE_FIELDS);
            this.stacktracesIncluded = true;
        }

        if (configurer.filterStacktraceTypes() != null && !configurer.filterStacktraceTypes().isEmpty()) {
            this.stacktraceTypes = configurer.filterStacktraceTypes();
            this.stacktracesIncluded = true;
        }

        if (configurer.filterStacktraceTags() != null && !configurer.filterStacktraceTags().isEmpty()) {
            this.stacktraceTags = configurer.filterStacktraceTags();
            this.stacktraceTagsIncluded = true;
        }

        if (configurer.threads()) {
            this.fields.addAll(THREAD_FIELDS);
            this.threadsIncluded = true;
        }

        if (configurer.specifiedThread() != null) {
            this.threadInfo = configurer.specifiedThread();
        }

        if (configurer.eventTypeInfo()) {
            this.fields.addAll(EVENT_TYPES_FIELDS);
            this.eventTypeInfoIncluded = true;
        }

        if (configurer.jsonFields()) {
            this.fields.addAll(EVENT_JSON_FIELDS);
            this.eventFieldsIncluded = true;
        }
    }

    public GenericQueryBuilder addGroupBy(String group) {
        this.groupBy.add(group);
        return this;
    }

    public GenericQueryBuilder addOrderBy(String order) {
        this.orderBy.add(order);
        return this;
    }

    //language=SQL
    @Override
    public String build() {
        StringBuilder queryBuilder = QueryUtils.selectFromEvents(fields);

        if (this.eventTypeInfoIncluded) {
            QueryUtils.includeEventTypeInfo(queryBuilder);
        }

        if (this.stacktracesIncluded) {
            QueryUtils.includeStacktraces(queryBuilder);
        }

        if (this.threadsIncluded) {
            QueryUtils.includeThreads(queryBuilder);
        }

        if (this.stacktraceTagsIncluded) {
            QueryUtils.includeStacktraceTags(queryBuilder);
        }

        if (this.eventFieldsIncluded) {
            QueryUtils.includeEventFields(queryBuilder);
        }

        QueryUtils.appendProfileIdAndEventType(queryBuilder, profileId, eventTypes);

        QueryUtils.appendTimeRange(queryBuilder, from, until);

        QueryUtils.appendThreadInfo(queryBuilder, threadInfo);

        QueryUtils.appendStacktraceTypes(queryBuilder, stacktraceTypes);

        QueryUtils.appendStacktraceTags(queryBuilder, stacktraceTags);

        QueryUtils.appendGroupBy(queryBuilder, groupBy);

        QueryUtils.appendOrderBy(queryBuilder, orderBy);

        return queryBuilder.toString();
    }
}
