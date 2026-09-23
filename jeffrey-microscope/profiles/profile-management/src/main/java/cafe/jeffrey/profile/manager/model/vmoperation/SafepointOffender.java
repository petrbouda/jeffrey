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
