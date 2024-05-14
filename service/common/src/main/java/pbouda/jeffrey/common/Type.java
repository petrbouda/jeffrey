package pbouda.jeffrey.common;

import com.fasterxml.jackson.annotation.JsonValue;
import jdk.jfr.EventType;
import jdk.jfr.consumer.RecordedEvent;

public record Type(@JsonValue String code) {

    public static final Type EXECUTION_SAMPLE = new Type("jdk.ExecutionSample");
    public static final Type OBJECT_ALLOCATION_IN_NEW_TLAB = new Type("jdk.ObjectAllocationInNewTLAB");
    public static final Type OBJECT_ALLOCATION_SAMPLE = new Type("jdk.ObjectAllocationSample");
    public static final Type LIVE_OBJECTS = new Type("profiler.LiveObject");
    public static final Type LOCKS = new Type("jdk.ThreadPark");
    public static final Type ACTIVE_RECORDING = new Type("jdk.ActiveRecording");
    public static final Type ACTIVE_SETTING = new Type("jdk.ActiveSetting");
    public static final Type GC_CONFIGURATION = new Type("jdk.GCConfiguration");
    public static final Type GC_HEAP_CONFIGURATION = new Type("jdk.GCHeapConfiguration");
    public static final Type GC_SURVIVOR_CONFIGURATION = new Type("jdk.GCSurvivorConfiguration");
    public static final Type GC_TLAB_CONFIGURATION = new Type("jdk.GCTLABConfiguration");
    public static final Type YOUNG_GENERATION_CONFIGURATION = new Type("jdk.YoungGenerationConfiguration");
    public static final Type COMPILER_CONFIGURATION = new Type("jdk.CompilerConfiguration");
    public static final Type CONTAINER_CONFIGURATION = new Type("jdk.ContainerConfiguration");
    public static final Type JVM_INFORMATION = new Type("jdk.JVMInformation");
    public static final Type CPU_INFORMATION = new Type("jdk.CPUInformation");
    public static final Type OS_INFORMATION = new Type("jdk.OSInformation");
    public static final Type VIRTUALIZATION_INFORMATION = new Type("jdk.VirtualizationInformation");

    public boolean sameAs(EventType eventType) {
        return this.code.equals(eventType.getName());
    }

    public boolean sameAs(RecordedEvent event) {
        return this.code.equals(event.getEventType().getName());
    }
}
