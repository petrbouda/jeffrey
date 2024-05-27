import EventTypes from "@/service/EventTypes";

export default class EventTitleFormatter {

    static allocationSamples(event) {
        if (event != null) {
            if (EventTypes.isObjectAllocationInNewTLAB(event.code)) {
                if (event.extras != null && event.extras.source === EventTypes.ASYNC_PROFILER_SOURCE) {
                    return "Async-Profiler (" + EventTypes.OBJECT_ALLOCATION_IN_NEW_TLAB + ")"
                } else {
                    return "JDK (" + EventTypes.OBJECT_ALLOCATION_IN_NEW_TLAB + ")"
                }
            } else if (EventTypes.isObjectAllocationSample(event.code)) {
                return "JDK (" + EventTypes.OBJECT_ALLOCATION_SAMPLE + ")"
            } else {
                console.log("Unknown Object Allocation Source")
                return ""
            }
        }
    }

    static executionSamples(event) {
        if (event != null && event.extras != null) {
            const extras = event.extras

            if (extras.source === EventTypes.ASYNC_PROFILER_SOURCE) {
                if (extras.cpu_event === "cpu") {
                    return "Async-Profiler (CPU - perf_event)"
                } else {
                    return "Async-Profiler (" + extras.cpu_event + ")"
                }
            } else if (extras.source === "JDK") {
                return "JDK (Method Samples)"
            } else {
                console.log("Unknown CPU Source")
                return ""
            }
        }
    }

    static blockingSamples(event) {
        if (event.extras != null && event.extras.source === EventTypes.ASYNC_PROFILER_SOURCE) {
            return "Async-Profiler (" + event.code + ")"
        } else {
            return "JDK (" + event.code + ")"
        }
    }

}
