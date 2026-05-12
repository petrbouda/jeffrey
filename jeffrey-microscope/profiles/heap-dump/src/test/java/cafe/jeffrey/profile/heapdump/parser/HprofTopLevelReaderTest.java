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
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HprofTopLevelReaderTest {

    private static final class Capture implements HprofTopLevelReader.Listener {
        final List<HprofRecord.Top> records = new ArrayList<>();
        final List<ParseWarning> warnings = new ArrayList<>();

        @Override
        public void onRecord(HprofRecord.Top record) {
            records.add(record);
        }

        @Override
        public void onWarning(ParseWarning warning) {
            warnings.add(warning);
        }
    }

    @Nested
    class HappyPath {

        @Test
        void emitsStringLoadClassAndHeapDumpRegionInOrder(@TempDir Path tmp) throws IOException {
            Path hprof = SyntheticHprof.create("1.0.2", 8, 0L)
                    .string(0xAAAAL, "java.lang.String")
                    .loadClass(1, 0xBBBBL, 0, 0xAAAAL)
                    .heapDumpSegment(seg -> {
                    })
                    .heapDumpEnd()
                    .writeTo(tmp, "happy.hprof");

            Capture cap = new Capture();
            try (HprofMappedFile file = HprofMappedFile.open(hprof)) {
                HprofTopLevelReader.read(file, cap);
            }

            assertEquals(0, cap.warnings.size(), () -> "warnings: " + cap.warnings);
            assertEquals(4, cap.records.size());

            HprofRecord.HprofString s = assertInstanceOf(HprofRecord.HprofString.class, cap.records.get(0));
            assertEquals(0xAAAAL, s.stringId());
            assertArrayEquals("java.lang.String".getBytes(StandardCharsets.UTF_8), s.utf8());

            HprofRecord.LoadClass lc = assertInstanceOf(HprofRecord.LoadClass.class, cap.records.get(1));
            assertEquals(1, lc.classSerial());
            assertEquals(0xBBBBL, lc.classId());
            assertEquals(0xAAAAL, lc.nameStringId());

            HprofRecord.HeapDumpRegion region = assertInstanceOf(
                    HprofRecord.HeapDumpRegion.class, cap.records.get(2));
            assertTrue(region.isSegment());
            assertEquals(0L, region.byteLength());

            assertInstanceOf(HprofRecord.OpaqueTop.class, cap.records.get(3));
        }

        @Test
        void treatsHeapDumpAsRegion(@TempDir Path tmp) throws IOException {
            Path hprof = SyntheticHprof.create("1.0.2", 8, 0L)
                    .topLevel(HprofTag.Top.HEAP_DUMP, new byte[0])
                    .writeTo(tmp, "heap-dump.hprof");

            Capture cap = new Capture();
            try (HprofMappedFile file = HprofMappedFile.open(hprof)) {
                HprofTopLevelReader.read(file, cap);
            }

            HprofRecord.HeapDumpRegion region = assertInstanceOf(
                    HprofRecord.HeapDumpRegion.class, cap.records.get(0));
            assertEquals(false, region.isSegment(),
                    "HEAP_DUMP (0x0C) should produce a non-segment region");
        }

        @Test
        void emitsOpaqueTopForUnknownTag(@TempDir Path tmp) throws IOException {
            Path hprof = SyntheticHprof.create("1.0.2", 8, 0L)
                    .unknownTopLevel(0x77)
                    .writeTo(tmp, "unknown.hprof");

            Capture cap = new Capture();
            try (HprofMappedFile file = HprofMappedFile.open(hprof)) {
                HprofTopLevelReader.read(file, cap);
            }

            assertEquals(0, cap.warnings.size());
            HprofRecord.OpaqueTop op = assertInstanceOf(HprofRecord.OpaqueTop.class, cap.records.get(0));
            assertEquals(0x77, op.tag());
        }

        @Test
        void worksWithIdSize4(@TempDir Path tmp) throws IOException {
            Path hprof = SyntheticHprof.create("1.0.1", 4, 0L)
                    .string(0xCAFEL, "X")
                    .loadClass(1, 0xBEEFL, 0, 0xCAFEL)
                    .writeTo(tmp, "id4.hprof");

            Capture cap = new Capture();
            try (HprofMappedFile file = HprofMappedFile.open(hprof)) {
                HprofTopLevelReader.read(file, cap);
            }

            assertEquals(0, cap.warnings.size());
            HprofRecord.LoadClass lc = (HprofRecord.LoadClass) cap.records.get(1);
            assertEquals(0xBEEFL, lc.classId());
        }
    }

    @Nested
    class CorruptInput {

        @Test
        void emitsErrorOnTruncatedRecordHeader(@TempDir Path tmp) throws IOException {
            // Append 5 bytes after header — short of the 9-byte record frame.
            Path hprof = SyntheticHprof.create("1.0.2", 8, 0L)
                    .appendRaw(new byte[]{0x01, 0x00, 0x00, 0x00, 0x00})
                    .writeTo(tmp, "trunc-header.hprof");

            Capture cap = new Capture();
            try (HprofMappedFile file = HprofMappedFile.open(hprof)) {
                HprofTopLevelReader.read(file, cap);
            }

            assertEquals(1, cap.warnings.size());
            assertEquals(ParseWarning.Severity.ERROR, cap.warnings.get(0).severity());
            assertTrue(cap.warnings.get(0).message().contains("Truncated record header"));
        }

        @Test
        void emitsErrorWhenRecordBodyExceedsEof(@TempDir Path tmp) throws IOException {
            // Construct a record header claiming 100-byte body but provide none.
            byte[] header = new byte[]{0x01, 0, 0, 0, 0, 0, 0, 0, 100};
            Path hprof = SyntheticHprof.create("1.0.2", 8, 0L)
                    .appendRaw(header)
                    .writeTo(tmp, "trunc-body.hprof");

            Capture cap = new Capture();
            try (HprofMappedFile file = HprofMappedFile.open(hprof)) {
                HprofTopLevelReader.read(file, cap);
            }

            assertEquals(1, cap.warnings.size());
            assertTrue(cap.warnings.get(0).message().contains("extends beyond EOF"));
        }
    }
}
