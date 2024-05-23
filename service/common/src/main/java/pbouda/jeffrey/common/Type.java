package pbouda.jeffrey.common;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jdk.jfr.EventType;
import jdk.jfr.consumer.RecordedEvent;

import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@JsonDeserialize(using = TypeDeserializer.class)
public record Type(String code, boolean known, String weightFieldName) {

    public static final Type EXECUTION_SAMPLE = new Type("jdk.ExecutionSample", true);
    public static final Type JAVA_MONITOR_ENTER = new Type("jdk.JavaMonitorEnter", true, "monitorClass");
    public static final Type JAVA_MONITOR_WAIT = new Type("jdk.JavaMonitorWait", true, "monitorClass");
    public static final Type THREAD_PARK = new Type("jdk.ThreadPark", true, "parkedClass");
    public static final Type OBJECT_ALLOCATION_IN_NEW_TLAB = new Type("jdk.ObjectAllocationInNewTLAB", true, "allocationSize");
    public static final Type OBJECT_ALLOCATION_OUTSIDE_TLAB = new Type("jdk.ObjectAllocationOutsideTLAB", true, "allocationSize");
    public static final Type OBJECT_ALLOCATION_SAMPLE = new Type("jdk.ObjectAllocationSample", true, "weight");
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
    }

    public Type(String code, boolean known) {
        this(code, known, null);
    }

    public Type(String code) {
        this(code, false, null);
    }

    public boolean isInternallyKnown() {
        return known;
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
}
