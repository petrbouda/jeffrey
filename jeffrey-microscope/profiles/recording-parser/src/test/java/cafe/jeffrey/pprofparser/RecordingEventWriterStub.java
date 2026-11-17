/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
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
