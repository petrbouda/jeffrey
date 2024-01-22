package pbouda.jeffrey.manager;

public record EventType(String code) {

    public static final EventType EXECUTION_SAMPLES = new EventType("jdk.ExecutionSample");
    public static final EventType ALLOCATIONS = new EventType("jdk.ObjectAllocationInNewTLAB");
    public static final EventType LIVE_OBJECTS = new EventType("profiler.LiveObject");
    public static final EventType LOCKS = new EventType("jdk.ThreadPark");
}
