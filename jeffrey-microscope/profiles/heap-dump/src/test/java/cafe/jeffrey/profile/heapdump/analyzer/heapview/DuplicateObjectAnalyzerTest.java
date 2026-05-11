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
package cafe.jeffrey.profile.heapdump.analyzer.heapview;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import cafe.jeffrey.profile.heapdump.model.DuplicateObjectsReport;
import cafe.jeffrey.profile.heapdump.parser.HeapDumpIndexPaths;
import cafe.jeffrey.profile.heapdump.parser.HeapView;
import cafe.jeffrey.profile.heapdump.parser.HprofIndex;
import cafe.jeffrey.profile.heapdump.parser.HprofMappedFile;
import cafe.jeffrey.profile.heapdump.parser.HprofTag;
import cafe.jeffrey.profile.heapdump.parser.SyntheticHprof;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DuplicateObjectAnalyzerTest {

    private static final Clock CLOCK =
            Clock.fixed(Instant.ofEpochMilli(1L), ZoneOffset.UTC);

    @Test
    void detectsByteIdenticalInstances(@TempDir Path tmp) throws IOException, SQLException {
        // 16-byte class with two instances having identical bytes + one with different bytes.
        long classId = 0xC001L;
        byte[] commonContent = new byte[16];
        for (int i = 0; i < 16; i++) {
            commonContent[i] = (byte) (i * 7);
        }
        byte[] otherContent = new byte[16];
        for (int i = 0; i < 16; i++) {
            otherContent[i] = (byte) (0xFF - i);
        }

        Path hprof = SyntheticHprof.create("1.0.2", 8, 0L)
                .string(0xA001L, "com.example.Holder")
                .string(0xA002L, "f")
                .loadClass(1, classId, 0, 0xA001L)
                .heapDumpSegment(seg -> seg
                        .classDumpWithFields(classId, 0L, 0L, 16,
                                new SyntheticHprof.SubBuilder.FieldSpec(0xA002L, HprofTag.BasicType.LONG),
                                new SyntheticHprof.SubBuilder.FieldSpec(0xA003L, HprofTag.BasicType.LONG))
                        .instanceDump(0x1001L, classId, commonContent)
                        .instanceDump(0x1002L, classId, commonContent)
                        .instanceDump(0x1003L, classId, otherContent))
                .heapDumpEnd()
                .writeTo(tmp, "dups.hprof");
        Path indexDb = HeapDumpIndexPaths.indexFor(hprof);
        try (HprofMappedFile file = HprofMappedFile.open(hprof)) {
            HprofIndex.build(file, indexDb, CLOCK);
        }
        try (HprofMappedFile file = HprofMappedFile.open(hprof);
             HeapView view = HeapView.open(indexDb, file)) {
            DuplicateObjectsReport report = DuplicateObjectAnalyzer.analyze(view, 10);
            assertEquals(3, report.totalInstancesAnalyzed());
            assertEquals(1, report.duplicates().size(),
                    "two identical instances form one duplicate group");
            assertEquals("com.example.Holder", report.duplicates().get(0).className());
            assertEquals(2, report.duplicates().get(0).duplicateCount());
            assertTrue(report.totalWastedBytes() > 0);
        }
    }

    @Test
    void noDuplicatesReturnsEmptyList(@TempDir Path tmp) throws IOException, SQLException {
        long classId = 0xC001L;
        Path hprof = SyntheticHprof.create("1.0.2", 8, 0L)
                .string(0xA001L, "X")
                .string(0xA002L, "f")
                .loadClass(1, classId, 0, 0xA001L)
                .heapDumpSegment(seg -> seg
                        .classDumpWithFields(classId, 0L, 0L, 8,
                                new SyntheticHprof.SubBuilder.FieldSpec(0xA002L, HprofTag.BasicType.LONG))
                        .instanceDump(0x1001L, classId, new byte[]{1, 0, 0, 0, 0, 0, 0, 0})
                        .instanceDump(0x1002L, classId, new byte[]{2, 0, 0, 0, 0, 0, 0, 0}))
                .heapDumpEnd()
                .writeTo(tmp, "uniq.hprof");
        Path indexDb = HeapDumpIndexPaths.indexFor(hprof);
        try (HprofMappedFile file = HprofMappedFile.open(hprof)) {
            HprofIndex.build(file, indexDb, CLOCK);
        }
        try (HprofMappedFile file = HprofMappedFile.open(hprof);
             HeapView view = HeapView.open(indexDb, file)) {
            DuplicateObjectsReport report = DuplicateObjectAnalyzer.analyze(view);
            assertTrue(report.duplicates().isEmpty());
            assertEquals(0, report.totalWastedBytes());
        }
    }

    @Test
    void singleInstanceClassIsFilteredOutBeforeHashing(@TempDir Path tmp) throws IOException, SQLException {
        // The (class_id, shallow_size) HAVING COUNT(*) >= 2 pre-filter should
        // drop this class entirely — no instance ever enters the hash loop.
        long classId = 0xC001L;
        byte[] content = new byte[16];
        for (int i = 0; i < 16; i++) {
            content[i] = (byte) i;
        }
        Path hprof = SyntheticHprof.create("1.0.2", 8, 0L)
                .string(0xA001L, "com.example.Lonely")
                .string(0xA002L, "f")
                .loadClass(1, classId, 0, 0xA001L)
                .heapDumpSegment(seg -> seg
                        .classDumpWithFields(classId, 0L, 0L, 16,
                                new SyntheticHprof.SubBuilder.FieldSpec(0xA002L, HprofTag.BasicType.LONG),
                                new SyntheticHprof.SubBuilder.FieldSpec(0xA002L, HprofTag.BasicType.LONG))
                        .instanceDump(0x1001L, classId, content))
                .heapDumpEnd()
                .writeTo(tmp, "lonely.hprof");
        Path indexDb = HeapDumpIndexPaths.indexFor(hprof);
        try (HprofMappedFile file = HprofMappedFile.open(hprof)) {
            HprofIndex.build(file, indexDb, CLOCK);
        }
        try (HprofMappedFile file = HprofMappedFile.open(hprof);
             HeapView view = HeapView.open(indexDb, file)) {
            DuplicateObjectsReport report = DuplicateObjectAnalyzer.analyze(view);
            assertEquals(0, report.totalInstancesAnalyzed(),
                    "single-instance class must be SQL-filtered before any byte read");
            assertEquals(0, report.totalWastedBytes());
            assertTrue(report.duplicates().isEmpty());
        }
    }

    @Test
    void multipleClassesEachWithDuplicatesAreReportedSeparately(@TempDir Path tmp)
            throws IOException, SQLException {
        // Four classes, each with three byte-identical instances. The bin-packer
        // will distribute these across multiple buckets (parallelism kicks in
        // because candidates >= 2), exercising the per-bucket SQL fan-out + the
        // local-map merge in runParallel.
        long classA = 0xC001L;
        long classB = 0xC002L;
        long classC = 0xC003L;
        long classD = 0xC004L;
        byte[] contentA = pattern((byte) 0xAA);
        byte[] contentB = pattern((byte) 0xBB);
        byte[] contentC = pattern((byte) 0xCC);
        byte[] contentD = pattern((byte) 0xDD);

        Path hprof = SyntheticHprof.create("1.0.2", 8, 0L)
                .string(0xA001L, "pkg.A")
                .string(0xA002L, "pkg.B")
                .string(0xA003L, "pkg.C")
                .string(0xA004L, "pkg.D")
                .string(0xA005L, "f")
                .loadClass(1, classA, 0, 0xA001L)
                .loadClass(2, classB, 0, 0xA002L)
                .loadClass(3, classC, 0, 0xA003L)
                .loadClass(4, classD, 0, 0xA004L)
                .heapDumpSegment(seg -> seg
                        .classDumpWithFields(classA, 0L, 0L, 16,
                                new SyntheticHprof.SubBuilder.FieldSpec(0xA005L, HprofTag.BasicType.LONG),
                                new SyntheticHprof.SubBuilder.FieldSpec(0xA005L, HprofTag.BasicType.LONG))
                        .classDumpWithFields(classB, 0L, 0L, 16,
                                new SyntheticHprof.SubBuilder.FieldSpec(0xA005L, HprofTag.BasicType.LONG),
                                new SyntheticHprof.SubBuilder.FieldSpec(0xA005L, HprofTag.BasicType.LONG))
                        .classDumpWithFields(classC, 0L, 0L, 16,
                                new SyntheticHprof.SubBuilder.FieldSpec(0xA005L, HprofTag.BasicType.LONG),
                                new SyntheticHprof.SubBuilder.FieldSpec(0xA005L, HprofTag.BasicType.LONG))
                        .classDumpWithFields(classD, 0L, 0L, 16,
                                new SyntheticHprof.SubBuilder.FieldSpec(0xA005L, HprofTag.BasicType.LONG),
                                new SyntheticHprof.SubBuilder.FieldSpec(0xA005L, HprofTag.BasicType.LONG))
                        .instanceDump(0x1001L, classA, contentA)
                        .instanceDump(0x1002L, classA, contentA)
                        .instanceDump(0x1003L, classA, contentA)
                        .instanceDump(0x2001L, classB, contentB)
                        .instanceDump(0x2002L, classB, contentB)
                        .instanceDump(0x2003L, classB, contentB)
                        .instanceDump(0x3001L, classC, contentC)
                        .instanceDump(0x3002L, classC, contentC)
                        .instanceDump(0x3003L, classC, contentC)
                        .instanceDump(0x4001L, classD, contentD)
                        .instanceDump(0x4002L, classD, contentD)
                        .instanceDump(0x4003L, classD, contentD))
                .heapDumpEnd()
                .writeTo(tmp, "multi.hprof");
        Path indexDb = HeapDumpIndexPaths.indexFor(hprof);
        try (HprofMappedFile file = HprofMappedFile.open(hprof)) {
            HprofIndex.build(file, indexDb, CLOCK);
        }
        try (HprofMappedFile file = HprofMappedFile.open(hprof);
             HeapView view = HeapView.open(indexDb, file)) {
            DuplicateObjectsReport report = DuplicateObjectAnalyzer.analyze(view, 100);
            assertEquals(12, report.totalInstancesAnalyzed());
            assertEquals(4, report.duplicates().size(),
                    "each class has one duplicate group of three identical instances");
            for (var entry : report.duplicates()) {
                assertEquals(3, entry.duplicateCount(),
                        "each group has exactly three instances: className=" + entry.className());
            }
        }
    }

    private static byte[] pattern(byte fill) {
        byte[] out = new byte[16];
        java.util.Arrays.fill(out, fill);
        return out;
    }

    @Test
    void differentClassesWithIdenticalBytesAreNotGrouped(@TempDir Path tmp) throws IOException, SQLException {
        long classA = 0xC001L;
        long classB = 0xC002L;
        byte[] sameBytes = new byte[16];

        Path hprof = SyntheticHprof.create("1.0.2", 8, 0L)
                .string(0xA001L, "A")
                .string(0xA002L, "B")
                .string(0xA003L, "f")
                .loadClass(1, classA, 0, 0xA001L)
                .loadClass(2, classB, 0, 0xA002L)
                .heapDumpSegment(seg -> seg
                        .classDumpWithFields(classA, 0L, 0L, 16,
                                new SyntheticHprof.SubBuilder.FieldSpec(0xA003L, HprofTag.BasicType.LONG),
                                new SyntheticHprof.SubBuilder.FieldSpec(0xA003L, HprofTag.BasicType.LONG))
                        .classDumpWithFields(classB, 0L, 0L, 16,
                                new SyntheticHprof.SubBuilder.FieldSpec(0xA003L, HprofTag.BasicType.LONG),
                                new SyntheticHprof.SubBuilder.FieldSpec(0xA003L, HprofTag.BasicType.LONG))
                        .instanceDump(0x1001L, classA, sameBytes)
                        .instanceDump(0x1002L, classB, sameBytes))
                .heapDumpEnd()
                .writeTo(tmp, "cross-class.hprof");
        Path indexDb = HeapDumpIndexPaths.indexFor(hprof);
        try (HprofMappedFile file = HprofMappedFile.open(hprof)) {
            HprofIndex.build(file, indexDb, CLOCK);
        }
        try (HprofMappedFile file = HprofMappedFile.open(hprof);
             HeapView view = HeapView.open(indexDb, file)) {
            DuplicateObjectsReport report = DuplicateObjectAnalyzer.analyze(view);
            assertTrue(report.duplicates().isEmpty(),
                    "duplicate detection groups by (class, content) so cross-class equal bytes don't pair up");
        }
    }

    @Test
    void stringDuplicatesShowDecodedContent(@TempDir Path tmp) throws IOException, SQLException {
        // Two java.lang.String instances sharing the same byte[] value-array of "hi" — the
        // Content Preview should render the decoded text, not the raw field-block hex.
        int idSize = 8;
        int stringInstanceBytes = idSize + 1 + 4; // value(OBJECT) + coder(BYTE) + hash(INT)
        long stringClass = 0xC001L;
        long sharedArray = 0x9001L;
        long s1 = 0x1001L;
        long s2 = 0x1002L;

        Path hprof = SyntheticHprof.create("1.0.2", idSize, 0L)
                .string(0xA001L, "java.lang.String")
                .string(0xA002L, "value")
                .string(0xA003L, "coder")
                .string(0xA004L, "hash")
                .loadClass(1, stringClass, 0, 0xA001L)
                .heapDumpSegment(seg -> seg
                        .classDumpWithFields(stringClass, 0L, 0L, stringInstanceBytes,
                                new SyntheticHprof.SubBuilder.FieldSpec(0xA002L, HprofTag.BasicType.OBJECT),
                                new SyntheticHprof.SubBuilder.FieldSpec(0xA003L, HprofTag.BasicType.BYTE),
                                new SyntheticHprof.SubBuilder.FieldSpec(0xA004L, HprofTag.BasicType.INT))
                        .primitiveArrayDump(sharedArray, HprofTag.BasicType.BYTE,
                                "hi".getBytes(StandardCharsets.ISO_8859_1), 2)
                        .instanceDump(s1, stringClass, stringFieldBytes(sharedArray, (byte) 0))
                        .instanceDump(s2, stringClass, stringFieldBytes(sharedArray, (byte) 0)))
                .heapDumpEnd()
                .writeTo(tmp, "strings.hprof");
        Path indexDb = HeapDumpIndexPaths.indexFor(hprof);
        try (HprofMappedFile file = HprofMappedFile.open(hprof)) {
            HprofIndex.build(file, indexDb, CLOCK);
        }
        try (HprofMappedFile file = HprofMappedFile.open(hprof);
             HeapView view = HeapView.open(indexDb, file)) {
            DuplicateObjectsReport report = DuplicateObjectAnalyzer.analyze(view, 10);
            assertEquals(1, report.duplicates().size());
            var entry = report.duplicates().get(0);
            assertEquals("java.lang.String", entry.className());
            assertEquals("\"hi\"", entry.contentPreview(),
                    "String preview should render the decoded value wrapped in quotes");
        }
    }

    @Test
    void longDuplicatesShowNumericValue(@TempDir Path tmp) throws IOException, SQLException {
        // Two java.lang.Long instances with the same numeric value — preview should be
        // "42", not the raw 8-byte big-endian hex.
        int idSize = 8;
        long longClass = 0xC001L;
        long l1 = 0x1001L;
        long l2 = 0x1002L;
        byte[] valueBytes = longFieldBytes(42L);

        Path hprof = SyntheticHprof.create("1.0.2", idSize, 0L)
                .string(0xA001L, "java.lang.Long")
                .string(0xA002L, "value")
                .loadClass(1, longClass, 0, 0xA001L)
                .heapDumpSegment(seg -> seg
                        .classDumpWithFields(longClass, 0L, 0L, 8,
                                new SyntheticHprof.SubBuilder.FieldSpec(0xA002L, HprofTag.BasicType.LONG))
                        .instanceDump(l1, longClass, valueBytes)
                        .instanceDump(l2, longClass, valueBytes))
                .heapDumpEnd()
                .writeTo(tmp, "longs.hprof");
        Path indexDb = HeapDumpIndexPaths.indexFor(hprof);
        try (HprofMappedFile file = HprofMappedFile.open(hprof)) {
            HprofIndex.build(file, indexDb, CLOCK);
        }
        try (HprofMappedFile file = HprofMappedFile.open(hprof);
             HeapView view = HeapView.open(indexDb, file)) {
            DuplicateObjectsReport report = DuplicateObjectAnalyzer.analyze(view, 10);
            assertEquals(1, report.duplicates().size());
            var entry = report.duplicates().get(0);
            assertEquals("java.lang.Long", entry.className());
            assertEquals("42", entry.contentPreview());
        }
    }

    @Test
    void unknownClassFallsBackToHexPreview(@TempDir Path tmp) throws IOException, SQLException {
        // A class neither String nor a boxed primitive — preview should keep the
        // original space-separated hex of the first 16 bytes.
        long classId = 0xC001L;
        byte[] content = new byte[]{
                0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08,
                0x09, 0x0A, 0x0B, 0x0C, 0x0D, 0x0E, 0x0F, 0x10
        };
        Path hprof = SyntheticHprof.create("1.0.2", 8, 0L)
                .string(0xA001L, "com.example.NotKnown")
                .string(0xA002L, "f")
                .loadClass(1, classId, 0, 0xA001L)
                .heapDumpSegment(seg -> seg
                        .classDumpWithFields(classId, 0L, 0L, 16,
                                new SyntheticHprof.SubBuilder.FieldSpec(0xA002L, HprofTag.BasicType.LONG),
                                new SyntheticHprof.SubBuilder.FieldSpec(0xA002L, HprofTag.BasicType.LONG))
                        .instanceDump(0x1001L, classId, content)
                        .instanceDump(0x1002L, classId, content))
                .heapDumpEnd()
                .writeTo(tmp, "unknown.hprof");
        Path indexDb = HeapDumpIndexPaths.indexFor(hprof);
        try (HprofMappedFile file = HprofMappedFile.open(hprof)) {
            HprofIndex.build(file, indexDb, CLOCK);
        }
        try (HprofMappedFile file = HprofMappedFile.open(hprof);
             HeapView view = HeapView.open(indexDb, file)) {
            DuplicateObjectsReport report = DuplicateObjectAnalyzer.analyze(view, 10);
            assertEquals(1, report.duplicates().size());
            var entry = report.duplicates().get(0);
            assertEquals("com.example.NotKnown", entry.className());
            assertNotNull(entry.contentPreview());
            assertEquals("01 02 03 04 05 06 07 08 09 0a 0b 0c 0d 0e 0f 10",
                    entry.contentPreview(),
                    "unknown classes keep the 16-byte hex preview from before this change");
        }
    }

    private static byte[] stringFieldBytes(long valueArrayId, byte coder) {
        try {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            DataOutputStream d = new DataOutputStream(b);
            d.writeLong(valueArrayId);
            d.writeByte(coder);
            d.writeInt(0); // hash
            return b.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static byte[] longFieldBytes(long value) {
        try {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            DataOutputStream d = new DataOutputStream(b);
            d.writeLong(value);
            return b.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
