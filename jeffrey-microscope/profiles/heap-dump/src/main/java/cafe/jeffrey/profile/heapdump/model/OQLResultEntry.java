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

package cafe.jeffrey.profile.heapdump.model;

/**
 * A single result entry from an OQL query.
 *
 * @param objectId     the heap-object ID for the row, or {@code null} when the
 *                     row doesn't correspond to a single instance (e.g. a
 *                     non-`instance` table like `string`, or an aggregate
 *                     result without an identifying column). Used by the
 *                     frontend to gate the per-row action buttons — `null`
 *                     means "no instance to inspect".
 * @param className    the class name of the object
 * @param value        string representation of the result
 * @param size         shallow size of the object in bytes (0 if not applicable)
 * @param retainedSize retained heap size in bytes (null if not calculated)
 */
public record OQLResultEntry(
        Long objectId,
        String className,
        String value,
        long size,
        Long retainedSize
) {
    /**
     * Create an entry for a non-Instance result.
     */
    public static OQLResultEntry ofValue(String value) {
        return new OQLResultEntry(null, null, value, 0, null);
    }

    /**
     * Create an entry for an Instance result without retained size.
     */
    public static OQLResultEntry ofInstance(long objectId, String className, String value, long size) {
        return new OQLResultEntry(objectId, className, value, size, null);
    }

    /**
     * Create an entry for an Instance result with retained size.
     */
    public static OQLResultEntry ofInstanceWithRetained(long objectId, String className, String value, long size, long retainedSize) {
        return new OQLResultEntry(objectId, className, value, size, retainedSize);
    }

    /**
     * Create an entry for a JavaClass result.
     */
    public static OQLResultEntry ofJavaClass(long classId, String className, String value, long totalSize) {
        return new OQLResultEntry(classId, className, value, totalSize, null);
    }
}
