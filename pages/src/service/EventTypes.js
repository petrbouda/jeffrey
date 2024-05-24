export default class EventTypes {

    static ASYNC_PROFILER_SOURCE = "ASYNC_PROFILER"

    static EXECUTION_SAMPLE = 'jdk.ExecutionSample'
    static OBJECT_ALLOCATION_IN_NEW_TLAB = "jdk.ObjectAllocationInNewTLAB"
    static OBJECT_ALLOCATION_SAMPLE = "jdk.ObjectAllocationSample"
    static JAVA_MONITOR_ENTER = "jdk.JavaMonitorEnter"
    static JAVA_MONITOR_WAIT = "jdk.JavaMonitorWait"
    static THREAD_PARK = "jdk.ThreadPark"

    static isObjectAllocationInNewTLAB(code) {
        return code === this.OBJECT_ALLOCATION_IN_NEW_TLAB
    }

    static isObjectAllocationSample(code) {
        return code === this.OBJECT_ALLOCATION_SAMPLE
    }

    static isJavaMonitorEnter(code) {
        return code === this.JAVA_MONITOR_ENTER
    }

    static isJavaMonitorWait(code) {
        return code === this.JAVA_MONITOR_WAIT
    }

    static isThreadPark(code) {
        return code === this.THREAD_PARK
    }

    static isAllocationEventType(code) {
        return this.isObjectAllocationInNewTLAB(code) || this.isObjectAllocationSample(code)
    }

    static isBlockingEventType(code) {
        return this.isJavaMonitorEnter(code) || this.isJavaMonitorWait(code) || this.isThreadPark(code)
    }

    static isDifferential(code) {
        return this.isJavaMonitorEnter(code) || this.isJavaMonitorWait(code) || this.isThreadPark(code)
    }

    static isExecutionEventType(code) {
        return code === this.EXECUTION_SAMPLE
    }
}
