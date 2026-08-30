/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
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
