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

import cafe.jeffrey.profile.heapdump.model.DominatorNode;
import cafe.jeffrey.profile.heapdump.model.DominatorTreeResponse;
import cafe.jeffrey.profile.heapdump.parser.DominatorTreeBuilder;
import cafe.jeffrey.profile.heapdump.parser.HeapDumpIndexPaths;
import cafe.jeffrey.profile.heapdump.parser.HeapView;
import cafe.jeffrey.profile.heapdump.parser.HprofIndex;
import cafe.jeffrey.profile.heapdump.parser.HprofMappedFile;
import cafe.jeffrey.profile.heapdump.parser.HprofTag;
import cafe.jeffrey.profile.heapdump.parser.SyntheticHprof;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DominatorTreeAnalyzerTest {

    private static final Clock CLOCK =
            Clock.fixed(Instant.ofEpochMilli(1L), ZoneOffset.UTC);

    @Test
    void topLevelShowsRootedInstancesWithRootKindLabel(@TempDir Path tmp) throws IOException, SQLException {
        int idSize = 8;
        long classId = 0xC001L;
        long a = 0x100L, b = 0x200L, c = 0x300L;

        // V → A (sticky-class root) → B; V → C (java-frame root)
        Path hprof = SyntheticHprof.create("1.0.2", idSize, 0L)
                .string(0xA001L, "Holder")
                .string(0xA002L, "next")
                .loadClass(1, classId, 0, 0xA001L)
                .heapDumpSegment(seg -> seg
                        .topLevelObjectClassDump(classId, 0xA002L)
                        .gcRoot(HprofTag.Sub.ROOT_STICKY_CLASS, a)
                        .gcRootJavaFrame(c, 1, 1)
                        .instanceDump(a, classId, idBytes(b, idSize))
                        .instanceDump(b, classId, idBytes(0L, idSize))
                        .instanceDump(c, classId, idBytes(0L, idSize)))
                .heapDumpEnd()
                .writeTo(tmp, "tree.hprof");

        Path indexDb = HeapDumpIndexPaths.indexFor(hprof);
        try (HprofMappedFile file = HprofMappedFile.open(hprof)) {
            HprofIndex.build(file, indexDb, CLOCK);
        }
        DominatorTreeBuilder.build(indexDb);

        try (HeapView view = HeapView.open(indexDb)) {
            DominatorTreeResponse response = DominatorTreeAnalyzer.children(view, 0L);
            // Top-level: A and C are directly dominated by virtual root.
            assertEquals(2, response.nodes().size());
            // Sorted by retained desc — A retains itself + B; C only itself. So A first.
            DominatorNode top = response.nodes().get(0);
            assertEquals(a, top.objectId());
            assertEquals("Sticky class", top.gcRootKind());
            assertTrue(top.hasChildren(), "A dominates B");

            DominatorNode second = response.nodes().get(1);
            assertEquals(c, second.objectId());
            assertEquals("Java frame", second.gcRootKind());
            assertFalse(second.hasChildren());
        }
    }

    @Test
    void childrenOfNonRootShowDirectDominees(@TempDir Path tmp) throws IOException, SQLException {
        int idSize = 8;
        long classId = 0xC001L;
        long parent = 0x100L, child1 = 0x200L, child2 = 0x300L;

        // V → parent → child1, child2
        Path hprof = SyntheticHprof.create("1.0.2", idSize, 0L)
                .string(0xA001L, "Holder")
                .string(0xA002L, "next")
                .loadClass(1, classId, 0, 0xA001L)
                .heapDumpSegment(seg -> seg
                        .topLevelObjectClassDump(classId, 0xA002L)
                        .gcRoot(HprofTag.Sub.ROOT_STICKY_CLASS, parent)
                        // Need a class with two object fields to point at both children.
                        // Reuse a single-OBJECT-field class and chain: parent → child1 → child2 isn't
                        // what we want. Instead emit object-array elements to fan out.
                        .objectArrayDump(0x999L, classId, new long[]{child1, child2})
                        .instanceDump(parent, classId, idBytes(0x999L, idSize))
                        .instanceDump(child1, classId, idBytes(0L, idSize))
                        .instanceDump(child2, classId, idBytes(0L, idSize)))
                .heapDumpEnd()
                .writeTo(tmp, "fan.hprof");
        Path indexDb = HeapDumpIndexPaths.indexFor(hprof);
        try (HprofMappedFile file = HprofMappedFile.open(hprof)) {
            HprofIndex.build(file, indexDb, CLOCK);
        }
        DominatorTreeBuilder.build(indexDb);

        try (HeapView view = HeapView.open(indexDb)) {
            // children(parent) → the array (which dominates both leaves)
            DominatorTreeResponse direct = DominatorTreeAnalyzer.children(view, parent);
            assertEquals(1, direct.nodes().size());
            assertEquals(0x999L, direct.nodes().get(0).objectId(),
                    "parent dominates the array which in turn dominates both children");

            DominatorTreeResponse leaves = DominatorTreeAnalyzer.children(view, 0x999L);
            assertEquals(2, leaves.nodes().size());
            // gcRootKind is null for non-root nodes
            assertNull(leaves.nodes().get(0).gcRootKind());
        }
    }

    @Test
    void hasMoreFlagSetWhenLimitTriggers(@TempDir Path tmp) throws IOException, SQLException {
        int idSize = 8;
        long classId = 0xC001L;
        long arrayId = 0x9000L;

        long[] children = new long[20];
        for (int i = 0; i < children.length; i++) {
            children[i] = 0x1000L + i;
        }

        Path hprof = SyntheticHprof.create("1.0.2", idSize, 0L)
                .string(0xA001L, "Holder")
                .string(0xA002L, "next")
                .loadClass(1, classId, 0, 0xA001L)
                .heapDumpSegment(seg -> {
                    seg.topLevelObjectClassDump(classId, 0xA002L);
                    seg.gcRoot(HprofTag.Sub.ROOT_STICKY_CLASS, arrayId);
                    seg.objectArrayDump(arrayId, classId, children);
                    for (long id : children) {
                        seg.instanceDump(id, classId, idBytes(0L, idSize));
                    }
                })
                .heapDumpEnd()
                .writeTo(tmp, "hasmore.hprof");
        Path indexDb = HeapDumpIndexPaths.indexFor(hprof);
        try (HprofMappedFile file = HprofMappedFile.open(hprof)) {
            HprofIndex.build(file, indexDb, CLOCK);
        }
        DominatorTreeBuilder.build(indexDb);

        try (HeapView view = HeapView.open(indexDb)) {
            DominatorTreeResponse capped = DominatorTreeAnalyzer.children(view, arrayId, 5);
            assertEquals(5, capped.nodes().size());
            assertTrue(capped.hasMore());

            DominatorTreeResponse all = DominatorTreeAnalyzer.children(view, arrayId, 100);
            assertEquals(20, all.nodes().size());
            assertFalse(all.hasMore());
            assertNotNull(all);
        }
    }

    @Test
    void retainedPercentIsRelativeToParentNotTotalHeap(@TempDir Path tmp) throws IOException, SQLException {
        // Two independent GC-root subtrees:
        //   ROOT_A → arrayA → leafA1, leafA2     (one heavy subtree we will drill into)
        //   ROOT_B → leafB                       (a second root that inflates total_shallow_size)
        // When we call children(ROOT_A), arrayA dominates both of A's leaves and accounts for
        // almost all of ROOT_A's retained size. Its retainedPercent must reflect that —
        // close to 100% of the parent's retained size, NOT diluted by ROOT_B's mass in the heap.
        int idSize = 8;
        long classId = 0xC001L;
        long rootA = 0x100L, arrayA = 0x101L, leafA1 = 0x102L, leafA2 = 0x103L;
        long rootB = 0x200L, leafB = 0x201L;

        Path hprof = SyntheticHprof.create("1.0.2", idSize, 0L)
                .string(0xA001L, "Holder")
                .string(0xA002L, "next")
                .loadClass(1, classId, 0, 0xA001L)
                .heapDumpSegment(seg -> seg
                        .topLevelObjectClassDump(classId, 0xA002L)
                        // Subtree A: ROOT_A → arrayA → leafA1, leafA2
                        .gcRoot(HprofTag.Sub.ROOT_STICKY_CLASS, rootA)
                        .objectArrayDump(arrayA, classId, new long[]{leafA1, leafA2})
                        .instanceDump(rootA, classId, idBytes(arrayA, idSize))
                        .instanceDump(leafA1, classId, idBytes(0L, idSize))
                        .instanceDump(leafA2, classId, idBytes(0L, idSize))
                        // Subtree B: ROOT_B → leafB (independent — inflates total heap size only)
                        .gcRoot(HprofTag.Sub.ROOT_STICKY_CLASS, rootB)
                        .instanceDump(rootB, classId, idBytes(leafB, idSize))
                        .instanceDump(leafB, classId, idBytes(0L, idSize)))
                .heapDumpEnd()
                .writeTo(tmp, "parent-percent.hprof");
        Path indexDb = HeapDumpIndexPaths.indexFor(hprof);
        try (HprofMappedFile file = HprofMappedFile.open(hprof)) {
            HprofIndex.build(file, indexDb, CLOCK);
        }
        DominatorTreeBuilder.build(indexDb);

        try (HeapView view = HeapView.open(indexDb)) {
            DominatorTreeResponse children = DominatorTreeAnalyzer.children(view, rootA);
            assertEquals(1, children.nodes().size(), "rootA dominates arrayA only");
            DominatorNode arrayNode = children.nodes().get(0);
            assertEquals(arrayA, arrayNode.objectId());

            // arrayA holds nearly all of rootA's retained size (just one extra instance header for rootA itself).
            // The percentage must be > 50% — anything less means the formula is dividing by total heap
            // (the old bug: subtree B inflates total_shallow_size and dilutes the percent below 50%).
            assertTrue(arrayNode.retainedPercent() > 50.0,
                    "arrayA should be > 50% of its parent's retained size; got "
                            + arrayNode.retainedPercent() + "% — likely divided by total heap, not parent");
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

    @SuppressWarnings("unused")
    private static List<Long> ignoreMe() {
        return List.of();
    }
}
