/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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

import cafe.jeffrey.profile.heapdump.model.DominatedClassEntry;
import cafe.jeffrey.profile.heapdump.model.GCRootPath;
import cafe.jeffrey.profile.heapdump.model.LeakSuspect;
import cafe.jeffrey.profile.heapdump.model.LeakSuspectsReport;
import cafe.jeffrey.profile.heapdump.persistence.DominatorTreeBuilder;
import cafe.jeffrey.profile.heapdump.persistence.HeapDumpIndexPaths;
import cafe.jeffrey.profile.heapdump.view.HeapView;
import cafe.jeffrey.profile.heapdump.parser.HprofIndex;
import cafe.jeffrey.profile.heapdump.parser.HprofMappedFile;
import cafe.jeffrey.profile.heapdump.view.HprofTag;
import cafe.jeffrey.profile.heapdump.parser.SyntheticHprof;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
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
            // B and C clear 50% too, but A dominates them: one chain is one suspect.
            assertEquals(1, report.suspects().size(), "B and C are A's chain, not suspects of their own");
            assertEquals(a, report.suspects().get(0).objectId());
            assertEquals(report.suspects().get(0).retainedSize(), report.analyzedBytes(),
                    "the chain's bytes are counted once");
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
    void disjointChainsAreSeparateSuspectsWhoseBytesSumWithinTheHeap(@TempDir Path tmp)
            throws IOException, SQLException {
        // Two rooted chains, A→B→C and X→Y. Every object is 24 bytes; the heap is 120.
        // At 30% (36 bytes): A (72), B (48) and X (48) clear it, B only as A's chain.
        int idSize = 8;
        long classId = 0xC001L;
        long a = 0x100L, b = 0x200L, c = 0x300L, x = 0x400L, y = 0x500L;

        Path hprof = SyntheticHprof.create("1.0.2", idSize, 0L)
                .string(0xA001L, "Holder")
                .string(0xA002L, "next")
                .loadClass(1, classId, 0, 0xA001L)
                .heapDumpSegment(seg -> seg
                        .topLevelObjectClassDump(classId, 0xA002L)
                        .gcRoot(HprofTag.Sub.ROOT_STICKY_CLASS, a)
                        .gcRoot(HprofTag.Sub.ROOT_STICKY_CLASS, x)
                        .instanceDump(a, classId, idBytes(b, idSize))
                        .instanceDump(b, classId, idBytes(c, idSize))
                        .instanceDump(c, classId, idBytes(0L, idSize))
                        .instanceDump(x, classId, idBytes(y, idSize))
                        .instanceDump(y, classId, idBytes(0L, idSize)))
                .heapDumpEnd()
                .writeTo(tmp, "two-chains.hprof");

        Path indexDb = HeapDumpIndexPaths.indexFor(hprof);
        try (HprofMappedFile file = HprofMappedFile.open(hprof)) {
            HprofIndex.build(file, indexDb, CLOCK);
        }
        DominatorTreeBuilder.build(indexDb);

        try (HeapView view = HeapView.open(indexDb)) {
            LeakSuspectsReport report = LeakSuspectsAnalyzer.analyze(view, 30.0, 5);
            assertEquals(List.of(a, x), report.suspects().stream().map(LeakSuspect::objectId).toList());
            assertEquals(List.of(1, 2), report.suspects().stream().map(LeakSuspect::rank).toList());
            long shallow = 16 + idSize;
            assertEquals(shallow * 5, report.analyzedBytes(), "the two chains are disjoint and cover the heap");
            assertTrue(report.analyzedBytes() <= report.totalHeapSize());
            assertTrue(report.suspects().get(0).reason().startsWith("One Holder object retains 60.0%"),
                    report.suspects().get(0).reason());
        }
    }

    @Test
    void namesWhereTheSuspectAccumulatesWhatItHoldsAndWhatKeepsItAlive(@TempDir Path tmp)
            throws IOException, SQLException {
        // Holder (rooted) -> Object[] -> four Elem objects. The array retains most of the holder, its
        // elements each a quarter of the rest: the array is where the memory accumulates.
        int idSize = 8;
        long holderClass = 0xC001L;
        long elemClass = 0xC002L;
        long holder = 0x100L;
        long array = 0x200L;
        long[] elems = {0x301L, 0x302L, 0x303L, 0x304L};

        Path hprof = SyntheticHprof.create("1.0.2", idSize, 0L)
                .string(0xA001L, "Holder")
                .string(0xA002L, "Elem")
                .string(0xA003L, "next")
                .loadClass(1, holderClass, 0, 0xA001L)
                .loadClass(2, elemClass, 0, 0xA002L)
                .heapDumpSegment(seg -> {
                    seg.topLevelObjectClassDump(holderClass, 0xA003L)
                            .topLevelObjectClassDump(elemClass, 0xA003L)
                            .gcRoot(HprofTag.Sub.ROOT_STICKY_CLASS, holder)
                            .instanceDump(holder, holderClass, idBytes(array, idSize))
                            .objectArrayDump(array, elemClass, elems);
                    for (long elem : elems) {
                        seg.instanceDump(elem, elemClass, idBytes(0L, idSize));
                    }
                })
                .heapDumpEnd()
                .writeTo(tmp, "accumulation.hprof");

        Path indexDb = HeapDumpIndexPaths.indexFor(hprof);
        try (HprofMappedFile file = HprofMappedFile.open(hprof)) {
            HprofIndex.build(file, indexDb, CLOCK);
        }
        DominatorTreeBuilder.build(indexDb);

        try (HeapView view = HeapView.open(indexDb)) {
            LeakSuspectsReport report = LeakSuspectsAnalyzer.analyze(view, 50.0, 5);
            assertEquals(1, report.suspects().size());
            LeakSuspect suspect = report.suspects().getFirst();
            long elemsRetained = 0;
            for (long elem : elems) {
                elemsRetained += view.retainedSize(elem);
            }

            assertEquals(Long.valueOf(array), suspect.accumulationPointId(), "one level below the holder");
            assertEquals(1, suspect.dominatedClassCount());
            DominatedClassEntry contents = suspect.dominatedHistogram().getFirst();
            assertEquals("Elem", contents.className());
            assertEquals(elems.length, contents.instanceCount());
            assertEquals(elemsRetained, contents.retainedSize());
            assertEquals(elemsRetained * 100.0 / suspect.retainedSize(), contents.percentOfCluster(), 1e-9);
            assertTrue(suspect.accumulationPoint().contains("1 level below the suspect"),
                    suspect.accumulationPoint());
            assertTrue(suspect.accumulationPoint().contains("directly holds 4 objects of 1 class"),
                    suspect.accumulationPoint());

            GCRootPath root = suspect.gcRootPath();
            assertNotNull(root, "the holder is a root, so it carries its own one-step path");
            assertEquals(holder, root.rootObjectId());
            assertEquals("Sticky class", root.rootType());
            assertEquals(1, root.steps().size());
        }
    }

    @Test
    void carriesTheShortestPathWhenTheSuspectIsHeldThroughOtherObjects(@TempDir Path tmp)
            throws IOException, SQLException {
        // Two roots, A and B, each hold the suspect S through one field; S heads a chain of three. No
        // single path dominates S, so it is a suspect of its own, and its path runs through a referrer.
        int idSize = 8;
        long classId = 0xC001L;
        long a = 0x100L;
        long b = 0x200L;
        long suspect = 0x300L;
        long x = 0x400L;
        long y = 0x500L;

        Path hprof = SyntheticHprof.create("1.0.2", idSize, 0L)
                .string(0xA001L, "Holder")
                .string(0xA002L, "next")
                .loadClass(1, classId, 0, 0xA001L)
                .heapDumpSegment(seg -> seg
                        .topLevelObjectClassDump(classId, 0xA002L)
                        .gcRoot(HprofTag.Sub.ROOT_STICKY_CLASS, a)
                        .gcRoot(HprofTag.Sub.ROOT_STICKY_CLASS, b)
                        .instanceDump(a, classId, idBytes(suspect, idSize))
                        .instanceDump(b, classId, idBytes(suspect, idSize))
                        .instanceDump(suspect, classId, idBytes(x, idSize))
                        .instanceDump(x, classId, idBytes(y, idSize))
                        .instanceDump(y, classId, idBytes(0L, idSize)))
                .heapDumpEnd()
                .writeTo(tmp, "held.hprof");

        Path indexDb = HeapDumpIndexPaths.indexFor(hprof);
        try (HprofMappedFile file = HprofMappedFile.open(hprof)) {
            HprofIndex.build(file, indexDb, CLOCK);
        }
        DominatorTreeBuilder.build(indexDb);

        try (HeapView view = HeapView.open(indexDb)) {
            // The heap is five objects; S retains three of them (60%), A and B one each.
            LeakSuspect top = LeakSuspectsAnalyzer.analyze(view, 50.0, 5).suspects().getFirst();
            assertEquals(Long.valueOf(suspect), top.objectId());

            GCRootPath path = top.gcRootPath();
            assertNotNull(path);
            assertTrue(path.rootObjectId() == a || path.rootObjectId() == b, "rooted at A or B");
            assertEquals("Sticky class", path.rootType());
            assertEquals(2, path.steps().size(), "the root, then the suspect");
            assertEquals("next", path.steps().getFirst().fieldName());
            assertTrue(path.steps().getLast().isTarget());
        }
    }

    @Test
    void suppressesDescendantAcrossClassNodesButKeepsIndependentStaticRetainers(@TempDir Path tmp)
            throws IOException, SQLException {
        // holder -> ClassA -> ClassB -> nested; independently rooted ClassC -> independent.
        // Class nodes are not suspect instances, but must not hide holder's dominance of nested.
        long holderClass = 0xC001L, classA = 0xC002L, classB = 0xC003L, classC = 0xC004L;
        long holder = 0x100L, nested = 0x200L, independent = 0x300L;
        Path hprof = SyntheticHprof.create("1.0.2", 8, 0L)
                .string(0xA001L, "Holder").string(0xA002L, "Statics").string(0xA003L, "next")
                .loadClass(1, holderClass, 0, 0xA001L)
                .loadClass(2, classA, 0, 0xA002L)
                .loadClass(3, classB, 0, 0xA002L)
                .loadClass(4, classC, 0, 0xA002L)
                .heapDumpSegment(seg -> seg
                        .topLevelObjectClassDump(holderClass, 0xA003L)
                        .classDumpWithStatics(classA, 0, 0, 0,
                                new SyntheticHprof.SubBuilder.StaticSpec[]{
                                        new SyntheticHprof.SubBuilder.StaticSpec(0xA003L, HprofTag.BasicType.OBJECT, classB)})
                        .classDumpWithStatics(classB, 0, 0, 0,
                                new SyntheticHprof.SubBuilder.StaticSpec[]{
                                        new SyntheticHprof.SubBuilder.StaticSpec(0xA003L, HprofTag.BasicType.OBJECT, nested)})
                        .classDumpWithStatics(classC, 0, 0, 0,
                                new SyntheticHprof.SubBuilder.StaticSpec[]{
                                        new SyntheticHprof.SubBuilder.StaticSpec(0xA003L, HprofTag.BasicType.OBJECT, independent)})
                        .gcRoot(HprofTag.Sub.ROOT_STICKY_CLASS, holder)
                        .gcRoot(HprofTag.Sub.ROOT_STICKY_CLASS, classC)
                        .instanceDump(holder, holderClass, idBytes(classA, 8))
                        .instanceDump(nested, holderClass, idBytes(0, 8))
                        .instanceDump(independent, holderClass, idBytes(0, 8)))
                .heapDumpEnd().writeTo(tmp, "class-bridge.hprof");
        Path indexDb = HeapDumpIndexPaths.indexFor(hprof);
        try (HprofMappedFile file = HprofMappedFile.open(hprof)) {
            HprofIndex.build(file, indexDb, CLOCK);
        }
        DominatorTreeBuilder.build(indexDb);
        try (HeapView view = HeapView.open(indexDb)) {
            LeakSuspectsReport report = LeakSuspectsAnalyzer.analyze(view, 5, 10);
            assertEquals(List.of(holder, independent), report.suspects().stream().map(LeakSuspect::objectId).toList());
            assertEquals(88, report.analyzedBytes(), "three 24-byte instances and two 8-byte dominated static slots count once");
            assertEquals(96, report.totalHeapSize(), "three 24-byte instances plus three 8-byte class static footprints");
            assertTrue(report.analyzedBytes() <= report.totalHeapSize());
            assertEquals(64.0 * 100 / 96, report.suspects().getFirst().heapPercentage(), 1e-9);
            assertTrue(LeakSuspectsAnalyzer.analyze(view, 80, 10).suspects().isEmpty(),
                    "the threshold uses the same graph-node total as the reported heap percentage");
            assertEquals(List.of(holder), LeakSuspectsAnalyzer.analyze(view, 5, 1).suspects().stream()
                    .map(LeakSuspect::objectId).toList(), "topN is applied after suppressing descendants");
        }
    }

    @Test
    void countsClassObjectAlreadyPresentAsInstanceOnlyOnce(@TempDir Path tmp)
            throws IOException, SQLException {
        long holderClass = 0xC001L, classObject = 0xC002L, payload = 0x200L;
        Path hprof = SyntheticHprof.create("1.0.2", 8, 0L)
                .string(0xA001L, "Holder").string(0xA002L, "Statics").string(0xA003L, "next")
                .loadClass(1, holderClass, 0, 0xA001L)
                .loadClass(2, classObject, 0, 0xA002L)
                .heapDumpSegment(seg -> seg
                        .topLevelObjectClassDump(holderClass, 0xA003L)
                        .classDumpWithStatics(classObject, 0, 0, 0,
                                new SyntheticHprof.SubBuilder.StaticSpec[]{
                                        new SyntheticHprof.SubBuilder.StaticSpec(0xA003L, HprofTag.BasicType.OBJECT, payload)})
                        .gcRoot(HprofTag.Sub.ROOT_STICKY_CLASS, classObject)
                        .instanceDump(classObject, holderClass, idBytes(0, 8))
                        .instanceDump(payload, holderClass, idBytes(0, 8)))
                .heapDumpEnd().writeTo(tmp, "class-is-instance.hprof");
        Path indexDb = HeapDumpIndexPaths.indexFor(hprof);
        try (HprofMappedFile file = HprofMappedFile.open(hprof)) {
            HprofIndex.build(file, indexDb, CLOCK);
        }
        DominatorTreeBuilder.build(indexDb);
        try (HeapView view = HeapView.open(indexDb)) {
            LeakSuspectsReport report = LeakSuspectsAnalyzer.analyze(view, 5, 10);
            assertEquals(48, report.totalHeapSize(), "two 24-byte instances; the class static footprint is not added again");
            assertEquals(48, report.analyzedBytes());
            assertEquals(List.of(classObject), report.suspects().stream().map(LeakSuspect::objectId).toList());
            assertEquals(100, report.suspects().getFirst().heapPercentage());
        }
    }

    @Test
    void leavesPathUnknownWhenStrongRootsAreBeyondSearchLimit(@TempDir Path tmp)
            throws IOException, SQLException {
        long clazz = 0xC001L, a = 0x100L, b = 0x200L, target = 0x1000L, payload = 0x2000L;
        Path hprof = SyntheticHprof.create("1.0.2", 8, 0L)
                .string(0xA001L, "Holder").string(0xA002L, "next").loadClass(1, clazz, 0, 0xA001L)
                .heapDumpSegment(seg -> {
                    seg.topLevelObjectClassDump(clazz, 0xA002L)
                            .gcRoot(HprofTag.Sub.ROOT_STICKY_CLASS, a)
                            .gcRoot(HprofTag.Sub.ROOT_STICKY_CLASS, b);
                    for (int i = 0; i < 110; i++) {
                        seg.instanceDump(a + i, clazz, idBytes(i == 109 ? target : a + i + 1, 8));
                        seg.instanceDump(b + i, clazz, idBytes(i == 109 ? target : b + i + 1, 8));
                    }
                    seg.instanceDump(target, clazz, idBytes(payload, 8))
                            .primitiveArrayDump(payload, HprofTag.BasicType.BYTE, new byte[100_000], 100_000);
                }).heapDumpEnd().writeTo(tmp, "long-strong-path.hprof");
        Path indexDb = HeapDumpIndexPaths.indexFor(hprof);
        try (HprofMappedFile file = HprofMappedFile.open(hprof)) {
            HprofIndex.build(file, indexDb, CLOCK);
        }
        DominatorTreeBuilder.build(indexDb);
        try (HeapView view = HeapView.open(indexDb)) {
            LeakSuspect suspect = LeakSuspectsAnalyzer.analyze(view, 5, 10).suspects().getFirst();
            assertEquals(target, suspect.objectId());
            assertNull(suspect.gcRootPath(), "a bounded search can leave a fully strongly reachable suspect unknown");
            assertTrue(suspect.retainedSize() > 100_000);
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
