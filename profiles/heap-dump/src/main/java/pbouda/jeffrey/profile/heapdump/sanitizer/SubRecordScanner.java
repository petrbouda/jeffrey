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

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;

/**
 * Scans sub-records within HEAP_DUMP and HEAP_DUMP_SEGMENT records to determine
 * how many bytes of valid data they contain. This is the core engine used by
 * {@link HprofSanitizer} to repair corrupted segments.
 */
public final class SubRecordScanner {

    /**
     * Result of scanning sub-records within a heap dump segment.
     *
     * @param validBytes     number of bytes that form complete, valid sub-records
     * @param subRecordCount number of complete sub-records found
     * @param truncated      true if the segment ended in the middle of a sub-record
     */
    public record ScanResult(long validBytes, long subRecordCount, boolean truncated) {
    }

    private static final int SCAN_BUFFER_SIZE = 8 * 1024 * 1024; // 8MB

    private SubRecordScanner() {
    }

    /**
     * Scans sub-records from a ByteBuffer.
     *
     * @param buffer the buffer positioned at the start of sub-record data
     * @param offset the starting offset within the buffer
     * @param limit  the maximum number of bytes to scan
     * @param idSize the size of object IDs (4 or 8)
     * @return the scan result
     */
    public static ScanResult scan(ByteBuffer buffer, int offset, long limit, int idSize) {
        long pos = offset;
        long end = offset + limit;
        long count = 0;

        while (pos < end) {
            if (pos + 1 > end) {
                return new ScanResult(pos - offset, count, true);
            }

            int tagByte = Byte.toUnsignedInt(buffer.get((int) pos));
            long bodySize = computeSubRecordBodySize(buffer, (int) pos + 1, end, tagByte, idSize);
            if (bodySize < 0) {
                // Truncated or invalid sub-record
                return new ScanResult(pos - offset, count, true);
            }

            long totalSize = 1 + bodySize; // 1 byte for tag + body
            if (pos + totalSize > end) {
                return new ScanResult(pos - offset, count, true);
            }

            pos += totalSize;
            count++;
        }

        return new ScanResult(pos - offset, count, false);
    }

    /**
     * Scans sub-records from a FileChannel, reading in chunks.
     *
     * @param channel       the file channel positioned at the start of sub-record data
     * @param startPosition the file position where sub-records begin
     * @param totalLength   the total number of bytes to scan
     * @param idSize        the size of object IDs (4 or 8)
     * @return the scan result
     * @throws IOException if an I/O error occurs
     */
    public static ScanResult scanFromChannel(FileChannel channel, long startPosition, long totalLength, int idSize) throws IOException {
        if (totalLength <= SCAN_BUFFER_SIZE) {
            // Small enough to scan in memory
            ByteBuffer buffer = ByteBuffer.allocate((int) totalLength);
            buffer.order(ByteOrder.BIG_ENDIAN);
            channel.position(startPosition);
            int read = 0;
            while (read < totalLength) {
                int r = channel.read(buffer);
                if (r < 0) break;
                read += r;
            }
            buffer.flip();
            return scan(buffer, 0, read, idSize);
        }

        // Large segment: scan in streaming chunks with overlap for boundary safety
        long totalValidBytes = 0;
        long totalSubRecords = 0;
        long remaining = totalLength;
        long channelPos = startPosition;

        // We need overlap because a sub-record at the end of one chunk
        // may extend into the next chunk. We process conservatively.
        while (remaining > 0) {
            int chunkSize = (int) Math.min(SCAN_BUFFER_SIZE, remaining);
            ByteBuffer buffer = ByteBuffer.allocate(chunkSize);
            buffer.order(ByteOrder.BIG_ENDIAN);
            channel.position(channelPos);

            int read = 0;
            while (read < chunkSize) {
                int r = channel.read(buffer);
                if (r < 0) break;
                read += r;
            }
            buffer.flip();

            ScanResult chunkResult = scan(buffer, 0, read, idSize);
            totalValidBytes += chunkResult.validBytes();
            totalSubRecords += chunkResult.subRecordCount();

            if (chunkResult.truncated() || chunkResult.validBytes() < read) {
                // Truncation occurred or we didn't consume all bytes
                return new ScanResult(totalValidBytes, totalSubRecords, chunkResult.truncated());
            }

            channelPos += read;
            remaining -= read;
        }

        return new ScanResult(totalValidBytes, totalSubRecords, false);
    }

    /**
     * Computes the body size of a sub-record (excluding the 1-byte tag).
     * Returns -1 if the data is truncated or the tag is unknown.
     */
    static long computeSubRecordBodySize(ByteBuffer buffer, int pos, long end, int tagByte, int idSize) {
        HprofSubRecordTag tag = HprofSubRecordTag.fromByte(tagByte);
        if (tag == null) {
            return -1;
        }

        return switch (tag) {
            case ROOT_UNKNOWN -> idSize;                        // id
            case ROOT_JNI_GLOBAL -> (long) idSize + idSize;     // id + id
            case ROOT_JNI_LOCAL -> (long) idSize + 4 + 4;       // id + u4 thread serial + u4 frame
            case ROOT_JAVA_FRAME -> (long) idSize + 4 + 4;      // id + u4 thread serial + u4 frame
            case ROOT_NATIVE_STACK -> (long) idSize + 4;         // id + u4 thread serial
            case ROOT_STICKY_CLASS -> idSize;                    // id
            case ROOT_THREAD_BLOCK -> (long) idSize + 4;         // id + u4 thread serial
            case ROOT_MONITOR_USED -> idSize;                    // id
            case ROOT_THREAD_OBJ -> (long) idSize + 4 + 4;      // id + u4 thread serial + u4 stack trace serial
            case CLASS_DUMP -> computeClassDumpSize(buffer, pos, end, idSize);
            case INSTANCE_DUMP -> computeInstanceDumpSize(buffer, pos, end, idSize);
            case OBJ_ARRAY_DUMP -> computeObjArrayDumpSize(buffer, pos, end, idSize);
            case PRIM_ARRAY_DUMP -> computePrimArrayDumpSize(buffer, pos, end, idSize);
        };
    }

