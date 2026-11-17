/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
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

import cafe.jeffrey.provider.profile.api.*;

import cafe.jeffrey.microscope.model.Type;
import cafe.jeffrey.microscope.model.time.RelativeTimeRange;
import cafe.jeffrey.provider.profile.api.EventQueryConfigurer;
import cafe.jeffrey.sql.SQLBuilder;

import java.util.List;

public class GenericQueryBuilder implements QueryBuilder {

    /**
     * Chronological streaming order for consumers that pair or sequence events. The events table is
     * physically clustered by (event_type, time), so scan order is not guaranteed to be
     * chronological — an explicit ORDER BY is required.
     */
    private static final String ORDER_BY_TIME = "events.start_timestamp";

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

        if (!configurer.specifiedThreads().isEmpty()) {
            builder.merge(sqlFormatter.threadInfo(configurer.specifiedThreads()));
        }

        if (configurer.eventTypeInfo()) {
            builder.merge(sqlFormatter.eventTypesInfo());
        }

        if (configurer.jsonFields()) {
            builder.merge(sqlFormatter.eventFields());
        }

        if (configurer.isOrderedByTime()) {
            builder.orderBy(ORDER_BY_TIME);
        }
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
