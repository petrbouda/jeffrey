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
package cafe.jeffrey.profile.heapdump.parser;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import cafe.jeffrey.profile.heapdump.persistence.HeapDumpIndexPaths;

/**
 * Covers {@link HprofStackTraceWriter}'s fallback paths — the unresolved-id
 * placeholders, the NULL source-file mapping, and frame_index ordering inside
 * a STACK_TRACE — by driving synthetic dumps through {@link HprofIndex#build}
 * and asserting against the resulting {@code stack_frame} /
 * {@code stack_trace_frame} rows.
 */
class HprofStackTraceWriterIntegrationTest {

    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.ofEpochMilli(1_770_000_000_000L), ZoneOffset.UTC);

    @Nested
    class UnresolvedReferences {

        @Test
        void missingMethodNameStringIdPlaceholders(@TempDir Path tmp) throws IOException, SQLException {
            // methodNameStringId = 0xDEAD is never declared in the STRING pool;
            // writer must fall back to "<unresolved-method>".
            long classNameId = 0xA001L;
            int classSerial = 1;
            Path hprof = SyntheticHprof.create("1.0.2", 8, 0L)
                    .string(classNameId, "com.example.Caller")
                    .loadClass(classSerial, 0xC001L, 0, classNameId)
                    .stackFrame(0xF001L, /* methodName */ 0xDEADL, /* sig */ 0L, /* sourceFile */ 0L,
                            classSerial, -1)
                    .writeTo(tmp, "unresolved-method.hprof");
            Path indexDb = HeapDumpIndexPaths.indexFor(hprof);
            try (HprofMappedFile file = HprofMappedFile.open(hprof)) {
                HprofIndex.build(file, indexDb, FIXED_CLOCK);
            }

            try (Connection conn = DriverManager.getConnection("jdbc:duckdb:" + indexDb.toAbsolutePath());
                 Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(
                         "SELECT method_name FROM stack_frame WHERE frame_id = " + 0xF001L)) {
                assertTrue(rs.next());
                assertEquals("<unresolved-method>", rs.getString(1));
            }
        }

        @Test
        void unresolvedClassSerialPlaceholders(@TempDir Path tmp) throws IOException, SQLException {
            // STACK_FRAME references classSerial 99 but no LOAD_CLASS uses that
            // serial. Writer must fall back to "<unresolved-class-serial:99>".
            Path hprof = SyntheticHprof.create("1.0.2", 8, 0L)
                    .string(0xA001L, "doIt")
                    .stackFrame(0xF002L, 0xA001L, 0L, 0L, /* unknown */ 99, 10)
                    .writeTo(tmp, "unresolved-class.hprof");
            Path indexDb = HeapDumpIndexPaths.indexFor(hprof);
            try (HprofMappedFile file = HprofMappedFile.open(hprof)) {
                HprofIndex.build(file, indexDb, FIXED_CLOCK);
            }

            try (Connection conn = DriverManager.getConnection("jdbc:duckdb:" + indexDb.toAbsolutePath());
                 Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(
                         "SELECT class_name FROM stack_frame WHERE frame_id = " + 0xF002L)) {
                assertTrue(rs.next());
                assertEquals("<unresolved-class-serial:99>", rs.getString(1));
            }
        }
    }

    @Nested
    class SourceFileMapping {

        @Test
        void sourceFileIdZeroMapsToNull(@TempDir Path tmp) throws IOException, SQLException {
            // sourceFileNameStringId 0 is the HPROF "no source file" sentinel;
            // writer must persist NULL, not a placeholder string.
            long classNameId = 0xA001L;
            long methodNameId = 0xA002L;
            int classSerial = 1;
            Path hprof = SyntheticHprof.create("1.0.2", 8, 0L)
                    .string(classNameId, "com.example.Native")
                    .string(methodNameId, "nativeMethod")
                    .loadClass(classSerial, 0xC001L, 0, classNameId)
                    .stackFrame(0xF003L, methodNameId, 0L, /* sourceFile */ 0L, classSerial, -2)
                    .writeTo(tmp, "no-source.hprof");
            Path indexDb = HeapDumpIndexPaths.indexFor(hprof);
            try (HprofMappedFile file = HprofMappedFile.open(hprof)) {
                HprofIndex.build(file, indexDb, FIXED_CLOCK);
            }

            try (Connection conn = DriverManager.getConnection("jdbc:duckdb:" + indexDb.toAbsolutePath());
                 Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(
                         "SELECT source_file FROM stack_frame WHERE frame_id = " + 0xF003L)) {
                assertTrue(rs.next());
                rs.getString(1);
                assertTrue(rs.wasNull(), "source_file must be NULL when sourceFileNameStringId is 0");
            }
        }

        @Test
        void resolvedSourceFileIsPersisted(@TempDir Path tmp) throws IOException, SQLException {
            long classNameId = 0xA001L;
            long methodNameId = 0xA002L;
            long sourceFileId = 0xA003L;
            int classSerial = 1;
            Path hprof = SyntheticHprof.create("1.0.2", 8, 0L)
                    .string(classNameId, "com.example.Resolved")
                    .string(methodNameId, "run")
                    .string(sourceFileId, "Resolved.java")
                    .loadClass(classSerial, 0xC001L, 0, classNameId)
                    .stackFrame(0xF004L, methodNameId, 0L, sourceFileId, classSerial, 100)
                    .writeTo(tmp, "with-source.hprof");
            Path indexDb = HeapDumpIndexPaths.indexFor(hprof);
            try (HprofMappedFile file = HprofMappedFile.open(hprof)) {
                HprofIndex.build(file, indexDb, FIXED_CLOCK);
            }

            try (Connection conn = DriverManager.getConnection("jdbc:duckdb:" + indexDb.toAbsolutePath());
                 Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(
                         "SELECT source_file, line_number FROM stack_frame WHERE frame_id = " + 0xF004L)) {
                assertTrue(rs.next());
                assertEquals("Resolved.java", rs.getString(1));
                assertEquals(100, rs.getInt(2));
            }
        }
    }

    @Nested
    class FrameIndexOrdering {

        @Test
        void writesOneRowPerFrameInDeclarationOrder(@TempDir Path tmp) throws IOException, SQLException {
            // Three frames in declaration order f1 → f2 → f3. The writer must
            // emit stack_trace_frame rows with frame_index 0,1,2 in that order.
            long classNameId = 0xA001L;
            long methodNameId = 0xA002L;
            int classSerial = 1;
            int traceSerial = 42;
            int threadSerial = 7;
            Path hprof = SyntheticHprof.create("1.0.2", 8, 0L)
                    .string(classNameId, "com.example.Trace")
                    .string(methodNameId, "frame")
                    .loadClass(classSerial, 0xC001L, 0, classNameId)
                    .stackFrame(0xF101L, methodNameId, 0L, 0L, classSerial, 1)
                    .stackFrame(0xF102L, methodNameId, 0L, 0L, classSerial, 2)
                    .stackFrame(0xF103L, methodNameId, 0L, 0L, classSerial, 3)
                    .stackTrace(traceSerial, threadSerial, 0xF101L, 0xF102L, 0xF103L)
                    .writeTo(tmp, "ordering.hprof");
            Path indexDb = HeapDumpIndexPaths.indexFor(hprof);
            try (HprofMappedFile file = HprofMappedFile.open(hprof)) {
                HprofIndex.build(file, indexDb, FIXED_CLOCK);
            }

            try (Connection conn = DriverManager.getConnection("jdbc:duckdb:" + indexDb.toAbsolutePath());
                 Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(
                         "SELECT frame_index, frame_id FROM stack_trace_frame "
                                 + "WHERE trace_serial = " + traceSerial
                                 + " ORDER BY frame_index")) {
                assertTrue(rs.next());
                assertEquals(0, rs.getInt(1));
                assertEquals(0xF101L, rs.getLong(2));
                assertTrue(rs.next());
                assertEquals(1, rs.getInt(1));
                assertEquals(0xF102L, rs.getLong(2));
                assertTrue(rs.next());
                assertEquals(2, rs.getInt(1));
                assertEquals(0xF103L, rs.getLong(2));
                assertFalse(rs.next(), "exactly 3 frame rows expected");
            }
        }
    }
}
