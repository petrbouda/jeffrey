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

import org.springframework.jdbc.core.RowMapper;
import pbouda.jeffrey.provider.api.repository.EventQueryConfigurer;
import pbouda.jeffrey.provider.api.streamer.EventStreamer;
import pbouda.jeffrey.provider.api.streamer.EventStreamerFactory;
import pbouda.jeffrey.provider.api.streamer.model.*;
import pbouda.jeffrey.provider.writer.sqlite.client.DatabaseClient;
import pbouda.jeffrey.provider.writer.sqlite.query.timeseries.*;

import java.util.List;

public class JdbcEventStreamerFactory implements EventStreamerFactory {

    private static final RowMapper<TimeseriesRecord> SIMPLE_TIMESERIES_RECORD_MAPPER =
            (r, _) -> TimeseriesRecord.secondsAndValues(r.getLong("seconds"), r.getLong("value"));

    private final DatabaseClient databaseClient;
    private final String profileId;

    public JdbcEventStreamerFactory(DatabaseClient databaseClient, String profileId) {
        this.databaseClient = databaseClient;
        this.profileId = profileId;
    }

    @Override
    public EventStreamer<SubSecondRecord> newSubSecondStreamer(EventQueryConfigurer configurer) {
        RowMapper<SubSecondRecord> mapper = (r, _) ->
                new SubSecondRecord(r.getLong("start_timestamp_from_beginning"), r.getLong("value"));

        String valueField = configurer.useWeight()
                ? "events.weight as value"
                : "events.samples as value";

        List<String> baseFields = List.of("events.start_timestamp_from_beginning", valueField);
        GenericQueryBuilder queryBuilder = new GenericQueryBuilder(profileId, configurer, baseFields);

        return new JdbcEventStreamer<>(databaseClient, mapper, queryBuilder);
    }

    @Override
    public EventStreamer<TimeseriesRecord> newSimpleTimeseriesStreamer(EventQueryConfigurer configurer) {
        TimeseriesQueryBuilder queryBuilder = new SimpleTimeseriesQueryBuilder(
                profileId, configurer.eventTypes().getFirst(), configurer.useWeight())
                .withTimeRange(configurer.timeRange())
                .withSpecifiedThread(configurer.specifiedThread())
                .withStacktraceTypes(configurer.filterStacktraceTypes())
                .withStacktraceTags(configurer.filterStacktraceTags());

        return new JdbcEventStreamer<>(databaseClient, SIMPLE_TIMESERIES_RECORD_MAPPER, queryBuilder);
    }

    @Override
    public EventStreamer<SecondValue> newFilterableTimeseriesStreamer(EventQueryConfigurer configurer) {
        TimeseriesQueryBuilder queryBuilder = new FilterableTimeseriesQueryBuilder(
                profileId, configurer.eventTypes().getFirst(), configurer.useWeight())
                .withTimeRange(configurer.timeRange())
                .withStacktraceTypes(configurer.filterStacktraceTypes())
                .withStacktraceTags(configurer.filterStacktraceTags());

        var rowMapper = new FilterableTimeseriesRecordRowMapper(configurer.jsonFieldsFilter());
        return new JdbcEventStreamer<>(databaseClient, rowMapper, queryBuilder);
    }

    @Override
    public EventStreamer<TimeseriesRecord> newFrameBasedTimeseriesStreamer(EventQueryConfigurer configurer) {
        TimeseriesQueryBuilder queryBuilder = new FrameBasedTimeseriesQueryBuilder(
                profileId, configurer.eventTypes().getFirst(), configurer.useWeight())
                .withTimeRange(configurer.timeRange())
                .withStacktraceTypes(configurer.filterStacktraceTypes())
                .withStacktraceTags(configurer.filterStacktraceTags());

        return new JdbcEventStreamer<>(databaseClient, new TimeseriesRecordRowMapper(), queryBuilder);
    }

    @Override
    public EventStreamer<FlamegraphRecord> newFlamegraphStreamer(EventQueryConfigurer configurer) {
        List<String> baseFields = List.of(
                "sum(events.samples) AS samples",
                "sum(events.weight) AS weight",
                "events.weight_entity");

        // Always include stackframes (otherwise flamegraph cannot be generated)
        configurer.withIncludeFrames();

        GenericQueryBuilder queryBuilder = new GenericQueryBuilder(profileId, configurer, baseFields)
                .addGroupBy("events.stacktrace_id");

        return new JdbcEventStreamer<>(databaseClient, new FlamegraphRecordRowMapper(configurer), queryBuilder);
    }

    @Override
    public EventStreamer<GenericRecord> newGenericStreamer(EventQueryConfigurer configurer) {
        return new JdbcEventStreamer<>(
                databaseClient,
                new GenericRecordRowMapper(configurer),
                new GenericQueryBuilder(profileId, configurer));
    }
}
