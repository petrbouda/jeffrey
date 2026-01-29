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

import java.util.Map;

/**
 * Represents a single object instance ranked by retained size.
 *
 * @param objectId     unique object identifier in the heap
 * @param className    fully qualified class name
 * @param shallowSize  size of this object alone in bytes
 * @param retainedSize total size of objects retained exclusively by this object
 * @param objectParams structured key/value pairs describing the object
 */
public record BiggestObjectEntry(
        long objectId,
        String className,
        long shallowSize,
        long retainedSize,
        Map<String, String> objectParams
) {
}
