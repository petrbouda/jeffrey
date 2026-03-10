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

import java.io.InputStream;
import java.io.Reader;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SegmentAllocator;
import java.math.BigDecimal;
import java.net.URL;
import java.sql.*;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;

/**
 * A {@link PreparedStatement} backed by a native libsql prepared statement.
 *
 * <p>Supports positional parameter binding via {@code ?} placeholders.
 * Parameters are bound using libsql's native bind functions.
 */
public class LibSqlPreparedStatement implements PreparedStatement {

    private final LibSqlConnection connection;
    private final String sql;
    private final Map<Integer, ParameterBinding> parameters = new HashMap<>();
    private boolean closed;
    private LibSqlResultSet currentResultSet;
    private long updateCount = -1;

    LibSqlPreparedStatement(LibSqlConnection connection, String sql) {
        this.connection = connection;
        this.sql = sql;
    }

    private MemorySegment prepareNativeStatement() throws SQLException {
        Arena arena = connection.arena();
        try {
            MemorySegment sqlStr = arena.allocateFrom(sql);
            MemorySegment txn = connection.transactionStruct();
            MemorySegment stmtStruct;

            if (txn != null) {
                stmtStruct = (MemorySegment) LibSql.TRANSACTION_PREPARE.invokeExact(
                        (SegmentAllocator) arena, txn, sqlStr);
            } else {
                stmtStruct = (MemorySegment) LibSql.CONNECTION_PREPARE.invokeExact(
                        (SegmentAllocator) arena, connection.connectionStruct(), sqlStr);
            }

            MemorySegment err = LibSql.getErr(stmtStruct);
            LibSql.checkError(err, "Failed to prepare statement");

            // Bind all parameters
            for (var entry : parameters.entrySet()) {
                bindParameter(arena, stmtStruct, entry.getValue());
            }

            return stmtStruct;
        } catch (LibSqlException e) {
            throw e.toSQLException();
        } catch (Throwable t) {
            throw new SQLException("Failed to prepare statement: " + sql, t);
        }
    }

    private void bindParameter(Arena arena, MemorySegment stmtStruct, ParameterBinding binding) throws Throwable {
        MemorySegment value = binding.toNativeValue(arena);

        MemorySegment bindResult = (MemorySegment) LibSql.STATEMENT_BIND_VALUE.invokeExact(
                (SegmentAllocator) arena, stmtStruct, value);
        MemorySegment bindErr = (MemorySegment) LibSql.BIND_ERR.get(bindResult, 0L);
        LibSql.checkError(bindErr, "Failed to bind parameter");
    }

    @Override
    public ResultSet executeQuery() throws SQLException {
        checkClosed();
        MemorySegment stmtStruct = prepareNativeStatement();
        try {
            MemorySegment rowsStruct = (MemorySegment) LibSql.STATEMENT_QUERY.invokeExact(
                    (SegmentAllocator) connection.arena(), stmtStruct);
            MemorySegment err = LibSql.getErr(rowsStruct);
            LibSql.checkError(err, "Failed to execute query");

            currentResultSet = new LibSqlResultSet(connection.arena(), rowsStruct);
            updateCount = -1;
            return currentResultSet;
        } catch (LibSqlException e) {
            throw e.toSQLException();
        } catch (Throwable t) {
            throw new SQLException("Failed to execute query", t);
        }
    }

    @Override
    public int executeUpdate() throws SQLException {
        checkClosed();
        MemorySegment stmtStruct = prepareNativeStatement();
        try {
            MemorySegment execResult = (MemorySegment) LibSql.STATEMENT_EXECUTE.invokeExact(
                    (SegmentAllocator) connection.arena(), stmtStruct);
            MemorySegment err = (MemorySegment) LibSql.EXECUTE_ERR.get(execResult, 0L);
            LibSql.checkError(err, "Failed to execute update");

            updateCount = (long) LibSql.EXECUTE_ROWS_CHANGED.get(execResult, 0L);
            currentResultSet = null;
            return (int) updateCount;
        } catch (LibSqlException e) {
            throw e.toSQLException();
        } catch (Throwable t) {
            throw new SQLException("Failed to execute update", t);
        }
    }

    @Override
    public boolean execute() throws SQLException {
        checkClosed();
        String trimmed = sql.trim().toUpperCase();
        if (trimmed.startsWith("SELECT") || trimmed.startsWith("PRAGMA") || trimmed.startsWith("EXPLAIN")) {
            executeQuery();
            return true;
        } else {
            executeUpdate();
            return false;
        }
    }

