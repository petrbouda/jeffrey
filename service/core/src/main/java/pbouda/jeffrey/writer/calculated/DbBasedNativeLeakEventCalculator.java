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

package pbouda.jeffrey.writer.calculated;

import org.eclipse.collections.api.factory.primitive.LongSets;
import org.eclipse.collections.api.set.primitive.MutableLongSet;
import pbouda.jeffrey.common.EventSource;
import pbouda.jeffrey.common.Json;
import pbouda.jeffrey.common.ProfilingStartEnd;
import pbouda.jeffrey.common.Type;
import pbouda.jeffrey.common.model.profile.Event;
import pbouda.jeffrey.common.model.profile.EventType;
import pbouda.jeffrey.persistence.profile.SQLiteClient;
import pbouda.jeffrey.writer.profile.BatchingDatabaseWriter;
import pbouda.jeffrey.writer.profile.ProfileSequences;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

public class DbBasedNativeLeakEventCalculator implements EventCalculator {

    private static class SamplesWeightCollector {
        long samples;
        long weight;

        public void add(long samples, long weight) {
            this.samples += samples;
            this.weight += weight;
        }
    }

    //language=SQL
    private static final String SELECT_ALL_EVENTS_TYPES =
            "SELECT * FROM event_types WHERE name = 'profiler.Malloc'";
    //language=SQL
    private static final String SELECT_MALLOC_EVENT_TYPE =
            "SELECT columns FROM event_types WHERE name = 'profiler.Malloc'";
    //language=SQL
    private static final String SELECT_MALLOC_EVENTS =
            "SELECT *, fields->>'address' AS address FROM events WHERE event_name = 'profiler.Malloc'";
    //language=SQL
    private static final String SELECT_FREE_EVENTS =
            "SELECT fields->>'address' AS address FROM events WHERE event_name = 'profiler.Free'";

    private final long recordingStartedAt;
    private final DataSource dataSource;
    private final ProfileSequences profileSequences;
    private final BatchingDatabaseWriter<Event> eventWriter;
    private final BatchingDatabaseWriter<EventType> eventTypeWriter;

    public DbBasedNativeLeakEventCalculator(
            ProfilingStartEnd profilingStartEnd,
            DataSource dataSource,
            ProfileSequences profileSequences,
            BatchingDatabaseWriter<Event> eventWriter,
            BatchingDatabaseWriter<EventType> eventTypeWriter) {

        this.recordingStartedAt = profilingStartEnd.start().toEpochMilli();
        this.dataSource = dataSource;
        this.profileSequences = profileSequences;
        this.eventWriter = eventWriter;
        this.eventTypeWriter = eventTypeWriter;
    }

    @Override
    public void publish() {
        MutableLongSet deallocations = LongSets.mutable.empty();
        try (var client = new SQLiteClient(dataSource, SELECT_FREE_EVENTS)) {
            client.select(rs -> deallocations.add(rs.getLong("address")));
        }

        eventWriter.start();

        SamplesWeightCollector collector = new SamplesWeightCollector();
        try (var client = new SQLiteClient(dataSource, SELECT_MALLOC_EVENTS); eventWriter) {
            client.select(rs -> {
                long timestamp = rs.getLong("timestamp");
                long timestampFromStart = timestamp - recordingStartedAt;

                long address = rs.getLong("address");
                if (!deallocations.contains(address)) {
                    Event entity = new Event(
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
                    eventWriter.insert(entity);
                    collector.add(entity.samples(), entity.weight());
                }
            });
        }

        AtomicReference<String> mallocColumns = new AtomicReference<>();
        try (var client = new SQLiteClient(dataSource, SELECT_MALLOC_EVENT_TYPE)) {
            client.select(rs -> mallocColumns.set(rs.getString("columns")));
        }

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
                Json.readTree(mallocColumns.get()));

        eventTypeWriter.singleInsert(entity);
    }

    @Override
    public boolean applicable() {
        List<String> eventTypes = new ArrayList<>();
        try (var client = new SQLiteClient(dataSource, SELECT_ALL_EVENTS_TYPES)) {
            client.select(rs -> eventTypes.add(rs.getString("name")));
        }
        return eventTypes.contains(Type.MALLOC.code()) && eventTypes.contains(Type.FREE.code());
    }
}
