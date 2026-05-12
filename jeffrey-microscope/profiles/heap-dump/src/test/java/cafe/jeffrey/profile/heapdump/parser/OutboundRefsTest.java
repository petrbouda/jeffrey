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
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that {@link HprofIndex} populates {@code outbound_ref} for both
 * INSTANCE_DUMP fields (walking the class hierarchy) and OBJECT_ARRAY_DUMP
 * elements, and that {@link HeapView#outboundRefs}/{@link HeapView#inboundRefs}
 * surface them.
 */
class OutboundRefsTest {

    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.ofEpochMilli(1L), ZoneOffset.UTC);

    @Nested
    class ArrayElementRefs {

        @Test
        void emitsOneRefPerNonNullArrayElement(@TempDir Path tmp) throws IOException, SQLException {
            long arrayId = 0x9001L;
            long classId = 0xC001L;
            // Three elements: two non-zero, one null (id 0). Only the non-zero ones become refs.
            Path hprof = SyntheticHprof.create("1.0.2", 8, 0L)
                    .string(0xA001L, "java.lang.Object[]")
                    .loadClass(1, classId, 0, 0xA001L)
                    .heapDumpSegment(seg -> seg
                            .simpleClassDump(classId, 0L, 0L, 0, 0xA001L)
                            .objectArrayDump(arrayId, classId, new long[]{0x10L, 0L, 0x20L}))
                    .heapDumpEnd()
                    .writeTo(tmp, "array-refs.hprof");

            Path indexDb = HeapDumpIndexPaths.indexFor(hprof);
            HprofIndex.IndexResult result;
            try (HprofMappedFile file = HprofMappedFile.open(hprof)) {
                result = HprofIndex.build(file, indexDb, FIXED_CLOCK);
            }
            assertEquals(2, result.outboundRefCount());

            try (HeapView view = HeapView.open(indexDb)) {
                List<OutboundRefRow> out = view.outboundRefs(arrayId);
                assertEquals(2, out.size());
                assertEquals(0x10L, out.get(0).targetId());
                assertEquals(1, out.get(0).fieldKind(), "field_kind for array element");
                assertEquals(0, out.get(0).fieldId(), "first non-null element is at index 0");
                assertEquals(0x20L, out.get(1).targetId());
                assertEquals(2, out.get(1).fieldId(), "third element is at index 2 — null at 1 was skipped");
            }
        }
    }

    @Nested
    class InstanceFieldRefs {

        /**
         * Build a class with one instance field of type OBJECT, instantiate it twice,
         * point the field at another object. Expect one outbound ref per instance.
         */
        @Test
        void emitsRefForObjectInstanceField(@TempDir Path tmp) throws IOException, SQLException {
            int idSize = 8;
            long stringNameId = 0xA001L;
            long stringFieldNameId = 0xA002L;
            long classId = 0xC001L;
            long target = 0xDEADL;
            long instance1 = 0x1001L;
            long instance2 = 0x1002L;

            byte[] objectFieldBytes = idBytes(target, idSize);

            Path hprof = SyntheticHprof.create("1.0.2", idSize, 0L)
                    .string(stringNameId, "Holder")
                    .string(stringFieldNameId, "ref")
                    .loadClass(1, classId, 0, stringNameId)
                    .heapDumpSegment(seg -> seg
                            .topLevelObjectClassDump(classId, stringFieldNameId)
                            .instanceDump(instance1, classId, objectFieldBytes)
                            .instanceDump(instance2, classId, objectFieldBytes))
                    .heapDumpEnd()
                    .writeTo(tmp, "instance-refs.hprof");

            Path indexDb = HeapDumpIndexPaths.indexFor(hprof);
            HprofIndex.IndexResult result;
            try (HprofMappedFile file = HprofMappedFile.open(hprof)) {
                result = HprofIndex.build(file, indexDb, FIXED_CLOCK);
            }

            assertEquals(2, result.outboundRefCount(), "one ref per instance");
            try (HeapView view = HeapView.open(indexDb)) {
                assertEquals(1, view.outboundRefs(instance1).size());
                assertEquals(target, view.outboundRefs(instance1).get(0).targetId());
                assertEquals(0, view.outboundRefs(instance1).get(0).fieldKind(),
                        "field_kind=0 for instance field");
                List<OutboundRefRow> incoming = view.inboundRefs(target);
                assertEquals(2, incoming.size());
                assertTrue(incoming.stream().anyMatch(r -> r.sourceId() == instance1));
                assertTrue(incoming.stream().anyMatch(r -> r.sourceId() == instance2));
            }
        }

        /** Class hierarchy: a Sub with one OBJECT field whose super (Top) also has one OBJECT field. */
        @Test
        void walksSuperClassFieldsForRefs(@TempDir Path tmp) throws IOException, SQLException {
            int idSize = 8;
            long topClass = 0xC001L;
            long subClass = 0xC002L;
            long target1 = 0xAAAAL;  // referenced via Sub's own field (declared first in field bytes)
            long target2 = 0xBBBBL;  // referenced via Top's field
            long subInstance = 0x1001L;

            // HPROF instance bytes: most-derived class fields first, then super.
            // Sub's one OBJECT field, then Top's one OBJECT field.
            byte[] fields = concat(idBytes(target1, idSize), idBytes(target2, idSize));

            Path hprof = SyntheticHprof.create("1.0.2", idSize, 0L)
                    .string(0xA001L, "Top")
                    .string(0xA002L, "Sub")
                    .string(0xA003L, "f")
                    .loadClass(1, topClass, 0, 0xA001L)
                    .loadClass(2, subClass, 0, 0xA002L)
                    .heapDumpSegment(seg -> seg
                            .topLevelObjectClassDump(topClass, 0xA003L)
                            .objectFieldClassDumpWithSuper(subClass, topClass, 0xA003L)
                            .instanceDump(subInstance, subClass, fields))
                    .heapDumpEnd()
                    .writeTo(tmp, "super-refs.hprof");

            Path indexDb = HeapDumpIndexPaths.indexFor(hprof);
            try (HprofMappedFile file = HprofMappedFile.open(hprof)) {
                HprofIndex.build(file, indexDb, FIXED_CLOCK);
            }

            try (HeapView view = HeapView.open(indexDb)) {
                List<OutboundRefRow> refs = view.outboundRefs(subInstance);
                assertEquals(2, refs.size(), "expect refs from both Sub's and Top's OBJECT fields");
                assertEquals(target1, refs.get(0).targetId());
                assertEquals(target2, refs.get(1).targetId());
            }
        }

        @Test
        void skipsNonObjectFields(@TempDir Path tmp) throws IOException, SQLException {
            int idSize = 8;
            long classId = 0xC001L;
            long instance = 0x1001L;

            // Layout is OBJECT (8 bytes) + INT (4 bytes) per simpleClassDump → won't work
            // because simpleClassDump declares only one INT field. Use the helper for "one OBJECT field".
            byte[] fields = idBytes(0xCAFEL, idSize);
            Path hprof = SyntheticHprof.create("1.0.2", idSize, 0L)
                    .string(0xA001L, "OnlyObj")
                    .string(0xA002L, "f")
                    .loadClass(1, classId, 0, 0xA001L)
                    .heapDumpSegment(seg -> seg
                            .topLevelObjectClassDump(classId, 0xA002L)
                            .instanceDump(instance, classId, fields))
                    .heapDumpEnd()
                    .writeTo(tmp, "obj-only.hprof");

            Path indexDb = HeapDumpIndexPaths.indexFor(hprof);
            try (HprofMappedFile file = HprofMappedFile.open(hprof)) {
                HprofIndex.build(file, indexDb, FIXED_CLOCK);
            }
            try (HeapView view = HeapView.open(indexDb)) {
                assertEquals(1, view.outboundRefs(instance).size());
            }
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

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] out = new byte[a.length + b.length];
        System.arraycopy(a, 0, out, 0, a.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        return out;
    }
}
