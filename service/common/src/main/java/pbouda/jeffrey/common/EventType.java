package pbouda.jeffrey.common;

public record EventType(String code) {

    public static final EventType EXECUTION_SAMPLES = new EventType("jdk.ExecutionSample");
    public static final EventType ALLOCATIONS = new EventType("jdk.ObjectAllocationInNewTLAB");
    public static final EventType LIVE_OBJECTS = new EventType("profiler.LiveObject");
    public static final EventType LOCKS = new EventType("jdk.ThreadPark");
    public static final EventType ACTIVE_RECORDING = new EventType("jdk.ActiveRecording");
    public static final EventType GC_CONFIGURATION = new EventType("jdk.GCConfiguration");
    public static final EventType GC_HEAP_CONFIGURATION = new EventType("jdk.GCHeapConfiguration");
    public static final EventType GC_SURVIVOR_CONFIGURATION = new EventType("jdk.GCSurvivorConfiguration");
    public static final EventType GC_TLAB_CONFIGURATION = new EventType("jdk.GCTLABConfiguration");
    public static final EventType YOUNG_GENERATION_CONFIGURATION = new EventType("jdk.YoungGenerationConfiguration");
    public static final EventType COMPILER_CONFIGURATION = new EventType("jdk.CompilerConfiguration");
    public static final EventType CONTAINER_CONFIGURATION = new EventType("jdk.ContainerConfiguration");
    public static final EventType JVM_INFORMATION = new EventType("jdk.JVMInformation");
    public static final EventType CPU_INFORMATION = new EventType("jdk.CPUInformation");
    public static final EventType OS_INFORMATION = new EventType("jdk.OSInformation");
    public static final EventType VIRTUALIZATION_INFORMATION = new EventType("jdk.VirtualizationInformation");
}
