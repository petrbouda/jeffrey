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

import EventTypes from "@/service/EventTypes";

export default class EventTitleFormatter {

    static allocationSamples(event) {
        if (event != null) {
            if (EventTypes.isObjectAllocationInNewTLAB(event["code"])) {
                if (event["extras"] != null && event["extras"]["source"] === EventTypes.ASYNC_PROFILER_SOURCE) {
                    return "Async-Profiler (" + EventTypes.OBJECT_ALLOCATION_IN_NEW_TLAB + ")"
                } else {
                    return "JDK (" + EventTypes.OBJECT_ALLOCATION_IN_NEW_TLAB + ")"
                }
            } else if (EventTypes.isObjectAllocationSample(event["code"])) {
                return "JDK (" + EventTypes.OBJECT_ALLOCATION_SAMPLE + ")"
            } else {
                console.log("Unknown Object Allocation Source")
                return ""
            }
        }
    }

    static executionSamples(event) {
        if (event != null && event["extras"] != null) {
            const extras = event["extras"]

            if (extras.source === EventTypes.ASYNC_PROFILER_SOURCE) {
                if (extras["cpu_event"] === "cpu") {
                    return "Async-Profiler (CPU - perf_event)"
                } else {
                    return "Async-Profiler (" + extras["cpu_event"] + ")"
                }
            } else if (extras["source"] === "JDK") {
                return "JDK (Method Samples)"
            } else {
                console.log("Unknown CPU Source")
                return ""
            }
        }
    }

    static blockingSamples(event) {
        if (event["extras"] != null && event["extras"]["source"] === EventTypes.ASYNC_PROFILER_SOURCE) {
            return "Async-Profiler (" + event["code"] + ")"
        } else {
            return "JDK (" + event["code"] + ")"
        }
    }

}
