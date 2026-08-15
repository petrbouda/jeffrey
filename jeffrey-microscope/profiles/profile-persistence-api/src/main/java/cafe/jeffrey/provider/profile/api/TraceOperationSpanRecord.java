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
 * @param occurrences  how many spans of this name the operation's traces contain in total
 * @param traceCount   how many of those traces contain at least one
 * @param totalNanos   summed inclusive duration across all of them
 * @param selfNanos    summed self time — what the spans of this name actually spent on their own
 *                     work, with their children's stretches taken out
 * @param p50Nanos     median inclusive duration of a single occurrence
 * @param p50SelfNanos median self time of a single occurrence
 * @param maxNanos     the slowest single occurrence, inclusive
 */
public record TraceOperationSpanRecord(
        String name,
        long occurrences,
        long traceCount,
        long totalNanos,
        long selfNanos,
        long p50Nanos,
        long p50SelfNanos,
        long maxNanos) {
}
