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

import java.util.List;

/**
 * Detailed information about a heap instance including all its fields.
 *
 * @param objectId     the unique identifier of the instance in the heap
 * @param className    the fully qualified class name of the instance
 * @param value        formatted string representation of the instance value
 * @param stringValue  raw string value for String instances or decoded byte[] (null if not applicable)
 * @param shallowSize  shallow size of the instance in bytes
 * @param retainedSize retained heap size in bytes (null if not calculated)
 * @param fields       list of instance fields
 * @param staticFields list of static fields from the class
 */
public record InstanceDetail(
        long objectId,
        String className,
        String value,
        String stringValue,
        long shallowSize,
        Long retainedSize,
        List<InstanceField> fields,
        List<InstanceField> staticFields
) {
}
