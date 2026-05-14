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
package cafe.jeffrey.profile.heapdump.parser.parquet;

import org.duckdb.DuckDBAppender;
import org.duckdb.DuckDBConnection;

import java.nio.file.Path;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Per-worker parquet sink: an in-memory DuckDB instance plus one
 * {@link DuckDBAppender} per registered table. Each worker thread holds
 * exactly one sink; the sink is closed when the worker finishes its slice of
 * the HPROF walk. On {@link #close()} every appender is flushed and the
 * corresponding staging table is written out to its assigned parquet shard
 * with {@code COPY ... TO '<path>' (FORMAT PARQUET)}. The in-memory DB is
 * then released.
 *
 * <p>An in-memory DuckDB per worker isolates appender state from every other
 * thread, sidestepping the connection-sharing failure that forced the
 * previous {@code persist} attempt to be sequential
 * ({@code a03fd5d8c}). Workers never touch the real index DB; only the
 * coordinator does, via {@code INSERT INTO ... SELECT FROM read_parquet}.
 */
public final class ParquetSink implements AutoCloseable {

    private static final String JDBC_URL = "jdbc:duckdb:";

    private final DuckDBConnection conn;

    private final Map<String, TableSink> tables;

    private boolean closed;

    private ParquetSink(DuckDBConnection conn, Map<String, TableSink> tables) {
        this.conn = conn;
        this.tables = tables;
    }

    /**
     * Opens a sink for one worker. {@code tableDdls} maps each table name to
     * its column DDL (e.g. {@code "instance_id BIGINT, class_id BIGINT, ..."} —
     * no constraints, since the staging side does not enforce them). For each
     * entry the sink creates a staging table, registers an appender, and
     * assigns the corresponding parquet output path. Iteration order is
     * preserved on close so callers can rely on deterministic flush order.
     *
     * @param tableDdls    table name → column DDL (no constraints)
     * @param outputPaths  table name → parquet output file
     */
    public static ParquetSink open(
            Map<String, String> tableDdls, Map<String, Path> outputPaths) throws SQLException {
        if (tableDdls == null || tableDdls.isEmpty()) {
            throw new IllegalArgumentException("tableDdls must not be empty");
        }
        if (outputPaths == null || outputPaths.size() != tableDdls.size()) {
            throw new IllegalArgumentException("outputPaths must cover every entry in tableDdls");
        }

        DuckDBConnection conn = (DuckDBConnection)
                DriverManager.getConnection(JDBC_URL).unwrap(DuckDBConnection.class);

        Map<String, TableSink> sinks = new LinkedHashMap<>();
        try {
            for (Map.Entry<String, String> e : tableDdls.entrySet()) {
                String table = e.getKey();
                String columnDdl = e.getValue();
                Path outputPath = outputPaths.get(table);
                if (outputPath == null) {
                    throw new IllegalArgumentException("Missing output path for table: " + table);
                }
                try (Statement s = conn.createStatement()) {
                    s.execute("CREATE TABLE " + table + " (" + columnDdl + ")");
                }
                DuckDBAppender app = conn.createAppender(table);
                sinks.put(table, new TableSink(app, outputPath));
            }
        } catch (SQLException | RuntimeException ex) {
            // Release everything we opened so far before propagating.
            for (TableSink ts : sinks.values()) {
                closeQuietly(ts.appender);
            }
            closeQuietly(conn);
            throw ex;
        }

        return new ParquetSink(conn, sinks);
    }

    /**
     * The appender for the named staging table. Callers drive the standard
     * {@code beginRow / append* / endRow} loop on the returned appender —
     * row-level errors propagate as {@link SQLException}, matching the existing
     * appender pattern in this module.
     */
    public DuckDBAppender appender(String table) {
        TableSink ts = tables.get(table);
        if (ts == null) {
            throw new IllegalArgumentException("No staging table registered: " + table);
        }
        return ts.appender;
    }

    /**
     * Flushes every appender, copies each staging table to its assigned parquet
     * shard, and releases the in-memory DuckDB. The two-phase shape (close all
     * appenders, then COPY each) matters: the appender must be closed before
     * the COPY scans the table, otherwise the not-yet-flushed rows are missing
     * from the output parquet.
     */
    @Override
    public void close() throws SQLException {
        if (closed) {
            return;
        }
        closed = true;

        SQLException firstFailure = null;
        for (TableSink ts : tables.values()) {
            try {
                ts.appender.close();
            } catch (SQLException ex) {
                if (firstFailure == null) {
                    firstFailure = ex;
                }
            }
        }

        if (firstFailure == null) {
            for (Map.Entry<String, TableSink> e : tables.entrySet()) {
                String table = e.getKey();
                Path outputPath = e.getValue().outputPath;
                String sql = "COPY " + table + " TO '" + outputPath.toAbsolutePath()
                        + "' (FORMAT PARQUET)";
                try (Statement s = conn.createStatement()) {
                    s.execute(sql);
                } catch (SQLException ex) {
                    if (firstFailure == null) {
                        firstFailure = ex;
                    }
                }
            }
        }

        try {
            conn.close();
        } catch (SQLException ex) {
            if (firstFailure == null) {
                firstFailure = ex;
            }
        }

        if (firstFailure != null) {
            throw firstFailure;
        }
    }

    private static void closeQuietly(AutoCloseable c) {
        if (c == null) {
            return;
        }
        try {
            c.close();
        } catch (Exception ignored) {
            // suppressed; we're already on an error path
        }
    }

    private record TableSink(DuckDBAppender appender, Path outputPath) {
    }
}
