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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end coverage of {@link HprofMetadataWriter}: drives synthetic dumps
 * through {@link HprofIndex#build} and asserts on the {@code dump_metadata}
 * columns that the writer composes plus the {@code parse_warning} rows it
 * persists. Complements {@link HprofIndexTest}'s happy-path coverage by
 * exercising the columns the existing tests skip (path, size, compressed_oops
 * inference branches, multi-phase warning aggregation).
 */
class HprofMetadataWriterIntegrationTest {

    private static final long FIXED_NOW_MS = 1_770_000_000_000L;
    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.ofEpochMilli(FIXED_NOW_MS), ZoneOffset.UTC);

    @Nested
    class FileMetadata {

        @Test
        void recordsAbsolutePathAndSizeAndMtime(@TempDir Path tmp) throws IOException, SQLException {
            Path hprof = SyntheticHprof.create("1.0.2", 8, 0xDEADBEEFL)
                    .string(1L, "x")
                    .writeTo(tmp, "meta.hprof");
            long expectedSize = Files.size(hprof);
            long expectedMtime = Files.getLastModifiedTime(hprof).toMillis();

            Path indexDb = HeapDumpIndexPaths.indexFor(hprof);
            try (HprofMappedFile file = HprofMappedFile.open(hprof)) {
                HprofIndex.build(file, indexDb, FIXED_CLOCK);
            }

            try (Connection conn = DriverManager.getConnection("jdbc:duckdb:" + indexDb.toAbsolutePath());
                 Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(
                         "SELECT hprof_path, hprof_size_bytes, hprof_mtime_ms, parsed_at_ms, parser_version "
                                 + "FROM dump_metadata")) {
                assertTrue(rs.next());
                assertEquals(hprof.toAbsolutePath().toString(), rs.getString(1));
                assertEquals(expectedSize, rs.getLong(2));
                assertEquals(expectedMtime, rs.getLong(3));
                assertEquals(FIXED_NOW_MS, rs.getLong(4));
                assertEquals(HprofIndex.PARSER_VERSION, rs.getString(5));
            }
        }
    }

    @Nested
    class CompressedOopsInference {

        @Test
        void thirtyTwoBitDumpReportsNoCompressedOops(@TempDir Path tmp) throws IOException, SQLException {
            // idSize = 4 → 32-bit JVM → compressed_oops always false.
            Path hprof = SyntheticHprof.create("1.0.2", 4, 0L)
                    .string(1L, "x")
                    .writeTo(tmp, "32bit.hprof");
            Path indexDb = HeapDumpIndexPaths.indexFor(hprof);
            try (HprofMappedFile file = HprofMappedFile.open(hprof)) {
                HprofIndex.build(file, indexDb, FIXED_CLOCK);
            }
            assertFalse(scalarBool(indexDb, "compressed_oops"));
            assertEquals(4, scalarInt(indexDb, "id_size"));
        }

        @Test
        void smallSixtyFourBitDumpAssumesCompressedOops(@TempDir Path tmp) throws IOException, SQLException {
            // idSize = 8 + file size well below the 32 GiB threshold → assumed
            // to be running with compressed oops.
            Path hprof = SyntheticHprof.create("1.0.2", 8, 0L)
                    .string(1L, "x")
                    .writeTo(tmp, "64bit-small.hprof");
            Path indexDb = HeapDumpIndexPaths.indexFor(hprof);
            try (HprofMappedFile file = HprofMappedFile.open(hprof)) {
                HprofIndex.build(file, indexDb, FIXED_CLOCK);
            }
            assertTrue(scalarBool(indexDb, "compressed_oops"));
            assertEquals(8, scalarInt(indexDb, "id_size"));
        }
    }

    @Nested
    class WarningAggregation {

        @Test
        void truncatedTailRecordsErrorWarningAndFlipsTruncatedFlag(@TempDir Path tmp)
                throws IOException, SQLException {
            // Bogus trailer whose declared body length is much larger than what
            // follows in the file — the reader emits a single ERROR-severity
            // ParseWarning, and HprofIndex must propagate that into
            // dump_metadata.truncated = true.
            byte[] truncatedTail = new byte[]{0x01, 0, 0, 0, 0, 0, 0, 0, 100};
            Path hprof = SyntheticHprof.create("1.0.2", 8, 0L)
                    .string(1L, "x")
                    .appendRaw(truncatedTail)
                    .writeTo(tmp, "truncated.hprof");
            Path indexDb = HeapDumpIndexPaths.indexFor(hprof);

            HprofIndex.IndexResult result;
            try (HprofMappedFile file = HprofMappedFile.open(hprof)) {
                result = HprofIndex.build(file, indexDb, FIXED_CLOCK);
            }
            assertTrue(result.truncated());
            assertEquals(1, result.warningCount());

            try (Connection conn = DriverManager.getConnection("jdbc:duckdb:" + indexDb.toAbsolutePath());
                 Statement stmt = conn.createStatement()) {
                // dump_metadata.warning_count must equal the parse_warning row count.
                assertEquals(scalarLong(stmt, "SELECT COUNT(*) FROM parse_warning"),
                        scalarLong(stmt, "SELECT warning_count FROM dump_metadata"));
                try (ResultSet rs = stmt.executeQuery(
                        "SELECT severity, file_offset, message FROM parse_warning")) {
                    assertTrue(rs.next());
                    assertEquals(2 /* ERROR ordinal */, rs.getInt(1));
                    assertTrue(rs.getLong(2) >= 0, "file_offset should be a non-negative byte offset");
                    assertTrue(rs.getString(3) != null && !rs.getString(3).isEmpty(),
                            "warning message must be populated");
                }
            }
        }

        @Test
        void cleanDumpReportsZeroWarningsAndNotTruncated(@TempDir Path tmp) throws IOException, SQLException {
            Path hprof = SyntheticHprof.create("1.0.2", 8, 0L)
                    .string(1L, "x")
                    .writeTo(tmp, "clean.hprof");
            Path indexDb = HeapDumpIndexPaths.indexFor(hprof);
            HprofIndex.IndexResult result;
            try (HprofMappedFile file = HprofMappedFile.open(hprof)) {
                result = HprofIndex.build(file, indexDb, FIXED_CLOCK);
            }
            assertFalse(result.truncated());
            assertEquals(0, result.warningCount());
            assertEquals(0, scalarLong(indexDb, "SELECT COUNT(*) FROM parse_warning"));
            assertEquals(0, scalarLong(indexDb, "SELECT warning_count FROM dump_metadata"));
        }
    }

    private static boolean scalarBool(Path indexDb, String column) throws SQLException {
        try (Connection conn = DriverManager.getConnection("jdbc:duckdb:" + indexDb.toAbsolutePath());
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT " + column + " FROM dump_metadata")) {
            assertTrue(rs.next());
            return rs.getBoolean(1);
        }
    }

    private static int scalarInt(Path indexDb, String column) throws SQLException {
        try (Connection conn = DriverManager.getConnection("jdbc:duckdb:" + indexDb.toAbsolutePath());
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT " + column + " FROM dump_metadata")) {
            assertTrue(rs.next());
            return rs.getInt(1);
        }
    }

    private static long scalarLong(Statement stmt, String sql) throws SQLException {
        try (ResultSet rs = stmt.executeQuery(sql)) {
            assertTrue(rs.next(), "no rows for query: " + sql);
            return rs.getLong(1);
        }
    }

    private static long scalarLong(Path indexDb, String sql) throws SQLException {
        try (Connection conn = DriverManager.getConnection("jdbc:duckdb:" + indexDb.toAbsolutePath());
             Statement stmt = conn.createStatement()) {
            return scalarLong(stmt, sql);
        }
    }
}
