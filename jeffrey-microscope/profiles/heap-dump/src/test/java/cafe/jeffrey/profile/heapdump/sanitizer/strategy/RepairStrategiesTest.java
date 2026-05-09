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

package cafe.jeffrey.profile.heapdump.sanitizer.strategy;

import cafe.jeffrey.profile.heapdump.sanitizer.HprofRecordReader;
import cafe.jeffrey.profile.heapdump.sanitizer.HprofRecordTag;
import cafe.jeffrey.profile.heapdump.sanitizer.HprofRepair;
import cafe.jeffrey.profile.heapdump.sanitizer.HprofSubRecordTag;
import cafe.jeffrey.profile.heapdump.sanitizer.HprofTestFileBuilder;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the strategy SPI: each strategy is exercised in isolation by
 * feeding it a hand-built {@link ScanContext}.
 */
class RepairStrategiesTest {

    @TempDir
    Path tempDir;

    @Nested
    class TruncatedHeader {

        private final TruncatedHeaderStrategy strategy = new TruncatedHeaderStrategy();

        @Test
        void declinesWhenAtLeastNineBytesRemain() throws IOException {
            Path file = writeBytes("nine-bytes.bin", new byte[20]);
            try (FileChannel ch = FileChannel.open(file, StandardOpenOption.READ)) {
                ScanContext ctx = new ScanContext(ch, 20, 8, 5, null, false);
                assertInstanceOf(StrategyOutcome.NotApplicable.class, strategy.examine(ctx));
            }
        }

        @Test
        void appliesWhenFewerThanNineBytesRemain() throws IOException {
            Path file = writeBytes("stray.bin", new byte[20]);
            try (FileChannel ch = FileChannel.open(file, StandardOpenOption.READ)) {
                ScanContext ctx = new ScanContext(ch, 20, 8, 17, null, false);
                StrategyOutcome outcome = strategy.examine(ctx);

                StrategyOutcome.Applied applied = assertInstanceOf(StrategyOutcome.Applied.class, outcome);
                assertEquals(1, applied.repairs().size());
                HprofRepair.TruncateFile truncate = assertInstanceOf(
                        HprofRepair.TruncateFile.class, applied.repairs().get(0));
                assertEquals(17L, truncate.offset());
                assertTrue(applied.terminal());
            }
        }

        @Test
        void exposesPhaseAndId() {
            assertEquals(RepairStrategy.Phase.BOUNDARY, strategy.phase());
            assertEquals("truncated-header", strategy.id());
        }
    }

    @Nested
    class ZeroLengthSegment {

        private final ZeroLengthSegmentStrategy strategy = new ZeroLengthSegmentStrategy();

        @Test
        void declinesWhenHeaderIsNotHeapDumpSegment() throws IOException {
            HprofTestFileBuilder builder = new HprofTestFileBuilder();
            byte[] root = builder.buildRootUnknownSubRecord(1L);
            builder.writeHeader().addHeapDumpSegment(root);

            Path file = builder.writeTo(tempDir.resolve("not-segment.hprof"));
            try (FileChannel ch = FileChannel.open(file, StandardOpenOption.READ)) {
                long firstRecordOffset = builder.build().length - root.length - 9;
                HprofRecordReader.RecordHeader header = new HprofRecordReader.RecordHeader(
                        HprofRecordTag.UTF8.value(), 0, 0, firstRecordOffset);
                ScanContext ctx = new ScanContext(ch, ch.size(), 8, firstRecordOffset, header, false);
                assertInstanceOf(StrategyOutcome.NotApplicable.class, strategy.examine(ctx));
            }
        }

        @Test
        void appliesWhenSegmentDeclaresZeroLengthButHasSubRecords() throws IOException {
            HprofTestFileBuilder builder = new HprofTestFileBuilder();
            byte[] root = builder.buildRootUnknownSubRecord(1L);
            builder.writeHeader().addZeroLengthHeapDumpSegment(root);

            Path file = builder.writeTo(tempDir.resolve("zero-seg.hprof"));
            try (FileChannel ch = FileChannel.open(file, StandardOpenOption.READ)) {
                int headerSize = "JAVA PROFILE 1.0.2".length() + 1 + 4 + 8;
                HprofRecordReader.RecordHeader header = new HprofRecordReader.RecordHeader(
                        HprofRecordTag.HEAP_DUMP_SEGMENT.value(), 0, 0, headerSize);
                ScanContext ctx = new ScanContext(ch, ch.size(), 8, headerSize, header, false);

                StrategyOutcome outcome = strategy.examine(ctx);
                StrategyOutcome.Applied applied = assertInstanceOf(StrategyOutcome.Applied.class, outcome);
                assertEquals(1, applied.repairs().size());
                HprofRepair.PatchRecordLength patch = assertInstanceOf(
                        HprofRepair.PatchRecordLength.class, applied.repairs().get(0));
                assertEquals(headerSize + 5L, patch.lengthFieldOffset());
                assertEquals(root.length, patch.newLength());
                assertFalse(applied.terminal());
                assertTrue(applied.objectsRecovered() >= 1);
            }
        }
    }

