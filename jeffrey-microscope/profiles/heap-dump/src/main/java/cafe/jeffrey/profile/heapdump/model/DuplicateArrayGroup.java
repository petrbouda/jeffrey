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

import java.util.List;

/**
 * One group of byte-identical primitive arrays.
 *
 * @param typeName        array type name (e.g. {@code byte[]})
 * @param arrayLength     element count of every array in the group
 * @param count           number of byte-identical instances
 * @param shallowSize     shallow size of a single instance
 * @param wastedBytes     {@code (count - 1) * shallowSize} — reclaimable by sharing one copy
 * @param contentPreview  short human-readable preview of the shared content
 * @param sampleObjectIds a few instance ids for drill-down
 */
public record DuplicateArrayGroup(
        String typeName,
        int arrayLength,
        int count,
        long shallowSize,
        long wastedBytes,
        String contentPreview,
        List<Long> sampleObjectIds
) {
}