    @Override
    public ResultSet executeQuery(String sql) throws SQLException {
        checkClosed();
        return new LibSqlPreparedStatement(connection, sql).executeQuery();
    }

    @Override
    public int executeUpdate(String sql) throws SQLException {
        checkClosed();
        return new LibSqlPreparedStatement(connection, sql).executeUpdate();
    }

    @Override
    public boolean execute(String sql) throws SQLException {
        checkClosed();
        return new LibSqlPreparedStatement(connection, sql).execute();
    }

    // ──────────────────────────────────────────────────────────────────────
    // Parameter Binding
    // ──────────────────────────────────────────────────────────────────────

    @Override
    public void setNull(int parameterIndex, int sqlType) {
        parameters.put(parameterIndex, ParameterBinding.ofNull());
    }

    @Override
    public void setBoolean(int parameterIndex, boolean x) {
        parameters.put(parameterIndex, ParameterBinding.ofLong(x ? 1 : 0));
    }

    @Override
    public void setByte(int parameterIndex, byte x) {
        parameters.put(parameterIndex, ParameterBinding.ofLong(x));
    }

    @Override
    public void setShort(int parameterIndex, short x) {
        parameters.put(parameterIndex, ParameterBinding.ofLong(x));
    }

    @Override
    public void setInt(int parameterIndex, int x) {
        parameters.put(parameterIndex, ParameterBinding.ofLong(x));
    }

    @Override
    public void setLong(int parameterIndex, long x) {
        parameters.put(parameterIndex, ParameterBinding.ofLong(x));
    }

    @Override
    public void setFloat(int parameterIndex, float x) {
        parameters.put(parameterIndex, ParameterBinding.ofDouble(x));
    }

    @Override
    public void setDouble(int parameterIndex, double x) {
        parameters.put(parameterIndex, ParameterBinding.ofDouble(x));
    }

    @Override
    public void setBigDecimal(int parameterIndex, BigDecimal x) {
        if (x == null) {
            setNull(parameterIndex, Types.DECIMAL);
        } else {
            parameters.put(parameterIndex, ParameterBinding.ofText(x.toPlainString()));
        }
    }

    @Override
    public void setString(int parameterIndex, String x) {
        if (x == null) {
            setNull(parameterIndex, Types.VARCHAR);
        } else {
            parameters.put(parameterIndex, ParameterBinding.ofText(x));
        }
    }

    @Override
    public void setBytes(int parameterIndex, byte[] x) {
        if (x == null) {
            setNull(parameterIndex, Types.BLOB);
        } else {
            parameters.put(parameterIndex, ParameterBinding.ofBlob(x));
        }
    }

    @Override
    public void setDate(int parameterIndex, Date x) {
        if (x == null) {
            setNull(parameterIndex, Types.DATE);
        } else {
            parameters.put(parameterIndex, ParameterBinding.ofLong(x.getTime()));
        }
    }

    @Override
    public void setTime(int parameterIndex, Time x) {
        if (x == null) {
            setNull(parameterIndex, Types.TIME);
        } else {
            parameters.put(parameterIndex, ParameterBinding.ofLong(x.getTime()));
        }
    }

    @Override
    public void setTimestamp(int parameterIndex, Timestamp x) {
        if (x == null) {
            setNull(parameterIndex, Types.TIMESTAMP);
        } else {
            // Store as epoch microseconds (consistent with Jeffrey's internal convention)
            parameters.put(parameterIndex, ParameterBinding.ofLong(
                    x.getTime() * 1000 + x.getNanos() / 1000 % 1000));
        }
    }

    @Override
    public void setObject(int parameterIndex, Object x) throws SQLException {
        if (x == null) {
            setNull(parameterIndex, Types.NULL);
        } else if (x instanceof String s) {
            setString(parameterIndex, s);
        } else if (x instanceof Integer i) {
            setInt(parameterIndex, i);
        } else if (x instanceof Long l) {
            setLong(parameterIndex, l);
        } else if (x instanceof Double d) {
            setDouble(parameterIndex, d);
        } else if (x instanceof Float f) {
            setFloat(parameterIndex, f);
        } else if (x instanceof Boolean b) {
            setBoolean(parameterIndex, b);
        } else if (x instanceof byte[] bytes) {
            setBytes(parameterIndex, bytes);
        } else if (x instanceof Timestamp ts) {
            setTimestamp(parameterIndex, ts);
        } else if (x instanceof Date d) {
            setDate(parameterIndex, d);
        } else if (x instanceof BigDecimal bd) {
            setBigDecimal(parameterIndex, bd);
        } else {
            setString(parameterIndex, x.toString());
        }
    }

