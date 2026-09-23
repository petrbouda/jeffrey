/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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

}
