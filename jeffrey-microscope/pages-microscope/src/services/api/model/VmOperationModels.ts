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

export interface VmOverview {
  vmOperationCount: number;
  totalSafepointPauseNanos: number;
  longestPauseNanos: number;
  longestPauseOperation: string | null;
  hasVmOperations: boolean;
  /**
   * Whether `jdk.SafepointStateSynchronization` is present — the per-safepoint view of how long the
   * JVM spent getting every thread to stop. Was called `hasSafepointLatency`, which read as a claim
   * about `jdk.SafepointLatency` and never was one.
   */
  hasTimeToSafepoint: boolean;
  /**
   * Whether `jdk.SafepointLatency` is present — the per-thread view, and the only one that can name
   * who the JVM was waiting for.
   */
  hasSafepointOffenders: boolean;
}

/**
 * One thread's record of holding the JVM up on its way into safepoints.
 *
 * Aggregated per thread because the event fires once *per thread per safepoint*: a small app across
 * 16 safepoints already writes 584 of them, so the finding only exists in their distribution.
 */
export interface SafepointOffender {
  threadName: string;
  /**
   * What the thread was doing when the safepoint was requested, verbatim (`_thread_in_Java`,
   * `_thread_in_native`, ...) — the column that turns a number into a diagnosis.
   */
  threadState: string;
  count: number;
  maxNanos: number;
  p99Nanos: number;
  totalNanos: number;
}

export interface SafepointLatencyData {
  /** The worst threads, longest first, capped — a page ranks rather than enumerates. */
  offenders: SafepointOffender[];
  /** How many distinct threads were measured, so the table's cap is visible rather than silent. */
  threadCount: number;
  worstNanos: number;
  /**
   * Summed across every thread and safepoint. Not elapsed time: threads reach a safepoint
   * concurrently, so this is a sum of overlapping waits and is only meaningful as a ranking weight.
   */
  totalNanos: number;
}

export interface VmOperationStat {
  operation: string;
  count: number;
  totalNanos: number;
  maxNanos: number;
  safepoint: boolean;
  blocking: boolean;
}
