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

package cafe.jeffrey.microscope.core.web.controllers;

import cafe.jeffrey.shared.common.model.EventTypeName;

import java.util.List;

/**
 * Curated catalog of stack-trace–carrying event types offered when authoring a Guardian guard.
 * Guards are global (not bound to a single profile), so the offered set is a fixed reference list
 * of JDK and async-profiler events that are known to carry stack traces — the editor still lets the
 * user type any custom event type that is not in this list.
 */
public final class GuardianEventTypeCatalog {

    /** Logical origin of an event type, used by the editor to group the suggestions. */
    private static final String SOURCE_JDK = "JDK";
    private static final String SOURCE_ASYNC_PROFILER = "ASYNC_PROFILER";

    /** A single suggestion: the event-type code, a human label and its origin. */
    public record EventTypeOption(String code, String label, String source) {
    }

    private static final List<EventTypeOption> CATALOG = List.of(
            // JDK events that carry a stack trace
            jdk(EventTypeName.EXECUTION_SAMPLE, "Execution Sample"),
            jdk(EventTypeName.CPU_TIME_SAMPLE, "CPU Time Sample"),
            jdk(EventTypeName.OBJECT_ALLOCATION_SAMPLE, "Object Allocation Sample"),
            jdk(EventTypeName.OBJECT_ALLOCATION_IN_NEW_TLAB, "Allocation in New TLAB"),
            jdk(EventTypeName.OBJECT_ALLOCATION_OUTSIDE_TLAB, "Allocation Outside TLAB"),
            jdk(EventTypeName.OLD_OBJECT_SAMPLE, "Old Object Sample"),
            jdk(EventTypeName.JAVA_MONITOR_ENTER, "Java Monitor Blocked"),
            jdk(EventTypeName.JAVA_MONITOR_WAIT, "Java Monitor Wait"),
            jdk(EventTypeName.THREAD_PARK, "Java Thread Park"),
            jdk(EventTypeName.THREAD_SLEEP, "Java Thread Sleep"),
            jdk(EventTypeName.THREAD_START, "Java Thread Start"),
            jdk(EventTypeName.SOCKET_READ, "Socket Read"),
            jdk(EventTypeName.SOCKET_WRITE, "Socket Write"),
            jdk(EventTypeName.FILE_READ, "File Read"),
            jdk(EventTypeName.FILE_WRITE, "File Write"),
            jdk(EventTypeName.JAVA_EXCEPTION_THROW, "Java Exception"),
            jdk(EventTypeName.JAVA_ERROR_THROW, "Java Error"),
            jdk(EventTypeName.DEOPTIMIZATION, "Deoptimization"),
            jdk(EventTypeName.NATIVE_LIBRARY_LOAD, "Native Library Load"),
            jdk(EventTypeName.VIRTUAL_THREAD_START, "Virtual Thread Start"),
            jdk(EventTypeName.VIRTUAL_THREAD_PINNED, "Virtual Thread Pinned"),
            jdk(EventTypeName.PROCESS_START, "Process Start"),
            jdk(EventTypeName.SHUTDOWN, "JVM Shutdown"),
            // async-profiler events that carry a stack trace
            asyncProfiler(EventTypeName.WALL_CLOCK_SAMPLE, "Wall Clock Sample"),
            asyncProfiler(EventTypeName.MALLOC, "Native malloc"),
            asyncProfiler(EventTypeName.FREE, "Native free"),
            asyncProfiler(EventTypeName.METHOD_TRACE, "Method Trace"));

    private GuardianEventTypeCatalog() {
    }

    /** Immutable list of stack-trace event types offered by the guard editor. */
    public static List<EventTypeOption> all() {
        return CATALOG;
    }

    private static EventTypeOption jdk(String code, String label) {
        return new EventTypeOption(code, label, SOURCE_JDK);
    }

    private static EventTypeOption asyncProfiler(String code, String label) {
        return new EventTypeOption(code, label, SOURCE_ASYNC_PROFILER);
    }
}
