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
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end coverage of {@link HprofAppenderUtils#primArrayName} and
 * {@link HprofAppenderUtils#primArrayClassId} — the synthetic class rows
 * that Pass A appends for primitive-array types HPROF never emits CLASS_DUMPs
 * for. Drives a dump containing arrays of each primitive type and asserts on
 * the {@code class} table rows that result.
 */
class PrimitiveArrayClassesIntegrationTest {

    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.ofEpochMilli(1_770_000_000_000L), ZoneOffset.UTC);

    @Nested
    class SyntheticRowsPerType {

        @Test
        void everyPrimitiveTypeGetsAClassRowWithUserFacingName(@TempDir Path tmp)
                throws IOException, SQLException {
            // One array of each HPROF basic primitive type. The walker must
            // emit a single synthetic class row per type, named "<type>[]".
            Path hprof = SyntheticHprof.create("1.0.2", 8, 0L)
                    .string(0xA001L, "placeholder")
                    .heapDumpSegment(seg -> seg
                            .primitiveArrayDump(0x1001L, HprofTag.BasicType.BOOLEAN, new byte[2], 2)
                            .primitiveArrayDump(0x1002L, HprofTag.BasicType.CHAR, new byte[4], 2)
                            .primitiveArrayDump(0x1003L, HprofTag.BasicType.FLOAT, new byte[8], 2)
                            .primitiveArrayDump(0x1004L, HprofTag.BasicType.DOUBLE, new byte[16], 2)
                            .primitiveArrayDump(0x1005L, HprofTag.BasicType.BYTE, new byte[2], 2)
                            .primitiveArrayDump(0x1006L, HprofTag.BasicType.SHORT, new byte[4], 2)
                            .primitiveArrayDump(0x1007L, HprofTag.BasicType.INT, new byte[8], 2)
                            .primitiveArrayDump(0x1008L, HprofTag.BasicType.LONG, new byte[16], 2))
                    .heapDumpEnd()
                    .writeTo(tmp, "all-prim-arrays.hprof");
            Path indexDb = HeapDumpIndexPaths.indexFor(hprof);
            try (HprofMappedFile file = HprofMappedFile.open(hprof)) {
                HprofIndex.build(file, indexDb, FIXED_CLOCK);
            }

            try (Connection conn = DriverManager.getConnection("jdbc:duckdb:" + indexDb.toAbsolutePath());
                 Statement stmt = conn.createStatement()) {
                Set<String> got = new HashSet<>();
                try (ResultSet rs = stmt.executeQuery(
                        "SELECT name FROM class WHERE is_array = TRUE ORDER BY name")) {
                    while (rs.next()) {
                        got.add(rs.getString(1));
                    }
                }
                Set<String> expected = Set.of(
                        "boolean[]", "byte[]", "char[]", "short[]",
                        "int[]", "long[]", "float[]", "double[]");
                assertEquals(expected, got, "every primitive type gets one synthetic class row");
            }
        }

        @Test
        void syntheticClassIdsAreDeeplyNegative(@TempDir Path tmp) throws IOException, SQLException {
            // The class_id allocator places synthetic primitive-array classes
            // at PRIM_ARRAY_CLASS_ID_BASE (-1_000_000_000) minus elementType
            // — far below any real HPROF id, which the parser treats as
            // non-negative.
            Path hprof = SyntheticHprof.create("1.0.2", 8, 0L)
                    .heapDumpSegment(seg -> seg
                            .primitiveArrayDump(0x2001L, HprofTag.BasicType.INT, new byte[8], 2))
                    .heapDumpEnd()
                    .writeTo(tmp, "neg-ids.hprof");
            Path indexDb = HeapDumpIndexPaths.indexFor(hprof);
            try (HprofMappedFile file = HprofMappedFile.open(hprof)) {
                HprofIndex.build(file, indexDb, FIXED_CLOCK);
            }

            try (Connection conn = DriverManager.getConnection("jdbc:duckdb:" + indexDb.toAbsolutePath());
                 Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(
                         "SELECT MIN(class_id), MAX(class_id) FROM class WHERE is_array = TRUE")) {
                assertTrue(rs.next());
                long min = rs.getLong(1);
                long max = rs.getLong(2);
                assertTrue(max < -1_000_000_000L,
                        "synthetic primitive-array class_ids must be < -1_000_000_000: got max=" + max);
                assertTrue(min <= max);
            }
        }
    }

    @Nested
    class Idempotency {

        @Test
        void repeatedArraysOfSameTypeShareOneClassRow(@TempDir Path tmp) throws IOException, SQLException {
            // Three int[] instances share the SAME synthetic class_id and the
            // class table contains a single int[] row.
            Path hprof = SyntheticHprof.create("1.0.2", 8, 0L)
                    .heapDumpSegment(seg -> seg
                            .primitiveArrayDump(0x3001L, HprofTag.BasicType.INT, new byte[4], 1)
                            .primitiveArrayDump(0x3002L, HprofTag.BasicType.INT, new byte[8], 2)
                            .primitiveArrayDump(0x3003L, HprofTag.BasicType.INT, new byte[12], 3))
                    .heapDumpEnd()
                    .writeTo(tmp, "shared-int-array-class.hprof");
            Path indexDb = HeapDumpIndexPaths.indexFor(hprof);
            try (HprofMappedFile file = HprofMappedFile.open(hprof)) {
                HprofIndex.build(file, indexDb, FIXED_CLOCK);
            }

            try (Connection conn = DriverManager.getConnection("jdbc:duckdb:" + indexDb.toAbsolutePath());
                 Statement stmt = conn.createStatement()) {
                assertEquals(1, scalarLong(stmt,
                                "SELECT COUNT(*) FROM class WHERE name = 'int[]'"),
                        "all three int[] instances must reference the same class row");
                long expectedClassId = HprofAppenderUtils.primArrayClassId(HprofTag.BasicType.INT);
                try (ResultSet rs = stmt.executeQuery(
                        "SELECT class_id FROM class WHERE name = 'int[]'")) {
                    assertTrue(rs.next());
                    assertEquals(expectedClassId, rs.getLong(1));
                    assertFalse(rs.next());
                }
                // All three instance rows must reference that class_id.
                assertEquals(3, scalarLong(stmt,
                        "SELECT COUNT(*) FROM instance WHERE class_id = " + expectedClassId));
            }
        }
    }

    private static long scalarLong(Statement stmt, String sql) throws SQLException {
        try (ResultSet rs = stmt.executeQuery(sql)) {
            assertTrue(rs.next());
            return rs.getLong(1);
        }
    }
}
