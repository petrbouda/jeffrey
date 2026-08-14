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
 * One span name in an operation's breakdown. Times are inclusive: a parent contains its children,
 * so these rows sum past the operation's own duration.
 *
 * @param name        span name, as the waterfall shows it
 * @param occurrences how many spans of this name the operation's traces contain
 * @param traceCount  how many traces contain at least one
 * @param totalNanos  summed duration across all of them
 * @param p50Nanos    median duration of one occurrence
 * @param maxNanos    the slowest single occurrence
 */
public record TraceOperationSpanRow(
        String name,
        long occurrences,
        long traceCount,
        long totalNanos,
        long p50Nanos,
        long maxNanos) {
}
