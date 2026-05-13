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
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HprofIndexTest {

    private static final long FAKE_NOW_MS = 1_770_000_000_000L;
    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.ofEpochMilli(FAKE_NOW_MS), ZoneOffset.UTC);

    @Nested
    class EndToEnd {

        @Test
        void buildsIndexWithStringsClassesInstancesAndRoots(@TempDir Path tmp) throws IOException, SQLException {
            // Class metadata
            long stringNameId = 0xA001;
            long stringFieldNameId = 0xA002;
            long classId = 0xC001;
            long superClassId = 0;          // java.lang.Object — no super
            long classloaderId = 0xC100;
            int instanceSize = 24;

            // Heap objects
            long instanceId = 0x1001;
            long objArrayId = 0x1002;
            long primArrayId = 0x1003;

            Path hprof = SyntheticHprof.create("1.0.2", 8, 0xDEADBEEFL)
                    .string(stringNameId, "java.lang.String")
                    .string(stringFieldNameId, "value")
                    .loadClass(1, classId, 0, stringNameId)
                    .heapDumpSegment(seg -> seg
                            .gcRoot(HprofTag.Sub.ROOT_STICKY_CLASS, classId)
                            .gcRootJavaFrame(instanceId, 7, 13)
                            .simpleClassDump(classId, superClassId, classloaderId, instanceSize, stringFieldNameId)
                            .instanceDump(instanceId, classId, new byte[]{0, 0, 0, 4})
                            .objectArrayDump(objArrayId, classId, new long[]{instanceId, instanceId})
                            .primitiveArrayDump(primArrayId, HprofTag.BasicType.INT, new byte[16], 4))
                    .heapDumpEnd()
                    .writeTo(tmp, "demo.hprof");

            Path indexDb = HeapDumpIndexPaths.indexFor(hprof);

            HprofIndex.IndexResult result;
            try (HprofMappedFile file = HprofMappedFile.open(hprof)) {
                result = HprofIndex.build(file, indexDb, FIXED_CLOCK);
            }

            assertTrue(Files.exists(indexDb), "index file should exist");
            assertEquals(2, result.stringCount());
            // 1 CLASS_DUMP + 8 synthetic primitive-array class rows (boolean[]..long[]).
            assertEquals(9, result.classCount());
            assertEquals(3, result.instanceCount(), "INSTANCE_DUMP + OBJECT_ARRAY_DUMP + PRIMITIVE_ARRAY_DUMP");
            assertEquals(2, result.gcRootCount());
            assertEquals(0, result.warningCount());
            assertFalse(result.truncated());

            // Verify by direct SQL
            try (Connection conn = DriverManager.getConnection("jdbc:duckdb:" + indexDb.toAbsolutePath());
                 Statement stmt = conn.createStatement()) {

                assertEquals(2, scalarLong(stmt, "SELECT COUNT(*) FROM string"));
                // 1 CLASS_DUMP + 8 synthetic primitive-array class rows.
                assertEquals(9, scalarLong(stmt, "SELECT COUNT(*) FROM class"));
                assertEquals(1, scalarLong(stmt, "SELECT COUNT(*) FROM class WHERE class_id >= 0"));
                assertEquals(8, scalarLong(stmt, "SELECT COUNT(*) FROM class WHERE is_array = TRUE AND class_id < 0"));
                assertEquals(3, scalarLong(stmt, "SELECT COUNT(*) FROM instance"));
                assertEquals(2, scalarLong(stmt, "SELECT COUNT(*) FROM gc_root"));
                assertEquals(1, scalarLong(stmt, "SELECT COUNT(*) FROM dump_metadata"));
                assertEquals(0, scalarLong(stmt, "SELECT COUNT(*) FROM parse_warning"));

                // Class name resolved via the string pool
                try (ResultSet rs = stmt.executeQuery(
                        "SELECT name, instance_size, super_class_id, classloader_id FROM class WHERE class_id = " + classId)) {
                    assertTrue(rs.next());
                    assertEquals("java.lang.String", rs.getString(1));
                    assertEquals(instanceSize, rs.getInt(2));
                    rs.getLong(3);
                    assertTrue(rs.wasNull(), "super_class_id 0 should map to NULL");
                    assertEquals(classloaderId, rs.getLong(4));
                    assertFalse(rs.next());
                }

                // Instance row layout
                try (ResultSet rs = stmt.executeQuery(
                        "SELECT class_id, record_kind, array_length, primitive_type FROM instance WHERE instance_id = " + instanceId)) {
                    assertTrue(rs.next());
                    assertEquals(classId, rs.getLong(1));
                    assertEquals(0, rs.getInt(2));
                    rs.getInt(3);
                    assertTrue(rs.wasNull(), "non-array instance has NULL array_length");
                }

                try (ResultSet rs = stmt.executeQuery(
                        "SELECT record_kind, array_length, primitive_type FROM instance WHERE instance_id = " + objArrayId)) {
                    assertTrue(rs.next());
                    assertEquals(1, rs.getInt(1)); // OBJECT_ARRAY
                    assertEquals(2, rs.getInt(2));
                }

                try (ResultSet rs = stmt.executeQuery(
                        "SELECT record_kind, array_length, primitive_type FROM instance WHERE instance_id = " + primArrayId)) {
                    assertTrue(rs.next());
                    assertEquals(2, rs.getInt(1)); // PRIMITIVE_ARRAY
                    assertEquals(4, rs.getInt(2));
                    assertEquals(HprofTag.BasicType.INT, rs.getInt(3));
                }

                // GC root with thread + frame
                try (ResultSet rs = stmt.executeQuery(
                        "SELECT root_kind, thread_serial, frame_index FROM gc_root WHERE instance_id = " + instanceId)) {
                    assertTrue(rs.next());
                    assertEquals(HprofTag.Sub.ROOT_JAVA_FRAME, rs.getInt(1));
                    assertEquals(7, rs.getInt(2));
                    assertEquals(13, rs.getInt(3));
                }

                // GC root without thread (sticky class) — thread_serial NULL
                try (ResultSet rs = stmt.executeQuery(
                        "SELECT thread_serial FROM gc_root WHERE instance_id = " + classId
                                + " AND root_kind = " + HprofTag.Sub.ROOT_STICKY_CLASS)) {
                    assertTrue(rs.next());
                    rs.getInt(1);
                    assertTrue(rs.wasNull(), "ROOT_STICKY_CLASS should have NULL thread_serial");
                }

                // dump_metadata sanity
                try (ResultSet rs = stmt.executeQuery(
                        "SELECT id_size, hprof_version, timestamp_ms, truncated, parsed_at_ms, parser_version FROM dump_metadata")) {
                    assertTrue(rs.next());
                    assertEquals(8, rs.getInt(1));
                    assertEquals("1.0.2", rs.getString(2));
                    assertEquals(0xDEADBEEFL, rs.getLong(3));
                    assertFalse(rs.getBoolean(4));
                    assertEquals(FAKE_NOW_MS, rs.getLong(5));
                    assertEquals(HprofIndex.PARSER_VERSION, rs.getString(6));
                }
            }
        }

        @Test
        void rebuildsIfIndexAlreadyExists(@TempDir Path tmp) throws IOException, SQLException {
            Path hprof = SyntheticHprof.create("1.0.2", 8, 0L)
                    .string(1L, "X")
                    .writeTo(tmp, "rebuild.hprof");
            Path indexDb = HeapDumpIndexPaths.indexFor(hprof);

            // First build
            try (HprofMappedFile file = HprofMappedFile.open(hprof)) {
                HprofIndex.build(file, indexDb, FIXED_CLOCK);
            }
            // Second build — should overwrite cleanly
            try (HprofMappedFile file = HprofMappedFile.open(hprof)) {
                HprofIndex.IndexResult r = HprofIndex.build(file, indexDb, FIXED_CLOCK);
                assertEquals(1, r.stringCount());
            }
        }

        @Test
        void recordsTruncationAsParseWarning(@TempDir Path tmp) throws IOException, SQLException {
            // Header claims 100-byte body but no body bytes follow.
            byte[] truncatedTail = new byte[]{0x01, 0, 0, 0, 0, 0, 0, 0, 100};
            Path hprof = SyntheticHprof.create("1.0.2", 8, 0L)
                    .appendRaw(truncatedTail)
                    .writeTo(tmp, "trunc.hprof");
            Path indexDb = HeapDumpIndexPaths.indexFor(hprof);

            HprofIndex.IndexResult result;
            try (HprofMappedFile file = HprofMappedFile.open(hprof)) {
                result = HprofIndex.build(file, indexDb, FIXED_CLOCK);
            }

            assertTrue(result.truncated());
            assertEquals(1, result.warningCount());

            try (Connection conn = DriverManager.getConnection("jdbc:duckdb:" + indexDb.toAbsolutePath());
                 Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(
                         "SELECT severity, message FROM parse_warning")) {
                assertTrue(rs.next());
                assertEquals(2 /* ERROR ordinal */, rs.getInt(1));
                assertTrue(rs.getString(2).contains("extends beyond EOF"));
            }
        }
    }

    @Nested
    class SchemaApplication {

        @Test
        void initialiseCreatesAllTables(@TempDir Path tmp) throws IOException, SQLException {
            Path indexDb = tmp.resolve("schema-only.idx.duckdb");
            try (HeapDumpIndexDb db = HeapDumpIndexDb.openAndInitialize(indexDb)) {
                assertTrue(Files.exists(indexDb));
                try (Statement stmt = db.connection().createStatement()) {
                    long tableCount = scalarLong(stmt,
                            "SELECT COUNT(*) FROM duckdb_tables() WHERE schema_name = 'main'");
                    // string, class, class_instance_field, class_interface, instance, gc_root,
                    // outbound_ref, dominator, retained_size, string_content, stack_frame,
                    // stack_trace_frame, dump_metadata, parse_warning
                    assertEquals(14, tableCount);
                }
            }
        }
    }

    private static long scalarLong(Statement stmt, String sql) throws SQLException {
        try (ResultSet rs = stmt.executeQuery(sql)) {
            assertTrue(rs.next(), () -> "expected a row from: " + sql);
            return rs.getLong(1);
        }
    }
}
