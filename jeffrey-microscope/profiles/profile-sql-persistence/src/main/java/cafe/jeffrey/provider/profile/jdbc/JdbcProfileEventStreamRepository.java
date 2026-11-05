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

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import cafe.jeffrey.provider.profile.api.*;
import cafe.jeffrey.jfrparser.api.type.JfrStackFrameImpl;
import cafe.jeffrey.jfrparser.api.type.JfrStackTrace;
import cafe.jeffrey.jfrparser.api.type.JfrStackTraceImpl;
import cafe.jeffrey.microscope.model.FrameResolutionMode;
import cafe.jeffrey.microscope.model.SearchPattern;
import cafe.jeffrey.microscope.model.StacktraceTag;
import cafe.jeffrey.microscope.model.StacktraceType;
import cafe.jeffrey.microscope.model.Type;
import cafe.jeffrey.microscope.model.time.RelativeTimeRange;
import cafe.jeffrey.provider.profile.api.RecordBuilder;
import cafe.jeffrey.shared.persistence.StatementLabel;
import cafe.jeffrey.shared.persistence.client.DatabaseClient;
import cafe.jeffrey.shared.persistence.client.DatabaseClientProvider;
import cafe.jeffrey.provider.profile.jdbc.*;

import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static cafe.jeffrey.shared.persistence.GroupLabel.PROFILE_EVENTS;

public class JdbcProfileEventStreamRepository implements ProfileEventStreamRepository {

    private static final String JSON_ROOT_PATH_PREFIX = "$.";
    private static final String GROUP_PATH_PARAM = "group_path";
    private static final String GROUP_VALUE_CHARS_PARAM = "group_value_chars";
    private static final String STACK_HASHES_PARAM = "stack_hashes";
    private static final String PATH_IDS_PARAM = "path_ids";
    private static final String SITE_LIMIT_PARAM = "site_limit";
    private static final String GROUP_LIMIT_PARAM = "group_limit";
    private static final String STACKTRACE_HASH = "stacktrace_hash";
    private static final String FRAME_HASHES = "frame_hashes";
    private static final String PATH_ID = "path_id";
    private static final String GROUP_VALUE = "group_value";
    private static final String GROUP_VALUE_LENGTH = "group_value_length";
    private static final String GROUP_KEY = "group_key";
    private static final String EVENT_COUNT = "event_count";
    private static final String TOTAL_DURATION = "total_duration";
    private static final String MAX_DURATION = "max_duration";
    private static final String TRACES = "traces";
    private static final String PER_TRACE_MAX = "per_trace_max";
    private static final String PER_TRACE_MEDIAN = "per_trace_median";
    private static final String EXEMPLARS = "exemplars";
    private static final String ALL_EVENTS = "all_events";
    private static final String ALL_DURATION = "all_duration";
    private static final String UNATTRIBUTED_EVENTS = "unattributed_events";
    private static final String ALL_SITES = "all_sites";
    private static final String ALL_GROUPS = "all_groups";
    private static final String GROUP_EVENT_COUNT = "group_event_count";
    private static final String GROUP_TOTAL_DURATION = "group_total_duration";
    private static final String GROUP_SITES = "group_sites";
    private static final String ROOT_NAME = "root_name";
    private static final String ROOT_KIND = "root_kind";
    private static final String ROOT_EVENT_TYPE = "root_event_type";
    private static final String SEARCH_LITERAL_PARAM = "search_literal";
    private static final String SEARCH_REGEX_PARAM = "search_regex";

    private record FlamegraphOptions(String sql, SqlParameterSource paramSource, RowMapper<FlamegraphRecord> mapper) {
    }

    private final QueryBuilderFactoryResolver queryBuilderFactoryResolver;
    private final DatabaseClient databaseClient;
    private final FrameResolutionMode frameResolutionMode;
    private final FramesCacheSlot framesCacheSlot;

    public JdbcProfileEventStreamRepository(
            QueryBuilderFactoryResolver queryBuilderFactoryResolver,
            DatabaseClientProvider databaseClientProvider,
            FrameResolutionMode frameResolutionMode,
            FramesCacheSlot framesCacheSlot) {

        this.queryBuilderFactoryResolver = queryBuilderFactoryResolver;
        this.databaseClient = databaseClientProvider.provide(PROFILE_EVENTS);
        this.frameResolutionMode = frameResolutionMode;
        this.framesCacheSlot = framesCacheSlot;
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
                factory.complexQueries().subSecond().simple(configurer),
                baseParams,
                (r, _) -> new SubSecondRecord(r.getLong("start_ms_offset"), r.getLong("value")),
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
                timeseries.simple(configurer),
                baseParams,
                (r, _) -> TimeseriesRecord.secondsAndValues(r.getLong("seconds"), r.getLong("value")),
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
                timeseries.simpleSearch(configurer),
                baseParams,
                (r, _) -> new TimeseriesSearchRecord(r.getLong("seconds"), r.getLong("total_value"), r.getLong("matched_value")),
                builder::onRecord);