    @Override
    public void setObject(int parameterIndex, Object x, int targetSqlType) throws SQLException {
        setObject(parameterIndex, x);
    }

    @Override
    public void setObject(int parameterIndex, Object x, int targetSqlType, int scaleOrLength) throws SQLException {
        setObject(parameterIndex, x);
    }

    @Override
    public void clearParameters() {
        parameters.clear();
    }

    // ──────────────────────────────────────────────────────────────────────
    // Result access
    // ──────────────────────────────────────────────────────────────────────

    @Override
    public ResultSet getResultSet() {
        return currentResultSet;
    }

    @Override
    public int getUpdateCount() {
        return (int) updateCount;
    }

    @Override
    public boolean getMoreResults() {
        currentResultSet = null;
        updateCount = -1;
        return false;
    }

    @Override
    public boolean getMoreResults(int current) {
        return getMoreResults();
    }

    @Override
    public ResultSet getGeneratedKeys() throws SQLException {
        throw new SQLFeatureNotSupportedException("getGeneratedKeys not supported");
    }

    @Override
    public int executeUpdate(String sql, int autoGeneratedKeys) throws SQLException {
        return executeUpdate(sql);
    }

    @Override
    public int executeUpdate(String sql, int[] columnIndexes) throws SQLException {
        return executeUpdate(sql);
    }

    @Override
    public int executeUpdate(String sql, String[] columnNames) throws SQLException {
        return executeUpdate(sql);
    }

    @Override
    public boolean execute(String sql, int autoGeneratedKeys) throws SQLException {
        return execute(sql);
    }

    @Override
    public boolean execute(String sql, int[] columnIndexes) throws SQLException {
        return execute(sql);
    }

    @Override
    public boolean execute(String sql, String[] columnNames) throws SQLException {
        return execute(sql);
    }

    // ──────────────────────────────────────────────────────────────────────
    // Statement configuration (mostly no-ops)
    // ──────────────────────────────────────────────────────────────────────

    @Override
    public int getMaxFieldSize() {
        return 0;
    }

    @Override
    public void setMaxFieldSize(int max) {
    }

    @Override
    public int getMaxRows() {
        return 0;
    }

    @Override
    public void setMaxRows(int max) {
    }

    @Override
    public void setEscapeProcessing(boolean enable) {
    }

    @Override
    public int getQueryTimeout() {
        return 0;
    }

    @Override
    public void setQueryTimeout(int seconds) {
    }

    @Override
    public void cancel() throws SQLException {
        throw new SQLFeatureNotSupportedException("cancel not supported");
    }

    @Override
    public SQLWarning getWarnings() {
        return null;
    }

    @Override
    public void clearWarnings() {
    }

    @Override
    public void setCursorName(String name) throws SQLException {
        throw new SQLFeatureNotSupportedException("cursors not supported");
    }

    @Override
    public void setFetchDirection(int direction) {
    }

    @Override
    public int getFetchDirection() {
        return ResultSet.FETCH_FORWARD;
    }

    @Override
    public void setFetchSize(int rows) {
    }

    @Override
    public int getFetchSize() {
        return 0;
    }

    @Override
    public int getResultSetConcurrency() {
        return ResultSet.CONCUR_READ_ONLY;
    }

    @Override
    public int getResultSetType() {
        return ResultSet.TYPE_FORWARD_ONLY;
    }

    @Override
    public void addBatch() {
        // Basic batch support not implemented for prepared statements
    }

    @Override
    public void addBatch(String sql) {
        // Basic batch support not implemented
    }

    @Override
    public void clearBatch() {
    }

    @Override
    public int[] executeBatch() throws SQLException {
        return new int[0];
    }

    @Override
    public Connection getConnection() {
        return connection;
    }

    @Override
    public int getResultSetHoldability() {
        return ResultSet.HOLD_CURSORS_OVER_COMMIT;
    }

