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

package pbouda.jeffrey.provider.profile.query;

import pbouda.jeffrey.shared.common.model.Type;
import pbouda.jeffrey.shared.common.model.time.RelativeTimeRange;
import pbouda.jeffrey.provider.profile.repository.EventQueryConfigurer;
import pbouda.jeffrey.sql.SQLBuilder;

import java.util.List;

public class GenericQueryBuilder implements QueryBuilder {

    private static final List<String> BASE_FIELDS = List.of(
            "events.event_type",
            "events.start_timestamp",
            "EPOCH_MS(events.start_timestamp - fs.first_ts) AS start_timestamp_from_beginning",
            "events.duration",
            "events.samples",
            "events.weight",
            "events.weight_entity");

    private final SQLBuilder builder;
    private final SQLFormatter sqlFormatter;

    public GenericQueryBuilder(SQLFormatter sqlFormatter, EventQueryConfigurer configurer) {
        this(sqlFormatter, configurer, configurer.eventTypes(), BASE_FIELDS);
    }

    public GenericQueryBuilder(SQLFormatter sqlFormatter, EventQueryConfigurer configurer, List<Type> eventTypes) {
        this(sqlFormatter, configurer, eventTypes, BASE_FIELDS);
    }

    public GenericQueryBuilder(
            SQLFormatter sqlFormatter, EventQueryConfigurer configurer, List<Type> eventTypes, List<String> baseFields) {

        if (eventTypes == null || eventTypes.isEmpty()) {
            throw new IllegalArgumentException("Event types must be specified in the configurer.");
        }

        this.sqlFormatter = sqlFormatter;
        this.builder = new SQLBuilder()
                .addColumns(baseFields)
                .from("events")
                .where(sqlFormatter.eventTypes(eventTypes));

        applyConfigurer(configurer);
    }

    private void applyConfigurer(EventQueryConfigurer configurer) {
        RelativeTimeRange timeRange = configurer.timeRange();
        if (timeRange != null) {
            builder.merge(sqlFormatter.timeRangeOptional(timeRange.start(), timeRange.end()));
        }

        if (configurer.threads()) {
            builder.merge(sqlFormatter.threads());
        }

        if (configurer.specifiedThread() != null) {
            builder.merge(sqlFormatter.threadInfo(configurer.specifiedThread()));
        }

        if (configurer.eventTypeInfo()) {
            builder.merge(sqlFormatter.eventTypesInfo());
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
