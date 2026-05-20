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
 * Streams sub-records out of a HEAP_DUMP or HEAP_DUMP_SEGMENT region.
 *
 * Each sub-record begins with a 1-byte tag followed by a tag-specific body.
 * Body sizes for variable-length records (CLASS_DUMP, INSTANCE_DUMP, OBJECT_ARRAY_DUMP,
 * PRIMITIVE_ARRAY_DUMP) are computed from prefix length fields; the rest are fixed
 * at compile time relative to {@code idSize}.
 *
 * On encountering an unknown tag or a truncated body, the reader emits a warning
 * and stops scanning the current region (subsequent sub-records cannot be located
 * without a known frame size). The top-level scan resumes at the next region.
 *
 * <p>Dispatch is visitor-based with primitive arguments: each sub-record kind
 * calls a typed {@code on*} method directly with the field values pulled from
 * the mapped file. This means the hot {@code INSTANCE_DUMP} /
 * {@code OBJECT_ARRAY_DUMP} / {@code PRIMITIVE_ARRAY_DUMP} paths never
 * allocate a record object — Pass B of a 7.6 M-instance dump previously paid
 * one {@code HprofRecord.InstanceDump} per instance just to switch over it.
 * {@link HprofRecord.ClassDump} stays as a record on the visitor signature
 * because it carries 12 fields and only fires ~22 K times per dump.
 */
public final class HprofSubRecordReader {

    /**
     * Visitor over sub-records. Each method has primitive arguments so the
     * reader can fan out per-sub-record state without materialising a record
     * object on the hot path. Default no-op implementations let callers
     * override only the sub-record kinds they care about (Pass A overrides
     * only {@link #onClassDump}).
     */
    public interface Visitor {

        default void onGcRoot(int rootKind, long instanceId, int threadSerial, int frameIndex, long fileOffset) {
        }

        default void onClassDump(HprofRecord.ClassDump record) {
        }

        default void onInstanceDump(
                long instanceId, int traceSerial, long classId,
                int instanceFieldsByteLength, long fileOffset) {
        }

        default void onObjectArrayDump(
                long arrayId, int traceSerial, int length, long arrayClassId, long fileOffset) {
        }

        default void onPrimitiveArrayDump(
                long arrayId, int traceSerial, int length, int elementType, long fileOffset) {
        }

        default void onOpaqueSub(int tag, long bodyOffset, long bodySize) {
        }

        default void onWarning(ParseWarning warning) {
        }
    }

    private HprofSubRecordReader() {
    }

    /**
     * Iterates sub-records contained in the given region.
     *
     * @param file        the underlying mapped file
     * @param regionStart absolute offset of the region's first byte
     * @param regionLength byte length of the region
     * @param visitor     receives decoded records and warnings
     */
    public static void read(HprofMappedFile file, long regionStart, long regionLength, Visitor visitor) {
        if (file == null) {
            throw new IllegalArgumentException("file must not be null");
        }
        if (visitor == null) {
            throw new IllegalArgumentException("visitor must not be null");
        }
        if (regionLength < 0) {
            throw new IllegalArgumentException("regionLength must be non-negative: regionLength=" + regionLength);
        }

        int idSize = file.header().idSize();
        long end = regionStart + regionLength;
        long pos = regionStart;

        while (pos < end) {
            int tag = Byte.toUnsignedInt(file.readByte(pos));
            long bodyOffset = pos + 1;

            long bodySize;
            try {
                bodySize = bodySizeFor(file, tag, bodyOffset, end, idSize);
            } catch (RuntimeException e) {
                visitor.onWarning(new ParseWarning(
                        pos, tag, ParseWarning.Severity.ERROR,
                        "Failed to compute sub-record size: tag=" + tag + " error=" + e.getMessage()));
                return;
            }

            if (bodySize < 0) {
                visitor.onWarning(new ParseWarning(
                        pos, tag, ParseWarning.Severity.ERROR,
                        "Unknown or truncated sub-record, abandoning region: tag=" + tag));
                return;
            }
            if (bodyOffset + bodySize > end) {
                visitor.onWarning(new ParseWarning(
                        pos, tag, ParseWarning.Severity.ERROR,
                        "Sub-record body exceeds region: tag=" + tag
                                + " bodySize=" + bodySize
                                + " bytesRemaining=" + (end - bodyOffset)));
                return;
            }

            try {
                dispatch(file, tag, bodyOffset, bodySize, idSize, visitor);
            } catch (RuntimeException e) {
                visitor.onWarning(new ParseWarning(
                        pos, tag, ParseWarning.Severity.ERROR,
                        "Failed to decode sub-record: tag=" + tag + " error=" + e.getMessage()));
                return;
            }

            pos = bodyOffset + bodySize;
        }
    }

