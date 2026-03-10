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

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SegmentAllocator;
import java.sql.*;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Executor;

/**
 * A {@link Connection} implementation wrapping a native libsql connection.
 *
 * <p>Each connection owns its own {@link Arena} for memory management.
 * When autoCommit is disabled, operations run within a native transaction.
 */
public class LibSqlConnection implements Connection {

    private final LibSqlDatabase database;
    private final Arena arena;
    private final MemorySegment connectionStruct;
    private boolean closed;
    private boolean autoCommit = true;
    private MemorySegment transactionStruct;

    LibSqlConnection(LibSqlDatabase database) {
        this.database = database;
        this.arena = Arena.ofConfined();
        this.connectionStruct = database.connect(arena);
    }

    /**
     * Returns the raw native connection struct for internal use.
     */
    MemorySegment connectionStruct() {
        return connectionStruct;
    }

    /**
     * Returns the arena associated with this connection.
     */
    Arena arena() {
        return arena;
    }

    /**
     * Returns the active transaction struct, or null if autoCommit is enabled.
     */
    MemorySegment transactionStruct() {
        return transactionStruct;
    }

    @Override
    public Statement createStatement() throws SQLException {
        checkClosed();
        return new LibSqlPreparedStatement(this, null);
    }

    @Override
    public PreparedStatement prepareStatement(String sql) throws SQLException {
        checkClosed();
        return new LibSqlPreparedStatement(this, sql);
    }

    @Override
    public CallableStatement prepareCall(String sql) throws SQLException {
        throw new SQLFeatureNotSupportedException("CallableStatement not supported");
    }

    @Override
    public String nativeSQL(String sql) {
        return sql;
    }

    @Override
    public void setAutoCommit(boolean autoCommit) throws SQLException {
        checkClosed();
        if (this.autoCommit == autoCommit) {
            return;
        }
        this.autoCommit = autoCommit;
        if (!autoCommit) {
            beginTransaction();
        } else {
            if (transactionStruct != null) {
                commit();
            }
        }
    }

    @Override
    public boolean getAutoCommit() {
        return autoCommit;
    }

    private void beginTransaction() throws SQLException {
        try {
            transactionStruct = (MemorySegment) LibSql.CONNECTION_TRANSACTION.invokeExact(
                    (SegmentAllocator) arena, connectionStruct);
            MemorySegment err = LibSql.getErr(transactionStruct);
            LibSql.checkError(err, "Failed to begin transaction");
        } catch (LibSqlException e) {
            throw e.toSQLException();
        } catch (Throwable t) {
            throw new SQLException("Failed to begin transaction", t);
        }
    }

    @Override
    public void commit() throws SQLException {
        checkClosed();
        if (autoCommit) {
            throw new SQLException("Cannot commit when autoCommit is enabled");
        }
        if (transactionStruct != null) {
            try {
                LibSql.TRANSACTION_COMMIT.invokeExact(transactionStruct);
                transactionStruct = null;
                // Start a new transaction for the next set of operations
                beginTransaction();
            } catch (Throwable t) {
                throw new SQLException("Failed to commit transaction", t);
            }
        }
    }

    @Override
    public void rollback() throws SQLException {
        checkClosed();
        if (autoCommit) {
            throw new SQLException("Cannot rollback when autoCommit is enabled");
        }
        if (transactionStruct != null) {
            try {
                LibSql.TRANSACTION_ROLLBACK.invokeExact(transactionStruct);
                transactionStruct = null;
                // Start a new transaction for the next set of operations
                beginTransaction();
            } catch (Throwable t) {
                throw new SQLException("Failed to rollback transaction", t);
            }
        }
    }

    @Override
    public void close() throws SQLException {
        if (!closed) {
            closed = true;
            // Rollback any pending transaction
            if (transactionStruct != null) {
                try {
                    LibSql.TRANSACTION_ROLLBACK.invokeExact(transactionStruct);
                } catch (Throwable t) {
                    // Ignore cleanup errors
                }
                transactionStruct = null;
            }
            try {
                LibSql.CONNECTION_DEINIT.invokeExact(connectionStruct);
            } catch (Throwable t) {
                throw new SQLException("Failed to close connection", t);
            } finally {
                arena.close();
            }
        }
    }

    @Override
    public boolean isClosed() {
        return closed;
    }

    @Override
    public DatabaseMetaData getMetaData() throws SQLException {
        checkClosed();
        return new LibSqlDatabaseMetaData(this);
    }

    @Override
    public void setReadOnly(boolean readOnly) {
        // No-op — libsql doesn't have a read-only connection mode
    }

    @Override
    public boolean isReadOnly() {
        return false;
    }

    @Override
    public void setCatalog(String catalog) {
        // No-op
    }

    @Override
    public String getCatalog() {
        return null;
    }

    @Override
    public void setTransactionIsolation(int level) {
        // libsql supports serializable isolation only
    }

    @Override
    public int getTransactionIsolation() {
        return Connection.TRANSACTION_SERIALIZABLE;
    }

    @Override
    public SQLWarning getWarnings() {
        return null;
    }

    @Override
    public void clearWarnings() {
        // No-op
    }

