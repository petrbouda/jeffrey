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
import pbouda.jeffrey.provider.api.streamer.model.FlamegraphRecord;
import pbouda.jeffrey.provider.api.streamer.model.GenericRecord;
import pbouda.jeffrey.provider.api.streamer.model.SecondValue;
import pbouda.jeffrey.provider.api.streamer.model.SubSecondRecord;
import pbouda.jeffrey.provider.api.streamer.model.TimeseriesRecord;
import pbouda.jeffrey.provider.writer.sqlite.client.DatabaseClient;
import pbouda.jeffrey.provider.writer.sqlite.query.builder.QueryBuilderFactory;
import pbouda.jeffrey.provider.writer.sqlite.query.timeseries.FilterableTimeseriesRecordRowMapper;
import pbouda.jeffrey.provider.writer.sqlite.query.timeseries.TimeseriesRecordRowMapper;

import java.util.List;

public class JdbcEventStreamerFactory implements EventStreamerFactory {

    private static final RowMapper<TimeseriesRecord> SIMPLE_TIMESERIES_RECORD_MAPPER =
            (r, _) -> TimeseriesRecord.secondsAndValues(r.getLong("seconds"), r.getLong("value"));

    private final DatabaseClient databaseClient;
    private final EventQueryConfigurer configurer;
    private final QueryBuilderFactory queryBuilderFactory;

    public JdbcEventStreamerFactory(
            DatabaseClient databaseClient,
            EventQueryConfigurer configurer,
            QueryBuilderFactory queryBuilderFactory) {

        this.databaseClient = databaseClient;
        this.configurer = configurer;
        this.queryBuilderFactory = queryBuilderFactory;
    }

    @Override
    public EventStreamer<SubSecondRecord> newSubSecondStreamer() {
        RowMapper<SubSecondRecord> mapper = (r, _) ->
                new SubSecondRecord(r.getLong("start_timestamp_from_beginning"), r.getLong("value"));

        String valueField = configurer.useWeight()
                ? "events.weight as value"
                : "events.samples as value";

        List<String> baseFields = List.of("events.start_timestamp_from_beginning", valueField);
        return new JdbcEventStreamer<>(
                databaseClient, mapper, queryBuilderFactory.createGenericQueryBuilder(configurer, baseFields));
    }

    @Override
    public EventStreamer<TimeseriesRecord> newSimpleTimeseriesStreamer() {
        return new JdbcEventStreamer<>(
                databaseClient,
                SIMPLE_TIMESERIES_RECORD_MAPPER,
                queryBuilderFactory.createSimpleTimeseriesQueryBuilder(configurer));
    }

    @Override
    public EventStreamer<SecondValue> newFilterableTimeseriesStreamer() {
        return new JdbcEventStreamer<>(
                databaseClient,
                new FilterableTimeseriesRecordRowMapper(configurer.jsonFieldsFilter()),
                queryBuilderFactory.createFilterableTimeseriesQueryBuilder(configurer));
    }

    @Override
    public EventStreamer<TimeseriesRecord> newFrameBasedTimeseriesStreamer() {
        return new JdbcEventStreamer<>(
                databaseClient,
                new TimeseriesRecordRowMapper(),
                queryBuilderFactory.createFrameBasedTimeseriesQueryBuilder(configurer));
    }

    @Override
    public EventStreamer<FlamegraphRecord> newFlamegraphStreamer() {
        List<String> baseFields = List.of(
                "sum(events.samples) AS samples",
                "sum(events.weight) AS weight",
                "events.weight_entity");

        // Always include stackframes (otherwise flamegraph cannot be generated)
        configurer.withIncludeFrames();

        QueryBuilder queryBuilder = queryBuilderFactory.createGenericQueryBuilder(configurer, baseFields)
                .addGroupBy("events.stacktrace_id");

        return new JdbcEventStreamer<>(databaseClient, new FlamegraphRecordRowMapper(configurer), queryBuilder);
    }

    @Override
    public EventStreamer<GenericRecord> newGenericStreamer() {
        return new JdbcEventStreamer<>(
                databaseClient,
                new GenericRecordRowMapper(configurer),
                queryBuilderFactory.createGenericQueryBuilder(configurer));
    }
}
