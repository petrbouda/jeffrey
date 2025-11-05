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

import pbouda.jeffrey.common.model.Type;
import pbouda.jeffrey.common.model.time.RelativeTimeRange;
import pbouda.jeffrey.provider.api.repository.EventQueryConfigurer;
import pbouda.jeffrey.sql.SQLBuilder;

import java.util.List;

public class GenericQueryBuilder implements QueryBuilder {

    private static final List<String> BASE_FIELDS = List.of(
            "events.event_type",
            "events.start_timestamp",
            "events.start_timestamp_from_beginning",
            "events.duration",
            "events.samples",
            "events.weight",
            "events.weight_entity");

    private final SQLBuilder builder;
    private final SQLFormatter sqlFormatter;

    public GenericQueryBuilder(SQLFormatter sqlFormatter, String profileId, EventQueryConfigurer configurer) {
        this(sqlFormatter, profileId, configurer, configurer.eventTypes(), BASE_FIELDS);
    }

    public GenericQueryBuilder(SQLFormatter sqlFormatter, String profileId, EventQueryConfigurer configurer, List<Type> eventTypes) {
        this(sqlFormatter, profileId, configurer, eventTypes, BASE_FIELDS);
    }

    public GenericQueryBuilder(
            SQLFormatter sqlFormatter, String profileId, EventQueryConfigurer configurer, List<Type> eventTypes, List<String> baseFields) {

        if (eventTypes == null || eventTypes.isEmpty()) {
            throw new IllegalArgumentException("Event types must be specified in the configurer.");
        }

        this.sqlFormatter = sqlFormatter;
        this.builder = new SQLBuilder()
                .addColumns(baseFields)
                .from("events")
                .where(sqlFormatter.profileAndTypes(profileId, eventTypes));

        applyConfigurer(configurer);
    }

    private void applyConfigurer(EventQueryConfigurer configurer) {
        RelativeTimeRange timeRange = configurer.timeRange();
        if (timeRange != null) {
            builder.merge(sqlFormatter.timeRangeOptional(timeRange.start(), timeRange.end()));
        }

        // Track if stacktraces join is needed
        boolean hasStacktraceTags = configurer.filterStacktraceTags() != null && !configurer.filterStacktraceTags().isEmpty();
        boolean hasStacktraceTypes = configurer.filterStacktraceTypes() != null && !configurer.filterStacktraceTypes().isEmpty();
        boolean needsStacktracesJoin = hasStacktraceTags || hasStacktraceTypes;

        if (configurer.includeFrames()) {
            builder.merge(sqlFormatter.stacktraces());
        } else if (needsStacktracesJoin) {
            // Add stacktraces join since it's needed for tags/types filtering but not added by includeFrames
            builder.join("stacktraces", SQLBuilder.and(
                    SQLBuilder.eq("events.profile_id", SQLBuilder.c("stacktraces.profile_id")),
                    SQLBuilder.eq("events.stacktrace_hash", SQLBuilder.c("stacktraces.stacktrace_hash"))));
        }

        if (hasStacktraceTypes) {
            // Just add the filter, join already exists (added above or by includeFrames)
            builder.merge(sqlFormatter.stacktraceTypesFilterOnly(configurer.filterStacktraceTypes()));
        }

        if (hasStacktraceTags) {
            // Just add the filter, join already exists (added above or by includeFrames)
            builder.merge(sqlFormatter.stacktraceTags(configurer.filterStacktraceTags()));
        }

        if (configurer.threads()) {
            builder.merge(sqlFormatter.threads());
        }

        if (configurer.specifiedThread() != null) {
            builder.merge(sqlFormatter.threadInfo(configurer.specifiedThread()));
        }

        if (configurer.eventTypeInfo()) {
            builder.merge(sqlFormatter.eventTypes());
        }

        if (configurer.jsonFields()) {
            builder.merge(sqlFormatter.eventFields());
        }
    }

    public GenericQueryBuilder addGroupBy(String group) {
        builder.groupBy(group);
        return this;
    }

    public GenericQueryBuilder addOrderBy(String order) {
        builder.orderBy(order);
        return this;
    }

    @Override
    public GenericQueryBuilder merge(SQLBuilder builder) {
        this.builder.merge(builder);
        return this;
    }

    @Override
    public String build() {
        return builder.build();
    }
}