    @Override
    public Statement createStatement(int resultSetType, int resultSetConcurrency) throws SQLException {
        return createStatement();
    }

    @Override
    public PreparedStatement prepareStatement(String sql, int resultSetType, int resultSetConcurrency)
            throws SQLException {
        return prepareStatement(sql);
    }

    @Override
    public CallableStatement prepareCall(String sql, int resultSetType, int resultSetConcurrency) throws SQLException {
        throw new SQLFeatureNotSupportedException("CallableStatement not supported");
    }

    @Override
    public Map<String, Class<?>> getTypeMap() {
        return Map.of();
    }

    @Override
    public void setTypeMap(Map<String, Class<?>> map) {
        // No-op
    }

    @Override
    public void setHoldability(int holdability) {
        // No-op
    }

    @Override
    public int getHoldability() {
        return ResultSet.HOLD_CURSORS_OVER_COMMIT;
    }

    @Override
    public Savepoint setSavepoint() throws SQLException {
        throw new SQLFeatureNotSupportedException("Savepoints not supported");
    }

    @Override
    public Savepoint setSavepoint(String name) throws SQLException {
        throw new SQLFeatureNotSupportedException("Savepoints not supported");
    }

    @Override
    public void rollback(Savepoint savepoint) throws SQLException {
        throw new SQLFeatureNotSupportedException("Savepoints not supported");
    }

    @Override
    public void releaseSavepoint(Savepoint savepoint) throws SQLException {
        throw new SQLFeatureNotSupportedException("Savepoints not supported");
    }

    @Override
    public Statement createStatement(int resultSetType, int resultSetConcurrency, int resultSetHoldability)
            throws SQLException {
        return createStatement();
    }

    @Override
    public PreparedStatement prepareStatement(String sql, int resultSetType, int resultSetConcurrency,
                                              int resultSetHoldability) throws SQLException {
        return prepareStatement(sql);
    }

    @Override
    public CallableStatement prepareCall(String sql, int resultSetType, int resultSetConcurrency,
                                         int resultSetHoldability) throws SQLException {
        throw new SQLFeatureNotSupportedException("CallableStatement not supported");
    }

    @Override
    public PreparedStatement prepareStatement(String sql, int autoGeneratedKeys) throws SQLException {
        return prepareStatement(sql);
    }

    @Override
    public PreparedStatement prepareStatement(String sql, int[] columnIndexes) throws SQLException {
        return prepareStatement(sql);
    }

    @Override
    public PreparedStatement prepareStatement(String sql, String[] columnNames) throws SQLException {
        return prepareStatement(sql);
    }

    @Override
    public Clob createClob() throws SQLException {
        throw new SQLFeatureNotSupportedException("Clob not supported");
    }

    @Override
    public Blob createBlob() throws SQLException {
        throw new SQLFeatureNotSupportedException("Blob not supported");
    }

    @Override
    public NClob createNClob() throws SQLException {
        throw new SQLFeatureNotSupportedException("NClob not supported");
    }

    @Override
    public SQLXML createSQLXML() throws SQLException {
        throw new SQLFeatureNotSupportedException("SQLXML not supported");
    }

    @Override
    public boolean isValid(int timeout) {
        return !closed;
    }

    @Override
    public void setClientInfo(String name, String value) {
        // No-op
    }

    @Override
    public void setClientInfo(Properties properties) {
        // No-op
    }

    @Override
    public String getClientInfo(String name) {
        return null;
    }

    @Override
    public Properties getClientInfo() {
        return new Properties();
    }

    @Override
    public Array createArrayOf(String typeName, Object[] elements) throws SQLException {
        throw new SQLFeatureNotSupportedException("Arrays not supported");
    }

    @Override
    public Struct createStruct(String typeName, Object[] attributes) throws SQLException {
        throw new SQLFeatureNotSupportedException("Structs not supported");
    }

    @Override
    public void setSchema(String schema) {
        // No-op
    }

    @Override
    public String getSchema() {
        return null;
    }

    @Override
    public void abort(Executor executor) throws SQLException {
        close();
    }

    @Override
    public void setNetworkTimeout(Executor executor, int milliseconds) {
        // No-op
    }

    @Override
    public int getNetworkTimeout() {
        return 0;
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

    /**
     * Executes a batch SQL string directly on the connection.
     * This is useful for DDL and multi-statement scripts.
     */
    public void executeBatch(String sql) throws SQLException {
        checkClosed();
        try {
            MemorySegment sqlStr = arena.allocateFrom(sql);
            MemorySegment batchResult = (MemorySegment) LibSql.CONNECTION_BATCH.invokeExact(
                    (SegmentAllocator) arena, connectionStruct, sqlStr);
            MemorySegment batchErr = (MemorySegment) LibSql.BATCH_ERR.get(batchResult, 0L);
            LibSql.checkError(batchErr, "Batch execution failed");
        } catch (LibSqlException e) {
            throw e.toSQLException();
        } catch (Throwable t) {
            throw new SQLException("Batch execution failed", t);
        }
    }

    private void checkClosed() throws SQLException {
        if (closed) {
            throw new SQLException("Connection is closed");
        }
    }
}
