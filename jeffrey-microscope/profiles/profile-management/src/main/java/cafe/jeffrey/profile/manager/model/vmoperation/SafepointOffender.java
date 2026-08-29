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

/**
 * One thread's record of holding the JVM up on its way into safepoints.
 * <p>
 * Aggregated per thread rather than listed per event, and it has to be: {@code jdk.SafepointLatency}
 * fires once <em>per thread per safepoint</em>, so a recording with a few hundred safepoints and a
 * few hundred threads writes tens of thousands of events that say almost nothing individually. The
 * question the page asks — which thread is habitually slow to yield — is a question about their
 * distribution.
 *
 * @param threadName  the thread that had to be waited for
 * @param threadState what it was doing when the safepoint was requested, verbatim from the event
 *                    ({@code _thread_in_Java}, {@code _thread_in_native}, ...). This is the column
 *                    that turns a number into a diagnosis: a thread slow to yield from
 *                    {@code _thread_in_Java} is running a loop the JIT stripped the safepoint poll
 *                    out of, while one slow from {@code _thread_in_native} is stuck in a call the
 *                    JVM cannot interrupt at all
 * @param count       how many safepoints this thread was measured for
 * @param maxNanos    its worst single time-to-safepoint
 * @param p99Nanos    its 99th percentile, so one outlier does not read as a habit
 * @param totalNanos  the summed latency, which ranks a thread that is always a little slow against
 *                    one that is occasionally very slow
 */
public record SafepointOffender(
        String threadName,
        String threadState,
        long count,
        long maxNanos,
        long p99Nanos,
        long totalNanos) {
}
