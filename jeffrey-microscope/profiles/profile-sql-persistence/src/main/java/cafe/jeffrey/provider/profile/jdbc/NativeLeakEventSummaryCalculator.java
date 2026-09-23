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

import tools.jackson.core.type.TypeReference;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import cafe.jeffrey.shared.common.Json;
import cafe.jeffrey.microscope.model.EventSummary;
import cafe.jeffrey.microscope.model.RecordingEventSource;
import cafe.jeffrey.microscope.model.Type;
import cafe.jeffrey.provider.profile.api.FieldDescription;
import cafe.jeffrey.shared.persistence.GroupLabel;
import cafe.jeffrey.shared.persistence.StatementLabel;
import cafe.jeffrey.shared.persistence.client.DatabaseClient;
import cafe.jeffrey.shared.persistence.client.DatabaseClientProvider;

import java.util.List;

public class NativeLeakEventSummaryCalculator implements EventSummaryCalculator {

    private static final TypeReference<List<FieldDescription>> FIELD_DESC =
            new TypeReference<List<FieldDescription>>() {
            };

    private record SamplesAndWeight(long samples, long weight) {
    }

    //language=SQL
    private static final String MALLOC_AND_FREE_EXISTS = """
            SELECT count(*) FROM event_types
                WHERE name = 'profiler.Malloc' OR name = 'profiler.Free'""";

    //language=SQL
    private static final String SELECT_MALLOC_EVENT_TYPE_COLUMNS =
            "SELECT columns FROM event_types WHERE name = 'profiler.Malloc'";

    //language=SQL
    private static final String SELECT_NATIVE_LEAK_EVENTS_SAMPLES_AND_WEIGHT = """
            SELECT count(eMalloc.samples) AS samples, sum(eMalloc.weight) AS weight FROM events eMalloc
            WHERE eMalloc.event_type = 'profiler.Malloc'
            AND NOT EXISTS (
                SELECT 1 FROM events eFree
                   WHERE eFree.event_type = 'profiler.Free'
                        AND eMalloc.weight_entity = eFree.weight_entity
            )""";

    private static final MapSqlParameterSource EMPTY_PARAMS = new MapSqlParameterSource();

    private final DatabaseClient databaseClient;

    public NativeLeakEventSummaryCalculator(DatabaseClientProvider databaseClientProvider) {
        this.databaseClient = databaseClientProvider.provide(GroupLabel.NATIVE_LEAK_EVENTS);
    }

    @Override
    public EventSummary eventSummary() {
        SamplesAndWeight samplesAndWeight = databaseClient.querySingle(
                StatementLabel.FIND_NATIVE_LEAK_EVENTS_SAMPLES_AND_WEIGHT,
                SELECT_NATIVE_LEAK_EVENTS_SAMPLES_AND_WEIGHT,
                EMPTY_PARAMS,
                samplesAndWeightMapper()).get();

        return new EventSummary(
                Type.NATIVE_LEAK.code(),
                "Native Leak",
                RecordingEventSource.ASYNC_PROFILER,
                null,
                samplesAndWeight.samples,
                samplesAndWeight.weight,
                true,
                true,
                List.of("Java Virtual Machine", "Native Memory"),
                null,
                null);
    }

    @Override
    public List<FieldDescription> fieldDescriptions() {
        List<String> columns = databaseClient.query(
                StatementLabel.FIND_MALLOC_EVENT_TYPE_COLUMNS,
                SELECT_MALLOC_EVENT_TYPE_COLUMNS,
                EMPTY_PARAMS,
                mallocColumnsMapper());

        return Json.read(columns.getFirst(), FIELD_DESC);
    }

    private RowMapper<SamplesAndWeight> samplesAndWeightMapper() {
        return (rs, _) -> new SamplesAndWeight(rs.getLong("samples"), rs.getLong("weight"));
    }

    private RowMapper<String> mallocColumnsMapper() {
        return (rs, __) -> rs.getString("columns");
    }

    @Override
    public boolean applicable() {
        long count = databaseClient.queryLong(
                StatementLabel.MALLOC_AND_FREE_EXISTS, MALLOC_AND_FREE_EXISTS, EMPTY_PARAMS);
        return count == 2;
    }


    @Override
    public Type type() {
        return Type.NATIVE_LEAK;
    }
}