    private static void dispatch(
            HprofMappedFile file, int tag, long bodyOffset, long bodySize, int idSize, Visitor visitor) {
        switch (tag) {
            case HprofTag.Sub.ROOT_UNKNOWN -> emitGcRoot(file, tag, bodyOffset, idSize, false, false, visitor);
            case HprofTag.Sub.ROOT_JNI_GLOBAL -> emitGcRoot(file, tag, bodyOffset, idSize, false, false, visitor);
            case HprofTag.Sub.ROOT_JNI_LOCAL -> emitGcRoot(file, tag, bodyOffset, idSize, true, true, visitor);
            case HprofTag.Sub.ROOT_JAVA_FRAME -> emitGcRoot(file, tag, bodyOffset, idSize, true, true, visitor);
            case HprofTag.Sub.ROOT_NATIVE_STACK -> emitGcRoot(file, tag, bodyOffset, idSize, true, false, visitor);
            case HprofTag.Sub.ROOT_STICKY_CLASS -> emitGcRoot(file, tag, bodyOffset, idSize, false, false, visitor);
            case HprofTag.Sub.ROOT_THREAD_BLOCK -> emitGcRoot(file, tag, bodyOffset, idSize, true, false, visitor);
            case HprofTag.Sub.ROOT_MONITOR_USED -> emitGcRoot(file, tag, bodyOffset, idSize, false, false, visitor);
            case HprofTag.Sub.ROOT_THREAD_OBJECT -> emitGcRoot(file, tag, bodyOffset, idSize, true, false, visitor);
            case HprofTag.Sub.CLASS_DUMP -> emitClassDump(file, bodyOffset, bodySize, idSize, visitor);
            case HprofTag.Sub.INSTANCE_DUMP -> emitInstanceDump(file, bodyOffset, idSize, visitor);
            case HprofTag.Sub.OBJECT_ARRAY_DUMP -> emitObjectArrayDump(file, bodyOffset, idSize, visitor);
            case HprofTag.Sub.PRIMITIVE_ARRAY_DUMP -> emitPrimitiveArrayDump(file, bodyOffset, idSize, visitor);
            default -> visitor.onOpaqueSub(tag, bodyOffset, bodySize);
        }
    }

    private static void emitGcRoot(
            HprofMappedFile file, int tag, long bodyOffset, int idSize,
            boolean hasThreadSerial, boolean hasFrameIndex, Visitor visitor) {
        long instanceId = file.readId(bodyOffset);
        long cursor = bodyOffset + idSize;
        // ROOT_JNI_GLOBAL also has a trailing JNI ref id; ROOT_THREAD_OBJECT has thread serial + stack-trace serial.
        // We capture only the data needed by the index: the rooted instance and (when present) thread / frame.
        int threadSerial = -1;
        int frameIndex = -1;
        if (hasThreadSerial) {
            threadSerial = file.readInt(cursor);
            cursor += 4;
        }
        if (hasFrameIndex) {
            frameIndex = file.readInt(cursor);
        }
        visitor.onGcRoot(tag, instanceId, threadSerial, frameIndex, bodyOffset);
    }

