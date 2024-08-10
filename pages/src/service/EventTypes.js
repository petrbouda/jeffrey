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

export default class EventTypes {

    static ASYNC_PROFILER_SOURCE = "ASYNC_PROFILER"

    static EXECUTION_SAMPLE = 'jdk.ExecutionSample'
    static OBJECT_ALLOCATION_IN_NEW_TLAB = "jdk.ObjectAllocationInNewTLAB"
    static OBJECT_ALLOCATION_OUTSIDE_TLAB = "jdk.ObjectAllocationOutsideTLAB"
    static OBJECT_ALLOCATION_SAMPLE = "jdk.ObjectAllocationSample"
    static JAVA_MONITOR_ENTER = "jdk.JavaMonitorEnter"
    static JAVA_MONITOR_WAIT = "jdk.JavaMonitorWait"
    static THREAD_PARK = "jdk.ThreadPark"

    static isObjectAllocationInNewTLAB(code) {
        return code === this.OBJECT_ALLOCATION_IN_NEW_TLAB
    }

    static isObjectAllocationOutsideTLAB(code) {
        return code === this.OBJECT_ALLOCATION_OUTSIDE_TLAB
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
        return this.isObjectAllocationInNewTLAB(code)
            || this.isObjectAllocationOutsideTLAB(code)
            || this.isObjectAllocationSample(code)
    }

    static isBlockingEventType(code) {
        return this.isJavaMonitorEnter(code)
            || this.isJavaMonitorWait(code)
            || this.isThreadPark(code)
    }

    static isDifferential(code) {
        return this.isJavaMonitorEnter(code)
            || this.isJavaMonitorWait(code)
            || this.isThreadPark(code)
    }

    static isExecutionEventType(code) {
        return code === this.EXECUTION_SAMPLE
    }
}
