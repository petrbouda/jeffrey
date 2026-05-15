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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import cafe.jeffrey.profile.heapdump.model.ClassInstanceEntry;
import cafe.jeffrey.profile.heapdump.model.ClassInstancesResponse;
import cafe.jeffrey.profile.heapdump.model.InstanceSortBy;
import cafe.jeffrey.profile.heapdump.parser.DominatorTreeBuilder;
import cafe.jeffrey.profile.heapdump.parser.HeapDumpIndexPaths;
import cafe.jeffrey.profile.heapdump.parser.HeapView;
import cafe.jeffrey.profile.heapdump.parser.HprofIndex;
import cafe.jeffrey.profile.heapdump.parser.HprofMappedFile;
import cafe.jeffrey.profile.heapdump.parser.HprofTag;
import cafe.jeffrey.profile.heapdump.parser.SyntheticHprof;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClassInstanceBrowserAnalyzerTest {

    private static final Clock CLOCK =
            Clock.fixed(Instant.ofEpochMilli(1L), ZoneOffset.UTC);
    private static final int ID_SIZE = 8;
    private static final int INSTANCE_BYTES = ID_SIZE + 4; // OBJECT + INT

    @Test
    void browsesInstancesAndDecodesFields(@TempDir Path tmp) throws IOException, SQLException {
        long classId = 0xC001L;
        long ref1 = 0xAAAAL;

        Path hprof = SyntheticHprof.create("1.0.2", ID_SIZE, 0L)
                .string(0xA001L, "com.example.Foo")
                .string(0xA002L, "next")
                .string(0xA003L, "count")
                .loadClass(1, classId, 0, 0xA001L)
                .heapDumpSegment(seg -> seg
                        .classDumpWithFields(classId, 0L, 0L, INSTANCE_BYTES,
                                new SyntheticHprof.SubBuilder.FieldSpec(0xA002L, HprofTag.BasicType.OBJECT),
                                new SyntheticHprof.SubBuilder.FieldSpec(0xA003L, HprofTag.BasicType.INT))
                        .instanceDump(0x1001L, classId, fields(ref1, 42))
                        .instanceDump(0x1002L, classId, fields(0L, 7)))
                .heapDumpEnd()
                .writeTo(tmp, "browse.hprof");

        Path indexDb = HeapDumpIndexPaths.indexFor(hprof);
        try (HprofMappedFile file = HprofMappedFile.open(hprof)) {
            HprofIndex.build(file, indexDb, CLOCK);
        }

        try (HprofMappedFile file = HprofMappedFile.open(hprof);
             HeapView view = HeapView.open(indexDb, file)) {
            ClassInstancesResponse response = ClassInstanceBrowserAnalyzer.browse(view, classId);
            assertEquals("com.example.Foo", response.className());
            assertEquals(2, response.totalInstances());
            assertFalse(response.hasMore());
            assertEquals(2, response.instances().size());

            ClassInstanceEntry first = response.instances().get(0);
            assertEquals(0x1001L, first.objectId());
            assertEquals("0x" + Long.toHexString(ref1), first.objectParams().get("next"));
            assertEquals("42", first.objectParams().get("count"));

            ClassInstanceEntry second = response.instances().get(1);
            assertEquals("null", second.objectParams().get("next"),
                    "OBJECT field with id 0 is rendered as null");
            assertEquals("7", second.objectParams().get("count"));
        }
    }

    @Test
    void honorsOffsetAndLimit(@TempDir Path tmp) throws IOException, SQLException {
        long classId = 0xC001L;

        Path hprof = SyntheticHprof.create("1.0.2", ID_SIZE, 0L)
                .string(0xA001L, "X")
                .string(0xA002L, "f")
                .loadClass(1, classId, 0, 0xA001L)
                .heapDumpSegment(seg -> {
                    seg.classDumpWithFields(classId, 0L, 0L, 4,
                            new SyntheticHprof.SubBuilder.FieldSpec(0xA002L, HprofTag.BasicType.INT));
                    for (int i = 0; i < 5; i++) {
                        byte[] body = new byte[]{0, 0, 0, (byte) i};
                        seg.instanceDump(0x1000L + i, classId, body);
                    }
                })
                .heapDumpEnd()
                .writeTo(tmp, "page.hprof");
        Path indexDb = HeapDumpIndexPaths.indexFor(hprof);
        try (HprofMappedFile file = HprofMappedFile.open(hprof)) {
            HprofIndex.build(file, indexDb, CLOCK);
        }
        try (HprofMappedFile file = HprofMappedFile.open(hprof);
             HeapView view = HeapView.open(indexDb, file)) {
            ClassInstancesResponse page1 = ClassInstanceBrowserAnalyzer.browse(view, classId, 0, 2);
            assertEquals(2, page1.instances().size());
            assertTrue(page1.hasMore());
            assertEquals(5, page1.totalInstances());

            ClassInstancesResponse page2 = ClassInstanceBrowserAnalyzer.browse(view, classId, 4, 2);
            assertEquals(1, page2.instances().size());
            assertFalse(page2.hasMore());
        }
    }

    @Test
    void sortsByRetainedSizeDescendingNullsLast(@TempDir Path tmp) throws IOException, SQLException {
        long classId = 0xC001L;
        long bigInstance = 0x1001L;
        long midInstance = 0x1002L;
        long smallInstance = 0x1003L;
        long bigArr = 0x9001L;
        long midArr = 0x9002L;
        long smallArr = 0x9003L;

        Path hprof = SyntheticHprof.create("1.0.2", ID_SIZE, 0L)
                .string(0xA001L, "com.example.Holder")
                .string(0xA002L, "data")
                .loadClass(1, classId, 0, 0xA001L)
                .heapDumpSegment(seg -> seg
                        .classDumpWithFields(classId, 0L, 0L, ID_SIZE,
                                new SyntheticHprof.SubBuilder.FieldSpec(0xA002L, HprofTag.BasicType.OBJECT))
                        .gcRoot(HprofTag.Sub.ROOT_STICKY_CLASS, bigInstance)
                        .gcRoot(HprofTag.Sub.ROOT_STICKY_CLASS, midInstance)
                        .gcRoot(HprofTag.Sub.ROOT_STICKY_CLASS, smallInstance)
                        .primitiveArrayDump(bigArr, HprofTag.BasicType.BYTE, new byte[2048], 2048)
                        .primitiveArrayDump(midArr, HprofTag.BasicType.BYTE, new byte[512], 512)
                        .primitiveArrayDump(smallArr, HprofTag.BasicType.BYTE, new byte[64], 64)
                        .instanceDump(bigInstance, classId, idBytes(bigArr))
                        .instanceDump(midInstance, classId, idBytes(midArr))
                        .instanceDump(smallInstance, classId, idBytes(smallArr)))
                .heapDumpEnd()
                .writeTo(tmp, "by-retained.hprof");

        Path indexDb = HeapDumpIndexPaths.indexFor(hprof);
        try (HprofMappedFile file = HprofMappedFile.open(hprof)) {
            HprofIndex.build(file, indexDb, CLOCK);
        }
        DominatorTreeBuilder.build(indexDb);

        try (HprofMappedFile file = HprofMappedFile.open(hprof);
             HeapView view = HeapView.open(indexDb, file)) {
            ClassInstancesResponse response =
                    ClassInstanceBrowserAnalyzer.browse(view, classId, 0, 10, InstanceSortBy.RETAINED_SIZE);

            assertEquals("com.example.Holder", response.className());
            assertEquals(3, response.instances().size());

            ClassInstanceEntry first = response.instances().get(0);
            ClassInstanceEntry second = response.instances().get(1);
            ClassInstanceEntry third = response.instances().get(2);
            assertEquals(bigInstance, first.objectId(), "biggest-retained instance must come first");
            assertEquals(midInstance, second.objectId());
            assertEquals(smallInstance, third.objectId());
            assertNotNull(first.retainedSize());
            assertNotNull(second.retainedSize());
            assertNotNull(third.retainedSize());
            assertTrue(first.retainedSize() > second.retainedSize());
            assertTrue(second.retainedSize() > third.retainedSize());
        }
    }

    @Test
    void populatesContentPreviewForStringInstances(@TempDir Path tmp) throws IOException, SQLException {
        long stringClass = 0xC001L;
        long s1 = 0x1001L;
        long s2 = 0x1002L;
        long bytes1 = 0x2001L;
        long bytes2 = 0x2002L;

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
                        .primitiveArrayDump(bytes1, HprofTag.BasicType.BYTE,
                                "hello".getBytes(StandardCharsets.ISO_8859_1), 5)
                        .primitiveArrayDump(bytes2, HprofTag.BasicType.BYTE,
                                "world".getBytes(StandardCharsets.ISO_8859_1), 5)
                        .instanceDump(s1, stringClass, stringFields(bytes1, (byte) 0))
                        .instanceDump(s2, stringClass, stringFields(bytes2, (byte) 0)))
                .heapDumpEnd()
                .writeTo(tmp, "string-preview.hprof");

        Path indexDb = HeapDumpIndexPaths.indexFor(hprof);
        try (HprofMappedFile file = HprofMappedFile.open(hprof)) {
            HprofIndex.build(file, indexDb, CLOCK);
        }
        try (HprofMappedFile file = HprofMappedFile.open(hprof);
             HeapView view = HeapView.open(indexDb, file)) {
            ClassInstancesResponse response = ClassInstanceBrowserAnalyzer.browse(view, stringClass);
            assertEquals(2, response.instances().size());
            assertEquals("\"hello\"", response.instances().get(0).contentPreview());
            assertEquals("\"world\"", response.instances().get(1).contentPreview());
        }
    }

    @Test
    void populatesContentPreviewForBoxedIntegers(@TempDir Path tmp) throws IOException, SQLException {
        long integerClass = 0xC001L;

        Path hprof = SyntheticHprof.create("1.0.2", ID_SIZE, 0L)
                .string(0xA001L, "java.lang.Integer")
                .string(0xA002L, "value")
                .loadClass(1, integerClass, 0, 0xA001L)
                .heapDumpSegment(seg -> seg
                        .classDumpWithFields(integerClass, 0L, 0L, 4,
                                new SyntheticHprof.SubBuilder.FieldSpec(0xA002L, HprofTag.BasicType.INT))
                        .instanceDump(0x1001L, integerClass, new byte[]{0, 0, 0, 42})
                        .instanceDump(0x1002L, integerClass, new byte[]{0, 0, 0, 7}))
                .heapDumpEnd()
                .writeTo(tmp, "integer-preview.hprof");

        Path indexDb = HeapDumpIndexPaths.indexFor(hprof);
        try (HprofMappedFile file = HprofMappedFile.open(hprof)) {
            HprofIndex.build(file, indexDb, CLOCK);
        }
        try (HprofMappedFile file = HprofMappedFile.open(hprof);
             HeapView view = HeapView.open(indexDb, file)) {
            ClassInstancesResponse response = ClassInstanceBrowserAnalyzer.browse(view, integerClass);
            assertEquals(2, response.instances().size());
            // Boxed wrappers render unquoted.
            assertEquals("42", response.instances().get(0).contentPreview());
            assertEquals("7", response.instances().get(1).contentPreview());
        }
    }

    @Test
    void contentPreviewIsNullForClassesWithoutKnownLayout(@TempDir Path tmp) throws IOException, SQLException {
        long classId = 0xC001L;

        Path hprof = SyntheticHprof.create("1.0.2", ID_SIZE, 0L)
                .string(0xA001L, "com.example.Foo")
                .string(0xA002L, "count")
                .loadClass(1, classId, 0, 0xA001L)
                .heapDumpSegment(seg -> seg
                        .classDumpWithFields(classId, 0L, 0L, 4,
                                new SyntheticHprof.SubBuilder.FieldSpec(0xA002L, HprofTag.BasicType.INT))
                        .instanceDump(0x1001L, classId, new byte[]{0, 0, 0, 99}))
                .heapDumpEnd()
                .writeTo(tmp, "no-preview.hprof");

        Path indexDb = HeapDumpIndexPaths.indexFor(hprof);
        try (HprofMappedFile file = HprofMappedFile.open(hprof)) {
            HprofIndex.build(file, indexDb, CLOCK);
        }
        try (HprofMappedFile file = HprofMappedFile.open(hprof);
             HeapView view = HeapView.open(indexDb, file)) {
            ClassInstancesResponse response = ClassInstanceBrowserAnalyzer.browse(view, classId);
            assertEquals(1, response.instances().size());
            assertEquals(null, response.instances().get(0).contentPreview(),
                    "no preview for unknown class layouts — null beats a meaningless hex blob");
        }
    }

    @Test
    void retainedSortRequiresDominatorTree(@TempDir Path tmp) throws IOException, SQLException {
        long classId = 0xC001L;
        Path hprof = SyntheticHprof.create("1.0.2", ID_SIZE, 0L)
                .string(0xA001L, "X")
                .loadClass(1, classId, 0, 0xA001L)
                .heapDumpSegment(seg -> seg
                        .classDumpWithFields(classId, 0L, 0L, 4,
                                new SyntheticHprof.SubBuilder.FieldSpec(0xA001L, HprofTag.BasicType.INT))
                        .instanceDump(0x1001L, classId, new byte[]{0, 0, 0, 1}))
                .heapDumpEnd()
                .writeTo(tmp, "no-dom.hprof");
        Path indexDb = HeapDumpIndexPaths.indexFor(hprof);
        try (HprofMappedFile file = HprofMappedFile.open(hprof)) {
            HprofIndex.build(file, indexDb, CLOCK);
        }
        try (HprofMappedFile file = HprofMappedFile.open(hprof);
             HeapView view = HeapView.open(indexDb, file)) {
            assertThrows(IllegalStateException.class, () ->
                    ClassInstanceBrowserAnalyzer.browse(view, classId, 0, 10, InstanceSortBy.RETAINED_SIZE));
        }
    }

    @Test
    void unknownClassReturnsEmptyWithUnknownLabel(@TempDir Path tmp) throws IOException, SQLException {
        Path hprof = SyntheticHprof.create("1.0.2", 8, 0L)
                .string(0xA001L, "X")
                .heapDumpEnd()
                .writeTo(tmp, "no-class.hprof");
        Path indexDb = HeapDumpIndexPaths.indexFor(hprof);
        try (HprofMappedFile file = HprofMappedFile.open(hprof)) {
            HprofIndex.build(file, indexDb, CLOCK);
        }
        try (HeapView view = HeapView.open(indexDb)) {
            ClassInstancesResponse response = ClassInstanceBrowserAnalyzer.browse(view, 0xDEADL);
            assertTrue(response.className().contains("unknown"));
            assertEquals(0, response.totalInstances());
            assertTrue(response.instances().isEmpty());
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

    private static byte[] idBytes(long ref) {
        try {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            DataOutputStream d = new DataOutputStream(b);
            d.writeLong(ref);
            return b.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Java 9+ {@code java.lang.String} field block: OBJECT value array reference,
     * BYTE coder (0 = Latin1, 1 = UTF-16), INT hash.
     */
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
