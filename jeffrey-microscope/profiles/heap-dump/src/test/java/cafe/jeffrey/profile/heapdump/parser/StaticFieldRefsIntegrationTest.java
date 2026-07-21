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
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import cafe.jeffrey.profile.heapdump.analyzer.heapview.DominatorTreeAnalyzer;
import cafe.jeffrey.profile.heapdump.model.DominatorNode;
import cafe.jeffrey.profile.heapdump.model.DominatorTreeResponse;
import cafe.jeffrey.profile.heapdump.persistence.DominatorTreeBuilder;
import cafe.jeffrey.profile.heapdump.persistence.HeapDumpIndexPaths;
import cafe.jeffrey.profile.heapdump.view.HeapView;
import cafe.jeffrey.profile.heapdump.view.HprofTag;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end coverage of class-static references: CLASS_DUMP static OBJECT
 * fields become {@code outbound_ref field_kind=2} rows, class objects become
 * dominator-graph nodes, and objects retained only through statics get
 * retained sizes and appear under their holder class in the dominator tree.
 */
class StaticFieldRefsIntegrationTest {

    private static final Clock CLOCK =
            Clock.fixed(Instant.ofEpochMilli(1L), ZoneOffset.UTC);
    private static final int ID_SIZE = 8;
    private static final byte FIELD_KIND_CLASS_STATIC = 2;

    @Test
    void staticObjectRefsEmittedToOutboundRef(@TempDir Path tmp) throws IOException, SQLException {
        long holderClass = 0xC001L;
        long cachedInstance = 0x100L;

        Path hprof = buildStaticHolderDump(tmp, holderClass, cachedInstance, "static-refs.hprof");

        Path indexDb = HeapDumpIndexPaths.indexFor(hprof);
        try (HprofMappedFile file = HprofMappedFile.open(hprof)) {
            HprofIndex.build(file, indexDb, CLOCK);
        }
        try (HeapView view = HeapView.open(indexDb)) {
            try (PreparedStatement stmt = view.databaseClient().connection().prepareStatement(
                    "SELECT source_id, target_id, field_id FROM outbound_ref WHERE field_kind = ?")) {
                stmt.setByte(1, FIELD_KIND_CLASS_STATIC);
                try (ResultSet rs = stmt.executeQuery()) {
                    assertTrue(rs.next(), "one class_static outbound_ref row expected");
                    assertEquals(holderClass, rs.getLong(1));
                    assertEquals(cachedInstance, rs.getLong(2));
                    assertEquals(1, rs.getInt(3), "CACHE is the second static field (index 1)");
                }
            }
            // Static footprint: INT(4) + OBJECT(8) = 12 bytes.
            try (PreparedStatement stmt = view.databaseClient().connection().prepareStatement(
                    "SELECT static_fields_size FROM class WHERE class_id = ?")) {
                stmt.setLong(1, holderClass);
                try (ResultSet rs = stmt.executeQuery()) {
                    assertTrue(rs.next());
                    assertEquals(12, rs.getInt(1));
                }
            }
        }
    }

    @Test
    void staticallyRetainedObjectAppearsUnderClassNode(@TempDir Path tmp)
            throws IOException, SQLException {
        long holderClass = 0xC001L;
        long cachedInstance = 0x100L;

        Path hprof = buildStaticHolderDump(tmp, holderClass, cachedInstance, "static-dominators.hprof");

        Path indexDb = HeapDumpIndexPaths.indexFor(hprof);
        try (HprofMappedFile file = HprofMappedFile.open(hprof)) {
            HprofIndex.build(file, indexDb, CLOCK);
        }
        DominatorTreeBuilder.build(indexDb);

        try (HeapView view = HeapView.open(indexDb)) {
            // The cached instance is reachable ONLY through the class static —
            // before class nodes existed it had no retained size at all.
            try (PreparedStatement stmt = view.databaseClient().connection().prepareStatement(
                    "SELECT bytes FROM retained_size WHERE instance_id = ?")) {
                stmt.setLong(1, cachedInstance);
                try (ResultSet rs = stmt.executeQuery()) {
                    assertTrue(rs.next(), "statics-retained object must have a retained size");
                    assertTrue(rs.getLong(1) > 0);
                }
            }

            // Top level shows the class node (rooted via ROOT_STICKY_CLASS)...
            DominatorTreeResponse topLevel = DominatorTreeAnalyzer.children(view, 0L);
            DominatorNode classNode = topLevel.nodes().stream()
                    .filter(n -> n.objectId() == holderClass)
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("class node missing from dominator roots"));
            assertEquals("class Holder", classNode.className());
            assertTrue(classNode.hasChildren(), "class node dominates its static-held instance");

            // ...and drilling into it surfaces the statics-held instance.
            DominatorTreeResponse children = DominatorTreeAnalyzer.children(view, holderClass);
            assertEquals(1, children.nodes().size());
            assertEquals(cachedInstance, children.nodes().get(0).objectId());
        }
    }

    /**
     * A class "Holder" with statics {@code COUNT:int=42, CACHE:Object -> cachedInstance};
     * the class object is a sticky-class GC root, and the cached instance is
     * reachable only through the static.
     */
    private static Path buildStaticHolderDump(
            Path tmp, long holderClass, long cachedInstance, String fileName) throws IOException {
        return SyntheticHprof.create("1.0.2", ID_SIZE, 0L)
                .string(0xA001L, "Holder")
                .string(0xA002L, "COUNT")
                .string(0xA003L, "CACHE")
                .string(0xA004L, "next")
                .loadClass(1, holderClass, 0, 0xA001L)
                .heapDumpSegment(seg -> seg
                        .classDumpWithStatics(holderClass, 0L, 0L, ID_SIZE,
                                new SyntheticHprof.SubBuilder.StaticSpec[]{
                                        new SyntheticHprof.SubBuilder.StaticSpec(
                                                0xA002L, HprofTag.BasicType.INT, 42L),
                                        new SyntheticHprof.SubBuilder.StaticSpec(
                                                0xA003L, HprofTag.BasicType.OBJECT, cachedInstance)
                                },
                                new SyntheticHprof.SubBuilder.FieldSpec(0xA004L, HprofTag.BasicType.OBJECT))
                        .gcRoot(HprofTag.Sub.ROOT_STICKY_CLASS, holderClass)
                        .instanceDump(cachedInstance, holderClass, idBytes(0L)))
                .heapDumpEnd()
                .writeTo(tmp, fileName);
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
