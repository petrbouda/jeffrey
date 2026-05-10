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
 * Returns the on-disk byte size of an HPROF basic type value.
 *
 * The OBJECT type has a variable size that depends on the file's {@code idSize}
 * (4 or 8); all other types are fixed.
 */
public final class HprofTypeSize {

    private HprofTypeSize() {
    }

    /** Returns the size in bytes, or -1 if the type byte is not a recognised HPROF basic type. */
    public static int sizeOf(int basicType, int idSize) {
        return switch (basicType) {
            case HprofTag.BasicType.OBJECT -> idSize;
            case HprofTag.BasicType.BOOLEAN, HprofTag.BasicType.BYTE -> 1;
            case HprofTag.BasicType.CHAR, HprofTag.BasicType.SHORT -> 2;
            case HprofTag.BasicType.FLOAT, HprofTag.BasicType.INT -> 4;
            case HprofTag.BasicType.DOUBLE, HprofTag.BasicType.LONG -> 8;
            default -> -1;
        };
    }
}
