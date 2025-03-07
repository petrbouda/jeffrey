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

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import jdk.jfr.EventType;
import jdk.jfr.consumer.RecordedEvent;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@JsonDeserialize(using = TypeDeserializer.class)
@JsonSerialize(using = TypeSerializer.class)
public record Type(String code, WeightExtractor weight, boolean calculated) {
    // Calculated/derived events
    public static final Type NATIVE_LEAK = new Type(EventTypeName.NATIVE_LEAK, WeightExtractor.allocation("size"), true);

    // Real events
    public static final Type EXECUTION_SAMPLE = new Type(EventTypeName.EXECUTION_SAMPLE);
    public static final Type WALL_CLOCK_SAMPLE = new Type(EventTypeName.WALL_CLOCK_SAMPLE);
    public static final Type MALLOC = new Type(EventTypeName.MALLOC, WeightExtractor.allocation("size", e -> String.valueOf(e.getLong("address"))));
    public static final Type FREE = new Type(EventTypeName.FREE, WeightExtractor.allocationEntityOnly(e -> String.valueOf(e.getLong("address"))));
    public static final Type JAVA_MONITOR_ENTER = new Type(EventTypeName.JAVA_MONITOR_ENTER, WeightExtractor.duration("monitorClass"));
    public static final Type JAVA_MONITOR_WAIT = new Type(EventTypeName.JAVA_MONITOR_WAIT, WeightExtractor.duration("monitorClass"));
    public static final Type THREAD_START = new Type(EventTypeName.THREAD_START);
    public static final Type THREAD_END = new Type(EventTypeName.THREAD_END);
    public static final Type THREAD_PARK = new Type(EventTypeName.THREAD_PARK, WeightExtractor.duration("parkedClass"));
    public static final Type THREAD_SLEEP = new Type(EventTypeName.THREAD_SLEEP, WeightExtractor.duration());
    public static final Type OBJECT_ALLOCATION_IN_NEW_TLAB = new Type(EventTypeName.OBJECT_ALLOCATION_IN_NEW_TLAB, WeightExtractor.allocation("allocationSize", "objectClass"));
    public static final Type OBJECT_ALLOCATION_OUTSIDE_TLAB = new Type(EventTypeName.OBJECT_ALLOCATION_OUTSIDE_TLAB, WeightExtractor.allocation("allocationSize", "objectClass"));
    public static final Type OBJECT_ALLOCATION_SAMPLE = new Type(EventTypeName.OBJECT_ALLOCATION_SAMPLE, WeightExtractor.allocation("weight", "objectClass"));
    public static final Type SOCKET_READ = new Type(EventTypeName.SOCKET_READ, WeightExtractor.allocation("bytesRead"));
    public static final Type SOCKET_WRITE = new Type(EventTypeName.SOCKET_WRITE, WeightExtractor.allocation("bytesWritten"));
    public static final Type FILE_READ = new Type(EventTypeName.FILE_READ, WeightExtractor.allocation("bytesRead"));
    public static final Type FILE_WRITE = new Type(EventTypeName.FILE_WRITE, WeightExtractor.allocation("bytesWritten"));
    public static final Type LIVE_OBJECTS = new Type(EventTypeName.LIVE_OBJECTS);
    public static final Type ACTIVE_RECORDING = new Type(EventTypeName.ACTIVE_RECORDING);
    public static final Type ACTIVE_SETTING = new Type(EventTypeName.ACTIVE_SETTING);
    public static final Type GC_CONFIGURATION = new Type(EventTypeName.GC_CONFIGURATION);
    public static final Type GC_HEAP_CONFIGURATION = new Type(EventTypeName.GC_HEAP_CONFIGURATION);
    public static final Type GC_SURVIVOR_CONFIGURATION = new Type(EventTypeName.GC_SURVIVOR_CONFIGURATION);
    public static final Type GC_TLAB_CONFIGURATION = new Type(EventTypeName.GC_TLAB_CONFIGURATION);
    public static final Type YOUNG_GENERATION_CONFIGURATION = new Type(EventTypeName.YOUNG_GENERATION_CONFIGURATION);
    public static final Type COMPILER_CONFIGURATION = new Type(EventTypeName.COMPILER_CONFIGURATION);
    public static final Type CONTAINER_CONFIGURATION = new Type(EventTypeName.CONTAINER_CONFIGURATION);
    public static final Type JVM_INFORMATION = new Type(EventTypeName.JVM_INFORMATION);
    public static final Type CPU_INFORMATION = new Type(EventTypeName.CPU_INFORMATION);
    public static final Type OS_INFORMATION = new Type(EventTypeName.OS_INFORMATION);
    public static final Type VIRTUALIZATION_INFORMATION = new Type(EventTypeName.VIRTUALIZATION_INFORMATION);

    private static final Map<String, Type> KNOWN_TYPES;

    public static final List<Type> WEIGHT_SUPPORTED_TYPES;

    static {
        KNOWN_TYPES = Stream.of(
                EXECUTION_SAMPLE,
                WALL_CLOCK_SAMPLE,
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
                YOUNG_GENERATION_CONFIGURATION,
                COMPILER_CONFIGURATION,
                CONTAINER_CONFIGURATION,
                JVM_INFORMATION,
                CPU_INFORMATION,
                OS_INFORMATION,
                VIRTUALIZATION_INFORMATION
        ).collect(Collectors.toMap(Type::code, Function.identity()));

        WEIGHT_SUPPORTED_TYPES = KNOWN_TYPES.values().stream()
                .filter(t -> t.weight != null)
                .toList();
    }

    public Type(String code) {
        this(code, null, false);
    }

    public Type(String code, WeightExtractor weight) {
        this(code, weight, false);
    }

    public boolean isTlabAllocationSamples() {
        return Type.OBJECT_ALLOCATION_IN_NEW_TLAB.equals(this)
                || Type.OBJECT_ALLOCATION_OUTSIDE_TLAB.equals(this);
    }

    public static List<Type> tlabAllocationSamples() {
        return List.of(Type.OBJECT_ALLOCATION_IN_NEW_TLAB, Type.OBJECT_ALLOCATION_OUTSIDE_TLAB);
    }

    public boolean isObjectAllocationSamples() {
        return Type.OBJECT_ALLOCATION_SAMPLE.equals(this);
    }

    public boolean isAllocationEvent() {
        return isTlabAllocationSamples() || isObjectAllocationSamples();
    }

    public List<Type> resolveGroupedTypes() {
        if (isTlabAllocationSamples()) {
            return tlabAllocationSamples();
        } else {
            return List.of(this);
        }
    }

    public boolean isBlockingEvent() {
        return Type.JAVA_MONITOR_ENTER.equals(this)
                || Type.JAVA_MONITOR_WAIT.equals(this)
                || Type.THREAD_PARK.equals(this);
    }

    public boolean isWeightSupported() {
        return weight != null;
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

    public boolean sameAs(EventType eventType) {
        return this.code.equals(eventType.getName());
    }

    public boolean sameAs(RecordedEvent event) {
        return this.code.equals(event.getEventType().getName());
    }

    public static Optional<Type> getKnownType(String code) {
        return Optional.ofNullable(KNOWN_TYPES.get(code));
    }

    public static Type fromCode(String code) {
        return getKnownType(code)
                .orElseGet(() -> new Type(code));
    }

    public static Type from(EventType eventType) {
        return fromCode(eventType.getName());
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
