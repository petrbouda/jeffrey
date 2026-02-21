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

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class HprofSanitizerTest {

    private static final int ID_SIZE = 8;

    /**
     * Builds a minimal valid HPROF file header for version "JAVA PROFILE 1.0.2"
     * with the given identifier size.
     */
    private static byte[] buildHeader(int idSize) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        // Version string "JAVA PROFILE 1.0.2" + null terminator
        byte[] version = "JAVA PROFILE 1.0.2\0".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        baos.writeBytes(version);
        // Identifier size (4 bytes big-endian)
        baos.writeBytes(intToBytes(idSize));
        // Timestamp (8 bytes)
        baos.writeBytes(intToBytes(0)); // high
        baos.writeBytes(intToBytes(0)); // low
        return baos.toByteArray();
    }

    /**
     * Builds a 9-byte top-level record header.
     */
    private static byte[] buildRecordHeader(int tag, int timestamp, int bodyLength) {
        ByteBuffer buf = ByteBuffer.allocate(9);
        buf.order(ByteOrder.BIG_ENDIAN);
        buf.put((byte) tag);
        buf.putInt(timestamp);
        buf.putInt(bodyLength);
        return buf.array();
    }

    /**
     * Builds a UTF-8 record (tag 0x01) with the given string ID and content.
     */
    private static byte[] buildUtf8Record(long stringId, String content, int idSize) {
        byte[] contentBytes = content.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        int bodyLength = idSize + contentBytes.length;

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        baos.writeBytes(buildRecordHeader(0x01, 0, bodyLength));
        baos.writeBytes(idToBytes(stringId, idSize));
        baos.writeBytes(contentBytes);
        return baos.toByteArray();
    }

    /**
     * Builds a HEAP_DUMP_END record (tag 0x2C, 9 bytes).
     */
    private static byte[] buildHeapDumpEnd() {
        return buildRecordHeader(0x2C, 0, 0);
    }

    /**
     * Builds a HEAP_DUMP_SEGMENT record (tag 0x1C) wrapping the given sub-record body.
     */
    private static byte[] buildHeapDumpSegment(byte[] subRecordBody) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        baos.writeBytes(buildRecordHeader(0x1C, 0, subRecordBody.length));
        baos.writeBytes(subRecordBody);
        return baos.toByteArray();
    }

    /**
     * Builds a HEAP_DUMP_SEGMENT record with a zero-length header but actual sub-record body appended.
     * This simulates Pattern 1 corruption.
     */
    private static byte[] buildZeroLengthSegment(byte[] subRecordBody) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        baos.writeBytes(buildRecordHeader(0x1C, 0, 0));
        baos.writeBytes(subRecordBody);
        return baos.toByteArray();
    }

    /**
     * Builds a GC_ROOT_UNKNOWN sub-record (sub-tag 0xFF).
     */
    private static byte[] buildGcRootUnknown(long objectId, int idSize) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        baos.write(0xFF);
        baos.writeBytes(idToBytes(objectId, idSize));
        return baos.toByteArray();
    }

    /**
     * Builds a GC_ROOT_STICKY_CLASS sub-record (sub-tag 0x05).
     */
    private static byte[] buildGcRootStickyClass(long objectId, int idSize) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        baos.write(0x05);
        baos.writeBytes(idToBytes(objectId, idSize));
        return baos.toByteArray();
    }

    /**
     * Builds a GC_ROOT_THREAD_OBJ sub-record (sub-tag 0x08).
     */
    private static byte[] buildGcRootThreadObj(long objectId, int threadSerial, int stackSerial, int idSize) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        baos.write(0x08);
        baos.writeBytes(idToBytes(objectId, idSize));
        baos.writeBytes(intToBytes(threadSerial));
        baos.writeBytes(intToBytes(stackSerial));
        return baos.toByteArray();
    }

    /**
     * Builds a GC_INSTANCE_DUMP sub-record (sub-tag 0x21).
     */
    private static byte[] buildInstanceDump(long objectId, long classId, byte[] fieldData, int idSize) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        baos.write(0x21);
        baos.writeBytes(idToBytes(objectId, idSize));
        baos.writeBytes(intToBytes(1)); // stack trace serial
        baos.writeBytes(idToBytes(classId, idSize));
        baos.writeBytes(intToBytes(fieldData.length)); // bytes following
        baos.writeBytes(fieldData);
        return baos.toByteArray();
    }

    /**
     * Builds a GC_PRIM_ARRAY_DUMP sub-record (sub-tag 0x23) for a byte array.
     */
    private static byte[] buildPrimArrayDump(long objectId, byte[] elements, int idSize) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        baos.write(0x23);
        baos.writeBytes(idToBytes(objectId, idSize));
        baos.writeBytes(intToBytes(1)); // stack trace serial
        baos.writeBytes(intToBytes(elements.length)); // number of elements
        baos.write(8); // element type = byte
        baos.writeBytes(elements);
        return baos.toByteArray();
    }

    /**
     * Builds a GC_OBJ_ARRAY_DUMP sub-record (sub-tag 0x22).
     */
    private static byte[] buildObjArrayDump(long objectId, long classId, long[] elementIds, int idSize) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        baos.write(0x22);
        baos.writeBytes(idToBytes(objectId, idSize));
        baos.writeBytes(intToBytes(1)); // stack trace serial
        baos.writeBytes(intToBytes(elementIds.length)); // number of elements
        baos.writeBytes(idToBytes(classId, idSize)); // array class ID
        for (long elemId : elementIds) {
            baos.writeBytes(idToBytes(elemId, idSize));
        }
        return baos.toByteArray();
    }

    private static byte[] intToBytes(int value) {
        ByteBuffer buf = ByteBuffer.allocate(4);
        buf.order(ByteOrder.BIG_ENDIAN);
        buf.putInt(value);
        return buf.array();
    }

    private static byte[] idToBytes(long id, int idSize) {
        if (idSize == 4) {
            return intToBytes((int) id);
        } else {
            ByteBuffer buf = ByteBuffer.allocate(8);
            buf.order(ByteOrder.BIG_ENDIAN);
            buf.putLong(id);
            return buf.array();
        }
    }

    /**
     * Concatenates multiple byte arrays.
     */
    private static byte[] concat(byte[]... arrays) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        for (byte[] a : arrays) {
            baos.writeBytes(a);
        }
        return baos.toByteArray();
    }

    /**
     * Builds a complete valid minimal HPROF file.
     */
    private static byte[] buildValidHprof(int idSize) {
        byte[] subRecords = concat(
                buildGcRootUnknown(0x100, idSize),
                buildGcRootStickyClass(0x200, idSize)
        );

        return concat(
                buildHeader(idSize),
                buildUtf8Record(1, "test-string", idSize),
                buildHeapDumpSegment(subRecords),
                buildHeapDumpEnd()
        );
    }

    @Nested
    class NeedsSanitization {

        @Test
        void validFile(@TempDir Path tempDir) throws IOException {
            Path hprof = tempDir.resolve("valid.hprof");
            Files.write(hprof, buildValidHprof(ID_SIZE));

            assertFalse(HprofSanitizer.needsSanitization(hprof));
        }

        @Test
        void fileTooSmall(@TempDir Path tempDir) throws IOException {
            Path hprof = tempDir.resolve("tiny.hprof");
            Files.write(hprof, new byte[]{1, 2, 3});

            assertTrue(HprofSanitizer.needsSanitization(hprof));
        }

        @Test
        void missingHeapDumpEnd(@TempDir Path tempDir) throws IOException {
            byte[] subRecords = buildGcRootUnknown(0x100, ID_SIZE);
            byte[] data = concat(
                    buildHeader(ID_SIZE),
                    buildHeapDumpSegment(subRecords)
                    // No HEAP_DUMP_END
            );

            Path hprof = tempDir.resolve("no-end.hprof");
            Files.write(hprof, data);

            assertTrue(HprofSanitizer.needsSanitization(hprof));
        }

        @Test
        void zeroLengthSegment(@TempDir Path tempDir) throws IOException {
            byte[] subRecords = buildGcRootUnknown(0x100, ID_SIZE);
            byte[] data = concat(
                    buildHeader(ID_SIZE),
                    buildZeroLengthSegment(subRecords),
                    buildHeapDumpEnd()
            );

            Path hprof = tempDir.resolve("zero-length.hprof");
            Files.write(hprof, data);

            assertTrue(HprofSanitizer.needsSanitization(hprof));
        }

        @Test
        void truncatedRecord(@TempDir Path tempDir) throws IOException {
            // Build a record header that declares more body than the file has
            byte[] data = concat(
                    buildHeader(ID_SIZE),
                    buildRecordHeader(0x01, 0, 1000) // declares 1000 bytes body
                    // but no body follows
            );

            Path hprof = tempDir.resolve("truncated.hprof");
            Files.write(hprof, data);

            assertTrue(HprofSanitizer.needsSanitization(hprof));
        }
    }

    @Nested
    class SanitizeValidFile {

        @Test
        void validFilePassesThrough(@TempDir Path tempDir) throws IOException {
            byte[] original = buildValidHprof(ID_SIZE);
            Path input = tempDir.resolve("valid.hprof");
            Path output = tempDir.resolve("output.hprof");
            Files.write(input, original);

            SanitizeResult result = HprofSanitizer.sanitize(input, output);

            assertFalse(result.wasModified());
            assertFalse(result.hadZeroLengthSegments());
            assertFalse(result.wasTruncated());
            assertFalse(result.hadMissingEndMarker());
            assertFalse(result.hadTruncatedSubRecords());
            assertFalse(result.hadOverflowedLengths());
            assertEquals(0, result.zeroLengthSegmentsFixed());
            assertTrue(result.totalRecordsProcessed() > 0);

            // Output should match input
            assertArrayEquals(original, Files.readAllBytes(output));
        }

        @Test
        void validFileWith4ByteIds(@TempDir Path tempDir) throws IOException {
            int idSize = 4;
            byte[] original = buildValidHprof(idSize);
            Path input = tempDir.resolve("valid4.hprof");
            Path output = tempDir.resolve("output4.hprof");
            Files.write(input, original);

            SanitizeResult result = HprofSanitizer.sanitize(input, output);

            assertFalse(result.wasModified());
            assertArrayEquals(original, Files.readAllBytes(output));
        }
    }

    @Nested
    class Pattern1ZeroLengthSegment {

        @Test
        void fixesSingleZeroLengthSegment(@TempDir Path tempDir) throws IOException {
            byte[] subRecords = concat(
                    buildGcRootUnknown(0x100, ID_SIZE),
                    buildGcRootStickyClass(0x200, ID_SIZE)
            );

            // Zero-length segment followed by HEAP_DUMP_END
            byte[] data = concat(
                    buildHeader(ID_SIZE),
                    buildZeroLengthSegment(subRecords),
                    buildHeapDumpEnd()
            );

            Path input = tempDir.resolve("zero-len.hprof");
            Path output = tempDir.resolve("fixed.hprof");
            Files.write(input, data);

            SanitizeResult result = HprofSanitizer.sanitize(input, output);

            assertTrue(result.wasModified());
            assertTrue(result.hadZeroLengthSegments());
            assertEquals(1, result.zeroLengthSegmentsFixed());
            assertFalse(result.hadMissingEndMarker());
            assertEquals(2, result.estimatedObjectsRecovered());

            // Verify the output is structurally valid
            assertFalse(HprofSanitizer.needsSanitization(output));
        }

        @Test
        void fixesMultipleZeroLengthSegments(@TempDir Path tempDir) throws IOException {
            byte[] subRecords1 = buildGcRootUnknown(0x100, ID_SIZE);
            byte[] subRecords2 = buildGcRootStickyClass(0x200, ID_SIZE);

            byte[] data = concat(
                    buildHeader(ID_SIZE),
                    buildZeroLengthSegment(subRecords1),
                    buildZeroLengthSegment(subRecords2),
                    buildHeapDumpEnd()
            );

            Path input = tempDir.resolve("multi-zero.hprof");
            Path output = tempDir.resolve("fixed.hprof");
            Files.write(input, data);

            SanitizeResult result = HprofSanitizer.sanitize(input, output);

            assertTrue(result.wasModified());
            assertTrue(result.hadZeroLengthSegments());
            assertEquals(2, result.zeroLengthSegmentsFixed());
        }

        @Test
        void fixesZeroLengthWithInstanceDump(@TempDir Path tempDir) throws IOException {
            byte[] fieldData = new byte[]{1, 2, 3, 4, 5, 6, 7, 8};
            byte[] subRecords = buildInstanceDump(0x300, 0x400, fieldData, ID_SIZE);

            byte[] data = concat(
                    buildHeader(ID_SIZE),
                    buildZeroLengthSegment(subRecords),
                    buildHeapDumpEnd()
            );

            Path input = tempDir.resolve("zero-instance.hprof");
            Path output = tempDir.resolve("fixed.hprof");
            Files.write(input, data);

            SanitizeResult result = HprofSanitizer.sanitize(input, output);

            assertTrue(result.wasModified());
            assertTrue(result.hadZeroLengthSegments());
            assertEquals(1, result.estimatedObjectsRecovered());
            assertFalse(HprofSanitizer.needsSanitization(output));
        }

        @Test
        void fixesZeroLengthWithPrimArrayDump(@TempDir Path tempDir) throws IOException {
            byte[] elements = new byte[]{10, 20, 30, 40, 50};
            byte[] subRecords = buildPrimArrayDump(0x500, elements, ID_SIZE);

            byte[] data = concat(
                    buildHeader(ID_SIZE),
                    buildZeroLengthSegment(subRecords),
                    buildHeapDumpEnd()
            );

            Path input = tempDir.resolve("zero-prim.hprof");
            Path output = tempDir.resolve("fixed.hprof");
            Files.write(input, data);

            SanitizeResult result = HprofSanitizer.sanitize(input, output);

            assertTrue(result.wasModified());
            assertTrue(result.hadZeroLengthSegments());
            assertFalse(HprofSanitizer.needsSanitization(output));
        }

        @Test
        void fixesZeroLengthWithObjArrayDump(@TempDir Path tempDir) throws IOException {
            byte[] subRecords = buildObjArrayDump(0x600, 0x700, new long[]{0x800, 0x900}, ID_SIZE);

            byte[] data = concat(
                    buildHeader(ID_SIZE),
                    buildZeroLengthSegment(subRecords),
                    buildHeapDumpEnd()
            );

            Path input = tempDir.resolve("zero-obj-array.hprof");
            Path output = tempDir.resolve("fixed.hprof");
            Files.write(input, data);

            SanitizeResult result = HprofSanitizer.sanitize(input, output);

            assertTrue(result.wasModified());
            assertTrue(result.hadZeroLengthSegments());
            assertFalse(HprofSanitizer.needsSanitization(output));
        }
    }

    @Nested
    class Pattern2TruncatedLastRecord {

        @Test
        void discardsIncompleteRecord(@TempDir Path tempDir) throws IOException {
            byte[] subRecords = buildGcRootUnknown(0x100, ID_SIZE);
            byte[] segment = buildHeapDumpSegment(subRecords);

            // Build valid segment + then a truncated UTF8 record
            byte[] truncatedRecord = buildRecordHeader(0x01, 0, 100);
            // Only header, no body (declares 100 bytes but has 0)

            byte[] data = concat(
                    buildHeader(ID_SIZE),
                    segment,
                    truncatedRecord
            );

            Path input = tempDir.resolve("truncated.hprof");
            Path output = tempDir.resolve("fixed.hprof");
            Files.write(input, data);

            SanitizeResult result = HprofSanitizer.sanitize(input, output);

            assertTrue(result.wasModified());
            assertTrue(result.wasTruncated());
            assertTrue(result.hadMissingEndMarker());
            assertFalse(HprofSanitizer.needsSanitization(output));
        }

        @Test
        void handlesPartialRecordHeader(@TempDir Path tempDir) throws IOException {
            byte[] subRecords = buildGcRootUnknown(0x100, ID_SIZE);

            byte[] data = concat(
                    buildHeader(ID_SIZE),
                    buildHeapDumpSegment(subRecords),
                    new byte[]{0x01, 0x00, 0x00} // Partial record header (only 3 bytes)
            );

            Path input = tempDir.resolve("partial-header.hprof");
            Path output = tempDir.resolve("fixed.hprof");
            Files.write(input, data);

            SanitizeResult result = HprofSanitizer.sanitize(input, output);

            assertTrue(result.wasModified());
            assertTrue(result.wasTruncated());
            assertTrue(result.hadMissingEndMarker());
        }
    }

    @Nested
    class Pattern3MissingEndMarker {

        @Test
        void appendsEndMarker(@TempDir Path tempDir) throws IOException {
            byte[] subRecords = buildGcRootUnknown(0x100, ID_SIZE);

            byte[] data = concat(
                    buildHeader(ID_SIZE),
                    buildUtf8Record(1, "hello", ID_SIZE),
                    buildHeapDumpSegment(subRecords)
                    // No HEAP_DUMP_END
            );

            Path input = tempDir.resolve("no-end.hprof");
            Path output = tempDir.resolve("fixed.hprof");
            Files.write(input, data);

            SanitizeResult result = HprofSanitizer.sanitize(input, output);

            assertTrue(result.wasModified());
            assertTrue(result.hadMissingEndMarker());
            assertFalse(result.hadZeroLengthSegments());
            assertFalse(result.wasTruncated());

            // Verify that the output now has a HEAP_DUMP_END
            assertFalse(HprofSanitizer.needsSanitization(output));

            // Verify the output is 9 bytes longer than expected
            byte[] outputData = Files.readAllBytes(output);
            byte[] expected = concat(data, buildHeapDumpEnd());
            assertArrayEquals(expected, outputData);
        }
    }

    @Nested
    class Pattern4TruncatedSubRecords {

        @Test
        void trimsAtLastValidSubRecord(@TempDir Path tempDir) throws IOException {
            byte[] validSubRecords = concat(
                    buildGcRootUnknown(0x100, ID_SIZE),
                    buildGcRootStickyClass(0x200, ID_SIZE)
            );

            // Create a truncated primitive array: declares 1000 byte elements but provides only 3
            ByteArrayOutputStream truncatedArray = new ByteArrayOutputStream();
            truncatedArray.write(0x23); // PRIM_ARRAY_DUMP
            truncatedArray.writeBytes(idToBytes(0x300, ID_SIZE)); // object ID
            truncatedArray.writeBytes(intToBytes(1)); // stack trace serial
            truncatedArray.writeBytes(intToBytes(1000)); // declares 1000 elements
            truncatedArray.write(8); // element type = byte
            truncatedArray.writeBytes(new byte[]{1, 2, 3}); // only 3 bytes of data

            byte[] segmentBody = concat(validSubRecords, truncatedArray.toByteArray());

            // Build segment with correct declared length (includes the truncated data)
            byte[] data = concat(
                    buildHeader(ID_SIZE),
                    buildHeapDumpSegment(segmentBody),
                    buildHeapDumpEnd()
            );

            Path input = tempDir.resolve("truncated-sub.hprof");
            Path output = tempDir.resolve("fixed.hprof");
            Files.write(input, data);

            SanitizeResult result = HprofSanitizer.sanitize(input, output);

            assertTrue(result.wasModified());
            assertTrue(result.hadTruncatedSubRecords());

            // The output should be valid
            assertFalse(HprofSanitizer.needsSanitization(output));
        }
    }

    @Nested
    class Pattern5OverflowedLength {

        @Test
        void handlesNegativeLength(@TempDir Path tempDir) throws IOException {
            byte[] subRecords = concat(
                    buildGcRootUnknown(0x100, ID_SIZE),
                    buildGcRootStickyClass(0x200, ID_SIZE)
            );

            // Build segment with negative (overflowed) length
            byte[] negativeSegment = concat(
                    buildRecordHeader(0x1C, 0, -1), // negative length
                    subRecords
            );

            byte[] data = concat(
                    buildHeader(ID_SIZE),
                    negativeSegment,
                    buildHeapDumpEnd()
            );

            Path input = tempDir.resolve("overflow.hprof");
            Path output = tempDir.resolve("fixed.hprof");
            Files.write(input, data);

            SanitizeResult result = HprofSanitizer.sanitize(input, output);

            assertTrue(result.wasModified());
            assertTrue(result.hadOverflowedLengths());
            assertTrue(result.estimatedObjectsRecovered() >= 2);
        }
    }

    @Nested
    class CombinedPatterns {

        @Test
        void fixesZeroLengthAndMissingEnd(@TempDir Path tempDir) throws IOException {
            byte[] subRecords = buildGcRootUnknown(0x100, ID_SIZE);

            byte[] data = concat(
                    buildHeader(ID_SIZE),
                    buildZeroLengthSegment(subRecords)
                    // No HEAP_DUMP_END
            );

            Path input = tempDir.resolve("combined.hprof");
            Path output = tempDir.resolve("fixed.hprof");
            Files.write(input, data);

            SanitizeResult result = HprofSanitizer.sanitize(input, output);

            assertTrue(result.wasModified());
            assertTrue(result.hadZeroLengthSegments());
            assertTrue(result.hadMissingEndMarker());
            assertFalse(HprofSanitizer.needsSanitization(output));
        }

        @Test
        void fixesValidSegmentFollowedByZeroLengthSegment(@TempDir Path tempDir) throws IOException {
            byte[] subRecords1 = buildGcRootUnknown(0x100, ID_SIZE);
            byte[] subRecords2 = buildGcRootStickyClass(0x200, ID_SIZE);

            byte[] data = concat(
                    buildHeader(ID_SIZE),
                    buildHeapDumpSegment(subRecords1), // valid segment
                    buildZeroLengthSegment(subRecords2), // zero-length segment
                    buildHeapDumpEnd()
            );

            Path input = tempDir.resolve("mixed.hprof");
            Path output = tempDir.resolve("fixed.hprof");
            Files.write(input, data);

            SanitizeResult result = HprofSanitizer.sanitize(input, output);

            assertTrue(result.wasModified());
            assertTrue(result.hadZeroLengthSegments());
            assertEquals(1, result.zeroLengthSegmentsFixed());
            assertFalse(HprofSanitizer.needsSanitization(output));
        }
    }

    @Nested
    class EdgeCases {

        @Test
        void emptyFileThrowsException(@TempDir Path tempDir) throws IOException {
            Path hprof = tempDir.resolve("empty.hprof");
            Files.write(hprof, new byte[0]);

            assertThrows(IOException.class, () ->
                    HprofSanitizer.sanitize(hprof, tempDir.resolve("out.hprof")));
        }

        @Test
        void gzipFileThrowsException(@TempDir Path tempDir) throws IOException {
            byte[] gzipData = new byte[]{0x1F, (byte) 0x8B, 0x08, 0x00};
            Path hprof = tempDir.resolve("compressed.hprof");
            Files.write(hprof, gzipData);

            assertThrows(IOException.class, () ->
                    HprofSanitizer.sanitize(hprof, tempDir.resolve("out.hprof")));
        }

        @Test
        void headerOnlyFile(@TempDir Path tempDir) throws IOException {
            byte[] data = buildHeader(ID_SIZE);

            Path input = tempDir.resolve("header-only.hprof");
            Path output = tempDir.resolve("fixed.hprof");
            Files.write(input, data);

            SanitizeResult result = HprofSanitizer.sanitize(input, output);

            // Should append HEAP_DUMP_END
            assertTrue(result.wasModified());
            assertTrue(result.hadMissingEndMarker());
        }

        @Test
        void metadataOnlyNoHeapSegments(@TempDir Path tempDir) throws IOException {
            byte[] data = concat(
                    buildHeader(ID_SIZE),
                    buildUtf8Record(1, "hello", ID_SIZE),
                    buildUtf8Record(2, "world", ID_SIZE),
                    buildHeapDumpEnd()
            );

            Path input = tempDir.resolve("metadata-only.hprof");
            Path output = tempDir.resolve("fixed.hprof");
            Files.write(input, data);

            SanitizeResult result = HprofSanitizer.sanitize(input, output);

            assertFalse(result.wasModified());
            assertArrayEquals(data, Files.readAllBytes(output));
        }

        @Test
        void summaryMessageIsNotEmpty(@TempDir Path tempDir) throws IOException {
            byte[] data = buildValidHprof(ID_SIZE);
            Path input = tempDir.resolve("valid.hprof");
            Path output = tempDir.resolve("out.hprof");
            Files.write(input, data);

            SanitizeResult result = HprofSanitizer.sanitize(input, output);

            assertNotNull(result.summaryMessage());
            assertFalse(result.summaryMessage().isEmpty());
        }
    }
}
