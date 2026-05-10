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

import java.io.IOException;
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
}
