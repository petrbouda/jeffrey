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

package cafe.jeffrey.shared.common.model;

/**
 * An interval of work reduced to what is needed to scope samples to it: the identity hash of the
 * thread it ran on plus its absolute time window. A sample belongs to the interval only if it was
 * taken on {@code threadHash} between {@code fromEpochMillis} and {@code toEpochMillis}.
 * {@code thread_hash} is used (not the OS id) so the match works for virtual threads too.
 * <p>
 * Deliberately neutral about what produced the interval. Both span features feed it: an
 * async-profiler tag contributes one interval per {@code profiler.Span} it covers, and a trace span
 * contributes its own window, or that window minus its children's when scoped to self time. This is
 * the one thing the two share — the SQL predicate that turns a window into a sample filter.
 */
public record SpanInterval(long threadHash, long fromEpochMillis, long toEpochMillis) {

    public SpanInterval {
        if (toEpochMillis < fromEpochMillis) {
            throw new IllegalArgumentException(
                    "Span interval end before start: from=" + fromEpochMillis + " to=" + toEpochMillis);
        }
    }
}
