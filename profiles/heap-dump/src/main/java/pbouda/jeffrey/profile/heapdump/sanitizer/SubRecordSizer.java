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

import java.nio.ByteBuffer;

import static pbouda.jeffrey.profile.heapdump.sanitizer.HprofConstants.*;

/**
 * Computes the byte size of heap dump sub-records (those found inside
 * HEAP_DUMP_SEGMENT and HEAP_DUMP record bodies). Each sub-record starts
 * with a 1-byte sub-tag followed by type-specific data.
 *
 * <p>This class reads from a {@link ByteBuffer} and returns the total size
 * of the sub-record body (excluding the sub-tag byte itself). Returns -1
 * if the sub-record is unknown, truncated, or otherwise invalid.</p>
 */
final class SubRecordSizer {

    private SubRecordSizer() {
    }

    /**
     * Computes the size of a sub-record's body (after the sub-tag byte).
     *
     * @param subTag    the sub-record tag (already read from the stream)
     * @param idSize    the HPROF identifier size (4 or 8)
     * @param buffer    buffer positioned at the start of the sub-record body (after the sub-tag)
     * @param remaining number of bytes remaining in the segment from the current position
     * @return the number of bytes this sub-record body occupies, or -1 if unknown/invalid
     */
    static long computeSize(int subTag, int idSize, ByteBuffer buffer, long remaining) {
        return switch (subTag) {
            case SUB_GC_ROOT_UNKNOWN -> idSize;
            case SUB_GC_ROOT_JNI_GLOBAL -> 2L * idSize;
            case SUB_GC_ROOT_JNI_LOCAL -> idSize + 4 + 4;
            case SUB_GC_ROOT_JAVA_FRAME -> idSize + 4 + 4;
            case SUB_GC_ROOT_NATIVE_STACK -> idSize + 4;
            case SUB_GC_ROOT_STICKY_CLASS -> idSize;
            case SUB_GC_ROOT_THREAD_BLOCK -> idSize + 4;
            case SUB_GC_ROOT_MONITOR_USED -> idSize;
            case SUB_GC_ROOT_THREAD_OBJ -> idSize + 4 + 4;
            case SUB_GC_CLASS_DUMP -> computeClassDumpSize(idSize, buffer, remaining);
            case SUB_GC_INSTANCE_DUMP -> computeInstanceDumpSize(idSize, buffer, remaining);
            case SUB_GC_OBJ_ARRAY_DUMP -> computeObjArrayDumpSize(idSize, buffer, remaining);
            case SUB_GC_PRIM_ARRAY_DUMP -> computePrimArrayDumpSize(idSize, buffer, remaining);
            default -> -1;
        };
    }

    /**
     * CLASS_DUMP: variable-length due to constant pool, static fields, and instance field descriptors.
     *
     * Layout after sub-tag:
     *   [class obj ID] [stack trace serial:u4] [super class obj ID]
     *   [classloader obj ID] [signers obj ID] [protection domain obj ID]
     *   [reserved ID] [reserved ID]
     *   [instance size:u4]
     *   [constant pool count:u2]
     *     for each: [cp index:u2] [type:u1] [value:type-dependent]
     *   [static field count:u2]
     *     for each: [name ID] [type:u1] [value:type-dependent]
     *   [instance field count:u2]
     *     for each: [name ID] [type:u1]
     */
    private static long computeClassDumpSize(int idSize, ByteBuffer buffer, long remaining) {
        // Fixed part: classObjId + stackSerial + superClassId + classLoaderId + signersId + protDomainId
        //           + reserved1 + reserved2 + instanceSize
        //           = 7*idSize + 4 + 4
        long fixedSize = 7L * idSize + 4 + 4;
        if (remaining < fixedSize + 2) {
            return -1;
        }

        int pos = buffer.position();
        long offset = fixedSize;

        // Constant pool
        int cpCount = readU2(buffer, pos + (int) offset);
        offset += 2;

        for (int i = 0; i < cpCount; i++) {
            if (remaining < offset + 2 + 1) {
                return -1;
            }
            offset += 2; // cp index
            int type = readU1(buffer, pos + (int) offset);
            offset += 1;
            int tSize = HprofConstants.typeSize(type, idSize);
            if (tSize < 0 || remaining < offset + tSize) {
                return -1;
            }
            offset += tSize;
        }

        // Static fields
        if (remaining < offset + 2) {
            return -1;
        }
        int sfCount = readU2(buffer, pos + (int) offset);
        offset += 2;

        for (int i = 0; i < sfCount; i++) {
            if (remaining < offset + idSize + 1) {
                return -1;
            }
            offset += idSize; // field name ID
            int type = readU1(buffer, pos + (int) offset);
            offset += 1;
            int tSize = HprofConstants.typeSize(type, idSize);
            if (tSize < 0 || remaining < offset + tSize) {
                return -1;
            }
            offset += tSize;
        }

        // Instance fields
        if (remaining < offset + 2) {
            return -1;
        }
        int ifCount = readU2(buffer, pos + (int) offset);
        offset += 2;

        long instanceFieldsSize = (long) ifCount * (idSize + 1);
        if (remaining < offset + instanceFieldsSize) {
            return -1;
        }
        offset += instanceFieldsSize;

        return offset;
    }

