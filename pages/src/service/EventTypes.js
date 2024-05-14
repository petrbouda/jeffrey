export default class EventTypes {

    static ASYNC_PROFILER_SOURCE = "ASYNC_PROFILER"

    static EXECUTION_SAMPLE = 'jdk.ExecutionSample'
    static OBJECT_ALLOCATION_IN_NEW_TLAB = "jdk.ObjectAllocationInNewTLAB"
    static OBJECT_ALLOCATION_SAMPLE = "jdk.ObjectAllocationSample"

    static isObjectAllocationInNewTLAB(eventType) {
        return eventType === this.OBJECT_ALLOCATION_IN_NEW_TLAB
    }

    static isObjectAllocationSample(eventType) {
        return eventType === this.OBJECT_ALLOCATION_SAMPLE
    }

    static isAllocationEventType(eventType) {
        return this.isObjectAllocationInNewTLAB(eventType) || this.isObjectAllocationSample(eventType)
    }

    static isExecutionEventType(eventType) {
        return eventType === this.EXECUTION_SAMPLE
    }
}
