/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/**
 * The headline figures of a perf-counters snapshot (`GET /perfcounters/overview`). Every figure is
 * null when the JVM did not write the counters behind it; durations are nanoseconds, sizes bytes,
 * and points in time UTC epoch milliseconds.
 */
export interface PerfCountersOverview {
  jvm: PerfCountersJvm;
  runtime: PerfCountersRuntime;
  memory: PerfCountersMemory;
  gc: PerfCountersGc;
  classes: PerfCountersClasses;
  safepoints: PerfCountersSafepoints;
  jit: PerfCountersJit;
  threads: PerfCountersThreads;
}

export interface PerfCountersJvm {
  vmName: string | null;
  vmVersion: string | null;
  vmVendor: string | null;
  javaVersion: string | null;
  command: string | null;
  vmArgs: string | null;
  gcPolicy: string | null;
}

/** TIMER: the JVM's own timer. SAFEPOINTS: application plus safepoint time, up to the last safepoint. */
export type UptimeBasis = 'TIMER' | 'SAFEPOINTS';

export interface PerfCountersRuntime {
  startedAtEpochMs: number | null;
  vmInitMillis: number | null;
  uptimeNanos: number | null;
  uptimeBasis: UptimeBasis | null;
  applicationTimeNanos: number | null;
}

export interface PerfCountersGeneration {
  name: string | null;
  usedBytes: number | null;
  capacityBytes: number | null;
  maxCapacityBytes: number | null;
}

export interface PerfCountersPool {
  usedBytes: number | null;
  capacityBytes: number | null;
  maxCapacityBytes: number | null;
}

export interface PerfCountersMemory {
  generations: PerfCountersGeneration[];
  metaspace: PerfCountersPool;
  compressedClassSpace: PerfCountersPool;
}

export interface PerfCountersCollector {
  name: string | null;
  invocations: number | null;
  timeNanos: number | null;
}

export interface PerfCountersGc {
  collectors: PerfCountersCollector[];
  invocations: number | null;
  timeNanos: number | null;
  /** From 0 to 1. */
  shareOfUptime: number | null;
  lastCause: string | null;
}

export interface PerfCountersClasses {
  loaded: number | null;
  unloaded: number | null;
  sharedLoaded: number | null;
  loadedBytes: number | null;
  loadTimeNanos: number | null;
}

export interface PerfCountersSafepoints {
  count: number | null;
  timeNanos: number | null;
  syncTimeNanos: number | null;
}

export interface PerfCountersJit {
  compiles: number | null;
  bailouts: number | null;
  invalidations: number | null;
  timeNanos: number | null;
  codeBytes: number | null;
}

export interface PerfCountersThreads {
  live: number | null;
  peak: number | null;
  daemon: number | null;
  started: number | null;
}
