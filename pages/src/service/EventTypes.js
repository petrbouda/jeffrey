export default class EventTypes {

    static EXECUTION_SAMPLE = 'jdk.ExecutionSample'
    static OBJECT_ALLOCATION_IN_NEW_TLAB = "jdk.ObjectAllocationInNewTLAB"
    static OBJECT_ALLOCATION_SAMPLE = "jdk.ObjectAllocationSample"

    static isAllocationEventType(eventType) {
        return eventType === this.OBJECT_ALLOCATION_IN_NEW_TLAB || eventType === this.OBJECT_ALLOCATION_SAMPLE
    }

    static isExecutionEventType(eventType) {
        return eventType === this.EXECUTION_SAMPLE
    }
}
