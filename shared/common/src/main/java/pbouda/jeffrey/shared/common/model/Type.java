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

package pbouda.jeffrey.shared.common.model;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public record Type(String code, boolean calculated) {
    // Calculated/derived events
    public static final Type NATIVE_LEAK = new Type(EventTypeName.NATIVE_LEAK, true);

    // Real events
    public static final Type EXECUTION_SAMPLE = new Type(EventTypeName.EXECUTION_SAMPLE);
    public static final Type WALL_CLOCK_SAMPLE = new Type(EventTypeName.WALL_CLOCK_SAMPLE);
    public static final Type METHOD_TRACE = new Type(EventTypeName.METHOD_TRACE);
    public static final Type MALLOC = new Type(EventTypeName.MALLOC);
    public static final Type FREE = new Type(EventTypeName.FREE);
    public static final Type JAVA_MONITOR_ENTER = new Type(EventTypeName.JAVA_MONITOR_ENTER);
    public static final Type JAVA_MONITOR_WAIT = new Type(EventTypeName.JAVA_MONITOR_WAIT);
    public static final Type THREAD_START = new Type(EventTypeName.THREAD_START);
    public static final Type THREAD_END = new Type(EventTypeName.THREAD_END);
    public static final Type THREAD_PARK = new Type(EventTypeName.THREAD_PARK);
    public static final Type THREAD_SLEEP = new Type(EventTypeName.THREAD_SLEEP);
    public static final Type OBJECT_ALLOCATION_IN_NEW_TLAB = new Type(EventTypeName.OBJECT_ALLOCATION_IN_NEW_TLAB);
    public static final Type OBJECT_ALLOCATION_OUTSIDE_TLAB = new Type(EventTypeName.OBJECT_ALLOCATION_OUTSIDE_TLAB);
    public static final Type OBJECT_ALLOCATION_SAMPLE = new Type(EventTypeName.OBJECT_ALLOCATION_SAMPLE);
    public static final Type SOCKET_READ = new Type(EventTypeName.SOCKET_READ);
    public static final Type SOCKET_WRITE = new Type(EventTypeName.SOCKET_WRITE);
    public static final Type FILE_READ = new Type(EventTypeName.FILE_READ);
    public static final Type FILE_WRITE = new Type(EventTypeName.FILE_WRITE);
    public static final Type LIVE_OBJECTS = new Type(EventTypeName.LIVE_OBJECTS);
    public static final Type ACTIVE_RECORDING = new Type(EventTypeName.ACTIVE_RECORDING);
    public static final Type ACTIVE_SETTING = new Type(EventTypeName.ACTIVE_SETTING);
    public static final Type GC_CONFIGURATION = new Type(EventTypeName.GC_CONFIGURATION);
    public static final Type GC_HEAP_CONFIGURATION = new Type(EventTypeName.GC_HEAP_CONFIGURATION);
    public static final Type GC_SURVIVOR_CONFIGURATION = new Type(EventTypeName.GC_SURVIVOR_CONFIGURATION);
    public static final Type GC_TLAB_CONFIGURATION = new Type(EventTypeName.GC_TLAB_CONFIGURATION);
    
    // GC Events
    public static final Type GARBAGE_COLLECTION = new Type(EventTypeName.GARBAGE_COLLECTION);
    public static final Type GC_HEAP_SUMMARY = new Type(EventTypeName.GC_HEAP_SUMMARY);
    public static final Type G1_HEAP_SUMMARY = new Type(EventTypeName.G1_HEAP_SUMMARY);
    public static final Type PS_HEAP_SUMMARY = new Type(EventTypeName.PS_HEAP_SUMMARY);
    public static final Type YOUNG_GARBAGE_COLLECTION = new Type(EventTypeName.YOUNG_GARBAGE_COLLECTION);
    public static final Type OLD_GARBAGE_COLLECTION = new Type(EventTypeName.OLD_GARBAGE_COLLECTION);
    public static final Type PARALLEL_OLD_GARBAGE_COLLECTION = new Type(EventTypeName.PARALLEL_OLD_GARBAGE_COLLECTION);
    public static final Type G1_GARBAGE_COLLECTION = new Type(EventTypeName.G1_GARBAGE_COLLECTION);
    public static final Type Z_YOUNG_GARBAGE_COLLECTION = new Type(EventTypeName.Z_YOUNG_GARBAGE_COLLECTION);
    public static final Type Z_OLD_GARBAGE_COLLECTION = new Type(EventTypeName.Z_OLD_GARBAGE_COLLECTION);
    public static final Type GC_PHASE_CONCURRENT = new Type(EventTypeName.GC_PHASE_CONCURRENT);
    public static final Type YOUNG_GENERATION_CONFIGURATION = new Type(EventTypeName.YOUNG_GENERATION_CONFIGURATION);
    public static final Type COMPILER_CONFIGURATION = new Type(EventTypeName.COMPILER_CONFIGURATION);
    public static final Type JVM_INFORMATION = new Type(EventTypeName.JVM_INFORMATION);
    public static final Type CPU_INFORMATION = new Type(EventTypeName.CPU_INFORMATION);
    public static final Type OS_INFORMATION = new Type(EventTypeName.OS_INFORMATION);
    public static final Type VIRTUALIZATION_INFORMATION = new Type(EventTypeName.VIRTUALIZATION_INFORMATION);
    public static final Type JAVA_THREAD_STATISTICS = new Type(EventTypeName.JAVA_THREAD_STATISTICS);
    public static final Type THREAD_ALLOCATION_STATISTICS = new Type(EventTypeName.THREAD_ALLOCATION_STATISTICS);
    public static final Type THREAD_CPU_LOAD = new Type(EventTypeName.THREAD_CPU_LOAD);
    public static final Type COMPILER_STATISTICS = new Type(EventTypeName.COMPILER_STATISTICS);
    public static final Type COMPILATION = new Type(EventTypeName.COMPILATION);

    // ----------------------------
    // Application events - JEFFREY
    // ----------------------------

    // JDBC Pool events
    public static final Type JDBC_POOL_STATISTICS = new Type(EventTypeName.JDBC_POOL_STATISTICS);
    public static final Type ACQUIRING_POOLED_JDBC_CONNECTION_TIMEOUT =
            new Type(EventTypeName.ACQUIRING_POOLED_JDBC_CONNECTION_TIMEOUT);
    public static final Type POOLED_JDBC_CONNECTION_ACQUIRED =
            new Type(EventTypeName.POOLED_JDBC_CONNECTION_ACQUIRED);
    public static final Type POOLED_JDBC_CONNECTION_BORROWED =
            new Type(EventTypeName.POOLED_JDBC_CONNECTION_BORROWED);
    public static final Type POOLED_JDBC_CONNECTION_CREATED =
            new Type(EventTypeName.POOLED_JDBC_CONNECTION_CREATED);

    // JDBC events
    public static final Type JDBC_INSERT = new Type(EventTypeName.JDBC_INSERT);
    public static final Type JDBC_UPDATE = new Type(EventTypeName.JDBC_UPDATE);
    public static final Type JDBC_DELETE = new Type(EventTypeName.JDBC_DELETE);
    public static final Type JDBC_QUERY = new Type(EventTypeName.JDBC_QUERY);
    public static final Type JDBC_EXECUTE = new Type(EventTypeName.JDBC_EXECUTE);
    public static final Type JDBC_STREAM = new Type(EventTypeName.JDBC_STREAM);

    // HTTP events
    public static final Type HTTP_SERVER_EXCHANGE = new Type(EventTypeName.HTTP_SERVER_EXCHANGE);
    public static final Type HTTP_CLIENT_EXCHANGE = new Type(EventTypeName.HTTP_CLIENT_EXCHANGE);

    // Container events
    public static final Type CONTAINER_CONFIGURATION = new Type(EventTypeName.CONTAINER_CONFIGURATION);
    public static final Type CONTAINER_CPU_THROTTLING = new Type(EventTypeName.CONTAINER_CPU_THROTTLING);
    public static final Type CONTAINER_MEMORY_USAGE = new Type(EventTypeName.CONTAINER_MEMORY_USAGE);
    public static final Type CONTAINER_IO_USAGE = new Type(EventTypeName.CONTAINER_IO_USAGE);

    // ImportantMessage
    public static final Type IMPORTANT_MESSAGE = new Type(EventTypeName.IMPORTANT_MESSAGE);

    private static final Map<String, Type> KNOWN_TYPES;

    static {
        KNOWN_TYPES = Stream.of(
                EXECUTION_SAMPLE,
                WALL_CLOCK_SAMPLE,
                METHOD_TRACE,
                MALLOC,
                FREE,
                NATIVE_LEAK,
                JAVA_MONITOR_ENTER,
                JAVA_MONITOR_WAIT,
                THREAD_START,
                THREAD_END,
                THREAD_PARK,
                THREAD_SLEEP,
                OBJECT_ALLOCATION_SAMPLE,
                OBJECT_ALLOCATION_IN_NEW_TLAB,
                OBJECT_ALLOCATION_OUTSIDE_TLAB,
                SOCKET_READ,
                SOCKET_WRITE,
                FILE_READ,
                FILE_WRITE,
                LIVE_OBJECTS,
                ACTIVE_RECORDING,
                ACTIVE_SETTING,
                GC_CONFIGURATION,
                GC_HEAP_CONFIGURATION,
                GC_SURVIVOR_CONFIGURATION,
                GC_TLAB_CONFIGURATION,
                GARBAGE_COLLECTION,
                GC_HEAP_SUMMARY,
                G1_HEAP_SUMMARY,
                PS_HEAP_SUMMARY,
                YOUNG_GARBAGE_COLLECTION,
                OLD_GARBAGE_COLLECTION,
                PARALLEL_OLD_GARBAGE_COLLECTION,
                G1_GARBAGE_COLLECTION,
                Z_YOUNG_GARBAGE_COLLECTION,
                Z_OLD_GARBAGE_COLLECTION,
                GC_PHASE_CONCURRENT,
                YOUNG_GENERATION_CONFIGURATION,
                COMPILER_CONFIGURATION,
                JVM_INFORMATION,
                CPU_INFORMATION,
                OS_INFORMATION,
                VIRTUALIZATION_INFORMATION,
                JAVA_THREAD_STATISTICS,
                THREAD_ALLOCATION_STATISTICS,
                THREAD_CPU_LOAD,
                COMPILER_STATISTICS,
                COMPILATION,
                JDBC_POOL_STATISTICS,
                ACQUIRING_POOLED_JDBC_CONNECTION_TIMEOUT,
                POOLED_JDBC_CONNECTION_ACQUIRED,
                POOLED_JDBC_CONNECTION_BORROWED,
                POOLED_JDBC_CONNECTION_CREATED,
                HTTP_SERVER_EXCHANGE,
                HTTP_CLIENT_EXCHANGE,
                JDBC_INSERT,
                JDBC_UPDATE,
                JDBC_DELETE,
                JDBC_QUERY,
                JDBC_EXECUTE,
                JDBC_STREAM,
                CONTAINER_CONFIGURATION,
                CONTAINER_CPU_THROTTLING,
                CONTAINER_MEMORY_USAGE,
                CONTAINER_IO_USAGE
        ).collect(Collectors.toMap(Type::code, Function.identity()));
    }

    public Type(String code) {
        this(code, false);
    }

    public boolean isTlabAllocationSamples() {
        return Type.OBJECT_ALLOCATION_IN_NEW_TLAB.equals(this)
                || Type.OBJECT_ALLOCATION_OUTSIDE_TLAB.equals(this);
    }

    public boolean isObjectAllocationSamples() {
        return Type.OBJECT_ALLOCATION_SAMPLE.equals(this);
    }

    public boolean isAllocationEvent() {
        return isTlabAllocationSamples() || isObjectAllocationSamples();
    }

    public boolean isBlockingEvent() {
        return Type.JAVA_MONITOR_ENTER.equals(this)
                || Type.JAVA_MONITOR_WAIT.equals(this)
                || Type.THREAD_PARK.equals(this);
    }

    public boolean isMethodTraceEvent() {
        return Type.METHOD_TRACE.equals(this);
    }

    /**
     * Calculated event means that the event is artificial. Very likely the event is calculated/derived from other
     * event. e.g. {@link #NATIVE_LEAK} is a calculated event from {@link #MALLOC} and {@link #FREE}.
     *
     * @return true if the event is calculated, derived from the other events.
     */
    public boolean isCalculated() {
        return calculated;
    }

    public boolean sameAs(Type eventType) {
        return this.code.equals(eventType.code);
    }

    public boolean sameAs(String code) {
        return this.code.equals(code);
    }

    public static Optional<Type> getKnownType(String code) {
        return Optional.ofNullable(KNOWN_TYPES.get(code));
    }

    public static Type fromCode(String code) {
        return getKnownType(code)
                .orElseGet(() -> new Type(code));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Type type)) return false;
        return Objects.equals(code, type.code);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(code);
    }
}