        return builder.build();
    }

    @Override
    public <T> T filterableTimeseriesStreamer(EventQueryConfigurer configurer, RecordBuilder<SecondValue, T> builder) {
        QueryBuilderFactory factory = queryBuilderFactoryResolver.resolve(configurer.eventTypes());

        MapSqlParameterSource baseParams = createBaseParams(configurer);
        applyJsonFieldFilter(baseParams, configurer.jsonFieldFilter());

        databaseClient.queryStream(
                StatementLabel.STREAM_EVENTS,
                factory.complexQueries().timeseries().filterable(configurer),
                baseParams,
                (r, _) -> new SecondValue(r.getLong("seconds"), r.getLong("samples")),
                builder::onRecord);

        return builder.build();
    }

    private static void applyJsonFieldFilter(
            MapSqlParameterSource baseParams, EventQueryConfigurer.JsonFieldFilter jsonFieldFilter) {

        if (jsonFieldFilter != null) {
            baseParams.addValue("json_field_path", JSON_ROOT_PATH_PREFIX + jsonFieldFilter.field());
            baseParams.addValue("json_field_value", jsonFieldFilter.value());
        } else {
            baseParams.addValue("json_field_path", null);
            baseParams.addValue("json_field_value", null);
        }
    }

    @Override
    public <T> T frameBasedTimeseriesStreamer(EventQueryConfigurer configurer, RecordBuilder<TimeseriesRecord, T> builder) {
        QueryBuilderFactory factory = queryBuilderFactoryResolver.resolve(configurer.eventTypes());

        MapSqlParameterSource baseParams = createBaseParams(configurer);

        databaseClient.queryStream(
                StatementLabel.STREAM_EVENTS,
                factory.complexQueries().timeseries().frameBased(configurer),
                baseParams,
                new TimeseriesRecordRowMapper(),
                builder::onRecord);

        return builder.build();
    }

    @Override
    public <T> T frameBasedEventStreamer(EventQueryConfigurer configurer, RecordBuilder<TimeseriesRecord, T> builder) {
        QueryBuilderFactory factory = queryBuilderFactoryResolver.resolve(configurer.eventTypes());

        MapSqlParameterSource baseParams = createBaseParams(configurer);

        databaseClient.queryStream(
                StatementLabel.STREAM_EVENTS,
                factory.complexQueries().timeseries().frameBasedEvents(configurer),
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

    @Override
    public <T> T callSites(EventQueryConfigurer configurer, String groupField,
                           CallSiteSelection selection, CallSiteReader<T> reader) {
        MapSqlParameterSource params = createBaseParams(configurer);
        applyJsonFieldFilter(params, configurer.jsonFieldFilter());
        if (groupField != null) {
            params.addValue(GROUP_PATH_PARAM, JSON_ROOT_PATH_PREFIX + DuckDBCallSiteQueries.requireFieldName(groupField));
        }
        params.addValue(GROUP_VALUE_CHARS_PARAM, CallSiteRow.MAX_GROUP_VALUE_CHARS);
        switch (selection) {
            case CallSiteSelection.Sites(int limit) -> params
                    .addValue(SITE_LIMIT_PARAM, limit).addValue(GROUP_LIMIT_PARAM, null);
            case CallSiteSelection.Groups(int limit, int sitesPerGroup) -> params
                    .addValue(SITE_LIMIT_PARAM, sitesPerGroup).addValue(GROUP_LIMIT_PARAM, limit);
        }
        FramesCache framesCache = framesCacheSlot.resolve(() -> FramesCache.load(databaseClient));

        // The stacks first, each charged to a path by the reader; the database then aggregates by path.
        Map<Long, JfrStackTrace> stacks = new LinkedHashMap<>();
        List<Long> stackHashes = new ArrayList<>();
        List<Integer> pathIds = new ArrayList<>();
        databaseClient.queryStream(
                StatementLabel.STREAM_EVENTS,
                DuckDBCallSiteQueries.stacks(configurer),
                params,
                (rs, _) -> stackOf(rs, framesCache),
                stack -> {
                    stacks.put(stack.id(), stack);
                    stackHashes.add(stack.id());
                    pathIds.add(reader.pathOf(stack));
                });
        params.addValue(STACK_HASHES_PARAM, stackHashes)
                .addValue(PATH_IDS_PARAM, pathIds);
        databaseClient.queryStream(
                StatementLabel.STREAM_EVENTS,
                DuckDBCallSiteQueries.sites(groupField, configurer, CallSiteRow.MAX_EXEMPLARS),
                params,
                (rs, _) -> callSiteResultOf(rs),
                new CallSiteResults<>(reader));
        if (stacks.isEmpty()) {
            return reader.build();
        }
        databaseClient.queryStream(
                StatementLabel.STREAM_EVENTS,
                DuckDBCallSiteQueries.operationStacks(configurer),
                params,
                (rs, _) -> new OperationStackRow(
                        stacks.get(rs.getLong(STACKTRACE_HASH)),
                        new TraceOperationId(rs.getString(ROOT_NAME), rs.getString(ROOT_KIND),
                                rs.getString(ROOT_EVENT_TYPE)),
                        rs.getLong(EVENT_COUNT)),
                row -> {
                    if (row.stackTrace() != null) {
                        reader.onOperationStack(row);
                    }
                });
        return reader.build();
    }

    private record CallSiteResult(CallSiteTotals totals, CallSiteGroupRow group, CallSiteRow site) {
    }

    /** The result repeats complete aggregates per selected row; the protocol hands each one over once. */
    private static final class CallSiteResults<T> implements Consumer<CallSiteResult> {
        private final CallSiteReader<T> reader;
        private final Set<String> groups = new HashSet<>();
        private boolean totalsRead;

        private CallSiteResults(CallSiteReader<T> reader) {
            this.reader = reader;
        }

        @Override
        public void accept(CallSiteResult row) {
            if (!totalsRead) {
                reader.onTotals(row.totals());
                totalsRead = true;
            }
            if (row.group() != null && groups.add(row.group().key())) {
                reader.onGroup(row.group());
            }
            if (row.site() != null) {
                reader.onSite(row.site());
            }
        }
    }

    private static CallSiteResult callSiteResultOf(ResultSet rs) throws SQLException {
        CallSiteTotals totals = new CallSiteTotals(rs.getLong(ALL_EVENTS), rs.getLong(ALL_DURATION),
                rs.getLong(UNATTRIBUTED_EVENTS), rs.getLong(ALL_SITES), rs.getLong(ALL_GROUPS));
        if (rs.getObject(PATH_ID) == null) {
            return new CallSiteResult(totals, null, null);
        }
        CallSiteRow site = siteOf(rs);
        CallSiteGroupRow group = new CallSiteGroupRow(site.groupKey(), site.groupValue(), site.groupValueChars(),
                rs.getLong(GROUP_EVENT_COUNT), rs.getLong(GROUP_TOTAL_DURATION), rs.getLong(GROUP_SITES));
        return new CallSiteResult(totals, group, site);
    }

    private static JfrStackTrace stackOf(ResultSet rs, FramesCache framesCache) throws SQLException {
        Array frameHashes = rs.getArray(FRAME_HASHES);
        List<JfrStackFrameImpl> frames = frameHashes == null
                ? List.of()
                : framesCache.resolveFrames(FlamegraphMapperUtils.toFrameHashArray(frameHashes));
        return new JfrStackTraceImpl(rs.getLong(STACKTRACE_HASH), frames);
    }

    private static CallSiteRow siteOf(ResultSet rs) throws SQLException {
        int length = rs.getInt(GROUP_VALUE_LENGTH);
        Integer groupValueChars = rs.wasNull() ? null : length;
        Array exemplars = rs.getArray(EXEMPLARS);
        List<Long> exemplarTraceIds = new ArrayList<>();
        if (exemplars != null) {
            for (Object id : (Object[]) exemplars.getArray()) {
                exemplarTraceIds.add(((Number) id).longValue());
            }
        }
        return new CallSiteRow(
                rs.getInt(PATH_ID),
                rs.getString(GROUP_VALUE),
                groupValueChars,
                rs.getString(GROUP_KEY),
                rs.getLong(EVENT_COUNT),
                rs.getLong(TOTAL_DURATION),
                rs.getLong(MAX_DURATION),
                rs.getInt(TRACES),
                rs.getLong(PER_TRACE_MAX),
                rs.getDouble(PER_TRACE_MEDIAN),
                exemplarTraceIds);
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
            // Frames of the currently-open profile are cached between generations and invalidated
            // by frame-mutating operations (class renaming, stacktrace transformations)
            FramesCache framesCache = framesCacheSlot.resolve(() -> FramesCache.load(databaseClient));

            if (configurer.threads()) {
                return new FlamegraphOptions(
                        flamegraphQueries.byThreadAndWeightOptimized(configurer),
                        baseParams,
                        new CachingFlamegraphRecordRowMapper(eventType, framesCache, true, true));
            } else {
                return new FlamegraphOptions(
                        flamegraphQueries.byWeightOptimized(configurer),
                        baseParams,
                        new CachingFlamegraphRecordRowMapper(eventType, framesCache, true, false));
            }
        }

        // DATABASE mode: SQL-side frame resolution (original implementation)
        if (configurer.threads()) {
            return new FlamegraphOptions(
                    flamegraphQueries.byThreadAndWeight(configurer),
                    baseParams,
                    new FlamegraphRecordRowMapper(eventType, true));
        } else {
            return new FlamegraphOptions(
                    flamegraphQueries.byWeight(configurer),
                    baseParams,
                    new FlamegraphRecordRowMapper(eventType, false));
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
            SearchPattern searchPattern = SearchPattern.of(configurer.searchPattern());
            baseParams = baseParams
                    .addValue(SEARCH_LITERAL_PARAM, searchPattern.literal())
                    .addValue(SEARCH_REGEX_PARAM, searchPattern.regexOrNull());
        } else {
            baseParams = baseParams
                    .addValue(SEARCH_LITERAL_PARAM, null)
                    .addValue(SEARCH_REGEX_PARAM, null);
        }

        ThreadIdParams.apply(baseParams, configurer.specifiedThreads());

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

        SpanScopeSql.apply(baseParams, configurer.spanScope());

        return baseParams;
    }
}
