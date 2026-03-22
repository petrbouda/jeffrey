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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class HprofSanitizerTest {

    @TempDir
    Path tempDir;

    @Nested
    class ValidFiles {

        @Test
        void validFilePassesThroughUnmodified() throws IOException {
            HprofTestFileBuilder builder = new HprofTestFileBuilder();
            byte[] root = builder.buildRootUnknownSubRecord(1L);
            byte[] subRecords = builder.concat(root);

            builder.writeHeader()
                    .addUtf8Record(1L, "test-string")
                    .addStackTraceRecord(1, 0, 0)
                    .addHeapDumpSegment(subRecords)
                    .addHeapDumpEnd();

            Path input = builder.writeTo(tempDir.resolve("valid.hprof"));
            Path output = tempDir.resolve("valid-sanitized.hprof");

            SanitizeResult result = HprofSanitizer.sanitize(input, output);

            assertFalse(result.wasModified());
            assertFalse(result.hadZeroLengthSegments());
            assertFalse(result.wasTruncated());
            assertFalse(result.hadMissingEndMarker());
            assertFalse(result.hadTruncatedSubRecords());
            assertFalse(result.hadOverflowedLengths());
            assertTrue(result.totalRecordsProcessed() > 0);
            assertTrue(Files.size(output) > 0);
        }

        @Test
        void validFileWithMultipleSegments() throws IOException {
            HprofTestFileBuilder builder = new HprofTestFileBuilder();
            byte[] root1 = builder.buildRootUnknownSubRecord(1L);
            byte[] root2 = builder.buildRootStickyClassSubRecord(2L);

            builder.writeHeader()
                    .addHeapDumpSegment(root1)
                    .addHeapDumpSegment(root2)
                    .addHeapDumpEnd();

            Path input = builder.writeTo(tempDir.resolve("multi-segment.hprof"));
            Path output = tempDir.resolve("multi-segment-sanitized.hprof");

            SanitizeResult result = HprofSanitizer.sanitize(input, output);

            assertFalse(result.wasModified());
            assertEquals(3, result.totalRecordsProcessed()); // 2 segments + 1 end
        }
    }

    @Nested
    class MissingEndMarker {

        @Test
        void appendsEndMarkerWhenMissing() throws IOException {
            HprofTestFileBuilder builder = new HprofTestFileBuilder();
            byte[] root = builder.buildRootUnknownSubRecord(1L);

            builder.writeHeader()
                    .addHeapDumpSegment(root);
            // No addHeapDumpEnd()

            Path input = builder.writeTo(tempDir.resolve("no-end.hprof"));
            Path output = tempDir.resolve("no-end-sanitized.hprof");

            SanitizeResult result = HprofSanitizer.sanitize(input, output);

            assertTrue(result.wasModified());
            assertTrue(result.hadMissingEndMarker());
            assertTrue(Files.size(output) > Files.size(input));
        }
    }

    @Nested
    class ZeroLengthSegments {

        @Test
        void fixesZeroLengthSegmentWithValidSubRecords() throws IOException {
            HprofTestFileBuilder builder = new HprofTestFileBuilder();
            byte[] root = builder.buildRootUnknownSubRecord(1L);

            builder.writeHeader()
                    .addZeroLengthHeapDumpSegment(root)
                    .addHeapDumpEnd();

            Path input = builder.writeTo(tempDir.resolve("zero-length.hprof"));
            Path output = tempDir.resolve("zero-length-sanitized.hprof");

            SanitizeResult result = HprofSanitizer.sanitize(input, output);

            assertTrue(result.wasModified());
            assertTrue(result.hadZeroLengthSegments());
            assertEquals(1, result.zeroLengthSegmentsFixed());
            assertTrue(result.estimatedObjectsRecovered() > 0);
        }
    }

    @Nested
    class TruncatedFiles {

        @Test
        void handlesTruncatedFileAtRecordBoundary() throws IOException {
            HprofTestFileBuilder builder = new HprofTestFileBuilder();
            byte[] root = builder.buildRootUnknownSubRecord(1L);

            builder.writeHeader()
                    .addHeapDumpSegment(root);
            // File ends without end marker and with partial data appended
            builder.writeRawBytes(new byte[]{0x1C, 0x00, 0x00}); // Partial record header

            Path input = builder.writeTo(tempDir.resolve("truncated.hprof"));
            Path output = tempDir.resolve("truncated-sanitized.hprof");

            SanitizeResult result = HprofSanitizer.sanitize(input, output);

            assertTrue(result.wasModified());
            assertTrue(result.hadMissingEndMarker());
        }

        @Test
        void handlesFileWithOnlyHeader() throws IOException {
            HprofTestFileBuilder builder = new HprofTestFileBuilder();
            builder.writeHeader();

            Path input = builder.writeTo(tempDir.resolve("header-only.hprof"));
            Path output = tempDir.resolve("header-only-sanitized.hprof");

            SanitizeResult result = HprofSanitizer.sanitize(input, output);

            assertTrue(result.wasModified());
            assertTrue(result.hadMissingEndMarker());
            assertEquals(0, result.totalRecordsProcessed());
        }
    }

    @Nested
    class OverflowedLengths {

        @Test
        void handlesSegmentLengthExceedingFileSize() throws IOException {
            HprofTestFileBuilder builder = new HprofTestFileBuilder();
            byte[] root = builder.buildRootUnknownSubRecord(1L);

            // Declare body length as 999999 but only provide root.length bytes
            builder.writeHeader()
                    .addOverflowedHeapDumpSegment(root, 999999);

            Path input = builder.writeTo(tempDir.resolve("overflowed.hprof"));
            Path output = tempDir.resolve("overflowed-sanitized.hprof");

            SanitizeResult result = HprofSanitizer.sanitize(input, output);

            assertTrue(result.wasModified());
            assertTrue(result.hadOverflowedLengths());
            assertTrue(result.hadMissingEndMarker());
            assertTrue(result.estimatedObjectsRecovered() > 0);
        }
    }

    @Nested
    class TruncatedSubRecords {

        @Test
        void trimsSegmentWithTruncatedLastSubRecord() throws IOException {
            HprofTestFileBuilder builder = new HprofTestFileBuilder();
            byte[] validRoot = builder.buildRootUnknownSubRecord(1L);

            // Create a segment with valid root + truncated instance dump
            byte[] truncatedData = new byte[validRoot.length + 5];
            System.arraycopy(validRoot, 0, truncatedData, 0, validRoot.length);
            // Write INSTANCE_DUMP tag + partial data
            truncatedData[validRoot.length] = (byte) HprofSubRecordTag.INSTANCE_DUMP.value();
            truncatedData[validRoot.length + 1] = 0;
            truncatedData[validRoot.length + 2] = 0;
            truncatedData[validRoot.length + 3] = 0;
            truncatedData[validRoot.length + 4] = 1;

            builder.writeHeader()
                    .addHeapDumpSegment(truncatedData)
                    .addHeapDumpEnd();

            Path input = builder.writeTo(tempDir.resolve("truncated-sub.hprof"));
            Path output = tempDir.resolve("truncated-sub-sanitized.hprof");

            SanitizeResult result = HprofSanitizer.sanitize(input, output);

            assertTrue(result.wasModified());
            assertTrue(result.hadTruncatedSubRecords());
            assertTrue(result.estimatedObjectsRecovered() > 0);
        }
    }

    @Nested
    class EdgeCases {

        @Test
        void throwsOnEmptyFile() {
            Path input = tempDir.resolve("empty.hprof");
            Path output = tempDir.resolve("empty-sanitized.hprof");

            assertThrows(IOException.class, () -> {
                Files.createFile(input);
                HprofSanitizer.sanitize(input, output);
            });
        }

        @Test
        void handlesInstanceDumpSubRecords() throws IOException {
            HprofTestFileBuilder builder = new HprofTestFileBuilder();
            byte[] instance = builder.buildInstanceDumpSubRecord(1L, 0, 2L, new byte[]{1, 2, 3, 4});
            byte[] root = builder.buildRootUnknownSubRecord(3L);

            builder.writeHeader()
                    .addHeapDumpSegment(builder.concat(root, instance))
                    .addHeapDumpEnd();

            Path input = builder.writeTo(tempDir.resolve("instance-dump.hprof"));
            Path output = tempDir.resolve("instance-dump-sanitized.hprof");

            SanitizeResult result = HprofSanitizer.sanitize(input, output);

            assertFalse(result.wasModified());
            assertEquals(2, result.totalRecordsProcessed()); // 1 segment + 1 end
        }

        @Test
        void handlesPrimArraySubRecords() throws IOException {
            HprofTestFileBuilder builder = new HprofTestFileBuilder();
            byte[] primArray = builder.buildPrimArraySubRecord(1L, 0, HprofTypeSize.BYTE, new byte[]{10, 20, 30});
            byte[] root = builder.buildRootUnknownSubRecord(2L);

            builder.writeHeader()
                    .addHeapDumpSegment(builder.concat(root, primArray))
                    .addHeapDumpEnd();

            Path input = builder.writeTo(tempDir.resolve("prim-array.hprof"));
            Path output = tempDir.resolve("prim-array-sanitized.hprof");

            SanitizeResult result = HprofSanitizer.sanitize(input, output);

            assertFalse(result.wasModified());
        }

        @Test
        void idSize4Works() throws IOException {
            HprofTestFileBuilder builder = new HprofTestFileBuilder().idSize(4);
            byte[] root = builder.buildRootUnknownSubRecord(1L);

            builder.writeHeader()
                    .addHeapDumpSegment(root)
                    .addHeapDumpEnd();

            Path input = builder.writeTo(tempDir.resolve("id4.hprof"));
            Path output = tempDir.resolve("id4-sanitized.hprof");

            SanitizeResult result = HprofSanitizer.sanitize(input, output);

            assertFalse(result.wasModified());
        }
    }
}