    private static void emitClassDump(
            HprofMappedFile file, long bodyOffset, long bodySize, int idSize, Visitor visitor) {
        long classId = file.readId(bodyOffset);
        int traceSerial = file.readInt(bodyOffset + idSize);
        long superClassId = file.readId(bodyOffset + idSize + 4);
        long classloaderId = file.readId(bodyOffset + 2L * idSize + 4);
        long signersId = file.readId(bodyOffset + 3L * idSize + 4);
        long protectionDomainId = file.readId(bodyOffset + 4L * idSize + 4);
        // Two reserved id slots follow.
        int instanceSize = file.readInt(bodyOffset + 7L * idSize + 4);

        // Walk the variable-length suffix to compute instance-fields byte length.
        long cursor = bodyOffset + 7L * idSize + 8;

        // Constant pool
        int constPoolCount = Short.toUnsignedInt(file.readShort(cursor));
        cursor += 2;
        for (int i = 0; i < constPoolCount; i++) {
            cursor += 2; // u2 index
            int type = Byte.toUnsignedInt(file.readByte(cursor));
            cursor += 1;
            int sz = HprofTypeSize.sizeOf(type, idSize);
            if (sz < 0) {
                throw new IllegalStateException("Unknown const-pool type: type=" + type);
            }
            cursor += sz;
        }

        // Static fields
        int staticFieldCount = Short.toUnsignedInt(file.readShort(cursor));
        cursor += 2;
        for (int i = 0; i < staticFieldCount; i++) {
            cursor += idSize; // id nameId
            int type = Byte.toUnsignedInt(file.readByte(cursor));
            cursor += 1;
            int sz = HprofTypeSize.sizeOf(type, idSize);
            if (sz < 0) {
                throw new IllegalStateException("Unknown static-field type: type=" + type);
            }
            cursor += sz;
        }

        // Instance fields — capture name id + type byte for each so the index
        // builder can decode INSTANCE_DUMP field bytes for ref extraction and
        // typed value reads.
        int instanceFieldCount = Short.toUnsignedInt(file.readShort(cursor));
        cursor += 2;
        int instanceFieldsByteLength = 0;
        long[] instanceFieldNameIds = new long[instanceFieldCount];
        int[] instanceFieldTypes = new int[instanceFieldCount];
        for (int i = 0; i < instanceFieldCount; i++) {
            instanceFieldNameIds[i] = file.readId(cursor);
            cursor += idSize;
            int type = Byte.toUnsignedInt(file.readByte(cursor));
            cursor += 1;
            int sz = HprofTypeSize.sizeOf(type, idSize);
            if (sz < 0) {
                throw new IllegalStateException("Unknown instance-field type: type=" + type);
            }
            instanceFieldTypes[i] = type;
            instanceFieldsByteLength += sz;
        }

        int totalByteLength = (int) Math.min(bodySize, Integer.MAX_VALUE);
        // ClassDump fires ~22 K times per dump (vs millions of instance dumps)
        // so the record allocation here is not the hot allocator; the 12-field
        // signature also makes a record cleaner than a 12-arg visitor method.
        visitor.onClassDump(new HprofRecord.ClassDump(
                classId, traceSerial, superClassId, classloaderId,
                signersId, protectionDomainId, instanceSize,
                instanceFieldsByteLength, totalByteLength,
                instanceFieldNameIds, instanceFieldTypes, bodyOffset));
    }

    private static void emitInstanceDump(
            HprofMappedFile file, long bodyOffset, int idSize, Visitor visitor) {
        long instanceId = file.readId(bodyOffset);
        int traceSerial = file.readInt(bodyOffset + idSize);
        long classId = file.readId(bodyOffset + idSize + 4);
        int instanceFieldsByteLength = file.readInt(bodyOffset + 2L * idSize + 4);
        visitor.onInstanceDump(instanceId, traceSerial, classId, instanceFieldsByteLength, bodyOffset);
    }

    private static void emitObjectArrayDump(
            HprofMappedFile file, long bodyOffset, int idSize, Visitor visitor) {
        long arrayId = file.readId(bodyOffset);
        int traceSerial = file.readInt(bodyOffset + idSize);
        int length = file.readInt(bodyOffset + idSize + 4);
        long arrayClassId = file.readId(bodyOffset + idSize + 8);
        visitor.onObjectArrayDump(arrayId, traceSerial, length, arrayClassId, bodyOffset);
    }

    private static void emitPrimitiveArrayDump(
            HprofMappedFile file, long bodyOffset, int idSize, Visitor visitor) {
        long arrayId = file.readId(bodyOffset);
        int traceSerial = file.readInt(bodyOffset + idSize);
        int length = file.readInt(bodyOffset + idSize + 4);
        int elementType = Byte.toUnsignedInt(file.readByte(bodyOffset + idSize + 8));
        visitor.onPrimitiveArrayDump(arrayId, traceSerial, length, elementType, bodyOffset);
    }

