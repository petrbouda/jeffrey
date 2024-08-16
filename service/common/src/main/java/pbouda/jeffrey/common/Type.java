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

    public static final Type EXECUTION_SAMPLE = new Type("jdk.ExecutionSample", true);
    public static final Type JAVA_MONITOR_ENTER = new Type("jdk.JavaMonitorEnter", true, "monitorClass", e -> e.getDuration().toNanos(), DurationFormatter::format);
    public static final Type JAVA_MONITOR_WAIT = new Type("jdk.JavaMonitorWait", true, "monitorClass", e -> e.getDuration().toNanos(), DurationFormatter::format);
    public static final Type THREAD_PARK = new Type("jdk.ThreadPark", true, "parkedClass", e -> e.getDuration().toNanos(), DurationFormatter::format);
    public static final Type OBJECT_ALLOCATION_IN_NEW_TLAB = new Type("jdk.ObjectAllocationInNewTLAB", true, "allocationSize", e -> e.getLong("allocationSize"), BytesFormatter::format);
    public static final Type OBJECT_ALLOCATION_OUTSIDE_TLAB = new Type("jdk.ObjectAllocationOutsideTLAB", true, "allocationSize", e -> e.getLong("allocationSize"), BytesFormatter::format);
    public static final Type OBJECT_ALLOCATION_SAMPLE = new Type("jdk.ObjectAllocationSample", true, "weight", e -> e.getLong("weight"), BytesFormatter::format);
    public static final Type LIVE_OBJECTS = new Type("profiler.LiveObject", true);
    public static final Type ACTIVE_RECORDING = new Type("jdk.ActiveRecording", true);
    public static final Type ACTIVE_SETTING = new Type("jdk.ActiveSetting", true);
    public static final Type GC_CONFIGURATION = new Type("jdk.GCConfiguration", true);
    public static final Type GC_HEAP_CONFIGURATION = new Type("jdk.GCHeapConfiguration", true);
    public static final Type GC_SURVIVOR_CONFIGURATION = new Type("jdk.GCSurvivorConfiguration", true);
    public static final Type GC_TLAB_CONFIGURATION = new Type("jdk.GCTLABConfiguration", true);
    public static final Type YOUNG_GENERATION_CONFIGURATION = new Type("jdk.YoungGenerationConfiguration", true);
    public static final Type COMPILER_CONFIGURATION = new Type("jdk.CompilerConfiguration", true);
    public static final Type CONTAINER_CONFIGURATION = new Type("jdk.ContainerConfiguration", true);
    public static final Type JVM_INFORMATION = new Type("jdk.JVMInformation", true);
    public static final Type CPU_INFORMATION = new Type("jdk.CPUInformation", true);
    public static final Type OS_INFORMATION = new Type("jdk.OSInformation", true);
    public static final Type VIRTUALIZATION_INFORMATION = new Type("jdk.VirtualizationInformation", true);

    private static final Map<String, Type> KNOWN_TYPES;

    public static final List<Type> WEIGHT_SUPPORTED_TYPES;

    static {
        KNOWN_TYPES = Stream.of(
                EXECUTION_SAMPLE,
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

    public boolean isAllocationTlab() {
        return Type.OBJECT_ALLOCATION_IN_NEW_TLAB.equals(this)
                || Type.OBJECT_ALLOCATION_OUTSIDE_TLAB.equals(this);
    }

    public boolean isAllocationSamples() {
        return Type.OBJECT_ALLOCATION_SAMPLE.equals(this);
    }

    public boolean isAllocationEvent() {
        return isAllocationTlab() || isAllocationSamples();
    }

    public boolean isWeightSupported() {
        return weightFieldName != null;
    }

    public LongFunction<String> weightFormatter() {
        return weightFormatter;
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
}
