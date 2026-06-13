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

package cafe.jeffrey.profile.manager.model.io;

/**
 * Headline I/O metrics for one {@link IoKind} (socket or file) — the stream is scoped to that kind.
 *
 * @param bytesRead     total bytes read
 * @param bytesWritten  total bytes written
 * @param opCount       number of read + write operations
 * @param slowestNanos  duration of the slowest single operation
 * @param slowestTarget target (host:port or path) of that slowest operation
 * @param hasEvents     whether any event of this kind is present
 */
public record IoOverview(
        long bytesRead,
        long bytesWritten,
        long opCount,
        long slowestNanos,
        String slowestTarget,
        boolean hasEvents) {
}
