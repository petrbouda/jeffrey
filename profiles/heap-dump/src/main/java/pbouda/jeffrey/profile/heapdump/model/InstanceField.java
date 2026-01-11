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

/**
 * Represents a field of a heap instance with its name, type, and value.
 *
 * @param name               the name of the field
 * @param type               the type of the field (e.g., "int", "java.lang.String")
 * @param value              the formatted string representation of the field value
 * @param isPrimitive        true if this is a primitive type field
 * @param referencedObjectId the object ID of the referenced instance (null for primitives or null references)
 */
public record InstanceField(
        String name,
        String type,
        String value,
        boolean isPrimitive,
        Long referencedObjectId
) {
    /**
     * Create a field for a primitive value.
     */
    public static InstanceField primitive(String name, String type, String value) {
        return new InstanceField(name, type, value, true, null);
    }

    /**
     * Create a field for an object reference.
     */
    public static InstanceField reference(String name, String type, String value, Long referencedObjectId) {
        return new InstanceField(name, type, value, false, referencedObjectId);
    }

    /**
     * Create a field for a null reference.
     */
    public static InstanceField nullReference(String name, String type) {
        return new InstanceField(name, type, "null", false, null);
    }
}
