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

export interface ProfilerConfig {
  agentPathCustom: string;
  event: string;
  wallValue: number | null;
  wallUnit: string;
  loopValue: number;
  loopUnit: string;
  intervalValue: number | null;
  intervalUnit: string;
  allocThresholdEnabled: boolean;
  allocValue: number | null;
  allocUnit: string;
  lockThresholdValue: number | null;
  lockThresholdUnit: string;
  methodTraces: MethodTraceTarget[];
  nativeMemValue: number | null;
  nativeMemUnit: string;
  nativeMemOmitFree: boolean;
  chunksizeValue: number;
  chunksizeUnit: string;
  chunktimeValue: number;
  chunktimeUnit: string;
  jfrsync: string;
  jfrsyncFile: string;
  jfcMode: string;
  file: string;
}

/**
 * One `trace=` target. Without a latency every call is recorded; with one, async-profiler keeps
 * only the calls that took at least that long (`trace=Class.method:5ms`).
 */
export interface MethodTraceTarget {
  /** Identifies the row across edits and removals; the command never carries it. */
  id: number;
  pattern: string;
  latencyValue: number | null;
  latencyUnit: string;
}

export interface OptionStates {
  event: boolean;
  alloc: boolean;
  lock: boolean;
  wall: boolean;
  methodTracing: boolean;
  nativeMem: boolean;
  chunksize: boolean;
  chunktime: boolean;
  jfrsync: boolean;
}

export interface ConfigToken {
  key: string;
  label: string;
  value: string;
}

export interface ConfigCardDefinition {
  id: keyof OptionStates;
  title: string;
  subtitle: string;
  icon: string;
  cardType: 'required' | 'optional';
  component?: string;
}

/** Placeholder for the Agent Path field, and the path used when it is left blank. */
export const DEFAULT_AGENT_PATH = '/path/to/libasyncProfiler.so';

/** Where a copied command writes its recordings when the output field is left blank. */
export const DEFAULT_OUTPUT_FILE = '/tmp/profile-%t.jfr';

/**
 * The builder starts lock profiling at 10 µs, what a bare `lock` and the `all` preset use, written
 * out as `lock=10us` so the field shows it. Recording every contention (`lock=0`) is as costly as
 * recording every malloc, so it is a deliberate choice rather than the default.
 */
export const DEFAULT_LOCK_THRESHOLD = { value: 10, unit: 'us' } as const;

/** What `all` uses for native memory. A bare `nativemem` would record every malloc. */
export const DEFAULT_NATIVE_MEM_INTERVAL = { value: 512, unit: 'kb' } as const;

/** A newly traced method records every call (`trace=M`) until a threshold is set on its row. */
export const DEFAULT_TRACE_LATENCY = { value: 0, unit: 'ms' } as const;

export const PROFILER_CONSTANTS = {
  selectableEvents: ['ctimer', 'cpu'] as const
} as const;

/**
 * A fresh configuration for one builder. It is a function, not a shared object: the traced-method
 * list is mutable, and a spread of a shared default would hand every builder the same array.
 */
export function defaultProfilerConfig(): ProfilerConfig {
  return {
    agentPathCustom: '',
    event: 'ctimer',
    wallValue: null,
    wallUnit: 'ms',
    loopValue: 15,
    loopUnit: 'm',
    intervalValue: null,
    intervalUnit: 'ms',
    allocThresholdEnabled: false,
    allocValue: null,
    allocUnit: 'mb',
    lockThresholdValue: DEFAULT_LOCK_THRESHOLD.value,
    lockThresholdUnit: DEFAULT_LOCK_THRESHOLD.unit,
    methodTraces: [],
    nativeMemValue: DEFAULT_NATIVE_MEM_INTERVAL.value,
    nativeMemUnit: DEFAULT_NATIVE_MEM_INTERVAL.unit,
    nativeMemOmitFree: false,
    chunksizeValue: 5,
    chunksizeUnit: 'm',
    chunktimeValue: 1,
    chunktimeUnit: 'h',
    jfrsync: 'default',
    jfrsyncFile: '',
    jfcMode: 'default',
    file: DEFAULT_OUTPUT_FILE
  };
}
