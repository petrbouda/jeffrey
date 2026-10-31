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
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import cafe.jeffrey.profile.heapdump.model.GCRootPath;
import cafe.jeffrey.profile.heapdump.parser.HprofIndex;
import cafe.jeffrey.profile.heapdump.parser.HprofMappedFile;
import cafe.jeffrey.profile.heapdump.parser.SyntheticHprof;
import cafe.jeffrey.profile.heapdump.persistence.HeapDumpIndexPaths;
import cafe.jeffrey.profile.heapdump.view.HeapView;
import cafe.jeffrey.profile.heapdump.view.HprofTag;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A dump with one thread, "main", whose frame {@code Checkout.main(Checkout.java:82)} holds a local
 * {@code Checkout$AuditLog}, and one object no root holds.
 */
class GcRootHoldersTest {

    private static final Clock CLOCK = Clock.fixed(Instant.ofEpochMilli(1L), ZoneOffset.UTC);

    private static final int ID_SIZE = 8;
    private static final int THREAD_INSTANCE_BYTES = ID_SIZE + 1 + 4;
    private static final int STRING_INSTANCE_BYTES = ID_SIZE + 1 + 4;

    private static final int THREAD_SERIAL = 1;
    private static final int TRACE_SERIAL = 7;
    private static final int FRAME_LINE = 82;

    private static final long THREAD_CLASS = 0xC001L;
    private static final long STRING_CLASS = 0xC002L;
    private static final long CHECKOUT_CLASS = 0xC003L;
    private static final long AUDIT_LOG_CLASS = 0xC004L;

    private static final long THREAD = 0x1000L;
    private static final long THREAD_NAME = 0x2000L;
    private static final long THREAD_NAME_BYTES = 0x3000L;
    private static final long AUDIT_LOG = 0x4000L;
    private static final long UNROOTED = 0x5000L;

    private static final long MAIN_FRAME = 0xF001L;

    @TempDir
    Path tmp;

    private Path hprof;
    private Path indexDb;

    @BeforeEach
    void buildDump() throws IOException, SQLException {
        hprof = SyntheticHprof.create("1.0.2", ID_SIZE, 0L)
                .string(0xA001L, "java.lang.Thread")
                .string(0xA002L, "java.lang.String")
                .string(0xA003L, "name")
                .string(0xA004L, "daemon")
                .string(0xA005L, "priority")
                .string(0xA006L, "value")
                .string(0xA007L, "coder")
                .string(0xA008L, "hash")
                .string(0xA009L, "com.acme.checkout.Checkout")
                .string(0xA00AL, "com.acme.checkout.Checkout$AuditLog")
                .string(0xA00BL, "orders")
                .string(0xA00CL, "main")
                .string(0xA00DL, "Checkout.java")
                .loadClass(1, THREAD_CLASS, 0, 0xA001L)
                .loadClass(2, STRING_CLASS, 0, 0xA002L)
                .loadClass(3, CHECKOUT_CLASS, 0, 0xA009L)
                .loadClass(4, AUDIT_LOG_CLASS, 0, 0xA00AL)
                .stackFrame(MAIN_FRAME, 0xA00CL, 0L, 0xA00DL, 3, FRAME_LINE)
                .stackTrace(TRACE_SERIAL, THREAD_SERIAL, MAIN_FRAME)
                .heapDumpSegment(seg -> seg
                        .classDumpWithFields(THREAD_CLASS, 0L, 0L, THREAD_INSTANCE_BYTES,
                                new SyntheticHprof.SubBuilder.FieldSpec(0xA003L, HprofTag.BasicType.OBJECT),
                                new SyntheticHprof.SubBuilder.FieldSpec(0xA004L, HprofTag.BasicType.BOOLEAN),
                                new SyntheticHprof.SubBuilder.FieldSpec(0xA005L, HprofTag.BasicType.INT))
                        .classDumpWithFields(STRING_CLASS, 0L, 0L, STRING_INSTANCE_BYTES,
                                new SyntheticHprof.SubBuilder.FieldSpec(0xA006L, HprofTag.BasicType.OBJECT),
                                new SyntheticHprof.SubBuilder.FieldSpec(0xA007L, HprofTag.BasicType.BYTE),
                                new SyntheticHprof.SubBuilder.FieldSpec(0xA008L, HprofTag.BasicType.INT))
                        .topLevelObjectClassDump(CHECKOUT_CLASS, 0xA00BL)
                        .topLevelObjectClassDump(AUDIT_LOG_CLASS, 0xA00BL)
                        .gcRootThreadObject(THREAD, THREAD_SERIAL, TRACE_SERIAL)
                        .gcRootJavaFrame(AUDIT_LOG, THREAD_SERIAL, 0)
                        .primitiveArrayDump(THREAD_NAME_BYTES, HprofTag.BasicType.BYTE,
                                "main".getBytes(StandardCharsets.ISO_8859_1), 4)
                        .instanceDump(THREAD_NAME, STRING_CLASS, stringFieldBytes(THREAD_NAME_BYTES))
                        .instanceDump(THREAD, THREAD_CLASS, threadFieldBytes(THREAD_NAME))
                        .instanceDump(AUDIT_LOG, AUDIT_LOG_CLASS, idBytes(0L))
                        .instanceDump(UNROOTED, AUDIT_LOG_CLASS, idBytes(0L)))
                .heapDumpEnd()
                .writeTo(tmp, "frame-root.hprof");
        indexDb = HeapDumpIndexPaths.indexFor(hprof);
        try (HprofMappedFile file = HprofMappedFile.open(hprof)) {
            HprofIndex.build(file, indexDb, CLOCK);
        }
    }

