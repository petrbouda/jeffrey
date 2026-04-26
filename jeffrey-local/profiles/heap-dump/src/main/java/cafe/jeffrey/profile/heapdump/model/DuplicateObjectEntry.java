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

package cafe.jeffrey.profile.heapdump.model;

/**
 * Represents a single group of duplicate objects found in the heap.
 *
 * @param className       the fully qualified class name of the duplicated object
 * @param contentPreview  a short preview of the content (e.g., first bytes as hex, or boxed value)
 * @param duplicateCount  number of instances with identical content
 * @param individualSize  size of a single instance in bytes
 * @param totalWastedBytes bytes wasted by duplicates (individualSize * (duplicateCount - 1))
 */
public record DuplicateObjectEntry(
        String className,
        String contentPreview,
        int duplicateCount,
        long individualSize,
        long totalWastedBytes
) {
}
