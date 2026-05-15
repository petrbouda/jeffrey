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
 * HPROF binary format tag constants.
 * <p>
 * Top-level tags appear after the file header and frame independent records
 * (each top-level record carries an 8-byte timestamp delta + 4-byte body length
 * after its tag byte).
 * <p>
 * Sub-record tags appear inside HEAP_DUMP and HEAP_DUMP_SEGMENT bodies.
 */
public final class HprofTag {

    private HprofTag() {
    }

    /**
     * Top-level record tags.
     */
    public static final class Top {
        public static final int STRING = 0x01;
        public static final int LOAD_CLASS = 0x02;
        public static final int UNLOAD_CLASS = 0x03;
        public static final int STACK_FRAME = 0x04;
        public static final int STACK_TRACE = 0x05;
        public static final int ALLOC_SITES = 0x06;
        public static final int HEAP_SUMMARY = 0x07;
        public static final int START_THREAD = 0x0A;
        public static final int END_THREAD = 0x0B;
        public static final int HEAP_DUMP = 0x0C;
        public static final int CPU_SAMPLES = 0x0D;
        public static final int CONTROL_SETTINGS = 0x0E;
        public static final int HEAP_DUMP_SEGMENT = 0x1C;
        public static final int HEAP_DUMP_END = 0x2C;

        private Top() {
        }
    }

    /**
     * Sub-record tags inside HEAP_DUMP / HEAP_DUMP_SEGMENT bodies.
     */
    public static final class Sub {
        public static final int ROOT_UNKNOWN = 0xFF;
        public static final int ROOT_JNI_GLOBAL = 0x01;
        public static final int ROOT_JNI_LOCAL = 0x02;
        public static final int ROOT_JAVA_FRAME = 0x03;
        public static final int ROOT_NATIVE_STACK = 0x04;
        public static final int ROOT_STICKY_CLASS = 0x05;
        public static final int ROOT_THREAD_BLOCK = 0x06;
        public static final int ROOT_MONITOR_USED = 0x07;
        public static final int ROOT_THREAD_OBJECT = 0x08;
        public static final int CLASS_DUMP = 0x20;
        public static final int INSTANCE_DUMP = 0x21;
        public static final int OBJECT_ARRAY_DUMP = 0x22;
        public static final int PRIMITIVE_ARRAY_DUMP = 0x23;
        public static final int PRIMITIVE_ARRAY_NODATA_DUMP = 0xC3;
        public static final int HEAP_DUMP_INFO = 0xFE;

        private Sub() {
        }

        /** Maps a raw HPROF root sub-tag byte to a stable display name. */
        public static String rootKindName(int rootKind) {
            return switch (rootKind) {
                case ROOT_UNKNOWN -> "Unknown";
                case ROOT_JNI_GLOBAL -> "JNI global";
                case ROOT_JNI_LOCAL -> "JNI local";
                case ROOT_JAVA_FRAME -> "Java frame";
                case ROOT_NATIVE_STACK -> "Native stack";
                case ROOT_STICKY_CLASS -> "Sticky class";
                case ROOT_THREAD_BLOCK -> "Thread block";
                case ROOT_MONITOR_USED -> "Monitor used";
                case ROOT_THREAD_OBJECT -> "Thread object";
                default -> "Other(0x" + Integer.toHexString(rootKind) + ")";
            };
        }
    }

    /**
     * HPROF basic types used in PRIMITIVE_ARRAY_DUMP and CLASS_DUMP field type bytes.
     */
    public static final class BasicType {
        public static final int OBJECT = 2;
        public static final int BOOLEAN = 4;
        public static final int CHAR = 5;
        public static final int FLOAT = 6;
        public static final int DOUBLE = 7;
        public static final int BYTE = 8;
        public static final int SHORT = 9;
        public static final int INT = 10;
        public static final int LONG = 11;

        /**
         * Size in bytes for a basic type. Object size depends on idSize and is handled separately.
         */
        public static int sizeOf(int basicType) {
            return switch (basicType) {
                case BOOLEAN, BYTE -> 1;
                case CHAR, SHORT -> 2;
                case FLOAT, INT -> 4;
                case DOUBLE, LONG -> 8;
                default -> throw new IllegalArgumentException("Unknown basic type: type=" + basicType);
            };
        }

        private BasicType() {
        }
    }
}