    /**
     * Returns the body size for a sub-record at {@code bodyOffset}, or -1 if the
     * tag is unknown or the body extends past {@code end}.
     */
    private static long bodySizeFor(HprofMappedFile file, int tag, long bodyOffset, long end, int idSize) {
        return switch (tag) {
            case HprofTag.Sub.ROOT_UNKNOWN -> idSize;
            case HprofTag.Sub.ROOT_JNI_GLOBAL -> 2L * idSize;
            case HprofTag.Sub.ROOT_JNI_LOCAL -> idSize + 8L;
            case HprofTag.Sub.ROOT_JAVA_FRAME -> idSize + 8L;
            case HprofTag.Sub.ROOT_NATIVE_STACK -> idSize + 4L;
            case HprofTag.Sub.ROOT_STICKY_CLASS -> idSize;
            case HprofTag.Sub.ROOT_THREAD_BLOCK -> idSize + 4L;
            case HprofTag.Sub.ROOT_MONITOR_USED -> idSize;
            case HprofTag.Sub.ROOT_THREAD_OBJECT -> idSize + 8L;
            case HprofTag.Sub.CLASS_DUMP -> classDumpBodySize(file, bodyOffset, end, idSize);
            case HprofTag.Sub.INSTANCE_DUMP -> instanceDumpBodySize(file, bodyOffset, end, idSize);
            case HprofTag.Sub.OBJECT_ARRAY_DUMP -> objectArrayBodySize(file, bodyOffset, end, idSize);
            case HprofTag.Sub.PRIMITIVE_ARRAY_DUMP -> primitiveArrayBodySize(file, bodyOffset, end, idSize);
            default -> -1L;
        };
    }

    private static long classDumpBodySize(HprofMappedFile file, long bodyOffset, long end, int idSize) {
        long fixed = 7L * idSize + 8;
        long cursor = bodyOffset + fixed;
        if (cursor + 2 > end) {
            return -1;
        }

        int constPoolCount = Short.toUnsignedInt(file.readShort(cursor));
        cursor += 2;
        for (int i = 0; i < constPoolCount; i++) {
            if (cursor + 3 > end) {
                return -1;
            }
            cursor += 2;
            int type = Byte.toUnsignedInt(file.readByte(cursor));
            cursor += 1;
            int sz = HprofTypeSize.sizeOf(type, idSize);
            if (sz < 0 || cursor + sz > end) {
                return -1;
            }
            cursor += sz;
        }

        if (cursor + 2 > end) {
            return -1;
        }
        int staticFieldCount = Short.toUnsignedInt(file.readShort(cursor));
        cursor += 2;
        for (int i = 0; i < staticFieldCount; i++) {
            if (cursor + idSize + 1 > end) {
                return -1;
            }
            cursor += idSize;
            int type = Byte.toUnsignedInt(file.readByte(cursor));
            cursor += 1;
            int sz = HprofTypeSize.sizeOf(type, idSize);
            if (sz < 0 || cursor + sz > end) {
                return -1;
            }
            cursor += sz;
        }

        if (cursor + 2 > end) {
            return -1;
        }
        int instanceFieldCount = Short.toUnsignedInt(file.readShort(cursor));
        cursor += 2;
        for (int i = 0; i < instanceFieldCount; i++) {
            if (cursor + idSize + 1 > end) {
                return -1;
            }
            cursor += idSize + 1;
        }

        return cursor - bodyOffset;
    }

    private static long instanceDumpBodySize(HprofMappedFile file, long bodyOffset, long end, int idSize) {
        long header = 2L * idSize + 8;
        if (bodyOffset + header > end) {
            return -1;
        }
        long fieldsLen = Integer.toUnsignedLong(file.readInt(bodyOffset + idSize + 4 + idSize));
        long total = header + fieldsLen;
        if (bodyOffset + total > end) {
            return -1;
        }
        return total;
    }

    private static long objectArrayBodySize(HprofMappedFile file, long bodyOffset, long end, int idSize) {
        long header = 2L * idSize + 8;
        if (bodyOffset + header > end) {
            return -1;
        }
        long n = Integer.toUnsignedLong(file.readInt(bodyOffset + idSize + 4));
        long total = header + n * idSize;
        if (bodyOffset + total > end) {
            return -1;
        }
        return total;
    }

    private static long primitiveArrayBodySize(HprofMappedFile file, long bodyOffset, long end, int idSize) {
        long header = idSize + 9L;
        if (bodyOffset + header > end) {
            return -1;
        }
        long n = Integer.toUnsignedLong(file.readInt(bodyOffset + idSize + 4));
        int elementType = Byte.toUnsignedInt(file.readByte(bodyOffset + idSize + 8));
        int sz = HprofTypeSize.sizeOf(elementType, idSize);
        if (sz < 0) {
            return -1;
        }
        long total = header + n * sz;
        if (bodyOffset + total > end) {
            return -1;
        }
        return total;
    }
}
