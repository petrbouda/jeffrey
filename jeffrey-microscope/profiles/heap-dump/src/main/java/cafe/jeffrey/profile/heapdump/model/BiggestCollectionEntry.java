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
 * A single entry representing one of the biggest individual collections in the heap.
 *
 * @param objectId       object ID of the collection instance in the heap dump
 * @param className      fully qualified class name of the collection (e.g., java.util.HashMap)
 * @param elementCount   number of elements stored in the collection
 * @param capacity       total capacity of the collection's backing array
 * @param fillRatio      ratio of elementCount to capacity (0.0 to 1.0)
 * @param shallowSize    shallow size of the collection instance in bytes
 * @param retainedSize   retained size of the collection instance in bytes
 * @param ownerClassName fully qualified class name of the object that references this collection, or null
 */
public record BiggestCollectionEntry(
        long objectId,
        String className,
        int elementCount,
        int capacity,
        double fillRatio,
        long shallowSize,
        long retainedSize,
        String ownerClassName
) {
}
