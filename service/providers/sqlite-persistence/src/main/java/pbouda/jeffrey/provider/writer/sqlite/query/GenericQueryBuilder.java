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

import pbouda.jeffrey.common.model.time.RelativeTimeRange;
import pbouda.jeffrey.provider.api.repository.EventQueryConfigurer;
import pbouda.jeffrey.sql.SQLBuilder;

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

    private final SQLBuilder builder;

    public GenericQueryBuilder(String profileId, EventQueryConfigurer configurer) {
        this(profileId, configurer, BASE_FIELDS);
    }

    public GenericQueryBuilder(String profileId, EventQueryConfigurer configurer, List<String> baseFields) {
        if (configurer.eventTypes() == null || configurer.eventTypes().isEmpty()) {
            throw new IllegalArgumentException("Event types must be specified in the configurer.");
        }

        this.builder = new SQLBuilder()
                .addColumns(baseFields)
                .from("events")
                .where(SQLParts.profileAndTypes(profileId, configurer.eventTypes()));

        applyConfigurer(configurer);
    }

    private void applyConfigurer(EventQueryConfigurer configurer) {
        RelativeTimeRange timeRange = configurer.timeRange();
        if (timeRange != null) {
            builder.merge(SQLParts.timeRangeOptional(timeRange.start(), timeRange.end()));
        }

        if (configurer.includeFrames()) {
            builder.merge(SQLParts.stacktraces());
        }

        if (configurer.filterStacktraceTypes() != null && !configurer.filterStacktraceTypes().isEmpty()) {
            if (configurer.includeFrames()) {
                // Just add the filter, join already exists
                builder.merge(SQLParts.stacktraceTypesFilterOnly(configurer.filterStacktraceTypes()));
            } else {
                // Need to add join if not already added by includeFrames
                builder.merge(SQLParts.stacktraceTypes(configurer.filterStacktraceTypes()));
            }
        }

        if (configurer.filterStacktraceTags() != null && !configurer.filterStacktraceTags().isEmpty()) {
            builder.merge(SQLParts.stacktraceTags(configurer.filterStacktraceTags()));
        }

        if (configurer.threads()) {
            builder.merge(SQLParts.threads());
        }

        if (configurer.specifiedThread() != null) {
            builder.merge(SQLParts.threadInfo(configurer.specifiedThread()));
        }

        if (configurer.eventTypeInfo()) {
            builder.merge(SQLParts.eventTypes());
        }

        if (configurer.jsonFields()) {
            builder.merge(SQLParts.eventFields());
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
    public String build() {
        return builder.build();
    }
}