    @Nested
    class OverflowedSegment {

        private final OverflowedSegmentStrategy strategy = new OverflowedSegmentStrategy();

        @Test
        void declinesWhenDeclaredFitsInFile() throws IOException {
            HprofTestFileBuilder builder = new HprofTestFileBuilder();
            byte[] root = builder.buildRootUnknownSubRecord(1L);
            builder.writeHeader().addHeapDumpSegment(root);

            Path file = builder.writeTo(tempDir.resolve("normal.hprof"));
            try (FileChannel ch = FileChannel.open(file, StandardOpenOption.READ)) {
                int headerSize = "JAVA PROFILE 1.0.2".length() + 1 + 4 + 8;
                HprofRecordReader.RecordHeader header = new HprofRecordReader.RecordHeader(
                        HprofRecordTag.HEAP_DUMP_SEGMENT.value(), 0, root.length, headerSize);
                ScanContext ctx = new ScanContext(ch, ch.size(), 8, headerSize, header, false);
                assertInstanceOf(StrategyOutcome.NotApplicable.class, strategy.examine(ctx));
            }
        }

        @Test
        void appliesPatchPlusTruncateWhenDeclaredOverrunsAndScanFindsLessThanAvailable() throws IOException {
            // valid-root + 5 garbage bytes → declared 100 bytes overruns
            HprofTestFileBuilder builder = new HprofTestFileBuilder();
            byte[] root = builder.buildRootUnknownSubRecord(1L);
            // create a body that starts with a valid root then has 5 bytes that are not a valid sub-record
            byte[] body = new byte[root.length + 5];
            System.arraycopy(root, 0, body, 0, root.length);
            body[root.length] = (byte) HprofSubRecordTag.INSTANCE_DUMP.value(); // truncated INSTANCE_DUMP
            builder.writeHeader().addOverflowedHeapDumpSegment(body, 9999);

            Path file = builder.writeTo(tempDir.resolve("overflow.hprof"));
            try (FileChannel ch = FileChannel.open(file, StandardOpenOption.READ)) {
                int headerSize = "JAVA PROFILE 1.0.2".length() + 1 + 4 + 8;
                HprofRecordReader.RecordHeader header = new HprofRecordReader.RecordHeader(
                        HprofRecordTag.HEAP_DUMP_SEGMENT.value(), 0, 9999, headerSize);
                ScanContext ctx = new ScanContext(ch, ch.size(), 8, headerSize, header, false);

                StrategyOutcome outcome = strategy.examine(ctx);
                StrategyOutcome.Applied applied = assertInstanceOf(StrategyOutcome.Applied.class, outcome);
                assertTrue(applied.terminal());
                assertEquals(2, applied.repairs().size());
                assertInstanceOf(HprofRepair.PatchRecordLength.class, applied.repairs().get(0));
                assertInstanceOf(HprofRepair.TruncateFile.class, applied.repairs().get(1));
            }
        }
    }

    @Nested
    class TruncatedSubRecords {

        private final TruncatedSubRecordsStrategy strategy = new TruncatedSubRecordsStrategy();

        @Test
        void declinesWhenSubRecordsAreClean() throws IOException {
            HprofTestFileBuilder builder = new HprofTestFileBuilder();
            byte[] root = builder.buildRootUnknownSubRecord(1L);
            builder.writeHeader().addHeapDumpSegment(root);

            Path file = builder.writeTo(tempDir.resolve("clean-seg.hprof"));
            try (FileChannel ch = FileChannel.open(file, StandardOpenOption.READ)) {
                int headerSize = "JAVA PROFILE 1.0.2".length() + 1 + 4 + 8;
                HprofRecordReader.RecordHeader header = new HprofRecordReader.RecordHeader(
                        HprofRecordTag.HEAP_DUMP_SEGMENT.value(), 0, root.length, headerSize);
                ScanContext ctx = new ScanContext(ch, ch.size(), 8, headerSize, header, false);
                assertInstanceOf(StrategyOutcome.NotApplicable.class, strategy.examine(ctx));
            }
        }

        @Test
        void appliesWhenSubRecordsTruncate() throws IOException {
            HprofTestFileBuilder builder = new HprofTestFileBuilder();
            byte[] root = builder.buildRootUnknownSubRecord(1L);
            byte[] body = new byte[root.length + 5];
            System.arraycopy(root, 0, body, 0, root.length);
            body[root.length] = (byte) HprofSubRecordTag.INSTANCE_DUMP.value();
            builder.writeHeader().addHeapDumpSegment(body).addHeapDumpEnd();

            Path file = builder.writeTo(tempDir.resolve("truncated-sub.hprof"));
            try (FileChannel ch = FileChannel.open(file, StandardOpenOption.READ)) {
                int headerSize = "JAVA PROFILE 1.0.2".length() + 1 + 4 + 8;
                HprofRecordReader.RecordHeader header = new HprofRecordReader.RecordHeader(
                        HprofRecordTag.HEAP_DUMP_SEGMENT.value(), 0, body.length, headerSize);
                ScanContext ctx = new ScanContext(ch, ch.size(), 8, headerSize, header, false);

                StrategyOutcome outcome = strategy.examine(ctx);
                StrategyOutcome.Applied applied = assertInstanceOf(StrategyOutcome.Applied.class, outcome);
                assertTrue(applied.terminal());
                assertInstanceOf(HprofRepair.PatchRecordLength.class, applied.repairs().get(0));
            }
        }
    }

