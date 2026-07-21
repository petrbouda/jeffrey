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
import cafe.jeffrey.profile.heapdump.persistence.HeapDumpIndexPaths;
import cafe.jeffrey.profile.heapdump.view.HeapView;
import cafe.jeffrey.profile.heapdump.parser.HprofIndex;
import cafe.jeffrey.profile.heapdump.parser.HprofMappedFile;
import cafe.jeffrey.profile.heapdump.view.HprofTag;
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
    void classWithoutRecognizedSizeFieldSkipped(@TempDir Path tmp) throws IOException, SQLException {
        long linkedListClass = 0xC001L;

        // A "LinkedList" whose only int field is named "f" — the size-only shape
        // requires a field literally named "size", so the class is skipped.
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
                    "class layout not matching any shape — counted as zero");
        }
    }

    @Test
    void linkedListCountedWithoutCapacityMetrics(@TempDir Path tmp) throws IOException, SQLException {
        long linkedListClass = 0xC001L;

        Path hprof = SyntheticHprof.create("1.0.2", ID_SIZE, 0L)
                .string(0xA001L, "java.util.LinkedList")
                .string(0xA002L, "size")
                .loadClass(1, linkedListClass, 0, 0xA001L)
                .heapDumpSegment(seg -> seg
                        .classDumpWithFields(linkedListClass, 0L, 0L, 4,
                                new SyntheticHprof.SubBuilder.FieldSpec(0xA002L, HprofTag.BasicType.INT))
                        .instanceDump(0x100L, linkedListClass, intBytes(5))
                        .instanceDump(0x101L, linkedListClass, intBytes(0)))
                .heapDumpEnd()
                .writeTo(tmp, "linkedlist-size.hprof");
        Path indexDb = HeapDumpIndexPaths.indexFor(hprof);
        try (HprofMappedFile file = HprofMappedFile.open(hprof)) {
            HprofIndex.build(file, indexDb, CLOCK);
        }
        try (HprofMappedFile file = HprofMappedFile.open(hprof);
             HeapView view = HeapView.open(indexDb, file)) {
            CollectionAnalysisReport report = CollectionAnalyzer.analyze(view);
            assertEquals(2, report.totalCollections());
            assertEquals(1, report.totalEmptyCount());
            assertEquals(0L, report.totalWastedBytes(), "no capacity concept, no waste");

            CollectionStats stats = statsFor(report, "java.util.LinkedList");
            assertEquals(2, stats.totalCount());
            assertEquals(1, stats.emptyCount());
        }
    }

    @Test
    void treeMapCountedViaSizeOnlyShape(@TempDir Path tmp) throws IOException, SQLException {
        long treeMapClass = 0xC001L;

        Path hprof = SyntheticHprof.create("1.0.2", ID_SIZE, 0L)
                .string(0xA001L, "java.util.TreeMap")
                .string(0xA002L, "size")
                .loadClass(1, treeMapClass, 0, 0xA001L)
                .heapDumpSegment(seg -> seg
                        .classDumpWithFields(treeMapClass, 0L, 0L, 4,
                                new SyntheticHprof.SubBuilder.FieldSpec(0xA002L, HprofTag.BasicType.INT))
                        .instanceDump(0x100L, treeMapClass, intBytes(7)))
                .heapDumpEnd()
                .writeTo(tmp, "treemap.hprof");
        Path indexDb = HeapDumpIndexPaths.indexFor(hprof);
        try (HprofMappedFile file = HprofMappedFile.open(hprof)) {
            HprofIndex.build(file, indexDb, CLOCK);
        }
        try (HprofMappedFile file = HprofMappedFile.open(hprof);
             HeapView view = HeapView.open(indexDb, file)) {
            CollectionAnalysisReport report = CollectionAnalyzer.analyze(view);
            CollectionStats stats = statsFor(report, "java.util.TreeMap");
            assertEquals(1, stats.totalCount());
            assertEquals(0, stats.emptyCount());
        }
    }

    @Test
    void vectorElementCountFieldDetected(@TempDir Path tmp) throws IOException, SQLException {
        long vectorClass = 0xC001L;
        long elementClass = 0xC002L;
        long backingArray = 0x9001L;

        long[] arr = new long[4];
        arr[0] = 0x2001L;

        Path hprof = SyntheticHprof.create("1.0.2", ID_SIZE, 0L)
                .string(0xA001L, "java.util.Vector")
                .string(0xA002L, "Element")
                .string(0xA003L, "elementCount")
                .string(0xA004L, "elementData")
                .loadClass(1, vectorClass, 0, 0xA001L)
                .loadClass(2, elementClass, 0, 0xA002L)
                .heapDumpSegment(seg -> seg
                        .classDumpWithFields(vectorClass, 0L, 0L, 4 + ID_SIZE,
                                new SyntheticHprof.SubBuilder.FieldSpec(0xA003L, HprofTag.BasicType.INT),
                                new SyntheticHprof.SubBuilder.FieldSpec(0xA004L, HprofTag.BasicType.OBJECT))
                        .topLevelObjectClassDump(elementClass, 0xA003L)
                        .objectArrayDump(backingArray, elementClass, arr)
                        .instanceDump(0x2001L, elementClass, idBytes(0L, ID_SIZE))
                        .instanceDump(0x100L, vectorClass, arrayListFields(1, backingArray)))
                .heapDumpEnd()
                .writeTo(tmp, "vector.hprof");
        Path indexDb = HeapDumpIndexPaths.indexFor(hprof);
        try (HprofMappedFile file = HprofMappedFile.open(hprof)) {
            HprofIndex.build(file, indexDb, CLOCK);
        }
        try (HprofMappedFile file = HprofMappedFile.open(hprof);
             HeapView view = HeapView.open(indexDb, file)) {
            CollectionAnalysisReport report = CollectionAnalyzer.analyze(view);
            CollectionStats stats = statsFor(report, "java.util.Vector");
            assertEquals(1, stats.totalCount());
            assertEquals(0, stats.emptyCount());
            // (4 - 1) * 8 bytes wasted
            assertEquals(24L, stats.totalWastedBytes());
        }
    }

    @Test
    void arrayDequeSizeComputedFromHeadTail(@TempDir Path tmp) throws IOException, SQLException {
        long dequeClass = 0xC001L;
        long elementClass = 0xC002L;
        long array1 = 0x9001L;
        long array2 = 0x9002L;

        Path hprof = SyntheticHprof.create("1.0.2", ID_SIZE, 0L)
                .string(0xA001L, "java.util.ArrayDeque")
                .string(0xA002L, "Element")
                .string(0xA003L, "head")
                .string(0xA004L, "tail")
                .string(0xA005L, "elements")
                .loadClass(1, dequeClass, 0, 0xA001L)
                .loadClass(2, elementClass, 0, 0xA002L)
                .heapDumpSegment(seg -> seg
                        .classDumpWithFields(dequeClass, 0L, 0L, 4 + 4 + ID_SIZE,
                                new SyntheticHprof.SubBuilder.FieldSpec(0xA003L, HprofTag.BasicType.INT),
                                new SyntheticHprof.SubBuilder.FieldSpec(0xA004L, HprofTag.BasicType.INT),
                                new SyntheticHprof.SubBuilder.FieldSpec(0xA005L, HprofTag.BasicType.OBJECT))
                        .topLevelObjectClassDump(elementClass, 0xA003L)
                        .objectArrayDump(array1, elementClass, new long[8])
                        .objectArrayDump(array2, elementClass, new long[8])
                        // head=2, tail=5 → size 3
                        .instanceDump(0x100L, dequeClass, dequeFields(2, 5, array1))
                        // wrapped: head=6, tail=2 → size 4
                        .instanceDump(0x101L, dequeClass, dequeFields(6, 2, array2)))
                .heapDumpEnd()
                .writeTo(tmp, "deque.hprof");
        Path indexDb = HeapDumpIndexPaths.indexFor(hprof);
        try (HprofMappedFile file = HprofMappedFile.open(hprof)) {
            HprofIndex.build(file, indexDb, CLOCK);
        }
        try (HprofMappedFile file = HprofMappedFile.open(hprof);
             HeapView view = HeapView.open(indexDb, file)) {
            CollectionAnalysisReport report = CollectionAnalyzer.analyze(view);
            CollectionStats stats = statsFor(report, "java.util.ArrayDeque");
            assertEquals(2, stats.totalCount());
            assertEquals(0, stats.emptyCount());
            // deque #1 wastes 8-3=5 slots, deque #2 wastes 8-4=4 slots → 9 * 8 bytes
            assertEquals(72L, stats.totalWastedBytes());
        }
    }

    @Test
    void concurrentHashMapSizeReadFromBaseCount(@TempDir Path tmp) throws IOException, SQLException {
        long chmClass = 0xC001L;
        long nodeClass = 0xC002L;
        long tableArray = 0x9001L;

        Path hprof = SyntheticHprof.create("1.0.2", ID_SIZE, 0L)
                .string(0xA001L, "java.util.concurrent.ConcurrentHashMap")
                .string(0xA002L, "Node")
                .string(0xA003L, "baseCount")
                .string(0xA004L, "table")
                .loadClass(1, chmClass, 0, 0xA001L)
                .loadClass(2, nodeClass, 0, 0xA002L)
                .heapDumpSegment(seg -> seg
                        .classDumpWithFields(chmClass, 0L, 0L, 8 + ID_SIZE,
                                new SyntheticHprof.SubBuilder.FieldSpec(0xA003L, HprofTag.BasicType.LONG),
                                new SyntheticHprof.SubBuilder.FieldSpec(0xA004L, HprofTag.BasicType.OBJECT))
                        .topLevelObjectClassDump(nodeClass, 0xA003L)
                        .objectArrayDump(tableArray, nodeClass, new long[16])
                        .instanceDump(0x100L, chmClass, chmFields(3L, tableArray)))
                .heapDumpEnd()
                .writeTo(tmp, "chm.hprof");
        Path indexDb = HeapDumpIndexPaths.indexFor(hprof);
        try (HprofMappedFile file = HprofMappedFile.open(hprof)) {
            HprofIndex.build(file, indexDb, CLOCK);
        }
        try (HprofMappedFile file = HprofMappedFile.open(hprof);
             HeapView view = HeapView.open(indexDb, file)) {
            CollectionAnalysisReport report = CollectionAnalyzer.analyze(view);
            CollectionStats stats = statsFor(report, "java.util.concurrent.ConcurrentHashMap");
            assertEquals(1, stats.totalCount());
            assertEquals(0, stats.emptyCount());
            // (16 - 3) * 8 bytes wasted
            assertEquals(104L, stats.totalWastedBytes());
        }
    }

    @Test
    void hashSetSizeReadThroughBackingMap(@TempDir Path tmp) throws IOException, SQLException {
        long hashSetClass = 0xC001L;
        long hashMapClass = 0xC002L;
        long nodeClass = 0xC003L;
        long mapInstance = 0x200L;
        long tableArray = 0x9001L;

        Path hprof = SyntheticHprof.create("1.0.2", ID_SIZE, 0L)
                .string(0xA001L, "java.util.HashSet")
                .string(0xA002L, "java.util.HashMap")
                .string(0xA003L, "Node")
                .string(0xA004L, "map")
                .string(0xA005L, "size")
                .string(0xA006L, "table")
                .loadClass(1, hashSetClass, 0, 0xA001L)
                .loadClass(2, hashMapClass, 0, 0xA002L)
                .loadClass(3, nodeClass, 0, 0xA003L)
                .heapDumpSegment(seg -> seg
                        .classDumpWithFields(hashSetClass, 0L, 0L, ID_SIZE,
                                new SyntheticHprof.SubBuilder.FieldSpec(0xA004L, HprofTag.BasicType.OBJECT))
                        .classDumpWithFields(hashMapClass, 0L, 0L, 4 + ID_SIZE,
                                new SyntheticHprof.SubBuilder.FieldSpec(0xA005L, HprofTag.BasicType.INT),
                                new SyntheticHprof.SubBuilder.FieldSpec(0xA006L, HprofTag.BasicType.OBJECT))
                        .topLevelObjectClassDump(nodeClass, 0xA005L)
                        .objectArrayDump(tableArray, nodeClass, new long[16])
                        .instanceDump(0x100L, hashSetClass, idBytes(mapInstance, ID_SIZE))
                        .instanceDump(mapInstance, hashMapClass, arrayListFields(7, tableArray)))
                .heapDumpEnd()
                .writeTo(tmp, "hashset.hprof");
        Path indexDb = HeapDumpIndexPaths.indexFor(hprof);
        try (HprofMappedFile file = HprofMappedFile.open(hprof)) {
            HprofIndex.build(file, indexDb, CLOCK);
        }
        try (HprofMappedFile file = HprofMappedFile.open(hprof);
             HeapView view = HeapView.open(indexDb, file)) {
            CollectionAnalysisReport report = CollectionAnalyzer.analyze(view);

            CollectionStats setStats = statsFor(report, "java.util.HashSet");
            assertEquals(1, setStats.totalCount());
            assertEquals(0, setStats.emptyCount());

            // The backing HashMap instance is independently counted by the
            // array-backed HashMap shape.
            CollectionStats mapStats = statsFor(report, "java.util.HashMap");
            assertEquals(1, mapStats.totalCount());
        }
    }

    private static CollectionStats statsFor(CollectionAnalysisReport report, String type) {
        return report.byType().stream()
                .filter(s -> type.equals(s.collectionType()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("missing stats for " + type));
    }

    private static byte[] intBytes(int value) {
        try {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            DataOutputStream d = new DataOutputStream(b);
            d.writeInt(value);
            return b.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static byte[] dequeFields(int head, int tail, long elementsRef) {
        try {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            DataOutputStream d = new DataOutputStream(b);
            d.writeInt(head);
            d.writeInt(tail);
            d.writeLong(elementsRef);
            return b.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static byte[] chmFields(long baseCount, long tableRef) {
        try {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            DataOutputStream d = new DataOutputStream(b);
            d.writeLong(baseCount);
            d.writeLong(tableRef);
            return b.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
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
