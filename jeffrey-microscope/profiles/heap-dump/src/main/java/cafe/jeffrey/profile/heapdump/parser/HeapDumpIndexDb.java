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
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import org.duckdb.DuckDBConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Owns the DuckDB connection to a heap-dump-index sibling database
 * ({@code <name>.hprof.idx.duckdb}) and applies the V001 schema.
 *
 * The schema lives at {@code /db/migration/heap-dump-index/V001__init.sql} on
 * the classpath. Per project policy the file is recreated on every build
 * (modify V001 in place rather than introducing V002).
 */
public final class HeapDumpIndexDb implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(HeapDumpIndexDb.class);

    private static final String SCHEMA_RESOURCE = "/db/migration/heap-dump-index/V001__init.sql";

    /**
     * Raises the WAL auto-checkpoint threshold so DuckDB doesn't flush WAL → main
     * file every 16 MB (the default) during a multi-GB index build. Each
     * auto-checkpoint stalls writes; for a bulk load they're pure overhead. The
     * close-time checkpoint (run automatically by DuckDB when the connection
     * closes in {@link #close()}) still fires regardless of this threshold, so
     * durability is preserved before the read-only HeapView reopens the file.
     * {@code 1TB} is DuckDB's documented "effectively disabled" value.
     */
    private static final String PRAGMA_WAL_AUTOCHECKPOINT = "PRAGMA wal_autocheckpoint = '1TB'";

    private final Path path;
    private final DuckDBConnection connection;

    private HeapDumpIndexDb(Path path, DuckDBConnection connection) {
        this.path = path;
        this.connection = connection;
    }

    /**
     * Opens (and creates if necessary) the index DB at {@code path} and runs the
     * V001 DDL. The file is opened in normal read-write mode; the caller is
     * responsible for deleting any pre-existing file when starting a fresh build.
     */
    public static HeapDumpIndexDb openAndInitialize(Path path) throws IOException, SQLException {
        if (path == null) {
            throw new IllegalArgumentException("path must not be null");
        }
        Path parent = path.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        String url = "jdbc:duckdb:" + path.toAbsolutePath();
        Connection raw = DriverManager.getConnection(url);
        DuckDBConnection duckConn;
        try {
            duckConn = raw.unwrap(DuckDBConnection.class);
        } catch (Throwable t) {
            raw.close();
            throw t;
        }

        try {
            applySchema(duckConn);
        } catch (Throwable t) {
            try {
                duckConn.close();
            } catch (SQLException ignored) {
            }
            throw t;
        }

        LOG.debug("Opened heap dump index db: path={}", path);
        return new HeapDumpIndexDb(path, duckConn);
    }

    public DuckDBConnection connection() {
        return connection;
    }

    public Path path() {
        return path;
    }

    @Override
    public void close() throws SQLException {
        connection.close();
    }

    private static void applySchema(DuckDBConnection conn) throws IOException, SQLException {
        String sql = loadResource(SCHEMA_RESOURCE);
        try (Statement s = conn.createStatement()) {
            // DuckDB JDBC supports running multiple ;-delimited statements in a single execute.
            s.execute(sql);
            s.execute(PRAGMA_WAL_AUTOCHECKPOINT);
        }
    }

    private static String loadResource(String resource) throws IOException {
        try (InputStream in = HeapDumpIndexDb.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new UncheckedIOException(new IOException(
                        "Schema resource not found on classpath: resource=" + resource));
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
