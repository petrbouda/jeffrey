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

import cafe.jeffrey.profile.heapdump.model.GCRootPath;
import cafe.jeffrey.profile.heapdump.parser.HeapDumpIndexPaths;
import cafe.jeffrey.profile.heapdump.parser.HeapView;
import cafe.jeffrey.profile.heapdump.parser.HprofIndex;
import cafe.jeffrey.profile.heapdump.parser.HprofMappedFile;
import cafe.jeffrey.profile.heapdump.parser.HprofTag;
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
