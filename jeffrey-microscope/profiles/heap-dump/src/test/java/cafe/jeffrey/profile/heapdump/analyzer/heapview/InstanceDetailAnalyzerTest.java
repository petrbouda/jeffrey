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
import cafe.jeffrey.profile.heapdump.persistence.HeapDumpIndexPaths;
import cafe.jeffrey.profile.heapdump.view.HeapView;
import cafe.jeffrey.profile.heapdump.parser.HprofIndex;
import cafe.jeffrey.profile.heapdump.parser.HprofMappedFile;
import cafe.jeffrey.profile.heapdump.view.HprofTag;
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
            // Non-null object refs intentionally have a null value — the
            // referencedClassName + referencedObjectId carry the real data.
            assertNull(next.value());

            InstanceField intField = detail.fields().get(1);
            assertEquals("value", intField.name());
            assertEquals("int", intField.type());
            assertTrue(intField.isPrimitive());
            assertEquals("42", intField.value());
            assertNull(intField.referencedObjectId());

            assertNull(detail.stringValue(), "Foo isn't java.lang.String → no decoded string");
        }
    }

    /**
     * Every reference field is resolved to its own class, all of them together: two fields pointing
     * at different classes, one pointing at an id that is no instance, and a null.
     */
    @Test
    void resolvesEachReferenceFieldToItsOwnClass(@TempDir Path tmp) throws IOException, SQLException {
        long holderClass = 0xC001L;
        long leftClass = 0xC002L;
        long rightClass = 0xC003L;
        long holder = 0x1001L;
        long left = 0x1002L;
        long right = 0x1003L;
        long dangling = 0xDEADL;

        Path hprof = SyntheticHprof.create("1.0.2", ID_SIZE, 0L)
                .string(0xA001L, "com.example.Holder")
                .string(0xA002L, "com.example.Left")
                .string(0xA003L, "com.example.Right")
                .string(0xA004L, "a")
                .string(0xA005L, "b")
                .string(0xA006L, "c")
                .string(0xA007L, "d")
                .loadClass(1, holderClass, 0, 0xA001L)
                .loadClass(2, leftClass, 0, 0xA002L)
                .loadClass(3, rightClass, 0, 0xA003L)
                .heapDumpSegment(seg -> seg
                        .classDumpWithFields(holderClass, 0L, 0L, 4 * ID_SIZE,
                                new SyntheticHprof.SubBuilder.FieldSpec(0xA004L, HprofTag.BasicType.OBJECT),
                                new SyntheticHprof.SubBuilder.FieldSpec(0xA005L, HprofTag.BasicType.OBJECT),
                                new SyntheticHprof.SubBuilder.FieldSpec(0xA006L, HprofTag.BasicType.OBJECT),
                                new SyntheticHprof.SubBuilder.FieldSpec(0xA007L, HprofTag.BasicType.OBJECT))
                        .classDumpWithFields(leftClass, 0L, 0L, 0)
                        .classDumpWithFields(rightClass, 0L, 0L, 0)
                        .instanceDump(holder, holderClass, ids(left, right, dangling, 0L))
                        .instanceDump(left, leftClass, new byte[0])
                        .instanceDump(right, rightClass, new byte[0]))
                .heapDumpEnd()
                .writeTo(tmp, "refs.hprof");
        Path indexDb = HeapDumpIndexPaths.indexFor(hprof);
        try (HprofMappedFile file = HprofMappedFile.open(hprof)) {
            HprofIndex.build(file, indexDb, CLOCK);
        }

        try (HprofMappedFile file = HprofMappedFile.open(hprof);
             HeapView view = HeapView.open(indexDb, file)) {
            InstanceDetail detail = InstanceDetailAnalyzer.analyze(view, holder).orElseThrow();
            assertEquals(4, detail.fields().size());

            assertEquals(left, detail.fields().get(0).referencedObjectId());
            assertEquals("com.example.Left", detail.fields().get(0).referencedClassName());
            assertEquals(right, detail.fields().get(1).referencedObjectId());
            assertEquals("com.example.Right", detail.fields().get(1).referencedClassName());
            assertEquals(dangling, detail.fields().get(2).referencedObjectId());
            assertNull(detail.fields().get(2).referencedClassName(), "an id with no instance has no class");
            assertNull(detail.fields().get(3).referencedObjectId());
            assertEquals("null", detail.fields().get(3).value());
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

    private static byte[] ids(long... refs) {
        try {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            DataOutputStream d = new DataOutputStream(b);
            for (long ref : refs) {
                d.writeLong(ref);
            }
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
