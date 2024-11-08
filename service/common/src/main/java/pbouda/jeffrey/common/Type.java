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
import java.util.Optional;
import java.util.function.Function;
import java.util.function.LongFunction;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@JsonDeserialize(using = TypeDeserializer.class)
@JsonSerialize(using = TypeSerializer.class)
public record Type(
        String code,
        boolean known,
        String weightFieldName,
        Function<RecordedEvent, Long> weightExtractor,
        LongFunction<String> weightFormatter) {

    public static final Type EXECUTION_SAMPLE = new Type(EventTypeName.EXECUTION_SAMPLE, true);
    public static final Type WALL_CLOCK_SAMPLE = new Type(EventTypeName.WALL_CLOCK_SAMPLE, true, "duration", e -> e.getDuration().toNanos(), DurationFormatter::format);
    public static final Type JAVA_MONITOR_ENTER = new Type(EventTypeName.JAVA_MONITOR_ENTER, true, "monitorClass", e -> e.getDuration().toNanos(), DurationFormatter::format);
    public static final Type JAVA_MONITOR_WAIT = new Type(EventTypeName.JAVA_MONITOR_WAIT, true, "monitorClass", e -> e.getDuration().toNanos(), DurationFormatter::format);
    public static final Type THREAD_PARK = new Type(EventTypeName.THREAD_PARK, true, "parkedClass", e -> e.getDuration().toNanos(), DurationFormatter::format);
    public static final Type OBJECT_ALLOCATION_IN_NEW_TLAB = new Type(EventTypeName.OBJECT_ALLOCATION_IN_NEW_TLAB, true, "allocationSize", e -> e.getLong("allocationSize"), BytesFormatter::format);
    public static final Type OBJECT_ALLOCATION_OUTSIDE_TLAB = new Type(EventTypeName.OBJECT_ALLOCATION_OUTSIDE_TLAB, true, "allocationSize", e -> e.getLong("allocationSize"), BytesFormatter::format);
    public static final Type OBJECT_ALLOCATION_SAMPLE = new Type(EventTypeName.OBJECT_ALLOCATION_SAMPLE, true, "weight", e -> e.getLong("weight"), BytesFormatter::format);
    public static final Type LIVE_OBJECTS = new Type(EventTypeName.LIVE_OBJECTS, true);
    public static final Type ACTIVE_RECORDING = new Type(EventTypeName.ACTIVE_RECORDING, true);
    public static final Type ACTIVE_SETTING = new Type(EventTypeName.ACTIVE_SETTING, true);
    public static final Type GC_CONFIGURATION = new Type(EventTypeName.GC_CONFIGURATION, true);
    public static final Type GC_HEAP_CONFIGURATION = new Type(EventTypeName.GC_HEAP_CONFIGURATION, true);
    public static final Type GC_SURVIVOR_CONFIGURATION = new Type(EventTypeName.GC_SURVIVOR_CONFIGURATION, true);
    public static final Type GC_TLAB_CONFIGURATION = new Type(EventTypeName.GC_TLAB_CONFIGURATION, true);
    public static final Type YOUNG_GENERATION_CONFIGURATION = new Type(EventTypeName.YOUNG_GENERATION_CONFIGURATION, true);
    public static final Type COMPILER_CONFIGURATION = new Type(EventTypeName.COMPILER_CONFIGURATION, true);
    public static final Type CONTAINER_CONFIGURATION = new Type(EventTypeName.CONTAINER_CONFIGURATION, true);
    public static final Type JVM_INFORMATION = new Type(EventTypeName.JVM_INFORMATION, true);
    public static final Type CPU_INFORMATION = new Type(EventTypeName.CPU_INFORMATION, true);
    public static final Type OS_INFORMATION = new Type(EventTypeName.OS_INFORMATION, true);
    public static final Type VIRTUALIZATION_INFORMATION = new Type(EventTypeName.VIRTUALIZATION_INFORMATION, true);

    private static final Map<String, Type> KNOWN_TYPES;

    public static final List<Type> WEIGHT_SUPPORTED_TYPES;

    static {
        KNOWN_TYPES = Stream.of(
                EXECUTION_SAMPLE,
                WALL_CLOCK_SAMPLE,
                JAVA_MONITOR_ENTER,
                JAVA_MONITOR_WAIT,
                THREAD_PARK,
                OBJECT_ALLOCATION_SAMPLE,
                OBJECT_ALLOCATION_IN_NEW_TLAB,
                OBJECT_ALLOCATION_OUTSIDE_TLAB,
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
                .filter(t -> t.weightFieldName != null)
                .toList();
    }

    public Type(String code, boolean known) {
        this(code, known, null, null, null);
    }

    public Type(String code) {
        this(code, false, null, null, null);
    }

    public boolean isInternallyKnown() {
        return known;
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

    public static List<Type> objectAllocationSamples() {
        return List.of(Type.OBJECT_ALLOCATION_SAMPLE);
    }

    public boolean isAllocationEvent() {
        return isTlabAllocationSamples() || isObjectAllocationSamples();
    }

    public List<Type> resolveAllocationTypes() {
        if (isTlabAllocationSamples()) {
            return Type.tlabAllocationSamples();
        } else if (isObjectAllocationSamples()) {
            return Type.tlabAllocationSamples();
        } else {
            throw new IllegalArgumentException("Unsupported allocation type: " + this.code);
        }
    }

    public boolean isWallClockSample() {
        return Type.WALL_CLOCK_SAMPLE.equals(this);
    }

    public boolean isExecutionSample() {
        return Type.EXECUTION_SAMPLE.equals(this);
    }

    public boolean isBlockingEvent() {
        return Type.JAVA_MONITOR_ENTER.equals(this)
                || Type.JAVA_MONITOR_WAIT.equals(this)
                || Type.THREAD_PARK.equals(this);
    }

    public boolean isWeightSupported() {
        return weightFieldName != null;
    }

    public LongFunction<String> weightFormatter() {
        return weightFormatter;
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
}
