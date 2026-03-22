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

import GlobalVars from '@/services/GlobalVars'

export default class EventTypes {
    static EXECUTION_SAMPLE = 'jdk.ExecutionSample'
    static METHOD_TRACE = 'jdk.MethodTrace'
    static OBJECT_ALLOCATION_IN_NEW_TLAB = "jdk.ObjectAllocationInNewTLAB"
    static OBJECT_ALLOCATION_OUTSIDE_TLAB = "jdk.ObjectAllocationOutsideTLAB"
    static OBJECT_ALLOCATION_SAMPLE = "jdk.ObjectAllocationSample"
    static JAVA_MONITOR_ENTER = "jdk.JavaMonitorEnter"
    static JAVA_MONITOR_WAIT = "jdk.JavaMonitorWait"
    static THREAD_PARK = "jdk.ThreadPark"
    static WALL_CLOCK = "profiler.WallClockSample"
    static NATIVE_MALLOC_ALLOCATION = "profiler.Malloc"
    static NATIVE_LEAK = "jeffrey.NativeLeak"

    static isObjectAllocationInNewTLAB(code: string) {
        return code === this.OBJECT_ALLOCATION_IN_NEW_TLAB
    }

    static isObjectAllocationOutsideTLAB(code: string) {
        return code === this.OBJECT_ALLOCATION_OUTSIDE_TLAB
    }

    static isObjectAllocationSample(code: string) {
        return code === this.OBJECT_ALLOCATION_SAMPLE
    }

    static isJavaMonitorEnter(code: string) {
        return code === this.JAVA_MONITOR_ENTER
    }

    static isJavaMonitorWait(code: string) {
        return code === this.JAVA_MONITOR_WAIT
    }

    static isThreadPark(code: string) {
        return code === this.THREAD_PARK
    }

    static isWallClock(code: string) {
        return code === this.WALL_CLOCK
    }

    static isAllocationEventType(code: string) {
        return this.isObjectAllocationInNewTLAB(code)
            || this.isObjectAllocationOutsideTLAB(code)
            || this.isObjectAllocationSample(code)
    }

    static isBlockingEventType(code: string) {
        return this.isJavaMonitorEnter(code)
            || this.isJavaMonitorWait(code)
            || this.isThreadPark(code)
    }

    static isDifferential(code: string) {
        return this.isJavaMonitorEnter(code)
            || this.isJavaMonitorWait(code)
            || this.isThreadPark(code)
    }

    static isExecutionEventType(code: string) {
        return code === this.EXECUTION_SAMPLE
    }

    static isMethodTraceEventType(code: string) {
        return code === this.METHOD_TRACE
    }

    static isMallocAllocationEventType(code: string) {
        return code === this.NATIVE_MALLOC_ALLOCATION
    }

    static isNativeLeakEventType(code: string) {
        return code === this.NATIVE_LEAK
    }

    static getSapDocumentationUrl(code: string): string | null {
        if (!code || !code.startsWith('jdk.')) {
            return null
        }
        const eventName = code.replace('jdk.', '')
        return `${GlobalVars.SAP_EVENT_LINK}?search=${eventName}`
    }
}
