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

package cafe.jeffrey.microscope.model;

import static cafe.jeffrey.microscope.model.RecordingEventSource.ASYNC_PROFILER;
import static cafe.jeffrey.microscope.model.RecordingEventSource.JDK;

public enum EventSubtype {
    CPU("cpu", ASYNC_PROFILER, "CPU Profiling (perf_events)"),
    CTIMER("ctimer", ASYNC_PROFILER, "CPU Profiling (ctimer)"),
    ITIMER("itimer", ASYNC_PROFILER, "CPU Profiling (itimer)"),
    WALL("wall", ASYNC_PROFILER, "Wall-Clock Profiling"),
    METHOD(null, ASYNC_PROFILER, "Method Tracing"),
    EXECUTION_SAMPLE(null, JDK, "Method Profiling Sample");

    private static final EventSubtype[] TYPES = EventSubtype.values();

    private final String name;
    private final RecordingEventSource source;
    private final String label;

    EventSubtype(String name, RecordingEventSource source, String label) {
        this.name = name;
        this.source = source;
        this.label = label;
    }

    public RecordingEventSource getSource() {
        return source;
    }

    public String getLabel() {
        return label;
    }

    public static EventSubtype resolve(String eventType) {
        for (EventSubtype type : TYPES) {
            if (type.name().equals(eventType)) {
                return type;
            }
        }
        return null;
    }

    public static EventSubtype resolveAsyncProfilerType(String eventType) {
        for (EventSubtype type : TYPES) {
            if (type.source == ASYNC_PROFILER && type.name != null && type.name.equals(eventType)) {
                return type;
            }
        }
        return EventSubtype.METHOD;
    }
}
