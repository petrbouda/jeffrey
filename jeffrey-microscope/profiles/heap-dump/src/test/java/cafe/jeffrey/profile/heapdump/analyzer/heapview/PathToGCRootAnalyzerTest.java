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

import cafe.jeffrey.profile.heapdump.model.GCRootPath;
import cafe.jeffrey.profile.heapdump.model.PathStep;
import cafe.jeffrey.profile.heapdump.persistence.HeapDumpIndexPaths;
import cafe.jeffrey.profile.heapdump.view.HeapView;
import cafe.jeffrey.profile.heapdump.parser.HprofIndex;
import cafe.jeffrey.profile.heapdump.parser.HprofMappedFile;
import cafe.jeffrey.profile.heapdump.view.HprofTag;
import cafe.jeffrey.profile.heapdump.parser.SyntheticHprof;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PathToGCRootAnalyzerTest {

    private static final Clock CLOCK =
            Clock.fixed(Instant.ofEpochMilli(1L), ZoneOffset.UTC);

    /**
     * Topology:
     *   ROOT (rootInst, ROOT_JAVA_FRAME) → middleInst → targetInst
     *
     * Each Holder class has one OBJECT field "next".
     * Expect a single path: [ROOT, middleInst, targetInst].
     */
    @Test
    void findsThreeStepPathFromJavaFrameRoot(@TempDir Path tmp) throws IOException, SQLException {
        int idSize = 8;
        long classId = 0xC001L;
        long rootInst = 0x1001L;
        long middleInst = 0x1002L;
        long targetInst = 0x1003L;

        Path hprof = SyntheticHprof.create("1.0.2", idSize, 0L)
                .string(0xA001L, "Holder")
                .string(0xA002L, "next")
                .loadClass(1, classId, 0, 0xA001L)
                .heapDumpSegment(seg -> seg
                        .topLevelObjectClassDump(classId, 0xA002L)
                        .gcRootJavaFrame(rootInst, 7, 13)
                        .instanceDump(rootInst, classId, idBytes(middleInst, idSize))
                        .instanceDump(middleInst, classId, idBytes(targetInst, idSize))
                        .instanceDump(targetInst, classId, idBytes(0L, idSize)))
                .heapDumpEnd()
                .writeTo(tmp, "path.hprof");

        Path indexDb = HeapDumpIndexPaths.indexFor(hprof);
        try (HprofMappedFile file = HprofMappedFile.open(hprof)) {
            HprofIndex.build(file, indexDb, CLOCK);
        }

        try (HeapView view = HeapView.open(indexDb)) {
            List<GCRootPath> paths = PathToGCRootAnalyzer.findPaths(view, targetInst, false, 5);
            assertEquals(1, paths.size());
            GCRootPath path = paths.get(0);
            assertEquals(rootInst, path.rootObjectId());
            assertEquals("Java frame", path.rootType());
            assertEquals("Holder", path.rootClassName());
            assertEquals(3, path.steps().size());
            // Steps are root → ... → target
            assertEquals(rootInst, path.steps().get(0).objectId());
            assertEquals("next", path.steps().get(0).fieldName(),
                    "first step's outgoing field is 'next' to middleInst");
            assertEquals(middleInst, path.steps().get(1).objectId());
            assertEquals("next", path.steps().get(1).fieldName());
            assertEquals(targetInst, path.steps().get(2).objectId());
            assertTrue(path.steps().get(2).isTarget(), "last step is the target");
        }
    }

    /**
     * Topology:
     *   ROOT (rootInst, ROOT_JAVA_FRAME) → targetInst, where targetInst is a Java-frame root itself.
     *
     * The target is not its own path: the search walks past it to the root that also holds it, and a
     * target that is a root with nothing referencing it has no path at all.
     */
    @Test
    void doesNotAnswerATargetThatIsARootWithItself(@TempDir Path tmp) throws IOException, SQLException {
        int idSize = 8;
        long classId = 0xC001L;
        long rootInst = 0x1001L;
        long targetInst = 0x1003L;
        long lonelyRoot = 0x1004L;

        Path hprof = SyntheticHprof.create("1.0.2", idSize, 0L)
                .string(0xA001L, "Holder")
                .string(0xA002L, "next")
                .loadClass(1, classId, 0, 0xA001L)
                .heapDumpSegment(seg -> seg
                        .topLevelObjectClassDump(classId, 0xA002L)
                        .gcRootJavaFrame(rootInst, 7, 13)
                        .gcRootJavaFrame(targetInst, 7, 14)
                        .gcRootJavaFrame(lonelyRoot, 7, 15)
                        .instanceDump(rootInst, classId, idBytes(targetInst, idSize))
                        .instanceDump(targetInst, classId, idBytes(0L, idSize))
                        .instanceDump(lonelyRoot, classId, idBytes(0L, idSize)))
                .heapDumpEnd()
                .writeTo(tmp, "self-root.hprof");

        Path indexDb = HeapDumpIndexPaths.indexFor(hprof);
        try (HprofMappedFile file = HprofMappedFile.open(hprof)) {
            HprofIndex.build(file, indexDb, CLOCK);
        }

        try (HeapView view = HeapView.open(indexDb)) {
            List<GCRootPath> paths = PathToGCRootAnalyzer.findPaths(view, targetInst, false, 5);
            assertEquals(1, paths.size());
            assertEquals(rootInst, paths.get(0).rootObjectId(), "the path starts at the other root, not the target");
            assertEquals(2, paths.get(0).steps().size());

            assertEquals(0, PathToGCRootAnalyzer.findPaths(view, lonelyRoot, false, 5).size(),
                    "a root nothing references has no path, not a path to itself");
        }
    }

    @Test
    void returnsEmptyForUnreachableInstance(@TempDir Path tmp) throws IOException, SQLException {
        int idSize = 8;
        long classId = 0xC001L;
        long unreachable = 0x4000L;

        Path hprof = SyntheticHprof.create("1.0.2", idSize, 0L)
                .string(0xA001L, "Detached")
                .string(0xA002L, "f")
                .loadClass(1, classId, 0, 0xA001L)
                .heapDumpSegment(seg -> seg
                        .topLevelObjectClassDump(classId, 0xA002L)
                        .instanceDump(unreachable, classId, idBytes(0L, idSize)))
                .heapDumpEnd()
                .writeTo(tmp, "unreach.hprof");

        Path indexDb = HeapDumpIndexPaths.indexFor(hprof);
        try (HprofMappedFile file = HprofMappedFile.open(hprof)) {
            HprofIndex.build(file, indexDb, CLOCK);
        }

        try (HeapView view = HeapView.open(indexDb)) {
            assertEquals(0, PathToGCRootAnalyzer.findPaths(view, unreachable, false, 5).size());
        }
    }

    @Test
    void returnsEmptyForUnknownInstanceId(@TempDir Path tmp) throws IOException, SQLException {
        Path hprof = SyntheticHprof.create("1.0.2", 8, 0L)
                .string(0xA001L, "X")
                .heapDumpEnd()
                .writeTo(tmp, "empty.hprof");
        Path indexDb = HeapDumpIndexPaths.indexFor(hprof);
        try (HprofMappedFile file = HprofMappedFile.open(hprof)) {
            HprofIndex.build(file, indexDb, CLOCK);
        }
        try (HeapView view = HeapView.open(indexDb)) {
            assertEquals(0, PathToGCRootAnalyzer.findPaths(view, 0xDEAD, false, 5).size());
        }
    }

    @Test
    void honorsMaxPathsCap(@TempDir Path tmp) throws IOException, SQLException {
        // Two roots, both pointing at the same target via separate holders.
        int idSize = 8;
        long classId = 0xC001L;
        long root1 = 0x1001L;
        long root2 = 0x1002L;
        long target = 0x2000L;

        Path hprof = SyntheticHprof.create("1.0.2", idSize, 0L)
                .string(0xA001L, "Holder")
                .string(0xA002L, "next")
                .loadClass(1, classId, 0, 0xA001L)
                .heapDumpSegment(seg -> seg
                        .topLevelObjectClassDump(classId, 0xA002L)
                        .gcRootJavaFrame(root1, 1, 1)
                        .gcRootJavaFrame(root2, 2, 2)
                        .instanceDump(root1, classId, idBytes(target, idSize))
                        .instanceDump(root2, classId, idBytes(target, idSize))
                        .instanceDump(target, classId, idBytes(0L, idSize)))
                .heapDumpEnd()
                .writeTo(tmp, "max-paths.hprof");

        Path indexDb = HeapDumpIndexPaths.indexFor(hprof);
        try (HprofMappedFile file = HprofMappedFile.open(hprof)) {
            HprofIndex.build(file, indexDb, CLOCK);
        }
        try (HeapView view = HeapView.open(indexDb)) {
            assertEquals(1, PathToGCRootAnalyzer.findPaths(view, target, false, 1).size());
            assertEquals(2, PathToGCRootAnalyzer.findPaths(view, target, false, 5).size());
        }
    }

    /**
     * Topology:
     *   ROOT root1 (Holder) → weakRef (CacheRef extends java.lang.ref.WeakReference) → target
     *   ROOT root2 (Holder) → holder (Holder) → target
     *
     * The weak path is the shorter one, so it comes first while weak references count; excluding
     * them leaves only the strong path, and the exclusion has to reach CacheRef through its
     * superclass, since only the JDK classes are named.
     */
    @Test
    void excludesPathsThroughSubclassesOfWeakReference(@TempDir Path tmp) throws IOException, SQLException {
        int idSize = 8;
        long holderClass = 0xC001L;
        long weakClass = 0xC002L;
        long cacheRefClass = 0xC003L;
        long root1 = 0x1001L;
        long root2 = 0x1002L;
        long weakRef = 0x2001L;
        long holder = 0x2002L;
        long target = 0x3000L;

        Path hprof = SyntheticHprof.create("1.0.2", idSize, 0L)
                .string(0xA001L, "Holder")
                .string(0xA002L, "next")
                .string(0xA003L, "java.lang.ref.WeakReference")
                .string(0xA004L, "CacheRef")
                .string(0xA005L, "referent")
                .loadClass(1, holderClass, 0, 0xA001L)
                .loadClass(2, weakClass, 0, 0xA003L)
                .loadClass(3, cacheRefClass, 0, 0xA004L)
                .heapDumpSegment(seg -> seg
                        .topLevelObjectClassDump(holderClass, 0xA002L)
                        .classDumpWithFields(weakClass, 0L, 0L, 0)
                        .objectFieldClassDumpWithSuper(cacheRefClass, weakClass, 0xA005L)
                        .gcRootJavaFrame(root1, 1, 1)
                        .gcRootJavaFrame(root2, 2, 2)
                        .instanceDump(root1, holderClass, idBytes(weakRef, idSize))
                        .instanceDump(root2, holderClass, idBytes(holder, idSize))
                        .instanceDump(weakRef, cacheRefClass, idBytes(target, idSize))
                        .instanceDump(holder, holderClass, idBytes(target, idSize))
                        .instanceDump(target, holderClass, idBytes(0L, idSize)))
                .heapDumpEnd()
                .writeTo(tmp, "weak.hprof");

        Path indexDb = HeapDumpIndexPaths.indexFor(hprof);
        try (HprofMappedFile file = HprofMappedFile.open(hprof)) {
            HprofIndex.build(file, indexDb, CLOCK);
        }
        try (HeapView view = HeapView.open(indexDb)) {
            List<GCRootPath> all = PathToGCRootAnalyzer.findPaths(view, target, false, 5);
            assertEquals(List.of(root1, root2), all.stream().map(GCRootPath::rootObjectId).toList(),
                    "both roots hold the target while weak references count, shallowest first");

            List<GCRootPath> strong = PathToGCRootAnalyzer.findPaths(view, target, true, 5);
            assertEquals(1, strong.size());
            assertEquals(root2, strong.get(0).rootObjectId());
            assertEquals(List.of(root2, holder, target),
                    strong.get(0).steps().stream().map(PathStep::objectId).toList());
        }
    }

    /**
     * Topology:
     *   ROOT near → target, and ROOT far → a → b → target
     *
     * Breadth-first, so with room for one path it is the shorter one, whatever the ids.
     */
    @Test
    void answersTheShortestPathFirst(@TempDir Path tmp) throws IOException, SQLException {
        int idSize = 8;
        long classId = 0xC001L;
        long far = 0x1001L;
        long near = 0x1009L;
        long a = 0x2001L;
        long b = 0x2002L;
        long target = 0x3000L;

        Path hprof = SyntheticHprof.create("1.0.2", idSize, 0L)
                .string(0xA001L, "Holder")
                .string(0xA002L, "next")
                .loadClass(1, classId, 0, 0xA001L)
                .heapDumpSegment(seg -> seg
                        .topLevelObjectClassDump(classId, 0xA002L)
                        .gcRootJavaFrame(far, 1, 1)
                        .gcRootJavaFrame(near, 2, 2)
                        .instanceDump(far, classId, idBytes(a, idSize))
                        .instanceDump(a, classId, idBytes(b, idSize))
                        .instanceDump(b, classId, idBytes(target, idSize))
                        .instanceDump(near, classId, idBytes(target, idSize))
                        .instanceDump(target, classId, idBytes(0L, idSize)))
                .heapDumpEnd()
                .writeTo(tmp, "shortest.hprof");

        Path indexDb = HeapDumpIndexPaths.indexFor(hprof);
        try (HprofMappedFile file = HprofMappedFile.open(hprof)) {
            HprofIndex.build(file, indexDb, CLOCK);
        }
        try (HeapView view = HeapView.open(indexDb)) {
            List<GCRootPath> one = PathToGCRootAnalyzer.findPaths(view, target, false, 1);
            assertEquals(1, one.size());
            assertEquals(near, one.get(0).rootObjectId());

            List<GCRootPath> both = PathToGCRootAnalyzer.findPaths(view, target, false, 5);
            assertEquals(List.of(2, 4), both.stream().map(path -> path.steps().size()).toList());
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
