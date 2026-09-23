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