    @Nested
    @DisplayName("GcRootHolders")
    class Holders {

        @Test
        @DisplayName("names the thread and the frame of a Java-frame root")
        void namesTheThreadAndFrameOfAFrameRoot() throws IOException, SQLException {
            try (HprofMappedFile file = HprofMappedFile.open(hprof);
                 HeapView view = HeapView.open(indexDb, file)) {
                GcRootHolders.Holder holder = GcRootHolders.of(view, AUDIT_LOG).orElseThrow();

                assertEquals("Java frame", holder.rootType());
                assertEquals("main", holder.threadName());
                assertEquals("com.acme.checkout.Checkout.main(Checkout.java:82)", holder.stackFrame());
            }
        }

        @Test
        @DisplayName("names a thread object's own thread, with no frame")
        void namesAThreadObjectRoot() throws IOException, SQLException {
            try (HprofMappedFile file = HprofMappedFile.open(hprof);
                 HeapView view = HeapView.open(indexDb, file)) {
                GcRootHolders.Holder holder = GcRootHolders.of(view, THREAD).orElseThrow();

                assertEquals("Thread object", holder.rootType());
                assertEquals("main", holder.threadName());
                assertNull(holder.stackFrame(), "a thread object is not a frame's local");
            }
        }

        @Test
        @DisplayName("is empty for an object that is not a root")
        void isEmptyForAnObjectThatIsNotARoot() throws IOException, SQLException {
            try (HprofMappedFile file = HprofMappedFile.open(hprof);
                 HeapView view = HeapView.open(indexDb, file)) {
                assertTrue(GcRootHolders.of(view, UNROOTED).isEmpty());
            }
        }

        @Test
        @DisplayName("leaves the thread unnamed when the view has no dump to decode it from")
        void leavesTheThreadUnnamedWithoutTheDump() throws IOException, SQLException {
            try (HeapView view = HeapView.open(indexDb)) {
                GcRootHolders.Holder holder = GcRootHolders.of(view, AUDIT_LOG).orElseThrow();

                assertNull(holder.threadName());
                assertEquals("com.acme.checkout.Checkout.main(Checkout.java:82)", holder.stackFrame());
            }
        }
    }

    @Nested
    @DisplayName("PathToGCRootAnalyzer.selfRoot")
    class SelfRoot {

        @Test
        @DisplayName("gives a root its own one-step path, naming its kind, thread and frame")
        void givesARootItsOwnPath() throws IOException, SQLException {
            try (HprofMappedFile file = HprofMappedFile.open(hprof);
                 HeapView view = HeapView.open(indexDb, file)) {
                GCRootPath path = PathToGCRootAnalyzer.selfRoot(view, AUDIT_LOG).orElseThrow();

                assertEquals(AUDIT_LOG, path.rootObjectId());
                assertEquals("com.acme.checkout.Checkout$AuditLog", path.rootClassName());
                assertEquals("Java frame", path.rootType());
                assertEquals("main", path.threadName());
                assertEquals("com.acme.checkout.Checkout.main(Checkout.java:82)", path.stackFrame());
                assertEquals(1, path.steps().size());
                assertTrue(path.steps().getFirst().isTarget());
            }
        }

        @Test
        @DisplayName("is empty for an object that is not a root")
        void isEmptyForAnObjectThatIsNotARoot() throws IOException, SQLException {
            try (HprofMappedFile file = HprofMappedFile.open(hprof);
                 HeapView view = HeapView.open(indexDb, file)) {
                Optional<GCRootPath> path = PathToGCRootAnalyzer.selfRoot(view, UNROOTED);

                assertTrue(path.isEmpty());
                assertEquals(List.of(), PathToGCRootAnalyzer.findPaths(view, UNROOTED, true, 1),
                        "and no root reaches it either");
            }
        }
    }

    private static byte[] threadFieldBytes(long nameRef) {
        try {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            DataOutputStream d = new DataOutputStream(b);
            d.writeLong(nameRef);
            d.writeByte(0);
            d.writeInt(5);
            return b.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static byte[] stringFieldBytes(long valueArrayId) {
        try {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            DataOutputStream d = new DataOutputStream(b);
            d.writeLong(valueArrayId);
            d.writeByte(0);
            d.writeInt(0);
            return b.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static byte[] idBytes(long id) {
        try {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            new DataOutputStream(b).writeLong(id);
            return b.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
