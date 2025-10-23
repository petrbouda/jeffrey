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

package pbouda.jeffrey.provider.writer.sql.calculated;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import pbouda.jeffrey.common.Json;
import pbouda.jeffrey.common.model.RecordingEventSource;
import pbouda.jeffrey.common.model.Type;
import pbouda.jeffrey.provider.api.model.EnhancedEventType;
import pbouda.jeffrey.provider.api.model.EventType;
import pbouda.jeffrey.provider.writer.sql.GroupLabel;
import pbouda.jeffrey.provider.writer.sql.StatementLabel;
import pbouda.jeffrey.provider.writer.sql.client.DatabaseClient;
import pbouda.jeffrey.provider.writer.sql.client.DatabaseClientProvider;
import pbouda.jeffrey.provider.writer.sql.writer.BatchingEventTypeWriter;

import java.util.List;

public class NativeLeakEventCalculator implements EventCalculator {

    private record SamplesAndWeight(long samples, long weight) {
    }

    //language=SQL
    private static final String MALLOC_AND_FREE_EXISTS = """
            SELECT count(*) FROM event_types
                WHERE profile_id = :profile_id
                    AND (name = 'profiler.Malloc' OR name = 'profiler.Free')""";

    //language=SQL
    private static final String SELECT_MALLOC_EVENT_TYPE_COLUMNS =
            "SELECT columns FROM event_types WHERE profile_id = :profile_id AND name = 'profiler.Malloc'";

    //language=SQL
    private static final String SELECT_NATIVE_LEAK_EVENTS_SAMPLES_AND_WEIGHT = """
            SELECT count(eMalloc.samples) AS samples, sum(eMalloc.weight) AS weight FROM events eMalloc
            WHERE eMalloc.profile_id = :profile_id AND eMalloc.event_type = 'profiler.Malloc'
            AND NOT EXISTS (
                SELECT 1 FROM events eFree
                   WHERE eFree.profile_id = :profile_id
                        AND eFree.event_type = 'profiler.Free'
                        AND eMalloc.weight_entity = eFree.weight_entity
            )""";

    private final DatabaseClient databaseClient;
    private final BatchingEventTypeWriter eventTypeWriter;

    private final MapSqlParameterSource profileIdParams;

    public NativeLeakEventCalculator(
            String profileId, DatabaseClientProvider databaseClientProvider, BatchingEventTypeWriter eventTypeWriter) {

        this.profileIdParams = new MapSqlParameterSource("profile_id", profileId);
        this.databaseClient = databaseClientProvider.provide(GroupLabel.NATIVE_LEAK_EVENTS);
        this.eventTypeWriter = eventTypeWriter;
    }

    @Override
    public void publish() {
        SamplesAndWeight samplesAndWeight = databaseClient.querySingle(
                StatementLabel.FIND_NATIVE_LEAK_EVENTS_SAMPLES_AND_WEIGHT,
                SELECT_NATIVE_LEAK_EVENTS_SAMPLES_AND_WEIGHT,
                profileIdParams,
                samplesAndWeightMapper()).get();

        List<String> mallocColumns = databaseClient.query(
                StatementLabel.FIND_MALLOC_EVENT_TYPE_COLUMNS,
                SELECT_MALLOC_EVENT_TYPE_COLUMNS,
                profileIdParams,
                mallocColumnsMapper());

        EventType eventType = new EventType(
                Type.NATIVE_LEAK.code(),
                "Native Leak",
                null,
                "Malloc allocations without corresponding Free events",
                List.of("Java Virtual Machine", "Native Memory"),
                Json.readTree(mallocColumns.getFirst()));

        EnhancedEventType enhancedEventType = new EnhancedEventType(
                eventType,
                RecordingEventSource.ASYNC_PROFILER,
                null,
                samplesAndWeight.samples,
                samplesAndWeight.weight,
                true,
                true,
                null,
                null);

        eventTypeWriter.insert(enhancedEventType);
    }

    private RowMapper<String> mallocColumnsMapper() {
        return (rs, __) -> rs.getString("columns");
    }

    private RowMapper<SamplesAndWeight> samplesAndWeightMapper() {
        return (rs, _) -> new SamplesAndWeight(rs.getLong("samples"), rs.getLong("weight"));
    }

    @Override
    public boolean applicable() {
        long count = databaseClient.queryLong(
                StatementLabel.MALLOC_AND_FREE_EXISTS, MALLOC_AND_FREE_EXISTS, profileIdParams);
        return count == 2;
    }
}
