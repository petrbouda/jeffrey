/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
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

package cafe.jeffrey.pprofparser;

import cafe.jeffrey.provider.profile.api.Event;
import cafe.jeffrey.provider.profile.api.EventSetting;
import cafe.jeffrey.provider.profile.api.EventStacktrace;
import cafe.jeffrey.provider.profile.api.EventThread;
import cafe.jeffrey.provider.profile.api.EventType;
import cafe.jeffrey.provider.profile.api.SingleThreadedEventWriter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Recording {@link SingleThreadedEventWriter} capturing the emission protocol for assertions.
 */
public class RecordingEventWriterStub implements SingleThreadedEventWriter {

    public final List<Event> events = new ArrayList<>();
    public final List<EventSetting> settings = new ArrayList<>();
    public final List<EventType> eventTypes = new ArrayList<>();
    public final Map<Long, EventStacktrace> stacktracesById = new LinkedHashMap<>();
    public final Map<Long, EventThread> threadsById = new LinkedHashMap<>();
    public int threadStarts;
    public int threadCompletions;

    private long nextStacktraceId = 100;
    private long nextThreadId = 1;

    @Override
    public void onThreadStart() {
        threadStarts++;
    }

    @Override
    public void onEvent(Event event) {
        events.add(event);
    }

    @Override
    public void onEventSetting(EventSetting setting) {
        settings.add(setting);
    }

    @Override
    public void onEventType(EventType eventType) {
        eventTypes.add(eventType);
    }

    @Override
    public long onEventStacktrace(EventStacktrace stacktrace) {
        long id = nextStacktraceId++;
        stacktracesById.put(id, stacktrace);
        return id;
    }

    @Override
    public long onEventThread(EventThread thread) {
        long id = nextThreadId++;
        threadsById.put(id, thread);
        return id;
    }

    @Override
    public void onThreadComplete() {
        threadCompletions++;
    }
}
