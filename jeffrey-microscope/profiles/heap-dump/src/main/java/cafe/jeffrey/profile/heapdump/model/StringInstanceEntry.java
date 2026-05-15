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
 * One row in the "Largest String Instances" report — a single {@code String}
 * object on the heap, ranked by its GC-retained size.
 *
 * <p>{@code retainedSize} is sharing-aware: it equals the String wrapper's
 * shallow size plus the backing {@code byte[]} shallow size <em>only when this
 * String is the sole referrer of that array</em>. Strings whose backing array
 * is shared with other Strings retain only their wrapper bytes — removing one
 * of them would not free the array.
 *
 * @param content          decoded and truncated string preview. Always non-null:
 *                         the analyzer re-decodes a bounded prefix from the
 *                         heap dump for Strings whose content exceeded the
 *                         indexer's cap, so the cost stays sub-kilobyte even
 *                         for multi-megabyte backing arrays. Empty only when
 *                         the underlying String can't be decoded at all.
 * @param instanceId       heap object id of the String instance
 * @param arrayShallowSize shallow size of the backing {@code byte[]} array
 * @param arrayRefCount    number of String instances that reference this
 *                         backing array (1 = unique, &gt; 1 = shared)
 * @param retainedSize     bytes the heap would shrink by if this single String
 *                         were removed
 */
public record StringInstanceEntry(
        String content,
        long instanceId,
        long arrayShallowSize,
        int arrayRefCount,
        long retainedSize
) {
}
