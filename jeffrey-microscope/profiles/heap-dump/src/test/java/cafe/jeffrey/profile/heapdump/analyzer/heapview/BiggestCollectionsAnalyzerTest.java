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
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import cafe.jeffrey.profile.heapdump.model.BiggestCollectionEntry;
import cafe.jeffrey.profile.heapdump.model.BiggestCollectionsReport;
import cafe.jeffrey.profile.heapdump.parser.DominatorTreeBuilder;
import cafe.jeffrey.profile.heapdump.parser.HeapDumpIndexPaths;
import cafe.jeffrey.profile.heapdump.parser.HeapView;
import cafe.jeffrey.profile.heapdump.parser.HprofIndex;
import cafe.jeffrey.profile.heapdump.parser.HprofMappedFile;
import cafe.jeffrey.profile.heapdump.parser.HprofTag;
import cafe.jeffrey.profile.heapdump.parser.SyntheticHprof;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BiggestCollectionsAnalyzerTest {

    private static final Clock CLOCK =
            Clock.fixed(Instant.ofEpochMilli(1L), ZoneOffset.UTC);
    private static final int ID_SIZE = 8;
    /** ArrayList instance layout: size(int, 4) + elementData(oop, 8) = 12 bytes. */
    private static final int ARRAYLIST_INSTANCE_BYTES = 4 + ID_SIZE;

    /**
     * Regression for the bug where the analyzer SQL referenced
     * {@code rs.retained_size} — the actual column name is {@code bytes}.
     * The query must bind, the join with {@code retained_size} must return
     * a positive value (populated by {@link DominatorTreeBuilder}), and both
     * ranking lists must surface the ArrayList in size order.
     */
    @Test
    void rankArrayListsByCountAndRetainedSize(@TempDir Path tmp) throws IOException, SQLException {
        long arraylistClass = 0xC001L;
        long elementClass = 0xC002L;
        long bigList = 0x1001L;
        long smallList = 0x1002L;
        long bigArr = 0x9001L;
        long smallArr = 0x9002L;
        long bigElem = 0x2001L;
        long smallElem = 0x2002L;

        // bigList: size=5, capacity=10, retains 5 distinct element instances.
        // smallList: size=1, capacity=10, retains 1 element instance.
        long[] bigArrSlots = new long[10];
        for (int i = 0; i < 5; i++) {
            bigArrSlots[i] = bigElem;
        }
        long[] smallArrSlots = new long[10];
        smallArrSlots[0] = smallElem;

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
                        // Roots so the dominator tree is populated for the two ArrayLists.
                        .gcRoot(HprofTag.Sub.ROOT_STICKY_CLASS, bigList)
                        .gcRoot(HprofTag.Sub.ROOT_STICKY_CLASS, smallList)
                        .objectArrayDump(bigArr, elementClass, bigArrSlots)
                        .objectArrayDump(smallArr, elementClass, smallArrSlots)
                        .instanceDump(bigElem, elementClass, idBytes(0L))
                        .instanceDump(smallElem, elementClass, idBytes(0L))
                        .instanceDump(bigList, arraylistClass, arrayListFields(5, bigArr))
                        .instanceDump(smallList, arraylistClass, arrayListFields(1, smallArr)))
                .heapDumpEnd()
                .writeTo(tmp, "biggest-collections.hprof");

        Path indexDb = HeapDumpIndexPaths.indexFor(hprof);
        try (HprofMappedFile file = HprofMappedFile.open(hprof)) {
            HprofIndex.build(file, indexDb, CLOCK);
        }
        DominatorTreeBuilder.build(indexDb);

        try (HprofMappedFile file = HprofMappedFile.open(hprof);
             HeapView view = HeapView.open(indexDb, file)) {
            BiggestCollectionsReport report = BiggestCollectionsAnalyzer.analyze(view, 50);

            assertEquals(2, report.totalCollectionsAnalyzed());

            List<BiggestCollectionEntry> byCount = report.byElementCount();
            assertEquals(2, byCount.size());
            assertEquals(bigList, byCount.get(0).objectId());
            assertEquals(5, byCount.get(0).elementCount());
            assertEquals(10, byCount.get(0).capacity());
            assertEquals("java.util.ArrayList", byCount.get(0).className());
            assertEquals(smallList, byCount.get(1).objectId());
            assertEquals(1, byCount.get(1).elementCount());

            // The retained-size column must come back populated — that's the
            // exact path the buggy SQL referenced (rs.retained_size → rs.bytes).
            List<BiggestCollectionEntry> byRetained = report.byRetainedSize();
            assertEquals(2, byRetained.size());
            assertTrue(byRetained.get(0).retainedSize() > 0,
                    "top entry retainedSize must be populated from retained_size.bytes");
            assertTrue(byRetained.get(0).retainedSize() >= byRetained.get(1).retainedSize(),
                    "byRetainedSize must be sorted descending");
            // bigList dominates its backing array plus 5 element references → more
            // retained than smallList which dominates only its array + 1 element.
            assertEquals(bigList, byRetained.get(0).objectId());
        }
    }

    /**
     * topN must cap each result list independently regardless of how many
     * collection instances the heap contains.
     */
    @Test
    void honorsTopNCap(@TempDir Path tmp) throws IOException, SQLException {
        long arraylistClass = 0xC001L;
        long elementClass = 0xC002L;

        SyntheticHprof builder = SyntheticHprof.create("1.0.2", ID_SIZE, 0L)
                .string(0xA001L, "java.util.ArrayList")
                .string(0xA002L, "Element")
                .string(0xA003L, "size")
                .string(0xA004L, "elementData")
                .loadClass(1, arraylistClass, 0, 0xA001L)
                .loadClass(2, elementClass, 0, 0xA002L);

        Path hprof = builder.heapDumpSegment(seg -> {
                    seg.classDumpWithFields(arraylistClass, 0L, 0L, ARRAYLIST_INSTANCE_BYTES,
                                    new SyntheticHprof.SubBuilder.FieldSpec(0xA003L, HprofTag.BasicType.INT),
                                    new SyntheticHprof.SubBuilder.FieldSpec(0xA004L, HprofTag.BasicType.OBJECT))
                            .topLevelObjectClassDump(elementClass, 0xA003L);
                    // 4 ArrayLists, sizes 1..4 — topN=2 should return only the two biggest.
                    for (int i = 1; i <= 4; i++) {
                        long arrId = 0x9000L + i;
                        long listId = 0x1000L + i;
                        long[] slots = new long[10];
                        seg.gcRoot(HprofTag.Sub.ROOT_STICKY_CLASS, listId)
                                .objectArrayDump(arrId, elementClass, slots)
                                .instanceDump(listId, arraylistClass, arrayListFields(i, arrId));
                    }
                })
                .heapDumpEnd()
                .writeTo(tmp, "biggest-collections-cap.hprof");

        Path indexDb = HeapDumpIndexPaths.indexFor(hprof);
        try (HprofMappedFile file = HprofMappedFile.open(hprof)) {
            HprofIndex.build(file, indexDb, CLOCK);
        }
        DominatorTreeBuilder.build(indexDb);

        try (HprofMappedFile file = HprofMappedFile.open(hprof);
             HeapView view = HeapView.open(indexDb, file)) {
            BiggestCollectionsReport report = BiggestCollectionsAnalyzer.analyze(view, 2);

            assertEquals(4, report.totalCollectionsAnalyzed());
            assertEquals(2, report.byElementCount().size());
            assertEquals(2, report.byRetainedSize().size());
            // Largest list (size=4) tops the count ranking.
            assertEquals(4, report.byElementCount().get(0).elementCount());
            assertEquals(3, report.byElementCount().get(1).elementCount());
            assertNotNull(report.byRetainedSize().get(0).className());
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

    private static byte[] idBytes(long id) {
        try {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            DataOutputStream d = new DataOutputStream(b);
            d.writeLong(id);
            return b.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
