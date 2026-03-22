/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
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

package pbouda.jeffrey.profile.heapdump.model;

/**
 * Represents a single entry in string deduplication analysis.
 * Used for both already-deduplicated strings and deduplication opportunities.
 *
 * @param content   the string content (truncated if very long)
 * @param count     number of String instances with this content/array
 * @param arraySize size of the backing byte[] array in bytes
 * @param savings   bytes saved (for deduplicated) or could be saved (for opportunities)
 */
public record StringDeduplicationEntry(
        String content,
        int count,
        long arraySize,
        long savings
) {
}
