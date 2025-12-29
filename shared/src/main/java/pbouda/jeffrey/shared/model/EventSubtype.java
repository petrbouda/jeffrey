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

package pbouda.jeffrey.shared.model;

import static pbouda.jeffrey.shared.model.RecordingEventSource.ASYNC_PROFILER;
import static pbouda.jeffrey.shared.model.RecordingEventSource.JDK;

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
