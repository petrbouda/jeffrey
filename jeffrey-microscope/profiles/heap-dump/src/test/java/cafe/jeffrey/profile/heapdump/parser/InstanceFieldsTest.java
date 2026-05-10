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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class InstanceFieldsTest {

    private static final Clock CLOCK =
            Clock.fixed(Instant.ofEpochMilli(1L), ZoneOffset.UTC);

    @Test
    void persistsClassFieldDescriptorsByName(@TempDir Path tmp) throws IOException, SQLException {
        long classId = 0xC001L;
        Path hprof = SyntheticHprof.create("1.0.2", 8, 0L)
                .string(0xA001L, "Holder")
                .string(0xA002L, "ref")
                .loadClass(1, classId, 0, 0xA001L)
                .heapDumpSegment(seg -> seg
                        .topLevelObjectClassDump(classId, 0xA002L))
                .heapDumpEnd()
                .writeTo(tmp, "fields.hprof");
        Path indexDb = HeapDumpIndexPaths.indexFor(hprof);
        try (HprofMappedFile file = HprofMappedFile.open(hprof)) {
            HprofIndex.build(file, indexDb, CLOCK);
        }
        try (HeapView view = HeapView.open(indexDb)) {
            List<InstanceFieldDescriptor> fields = view.instanceFields(classId);
            assertEquals(1, fields.size());
            assertEquals("ref", fields.get(0).name());
            assertEquals(HprofTag.BasicType.OBJECT, fields.get(0).basicType());
        }
    }

    @Test
    void readsTypedFieldValuesAcrossClassChain(@TempDir Path tmp) throws IOException, SQLException {
        int idSize = 8;
        long topClass = 0xC001L;
        long subClass = 0xC002L;
        long subInstance = 0x1001L;
        long target1 = 0xAAAAL;
        long target2 = 0xBBBBL;

        // Sub's OBJECT field (target1), then Top's OBJECT field (target2) — both 8-byte ids.
        byte[] fields = concat(idBytes(target1, idSize), idBytes(target2, idSize));

        Path hprof = SyntheticHprof.create("1.0.2", idSize, 0L)
                .string(0xA001L, "Top")
                .string(0xA002L, "Sub")
                .string(0xA003L, "topField")
                .string(0xA004L, "subField")
                .loadClass(1, topClass, 0, 0xA001L)
                .loadClass(2, subClass, 0, 0xA002L)
                .heapDumpSegment(seg -> seg
                        .topLevelObjectClassDump(topClass, 0xA003L)
                        .objectFieldClassDumpWithSuper(subClass, topClass, 0xA004L)
                        .instanceDump(subInstance, subClass, fields))
                .heapDumpEnd()
                .writeTo(tmp, "field-read.hprof");

        Path indexDb = HeapDumpIndexPaths.indexFor(hprof);
        try (HprofMappedFile file = HprofMappedFile.open(hprof)) {
            HprofIndex.build(file, indexDb, CLOCK);
        }

        try (HprofMappedFile file = HprofMappedFile.open(hprof);
             HeapView view = HeapView.open(indexDb, file)) {
            List<InstanceFieldValue> values = view.readInstanceFields(subInstance);
            assertEquals(2, values.size(),
                    "expected one value per declared field, walked across the super chain");
            // HPROF layout: most-derived class fields first, then super.
            assertEquals("subField", values.get(0).name());
            assertEquals(target1, values.get(0).value());
            assertEquals("topField", values.get(1).name());
            assertEquals(target2, values.get(1).value());
        }
    }

    @Test
    void readInstanceFieldsThrowsWhenHprofNotAttached(@TempDir Path tmp) throws IOException, SQLException {
        long classId = 0xC001L;
        long instanceId = 0x1001L;
        Path hprof = SyntheticHprof.create("1.0.2", 8, 0L)
                .string(0xA001L, "X")
                .string(0xA002L, "f")
                .loadClass(1, classId, 0, 0xA001L)
                .heapDumpSegment(seg -> seg
                        .topLevelObjectClassDump(classId, 0xA002L)
                        .instanceDump(instanceId, classId, idBytes(0L, 8)))
                .heapDumpEnd()
                .writeTo(tmp, "no-hprof.hprof");
        Path indexDb = HeapDumpIndexPaths.indexFor(hprof);
        try (HprofMappedFile file = HprofMappedFile.open(hprof)) {
            HprofIndex.build(file, indexDb, CLOCK);
        }
        try (HeapView view = HeapView.open(indexDb)) {
            assertThrows(IllegalStateException.class, () -> view.readInstanceFields(instanceId));
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
