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
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import cafe.jeffrey.profile.heapdump.model.InstanceDetail;
import cafe.jeffrey.profile.heapdump.model.InstanceField;
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

class InstanceDetailAnalyzerTest {

    private static final Clock CLOCK =
            Clock.fixed(Instant.ofEpochMilli(1L), ZoneOffset.UTC);
    private static final int ID_SIZE = 8;

    @Test
    void rendersInstanceWithMixedFields(@TempDir Path tmp) throws IOException, SQLException {
        long classId = 0xC001L;
        long target = 0x1001L;
        long refTarget = 0xAAAAL;

        Path hprof = SyntheticHprof.create("1.0.2", ID_SIZE, 0L)
                .string(0xA001L, "com.example.Foo")
                .string(0xA002L, "next")
                .string(0xA003L, "value")
                .loadClass(1, classId, 0, 0xA001L)
                .heapDumpSegment(seg -> seg
                        .classDumpWithFields(classId, 0L, 0L, ID_SIZE + 4,
                                new SyntheticHprof.SubBuilder.FieldSpec(0xA002L, HprofTag.BasicType.OBJECT),
                                new SyntheticHprof.SubBuilder.FieldSpec(0xA003L, HprofTag.BasicType.INT))
                        .instanceDump(target, classId, fields(refTarget, 42))
                        .instanceDump(refTarget, classId, fields(0L, 99)))
                .heapDumpEnd()
                .writeTo(tmp, "detail.hprof");
        Path indexDb = HeapDumpIndexPaths.indexFor(hprof);
        try (HprofMappedFile file = HprofMappedFile.open(hprof)) {
            HprofIndex.build(file, indexDb, CLOCK);
        }

        try (HprofMappedFile file = HprofMappedFile.open(hprof);
             HeapView view = HeapView.open(indexDb, file)) {
            Optional<InstanceDetail> opt = InstanceDetailAnalyzer.analyze(view, target);
            assertTrue(opt.isPresent());
            InstanceDetail detail = opt.get();
            assertEquals(target, detail.objectId());
            assertEquals("com.example.Foo", detail.className());
            assertEquals(2, detail.fields().size());

            InstanceField next = detail.fields().get(0);
            assertEquals("next", next.name());
            assertEquals("Object", next.type());
            assertFalse(next.isPrimitive());
            assertEquals(refTarget, next.referencedObjectId());
            assertEquals("com.example.Foo", next.referencedClassName());
            assertNotNull(next.value());

            InstanceField intField = detail.fields().get(1);
            assertEquals("value", intField.name());
            assertEquals("int", intField.type());
            assertTrue(intField.isPrimitive());
            assertEquals("42", intField.value());
            assertNull(intField.referencedObjectId());

            assertNull(detail.stringValue(), "Foo isn't java.lang.String → no decoded string");
        }
    }

    @Test
    void decodesStringValueForJavaLangString(@TempDir Path tmp) throws IOException, SQLException {
        long stringClass = 0xC001L;
        long s = 0x1001L;
        long charArray = 0x2000L;

        Path hprof = SyntheticHprof.create("1.0.2", ID_SIZE, 0L)
                .string(0xA001L, "java.lang.String")
                .string(0xA002L, "value")
                .string(0xA003L, "coder")
                .string(0xA004L, "hash")
                .loadClass(1, stringClass, 0, 0xA001L)
                .heapDumpSegment(seg -> seg
                        .classDumpWithFields(stringClass, 0L, 0L, ID_SIZE + 1 + 4,
                                new SyntheticHprof.SubBuilder.FieldSpec(0xA002L, HprofTag.BasicType.OBJECT),
                                new SyntheticHprof.SubBuilder.FieldSpec(0xA003L, HprofTag.BasicType.BYTE),
                                new SyntheticHprof.SubBuilder.FieldSpec(0xA004L, HprofTag.BasicType.INT))
                        .primitiveArrayDump(charArray, HprofTag.BasicType.BYTE,
                                "hello".getBytes(StandardCharsets.ISO_8859_1), 5)
                        .instanceDump(s, stringClass, stringFields(charArray, (byte) 0)))
                .heapDumpEnd()
                .writeTo(tmp, "string-detail.hprof");
        Path indexDb = HeapDumpIndexPaths.indexFor(hprof);
        try (HprofMappedFile file = HprofMappedFile.open(hprof)) {
            HprofIndex.build(file, indexDb, CLOCK);
        }
        try (HprofMappedFile file = HprofMappedFile.open(hprof);
             HeapView view = HeapView.open(indexDb, file)) {
            InstanceDetail detail = InstanceDetailAnalyzer.analyze(view, s).orElseThrow();
            assertEquals("hello", detail.stringValue());
            assertTrue(detail.displayValue().contains("hello"));
        }
    }

    @Test
    void unknownInstanceReturnsEmpty(@TempDir Path tmp) throws IOException, SQLException {
        Path hprof = SyntheticHprof.create("1.0.2", 8, 0L)
                .string(0xA001L, "X")
                .heapDumpEnd()
                .writeTo(tmp, "no-inst.hprof");
        Path indexDb = HeapDumpIndexPaths.indexFor(hprof);
        try (HprofMappedFile file = HprofMappedFile.open(hprof)) {
            HprofIndex.build(file, indexDb, CLOCK);
        }
        try (HeapView view = HeapView.open(indexDb)) {
            assertTrue(InstanceDetailAnalyzer.analyze(view, 0xDEADL).isEmpty());
        }
    }

    private static byte[] fields(long objectRef, int intValue) {
        try {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            DataOutputStream d = new DataOutputStream(b);
            d.writeLong(objectRef);
            d.writeInt(intValue);
            return b.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static byte[] stringFields(long valueArrayId, byte coder) {
        try {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            DataOutputStream d = new DataOutputStream(b);
            d.writeLong(valueArrayId);
            d.writeByte(coder);
            d.writeInt(0);
            return b.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
