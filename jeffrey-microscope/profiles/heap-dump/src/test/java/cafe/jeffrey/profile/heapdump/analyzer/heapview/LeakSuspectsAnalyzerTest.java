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
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import cafe.jeffrey.profile.heapdump.model.LeakSuspectsReport;
import cafe.jeffrey.profile.heapdump.parser.DominatorTreeBuilder;
import cafe.jeffrey.profile.heapdump.parser.HeapDumpIndexPaths;
import cafe.jeffrey.profile.heapdump.parser.HeapView;
import cafe.jeffrey.profile.heapdump.parser.HprofIndex;
import cafe.jeffrey.profile.heapdump.parser.HprofMappedFile;
import cafe.jeffrey.profile.heapdump.parser.HprofTag;
import cafe.jeffrey.profile.heapdump.parser.SyntheticHprof;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LeakSuspectsAnalyzerTest {

    private static final Clock CLOCK =
            Clock.fixed(Instant.ofEpochMilli(1L), ZoneOffset.UTC);

    @Test
    void identifiesTopRetainerAsLeakSuspect(@TempDir Path tmp) throws IOException, SQLException {
        // Chain V→A→B→C→D where everyone has 24-byte shallow size.
        // A retains 24*4 = 96 bytes (whole chain). Total heap = 96.
        // With threshold 50%, A is the only suspect (B retains 72, also >50%).
        int idSize = 8;
        long classId = 0xC001L;
        long a = 0x100L, b = 0x200L, c = 0x300L, d = 0x400L;

        Path hprof = SyntheticHprof.create("1.0.2", idSize, 0L)
                .string(0xA001L, "Holder")
                .string(0xA002L, "next")
                .loadClass(1, classId, 0, 0xA001L)
                .heapDumpSegment(seg -> seg
                        .topLevelObjectClassDump(classId, 0xA002L)
                        .gcRoot(HprofTag.Sub.ROOT_STICKY_CLASS, a)
                        .instanceDump(a, classId, idBytes(b, idSize))
                        .instanceDump(b, classId, idBytes(c, idSize))
                        .instanceDump(c, classId, idBytes(d, idSize))
                        .instanceDump(d, classId, idBytes(0L, idSize)))
                .heapDumpEnd()
                .writeTo(tmp, "leak.hprof");

        Path indexDb = HeapDumpIndexPaths.indexFor(hprof);
        try (HprofMappedFile file = HprofMappedFile.open(hprof)) {
            HprofIndex.build(file, indexDb, CLOCK);
        }
        DominatorTreeBuilder.build(indexDb);

        try (HeapView view = HeapView.open(indexDb)) {
            LeakSuspectsReport report = LeakSuspectsAnalyzer.analyze(view, 50.0, 5);
            assertTrue(report.suspects().size() >= 1, "A retains 100% of heap, must surface as suspect");
            // Top suspect is whoever retains most — that's A.
            assertEquals(a, report.suspects().get(0).objectId());
            assertEquals(1, report.suspects().get(0).rank());
            assertEquals("Holder", report.suspects().get(0).className());
            assertTrue(report.suspects().get(0).heapPercentage() >= 50.0);
            assertTrue(report.suspects().get(0).reason().contains("Holder"));
        }
    }

    @Test
    void honorsThresholdAndTopN(@TempDir Path tmp) throws IOException, SQLException {
        int idSize = 8;
        long classId = 0xC001L;
        long a = 0x100L, b = 0x200L;

        // Two roots: A and B. Both retain only themselves (24 bytes each).
        // Total heap = 48. 50% threshold = 24. Both retain exactly 24, both surface.
        Path hprof = SyntheticHprof.create("1.0.2", idSize, 0L)
                .string(0xA001L, "Holder")
                .string(0xA002L, "next")
                .loadClass(1, classId, 0, 0xA001L)
                .heapDumpSegment(seg -> seg
                        .topLevelObjectClassDump(classId, 0xA002L)
                        .gcRoot(HprofTag.Sub.ROOT_STICKY_CLASS, a)
                        .gcRoot(HprofTag.Sub.ROOT_STICKY_CLASS, b)
                        .instanceDump(a, classId, idBytes(0L, idSize))
                        .instanceDump(b, classId, idBytes(0L, idSize)))
                .heapDumpEnd()
                .writeTo(tmp, "two-roots.hprof");

        Path indexDb = HeapDumpIndexPaths.indexFor(hprof);
        try (HprofMappedFile file = HprofMappedFile.open(hprof)) {
            HprofIndex.build(file, indexDb, CLOCK);
        }
        DominatorTreeBuilder.build(indexDb);

        try (HeapView view = HeapView.open(indexDb)) {
            // topN=1 → only one returned even though two qualify.
            LeakSuspectsReport report = LeakSuspectsAnalyzer.analyze(view, 50.0, 1);
            assertEquals(1, report.suspects().size());

            // Threshold 200% → nobody qualifies.
            LeakSuspectsReport empty = LeakSuspectsAnalyzer.analyze(view, 99.999, 5);
            // Each retains exactly 50% so 99.999% threshold filters them out.
            assertEquals(0, empty.suspects().size());
        }
    }

    @Test
    void throwsIfDominatorTreeNotBuilt(@TempDir Path tmp) throws IOException, SQLException {
        Path hprof = SyntheticHprof.create("1.0.2", 8, 0L)
                .string(0xA001L, "X")
                .heapDumpEnd()
                .writeTo(tmp, "no-dom.hprof");
        Path indexDb = HeapDumpIndexPaths.indexFor(hprof);
        try (HprofMappedFile file = HprofMappedFile.open(hprof)) {
            HprofIndex.build(file, indexDb, CLOCK);
        }
        try (HeapView view = HeapView.open(indexDb)) {
            assertThrows(IllegalStateException.class, () -> LeakSuspectsAnalyzer.analyze(view));
        }
    }

    private static byte[] idBytes(long id, int idSize) {
        try {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            DataOutputStream d = new DataOutputStream(b);
            if (idSize == 4) {
                d.writeInt((int) id);
            } else {
                d.writeLong(id);
            }
            return b.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
