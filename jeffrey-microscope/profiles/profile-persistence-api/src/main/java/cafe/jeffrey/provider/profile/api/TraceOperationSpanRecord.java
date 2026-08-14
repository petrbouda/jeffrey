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
 * Times are <em>inclusive</em>: a span contains its children, so the rows sum to more than the
 * operation's own duration. Self time would need the interval merge
 * {@code TraceManagerImpl.selfDurationOf} does per trace, which is not what a SQL aggregate can
 * answer cheaply; the reader is told which of the two they are looking at.
 *
 * @param name        the span name, as it appears in the waterfall
 * @param occurrences how many spans of this name the operation's traces contain in total
 * @param traceCount  how many of those traces contain at least one
 * @param totalNanos  summed duration across all of them
 * @param p50Nanos    median duration of a single occurrence
 * @param maxNanos    the slowest single occurrence
 */
public record TraceOperationSpanRecord(
        String name,
        long occurrences,
        long traceCount,
        long totalNanos,
        long p50Nanos,
        long maxNanos) {
}
