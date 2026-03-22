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
 * Top-level HPROF record tags as defined in the HPROF binary format specification.
 */
public enum HprofRecordTag {

    UTF8(0x01),
    LOAD_CLASS(0x02),
    UNLOAD_CLASS(0x03),
    FRAME(0x04),
    TRACE(0x05),
    ALLOC_SITES(0x06),
    HEAP_SUMMARY(0x07),
    START_THREAD(0x0A),
    END_THREAD(0x0B),
    HEAP_DUMP(0x0C),
    CPU_SAMPLES(0x0D),
    CONTROL_SETTINGS(0x0E),
    HEAP_DUMP_SEGMENT(0x1C),
    HEAP_DUMP_END(0x2C);

    private static final Map<Integer, HprofRecordTag> LOOKUP = new HashMap<>();

    static {
        for (HprofRecordTag tag : values()) {
            LOOKUP.put(tag.value, tag);
        }
    }

    private final int value;

    HprofRecordTag(int value) {
        this.value = value;
    }

    public int value() {
        return value;
    }

    /**
     * Returns true if this tag represents heap dump data (HEAP_DUMP or HEAP_DUMP_SEGMENT).
     */
    public boolean isHeapDumpData() {
        return this == HEAP_DUMP || this == HEAP_DUMP_SEGMENT;
    }

    /**
     * Looks up a tag by its byte value.
     *
     * @param value the byte value
     * @return the matching tag, or null if unknown
     */
    public static HprofRecordTag fromByte(int value) {
        return LOOKUP.get(value);
    }
}
