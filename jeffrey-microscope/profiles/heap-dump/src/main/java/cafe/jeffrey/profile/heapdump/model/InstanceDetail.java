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

import java.util.List;

/**
 * Detailed information about a heap instance including all its fields.
 *
 * @param objectId     the unique identifier of the instance in the heap
 * @param className    the fully qualified class name of the instance
 * @param value        formatted string representation of the instance value
 * @param stringValue  raw string value for String instances or decoded byte[] (null if not applicable)
 * @param displayValue human-readable value for common types like String (null if not applicable)
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
        String displayValue,
        long shallowSize,
        Long retainedSize,
        List<InstanceField> fields,
        List<InstanceField> staticFields
) {
}
