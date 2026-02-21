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

package pbouda.jeffrey.profile.heapdump.sanitizer;

/**
 * Represents the parsed HPROF file header.
 *
 * @param version    the version string (e.g. "JAVA PROFILE 1.0.2")
 * @param idSize     the size of object IDs in bytes (4 or 8)
 * @param timestamp  the heap dump timestamp in milliseconds
 * @param headerSize the total size of the header in bytes (version + null + 4 + 8)
 */
public record HprofHeader(String version, int idSize, long timestamp, int headerSize) {
}
