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

package cafe.jeffrey.profile.heapdump.sanitizer;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HprofInPlaceSanitizerTest {

    @TempDir
    Path tempDir;

    @Nested
    class CleanFiles {

        @Test
        void validFileLeftUntouched() throws IOException {
            HprofTestFileBuilder builder = new HprofTestFileBuilder();
            byte[] root = builder.buildRootUnknownSubRecord(1L);

            builder.writeHeader()
                    .addUtf8Record(1L, "test")
                    .addStackTraceRecord(1, 0, 0)
                    .addHeapDumpSegment(root)
                    .addHeapDumpEnd();

            Path file = builder.writeTo(tempDir.resolve("clean.hprof"));
            byte[] before = Files.readAllBytes(file);

            SanitizeResult result = HprofInPlaceSanitizer.sanitize(file);

            assertFalse(result.wasModified());
            assertArrayEquals(before, Files.readAllBytes(file));
        }

        @Test
        void multipleSegmentsLeftUntouched() throws IOException {
            HprofTestFileBuilder builder = new HprofTestFileBuilder();
            byte[] root1 = builder.buildRootUnknownSubRecord(1L);
            byte[] root2 = builder.buildRootStickyClassSubRecord(2L);

            builder.writeHeader()
                    .addHeapDumpSegment(root1)
                    .addHeapDumpSegment(root2)
                    .addHeapDumpEnd();

            Path file = builder.writeTo(tempDir.resolve("multi-segment.hprof"));
            byte[] before = Files.readAllBytes(file);

            SanitizeResult result = HprofInPlaceSanitizer.sanitize(file);

            assertFalse(result.wasModified());
            assertEquals(3, result.totalRecordsProcessed()); // 2 segments + 1 end
            assertArrayEquals(before, Files.readAllBytes(file));
        }

        @Test
        void instanceDumpSubRecordsLeftUntouched() throws IOException {
            HprofTestFileBuilder builder = new HprofTestFileBuilder();
            byte[] instance = builder.buildInstanceDumpSubRecord(1L, 0, 2L, new byte[]{1, 2, 3, 4});
            byte[] root = builder.buildRootUnknownSubRecord(3L);

            builder.writeHeader()
                    .addHeapDumpSegment(builder.concat(root, instance))
                    .addHeapDumpEnd();

            Path file = builder.writeTo(tempDir.resolve("instance-dump.hprof"));
            byte[] before = Files.readAllBytes(file);

            SanitizeResult result = HprofInPlaceSanitizer.sanitize(file);

            assertFalse(result.wasModified());
            assertEquals(2, result.totalRecordsProcessed()); // 1 segment + 1 end
            assertArrayEquals(before, Files.readAllBytes(file));
        }

        @Test
        void primArraySubRecordsLeftUntouched() throws IOException {
            HprofTestFileBuilder builder = new HprofTestFileBuilder();
            byte[] primArray = builder.buildPrimArraySubRecord(1L, 0, HprofTypeSize.BYTE, new byte[]{10, 20, 30});
            byte[] root = builder.buildRootUnknownSubRecord(2L);

            builder.writeHeader()
                    .addHeapDumpSegment(builder.concat(root, primArray))
                    .addHeapDumpEnd();

            Path file = builder.writeTo(tempDir.resolve("prim-array.hprof"));
            byte[] before = Files.readAllBytes(file);

            SanitizeResult result = HprofInPlaceSanitizer.sanitize(file);

            assertFalse(result.wasModified());
            assertArrayEquals(before, Files.readAllBytes(file));
        }

        @Test
        void idSize4Works() throws IOException {
            HprofTestFileBuilder builder = new HprofTestFileBuilder().idSize(4);
            byte[] root = builder.buildRootUnknownSubRecord(1L);

            builder.writeHeader()
                    .addHeapDumpSegment(root)
                    .addHeapDumpEnd();

            Path file = builder.writeTo(tempDir.resolve("id4.hprof"));
            byte[] before = Files.readAllBytes(file);

            SanitizeResult result = HprofInPlaceSanitizer.sanitize(file);

            assertFalse(result.wasModified());
            assertArrayEquals(before, Files.readAllBytes(file));
        }
    }

    @Nested
    class MissingEndMarkerStrategyTests {

        @Test
        void appendsHeapDumpEndWhenAbsent() throws IOException {
            HprofTestFileBuilder builder = new HprofTestFileBuilder();
            byte[] root = builder.buildRootUnknownSubRecord(1L);
            builder.writeHeader().addHeapDumpSegment(root);

            Path file = builder.writeTo(tempDir.resolve("no-end.hprof"));
            long sizeBefore = Files.size(file);

            SanitizeResult result = HprofInPlaceSanitizer.sanitize(file);

            assertTrue(result.wasModified());
            assertTrue(result.hadMissingEndMarker());
            assertEquals(sizeBefore + 9, Files.size(file));
            assertEndMarkerAtTail(file);
        }
    }

    @Nested
    class ZeroLengthSegmentStrategyTests {

        @Test
        void patchesLengthToActualSubRecordCount() throws IOException {
            HprofTestFileBuilder builder = new HprofTestFileBuilder();
            byte[] root = builder.buildRootUnknownSubRecord(1L);

            builder.writeHeader()
                    .addZeroLengthHeapDumpSegment(root)
                    .addHeapDumpEnd();

            Path file = builder.writeTo(tempDir.resolve("zero-length.hprof"));
            long sizeBefore = Files.size(file);

            SanitizeResult result = HprofInPlaceSanitizer.sanitize(file);

            assertTrue(result.wasModified());
            assertTrue(result.hadZeroLengthSegments());
            assertEquals(1, result.zeroLengthSegmentsFixed());
            assertTrue(result.estimatedObjectsRecovered() > 0);
            // No size change — only the length field was patched.
            assertEquals(sizeBefore, Files.size(file));
            assertEndMarkerAtTail(file);
        }

        @Test
        void writesPatchedLengthIntoHeader() throws IOException {
            HprofTestFileBuilder builder = new HprofTestFileBuilder();
            byte[] root = builder.buildRootUnknownSubRecord(1L);
            int rootLen = root.length;

            builder.writeHeader().addZeroLengthHeapDumpSegment(root).addHeapDumpEnd();
            Path file = builder.writeTo(tempDir.resolve("zero-patched.hprof"));

            HprofInPlaceSanitizer.sanitize(file);

            // Compute the offset of the segment record's length field.
            // header = 18-byte version + null + 4 idSize + 8 timestamp = 31 bytes for "JAVA PROFILE 1.0.2"
            int headerSize = "JAVA PROFILE 1.0.2".length() + 1 + 4 + 8;
            int lengthFieldOffset = headerSize + 5; // record tag(1) + timestamp(4)
            byte[] patched = Files.readAllBytes(file);
            int patchedLength = ByteBuffer.wrap(patched, lengthFieldOffset, 4)
                    .order(ByteOrder.BIG_ENDIAN).getInt();
            assertEquals(rootLen, patchedLength);
        }
    }

    @Nested
    class OverflowedSegmentStrategyTests {

        @Test
        void patchesLengthAndTruncatesAtTail() throws IOException {
            HprofTestFileBuilder builder = new HprofTestFileBuilder();
            byte[] root = builder.buildRootUnknownSubRecord(1L);
            builder.writeHeader().addOverflowedHeapDumpSegment(root, 999_999);

            Path file = builder.writeTo(tempDir.resolve("overflowed.hprof"));

            SanitizeResult result = HprofInPlaceSanitizer.sanitize(file);

            assertTrue(result.wasModified());
            assertTrue(result.hadOverflowedLengths());
            assertTrue(result.hadMissingEndMarker());
            // File ends with the synthetic end marker.
            assertEndMarkerAtTail(file);
        }
    }

    @Nested
    class TruncatedSubRecordsStrategyTests {

        @Test
        void trimsSegmentToLastCompleteSubRecord() throws IOException {
            HprofTestFileBuilder builder = new HprofTestFileBuilder();
            byte[] validRoot = builder.buildRootUnknownSubRecord(1L);

            // valid root + INSTANCE_DUMP tag with only 4 trailing bytes (not enough)
            byte[] partial = new byte[validRoot.length + 5];
            System.arraycopy(validRoot, 0, partial, 0, validRoot.length);
            partial[validRoot.length] = (byte) HprofSubRecordTag.INSTANCE_DUMP.value();

            builder.writeHeader().addHeapDumpSegment(partial).addHeapDumpEnd();
            Path file = builder.writeTo(tempDir.resolve("truncated-sub.hprof"));
            long sizeBefore = Files.size(file);

            SanitizeResult result = HprofInPlaceSanitizer.sanitize(file);

            assertTrue(result.wasModified());
            assertTrue(result.hadTruncatedSubRecords());
            // Patched + truncated → file is shorter than before.
            assertTrue(Files.size(file) < sizeBefore);
            // Strategy is terminal so the original HEAP_DUMP_END is gone — finalize re-adds one.
            assertTrue(result.hadMissingEndMarker());
            assertEndMarkerAtTail(file);
        }
    }

    @Nested
    class TruncatedHeaderStrategyTests {

        @Test
        void discardsStrayBytesShorterThanRecordHeader() throws IOException {
            HprofTestFileBuilder builder = new HprofTestFileBuilder();
            byte[] root = builder.buildRootUnknownSubRecord(1L);
            builder.writeHeader().addHeapDumpSegment(root);
            // Append 3 stray bytes — not enough for a full 9-byte header.
            builder.writeRawBytes(new byte[]{0x1C, 0x00, 0x00});

            Path file = builder.writeTo(tempDir.resolve("stray.hprof"));

            SanitizeResult result = HprofInPlaceSanitizer.sanitize(file);

            assertTrue(result.wasModified());
            assertTrue(result.wasTruncated());
            assertTrue(result.hadMissingEndMarker());
            assertEndMarkerAtTail(file);
        }
    }

    @Nested
    class TruncatedNonHeapRecordStrategyTests {

        @Test
        void discardsNonHeapRecordWhoseBodyOverrunsEof() throws IOException {
            // Build a valid prefix, then manually append a UTF8 record with
            // declared length > available bytes.
            HprofTestFileBuilder builder = new HprofTestFileBuilder();
            byte[] root = builder.buildRootUnknownSubRecord(1L);
            builder.writeHeader().addHeapDumpSegment(root);

            // UTF8 header: tag=0x01, ts=0, length=999 — but we only write 4 body bytes.
            ByteBuffer buf = ByteBuffer.allocate(9 + 4).order(ByteOrder.BIG_ENDIAN);
            buf.put((byte) HprofRecordTag.UTF8.value());
            buf.putInt(0);
            buf.putInt(999);
            buf.put(new byte[]{1, 2, 3, 4});
            builder.writeRawBytes(buf.array());

            Path file = builder.writeTo(tempDir.resolve("trunc-utf8.hprof"));

            SanitizeResult result = HprofInPlaceSanitizer.sanitize(file);

            assertTrue(result.wasModified());
            assertTrue(result.wasTruncated());
            assertTrue(result.hadMissingEndMarker());
            assertEndMarkerAtTail(file);
        }
    }

    @Nested
    class EdgeCases {

        @Test
        void emptyFileThrows() {
            Path file = tempDir.resolve("empty.hprof");
            assertThrows(IOException.class, () -> {
                Files.createFile(file);
                HprofInPlaceSanitizer.sanitize(file);
            });
        }

        @Test
        void headerOnlyGetsEndMarker() throws IOException {
            HprofTestFileBuilder builder = new HprofTestFileBuilder();
            builder.writeHeader();

            Path file = builder.writeTo(tempDir.resolve("header-only.hprof"));
            long sizeBefore = Files.size(file);

            SanitizeResult result = HprofInPlaceSanitizer.sanitize(file);

            assertTrue(result.wasModified());
            assertTrue(result.hadMissingEndMarker());
            assertEquals(0, result.totalRecordsProcessed());
            assertEquals(sizeBefore + 9, Files.size(file));
        }

        @Test
        void idempotentOnRepeatedSanitization() throws IOException {
            HprofTestFileBuilder builder = new HprofTestFileBuilder();
            byte[] root = builder.buildRootUnknownSubRecord(1L);
            builder.writeHeader().addZeroLengthHeapDumpSegment(root);

            Path file = builder.writeTo(tempDir.resolve("idem.hprof"));

            SanitizeResult first = HprofInPlaceSanitizer.sanitize(file);
            byte[] afterFirst = Files.readAllBytes(file);
            SanitizeResult second = HprofInPlaceSanitizer.sanitize(file);

            assertTrue(first.wasModified());
            assertFalse(second.wasModified());
            assertArrayEquals(afterFirst, Files.readAllBytes(file));
        }
    }

    private static void assertEndMarkerAtTail(Path file) throws IOException {
        byte[] bytes = Files.readAllBytes(file);
        byte[] tail = Arrays.copyOfRange(bytes, bytes.length - 9, bytes.length);
        assertEquals((byte) HprofRecordTag.HEAP_DUMP_END.value(), tail[0],
                "Expected HEAP_DUMP_END tag (0x2C) at offset -9");
        for (int i = 1; i < 9; i++) {
            assertEquals(0, tail[i], "Expected zero byte at end-marker offset " + i);
        }
    }
}