    @Override
    public boolean isClosed() {
        return closed;
    }

    @Override
    public void setPoolable(boolean poolable) {
    }

    @Override
    public boolean isPoolable() {
        return false;
    }

    @Override
    public void closeOnCompletion() {
    }

    @Override
    public boolean isCloseOnCompletion() {
        return false;
    }

    @Override
    public void close() {
        closed = true;
        if (currentResultSet != null) {
            currentResultSet.close();
            currentResultSet = null;
        }
    }

    @Override
    public ResultSetMetaData getMetaData() throws SQLException {
        if (currentResultSet != null) {
            return currentResultSet.getMetaData();
        }
        return null;
    }

    @Override
    public ParameterMetaData getParameterMetaData() throws SQLException {
        throw new SQLFeatureNotSupportedException("ParameterMetaData not supported");
    }

    // ──────────────────────────────────────────────────────────────────────
    // Unsupported setters (streams, LOBs, etc.)
    // ──────────────────────────────────────────────────────────────────────

    @Override
    public void setAsciiStream(int parameterIndex, InputStream x, int length) throws SQLException {
        throw new SQLFeatureNotSupportedException("Stream parameters not supported");
    }

    @Override
    @SuppressWarnings("deprecation")
    public void setUnicodeStream(int parameterIndex, InputStream x, int length) throws SQLException {
        throw new SQLFeatureNotSupportedException("Stream parameters not supported");
    }

    @Override
    public void setBinaryStream(int parameterIndex, InputStream x, int length) throws SQLException {
        throw new SQLFeatureNotSupportedException("Stream parameters not supported");
    }

    @Override
    public void setCharacterStream(int parameterIndex, Reader reader, int length) throws SQLException {
        throw new SQLFeatureNotSupportedException("Stream parameters not supported");
    }

    @Override
    public void setRef(int parameterIndex, Ref x) throws SQLException {
        throw new SQLFeatureNotSupportedException("Ref not supported");
    }

    @Override
    public void setBlob(int parameterIndex, Blob x) throws SQLException {
        throw new SQLFeatureNotSupportedException("Blob not supported");
    }

    @Override
    public void setClob(int parameterIndex, Clob x) throws SQLException {
        throw new SQLFeatureNotSupportedException("Clob not supported");
    }

    @Override
    public void setArray(int parameterIndex, Array x) throws SQLException {
        throw new SQLFeatureNotSupportedException("Array not supported");
    }

    @Override
    public void setDate(int parameterIndex, Date x, Calendar cal) throws SQLException {
        setDate(parameterIndex, x);
    }

    @Override
    public void setTime(int parameterIndex, Time x, Calendar cal) throws SQLException {
        setTime(parameterIndex, x);
    }

    @Override
    public void setTimestamp(int parameterIndex, Timestamp x, Calendar cal) throws SQLException {
        setTimestamp(parameterIndex, x);
    }

    @Override
    public void setNull(int parameterIndex, int sqlType, String typeName) throws SQLException {
        setNull(parameterIndex, sqlType);
    }

    @Override
    public void setURL(int parameterIndex, URL x) throws SQLException {
        if (x == null) {
            setNull(parameterIndex, Types.VARCHAR);
        } else {
            setString(parameterIndex, x.toString());
        }
    }

    @Override
    public void setRowId(int parameterIndex, RowId x) throws SQLException {
        throw new SQLFeatureNotSupportedException("RowId not supported");
    }

    @Override
    public void setNString(int parameterIndex, String value) throws SQLException {
        setString(parameterIndex, value);
    }

    @Override
    public void setNCharacterStream(int parameterIndex, Reader value, long length) throws SQLException {
        throw new SQLFeatureNotSupportedException("NCharacterStream not supported");
    }

    @Override
    public void setNClob(int parameterIndex, NClob value) throws SQLException {
        throw new SQLFeatureNotSupportedException("NClob not supported");
    }

    @Override
    public void setClob(int parameterIndex, Reader reader, long length) throws SQLException {
        throw new SQLFeatureNotSupportedException("Clob not supported");
    }

    @Override
    public void setBlob(int parameterIndex, InputStream inputStream, long length) throws SQLException {
        throw new SQLFeatureNotSupportedException("Blob not supported");
    }

    @Override
    public void setNClob(int parameterIndex, Reader reader, long length) throws SQLException {
        throw new SQLFeatureNotSupportedException("NClob not supported");
    }

