package pbouda.jeffrey.flamegraph;

public enum EventType {
    EXECUTION_SAMPLES("jdk.ExecutionSample"),
    // TODO: change to jdk.ObjectAllocationSample
    ALLOCATIONS("jdk.ObjectAllocationInNewTLAB"),
    LIVE_OBJECTS("profiler.LiveObject"),
    LOCKS("jdk.ThreadPark");

    private final String eventTypeName;

    EventType(String eventTypeName) {
        this.eventTypeName = eventTypeName;
    }

    public String eventTypeName() {
        return eventTypeName;
    }
}
