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

package pbouda.jeffrey.shared.turso;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.foreign.*;

/**
 * Manages a libsql database handle and its lifecycle.
 *
 * <p>Wraps the native {@code libsql_database_t} and provides methods to create connections
 * and enable MVCC mode for concurrent writes.
 */
public class LibSqlDatabase implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(LibSqlDatabase.class);

    private final Arena arena;
    private final MemorySegment databaseStruct;
    private volatile boolean closed;

    /**
     * Opens an embedded libsql database at the given path.
     *
     * @param path       local file path, or {@code ":memory:"} for in-memory database
     * @param enableMvcc if true, enables MVCC journal mode for concurrent writes
     */
    public LibSqlDatabase(String path, boolean enableMvcc) {
        this(path, null, null, enableMvcc);
    }

    /**
     * Opens a libsql database with optional remote sync.
     *
     * @param path       local file path, or {@code ":memory:"} for in-memory database
     * @param syncUrl    optional URL to the primary database for replication (null for local-only)
     * @param authToken  optional auth token for remote sync (null for local-only)
     * @param enableMvcc if true, enables MVCC journal mode for concurrent writes
     */
    public LibSqlDatabase(String path, String syncUrl, String authToken, boolean enableMvcc) {
        this.arena = Arena.ofShared();

        try {
            // Build the database descriptor
            MemorySegment desc = arena.allocate(LibSql.DATABASE_DESC_T);

            // Zero-initialize all fields
            desc.fill((byte) 0);

            // Set path (required)
            MemorySegment pathStr = arena.allocateFrom(path);
            setAddress(desc, LibSql.DATABASE_DESC_T, "path", pathStr);

            // Set optional sync URL
            if (syncUrl != null) {
                MemorySegment urlStr = arena.allocateFrom(syncUrl);
                setAddress(desc, LibSql.DATABASE_DESC_T, "url", urlStr);
            }

            // Set optional auth token
            if (authToken != null) {
                MemorySegment tokenStr = arena.allocateFrom(authToken);
                setAddress(desc, LibSql.DATABASE_DESC_T, "auth_token", tokenStr);
            }

            LOG.info("Opening libsql database: path={}", path);

            // libsql_database_t libsql_database_init(libsql_database_desc_t desc)
            this.databaseStruct = (MemorySegment) LibSql.DATABASE_INIT.invokeExact(
                    (SegmentAllocator) arena, desc);

            MemorySegment err = LibSql.getErr(databaseStruct);
            LibSql.checkError(err, "Failed to initialize database");

            // Enable MVCC for concurrent writes (Turso 0.5.0+)
            if (enableMvcc) {
                enableMvccMode();
            }

            LOG.info("Database opened successfully: path={} mvcc={}", path, enableMvcc);

        } catch (Throwable t) {
            arena.close();
            if (t instanceof LibSqlException lse) {
                throw lse;
            }
            throw new LibSqlException("Failed to open database: " + path, t);
        }
    }

    private void enableMvccMode() throws Throwable {
        MemorySegment connStruct = (MemorySegment) LibSql.DATABASE_CONNECT.invokeExact(
                (SegmentAllocator) arena, databaseStruct);
        MemorySegment connErr = LibSql.getErr(connStruct);
        LibSql.checkError(connErr, "Failed to connect for MVCC setup");

        try {
            MemorySegment sql = arena.allocateFrom("PRAGMA journal_mode = 'mvcc'");
            MemorySegment batchResult = (MemorySegment) LibSql.CONNECTION_BATCH.invokeExact(
                    (SegmentAllocator) arena, connStruct, sql);
            MemorySegment batchErr = (MemorySegment) LibSql.BATCH_ERR.get(batchResult, 0L);
            LibSql.checkError(batchErr, "Failed to enable MVCC mode");
        } finally {
            LibSql.CONNECTION_DEINIT.invokeExact(connStruct);
        }
    }

    /**
     * Creates a new native connection to this database.
     * Each connection is independent and can be used from a separate thread.
     *
     * @param connectionArena arena for the connection's memory allocations
     * @return the native {@code libsql_connection_t} struct
     */
    MemorySegment connect(Arena connectionArena) {
        if (closed) {
            throw new LibSqlException("Database is closed");
        }
        try {
            MemorySegment connStruct = (MemorySegment) LibSql.DATABASE_CONNECT.invokeExact(
                    (SegmentAllocator) connectionArena, databaseStruct);
            MemorySegment err = LibSql.getErr(connStruct);
            LibSql.checkError(err, "Failed to create connection");
            return connStruct;
        } catch (LibSqlException e) {
            throw e;
        } catch (Throwable t) {
            throw new LibSqlException("Failed to create connection", t);
        }
    }

    /**
     * Returns the underlying database struct for advanced usage.
     */
    MemorySegment databaseStruct() {
        return databaseStruct;
    }

    @Override
    public void close() {
        if (!closed) {
            closed = true;
            try {
                LibSql.DATABASE_DEINIT.invokeExact(databaseStruct);
            } catch (Throwable t) {
                LOG.warn("Error closing database: {}", t.getMessage());
            }
            arena.close();
            LOG.info("Database closed");
        }
    }

    private static void setAddress(MemorySegment struct, StructLayout layout, String field, MemorySegment value) {
        struct.set(ValueLayout.ADDRESS, layout.byteOffset(MemoryLayout.PathElement.groupElement(field)), value);
    }
}
