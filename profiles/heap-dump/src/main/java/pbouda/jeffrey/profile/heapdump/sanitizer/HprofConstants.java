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

import java.util.Set;

/**
 * Constants for the HPROF binary file format, including top-level record tags,
 * heap dump sub-record tags, and Java type sizes.
 */
final class HprofConstants {

    private HprofConstants() {
    }

    // --- HPROF file structure ---

    /** Size of the top-level record header: tag (1) + timestamp (4) + length (4) */
    static final int RECORD_HEADER_SIZE = 9;

    /** Minimum valid HPROF header size: version string (18) + null (1) + id size (4) + timestamp (8) = 31 */
    static final int MIN_HEADER_SIZE = 19 + 4 + 8; // "JAVA PROFILE 1.0.2\0" (19 bytes) + idSize + timestamp

    /** HPROF version 1.0.1 */
    static final String VERSION_101 = "JAVA PROFILE 1.0.1";

    /** HPROF version 1.0.2 */
    static final String VERSION_102 = "JAVA PROFILE 1.0.2";

    /** Gzip magic bytes */
    static final byte GZIP_MAGIC_1 = 0x1F;
    static final byte GZIP_MAGIC_2 = (byte) 0x8B;

    // --- Top-level record tags ---

    static final int TAG_UTF8 = 0x01;
    static final int TAG_LOAD_CLASS = 0x02;
    static final int TAG_UNLOAD_CLASS = 0x03;
    static final int TAG_FRAME = 0x04;
    static final int TAG_TRACE = 0x05;
    static final int TAG_ALLOC_SITES = 0x06;
    static final int TAG_HEAP_SUMMARY = 0x07;
    static final int TAG_START_THREAD = 0x0A;
    static final int TAG_END_THREAD = 0x0B;
    static final int TAG_HEAP_DUMP = 0x0C;
    static final int TAG_CPU_SAMPLES = 0x0D;
    static final int TAG_CONTROL_SETTINGS = 0x0E;
    static final int TAG_HEAP_DUMP_SEGMENT = 0x1C;
    static final int TAG_HEAP_DUMP_END = 0x2C;

    /** All known top-level tags */
    static final Set<Integer> KNOWN_TAGS = Set.of(
            TAG_UTF8, TAG_LOAD_CLASS, TAG_UNLOAD_CLASS,
            TAG_FRAME, TAG_TRACE, TAG_ALLOC_SITES,
            TAG_HEAP_SUMMARY, TAG_START_THREAD, TAG_END_THREAD,
            TAG_HEAP_DUMP, TAG_CPU_SAMPLES, TAG_CONTROL_SETTINGS,
            TAG_HEAP_DUMP_SEGMENT, TAG_HEAP_DUMP_END
    );

    // --- Heap dump sub-record tags (inside 0x1C / 0x0C bodies) ---

    static final int SUB_GC_ROOT_UNKNOWN = 0xFF;
    static final int SUB_GC_ROOT_JNI_GLOBAL = 0x01;
    static final int SUB_GC_ROOT_JNI_LOCAL = 0x02;
    static final int SUB_GC_ROOT_JAVA_FRAME = 0x03;
    static final int SUB_GC_ROOT_NATIVE_STACK = 0x04;
    static final int SUB_GC_ROOT_STICKY_CLASS = 0x05;
    static final int SUB_GC_ROOT_THREAD_BLOCK = 0x06;
    static final int SUB_GC_ROOT_MONITOR_USED = 0x07;
    static final int SUB_GC_ROOT_THREAD_OBJ = 0x08;
    static final int SUB_GC_CLASS_DUMP = 0x20;
    static final int SUB_GC_INSTANCE_DUMP = 0x21;
    static final int SUB_GC_OBJ_ARRAY_DUMP = 0x22;
    static final int SUB_GC_PRIM_ARRAY_DUMP = 0x23;

    // --- Java type tags (for constant pool, static fields, primitive arrays) ---

    static final int TYPE_OBJECT = 2;
    static final int TYPE_BOOLEAN = 4;
    static final int TYPE_CHAR = 5;
    static final int TYPE_FLOAT = 6;
    static final int TYPE_DOUBLE = 7;
    static final int TYPE_BYTE = 8;
    static final int TYPE_SHORT = 9;
    static final int TYPE_INT = 10;
    static final int TYPE_LONG = 11;

    /**
     * Returns the byte size of a Java type tag value.
     *
     * @param type   the Java type tag (2=object, 4=boolean, ..., 11=long)
     * @param idSize the identifier size from the HPROF header (4 or 8)
     * @return the size in bytes, or -1 if the type is unknown
     */
    static int typeSize(int type, int idSize) {
        return switch (type) {
            case TYPE_OBJECT -> idSize;
            case TYPE_BOOLEAN, TYPE_BYTE -> 1;
            case TYPE_CHAR, TYPE_SHORT -> 2;
            case TYPE_FLOAT, TYPE_INT -> 4;
            case TYPE_DOUBLE, TYPE_LONG -> 8;
            default -> -1;
        };
    }

    /**
     * Returns a human-readable name for a top-level record tag.
     */
    static String tagName(int tag) {
        return switch (tag) {
            case TAG_UTF8 -> "UTF8";
            case TAG_LOAD_CLASS -> "LOAD_CLASS";
            case TAG_UNLOAD_CLASS -> "UNLOAD_CLASS";
            case TAG_FRAME -> "FRAME";
            case TAG_TRACE -> "TRACE";
            case TAG_ALLOC_SITES -> "ALLOC_SITES";
            case TAG_HEAP_SUMMARY -> "HEAP_SUMMARY";
            case TAG_START_THREAD -> "START_THREAD";
            case TAG_END_THREAD -> "END_THREAD";
            case TAG_HEAP_DUMP -> "HEAP_DUMP";
            case TAG_CPU_SAMPLES -> "CPU_SAMPLES";
            case TAG_CONTROL_SETTINGS -> "CONTROL_SETTINGS";
            case TAG_HEAP_DUMP_SEGMENT -> "HEAP_DUMP_SEGMENT";
            case TAG_HEAP_DUMP_END -> "HEAP_DUMP_END";
            default -> "UNKNOWN(0x" + Integer.toHexString(tag) + ")";
        };
    }
}
