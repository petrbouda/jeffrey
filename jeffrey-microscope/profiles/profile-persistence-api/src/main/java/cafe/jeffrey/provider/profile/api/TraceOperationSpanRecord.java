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

package cafe.jeffrey.provider.profile.api;

/**
 * One span name, aggregated across every trace of an operation — the "where does this operation
 * spend its time" row.
 * <p>
 * Carries both readings of "time", because they answer different questions and routinely disagree.
 * The <em>inclusive</em> one contains the span's children, so the rows sum past the operation's own
 * duration; it says which part of the tree a request is inside. The <em>self</em> one contains only
 * the span's own work, so the rows sum to the operation's time and can be ranked against each other;
 * it says which code to go and look at. A span that merely wraps three slow queries tops the first
 * list and barely registers on the second.
 *
 * @param name         the span name, as it appears in the waterfall
 * @param eventType    the event the spans of this row were made from — an instrumented
 *                     {@code jeffrey.*} span event, or the {@code jdk.*} blocking event the
 *                     derivation promoted into a leaf span. Rows are grouped by it as well as by
 *                     name, so a row is one kind of thing rather than a name shared by two.
 * @param occurrences  how many spans of this name the operation's traces contain in total
 * @param traceCount   how many of those traces contain at least one
 * @param totalNanos   summed inclusive duration across all of them
 * @param selfNanos    summed self time — what the spans of this name actually spent on their own
 *                     work, with their children's stretches taken out
 * @param p50Nanos     median inclusive duration of a single occurrence
 * @param p50SelfNanos median self time of a single occurrence
 * @param p99Nanos     99th-percentile inclusive duration of a single occurrence
 * @param p99SelfNanos 99th-percentile self time of a single occurrence
 * @param maxNanos     the slowest single occurrence, inclusive
 */
public record TraceOperationSpanRecord(
        String name,
        String eventType,
        long occurrences,
        long traceCount,
        long totalNanos,
        long selfNanos,
        long p50Nanos,
        long p50SelfNanos,
        long p99Nanos,
        long p99SelfNanos,
        long maxNanos) {
}