    /**
     * CLASS_DUMP body:
     *   id(classObjId) + u4(stackTrace) + id(super) + id(classLoader) + id(signers) +
     *   id(protDomain) + id(reserved1) + id(reserved2) + u4(instanceSize) +
     *   u2(constPoolSize) + [constPool entries] +
     *   u2(staticFieldCount) + [static field entries] +
     *   u2(instanceFieldCount) + [instance field entries]
     */
    private static long computeClassDumpSize(ByteBuffer buffer, int pos, long end, int idSize) {
        // Fixed part: id + u4 + 5*id + u4 = idSize + 4 + 5*idSize + 4 = 6*idSize + 8
        long fixedSize = (long) 6 * idSize + 8;
        long cursor = pos + fixedSize;

        if (cursor + 2 > end) return -1;

        // Constant pool
        int constPoolSize = Short.toUnsignedInt(buffer.getShort((int) cursor));
        cursor += 2;
        for (int i = 0; i < constPoolSize; i++) {
            if (cursor + 3 > end) return -1;
            cursor += 2; // u2 index
            int type = Byte.toUnsignedInt(buffer.get((int) cursor));
            cursor += 1;
            int typeSize = HprofTypeSize.sizeOf(type, idSize);
            if (typeSize < 0) return -1;
            cursor += typeSize;
        }

        // Static fields
        if (cursor + 2 > end) return -1;
        int staticFieldCount = Short.toUnsignedInt(buffer.getShort((int) cursor));
        cursor += 2;
        for (int i = 0; i < staticFieldCount; i++) {
            if (cursor + idSize + 1 > end) return -1;
            cursor += idSize; // id nameId
            int type = Byte.toUnsignedInt(buffer.get((int) cursor));
            cursor += 1;
            int typeSize = HprofTypeSize.sizeOf(type, idSize);
            if (typeSize < 0) return -1;
            if (cursor + typeSize > end) return -1;
            cursor += typeSize;
        }

        // Instance fields
        if (cursor + 2 > end) return -1;
        int instanceFieldCount = Short.toUnsignedInt(buffer.getShort((int) cursor));
        cursor += 2;
        for (int i = 0; i < instanceFieldCount; i++) {
            if (cursor + idSize + 1 > end) return -1;
            cursor += idSize; // id nameId
            cursor += 1;     // u1 type
        }

        return cursor - pos;
    }

    /**
     * INSTANCE_DUMP body:
     *   id(objId) + u4(stackTrace) + id(classObjId) + u4(bytesFollowing) + [instance bytes]
     */
    private static long computeInstanceDumpSize(ByteBuffer buffer, int pos, long end, int idSize) {
        // id + u4 + id + u4 = 2*idSize + 8
        long headerSize = (long) 2 * idSize + 8;
        if (pos + headerSize > end) return -1;

        int bytesFollowing = buffer.getInt((int) (pos + idSize + 4 + idSize));
        long totalBodyBytes = Integer.toUnsignedLong(bytesFollowing);

        long total = headerSize + totalBodyBytes;
        if (pos + total > end) return -1;

        return total;
    }

    /**
     * OBJ_ARRAY_DUMP body:
     *   id(arrayObjId) + u4(stackTrace) + u4(numElements) + id(arrayClassId) + [id * numElements]
     */
    private static long computeObjArrayDumpSize(ByteBuffer buffer, int pos, long end, int idSize) {
        // id + u4 + u4 + id = 2*idSize + 8
        long headerSize = (long) 2 * idSize + 8;
        if (pos + headerSize > end) return -1;

        int numElements = buffer.getInt((int) (pos + idSize + 4));
        long elementsSize = Integer.toUnsignedLong(numElements) * idSize;

        long total = headerSize + elementsSize;
        if (pos + total > end) return -1;

        return total;
    }

    /**
     * PRIM_ARRAY_DUMP body:
     *   id(arrayObjId) + u4(stackTrace) + u4(numElements) + u1(elementType) + [primitives]
     */
    private static long computePrimArrayDumpSize(ByteBuffer buffer, int pos, long end, int idSize) {
        // id + u4 + u4 + u1 = idSize + 9
        long headerSize = (long) idSize + 9;
        if (pos + headerSize > end) return -1;

        int numElements = buffer.getInt((int) (pos + idSize + 4));
        int elementType = Byte.toUnsignedInt(buffer.get((int) (pos + idSize + 8)));
        int elementSize = HprofTypeSize.sizeOf(elementType, idSize);
        if (elementSize < 0) return -1;

        long elementsSize = Integer.toUnsignedLong(numElements) * elementSize;

        long total = headerSize + elementsSize;
        if (pos + total > end) return -1;

        return total;
    }
}
