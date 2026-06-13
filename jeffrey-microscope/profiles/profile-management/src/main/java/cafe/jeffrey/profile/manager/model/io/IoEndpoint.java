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
 * Aggregated I/O for one endpoint — a socket peer ({@code host:port}) or a file path.
 *
 * @param target       host:port or file path
 * @param opCount      number of operations against this endpoint
 * @param bytes        total bytes transferred (read + written)
 * @param totalNanos   summed duration
 * @param maxNanos     slowest single operation against this endpoint
 */
public record IoEndpoint(String target, long opCount, long bytes, long totalNanos, long maxNanos) {
}
