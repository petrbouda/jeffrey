/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
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
package cafe.jeffrey.profile.heapdump.view;

/**
 * One row of the {@code instance} table.
 *
 * {@link Kind} ordinals match the {@code record_kind} TINYINT discriminator
 * stored in the index. Nullable fields:
 * <ul>
 *   <li>{@code classId} — null only for orphan instances (corrupt class ref)
 *       or primitive arrays, which don't have a class entry of their own.</li>
 *   <li>{@code arrayLength} — null for non-array instances.</li>
 *   <li>{@code primitiveType} — null unless {@code kind == PRIMITIVE_ARRAY}.</li>
 * </ul>
 */
public record InstanceRow(
        long instanceId,
        Long classId,
        long fileOffset,
        Kind kind,
        int shallowSize,
        Integer arrayLength,
        Integer primitiveType) {

    public InstanceRow {
        if (kind == null) {
            throw new IllegalArgumentException("kind must not be null");
        }
    }

    public enum Kind {
        INSTANCE, OBJECT_ARRAY, PRIMITIVE_ARRAY;

        public static Kind fromOrdinal(int ord) {
            return switch (ord) {
                case 0 -> INSTANCE;
                case 1 -> OBJECT_ARRAY;
                case 2 -> PRIMITIVE_ARRAY;
                default -> throw new IllegalArgumentException("Unknown record_kind ordinal: " + ord);
            };
        }
    }
}
