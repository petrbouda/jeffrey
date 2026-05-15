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
 * One row in the "Top Strings by Retained Size" report — strings grouped by
 * decoded content, ranked by how much heap they occupy in total.
 *
 * <p>{@code retainedSize} sums the shallow size of every {@code String} wrapper
 * with this content plus the shallow size of each distinct backing {@code byte[]}
 * array, so it reflects what the heap would shrink by if every instance of this
 * content were removed.
 *
 * @param content          the decoded string content (truncated if very long)
 * @param count            number of String instances with this content
 * @param arrayShallowSize shallow size of one backing {@code byte[]} array, in bytes
 *                         (constant across all arrays for the same content)
 * @param retainedSize     total bytes occupied: sum of String shallow sizes +
 *                         (distinct backing arrays) × arrayShallowSize
 */
public record StringTopEntry(
        String content,
        int count,
        long arrayShallowSize,
        long retainedSize
) {
}
