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

import cafe.jeffrey.profile.heapdump.model.InstanceTreeRequest;
import cafe.jeffrey.profile.heapdump.model.InstanceTreeResponse;
import cafe.jeffrey.profile.heapdump.parser.HeapDumpIndexPaths;
import cafe.jeffrey.profile.heapdump.parser.HeapView;
import cafe.jeffrey.profile.heapdump.parser.HprofIndex;
import cafe.jeffrey.profile.heapdump.parser.HprofMappedFile;
import cafe.jeffrey.profile.heapdump.parser.SyntheticHprof;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InstanceTreeAnalyzerTest {

    private static final Clock CLOCK =
            Clock.fixed(Instant.ofEpochMilli(1L), ZoneOffset.UTC);
    private static final int ID_SIZE = 8;

    @Test
    void reachablesShowsOutboundChildrenWithFieldNames(@TempDir Path tmp)
            throws IOException, SQLException {
        long classId = 0xC001L;
        long parent = 0x1001L;
        long child = 0x1002L;

        Path hprof = SyntheticHprof.create("1.0.2", ID_SIZE, 0L)
                .string(0xA001L, "com.example.Node")
                .string(0xA002L, "next")
                .loadClass(1, classId, 0, 0xA001L)
                .heapDumpSegment(seg -> seg
                        .topLevelObjectClassDump(classId, 0xA002L)
                        .instanceDump(parent, classId, idBytes(child, ID_SIZE))
                        .instanceDump(child, classId, idBytes(0L, ID_SIZE)))
                .heapDumpEnd()
                .writeTo(tmp, "tree-reach.hprof");
        Path indexDb = HeapDumpIndexPaths.indexFor(hprof);
        try (HprofMappedFile file = HprofMappedFile.open(hprof)) {
            HprofIndex.build(file, indexDb, CLOCK);
        }

        try (HeapView view = HeapView.open(indexDb)) {
            InstanceTreeResponse response = InstanceTreeAnalyzer.analyze(
                    view, InstanceTreeRequest.reachables(parent));
            assertEquals(parent, response.root().objectId());
            assertEquals("com.example.Node", response.root().className());
            assertEquals(1, response.children().size());
            assertEquals(child, response.children().get(0).objectId());
            assertEquals("next", response.children().get(0).fieldName());
            assertEquals("REACHABLE", response.children().get(0).relationshipType());
        }
    }

    @Test
    void referrersShowsInboundChildren(@TempDir Path tmp) throws IOException, SQLException {
        long classId = 0xC001L;
        long target = 0x1001L;
        long ref1 = 0x2001L;
        long ref2 = 0x2002L;

        Path hprof = SyntheticHprof.create("1.0.2", ID_SIZE, 0L)
                .string(0xA001L, "com.example.Node")
                .string(0xA002L, "next")
                .loadClass(1, classId, 0, 0xA001L)
                .heapDumpSegment(seg -> seg
                        .topLevelObjectClassDump(classId, 0xA002L)
                        .instanceDump(target, classId, idBytes(0L, ID_SIZE))
                        .instanceDump(ref1, classId, idBytes(target, ID_SIZE))
                        .instanceDump(ref2, classId, idBytes(target, ID_SIZE)))
                .heapDumpEnd()
                .writeTo(tmp, "tree-ref.hprof");
        Path indexDb = HeapDumpIndexPaths.indexFor(hprof);
        try (HprofMappedFile file = HprofMappedFile.open(hprof)) {
            HprofIndex.build(file, indexDb, CLOCK);
        }
        try (HeapView view = HeapView.open(indexDb)) {
            InstanceTreeResponse response = InstanceTreeAnalyzer.analyze(
                    view, InstanceTreeRequest.referrers(target));
            assertEquals(2, response.children().size());
            assertEquals("REFERRER", response.children().get(0).relationshipType());
            // Field name is null in REFERRERS mode (would require per-source class chain walks).
            assertNull(response.children().get(0).fieldName());
        }
    }

    @Test
    void honorsLimitAndOffsetWithHasMoreFlag(@TempDir Path tmp) throws IOException, SQLException {
        long classId = 0xC001L;
        long arrayId = 0x9001L;
        long[] children = new long[10];
        for (int i = 0; i < children.length; i++) {
            children[i] = 0x2000L + i;
        }

        Path hprof = SyntheticHprof.create("1.0.2", ID_SIZE, 0L)
                .string(0xA001L, "Holder")
                .string(0xA002L, "f")
                .loadClass(1, classId, 0, 0xA001L)
                .heapDumpSegment(seg -> {
                    seg.topLevelObjectClassDump(classId, 0xA002L);
                    seg.objectArrayDump(arrayId, classId, children);
                    for (long id : children) {
                        seg.instanceDump(id, classId, idBytes(0L, ID_SIZE));
                    }
                })
                .heapDumpEnd()
                .writeTo(tmp, "tree-paged.hprof");
        Path indexDb = HeapDumpIndexPaths.indexFor(hprof);
        try (HprofMappedFile file = HprofMappedFile.open(hprof)) {
            HprofIndex.build(file, indexDb, CLOCK);
        }
        try (HeapView view = HeapView.open(indexDb)) {
            InstanceTreeResponse first = InstanceTreeAnalyzer.analyze(
                    view, new InstanceTreeRequest(arrayId, InstanceTreeRequest.TreeMode.REACHABLES, 5, 0));
            assertEquals(5, first.children().size());
            assertTrue(first.hasMore());
            assertEquals(10, first.totalCount());

            InstanceTreeResponse last = InstanceTreeAnalyzer.analyze(
                    view, new InstanceTreeRequest(arrayId, InstanceTreeRequest.TreeMode.REACHABLES, 5, 5));
            assertEquals(5, last.children().size());
            assertFalse(last.hasMore());
        }
    }

    @Test
    void notFoundResponseForUnknownInstance(@TempDir Path tmp) throws IOException, SQLException {
        Path hprof = SyntheticHprof.create("1.0.2", 8, 0L)
                .string(0xA001L, "X")
                .heapDumpEnd()
                .writeTo(tmp, "nope.hprof");
        Path indexDb = HeapDumpIndexPaths.indexFor(hprof);
        try (HprofMappedFile file = HprofMappedFile.open(hprof)) {
            HprofIndex.build(file, indexDb, CLOCK);
        }
        try (HeapView view = HeapView.open(indexDb)) {
            InstanceTreeResponse r = InstanceTreeAnalyzer.analyze(
                    view, InstanceTreeRequest.referrers(0xDEADL));
            assertNull(r.root());
            assertTrue(r.children().isEmpty());
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
