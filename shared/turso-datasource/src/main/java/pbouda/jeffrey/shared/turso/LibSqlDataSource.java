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

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.logging.Logger;

/**
 * A {@link DataSource} implementation backed by an embedded libsql (Turso) database.
 *
 * <p>Each call to {@link #getConnection()} creates a new native connection.
 * Thread-safe: connections are independent and can be used concurrently.
 *
 * <p>When MVCC mode is enabled (default), multiple connections can write simultaneously
 * without {@code SQLITE_BUSY} errors.
 */
public class LibSqlDataSource implements DataSource, AutoCloseable {

    private final LibSqlDatabase database;
    private final String path;
    private PrintWriter logWriter;
    private int loginTimeout;

    /**
     * Creates a DataSource for an embedded libsql database with MVCC enabled.
     *
     * @param path local file path, or {@code ":memory:"} for in-memory database
     */
    public LibSqlDataSource(String path) {
        this(path, true);
    }

    /**
     * Creates a DataSource for an embedded libsql database.
     *
     * @param path       local file path, or {@code ":memory:"} for in-memory database
     * @param enableMvcc if true, enables MVCC journal mode for concurrent writes
     */
    public LibSqlDataSource(String path, boolean enableMvcc) {
        this.path = path;
        this.database = new LibSqlDatabase(path, enableMvcc);
    }

    /**
     * Creates a DataSource for a libsql database with remote sync support.
     *
     * @param path       local file path for the embedded replica
     * @param syncUrl    URL to the primary Turso database
     * @param authToken  authentication token for remote access
     * @param enableMvcc if true, enables MVCC journal mode
     */
    public LibSqlDataSource(String path, String syncUrl, String authToken, boolean enableMvcc) {
        this.path = path;
        this.database = new LibSqlDatabase(path, syncUrl, authToken, enableMvcc);
    }

    @Override
    public Connection getConnection() throws SQLException {
        try {
            return new LibSqlConnection(database);
        } catch (LibSqlException e) {
            throw e.toSQLException();
        }
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        return getConnection();
    }

    /**
     * Returns the underlying database handle for advanced usage (sync, etc.).
     */
    public LibSqlDatabase getDatabase() {
        return database;
    }

    /**
     * Returns the database path this DataSource was created with.
     */
    public String getPath() {
        return path;
    }

    @Override
    public void close() {
        database.close();
    }

    @Override
    public PrintWriter getLogWriter() {
        return logWriter;
    }

    @Override
    public void setLogWriter(PrintWriter out) {
        this.logWriter = out;
    }

    @Override
    public int getLoginTimeout() {
        return loginTimeout;
    }

    @Override
    public void setLoginTimeout(int seconds) {
        this.loginTimeout = seconds;
    }

    @Override
    public Logger getParentLogger() throws SQLFeatureNotSupportedException {
        throw new SQLFeatureNotSupportedException("java.util.logging not supported");
    }

    @Override
    public <T> T unwrap(Class<T> iface) throws SQLException {
        if (iface.isAssignableFrom(getClass())) {
            return iface.cast(this);
        }
        throw new SQLException("Cannot unwrap to " + iface.getName());
    }

    @Override
    public boolean isWrapperFor(Class<?> iface) {
        return iface.isAssignableFrom(getClass());
    }
}
