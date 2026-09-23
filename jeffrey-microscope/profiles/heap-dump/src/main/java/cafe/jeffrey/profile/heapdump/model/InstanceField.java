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
 * Represents a field of a heap instance with its name, type, and value.
 *
 * @param name                the name of the field
 * @param type                the type of the field (e.g., "int", "java.lang.String")
 * @param value               the formatted string representation of the field value
 * @param isPrimitive         true if this is a primitive type field
 * @param referencedObjectId  the object ID of the referenced instance (null for primitives or null references)
 * @param referencedClassName the actual runtime class name of the referenced instance (null for primitives or null references)
 */
public record InstanceField(
        String name,
        String type,
        String value,
        boolean isPrimitive,
        Long referencedObjectId,
        String referencedClassName
) {
    /**
     * Create a field for a primitive value.
     */
    public static InstanceField primitive(String name, String type, String value) {
        return new InstanceField(name, type, value, true, null, null);
    }

    /**
     * Create a field for an object reference.
     */
    public static InstanceField reference(String name, String type, String value,
                                          Long referencedObjectId, String referencedClassName) {
        return new InstanceField(name, type, value, false, referencedObjectId, referencedClassName);
    }

}
