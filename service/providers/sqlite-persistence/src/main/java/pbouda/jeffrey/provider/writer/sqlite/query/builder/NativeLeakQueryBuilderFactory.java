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

package pbouda.jeffrey.provider.writer.sqlite.query.builder;

import pbouda.jeffrey.common.model.Type;
import pbouda.jeffrey.provider.api.repository.EventQueryConfigurer;
import pbouda.jeffrey.provider.writer.sqlite.query.GenericQueryBuilder;
import pbouda.jeffrey.provider.writer.sqlite.query.timeseries.FrameBasedTimeseriesQueryBuilder;
import pbouda.jeffrey.provider.writer.sqlite.query.timeseries.SimpleTimeseriesQueryBuilder;
import pbouda.jeffrey.provider.writer.sqlite.query.timeseries.TimeseriesQueryBuilder;
import pbouda.jeffrey.sql.SQLBuilder;

import java.util.List;

public class NativeLeakQueryBuilderFactory implements QueryBuilderFactory {

    private final String profileId;

    //language=sql
    private static final String FREE_EVENT_EXISTS = """
            SELECT 1 FROM events eFree
            WHERE eFree.profile_id = '<<profile_id>>'
                AND eFree.event_type = 'profiler.Free'
                AND events.weight_entity = eFree.weight_entity
            """;

    private final SQLBuilder builder;

    public NativeLeakQueryBuilderFactory(String profileId) {
        this.profileId = profileId;
        this.builder = new SQLBuilder()
                .where(SQLBuilder.notExists(FREE_EVENT_EXISTS.replace("<<profile_id>>", profileId)));
    }

    @Override
    public GenericQueryBuilder createGenericQueryBuilder(EventQueryConfigurer configurer) {
        return new GenericQueryBuilder(profileId, configurer, List.of(Type.MALLOC))
                .merge(builder);
    }

    @Override
    public GenericQueryBuilder createGenericQueryBuilder(EventQueryConfigurer configurer, List<String> baseFields) {
        return new GenericQueryBuilder(profileId, configurer, List.of(Type.MALLOC), baseFields)
                .merge(builder);
    }

    @Override
    public TimeseriesQueryBuilder createSimpleTimeseriesQueryBuilder(EventQueryConfigurer configurer) {
        return new SimpleTimeseriesQueryBuilder(
                profileId, Type.MALLOC, configurer.useWeight())
                .withSpecifiedThread(configurer.specifiedThread())
                .withTimeRange(configurer.timeRange())
                .withStacktraceTypes(configurer.filterStacktraceTypes())
                .withStacktraceTags(configurer.filterStacktraceTags())
                .merge(builder);
    }

    @Override
    public TimeseriesQueryBuilder createFrameBasedTimeseriesQueryBuilder(EventQueryConfigurer configurer) {
        return new FrameBasedTimeseriesQueryBuilder(
                profileId, Type.MALLOC, configurer.useWeight())
                .withTimeRange(configurer.timeRange())
                .withStacktraceTypes(configurer.filterStacktraceTypes())
                .withStacktraceTags(configurer.filterStacktraceTags())
                .merge(builder);
    }

    @Override
    public TimeseriesQueryBuilder createFilterableTimeseriesQueryBuilder(EventQueryConfigurer configurer) {
        return new FrameBasedTimeseriesQueryBuilder(
                profileId, Type.MALLOC, configurer.useWeight())
                .withTimeRange(configurer.timeRange())
                .withStacktraceTypes(configurer.filterStacktraceTypes())
                .withStacktraceTags(configurer.filterStacktraceTags())
                .merge(builder);
    }
}