    @Override
    public void setSQLXML(int parameterIndex, SQLXML xmlObject) throws SQLException {
        throw new SQLFeatureNotSupportedException("SQLXML not supported");
    }

    @Override
    public void setAsciiStream(int parameterIndex, InputStream x, long length) throws SQLException {
        throw new SQLFeatureNotSupportedException("Stream parameters not supported");
    }

    @Override
    public void setBinaryStream(int parameterIndex, InputStream x, long length) throws SQLException {
        throw new SQLFeatureNotSupportedException("Stream parameters not supported");
    }

    @Override
    public void setCharacterStream(int parameterIndex, Reader reader, long length) throws SQLException {
        throw new SQLFeatureNotSupportedException("Stream parameters not supported");
    }

    @Override
    public void setAsciiStream(int parameterIndex, InputStream x) throws SQLException {
        throw new SQLFeatureNotSupportedException("Stream parameters not supported");
    }

    @Override
    public void setBinaryStream(int parameterIndex, InputStream x) throws SQLException {
        throw new SQLFeatureNotSupportedException("Stream parameters not supported");
    }

    @Override
    public void setCharacterStream(int parameterIndex, Reader reader) throws SQLException {
        throw new SQLFeatureNotSupportedException("Stream parameters not supported");
    }

    @Override
    public void setNCharacterStream(int parameterIndex, Reader value) throws SQLException {
        throw new SQLFeatureNotSupportedException("NCharacterStream not supported");
    }

    @Override
    public void setClob(int parameterIndex, Reader reader) throws SQLException {
        throw new SQLFeatureNotSupportedException("Clob not supported");
    }

    @Override
    public void setBlob(int parameterIndex, InputStream inputStream) throws SQLException {
        throw new SQLFeatureNotSupportedException("Blob not supported");
    }

    @Override
    public void setNClob(int parameterIndex, Reader reader) throws SQLException {
        throw new SQLFeatureNotSupportedException("NClob not supported");
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

    private void checkClosed() throws SQLException {
        if (closed) {
            throw new SQLException("Statement is closed");
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    // Internal parameter binding representation
    // ──────────────────────────────────────────────────────────────────────

    sealed interface ParameterBinding {

        MemorySegment toNativeValue(Arena arena) throws Throwable;

        static ParameterBinding ofLong(long value) {
            return new LongBinding(value);
        }

        static ParameterBinding ofDouble(double value) {
            return new DoubleBinding(value);
        }

        static ParameterBinding ofText(String value) {
            return new TextBinding(value);
        }

        static ParameterBinding ofBlob(byte[] value) {
            return new BlobBinding(value);
        }

        static ParameterBinding ofNull() {
            return NullBinding.INSTANCE;
        }

        record LongBinding(long value) implements ParameterBinding {
            @Override
            public MemorySegment toNativeValue(Arena arena) throws Throwable {
                return (MemorySegment) LibSql.INTEGER.invokeExact((SegmentAllocator) arena, value);
            }
        }

        record DoubleBinding(double value) implements ParameterBinding {
            @Override
            public MemorySegment toNativeValue(Arena arena) throws Throwable {
                return (MemorySegment) LibSql.REAL.invokeExact((SegmentAllocator) arena, value);
            }
        }

        record TextBinding(String value) implements ParameterBinding {
            @Override
            public MemorySegment toNativeValue(Arena arena) throws Throwable {
                MemorySegment textPtr = arena.allocateFrom(value);
                return (MemorySegment) LibSql.TEXT.invokeExact(
                        (SegmentAllocator) arena, textPtr, (long) value.length());
            }
        }

        record BlobBinding(byte[] value) implements ParameterBinding {
            @Override
            public MemorySegment toNativeValue(Arena arena) throws Throwable {
                MemorySegment blobPtr = arena.allocate(value.length);
                blobPtr.copyFrom(MemorySegment.ofArray(value));
                return (MemorySegment) LibSql.BLOB.invokeExact(
                        (SegmentAllocator) arena, blobPtr, (long) value.length);
            }
        }

        enum NullBinding implements ParameterBinding {
            INSTANCE;

            @Override
            public MemorySegment toNativeValue(Arena arena) throws Throwable {
                return (MemorySegment) LibSql.NULL_VALUE.invokeExact((SegmentAllocator) arena);
            }
        }
    }
}
