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

package pbouda.jeffrey.provider.profile.repository;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import pbouda.jeffrey.provider.profile.model.*;
import pbouda.jeffrey.shared.common.FrameResolutionMode;
import pbouda.jeffrey.shared.common.model.StacktraceTag;
import pbouda.jeffrey.shared.common.model.StacktraceType;
import pbouda.jeffrey.shared.common.model.Type;
import pbouda.jeffrey.shared.common.model.time.RelativeTimeRange;
import pbouda.jeffrey.provider.profile.builder.RecordBuilder;
import pbouda.jeffrey.shared.persistence.StatementLabel;
import pbouda.jeffrey.shared.persistence.client.DatabaseClient;
import pbouda.jeffrey.shared.persistence.client.DatabaseClientProvider;
import pbouda.jeffrey.provider.profile.query.*;
import pbouda.jeffrey.provider.profile.query.builder.QueryBuilderFactory;
import pbouda.jeffrey.provider.profile.query.builder.QueryBuilderFactoryResolver;
import pbouda.jeffrey.provider.profile.query.timeseries.FilterableTimeseriesRecordRowMapper;
import pbouda.jeffrey.provider.profile.query.timeseries.TimeseriesRecordRowMapper;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static pbouda.jeffrey.shared.persistence.GroupLabel.PROFILE_EVENTS;

public class JdbcProfileEventStreamRepository implements ProfileEventStreamRepository {

    private record FlamegraphOptions(String sql, SqlParameterSource paramSource, RowMapper<FlamegraphRecord> mapper) {
    }

    private final QueryBuilderFactoryResolver queryBuilderFactoryResolver;
    private final DatabaseClient databaseClient;
    private final FrameResolutionMode frameResolutionMode;

    public JdbcProfileEventStreamRepository(
            QueryBuilderFactoryResolver queryBuilderFactoryResolver,
            DatabaseClientProvider databaseClientProvider,
            FrameResolutionMode frameResolutionMode) {

        this.queryBuilderFactoryResolver = queryBuilderFactoryResolver;
        this.databaseClient = databaseClientProvider.provide(PROFILE_EVENTS);
        this.frameResolutionMode = frameResolutionMode;
    }

    @Override
    public <T> T genericStreaming(EventQueryConfigurer configurer, RecordBuilder<GenericRecord, T> builder) {
        QueryBuilderFactory factory = queryBuilderFactoryResolver.resolve(configurer.eventTypes());
        return startStreaming(
                factory.createGenericQueryBuilder(configurer),
                new GenericRecordRowMapper(configurer),
                builder);
    }

    @Override
    public <T> T subSecondStreamer(EventQueryConfigurer configurer, RecordBuilder<SubSecondRecord, T> builder) {
        QueryBuilderFactory factory = queryBuilderFactoryResolver.resolve(configurer.eventTypes());

        MapSqlParameterSource baseParams = createBaseParams(configurer);

        databaseClient.queryStream(
                StatementLabel.STREAM_EVENTS,
                factory.complexQueries().subSecond().simple(configurer.useWeight()),
                baseParams,
                (r, __) -> new SubSecondRecord(r.getLong("start_ms_offset"), r.getLong("value")),
                builder::onRecord);

        return builder.build();
    }

    @Override
    public <T> T timeseriesStreamer(EventQueryConfigurer configurer, RecordBuilder<TimeseriesRecord, T> builder) {
        QueryBuilderFactory factory = queryBuilderFactoryResolver.resolve(configurer.eventTypes());

        MapSqlParameterSource baseParams = createBaseParams(configurer);

        ComplexQueries.Timeseries timeseries = factory.complexQueries().timeseries();
        databaseClient.queryStream(
                StatementLabel.STREAM_EVENTS,
                timeseries.simple(configurer.useWeight(), configurer.specifiedThread() != null),
                baseParams,
                (r, __) -> TimeseriesRecord.secondsAndValues(r.getLong("seconds"), r.getLong("value")),
                builder::onRecord);

        return builder.build();
    }

    @Override
    public <T> T timeseriesSearchingStreamer(EventQueryConfigurer configurer, RecordBuilder<TimeseriesSearchRecord, T> builder) {
        QueryBuilderFactory factory = queryBuilderFactoryResolver.resolve(configurer.eventTypes());

        MapSqlParameterSource baseParams = createBaseParams(configurer);

        ComplexQueries.Timeseries timeseries = factory.complexQueries().timeseries();
        databaseClient.queryStream(
                StatementLabel.STREAM_EVENTS,
                timeseries.simpleSearch(configurer.useWeight(), configurer.specifiedThread() != null),
                baseParams,
                (r, __) -> new TimeseriesSearchRecord(r.getLong("seconds"), r.getLong("total_value"), r.getLong("matched_value")),
                builder::onRecord);

        return builder.build();
    }

    @Override
    public <T> T filterableTimeseriesStreamer(EventQueryConfigurer configurer, RecordBuilder<SecondValue, T> builder) {
        QueryBuilderFactory factory = queryBuilderFactoryResolver.resolve(configurer.eventTypes());

        MapSqlParameterSource baseParams = createBaseParams(configurer);

        databaseClient.queryStream(
                StatementLabel.STREAM_EVENTS,
                factory.complexQueries().timeseries().filterable(configurer.useWeight()),
                baseParams,
                new FilterableTimeseriesRecordRowMapper(configurer.jsonFieldsFilter()),
                builder::onRecord);

        return builder.build();
    }

