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

package pbouda.jeffrey.provider.writer.sqlite.calculated;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import pbouda.jeffrey.common.Json;
import pbouda.jeffrey.common.model.EventSource;
import pbouda.jeffrey.common.model.Type;
import pbouda.jeffrey.provider.api.model.EnhancedEventType;
import pbouda.jeffrey.provider.api.model.EventType;
import pbouda.jeffrey.provider.writer.sqlite.writer.BatchingEventTypeWriter;

import java.util.List;
import java.util.Map;

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

    private final String profileId;
    private final JdbcClient jdbcClient;
    private final BatchingEventTypeWriter eventTypeWriter;

    public NativeLeakEventCalculator(String profileId, BatchingEventTypeWriter eventTypeWriter) {
        this.profileId = profileId;
        this.jdbcClient = JdbcClient.create(eventTypeWriter.getJdbcTemplate());
        this.eventTypeWriter = eventTypeWriter;
    }

    @Override
    public void publish() {
        Map<String, String> profileIdParam = Map.of("profile_id", profileId);

        SamplesAndWeight samplesAndWeight = jdbcClient.sql(SELECT_NATIVE_LEAK_EVENTS_SAMPLES_AND_WEIGHT)
                .param("profile_id", profileId)
                .query((rs, __) -> new SamplesAndWeight(rs.getLong("samples"), rs.getLong("weight")))
                .single();

        List<String> mallocColumns = jdbcClient.sql(SELECT_MALLOC_EVENT_TYPE_COLUMNS)
                .param("profile_id", profileId)
                .query(mallocColumnsMapper())
                .list();

        EventType eventType = new EventType(
                Type.NATIVE_LEAK.code(),
                "Native Leak",
                null,
                "Malloc allocations without corresponding Free events",
                List.of("Java Virtual Machine", "Native Memory"),
                true,
                Json.readTree(mallocColumns.getFirst()));

        EnhancedEventType enhancedEventType = new EnhancedEventType(
                eventType,
                EventSource.ASYNC_PROFILER,
                null,
                samplesAndWeight.samples,
                samplesAndWeight.weight,
                true,
                null,
                null);

        eventTypeWriter.insert(enhancedEventType);
    }

    private RowMapper<String> mallocColumnsMapper() {
        return (rs, __) -> rs.getString("columns");
    }

    @Override
    public boolean applicable() {
        return jdbcClient.sql(MALLOC_AND_FREE_EXISTS)
                .param("profile_id", profileId)
                .query(Integer.class)
                .optional()
                .map(count -> count == 2)
                .orElse(false);
    }
}
