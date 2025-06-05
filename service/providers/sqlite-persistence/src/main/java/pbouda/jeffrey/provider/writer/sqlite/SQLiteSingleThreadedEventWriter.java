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

package pbouda.jeffrey.provider.writer.sqlite;

import org.eclipse.collections.api.factory.primitive.ObjectLongMaps;
import org.eclipse.collections.api.map.primitive.MutableObjectLongMap;
import pbouda.jeffrey.common.model.StacktraceTag;
import pbouda.jeffrey.common.settings.ActiveSetting;
import pbouda.jeffrey.provider.api.SingleThreadedEventWriter;
import pbouda.jeffrey.provider.api.model.*;
import pbouda.jeffrey.provider.writer.sqlite.model.EventStacktraceTagWithId;
import pbouda.jeffrey.provider.writer.sqlite.model.EventStacktraceWithId;
import pbouda.jeffrey.provider.writer.sqlite.model.EventThreadWithId;
import pbouda.jeffrey.provider.writer.sqlite.model.EventWithId;

import java.time.Instant;
import java.util.*;

public class SQLiteSingleThreadedEventWriter implements SingleThreadedEventWriter {

    private final MutableObjectLongMap<String> weightCollector = ObjectLongMaps.mutable.empty();
    private final MutableObjectLongMap<String> samplesCollector = ObjectLongMaps.mutable.empty();
    private final Map<String, ActiveSetting> activeSettings = new HashMap<>();
    private final List<EventThreadWithId> eventThreads = new ArrayList<>();
    private final List<EventType> eventTypes = new ArrayList<>();
    private final JdbcWriters jdbcWriters;
    private final ProfileSequences sequences;
    private final Set<String> eventTypesContainingStacktraces = new HashSet<>();

    private long latestEventTimestamp = -1;

    public SQLiteSingleThreadedEventWriter(JdbcWriters jdbcWriters, ProfileSequences sequences) {
        this.jdbcWriters = jdbcWriters;
        this.sequences = sequences;
    }

    @Override
    public void onThreadStart() {
    }

    @Override
    public long onEvent(Event event) {
        long eventId = sequences.nextEventId();

        jdbcWriters.events().insert(new EventWithId(eventId, event));
        if (event.weight() != null) {
            weightCollector.addToValue(event.eventType(), event.weight());
        }
        samplesCollector.addToValue(event.eventType(), event.samples());

        if (event.timestamp() > latestEventTimestamp) {
            latestEventTimestamp = event.timestamp();
        }

        if (event.stacktraceId() != null) {
            eventTypesContainingStacktraces.add(event.eventType());
        }
        return eventId;
    }

    @Override
    public void onEventFields(EventFields eventFields) {
        jdbcWriters.eventFields().insert(eventFields);
    }

    @Override
    public void onEventSetting(EventSetting eventSetting) {
        String eventType = eventSetting.eventType();
        ActiveSetting setting = activeSettings.get(eventType);
        if (setting == null) {
            setting = new ActiveSetting(eventType);
            activeSettings.put(eventType, setting);
        }
        setting.putParam(eventSetting.name(), eventSetting.value());
    }

    @Override
    public void onEventType(EventType eventType) {
        eventTypes.add(eventType);
    }

    @Override
    public long onEventStacktrace(EventStacktrace stacktrace) {
        long stacktraceId = sequences.nextStacktraceId();
        jdbcWriters.stacktraces().insert(new EventStacktraceWithId(stacktraceId, stacktrace));

        for (StacktraceTag tag : stacktrace.tags()) {
            jdbcWriters.stacktraceTags().insert(new EventStacktraceTagWithId(stacktraceId, tag));
        }
        return stacktraceId;
    }

    @Override
    public long onEventThread(EventThread thread) {
        long threadId = sequences.nextThreadId();
        eventThreads.add(new EventThreadWithId(threadId, thread));
        return threadId;
    }

    @Override
    public void onThreadComplete() {
        try {
            jdbcWriters.close();
        } catch (Exception e) {
            throw new RuntimeException("Cannot close JDBC writers", e);
        }
    }

    public EventWriterResult getResult() {
        List<EventTypeBuilder> eventTypeBuilders = new ArrayList<>();
        for (EventType eventType : eventTypes) {
            EventTypeBuilder builder = EventTypeBuilder.newBuilder(eventType)
                    .addSamples(samplesCollector.get(eventType.name()))
                    .addWeight(weightCollector.get(eventType.name()));

            eventTypeBuilders.add(builder);
        }

        return new EventWriterResult(
                eventThreads,
                eventTypeBuilders,
                activeSettings,
                eventTypesContainingStacktraces,
                Instant.ofEpochMilli(latestEventTimestamp));
    }
}
