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
 * A single async-profiler span instance reduced to what is needed to scope samples to it: the
 * identity hash of the thread the span ran on plus its absolute time window. Scoping a flamegraph
 * to a span tag means OR-ing the windows of all its spans — a sample belongs to the span only if it
 * was taken on {@code threadHash} between {@code fromEpochMillis} and {@code toEpochMillis}.
 * {@code thread_hash} is used (not the OS id) so the match works for virtual threads too.
 */
public record SpanInterval(long threadHash, long fromEpochMillis, long toEpochMillis) {

    public SpanInterval {
        if (toEpochMillis < fromEpochMillis) {
            throw new IllegalArgumentException(
                    "Span interval end before start: from=" + fromEpochMillis + " to=" + toEpochMillis);
        }
    }
}
