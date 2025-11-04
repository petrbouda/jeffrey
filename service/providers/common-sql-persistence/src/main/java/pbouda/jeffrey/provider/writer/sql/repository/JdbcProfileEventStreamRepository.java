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

package pbouda.jeffrey.provider.writer.sql.repository;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import pbouda.jeffrey.common.model.Type;
import pbouda.jeffrey.common.model.time.RelativeTimeRange;
import pbouda.jeffrey.provider.api.builder.RecordBuilder;
import pbouda.jeffrey.provider.api.repository.EventQueryConfigurer;
import pbouda.jeffrey.provider.api.repository.ProfileEventStreamRepository;
import pbouda.jeffrey.provider.api.repository.model.*;
import pbouda.jeffrey.provider.writer.sql.StatementLabel;
import pbouda.jeffrey.provider.writer.sql.client.DatabaseClient;
import pbouda.jeffrey.provider.writer.sql.client.DatabaseClientProvider;
import pbouda.jeffrey.provider.writer.sql.query.FlamegraphRecordRowMapper;
import pbouda.jeffrey.provider.writer.sql.query.FlamegraphRecordWithThreadsRowMapper;
import pbouda.jeffrey.provider.writer.sql.query.GenericRecordRowMapper;
import pbouda.jeffrey.provider.writer.sql.query.QueryBuilder;
import pbouda.jeffrey.provider.writer.sql.query.builder.QueryBuilderFactory;
import pbouda.jeffrey.provider.writer.sql.query.builder.QueryBuilderFactoryResolver;
import pbouda.jeffrey.provider.writer.sql.query.timeseries.FilterableTimeseriesRecordRowMapper;
import pbouda.jeffrey.provider.writer.sql.query.timeseries.TimeseriesRecordRowMapper;

import java.util.List;

import static pbouda.jeffrey.provider.writer.sql.GroupLabel.PROFILE_EVENTS;

public class JdbcProfileEventStreamRepository implements ProfileEventStreamRepository {

    private record FlamegraphOptions(String sql, SqlParameterSource paramSource, RowMapper<FlamegraphRecord> mapper) {
    }

    private final QueryBuilderFactoryResolver queryBuilderFactoryResolver;
    private final String profileId;
    private final DatabaseClient databaseClient;

    public JdbcProfileEventStreamRepository(
            QueryBuilderFactoryResolver queryBuilderFactoryResolver,
            String profileId,
            DatabaseClientProvider databaseClientProvider) {

        this.queryBuilderFactoryResolver = queryBuilderFactoryResolver;
        this.profileId = profileId;
        this.databaseClient = databaseClientProvider.provide(PROFILE_EVENTS);
    }

    @Override
    public <T> T genericStreaming(EventQueryConfigurer configurer, RecordBuilder<GenericRecord, T> builder) {
        QueryBuilderFactory factory = queryBuilderFactoryResolver.resolve(profileId, configurer.eventTypes());
        return startStreaming(
                factory.createGenericQueryBuilder(configurer),
                new GenericRecordRowMapper(configurer),
                builder);
    }

    @Override
    public <T> T subSecondStreamer(EventQueryConfigurer configurer, RecordBuilder<SubSecondRecord, T> builder) {
        QueryBuilderFactory factory = queryBuilderFactoryResolver.resolve(profileId, configurer.eventTypes());

        String valueField = configurer.useWeight()
                ? "events.weight as value"
                : "events.samples as value";

        List<String> baseFields = List.of("events.start_timestamp_from_beginning", valueField);

        return startStreaming(
                factory.createGenericQueryBuilder(configurer, baseFields),
                (r, _) -> new SubSecondRecord(r.getLong("start_timestamp_from_beginning"), r.getLong("value")),
                builder);
    }

