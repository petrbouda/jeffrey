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
import cafe.jeffrey.profile.heapdump.model.StringInstanceEntry;
import cafe.jeffrey.profile.heapdump.model.StringTopEntry;
import cafe.jeffrey.profile.heapdump.parser.BuildOptions;
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
            assertTrue(r.topByRetained().isEmpty());
            assertTrue(r.topInstancesByRetained().isEmpty());
        }
    }

    @Test
    void rankByRetainedSizeAcrossContents(@TempDir Path tmp) throws IOException, SQLException {
        long stringClass = 0xC001L;
        long arrSmall = 0x9001L;
        long arrBig = 0x9002L;
        long sSmall1 = 0x1001L;
        long sSmall2 = 0x1002L;
        long sBig = 0x1003L;

        // Two strings sharing a tiny array ("a") and one string with a much
        // larger payload — the larger one must outrank the tiny pair.
        // Keep under PREVIEW_MAX_CHARS so the returned content isn't truncated.
        byte[] bigContent = new byte[100];
        java.util.Arrays.fill(bigContent, (byte) 'B');

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
                        .primitiveArrayDump(arrSmall, HprofTag.BasicType.BYTE,
                                "a".getBytes(StandardCharsets.ISO_8859_1), 1)
                        .primitiveArrayDump(arrBig, HprofTag.BasicType.BYTE, bigContent, bigContent.length)
                        .instanceDump(sSmall1, stringClass, stringFieldBytes(arrSmall, (byte) 0))
                        .instanceDump(sSmall2, stringClass, stringFieldBytes(arrSmall, (byte) 0))
                        .instanceDump(sBig, stringClass, stringFieldBytes(arrBig, (byte) 0)))
                .heapDumpEnd()
                .writeTo(tmp, "top-retained.hprof");

        Path indexDb = HeapDumpIndexPaths.indexFor(hprof);
        try (HprofMappedFile file = HprofMappedFile.open(hprof)) {
            HprofIndex.build(file, indexDb, CLOCK);
        }

        try (HprofMappedFile file = HprofMappedFile.open(hprof);
             HeapView view = HeapView.open(indexDb, file)) {
            StringAnalysisReport r = StringAnalyzer.analyze(view, 10);

            assertEquals(2, r.topByRetained().size(), "two distinct contents");
            StringTopEntry first = r.topByRetained().get(0);
            StringTopEntry second = r.topByRetained().get(1);

            String bigStr = new String(bigContent, StandardCharsets.ISO_8859_1);
            assertEquals(bigStr, first.content(), "256-byte content must outrank 1-byte content");
            assertEquals(1, first.count());
            assertTrue(first.retainedSize() > second.retainedSize());

            assertEquals("a", second.content());
            assertEquals(2, second.count());
            // Retained = sum(string shallow) + distinct_arrays * array_shallow.
            // For "a": 2 strings sharing 1 array → strongly less than the 256-byte payload.
            assertTrue(second.arrayShallowSize() > 0);
            assertTrue(second.retainedSize() >= 2L * STRING_INSTANCE_BYTES);
        }
    }

    @Test
    void rankIndividualInstancesByRetained(@TempDir Path tmp) throws IOException, SQLException {
        long stringClass = 0xC001L;
        long sharedArr = 0x9001L;
        long uniqueArr = 0x9002L;
        long sShared1 = 0x1001L;
        long sShared2 = 0x1002L;
        long sLarge = 0x1003L;

        // Two Strings share a tiny array; one String holds a unique large
        // array. The unique String must lead — only it retains its array.
        byte[] bigContent = new byte[100];
        java.util.Arrays.fill(bigContent, (byte) 'B');

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
                        .primitiveArrayDump(sharedArr, HprofTag.BasicType.BYTE,
                                "a".getBytes(StandardCharsets.ISO_8859_1), 1)
                        .primitiveArrayDump(uniqueArr, HprofTag.BasicType.BYTE, bigContent, bigContent.length)
                        .instanceDump(sShared1, stringClass, stringFieldBytes(sharedArr, (byte) 0))
                        .instanceDump(sShared2, stringClass, stringFieldBytes(sharedArr, (byte) 0))
                        .instanceDump(sLarge, stringClass, stringFieldBytes(uniqueArr, (byte) 0)))
                .heapDumpEnd()
                .writeTo(tmp, "top-instances.hprof");

        Path indexDb = HeapDumpIndexPaths.indexFor(hprof);
        try (HprofMappedFile file = HprofMappedFile.open(hprof)) {
            HprofIndex.build(file, indexDb, CLOCK);
        }

        try (HprofMappedFile file = HprofMappedFile.open(hprof);
             HeapView view = HeapView.open(indexDb, file)) {
            StringAnalysisReport r = StringAnalyzer.analyze(view, 10);

            assertEquals(3, r.topInstancesByRetained().size(), "three String instances");
            StringInstanceEntry first = r.topInstancesByRetained().get(0);

            assertEquals(sLarge, first.instanceId(), "unique large String must rank first");
            assertEquals(1, first.arrayRefCount(), "its array is unique");
            assertTrue(first.retainedSize() >= bigContent.length,
                    "retained includes the big backing array");

            // Derive the actual String wrapper shallow size from the first row
            // (= retained - array shallow when refCount = 1). Both shared
            // instances must retain exactly that — their array stays alive
            // through the other shared instance.
            long wrapperShallow = first.retainedSize() - first.arrayShallowSize();
            assertTrue(wrapperShallow > 0, "wrapper shallow must be positive");
            for (int i = 1; i < r.topInstancesByRetained().size(); i++) {
                StringInstanceEntry tail = r.topInstancesByRetained().get(i);
                assertEquals(2, tail.arrayRefCount(), "tail entries share their array");
                assertEquals(wrapperShallow, tail.retainedSize(),
                        "shared instance retains only its wrapper");
            }
        }
    }

    @Test
    void decodesOverCapStringPreviewFromMmap(@TempDir Path tmp) throws IOException, SQLException {
        long stringClass = 0xC001L;
        long arrayId = 0x9001L;
        long stringId = 0x1001L;

        // Force the indexer to mark this String as over-cap by lowering the
        // threshold to 4. The analyzer must re-decode a bounded prefix from
        // the mmap so the Top Instances row still shows a real preview.
        String contentSource = "Hello, this is the bounded preview test payload";
        byte[] contentBytes = contentSource.getBytes(StandardCharsets.ISO_8859_1);

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
                        .primitiveArrayDump(arrayId, HprofTag.BasicType.BYTE,
                                contentBytes, contentBytes.length)
                        .instanceDump(stringId, stringClass, stringFieldBytes(arrayId, (byte) 0)))
                .heapDumpEnd()
                .writeTo(tmp, "over-cap.hprof");

        Path indexDb = HeapDumpIndexPaths.indexFor(hprof);
        BuildOptions options = new BuildOptions(4, BuildOptions.DEFAULT_WALK_WORKERS);
        try (HprofMappedFile file = HprofMappedFile.open(hprof)) {
            HprofIndex.build(file, indexDb, CLOCK, options);
        }

        try (HprofMappedFile file = HprofMappedFile.open(hprof);
             HeapView view = HeapView.open(indexDb, file)) {
            StringAnalysisReport r = StringAnalyzer.analyze(view, 10);

            assertEquals(1, r.topInstancesByRetained().size());
            StringInstanceEntry entry = r.topInstancesByRetained().get(0);
            assertEquals(stringId, entry.instanceId());
            // Indexer stored content NULL (length 47 > threshold 4) → preview
            // came from decodeOverCapPreview's bounded read.
            assertEquals(contentSource, entry.content(),
                    "bounded re-decode must return the full short payload");
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
