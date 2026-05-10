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
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import cafe.jeffrey.profile.heapdump.model.HeapThreadInfo;
import cafe.jeffrey.profile.heapdump.parser.HeapDumpIndexPaths;
import cafe.jeffrey.profile.heapdump.parser.HeapView;
import cafe.jeffrey.profile.heapdump.parser.HprofIndex;
import cafe.jeffrey.profile.heapdump.parser.HprofMappedFile;
import cafe.jeffrey.profile.heapdump.parser.HprofTag;
import cafe.jeffrey.profile.heapdump.parser.SyntheticHprof;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ThreadAnalyzerTest {

    private static final Clock CLOCK =
            Clock.fixed(Instant.ofEpochMilli(1L), ZoneOffset.UTC);

    private static final int ID_SIZE = 8;
    // Thread instance bytes: name(8) + daemon(1) + priority(4) = 13
    private static final int THREAD_INSTANCE_BYTES = ID_SIZE + 1 + 4;
    // String instance bytes: value(8) + coder(1) + hash(4) = 13
    private static final int STRING_INSTANCE_BYTES = ID_SIZE + 1 + 4;

    @Test
    void readsThreadNameDaemonAndPriority(@TempDir Path tmp) throws IOException, SQLException {
        long threadClass = 0xC001L;
        long stringClass = 0xC002L;
        long thread = 0x1000L;
        long nameString = 0x2000L;
        long nameArray = 0x3000L;

        Path hprof = SyntheticHprof.create("1.0.2", ID_SIZE, 0L)
                .string(0xA001L, "java.lang.Thread")
                .string(0xA002L, "java.lang.String")
                .string(0xA003L, "name")
                .string(0xA004L, "daemon")
                .string(0xA005L, "priority")
                .string(0xA006L, "value")
                .string(0xA007L, "coder")
                .string(0xA008L, "hash")
                .loadClass(1, threadClass, 0, 0xA001L)
                .loadClass(2, stringClass, 0, 0xA002L)
                .heapDumpSegment(seg -> seg
                        .classDumpWithFields(threadClass, 0L, 0L, THREAD_INSTANCE_BYTES,
                                new SyntheticHprof.SubBuilder.FieldSpec(0xA003L, HprofTag.BasicType.OBJECT),
                                new SyntheticHprof.SubBuilder.FieldSpec(0xA004L, HprofTag.BasicType.BOOLEAN),
                                new SyntheticHprof.SubBuilder.FieldSpec(0xA005L, HprofTag.BasicType.INT))
                        .classDumpWithFields(stringClass, 0L, 0L, STRING_INSTANCE_BYTES,
                                new SyntheticHprof.SubBuilder.FieldSpec(0xA006L, HprofTag.BasicType.OBJECT),
                                new SyntheticHprof.SubBuilder.FieldSpec(0xA007L, HprofTag.BasicType.BYTE),
                                new SyntheticHprof.SubBuilder.FieldSpec(0xA008L, HprofTag.BasicType.INT))
                        .gcRootThreadObject(thread, 1, 1)
                        .primitiveArrayDump(nameArray, HprofTag.BasicType.BYTE,
                                "main".getBytes(StandardCharsets.ISO_8859_1), 4)
                        .instanceDump(nameString, stringClass,
                                stringFieldBytes(nameArray, (byte) 0))
                        .instanceDump(thread, threadClass,
                                threadFieldBytes(nameString, true, 7)))
                .heapDumpEnd()
                .writeTo(tmp, "thread.hprof");

        Path indexDb = HeapDumpIndexPaths.indexFor(hprof);
        try (HprofMappedFile file = HprofMappedFile.open(hprof)) {
            HprofIndex.build(file, indexDb, CLOCK);
        }

        try (HprofMappedFile file = HprofMappedFile.open(hprof);
             HeapView view = HeapView.open(indexDb, file)) {
            List<HeapThreadInfo> threads = ThreadAnalyzer.analyze(view);
            assertEquals(1, threads.size());
            HeapThreadInfo t = threads.get(0);
            assertEquals(thread, t.objectId());
            assertEquals("main", t.name());
            assertTrue(t.daemon());
            assertEquals(7, t.priority());
            assertNull(t.retainedSize(), "no dominator tree built — retainedSize null");
        }
    }

    @Test
    void emptyResultWhenNoThreadObjectGcRoots(@TempDir Path tmp) throws IOException, SQLException {
        Path hprof = SyntheticHprof.create("1.0.2", 8, 0L)
                .string(0xA001L, "X")
                .heapDumpEnd()
                .writeTo(tmp, "no-threads.hprof");
        Path indexDb = HeapDumpIndexPaths.indexFor(hprof);
        try (HprofMappedFile file = HprofMappedFile.open(hprof)) {
            HprofIndex.build(file, indexDb, CLOCK);
        }
        try (HprofMappedFile file = HprofMappedFile.open(hprof);
             HeapView view = HeapView.open(indexDb, file)) {
            assertTrue(ThreadAnalyzer.analyze(view).isEmpty());
        }
    }

    @Test
    void analyzeFromIndexOnlyViewSkipsThreadsThatNeedFieldDecoding(@TempDir Path tmp)
            throws IOException, SQLException {
        // No HprofMappedFile attached → readInstanceFields throws → analyzer skips.
        long threadClass = 0xC001L;
        long thread = 0x1000L;

        Path hprof = SyntheticHprof.create("1.0.2", ID_SIZE, 0L)
                .string(0xA001L, "java.lang.Thread")
                .string(0xA003L, "name")
                .string(0xA004L, "daemon")
                .string(0xA005L, "priority")
                .loadClass(1, threadClass, 0, 0xA001L)
                .heapDumpSegment(seg -> seg
                        .classDumpWithFields(threadClass, 0L, 0L, THREAD_INSTANCE_BYTES,
                                new SyntheticHprof.SubBuilder.FieldSpec(0xA003L, HprofTag.BasicType.OBJECT),
                                new SyntheticHprof.SubBuilder.FieldSpec(0xA004L, HprofTag.BasicType.BOOLEAN),
                                new SyntheticHprof.SubBuilder.FieldSpec(0xA005L, HprofTag.BasicType.INT))
                        .gcRootThreadObject(thread, 1, 1)
                        .instanceDump(thread, threadClass, threadFieldBytes(0L, false, 5)))
                .heapDumpEnd()
                .writeTo(tmp, "no-hprof-thread.hprof");
        Path indexDb = HeapDumpIndexPaths.indexFor(hprof);
        try (HprofMappedFile file = HprofMappedFile.open(hprof)) {
            HprofIndex.build(file, indexDb, CLOCK);
        }
        try (HeapView view = HeapView.open(indexDb)) {
            List<HeapThreadInfo> threads = ThreadAnalyzer.analyze(view);
            assertTrue(threads.isEmpty(),
                    "no .hprof attached → field-value reads fail → thread skipped silently");
            assertFalse(view.hasDominatorTree());
        }
    }

    private static byte[] threadFieldBytes(long nameRef, boolean daemon, int priority) {
        try {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            DataOutputStream d = new DataOutputStream(b);
            d.writeLong(nameRef);
            d.writeByte(daemon ? 1 : 0);
            d.writeInt(priority);
            return b.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static byte[] stringFieldBytes(long valueArrayId, byte coder) {
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