    @Override
    public <T> T frameBasedTimeseriesStreamer(EventQueryConfigurer configurer, RecordBuilder<TimeseriesRecord, T> builder) {
        QueryBuilderFactory factory = queryBuilderFactoryResolver.resolve(configurer.eventTypes());

        MapSqlParameterSource baseParams = createBaseParams(configurer);

        databaseClient.queryStream(
                StatementLabel.STREAM_EVENTS,
                factory.complexQueries().timeseries().frameBased(configurer.useWeight()),
                baseParams,
                new TimeseriesRecordRowMapper(),
                builder::onRecord);

        return builder.build();
    }

    @Override
    public <T> T flamegraphStreamer(EventQueryConfigurer configurer, RecordBuilder<FlamegraphRecord, T> builder) {
        QueryBuilderFactory factory = queryBuilderFactoryResolver.resolve(configurer.eventTypes());

        // Flamegraph is always for a single event type
        Type eventType = configurer.eventTypes().getFirst();

        FlamegraphOptions options = flamegraphResolver(factory, eventType, configurer);
        databaseClient.queryStream(
                StatementLabel.STREAM_EVENTS, options.sql, options.paramSource, options.mapper, builder::onRecord);

        return builder.build();
    }

    private <T, R> R startStreaming(QueryBuilder queryBuilder, RowMapper<T> mapper, RecordBuilder<T, R> builder) {
        databaseClient.queryStream(StatementLabel.STREAM_EVENTS, queryBuilder.build(), mapper, builder::onRecord);
        return builder.build();
    }

    private FlamegraphOptions flamegraphResolver(
            QueryBuilderFactory factory, Type eventType, EventQueryConfigurer configurer) {

        MapSqlParameterSource baseParams = createBaseParams(configurer);

        // DuckDBFlamegraphQueries is required for optimized queries
        DuckDBFlamegraphQueries flamegraphQueries = (DuckDBFlamegraphQueries) factory.complexQueries().flamegraph();

        if (frameResolutionMode == FrameResolutionMode.CACHE) {
            // Load frames fresh for this request - no caching between generations
            FramesCache framesCache = FramesCache.load(databaseClient);

            if (configurer.threads()) {
                return new FlamegraphOptions(
                        flamegraphQueries.byThreadAndWeightOptimized(),
                        baseParams,
                        new CachingFlamegraphRecordWithThreadsRowMapper(eventType, framesCache, true));
            } else {
                return new FlamegraphOptions(
                        flamegraphQueries.byWeightOptimized(),
                        baseParams,
                        new CachingFlamegraphRecordRowMapper(eventType, framesCache, true));
            }
        }

        // DATABASE mode: SQL-side frame resolution (original implementation)
        if (configurer.threads()) {
            return new FlamegraphOptions(
                    flamegraphQueries.byThreadAndWeight(),
                    baseParams,
                    new FlamegraphRecordWithThreadsRowMapper(eventType));
        } else {
            return new FlamegraphOptions(
                    flamegraphQueries.byWeight(),
                    baseParams,
                    new FlamegraphRecordRowMapper(eventType));
        }
    }

    private static MapSqlParameterSource createBaseParams(EventQueryConfigurer configurer) {
        // Flamegraph is always for a single event type
        Type eventType = configurer.eventTypes().getFirst();

        MapSqlParameterSource baseParams = new MapSqlParameterSource()
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

        if (configurer.searchPattern() != null && !configurer.searchPattern().isBlank()) {
            baseParams = baseParams.addValue("search_pattern", configurer.searchPattern());
        } else {
            baseParams = baseParams.addValue("search_pattern", null);
        }

        if (configurer.threads()) {
            boolean specifiedThread = configurer.specifiedThread() != null;
            Long javaThreadId = specifiedThread ? configurer.specifiedThread().javaId() : null;
            Long osThreadId = specifiedThread ? configurer.specifiedThread().osId() : null;

            baseParams.addValue("java_thread_id", javaThreadId);
            baseParams.addValue("os_thread_id", osThreadId);
        } else {
            baseParams = baseParams
                    .addValue("java_thread_id", null)
                    .addValue("os_thread_id", null);
        }

        List<StacktraceType> stacktraceTypes = configurer.filterStacktraceTypes();
        if (stacktraceTypes != null && !stacktraceTypes.isEmpty()) {
            List<Integer> typeIds = stacktraceTypes.stream()
                    .map(StacktraceType::id)
                    .toList();
            baseParams = baseParams.addValue("stacktrace_types", typeIds);
        } else {
            baseParams = baseParams.addValue("stacktrace_types", null);
        }

        List<StacktraceTag> stacktraceTags = configurer.filterStacktraceTags();
        if (stacktraceTags != null && !stacktraceTags.isEmpty()) {
            Map<Boolean, List<StacktraceTag>> partitioned = stacktraceTags.stream()
                    .collect(Collectors.partitioningBy(StacktraceTag::includes));

            List<Integer> includedTagIds = partitioned.get(true).stream()
                    .map(StacktraceTag::id)
                    .toList();
            List<Integer> excludedTagIds = partitioned.get(false).stream()
                    .map(StacktraceTag::id)
                    .toList();

            baseParams = baseParams
                    .addValue("included_tags", includedTagIds.isEmpty() ? null : includedTagIds)
                    .addValue("excluded_tags", excludedTagIds.isEmpty() ? null : excludedTagIds);
        } else {
            baseParams = baseParams
                    .addValue("included_tags", null)
                    .addValue("excluded_tags", null);
        }

        return baseParams;
    }
}
