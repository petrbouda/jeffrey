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

package pbouda.jeffrey.profile.heapdump.sanitizer;

/**
 * Maps HPROF basic type tags (2-11) to their byte sizes.
 * Type tags are used in primitive array dumps and instance field descriptors.
 */
public final class HprofTypeSize {

    public static final int OBJECT = 2;
    public static final int BOOLEAN = 4;
    public static final int CHAR = 5;
    public static final int FLOAT = 6;
    public static final int DOUBLE = 7;
    public static final int BYTE = 8;
    public static final int SHORT = 9;
    public static final int INT = 10;
    public static final int LONG = 11;

    private HprofTypeSize() {
    }

    /**
     * Returns the byte size of the given HPROF type tag.
     *
     * @param typeTag the HPROF type tag (2-11)
     * @param idSize  the size of object IDs in this heap dump (4 or 8)
     * @return the byte size, or -1 if the type tag is unknown
     */
    public static int sizeOf(int typeTag, int idSize) {
        return switch (typeTag) {
            case OBJECT -> idSize;
            case BOOLEAN -> 1;
            case CHAR -> 2;
            case FLOAT -> 4;
            case DOUBLE -> 8;
            case BYTE -> 1;
            case SHORT -> 2;
            case INT -> 4;
            case LONG -> 8;
            default -> -1;
        };
    }
}
