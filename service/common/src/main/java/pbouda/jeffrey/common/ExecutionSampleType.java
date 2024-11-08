/*
 * Jeffrey
 * Copyright (C) 2024 Petr Bouda
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

package pbouda.jeffrey.common;

import static pbouda.jeffrey.common.EventSource.ASYNC_PROFILER;
import static pbouda.jeffrey.common.EventSource.JDK;

public enum ExecutionSampleType {
    CPU("cpu", ASYNC_PROFILER, "CPU Profiling (perf_events)"),
    CTIMER("ctimer", ASYNC_PROFILER, "CPU Profiling (ctimer)"),
    ITIMER("itimer", ASYNC_PROFILER, "CPU Profiling (itimer)"),
    WALL("wall", ASYNC_PROFILER, "Wall-Clock Profiling"),
    METHOD(null, ASYNC_PROFILER, "Method Tracing"),
    EXECUTION_SAMPLE(null, JDK, "Method Profiling Sample");

    private static final ExecutionSampleType[] TYPES = ExecutionSampleType.values();

    private final String name;
    private final EventSource source;
    private final String label;

    ExecutionSampleType(String name, EventSource source, String label) {
        this.name = name;
        this.source = source;
        this.label = label;
    }

    public EventSource getSource() {
        return source;
    }

    public String getLabel() {
        return label;
    }

    public static ExecutionSampleType resolveAsyncProfilerType(String eventName) {
        for (ExecutionSampleType type : TYPES) {
            if (type.source == ASYNC_PROFILER && type.name != null && type.name.equals(eventName)) {
                return type;
            }
        }
        return ExecutionSampleType.METHOD;
    }
}
