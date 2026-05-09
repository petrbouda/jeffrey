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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HprofCopySanitizerTest {

    @TempDir
    Path tempDir;

    @Nested
    class CleanFiles {

        @Test
        void cleanFileIsCopiedByteForByte() throws IOException {
            HprofTestFileBuilder builder = new HprofTestFileBuilder();
            byte[] root = builder.buildRootUnknownSubRecord(1L);
            builder.writeHeader().addHeapDumpSegment(root).addHeapDumpEnd();

            Path source = builder.writeTo(tempDir.resolve("clean.hprof"));
            Path target = tempDir.resolve("clean.hprof.sanitized");

            SanitizeResult result = HprofCopySanitizer.sanitize(source, target);

            assertFalse(result.wasModified());
            // Source untouched, target is byte-identical to source.
            assertArrayEquals(Files.readAllBytes(source), Files.readAllBytes(target));
        }
    }

    @Nested
    class CorruptionPatterns {

        @Test
        void zeroLengthSegmentRepairedInTargetSourceUntouched() throws IOException {
            HprofTestFileBuilder builder = new HprofTestFileBuilder();
            byte[] root = builder.buildRootUnknownSubRecord(1L);
            builder.writeHeader().addZeroLengthHeapDumpSegment(root).addHeapDumpEnd();

            Path source = builder.writeTo(tempDir.resolve("zero.hprof"));
            Path target = tempDir.resolve("zero.hprof.sanitized");
            byte[] sourceBefore = Files.readAllBytes(source);

            SanitizeResult result = HprofCopySanitizer.sanitize(source, target);

            assertTrue(result.wasModified());
            assertTrue(result.hadZeroLengthSegments());
            // Source unchanged.
            assertArrayEquals(sourceBefore, Files.readAllBytes(source));
            // Target has the same length but a patched length field.
            assertEquals(sourceBefore.length, Files.size(target));
            assertFalse(Arrays.equals(sourceBefore, Files.readAllBytes(target)));
        }

        @Test
        void missingEndMarkerAppendedInTarget() throws IOException {
            HprofTestFileBuilder builder = new HprofTestFileBuilder();
            byte[] root = builder.buildRootUnknownSubRecord(1L);
            builder.writeHeader().addHeapDumpSegment(root);
            // No addHeapDumpEnd()

            Path source = builder.writeTo(tempDir.resolve("no-end.hprof"));
            Path target = tempDir.resolve("no-end.hprof.sanitized");
            long sourceSize = Files.size(source);

            SanitizeResult result = HprofCopySanitizer.sanitize(source, target);

            assertTrue(result.wasModified());
            assertTrue(result.hadMissingEndMarker());
            assertEquals(sourceSize, Files.size(source));
            assertEquals(sourceSize + 9, Files.size(target));
            assertEndMarkerAtTail(target);
        }

        @Test
        void overflowedSegmentTruncatedInTargetSourceIntact() throws IOException {
            HprofTestFileBuilder builder = new HprofTestFileBuilder();
            byte[] root = builder.buildRootUnknownSubRecord(1L);
            builder.writeHeader().addOverflowedHeapDumpSegment(root, 999_999);

            Path source = builder.writeTo(tempDir.resolve("overflow.hprof"));
            Path target = tempDir.resolve("overflow.hprof.sanitized");
            long sourceSize = Files.size(source);

            SanitizeResult result = HprofCopySanitizer.sanitize(source, target);

            assertTrue(result.wasModified());
            assertTrue(result.hadOverflowedLengths());
            assertEquals(sourceSize, Files.size(source));
            assertEndMarkerAtTail(target);
        }
    }

    @Nested
    class EdgeCases {

        @Test
        void replacesPreExistingTarget() throws IOException {
            HprofTestFileBuilder builder = new HprofTestFileBuilder();
            byte[] root = builder.buildRootUnknownSubRecord(1L);
            builder.writeHeader().addHeapDumpSegment(root).addHeapDumpEnd();

            Path source = builder.writeTo(tempDir.resolve("clean.hprof"));
            Path target = tempDir.resolve("clean.hprof.sanitized");
            // pre-existing target with junk content
            Files.write(target, new byte[]{1, 2, 3, 4, 5});

            HprofCopySanitizer.sanitize(source, target);

            assertArrayEquals(Files.readAllBytes(source), Files.readAllBytes(target));
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
