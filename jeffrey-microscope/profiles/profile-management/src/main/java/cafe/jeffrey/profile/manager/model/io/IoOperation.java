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
 * A single I/O operation (one of the slowest), from a socket/file read/write event.
 *
 * @param kind          human label ("Socket Read", "File Write", …)
 * @param target        host:port for sockets, file path for files
 * @param bytes         bytes transferred
 * @param durationNanos operation duration
 * @param thread        the thread that performed the operation
 */
public record IoOperation(String kind, String target, long bytes, long durationNanos, String thread) {
}
