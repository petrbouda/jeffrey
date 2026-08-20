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
