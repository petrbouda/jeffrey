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

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HprofSubRecordReaderTest {

    private static final class Capture implements HprofSubRecordReader.Listener {
        final List<HprofRecord.Sub> records = new ArrayList<>();
        final List<ParseWarning> warnings = new ArrayList<>();

        @Override
        public void onRecord(HprofRecord.Sub record) {
            records.add(record);
        }

        @Override
        public void onWarning(ParseWarning warning) {
            warnings.add(warning);
        }
    }

    /** Open the synthesised hprof, find the first heap-dump segment, run the sub-record reader against it. */
    private static List<HprofRecord.Sub> readSegment(Path hprof, Capture cap) throws IOException {
        try (HprofMappedFile file = HprofMappedFile.open(hprof)) {
            HprofRecord.HeapDumpRegion[] region = new HprofRecord.HeapDumpRegion[1];
            HprofTopLevelReader.read(file, r -> {
                if (region[0] == null && r instanceof HprofRecord.HeapDumpRegion hr) {
                    region[0] = hr;
                }
            });
            assertTrue(region[0] != null, "synthesised dump must contain a heap dump region");
            HprofSubRecordReader.read(file, region[0].fileOffset(), region[0].byteLength(), cap);
        }
        return cap.records;
    }

    @Nested
    class GcRoots {

        @Test
        void emitsStickyClassRootWithInstanceIdOnly(@TempDir Path tmp) throws IOException {
            Path hprof = SyntheticHprof.create("1.0.2", 8, 0L)
                    .heapDumpSegment(seg -> seg.gcRoot(HprofTag.Sub.ROOT_STICKY_CLASS, 0xC0FFEEL))
                    .writeTo(tmp, "sticky.hprof");

            Capture cap = new Capture();
            readSegment(hprof, cap);

            assertEquals(0, cap.warnings.size());
            HprofRecord.GcRoot root = assertInstanceOf(HprofRecord.GcRoot.class, cap.records.get(0));
            assertEquals(HprofTag.Sub.ROOT_STICKY_CLASS, root.rootKind());
            assertEquals(0xC0FFEEL, root.instanceId());
            assertEquals(-1, root.threadSerial());
            assertEquals(-1, root.frameIndex());
        }

        @Test
        void emitsJavaFrameRootWithThreadAndFrame(@TempDir Path tmp) throws IOException {
            Path hprof = SyntheticHprof.create("1.0.2", 8, 0L)
                    .heapDumpSegment(seg -> seg.gcRootJavaFrame(0xDEADL, 7, 13))
                    .writeTo(tmp, "javaframe.hprof");

            Capture cap = new Capture();
            readSegment(hprof, cap);

            assertEquals(0, cap.warnings.size());
            HprofRecord.GcRoot root = (HprofRecord.GcRoot) cap.records.get(0);
            assertEquals(HprofTag.Sub.ROOT_JAVA_FRAME, root.rootKind());
            assertEquals(0xDEADL, root.instanceId());
            assertEquals(7, root.threadSerial());
            assertEquals(13, root.frameIndex());
        }

        @Test
        void emitsThreadObjectRoot(@TempDir Path tmp) throws IOException {
            Path hprof = SyntheticHprof.create("1.0.2", 8, 0L)
                    .heapDumpSegment(seg -> seg.gcRootThreadObject(0x1111L, 5, 9))
                    .writeTo(tmp, "thread-obj.hprof");

            Capture cap = new Capture();
            readSegment(hprof, cap);

            assertEquals(0, cap.warnings.size());
            HprofRecord.GcRoot root = (HprofRecord.GcRoot) cap.records.get(0);
            assertEquals(HprofTag.Sub.ROOT_THREAD_OBJECT, root.rootKind());
            assertEquals(0x1111L, root.instanceId());
            assertEquals(5, root.threadSerial());
        }
    }

    @Nested
    class ClassDumps {

        @Test
        void parsesMinimalClassDump(@TempDir Path tmp) throws IOException {
            Path hprof = SyntheticHprof.create("1.0.2", 8, 0L)
                    .heapDumpSegment(seg -> seg.simpleClassDump(0xC1L, 0xC2L, 0xC3L, 12, 0xF1L))
                    .writeTo(tmp, "class-dump.hprof");

            Capture cap = new Capture();
            readSegment(hprof, cap);

            assertEquals(0, cap.warnings.size());
            HprofRecord.ClassDump cd = assertInstanceOf(HprofRecord.ClassDump.class, cap.records.get(0));
            assertEquals(0xC1L, cd.classId());
            assertEquals(0xC2L, cd.superClassId());
            assertEquals(0xC3L, cd.classloaderId());
            assertEquals(12, cd.instanceSize());
            // simpleClassDump declares one int instance field
            assertEquals(4, cd.instanceFieldsByteLength());
        }
    }

    @Nested
    class InstanceDumps {

        @Test
        void parsesEmptyFieldedInstance(@TempDir Path tmp) throws IOException {
            Path hprof = SyntheticHprof.create("1.0.2", 8, 0L)
                    .heapDumpSegment(seg -> seg.instanceDump(0x100L, 0xC1L, new byte[0]))
                    .writeTo(tmp, "inst-empty.hprof");

            Capture cap = new Capture();
            readSegment(hprof, cap);

            assertEquals(0, cap.warnings.size());
            HprofRecord.InstanceDump i = (HprofRecord.InstanceDump) cap.records.get(0);
            assertEquals(0x100L, i.instanceId());
            assertEquals(0xC1L, i.classId());
            assertEquals(0, i.instanceFieldsByteLength());
        }

        @Test
        void parsesInstanceWithFieldBytes(@TempDir Path tmp) throws IOException {
            byte[] fieldBytes = new byte[]{1, 2, 3, 4, 5, 6, 7, 8};
            Path hprof = SyntheticHprof.create("1.0.2", 8, 0L)
                    .heapDumpSegment(seg -> seg.instanceDump(0x200L, 0xC1L, fieldBytes))
                    .writeTo(tmp, "inst-fields.hprof");

            Capture cap = new Capture();
            readSegment(hprof, cap);

            assertEquals(0, cap.warnings.size());
            HprofRecord.InstanceDump i = (HprofRecord.InstanceDump) cap.records.get(0);
            assertEquals(8, i.instanceFieldsByteLength());
        }
    }

    @Nested
    class ArrayDumps {

        @Test
        void parsesObjectArray(@TempDir Path tmp) throws IOException {
            long[] elements = {1L, 2L, 3L};
            Path hprof = SyntheticHprof.create("1.0.2", 8, 0L)
                    .heapDumpSegment(seg -> seg.objectArrayDump(0xA1L, 0xC1L, elements))
                    .writeTo(tmp, "obj-array.hprof");

            Capture cap = new Capture();
            readSegment(hprof, cap);

            assertEquals(0, cap.warnings.size());
            HprofRecord.ObjectArrayDump a = (HprofRecord.ObjectArrayDump) cap.records.get(0);
            assertEquals(0xA1L, a.arrayId());
            assertEquals(3, a.length());
            assertEquals(0xC1L, a.arrayClassId());
        }

        @Test
        void parsesPrimitiveIntArray(@TempDir Path tmp) throws IOException {
            // 4 ints = 16 bytes payload
            byte[] payload = new byte[16];
            Path hprof = SyntheticHprof.create("1.0.2", 8, 0L)
                    .heapDumpSegment(seg -> seg.primitiveArrayDump(
                            0xB1L, HprofTag.BasicType.INT, payload, 4))
                    .writeTo(tmp, "prim-array.hprof");

            Capture cap = new Capture();
            readSegment(hprof, cap);

            assertEquals(0, cap.warnings.size());
            HprofRecord.PrimitiveArrayDump a = (HprofRecord.PrimitiveArrayDump) cap.records.get(0);
            assertEquals(4, a.length());
            assertEquals(HprofTag.BasicType.INT, a.elementType());
        }
    }

    @Nested
    class MultiRecordSegment {

        @Test
        void iteratesAllRecordsInOrder(@TempDir Path tmp) throws IOException {
            Path hprof = SyntheticHprof.create("1.0.2", 8, 0L)
                    .heapDumpSegment(seg -> seg
                            .gcRoot(HprofTag.Sub.ROOT_STICKY_CLASS, 0xC1L)
                            .simpleClassDump(0xC1L, 0L, 0L, 8, 0xF1L)
                            .instanceDump(0x101L, 0xC1L, new byte[]{0, 0, 0, 0})
                            .objectArrayDump(0xA1L, 0xC1L, new long[]{0x101L})
                            .primitiveArrayDump(0xB1L, HprofTag.BasicType.BYTE, new byte[]{1, 2, 3}, 3))
                    .writeTo(tmp, "multi.hprof");

            Capture cap = new Capture();
            readSegment(hprof, cap);

            assertEquals(0, cap.warnings.size(), () -> "warnings: " + cap.warnings);
            assertEquals(5, cap.records.size());
            assertInstanceOf(HprofRecord.GcRoot.class, cap.records.get(0));
            assertInstanceOf(HprofRecord.ClassDump.class, cap.records.get(1));
            assertInstanceOf(HprofRecord.InstanceDump.class, cap.records.get(2));
            assertInstanceOf(HprofRecord.ObjectArrayDump.class, cap.records.get(3));
            assertInstanceOf(HprofRecord.PrimitiveArrayDump.class, cap.records.get(4));
        }
    }

    @Nested
    class CorruptInput {

        @Test
        void emitsErrorAndStopsOnUnknownSubTag(@TempDir Path tmp) throws IOException {
            Path hprof = SyntheticHprof.create("1.0.2", 8, 0L)
                    .heapDumpSegment(seg -> seg
                            .gcRoot(HprofTag.Sub.ROOT_STICKY_CLASS, 0xC1L)
                            .unknownSub(0x55))
                    .writeTo(tmp, "unknown-sub.hprof");

            Capture cap = new Capture();
            readSegment(hprof, cap);

            // ROOT_STICKY_CLASS decodes successfully; the unknown tag stops scanning.
            assertEquals(1, cap.records.size());
            assertEquals(1, cap.warnings.size());
            assertTrue(cap.warnings.get(0).message().contains("Unknown or truncated"));
        }
    }
}
