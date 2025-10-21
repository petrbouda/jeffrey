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

package pbouda.jeffrey.provider.writer.sql.query.builder;

import pbouda.jeffrey.provider.api.repository.EventQueryConfigurer;
import pbouda.jeffrey.provider.writer.sql.query.GenericQueryBuilder;
import pbouda.jeffrey.provider.writer.sql.query.SQLFormatter;
import pbouda.jeffrey.provider.writer.sql.query.timeseries.FilterableTimeseriesQueryBuilder;
import pbouda.jeffrey.provider.writer.sql.query.timeseries.FrameBasedTimeseriesQueryBuilder;
import pbouda.jeffrey.provider.writer.sql.query.timeseries.SimpleTimeseriesQueryBuilder;
import pbouda.jeffrey.provider.writer.sql.query.timeseries.TimeseriesQueryBuilder;

import java.util.List;

public class DefaultQueryBuilderFactory implements QueryBuilderFactory {

    private final SQLFormatter sqlFormatter;
    private final String profileId;

    public DefaultQueryBuilderFactory(SQLFormatter sqlFormatter, String profileId) {
        this.sqlFormatter = sqlFormatter;
        this.profileId = profileId;
    }

    @Override
    public GenericQueryBuilder createGenericQueryBuilder(EventQueryConfigurer configurer) {
        return new GenericQueryBuilder(sqlFormatter, profileId, configurer);
    }

    @Override
    public GenericQueryBuilder createGenericQueryBuilder(EventQueryConfigurer configurer, List<String> baseFields) {
        return new GenericQueryBuilder(sqlFormatter, profileId, configurer, configurer.eventTypes(), baseFields);
    }

    @Override
    public TimeseriesQueryBuilder createSimpleTimeseriesQueryBuilder(EventQueryConfigurer configurer) {
        return new SimpleTimeseriesQueryBuilder(
                sqlFormatter, profileId, configurer.eventTypes().getFirst(), configurer.useWeight())
                .withSpecifiedThread(configurer.specifiedThread())
                .withTimeRange(configurer.timeRange())
                .withStacktraceTypes(configurer.filterStacktraceTypes())
                .withStacktraceTags(configurer.filterStacktraceTags());
    }

    @Override
    public TimeseriesQueryBuilder createFrameBasedTimeseriesQueryBuilder(EventQueryConfigurer configurer) {
        return new FrameBasedTimeseriesQueryBuilder(
                sqlFormatter, profileId, configurer.eventTypes().getFirst(), configurer.useWeight())
                .withTimeRange(configurer.timeRange())
                .withStacktraceTypes(configurer.filterStacktraceTypes())
                .withStacktraceTags(configurer.filterStacktraceTags());
    }

    @Override
    public TimeseriesQueryBuilder createFilterableTimeseriesQueryBuilder(EventQueryConfigurer configurer) {
        return new FilterableTimeseriesQueryBuilder(
                sqlFormatter, profileId, configurer.eventTypes().getFirst(), configurer.useWeight())
                .withTimeRange(configurer.timeRange())
                .withStacktraceTypes(configurer.filterStacktraceTypes())
                .withStacktraceTags(configurer.filterStacktraceTags());
    }
}
