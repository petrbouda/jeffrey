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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end coverage of {@link HprofTopLevelWalk}: drives every kind of
 * top-level record through {@link HprofIndex#build} and verifies the side
 * effects (string pool persistence, region buffering, stack-frame buffering,
 * record-count accounting) by querying the resulting index.
 */
class HprofTopLevelWalkIntegrationTest {

    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.ofEpochMilli(1_770_000_000_000L), ZoneOffset.UTC);

    @Nested
    class StringPool {

        @Test
        void persistsEveryStringRecord(@TempDir Path tmp) throws IOException, SQLException {
            Path hprof = SyntheticHprof.create("1.0.2", 8, 0L)
                    .string(0xA001L, "one")
                    .string(0xA002L, "two")
                    .string(0xA003L, "three")
                    .writeTo(tmp, "strings.hprof");
            Path indexDb = HeapDumpIndexPaths.indexFor(hprof);

            HprofIndex.IndexResult result;
            try (HprofMappedFile file = HprofMappedFile.open(hprof)) {
                result = HprofIndex.build(file, indexDb, FIXED_CLOCK);
            }

            assertEquals(3, result.stringCount());
            try (Connection conn = DriverManager.getConnection("jdbc:duckdb:" + indexDb.toAbsolutePath());
                 Statement stmt = conn.createStatement()) {
                assertEquals(3, scalarLong(stmt, "SELECT COUNT(*) FROM string"));
                // Verify value column is round-tripped through the appender, not just counted.
                try (ResultSet rs = stmt.executeQuery(
                        "SELECT value FROM string WHERE string_id = " + 0xA002L)) {
                    assertTrue(rs.next());
                    assertEquals("two", rs.getString(1));
                }
            }
        }

        @Test
        void resolvesStringDeclaredAfterRecordReferencingIt(@TempDir Path tmp) throws IOException, SQLException {
            // STACK_FRAME references methodNameStringId 0xA001 — but the STRING
            // record for 0xA001 appears AFTER the STACK_FRAME. The top-level walk
            // must finish before the stack-trace writer reads the pool, so this
            // resolution must still succeed.
            long methodNameId = 0xA001L;
            long sigId = 0xA002L;
            long classNameId = 0xA003L;
            int classSerial = 1;

            Path hprof = SyntheticHprof.create("1.0.2", 8, 0L)
                    .stackFrame(0xF001L, methodNameId, sigId, 0L, classSerial, 42)
                    .stackTrace(7, 1, 0xF001L)
                    .loadClass(classSerial, 0xC001L, 0, classNameId)
                    // STRING records appear *after* the records that reference them.
                    .string(classNameId, "com.example.Late")
                    .string(methodNameId, "doSomething")
                    .string(sigId, "()V")
                    .writeTo(tmp, "out-of-order.hprof");
            Path indexDb = HeapDumpIndexPaths.indexFor(hprof);

            try (HprofMappedFile file = HprofMappedFile.open(hprof)) {
                HprofIndex.build(file, indexDb, FIXED_CLOCK);
            }

            try (Connection conn = DriverManager.getConnection("jdbc:duckdb:" + indexDb.toAbsolutePath());
                 Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(
                         "SELECT method_name, method_signature FROM stack_frame WHERE frame_id = " + 0xF001L)) {
                assertTrue(rs.next());
                assertEquals("doSomething", rs.getString(1));
                assertEquals("()V", rs.getString(2));
            }
        }
    }

    @Nested
    class HeapDumpRegions {

        @Test
        void walksEverySegment(@TempDir Path tmp) throws IOException, SQLException {
            long classNameId = 0xA001L;
            long fieldNameId = 0xA002L;
            long classId = 0xC001L;

            // Three separate HEAP_DUMP_SEGMENT regions, each contributing one
            // instance. Pass B must visit all three regions.
            Path hprof = SyntheticHprof.create("1.0.2", 8, 0L)
                    .string(classNameId, "com.example.Foo")
                    .string(fieldNameId, "value")
                    .loadClass(1, classId, 0, classNameId)
                    .heapDumpSegment(seg -> seg
                            .simpleClassDump(classId, 0L, 0L, 16, fieldNameId)
                            .instanceDump(0x1001L, classId, new byte[]{0, 0, 0, 1}))
                    .heapDumpSegment(seg -> seg
                            .instanceDump(0x1002L, classId, new byte[]{0, 0, 0, 2}))
                    .heapDumpSegment(seg -> seg
                            .instanceDump(0x1003L, classId, new byte[]{0, 0, 0, 3}))
                    .heapDumpEnd()
                    .writeTo(tmp, "multi-segment.hprof");
            Path indexDb = HeapDumpIndexPaths.indexFor(hprof);

            HprofIndex.IndexResult result;
            try (HprofMappedFile file = HprofMappedFile.open(hprof)) {
                result = HprofIndex.build(file, indexDb, FIXED_CLOCK);
            }
            assertEquals(3, result.instanceCount(),
                    "all three regions' instance dumps must be ingested");

            try (Connection conn = DriverManager.getConnection("jdbc:duckdb:" + indexDb.toAbsolutePath());
                 Statement stmt = conn.createStatement()) {
                assertEquals(3, scalarLong(stmt,
                        "SELECT COUNT(*) FROM instance WHERE class_id = " + classId));
            }
        }
    }

    @Nested
    class UnknownTopLevelRecords {

        @Test
        void unknownTopLevelTagIsIgnored(@TempDir Path tmp) throws IOException, SQLException {
            // 0x55 is not a defined top-level tag. The reader must classify it
            // as OpaqueTop and continue, not crash and not emit a parse_warning
            // (it's an information-loss skip, not a corruption signal).
            Path hprof = SyntheticHprof.create("1.0.2", 8, 0L)
                    .string(0xA001L, "before")
                    .unknownTopLevel(0x55)
                    .string(0xA002L, "after")
                    .writeTo(tmp, "unknown-tag.hprof");
            Path indexDb = HeapDumpIndexPaths.indexFor(hprof);

            HprofIndex.IndexResult result;
            try (HprofMappedFile file = HprofMappedFile.open(hprof)) {
                result = HprofIndex.build(file, indexDb, FIXED_CLOCK);
            }
            // Both STRING records survive — the unknown tag between them did not
            // poison subsequent parsing.
            assertEquals(2, result.stringCount());
        }
    }

    @Nested
    class RecordCount {

        @Test
        void dumpMetadataRecordCountTracksTopLevelPlusSubRecords(@TempDir Path tmp)
                throws IOException, SQLException {
            // 2 strings + 1 load_class + 1 heap_dump_segment + 1 heap_dump_end = 5 top-level
            // The segment contains 1 class_dump + 1 instance_dump = 2 sub-records.
            // record_count totals top-level + sub.
            long classNameId = 0xA001L;
            long fieldNameId = 0xA002L;
            long classId = 0xC001L;

            Path hprof = SyntheticHprof.create("1.0.2", 8, 0L)
                    .string(classNameId, "X")
                    .string(fieldNameId, "f")
                    .loadClass(1, classId, 0, classNameId)
                    .heapDumpSegment(seg -> seg
                            .simpleClassDump(classId, 0L, 0L, 16, fieldNameId)
                            .instanceDump(0x1001L, classId, new byte[]{0, 0, 0, 0}))
                    .heapDumpEnd()
                    .writeTo(tmp, "recordcount.hprof");
            Path indexDb = HeapDumpIndexPaths.indexFor(hprof);

            HprofIndex.IndexResult result;
            try (HprofMappedFile file = HprofMappedFile.open(hprof)) {
                result = HprofIndex.build(file, indexDb, FIXED_CLOCK);
            }
            assertTrue(result.recordCount() >= 5,
                    "expected at least the 5 top-level records to be counted: actual=" + result.recordCount());
            assertEquals(result.recordCount(), scalarLongFromMetadata(indexDb, "record_count"),
                    "dump_metadata.record_count must match IndexResult.recordCount");
        }
    }

    private static long scalarLong(Statement stmt, String sql) throws SQLException {
        try (ResultSet rs = stmt.executeQuery(sql)) {
            assertTrue(rs.next(), "query returned no rows: " + sql);
            return rs.getLong(1);
        }
    }

    private static long scalarLongFromMetadata(Path indexDb, String column) throws SQLException {
        try (Connection conn = DriverManager.getConnection("jdbc:duckdb:" + indexDb.toAbsolutePath());
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT " + column + " FROM dump_metadata")) {
            assertTrue(rs.next());
            return rs.getLong(1);
        }
    }
}
