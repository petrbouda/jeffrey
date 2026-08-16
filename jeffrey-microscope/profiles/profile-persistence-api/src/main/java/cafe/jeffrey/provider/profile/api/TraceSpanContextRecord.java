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
 * How much of one span's window went to one kind of waiting.
 * <p>
 * One row per {@code (span, category)} that actually recorded something, so a span that only ever
 * ran has no rows at all rather than a row of zeroes for every category there is.
 * <p>
 * The times are summed event durations, not merged intervals: two blocking events on the same thread
 * cannot overlap — a thread waits on one thing at a time — so summing is exact for a single
 * category. Across categories it is still a sum of disjoint stretches for the same reason. What it
 * is <em>not</em> is comparable to the span's inclusive duration, because a child span's waiting is
 * recorded on the child's own thread window and counted there; compare against self time instead.
 *
 * @param spanId      the span these events happened inside
 * @param category    what the thread was waiting on
 * @param totalNanos  how long, summed across every event of that category in the span's window
 * @param occurrences how many events that was, which separates one long stall from a thousand short
 *                    ones — the same total, very different problems
 */
public record TraceSpanContextRecord(
        long spanId,
        TraceContextCategory category,
        long totalNanos,
        long occurrences) {
}
