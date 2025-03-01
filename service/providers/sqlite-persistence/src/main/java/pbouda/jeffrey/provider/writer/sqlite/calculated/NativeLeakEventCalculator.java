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

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import pbouda.jeffrey.common.EventSource;
import pbouda.jeffrey.common.Json;
import pbouda.jeffrey.common.ProfilingStartEnd;
import pbouda.jeffrey.common.Type;
import pbouda.jeffrey.common.model.profile.Event;
import pbouda.jeffrey.common.model.profile.EventType;
import pbouda.jeffrey.provider.writer.sqlite.ProfileSequences;
import pbouda.jeffrey.provider.writer.sqlite.writer.DatabaseWriter;

import javax.sql.DataSource;
import java.util.List;

public class NativeLeakEventCalculator implements EventCalculator {

    private static class SamplesWeightCollector {
        long samples;
        long weight;

        public void add(long samples, long weight) {
            this.samples += samples;
            this.weight += weight;
        }
    }

    //language=SQL
    private static final String MALLOC_AND_FREE_EXISTS =
            "SELECT * FROM event_types WHERE name = 'profiler.Malloc' AND name = 'profiler.Free'";

    //language=SQL
    private static final String SELECT_MALLOC_EVENT_TYPE_COLUMNS =
            "SELECT columns FROM event_types WHERE name = 'profiler.Malloc'";

    //language=SQL
    private static final String SELECT_NATIVE_LEAK_EVENTS = """
                WHERE eMalloc.event_name = 'profiler.Malloc'
                AND NOT EXISTS (SELECT 1 FROM events eFree
                   WHERE eFree.event_name = 'profiler.Free'
                        AND eMalloc.fields->>'address' = eFree.fields->>'address'
            """;

    private final long recordingStartedAt;
    private final JdbcTemplate jdbcTemplate;
    private final ProfileSequences profileSequences;
    private final DatabaseWriter<Event> eventWriter;
    private final DatabaseWriter<EventType> eventTypeWriter;

    public NativeLeakEventCalculator(
            ProfilingStartEnd profilingStartEnd,
            DataSource dataSource,
            ProfileSequences profileSequences,
            DatabaseWriter<Event> eventWriter,
            DatabaseWriter<EventType> eventTypeWriter) {

        this.recordingStartedAt = profilingStartEnd.start().toEpochMilli();
        this.jdbcTemplate = new JdbcTemplate(dataSource);
        this.profileSequences = profileSequences;
        this.eventWriter = eventWriter;
        this.eventTypeWriter = eventTypeWriter;
    }

    @Override
    public void publish() {
        eventWriter.start();

        SamplesWeightCollector collector = new SamplesWeightCollector();
        jdbcTemplate.queryForStream(SELECT_NATIVE_LEAK_EVENTS, eventTypeMapper())
                .forEach(event -> {
                    eventWriter.insert(event);
                    collector.add(event.samples(), event.weight());
                });

        List<String> mallocColumns = jdbcTemplate.query(SELECT_MALLOC_EVENT_TYPE_COLUMNS, mallocColumnsMapper());

        EventType entity = new EventType(
                Type.NATIVE_LEAK.code(),
                "Native Leak",
                null,
                null,
                List.of("Java Virtual Machine", "Native Memory"),
                EventSource.ASYNC_PROFILER,
                null,
                collector.samples,
                collector.weight,
                true,
                true,
                null,
                Json.readTree(mallocColumns.getFirst()));

        eventTypeWriter.insert(entity);
    }

    private RowMapper<String> mallocColumnsMapper() {
        return (rs, __) -> rs.getString("columns");
    }

    private RowMapper<Event> eventTypeMapper() {
        return (rs, __) -> {
            long timestamp = rs.getLong("timestamp");
            long timestampFromStart = timestamp - recordingStartedAt;

            return new Event(
                    profileSequences.nextEventId(),
                    Type.NATIVE_LEAK.code(),
                    timestamp,
                    timestampFromStart,
                    rs.getLong("duration"),
                    rs.getLong("samples"),
                    rs.getLong("weight"),
                    null,
                    rs.getLong("stacktrace_id"),
                    rs.getLong("thread_id"),
                    new ExactTextNode(rs.getString("fields"))
            );
        };
    }

    @Override
    public boolean applicable() {
        Integer count = jdbcTemplate.queryForObject(MALLOC_AND_FREE_EXISTS, Integer.class);
        return count != null && count == 2;
    }
}
