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

import cafe.jeffrey.profile.heapdump.model.ClassInstanceEntry;
import cafe.jeffrey.profile.heapdump.model.ClassInstancesResponse;
import cafe.jeffrey.profile.heapdump.parser.HeapDumpIndexPaths;
import cafe.jeffrey.profile.heapdump.parser.HeapView;
import cafe.jeffrey.profile.heapdump.parser.HprofIndex;
import cafe.jeffrey.profile.heapdump.parser.HprofMappedFile;
import cafe.jeffrey.profile.heapdump.parser.HprofTag;
import cafe.jeffrey.profile.heapdump.parser.SyntheticHprof;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
}
