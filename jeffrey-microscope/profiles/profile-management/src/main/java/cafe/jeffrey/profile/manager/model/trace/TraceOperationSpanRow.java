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

package cafe.jeffrey.profile.manager.model.trace;

/**
 * One span name in an operation's breakdown, carrying both readings of its time.
 * <p>
 * Inclusive times contain the span's children, so those rows sum past the operation's own duration
 * and answer "which part of the tree is this request in". Self times contain only the span's own
 * work, so those rows sum to the operation's time and answer "which code should I go and look at".
 * The two rankings routinely disagree, which is why the breakdown offers both.
 *
 * @param name         span name, as the waterfall shows it
 * @param eventType    the event the row was made from, verbatim — {@code jeffrey.JdbcQuery} for an
 *                     instrumented span, {@code jdk.SocketRead} for a wait the derivation promoted.
 *                     It is what lets the breakdown draw a blocking wait apart from a call the
 *                     application made on purpose, rather than guessing from the display name.
 * @param occurrences  how many spans of this name the operation's traces contain
 * @param traceCount   how many traces contain at least one
 * @param totalNanos   summed inclusive duration across all of them
 * @param selfNanos    summed self time across all of them
 * @param p50Nanos     median inclusive duration of one occurrence
 * @param p50SelfNanos median self time of one occurrence
 * @param p99Nanos     99th-percentile inclusive duration of one occurrence
 * @param p99SelfNanos 99th-percentile self time of one occurrence
 * @param maxNanos     the slowest single occurrence, inclusive
 */
public record TraceOperationSpanRow(
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
