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
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import cafe.jeffrey.profile.heapdump.model.StackFrameLocal;
import cafe.jeffrey.profile.heapdump.model.ThreadStackFrame;
import cafe.jeffrey.profile.heapdump.parser.HeapDumpIndexPaths;
import cafe.jeffrey.profile.heapdump.parser.HeapView;
import cafe.jeffrey.profile.heapdump.parser.HprofIndex;
import cafe.jeffrey.profile.heapdump.parser.HprofMappedFile;
import cafe.jeffrey.profile.heapdump.parser.HprofTag;
import cafe.jeffrey.profile.heapdump.parser.SyntheticHprof;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ThreadStackAnalyzerTest {

    private static final Clock CLOCK =
            Clock.fixed(Instant.ofEpochMilli(1L), ZoneOffset.UTC);
    private static final int ID_SIZE = 8;

    /**
     * Builds a synthetic dump with one thread that has a 2-frame stack:
     *
     * <pre>
     *   #0 jdk.internal.misc.Unsafe.park (Unsafe.java)            line: -3 (native)
     *   #1 java.util.concurrent.locks.LockSupport.park (LockSupport.java:371)
     * </pre>
     *
     * A ROOT_JAVA_FRAME makes a single local visible in frame #1 — the
     * analyzer should surface it as a {@link StackFrameLocal} with the
     * referenced object's class name and shallow size.
     */
    @Test
    void assemblesFramesAndLocalsForThread(@TempDir Path tmp) throws IOException, SQLException {
        long threadClass = 0xC100L;
        long threadInstance = 0x1000L;
        long localClass = 0xC200L;
        long localInstance = 0x2000L;

        int threadSerial = 7;
        int traceSerial = 99;
        int unsafeClassSerial = 1;
        int lockSupportClassSerial = 2;
        int threadClassSerial = 10;
        int localClassSerial = 11;
        long frame0Id = 0xF1L;
        long frame1Id = 0xF2L;

        SyntheticHprof hprof = SyntheticHprof.create("1.0.2", ID_SIZE, 0L)
                .string(0xA001L, "java.lang.Thread")
                .string(0xA002L, "jdk/internal/misc/Unsafe")
                .string(0xA003L, "java/util/concurrent/locks/LockSupport")
                .string(0xA004L, "park")
                .string(0xA005L, "()V")
                .string(0xA006L, "Unsafe.java")
                .string(0xA007L, "LockSupport.java")
                .string(0xA008L, "f")
                .string(0xA009L, "java.util.concurrent.locks.AbstractQueuedSynchronizer$ConditionNode")
                .loadClass(threadClassSerial, threadClass, 0, 0xA001L)
                .loadClass(unsafeClassSerial, 0xC300L, 0, 0xA002L)
                .loadClass(lockSupportClassSerial, 0xC400L, 0, 0xA003L)
                .loadClass(localClassSerial, localClass, 0, 0xA009L);

        // STACK_FRAME #0 — native (lineNumber = -3)
        hprof.topLevel(HprofTag.Top.STACK_FRAME, stackFrameBody(
                frame0Id, /* methodName */ 0xA004L, /* signature */ 0xA005L,
                /* sourceFile */ 0xA006L, unsafeClassSerial, /* lineNumber */ -3));
        // STACK_FRAME #1 — LockSupport.java:371
        hprof.topLevel(HprofTag.Top.STACK_FRAME, stackFrameBody(
                frame1Id, 0xA004L, 0xA005L, 0xA007L, lockSupportClassSerial, 371));
        // STACK_TRACE — two frames, top-most first.
        hprof.topLevel(HprofTag.Top.STACK_TRACE, stackTraceBody(
                traceSerial, threadSerial, new long[]{frame0Id, frame1Id}));

        Path hprofPath = hprof
                .heapDumpSegment(seg -> seg
                        // Thread class metadata so the thread instance has a class to point at.
                        .simpleClassDump(threadClass, 0L, 0L, 16, 0xA008L)
                        .simpleClassDump(localClass, 0L, 0L, 16, 0xA008L)
                        // ROOT_THREAD_OBJECT links the Thread instance to threadSerial.
                        .gcRootThreadObject(threadInstance, threadSerial, traceSerial)
                        // ROOT_JAVA_FRAME — local visible in frame #1.
                        .gcRootJavaFrame(localInstance, threadSerial, 1)
                        .instanceDump(threadInstance, threadClass, new byte[]{0, 0, 0, 0})
                        .instanceDump(localInstance, localClass, new byte[]{0, 0, 0, 0}))
                .heapDumpEnd()
                .writeTo(tmp, "thread-stack.hprof");

        Path indexDb = HeapDumpIndexPaths.indexFor(hprofPath);
        try (HprofMappedFile file = HprofMappedFile.open(hprofPath)) {
            HprofIndex.build(file, indexDb, CLOCK);
        }

        try (HeapView view = HeapView.open(indexDb)) {
            List<ThreadStackFrame> frames = ThreadStackAnalyzer.getStack(view, threadInstance);
            assertEquals(2, frames.size(), "two frames decoded");

            // Frame #0 — native method, no source line.
            ThreadStackFrame f0 = frames.get(0);
            assertEquals("jdk.internal.misc.Unsafe", f0.className());
            assertEquals("park", f0.methodName());
            assertEquals("Unsafe.java", f0.sourceFile());
            assertEquals(-3, f0.lineNumber(), "raw HPROF sentinel for native frames");
            assertTrue(f0.locals().isEmpty(), "no locals on frame #0");

            // Frame #1 — has the single local from ROOT_JAVA_FRAME.
            ThreadStackFrame f1 = frames.get(1);
            assertEquals("java.util.concurrent.locks.LockSupport", f1.className());
            assertEquals("park", f1.methodName());
            assertEquals("LockSupport.java", f1.sourceFile());
            assertEquals(371, f1.lineNumber());
            assertEquals(1, f1.locals().size(), "one local pulled from ROOT_JAVA_FRAME");

            StackFrameLocal local = f1.locals().get(0);
            assertEquals(localInstance, local.objectId());
            assertEquals(
                    "java.util.concurrent.locks.AbstractQueuedSynchronizer$ConditionNode",
                    local.className());
            assertEquals("", local.fieldName(), "HPROF doesn't carry local-variable names");
            assertTrue(local.shallowSize() > 0, "shallow size is populated from the instance row");
        }
    }

    /** Unknown thread id → empty list (no NPE, no SQL leakage). */
    @Test
    void unknownThreadReturnsEmpty(@TempDir Path tmp) throws IOException, SQLException {
        Path hprof = SyntheticHprof.create("1.0.2", ID_SIZE, 0L)
                .string(0xA001L, "f")
                .heapDumpSegment(seg -> {
                })
                .heapDumpEnd()
                .writeTo(tmp, "empty.hprof");

        Path indexDb = HeapDumpIndexPaths.indexFor(hprof);
        try (HprofMappedFile file = HprofMappedFile.open(hprof)) {
            HprofIndex.build(file, indexDb, CLOCK);
        }

        try (HeapView view = HeapView.open(indexDb)) {
            assertTrue(ThreadStackAnalyzer.getStack(view, 0xDEADBEEFL).isEmpty());
        }
    }

    /** Source-file id 0 → NULL in the index, surfaced as null on the frame. */
    @Test
    void absentSourceFileMapsToNull(@TempDir Path tmp) throws IOException, SQLException {
        long threadInst = 0x1000L;
        long threadClass = 0xC100L;
        int threadSerial = 3;
        int traceSerial = 4;
        int methodClassSerial = 1;
        long frameId = 0xF1L;

        SyntheticHprof hprof = SyntheticHprof.create("1.0.2", ID_SIZE, 0L)
                .string(0xA001L, "java.lang.Thread")
                .string(0xA002L, "java/lang/Object")
                .string(0xA003L, "wait")
                .string(0xA004L, "()V")
                .string(0xA005L, "f")
                .loadClass(10, threadClass, 0, 0xA001L)
                .loadClass(methodClassSerial, 0xC200L, 0, 0xA002L);

        // sourceFile string id = 0 — exercises the NULL path in writeStackTraces.
        hprof.topLevel(HprofTag.Top.STACK_FRAME, stackFrameBody(
                frameId, 0xA003L, 0xA004L, /* sourceFile */ 0L, methodClassSerial, -1));
        hprof.topLevel(HprofTag.Top.STACK_TRACE, stackTraceBody(
                traceSerial, threadSerial, new long[]{frameId}));

        Path hprofPath = hprof
                .heapDumpSegment(seg -> seg
                        .simpleClassDump(threadClass, 0L, 0L, 16, 0xA005L)
                        .gcRootThreadObject(threadInst, threadSerial, traceSerial)
                        .instanceDump(threadInst, threadClass, new byte[]{0, 0, 0, 0}))
                .heapDumpEnd()
                .writeTo(tmp, "no-source.hprof");

        Path indexDb = HeapDumpIndexPaths.indexFor(hprofPath);
        try (HprofMappedFile file = HprofMappedFile.open(hprofPath)) {
            HprofIndex.build(file, indexDb, CLOCK);
        }

        try (HeapView view = HeapView.open(indexDb)) {
            List<ThreadStackFrame> frames = ThreadStackAnalyzer.getStack(view, threadInst);
            assertEquals(1, frames.size());
            assertNull(frames.get(0).sourceFile(), "string id 0 must surface as null");
            assertEquals(-1, frames.get(0).lineNumber(), "HPROF -1 = no line info");
        }
    }

    private static byte[] stackFrameBody(
            long frameId, long methodNameId, long signatureId,
            long sourceFileId, int classSerial, int lineNumber) {
        try {
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            DataOutputStream d = new DataOutputStream(buf);
            d.writeLong(frameId);
            d.writeLong(methodNameId);
            d.writeLong(signatureId);
            d.writeLong(sourceFileId);
            d.writeInt(classSerial);
            d.writeInt(lineNumber);
            return buf.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static byte[] stackTraceBody(int traceSerial, int threadSerial, long[] frameIds) {
        try {
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            DataOutputStream d = new DataOutputStream(buf);
            d.writeInt(traceSerial);
            d.writeInt(threadSerial);
            d.writeInt(frameIds.length);
            for (long id : frameIds) {
                d.writeLong(id);
            }
            return buf.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
