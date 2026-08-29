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

/**
 * One method's timing tally, as `jdk.MethodTiming` counted it.
 *
 * Every figure is exact and complete rather than sampled: the JVM instruments the method and keeps
 * running counters, so it can watch one called millions of times for a fixed price. The trade is
 * that it keeps no stack, no thread and no individual invocation — it can say a method was called
 * 4.2 million times averaging 3 µs, and can never say who called it or when the slow one happened.
 */
export interface MethodTimingStat {
  className: string;
  methodName: string;
  /** Calls over the whole recording. */
  invocations: number;
  minNanos: number;
  /** The mean across every call, as the JVM computed it — not derivable from the other columns. */
  avgNanos: number;
  maxNanos: number;
}

export default interface MethodTimingData {
  /** One row per method, most-invoked first. */
  methods: MethodTimingStat[];
  /**
   * Summed across the methods. A scale marker for the header, not a meaningful quantity in itself —
   * the methods are unrelated and one may sit inside another.
   */
  totalInvocations: number;
}
