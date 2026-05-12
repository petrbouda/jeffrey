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
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import cafe.jeffrey.profile.heapdump.model.ClassHistogramEntry;
import cafe.jeffrey.profile.heapdump.model.GCRootSummary;
import cafe.jeffrey.profile.heapdump.model.HeapSummary;
import cafe.jeffrey.profile.heapdump.model.SortBy;
import cafe.jeffrey.profile.heapdump.parser.HeapDumpIndexPaths;
import cafe.jeffrey.profile.heapdump.parser.HeapView;
import cafe.jeffrey.profile.heapdump.parser.HprofIndex;
import cafe.jeffrey.profile.heapdump.parser.HprofMappedFile;
import cafe.jeffrey.profile.heapdump.parser.HprofTag;
import cafe.jeffrey.profile.heapdump.parser.SyntheticHprof;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Tier1AnalyzerTest {

    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.ofEpochMilli(1_770_000_000_000L), ZoneOffset.UTC);

    /** Build a representative index — used by every test in this file. */
    private static HeapView openIndex(Path tmp) throws IOException, SQLException {
        Path hprof = SyntheticHprof.create("1.0.2", 8, 4242L)
                .string(0xA001L, "com.example.Foo")
                .string(0xA002L, "com.example.Bar")
                .string(0xA003L, "value")
                .loadClass(1, 0xC001L, 0, 0xA001L)
                .loadClass(2, 0xC002L, 0, 0xA002L)
                .heapDumpSegment(seg -> seg
                        .gcRoot(HprofTag.Sub.ROOT_STICKY_CLASS, 0xC001L)
                        .gcRoot(HprofTag.Sub.ROOT_STICKY_CLASS, 0xC002L)
                        .gcRootJavaFrame(0x1001L, 7, 13)
                        .gcRootJavaFrame(0x1002L, 7, 14)
                        .gcRootThreadObject(0x9001L, 1, 1)
                        .simpleClassDump(0xC001L, 0L, 0xCC00L, 16, 0xA003L)
                        .simpleClassDump(0xC002L, 0xC001L, 0xCC00L, 24, 0xA003L)
                        .instanceDump(0x1001L, 0xC001L, new byte[]{0, 0, 0, 1})
                        .instanceDump(0x1002L, 0xC001L, new byte[]{0, 0, 0, 2})
                        .instanceDump(0x2001L, 0xC002L, new byte[8])
                        .instanceDump(0x2002L, 0xC002L, new byte[8])
                        .instanceDump(0x2003L, 0xC002L, new byte[8])
                        .primitiveArrayDump(0x3001L, HprofTag.BasicType.INT, new byte[16], 4))
                .heapDumpEnd()
                .writeTo(tmp, "tier1.hprof");

        Path indexDb = HeapDumpIndexPaths.indexFor(hprof);
        try (HprofMappedFile file = HprofMappedFile.open(hprof)) {
            HprofIndex.build(file, indexDb, FIXED_CLOCK);
        }
        return HeapView.open(indexDb);
    }

    @Nested
    class Summary {

        @Test
        void aggregatesTotalsFromTheIndex(@TempDir Path tmp) throws IOException, SQLException {
            try (HeapView view = openIndex(tmp)) {
                HeapSummary s = HeapSummaryAnalyzer.analyze(view);
                assertEquals(6, s.totalInstances(), "5 instance dumps + 1 primitive array");
                // 2 real CLASS_DUMPs + 8 synthetic primitive-array class rows (boolean[]..long[]).
                assertEquals(10, s.classCount());
                assertEquals(5, s.gcRootCount());
                assertEquals(Instant.ofEpochMilli(4242L), s.timestamp());
                assertTrue(s.totalBytes() > 0);
            }
        }
    }

    @Nested
    class Histogram {

        @Test
        void sortsBySizeDescendingByDefault(@TempDir Path tmp) throws IOException, SQLException {
            try (HeapView view = openIndex(tmp)) {
                List<ClassHistogramEntry> hist =
                        ClassHistogramAnalyzer.analyze(view, 10, SortBy.SIZE);
                assertTrue(hist.size() >= 2);
                // Bar has more instances (3) but Foo and Bar both have non-trivial size;
                // we just assert ordering is by total size descending.
                for (int i = 1; i < hist.size(); i++) {
                    assertTrue(hist.get(i - 1).totalSize() >= hist.get(i).totalSize(),
                            "totalSize must be non-increasing");
                }
            }
        }

        @Test
        void sortsByCountDescending(@TempDir Path tmp) throws IOException, SQLException {
            try (HeapView view = openIndex(tmp)) {
                List<ClassHistogramEntry> hist =
                        ClassHistogramAnalyzer.analyze(view, 10, SortBy.COUNT);
                // First non-unknown row should be Bar (3) before Foo (2) before primitive bucket.
                ClassHistogramEntry top = hist.get(0);
                assertEquals(3, top.instanceCount(), () -> "expected Bar (3 instances) on top, got: " + top);
                assertEquals("com.example.Bar", top.className());
            }
        }

        @Test
        void sortsByClassNameAscending(@TempDir Path tmp) throws IOException, SQLException {
            try (HeapView view = openIndex(tmp)) {
                List<ClassHistogramEntry> hist =
                        ClassHistogramAnalyzer.analyze(view, 10, SortBy.CLASS_NAME);
                for (int i = 1; i < hist.size(); i++) {
                    assertTrue(hist.get(i - 1).className().compareTo(hist.get(i).className()) <= 0);
                }
            }
        }

        @Test
        void honorsTopNCap(@TempDir Path tmp) throws IOException, SQLException {
            try (HeapView view = openIndex(tmp)) {
                assertEquals(1, ClassHistogramAnalyzer.analyze(view, 1, SortBy.SIZE).size());
            }
        }

        @Test
        void rejectsNonPositiveTopN(@TempDir Path tmp) throws IOException, SQLException {
            try (HeapView view = openIndex(tmp)) {
                assertThrows(IllegalArgumentException.class,
                        () -> ClassHistogramAnalyzer.analyze(view, 0, SortBy.SIZE));
            }
        }

        @Test
        void rejectsNullSortBy(@TempDir Path tmp) throws IOException, SQLException {
            try (HeapView view = openIndex(tmp)) {
                assertThrows(IllegalArgumentException.class,
                        () -> ClassHistogramAnalyzer.analyze(view, 10, null));
            }
        }
    }

    @Nested
    class GcRoots {

        @Test
        void groupsRootsByDisplayName(@TempDir Path tmp) throws IOException, SQLException {
            try (HeapView view = openIndex(tmp)) {
                GCRootSummary summary = GcRootAnalyzer.analyze(view);
                assertEquals(5, summary.totalRoots());
                assertEquals(Long.valueOf(2), summary.rootsByType().get("Sticky class"));
                assertEquals(Long.valueOf(2), summary.rootsByType().get("Java frame"));
                assertEquals(Long.valueOf(1), summary.rootsByType().get("Thread object"));
            }
        }

        @Test
        void returnsEmptyForIndexWithoutGcRoots(@TempDir Path tmp) throws IOException, SQLException {
            Path hprof = SyntheticHprof.create("1.0.2", 8, 0L)
                    .string(0xA001L, "X")
                    .writeTo(tmp, "no-roots.hprof");
            Path indexDb = HeapDumpIndexPaths.indexFor(hprof);
            try (HprofMappedFile file = HprofMappedFile.open(hprof)) {
                HprofIndex.build(file, indexDb, FIXED_CLOCK);
            }
            try (HeapView view = HeapView.open(indexDb)) {
                assertEquals(GCRootSummary.EMPTY, GcRootAnalyzer.analyze(view));
            }
        }
    }
}
