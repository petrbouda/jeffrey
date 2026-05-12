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

import cafe.jeffrey.profile.heapdump.model.StringAnalysisReport;
import cafe.jeffrey.profile.heapdump.parser.HeapDumpIndexPaths;
import cafe.jeffrey.profile.heapdump.parser.HeapView;
import cafe.jeffrey.profile.heapdump.parser.HprofIndex;
import cafe.jeffrey.profile.heapdump.parser.HprofMappedFile;
import cafe.jeffrey.profile.heapdump.parser.HprofTag;
import cafe.jeffrey.profile.heapdump.parser.SyntheticHprof;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StringAnalyzerTest {

    private static final Clock CLOCK =
            Clock.fixed(Instant.ofEpochMilli(1L), ZoneOffset.UTC);

    private static final int ID_SIZE = 8;
    // String layout (Java 9+): value:OBJECT(8) + coder:BYTE(1) + hash:INT(4) = 13 bytes
    private static final int STRING_INSTANCE_BYTES = ID_SIZE + 1 + 4;

    @Test
    void detectsAlreadyDeduplicatedStrings(@TempDir Path tmp) throws IOException, SQLException {
        long stringClass = 0xC001L;
        long sharedArray = 0x9001L;
        long s1 = 0x1001L;
        long s2 = 0x1002L;

        // Two String instances point at the same byte[] backing array → already dedup'd.
        Path hprof = SyntheticHprof.create("1.0.2", ID_SIZE, 0L)
                .string(0xA001L, "java.lang.String")
                .string(0xA002L, "value")
                .string(0xA003L, "coder")
                .string(0xA004L, "hash")
                .loadClass(1, stringClass, 0, 0xA001L)
                .heapDumpSegment(seg -> seg
                        .classDumpWithFields(stringClass, 0L, 0L, STRING_INSTANCE_BYTES,
                                new SyntheticHprof.SubBuilder.FieldSpec(0xA002L, HprofTag.BasicType.OBJECT),
                                new SyntheticHprof.SubBuilder.FieldSpec(0xA003L, HprofTag.BasicType.BYTE),
                                new SyntheticHprof.SubBuilder.FieldSpec(0xA004L, HprofTag.BasicType.INT))
                        .primitiveArrayDump(sharedArray, HprofTag.BasicType.BYTE,
                                "hi".getBytes(StandardCharsets.ISO_8859_1), 2)
                        .instanceDump(s1, stringClass, stringFieldBytes(sharedArray, (byte) 0))
                        .instanceDump(s2, stringClass, stringFieldBytes(sharedArray, (byte) 0)))
                .heapDumpEnd()
                .writeTo(tmp, "shared.hprof");

        Path indexDb = HeapDumpIndexPaths.indexFor(hprof);
        try (HprofMappedFile file = HprofMappedFile.open(hprof)) {
            HprofIndex.build(file, indexDb, CLOCK);
        }

        try (HprofMappedFile file = HprofMappedFile.open(hprof);
             HeapView view = HeapView.open(indexDb, file)) {
            StringAnalysisReport r = StringAnalyzer.analyze(view, 10);
            assertEquals(2, r.totalStrings());
            assertEquals(1, r.uniqueArrays());
            assertEquals(1, r.sharedArrays());
            assertEquals(2, r.totalSharedStrings());
            assertEquals(1, r.alreadyDeduplicated().size());
            assertEquals("hi", r.alreadyDeduplicated().get(0).content());
            assertEquals(2, r.alreadyDeduplicated().get(0).count());
            assertTrue(r.opportunities().isEmpty(), "no distinct arrays with same content");
        }
    }

    @Test
    void detectsDeduplicationOpportunities(@TempDir Path tmp) throws IOException, SQLException {
        long stringClass = 0xC001L;
        long array1 = 0x9001L;
        long array2 = 0x9002L;
        long s1 = 0x1001L;
        long s2 = 0x1002L;

        // Same content "abc" in two different byte[] → dedup opportunity.
        Path hprof = SyntheticHprof.create("1.0.2", ID_SIZE, 0L)
                .string(0xA001L, "java.lang.String")
                .string(0xA002L, "value")
                .string(0xA003L, "coder")
                .string(0xA004L, "hash")
                .loadClass(1, stringClass, 0, 0xA001L)
                .heapDumpSegment(seg -> seg
                        .classDumpWithFields(stringClass, 0L, 0L, STRING_INSTANCE_BYTES,
                                new SyntheticHprof.SubBuilder.FieldSpec(0xA002L, HprofTag.BasicType.OBJECT),
                                new SyntheticHprof.SubBuilder.FieldSpec(0xA003L, HprofTag.BasicType.BYTE),
                                new SyntheticHprof.SubBuilder.FieldSpec(0xA004L, HprofTag.BasicType.INT))
                        .primitiveArrayDump(array1, HprofTag.BasicType.BYTE,
                                "abc".getBytes(StandardCharsets.ISO_8859_1), 3)
                        .primitiveArrayDump(array2, HprofTag.BasicType.BYTE,
                                "abc".getBytes(StandardCharsets.ISO_8859_1), 3)
                        .instanceDump(s1, stringClass, stringFieldBytes(array1, (byte) 0))
                        .instanceDump(s2, stringClass, stringFieldBytes(array2, (byte) 0)))
                .heapDumpEnd()
                .writeTo(tmp, "opportunity.hprof");

        Path indexDb = HeapDumpIndexPaths.indexFor(hprof);
        try (HprofMappedFile file = HprofMappedFile.open(hprof)) {
            HprofIndex.build(file, indexDb, CLOCK);
        }

        try (HprofMappedFile file = HprofMappedFile.open(hprof);
             HeapView view = HeapView.open(indexDb, file)) {
            StringAnalysisReport r = StringAnalyzer.analyze(view, 10);
            assertEquals(2, r.totalStrings());
            assertEquals(2, r.uniqueArrays(), "two distinct backing arrays");
            assertEquals(0, r.sharedArrays(), "no array referenced by more than one String");
            assertEquals(1, r.opportunities().size());
            assertEquals("abc", r.opportunities().get(0).content());
            assertTrue(r.alreadyDeduplicated().isEmpty());
        }
    }

    @Test
    void emptyReportWhenNoStrings(@TempDir Path tmp) throws IOException, SQLException {
        Path hprof = SyntheticHprof.create("1.0.2", 8, 0L)
                .string(0xA001L, "x")
                .heapDumpEnd()
                .writeTo(tmp, "no-strings.hprof");
        Path indexDb = HeapDumpIndexPaths.indexFor(hprof);
        try (HprofMappedFile file = HprofMappedFile.open(hprof)) {
            HprofIndex.build(file, indexDb, CLOCK);
        }
        try (HprofMappedFile file = HprofMappedFile.open(hprof);
             HeapView view = HeapView.open(indexDb, file)) {
            StringAnalysisReport r = StringAnalyzer.analyze(view);
            assertEquals(0, r.totalStrings());
            assertTrue(r.alreadyDeduplicated().isEmpty());
            assertTrue(r.opportunities().isEmpty());
        }
    }

    /** Build the 13-byte field block for a Java 9+ String: value(8) + coder(1) + hash(4). */
    private static byte[] stringFieldBytes(long valueArrayId, byte coder) {
        try {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            DataOutputStream d = new DataOutputStream(b);
            d.writeLong(valueArrayId);
            d.writeByte(coder);
            d.writeInt(0); // hash
            return b.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