    @Override
    public <T> T timeseriesStreamer(EventQueryConfigurer configurer, RecordBuilder<TimeseriesRecord, T> builder) {
        QueryBuilderFactory factory = queryBuilderFactoryResolver.resolve(profileId, configurer.eventTypes());
        return startStreaming(
                factory.createSimpleTimeseriesQueryBuilder(configurer),
                (r, _) -> TimeseriesRecord.secondsAndValues(r.getLong("seconds"), r.getLong("value")),
                builder);
    }

    @Override
    public <T> T filterableTimeseriesStreamer(EventQueryConfigurer configurer, RecordBuilder<SecondValue, T> builder) {
        QueryBuilderFactory factory = queryBuilderFactoryResolver.resolve(profileId, configurer.eventTypes());
        return startStreaming(
                factory.createFilterableTimeseriesQueryBuilder(configurer),
                new FilterableTimeseriesRecordRowMapper(configurer.jsonFieldsFilter()),
                builder);
    }

    @Override
    public <T> T frameBasedTimeseriesStreamer(EventQueryConfigurer configurer, RecordBuilder<TimeseriesRecord, T> builder) {
        QueryBuilderFactory factory = queryBuilderFactoryResolver.resolve(profileId, configurer.eventTypes());
        return startStreaming(
                factory.createFrameBasedTimeseriesQueryBuilder(configurer),
                new TimeseriesRecordRowMapper(),
                builder);
    }

    @Override
    public <T> T flamegraphStreamer(EventQueryConfigurer configurer, RecordBuilder<FlamegraphRecord, T> builder) {
        // TODO: Native flamegraph will have a special handling very likely
        QueryBuilderFactory factory = queryBuilderFactoryResolver.resolve(profileId, configurer.eventTypes());

        // Flamegraph is always for a single event type
        Type eventType = configurer.eventTypes().getFirst();

        FlamegraphOptions options = flamegraphResolver(profileId, eventType, configurer);
        databaseClient.queryStream(
                StatementLabel.STREAM_EVENTS, options.sql, options.paramSource, options.mapper, builder::onRecord);

        return builder.build();
    }

    private <T, R> R startStreaming(QueryBuilder queryBuilder, RowMapper<T> mapper, RecordBuilder<T, R> builder) {
        databaseClient.queryStream(StatementLabel.STREAM_EVENTS, queryBuilder.build(), mapper, builder::onRecord);
        return builder.build();
    }

    private static FlamegraphOptions flamegraphResolver(
            String profileId, Type eventType, EventQueryConfigurer configurer) {

        MapSqlParameterSource baseParams = new MapSqlParameterSource()
                .addValue("profile_id", profileId)
                .addValue("event_type", eventType.code());

        RelativeTimeRange timeRange = configurer.timeRange();
        if (timeRange != null) {
            baseParams = baseParams
                    .addValue("from_time", timeRange.start() != null ? timeRange.start().toMillis() : null)
                    .addValue("to_time", timeRange.end() != null ? timeRange.end().toMillis() : null);
        } else {
            baseParams = baseParams
                    .addValue("from_time", null)
                    .addValue("to_time", null);
        }

        if (configurer.threads()) {
            Long javaThreadId = configurer.specifiedThread() != null
                    ? configurer.specifiedThread().javaId()
                    : null;

            baseParams.addValue("java_thread_id", javaThreadId);

            String sql = configurer.useWeight()
                    ? DuckDBFlamegraphQueries.STACKTRACE_DETAILS_BY_THREAD_AND_WEIGHT_ENTITY
                    : DuckDBFlamegraphQueries.STACKTRACE_DETAILS_BY_THREAD;

            return new FlamegraphOptions(
                    sql, baseParams, new FlamegraphRecordWithThreadsRowMapper(eventType, configurer.useWeight()));
        } else {
            String sql = configurer.useWeight()
                    ? DuckDBFlamegraphQueries.STACKTRACE_DETAILS_BY_WEIGHT_ENTITY
                    : DuckDBFlamegraphQueries.STACKTRACE_DETAILS;

            return new FlamegraphOptions(
                    sql, baseParams, new FlamegraphRecordRowMapper(eventType, configurer.useWeight()));
        }
    }
}
