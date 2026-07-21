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
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import cafe.jeffrey.profile.heapdump.model.DuplicateArrayGroup;
import cafe.jeffrey.profile.heapdump.model.DuplicateDataReport;
import cafe.jeffrey.profile.heapdump.persistence.HeapDumpIndexPaths;
import cafe.jeffrey.profile.heapdump.parser.HprofIndex;
import cafe.jeffrey.profile.heapdump.parser.HprofMappedFile;
import cafe.jeffrey.profile.heapdump.parser.SyntheticHprof;
import cafe.jeffrey.profile.heapdump.view.HeapView;
import cafe.jeffrey.profile.heapdump.view.HprofTag;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DuplicateArrayAnalyzerTest {

    private static final Clock CLOCK =
            Clock.fixed(Instant.ofEpochMilli(1L), ZoneOffset.UTC);
    private static final int ID_SIZE = 8;

    @Test
    void groupsByteIdenticalArraysAndComputesSavings(@TempDir Path tmp)
            throws IOException, SQLException {
        byte[] shared = "this-content-repeats-many-times!".getBytes(StandardCharsets.US_ASCII);
        byte[] unique = "completely-different-payload...!".getBytes(StandardCharsets.US_ASCII);
        byte[] tiny = new byte[]{1, 2, 3, 4}; // below the 16-byte floor — ignored

        Path hprof = SyntheticHprof.create("1.0.2", ID_SIZE, 0L)
                .heapDumpSegment(seg -> seg
                        .primitiveArrayDump(0x100L, HprofTag.BasicType.BYTE, shared, shared.length)
                        .primitiveArrayDump(0x101L, HprofTag.BasicType.BYTE, shared, shared.length)
                        .primitiveArrayDump(0x102L, HprofTag.BasicType.BYTE, shared, shared.length)
                        .primitiveArrayDump(0x200L, HprofTag.BasicType.BYTE, unique, unique.length)
                        .primitiveArrayDump(0x300L, HprofTag.BasicType.BYTE, tiny, tiny.length))
                .heapDumpEnd()
                .writeTo(tmp, "dup.hprof");

        Path indexDb = HeapDumpIndexPaths.indexFor(hprof);
        try (HprofMappedFile file = HprofMappedFile.open(hprof)) {
            HprofIndex.build(file, indexDb, CLOCK);
        }
        try (HprofMappedFile file = HprofMappedFile.open(hprof);
             HeapView view = HeapView.open(indexDb, file)) {
            DuplicateDataReport report = DuplicateArrayAnalyzer.analyze(view);

            assertEquals(5, report.totalPrimitiveArrays());
            assertEquals(1, report.duplicateGroups());
            assertEquals(2, report.duplicateArrayCount(), "two redundant copies of the shared array");
            assertEquals(0, report.oversizedSkipped());
            assertTrue(report.potentialSavings() > 0);

            assertEquals(1, report.topGroups().size());
            DuplicateArrayGroup group = report.topGroups().get(0);
            assertEquals(3, group.count());
            assertEquals(shared.length, group.arrayLength());
            assertEquals(2L * group.shallowSize(), group.wastedBytes());
            assertTrue(group.contentPreview().contains("this-content-repeats"),
                    "ASCII byte[] content must be previewed as text: " + group.contentPreview());
            assertEquals(3, group.sampleObjectIds().size());
        }
    }

    @Test
    void arraysWithSameLengthDifferentContentNotGrouped(@TempDir Path tmp)
            throws IOException, SQLException {
        byte[] a = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa".getBytes(StandardCharsets.US_ASCII);
        byte[] b = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb".getBytes(StandardCharsets.US_ASCII);

        Path hprof = SyntheticHprof.create("1.0.2", ID_SIZE, 0L)
                .heapDumpSegment(seg -> seg
                        .primitiveArrayDump(0x100L, HprofTag.BasicType.BYTE, a, a.length)
                        .primitiveArrayDump(0x101L, HprofTag.BasicType.BYTE, b, b.length))
                .heapDumpEnd()
                .writeTo(tmp, "nodup.hprof");

        Path indexDb = HeapDumpIndexPaths.indexFor(hprof);
        try (HprofMappedFile file = HprofMappedFile.open(hprof)) {
            HprofIndex.build(file, indexDb, CLOCK);
        }
        try (HprofMappedFile file = HprofMappedFile.open(hprof);
             HeapView view = HeapView.open(indexDb, file)) {
            DuplicateDataReport report = DuplicateArrayAnalyzer.analyze(view);
            assertEquals(0, report.duplicateGroups());
            assertEquals(0, report.potentialSavings());
            assertTrue(report.topGroups().isEmpty());
        }
    }
}