    /**
     * INSTANCE_DUMP:
     *   [object ID] [stack trace serial:u4] [class obj ID]
     *   [bytes following:u4] [instance field values...]
     */
    private static long computeInstanceDumpSize(int idSize, ByteBuffer buffer, long remaining) {
        // Need at least: objId + stackSerial + classObjId + bytesFollowing
        long fixedSize = idSize + 4L + idSize + 4;
        if (remaining < fixedSize) {
            return -1;
        }

        int pos = buffer.position();
        // bytesFollowing is at offset: idSize + 4 + idSize
        long bytesFollowing = readU4AsLong(buffer, pos + idSize + 4 + idSize);
        long totalSize = fixedSize + bytesFollowing;

        if (remaining < totalSize) {
            return -1;
        }
        return totalSize;
    }

    /**
     * OBJ_ARRAY_DUMP:
     *   [array obj ID] [stack trace serial:u4]
     *   [number of elements:u4] [array class obj ID]
     *   [element IDs...] (each is idSize bytes)
     */
    private static long computeObjArrayDumpSize(int idSize, ByteBuffer buffer, long remaining) {
        // Need at least: objId + stackSerial + numElements + arrayClassId
        long fixedSize = idSize + 4L + 4 + idSize;
        if (remaining < fixedSize) {
            return -1;
        }

        int pos = buffer.position();
        long numElements = readU4AsLong(buffer, pos + idSize + 4);
        long totalSize = fixedSize + numElements * idSize;

        if (remaining < totalSize) {
            return -1;
        }
        return totalSize;
    }

    /**
     * PRIM_ARRAY_DUMP:
     *   [array obj ID] [stack trace serial:u4]
     *   [number of elements:u4] [element type:u1]
     *   [elements...] (each is typeSize bytes)
     */
    private static long computePrimArrayDumpSize(int idSize, ByteBuffer buffer, long remaining) {
        // Need at least: objId + stackSerial + numElements + elemType
        long fixedSize = idSize + 4L + 4 + 1;
        if (remaining < fixedSize) {
            return -1;
        }

        int pos = buffer.position();
        long numElements = readU4AsLong(buffer, pos + idSize + 4);
        int elemType = readU1(buffer, pos + idSize + 4 + 4);
        int elemSize = HprofConstants.typeSize(elemType, idSize);
        if (elemSize < 0) {
            return -1;
        }

        long totalSize = fixedSize + numElements * elemSize;
        if (remaining < totalSize) {
            return -1;
        }
        return totalSize;
    }

    private static int readU1(ByteBuffer buffer, int position) {
        return buffer.get(position) & 0xFF;
    }

    private static int readU2(ByteBuffer buffer, int position) {
        return ((buffer.get(position) & 0xFF) << 8)
                | (buffer.get(position + 1) & 0xFF);
    }

    /**
     * Reads a 4-byte unsigned value as a long to avoid sign issues.
     */
    private static long readU4AsLong(ByteBuffer buffer, int position) {
        return ((long) (buffer.get(position) & 0xFF) << 24)
                | ((long) (buffer.get(position + 1) & 0xFF) << 16)
                | ((long) (buffer.get(position + 2) & 0xFF) << 8)
                | ((long) (buffer.get(position + 3) & 0xFF));
    }
}
