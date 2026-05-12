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
package cafe.jeffrey.profile.heapdump.parser;

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
