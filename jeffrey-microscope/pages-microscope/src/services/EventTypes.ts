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

import GlobalVars from '@/services/GlobalVars';

export default class EventTypes {
  static EXECUTION_SAMPLE = 'jdk.ExecutionSample';
  static CPU_TIME_SAMPLE = 'jdk.CPUTimeSample';
  static METHOD_TRACE = 'jdk.MethodTrace';
  static OBJECT_ALLOCATION_IN_NEW_TLAB = 'jdk.ObjectAllocationInNewTLAB';
  static OBJECT_ALLOCATION_OUTSIDE_TLAB = 'jdk.ObjectAllocationOutsideTLAB';
  static OBJECT_ALLOCATION_SAMPLE = 'jdk.ObjectAllocationSample';
  static JAVA_MONITOR_ENTER = 'jdk.JavaMonitorEnter';
  static JAVA_MONITOR_WAIT = 'jdk.JavaMonitorWait';
  static THREAD_PARK = 'jdk.ThreadPark';
  static VIRTUAL_THREAD_PINNED = 'jdk.VirtualThreadPinned';
  static WALL_CLOCK = 'profiler.WallClockSample';
  static NATIVE_MALLOC_ALLOCATION = 'profiler.Malloc';
  static NATIVE_LEAK = 'jeffrey.NativeLeak';

  // pprof profiles carry one event type per sample-value dimension, namespaced `pprof.<type>`
  // (matching the backend PprofEventTypeNaming). The dimension name drives which analysis cards
  // a pprof event type lights up.
  static PPROF_NAMESPACE = 'pprof.';
  static PPROF_EXECUTION_TYPES = ['cpu', 'samples'];
  static PPROF_WALL_TYPES = ['wall'];
  static PPROF_ALLOCATION_TYPES = [
    'alloc_space',
    'alloc_objects',
    'inuse_space',
    'inuse_objects',
    'allocations',
    'space',
    'alloc',
    'inuse'
  ];
  static PPROF_BLOCKING_TYPES = ['contentions', 'delay', 'block', 'mutex'];

  // OpenTelemetry (OTLP) profiles carry one event type per sample-value dimension, namespaced
  // `otel.<type>` (matching the backend OtelEventTypeNaming). The dimension name drives which analysis
  // cards an OTLP event type lights up.
  static OTEL_NAMESPACE = 'otel.';
  static OTEL_CPU = 'otel.cpu';
  static OTEL_SAMPLES = 'otel.samples';
  static OTEL_WALL = 'otel.wall';
  static OTEL_ALLOC = 'otel.alloc';
  static OTEL_LOCK = 'otel.lock';
  static OTEL_EXECUTION_TYPES = ['cpu', 'samples'];
  static OTEL_WALL_TYPES = ['wall'];
  static OTEL_ALLOCATION_TYPES = ['alloc', 'allocations', 'alloc_space', 'alloc_objects'];
  static OTEL_BLOCKING_TYPES = ['lock', 'block', 'mutex', 'contentions', 'delay'];

  static isPprofEvent(code: string) {
    return code != null && code.startsWith(this.PPROF_NAMESPACE);
  }

  private static isPprofOfType(code: string, types: string[]) {
    if (!this.isPprofEvent(code)) {
      return false;
    }
    return types.includes(code.substring(this.PPROF_NAMESPACE.length));
  }

  static isOtelEvent(code: string) {
    return code != null && code.startsWith(this.OTEL_NAMESPACE);
  }

  private static isOtelOfType(code: string, types: string[]) {
    if (!this.isOtelEvent(code)) {
      return false;
    }
    return types.includes(code.substring(this.OTEL_NAMESPACE.length));
  }

  static isObjectAllocationInNewTLAB(code: string) {
    return code === this.OBJECT_ALLOCATION_IN_NEW_TLAB;
  }

  static isObjectAllocationOutsideTLAB(code: string) {
    return code === this.OBJECT_ALLOCATION_OUTSIDE_TLAB;
  }

  static isObjectAllocationSample(code: string) {
    return code === this.OBJECT_ALLOCATION_SAMPLE;
  }

  static isJavaMonitorEnter(code: string) {
    return code === this.JAVA_MONITOR_ENTER;
  }

  static isJavaMonitorWait(code: string) {
    return code === this.JAVA_MONITOR_WAIT;
  }

  static isThreadPark(code: string) {
    return code === this.THREAD_PARK;
  }

  static isVirtualThreadPinned(code: string) {
    return code === this.VIRTUAL_THREAD_PINNED;
  }

  static isWallClock(code: string) {
    return (
      code === this.WALL_CLOCK ||
      this.isPprofOfType(code, this.PPROF_WALL_TYPES) ||
      this.isOtelOfType(code, this.OTEL_WALL_TYPES)
    );
  }

  static isAllocationEventType(code: string) {
    return (
      this.isObjectAllocationInNewTLAB(code) ||
      this.isObjectAllocationOutsideTLAB(code) ||
      this.isObjectAllocationSample(code) ||
      this.isPprofOfType(code, this.PPROF_ALLOCATION_TYPES) ||
      this.isOtelOfType(code, this.OTEL_ALLOCATION_TYPES)
    );
  }

  static isBlockingEventType(code: string) {
    return (
      this.isJavaMonitorEnter(code) ||
      this.isJavaMonitorWait(code) ||
      this.isThreadPark(code) ||
      this.isVirtualThreadPinned(code) ||
      this.isPprofOfType(code, this.PPROF_BLOCKING_TYPES) ||
      this.isOtelOfType(code, this.OTEL_BLOCKING_TYPES)
    );
  }

  static isDifferential(code: string) {
    return (
      this.isJavaMonitorEnter(code) ||
      this.isJavaMonitorWait(code) ||
      this.isThreadPark(code) ||
      this.isVirtualThreadPinned(code)
    );
  }

  static isExecutionEventType(code: string) {
    return (
      code === this.EXECUTION_SAMPLE ||
      this.isPprofOfType(code, this.PPROF_EXECUTION_TYPES) ||
      this.isOtelOfType(code, this.OTEL_EXECUTION_TYPES)
    );
  }

  static isCpuTimeSample(code: string) {
    return code === this.CPU_TIME_SAMPLE;
  }

  static isMethodTraceEventType(code: string) {
    return code === this.METHOD_TRACE;
  }

  static isMallocAllocationEventType(code: string) {
    return code === this.NATIVE_MALLOC_ALLOCATION;
  }

  static isNativeLeakEventType(code: string) {
    return code === this.NATIVE_LEAK;
  }

  static getSapDocumentationUrl(code: string): string | null {
    if (!code || !code.startsWith('jdk.')) {
      return null;
    }
    const eventName = code.replace('jdk.', '');
    return `${GlobalVars.SAP_EVENT_LINK}?search=${eventName}`;
  }
}
