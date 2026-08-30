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
