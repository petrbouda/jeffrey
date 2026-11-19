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
