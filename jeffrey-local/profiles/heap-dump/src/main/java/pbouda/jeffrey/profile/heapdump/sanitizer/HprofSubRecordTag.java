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

import java.util.HashMap;
import java.util.Map;

/**
 * Sub-record tags within HEAP_DUMP and HEAP_DUMP_SEGMENT records.
 */
public enum HprofSubRecordTag {

    ROOT_UNKNOWN(0xFF),
    ROOT_JNI_GLOBAL(0x01),
    ROOT_JNI_LOCAL(0x02),
    ROOT_JAVA_FRAME(0x03),
    ROOT_NATIVE_STACK(0x04),
    ROOT_STICKY_CLASS(0x05),
    ROOT_THREAD_BLOCK(0x06),
    ROOT_MONITOR_USED(0x07),
    ROOT_THREAD_OBJ(0x08),
    CLASS_DUMP(0x20),
    INSTANCE_DUMP(0x21),
    OBJ_ARRAY_DUMP(0x22),
    PRIM_ARRAY_DUMP(0x23);

    private static final Map<Integer, HprofSubRecordTag> LOOKUP = new HashMap<>();

    static {
        for (HprofSubRecordTag tag : values()) {
            LOOKUP.put(tag.value, tag);
        }
    }

    private final int value;

    HprofSubRecordTag(int value) {
        this.value = value;
    }

    public int value() {
        return value;
    }

    /**
     * Looks up a sub-record tag by its byte value.
     *
     * @param value the byte value
     * @return the matching tag, or null if unknown
     */
    public static HprofSubRecordTag fromByte(int value) {
        return LOOKUP.get(value);
    }
}
