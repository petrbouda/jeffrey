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

package cafe.jeffrey.profile.manager.model.vmoperation;

import java.util.List;

/**
 * Who the JVM waited for on its way into safepoints.
 * <p>
 * The pause timeline on the same page says how long the world stood still, and the time-to-safepoint
 * series says how much of that was spent merely getting there. Neither can say <em>whose</em> fault
 * the getting-there was, because both are recorded once per safepoint on a VM thread.
 * {@code jdk.SafepointLatency} is recorded on each application thread instead, which is what makes
 * naming the culprit possible at all.
 *
 * @param offenders     the worst threads, longest first, capped — a page ranks, it does not
 *                      enumerate
 * @param threadCount   how many distinct threads were measured, so the table's cap is visible rather
 *                      than silent
 * @param worstNanos    the longest single time-to-safepoint anywhere in the recording
 * @param totalNanos    the summed latency across every thread and safepoint. Deliberately not read
 *                      as elapsed time: threads reach a safepoint concurrently, so this is a sum of
 *                      overlapping waits and is only meaningful as a ranking weight
 */
public record SafepointLatencyData(
        List<SafepointOffender> offenders,
        int threadCount,
        long worstNanos,
        long totalNanos) {

    public static final SafepointLatencyData EMPTY = new SafepointLatencyData(List.of(), 0, 0, 0);
}
