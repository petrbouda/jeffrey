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

import cafe.jeffrey.profile.heapdump.model.CollectionAnalysisReport;
import cafe.jeffrey.profile.heapdump.model.CollectionStats;
import cafe.jeffrey.profile.heapdump.parser.HeapDumpIndexPaths;
import cafe.jeffrey.profile.heapdump.parser.HeapView;
import cafe.jeffrey.profile.heapdump.parser.HprofIndex;
import cafe.jeffrey.profile.heapdump.parser.HprofMappedFile;
import cafe.jeffrey.profile.heapdump.parser.HprofTag;
import cafe.jeffrey.profile.heapdump.parser.SyntheticHprof;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CollectionAnalyzerTest {

    private static final Clock CLOCK =
            Clock.fixed(Instant.ofEpochMilli(1L), ZoneOffset.UTC);
    private static final int ID_SIZE = 8;
    // ArrayList instance bytes: size(4) + elementData(8) = 12
    private static final int ARRAYLIST_INSTANCE_BYTES = 4 + ID_SIZE;

    @Test
    void detectsArrayListSizeAndCapacity(@TempDir Path tmp) throws IOException, SQLException {
        long arraylistClass = 0xC001L;
        long elementClass = 0xC002L;
        long al1 = 0x1001L;
        long al2 = 0x1002L;
        long backingArray1 = 0x9001L;
        long backingArray2 = 0x9002L;
        long elem = 0x2001L;

        // ArrayList #1: size=2, capacity=10 → wasted 8 slots
        // ArrayList #2: size=0, capacity=10 → empty, wasted 10 slots
        long[] arr1 = new long[10];
        arr1[0] = elem;
        arr1[1] = elem;
        long[] arr2 = new long[10]; // all zeros = empty

        Path hprof = SyntheticHprof.create("1.0.2", ID_SIZE, 0L)
                .string(0xA001L, "java.util.ArrayList")
                .string(0xA002L, "Element")
                .string(0xA003L, "size")
                .string(0xA004L, "elementData")
                .loadClass(1, arraylistClass, 0, 0xA001L)
                .loadClass(2, elementClass, 0, 0xA002L)
                .heapDumpSegment(seg -> seg
                        .classDumpWithFields(arraylistClass, 0L, 0L, ARRAYLIST_INSTANCE_BYTES,
                                new SyntheticHprof.SubBuilder.FieldSpec(0xA003L, HprofTag.BasicType.INT),
                                new SyntheticHprof.SubBuilder.FieldSpec(0xA004L, HprofTag.BasicType.OBJECT))
                        .topLevelObjectClassDump(elementClass, 0xA003L)
                        .objectArrayDump(backingArray1, elementClass, arr1)
                        .objectArrayDump(backingArray2, elementClass, arr2)
                        .instanceDump(elem, elementClass, idBytes(0L, ID_SIZE))
                        .instanceDump(al1, arraylistClass, arrayListFields(2, backingArray1))
                        .instanceDump(al2, arraylistClass, arrayListFields(0, backingArray2)))
                .heapDumpEnd()
                .writeTo(tmp, "arraylist.hprof");

        Path indexDb = HeapDumpIndexPaths.indexFor(hprof);
        try (HprofMappedFile file = HprofMappedFile.open(hprof)) {
            HprofIndex.build(file, indexDb, CLOCK);
        }
        try (HprofMappedFile file = HprofMappedFile.open(hprof);
             HeapView view = HeapView.open(indexDb, file)) {
            CollectionAnalysisReport report = CollectionAnalyzer.analyze(view);
            assertEquals(2, report.totalCollections());
            assertEquals(1, report.totalEmptyCount());
            assertTrue(report.totalWastedBytes() > 0);

            CollectionStats arrayListStats = report.byType().stream()
                    .filter(s -> "java.util.ArrayList".equals(s.collectionType()))
                    .findFirst()
                    .orElseThrow();
            assertEquals(2, arrayListStats.totalCount());
            assertEquals(1, arrayListStats.emptyCount());
            // (10-2 + 10-0) * 8 bytes = 144
            assertEquals(144L, arrayListStats.totalWastedBytes());
        }
    }

    @Test
    void unsupportedCollectionsAbsentFromReport(@TempDir Path tmp) throws IOException, SQLException {
        long linkedListClass = 0xC001L;

        Path hprof = SyntheticHprof.create("1.0.2", ID_SIZE, 0L)
                .string(0xA001L, "java.util.LinkedList")
                .string(0xA002L, "f")
                .loadClass(1, linkedListClass, 0, 0xA001L)
                .heapDumpSegment(seg -> seg
                        .simpleClassDump(linkedListClass, 0L, 0L, 4, 0xA002L)
                        .instanceDump(0x100L, linkedListClass, new byte[]{0, 0, 0, 5}))
                .heapDumpEnd()
                .writeTo(tmp, "linkedlist.hprof");
        Path indexDb = HeapDumpIndexPaths.indexFor(hprof);
        try (HprofMappedFile file = HprofMappedFile.open(hprof)) {
            HprofIndex.build(file, indexDb, CLOCK);
        }
        try (HprofMappedFile file = HprofMappedFile.open(hprof);
             HeapView view = HeapView.open(indexDb, file)) {
            CollectionAnalysisReport report = CollectionAnalyzer.analyze(view);
            assertEquals(0, report.totalCollections(),
                    "LinkedList not in the supported shape list — counted as zero");
        }
    }

    private static byte[] arrayListFields(int size, long elementDataRef) {
        try {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            DataOutputStream d = new DataOutputStream(b);
            d.writeInt(size);
            d.writeLong(elementDataRef);
            return b.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
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
