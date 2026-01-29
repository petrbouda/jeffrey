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
 * A single step in a reference chain from a GC root to a target object.
 *
 * @param objectId     unique object identifier
 * @param className    fully qualified class name
 * @param fieldName    field name (or array index) pointing to the next object in the chain
 * @param shallowSize  shallow size of this object in bytes
 * @param objectParams structured key/value pairs describing the object
 * @param isTarget     whether this is the target object being investigated
 */
public record PathStep(
        long objectId,
        String className,
        String fieldName,
        long shallowSize,
        Map<String, String> objectParams,
        boolean isTarget
) {
}