    @Nested
    class TruncatedNonHeapRecord {

        private final TruncatedNonHeapRecordStrategy strategy = new TruncatedNonHeapRecordStrategy();

        @Test
        void declinesWhenBodyFits() throws IOException {
            byte[] data = new byte[100];
            Path file = writeBytes("fits.bin", data);
            try (FileChannel ch = FileChannel.open(file, StandardOpenOption.READ)) {
                HprofRecordReader.RecordHeader header = new HprofRecordReader.RecordHeader(
                        HprofRecordTag.UTF8.value(), 0, 50, 0);
                ScanContext ctx = new ScanContext(ch, 100, 8, 0, header, false);
                assertInstanceOf(StrategyOutcome.NotApplicable.class, strategy.examine(ctx));
            }
        }

        @Test
        void declinesForHeapDumpSegment() throws IOException {
            byte[] data = new byte[100];
            Path file = writeBytes("hd.bin", data);
            try (FileChannel ch = FileChannel.open(file, StandardOpenOption.READ)) {
                HprofRecordReader.RecordHeader header = new HprofRecordReader.RecordHeader(
                        HprofRecordTag.HEAP_DUMP_SEGMENT.value(), 0, 9999, 0);
                ScanContext ctx = new ScanContext(ch, 100, 8, 0, header, false);
                // Heap-dump segments are owned by other strategies.
                assertInstanceOf(StrategyOutcome.NotApplicable.class, strategy.examine(ctx));
            }
        }

        @Test
        void appliesWhenNonHeapRecordOverrunsEof() throws IOException {
            byte[] data = new byte[100];
            Path file = writeBytes("ovr.bin", data);
            try (FileChannel ch = FileChannel.open(file, StandardOpenOption.READ)) {
                HprofRecordReader.RecordHeader header = new HprofRecordReader.RecordHeader(
                        HprofRecordTag.UTF8.value(), 0, 200, 50);
                ScanContext ctx = new ScanContext(ch, 100, 8, 50, header, false);

                StrategyOutcome outcome = strategy.examine(ctx);
                StrategyOutcome.Applied applied = assertInstanceOf(StrategyOutcome.Applied.class, outcome);
                assertTrue(applied.terminal());
                HprofRepair.TruncateFile truncate = assertInstanceOf(
                        HprofRepair.TruncateFile.class, applied.repairs().get(0));
                assertEquals(50L, truncate.offset());
            }
        }
    }

    @Nested
    class MissingEndMarker {

        private final MissingEndMarkerStrategy strategy = new MissingEndMarkerStrategy();

        @Test
        void declinesWhenEndMarkerSeen() throws IOException {
            Path file = writeBytes("seen.bin", new byte[10]);
            try (FileChannel ch = FileChannel.open(file, StandardOpenOption.READ)) {
                ScanContext ctx = new ScanContext(ch, 10, 8, 10, null, true);
                assertInstanceOf(StrategyOutcome.NotApplicable.class, strategy.examine(ctx));
            }
        }

        @Test
        void appliesWhenEndMarkerNotSeen() throws IOException {
            Path file = writeBytes("absent.bin", new byte[10]);
            try (FileChannel ch = FileChannel.open(file, StandardOpenOption.READ)) {
                ScanContext ctx = new ScanContext(ch, 10, 8, 10, null, false);
                StrategyOutcome outcome = strategy.examine(ctx);
                StrategyOutcome.Applied applied = assertInstanceOf(StrategyOutcome.Applied.class, outcome);
                assertInstanceOf(HprofRepair.AppendEndMarker.class, applied.repairs().get(0));
            }
        }

        @Test
        void exposesPhaseAndId() {
            assertEquals(RepairStrategy.Phase.FINALIZE, strategy.phase());
            assertEquals("missing-end-marker", strategy.id());
        }
    }

    @Nested
    class ScanContextValidation {

        @Test
        void rejectsNegativeFileSize() {
            assertThrowsIAE(() -> new ScanContext(null, -1, 8, 0, null, false));
        }

        @Test
        void rejectsBadIdSize() {
            assertThrowsIAE(() -> new ScanContext(null, 0, 5, 0, null, false));
        }

        @Test
        void rejectsNegativePosition() {
            assertThrowsIAE(() -> new ScanContext(null, 0, 8, -1, null, false));
        }

        private static void assertThrowsIAE(Runnable r) {
            try {
                r.run();
            } catch (IllegalArgumentException expected) {
                return;
            }
            throw new AssertionError("Expected IllegalArgumentException");
        }
    }

    private Path writeBytes(String name, byte[] bytes) throws IOException {
        Path file = tempDir.resolve(name);
        Files.write(file, bytes);
        return file;
    }
}
