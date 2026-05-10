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
package cafe.jeffrey.profile.heapdump.parser;

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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DominatorTreeBuilderTest {

    private static final Clock CLOCK =
            Clock.fixed(Instant.ofEpochMilli(1L), ZoneOffset.UTC);

    /**
     * Diamond:
     *   V (virtual root) → A, B
     *   A → C
     *   B → C
     *   C → D
     *
     * Expected dominators: A:V, B:V, C:V (common ancestor of two paths), D:C.
     * Expected retained sizes (shallow=16 for everyone):
     *   D = 16, C = 16+16 = 32 (dominates D), A = 16, B = 16.
     */
    @Test
    void diamondGraphProducesExpectedDominators(@TempDir Path tmp) throws IOException, SQLException {
        int idSize = 8;
        long classId = 0xC001L;
        long a = 0x1A0L, b = 0x1B0L, c = 0x1C0L, d = 0x1D0L;

        Path hprof = SyntheticHprof.create("1.0.2", idSize, 0L)
                .string(0xA001L, "Holder")
                .string(0xA002L, "next")
                .loadClass(1, classId, 0, 0xA001L)
                .heapDumpSegment(seg -> seg
                        .topLevelObjectClassDump(classId, 0xA002L)
                        .gcRoot(HprofTag.Sub.ROOT_STICKY_CLASS, a)
                        .gcRoot(HprofTag.Sub.ROOT_STICKY_CLASS, b)
                        .instanceDump(a, classId, idBytes(c, idSize))
                        .instanceDump(b, classId, idBytes(c, idSize))
                        .instanceDump(c, classId, idBytes(d, idSize))
                        .instanceDump(d, classId, idBytes(0L, idSize)))
                .heapDumpEnd()
                .writeTo(tmp, "diamond.hprof");

        Path indexDb = HeapDumpIndexPaths.indexFor(hprof);
        try (HprofMappedFile file = HprofMappedFile.open(hprof)) {
            HprofIndex.build(file, indexDb, CLOCK);
        }

        // Pre-build: dominator tree absent.
        try (HeapView view = HeapView.open(indexDb)) {
            assertFalse(view.hasDominatorTree());
            assertEquals(-1L, view.dominatorOf(a));
        }

        DominatorTreeBuilder.BuildResult result = DominatorTreeBuilder.build(indexDb);
        assertEquals(4, result.reachableInstances());
        assertEquals(2, result.rootEdges(), "two GC-rooted instances → two virtual-root edges");

        try (HeapView view = HeapView.open(indexDb)) {
            assertTrue(view.hasDominatorTree());
            assertEquals(DominatorTreeBuilder.VIRTUAL_ROOT, view.dominatorOf(a));
            assertEquals(DominatorTreeBuilder.VIRTUAL_ROOT, view.dominatorOf(b));
            assertEquals(DominatorTreeBuilder.VIRTUAL_ROOT, view.dominatorOf(c),
                    "two paths to C → common ancestor is the virtual root");
            assertEquals(c, view.dominatorOf(d), "single path to D goes through C");

            // Shallow size = 16-byte object header + 8-byte object field = 24
            long shallow = 16 + idSize;
            assertEquals(shallow, view.retainedSize(a));
            assertEquals(shallow, view.retainedSize(b));
            assertEquals(shallow * 2, view.retainedSize(c), "C dominates D");
            assertEquals(shallow, view.retainedSize(d));
        }
    }

    /**
     * Single linear chain: root → X → Y → Z. X dominates Y, Y dominates Z.
     * Retained sizes accumulate fully along the chain.
     */
    @Test
    void linearChainBubblesRetainedSize(@TempDir Path tmp) throws IOException, SQLException {
        int idSize = 8;
        long classId = 0xC001L;
        long x = 0x100L, y = 0x200L, z = 0x300L;

        Path hprof = SyntheticHprof.create("1.0.2", idSize, 0L)
                .string(0xA001L, "Holder")
                .string(0xA002L, "next")
                .loadClass(1, classId, 0, 0xA001L)
                .heapDumpSegment(seg -> seg
                        .topLevelObjectClassDump(classId, 0xA002L)
                        .gcRoot(HprofTag.Sub.ROOT_STICKY_CLASS, x)
                        .instanceDump(x, classId, idBytes(y, idSize))
                        .instanceDump(y, classId, idBytes(z, idSize))
                        .instanceDump(z, classId, idBytes(0L, idSize)))
                .heapDumpEnd()
                .writeTo(tmp, "chain.hprof");
        Path indexDb = HeapDumpIndexPaths.indexFor(hprof);
        try (HprofMappedFile file = HprofMappedFile.open(hprof)) {
            HprofIndex.build(file, indexDb, CLOCK);
        }
        DominatorTreeBuilder.build(indexDb);
        long shallow = 16 + idSize;
        try (HeapView view = HeapView.open(indexDb)) {
            assertEquals(DominatorTreeBuilder.VIRTUAL_ROOT, view.dominatorOf(x));
            assertEquals(x, view.dominatorOf(y));
            assertEquals(y, view.dominatorOf(z));
            assertEquals(shallow, view.retainedSize(z));
            assertEquals(shallow * 2, view.retainedSize(y));
            assertEquals(shallow * 3, view.retainedSize(x));
        }
    }

    @Test
    void rebuildIsIdempotent(@TempDir Path tmp) throws IOException, SQLException {
        int idSize = 8;
        long classId = 0xC001L;
        long a = 0x1A0L;

        Path hprof = SyntheticHprof.create("1.0.2", idSize, 0L)
                .string(0xA001L, "Holder")
                .string(0xA002L, "next")
                .loadClass(1, classId, 0, 0xA001L)
                .heapDumpSegment(seg -> seg
                        .topLevelObjectClassDump(classId, 0xA002L)
                        .gcRoot(HprofTag.Sub.ROOT_STICKY_CLASS, a)
                        .instanceDump(a, classId, idBytes(0L, idSize)))
                .heapDumpEnd()
                .writeTo(tmp, "single.hprof");
        Path indexDb = HeapDumpIndexPaths.indexFor(hprof);
        try (HprofMappedFile file = HprofMappedFile.open(hprof)) {
            HprofIndex.build(file, indexDb, CLOCK);
        }

        DominatorTreeBuilder.build(indexDb);
        DominatorTreeBuilder.build(indexDb); // second build clears + recomputes
        try (HeapView view = HeapView.open(indexDb)) {
            assertEquals(1, view.gcRootCount());
            assertEquals(DominatorTreeBuilder.VIRTUAL_ROOT, view.dominatorOf(a));
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
