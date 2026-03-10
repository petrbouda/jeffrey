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
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SegmentAllocator;
import java.math.BigDecimal;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;

/**
 * A {@link ResultSet} implementation backed by native libsql rows.
 *
 * <p>Forward-only, read-only result set. Column names are resolved lazily
 * and cached for the lifetime of the result set.
 */
public class LibSqlResultSet implements ResultSet {

    private final Arena arena;
    private final MemorySegment rowsStruct;
    private final int columnCount;
    private MemorySegment currentRow;
    private boolean closed;
    private boolean wasNull;
    private boolean beforeFirst = true;
    private Map<String, Integer> columnNameIndex;

    LibSqlResultSet(Arena arena, MemorySegment rowsStruct) {
        this.arena = arena;
        this.rowsStruct = rowsStruct;

        try {
            this.columnCount = (int) LibSql.ROWS_COLUMN_COUNT.invokeExact(rowsStruct);
        } catch (Throwable t) {
            throw new LibSqlException("Failed to get column count", t);
        }
    }

    @Override
    public boolean next() throws SQLException {
        checkClosed();
        beforeFirst = false;

        // Clean up previous row
        if (currentRow != null) {
            try {
                LibSql.ROW_DEINIT.invokeExact(currentRow);
            } catch (Throwable t) {
                // Ignore cleanup errors
            }
            currentRow = null;
        }

        try {
            MemorySegment row = (MemorySegment) LibSql.ROWS_NEXT.invokeExact(
                    (SegmentAllocator) arena, rowsStruct);

            MemorySegment err = LibSql.getErr(row);
            LibSql.checkError(err, "Failed to fetch next row");

            boolean empty = (boolean) LibSql.ROW_EMPTY.invokeExact(row);
            if (empty) {
                currentRow = null;
                return false;
            }

            currentRow = row;

            // Build column name index on the first row
            if (columnNameIndex == null) {
                buildColumnNameIndex();
            }

            return true;
        } catch (LibSqlException e) {
            throw e.toSQLException();
        } catch (Throwable t) {
            throw new SQLException("Failed to fetch next row", t);
        }
    }

    private void buildColumnNameIndex() {
        columnNameIndex = new HashMap<>();
        for (int i = 0; i < columnCount; i++) {
            try {
                MemorySegment nameSlice = (MemorySegment) LibSql.ROWS_COLUMN_NAME.invokeExact(
                        (SegmentAllocator) arena, rowsStruct, i);
                String name = extractSliceString(nameSlice);
                if (name != null) {
                    columnNameIndex.put(name.toLowerCase(), i + 1); // 1-based index
                }
            } catch (Throwable t) {
                // Skip column name resolution on error
            }
        }
    }

    private ResultValue getValueAt(int columnIndex) throws SQLException {
        if (currentRow == null) {
            throw new SQLException("No current row");
        }
        try {
            MemorySegment resultValue = (MemorySegment) LibSql.ROW_VALUE.invokeExact(
                    (SegmentAllocator) arena, currentRow, columnIndex - 1);

            MemorySegment err = (MemorySegment) LibSql.RESULT_VALUE_ERR.get(resultValue, 0L);
            LibSql.checkError(err, "Failed to get value at column " + columnIndex);

            // Extract the value_t portion
            MemorySegment valueStruct = resultValue.asSlice(
                    LibSql.RESULT_VALUE_T.byteOffset(MemoryLayout.PathElement.groupElement("ok")),
                    LibSql.VALUE_T.byteSize());

            int type = (int) LibSql.VALUE_TYPE.get(valueStruct, 0L);

            wasNull = (type == LibSql.TYPE_NULL);

            return new ResultValue(type, valueStruct);
        } catch (LibSqlException e) {
            throw e.toSQLException();
        } catch (Throwable t) {
            throw new SQLException("Failed to get value at column " + columnIndex, t);
        }
    }

    private String extractSliceString(MemorySegment sliceStruct) {
        MemorySegment ptr = (MemorySegment) LibSql.SLICE_PTR.get(sliceStruct, 0L);
        long len = (long) LibSql.SLICE_LEN.get(sliceStruct, 0L);
        if (ptr.address() == 0 || len == 0) {
            return null;
        }
        byte[] bytes = ptr.reinterpret(len).toArray(java.lang.foreign.ValueLayout.JAVA_BYTE);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    // ──────────────────────────────────────────────────────────────────────
    // Typed getters by column index (1-based)
    // ──────────────────────────────────────────────────────────────────────

    @Override
    public String getString(int columnIndex) throws SQLException {
        ResultValue rv = getValueAt(columnIndex);
        if (wasNull) return null;
        return rv.asString();
    }

    @Override
    public boolean getBoolean(int columnIndex) throws SQLException {
        ResultValue rv = getValueAt(columnIndex);
        if (wasNull) return false;
        return rv.asLong() != 0;
    }

    @Override
    public byte getByte(int columnIndex) throws SQLException {
        ResultValue rv = getValueAt(columnIndex);
        if (wasNull) return 0;
        return (byte) rv.asLong();
    }

    @Override
    public short getShort(int columnIndex) throws SQLException {
        ResultValue rv = getValueAt(columnIndex);
        if (wasNull) return 0;
        return (short) rv.asLong();
    }

    @Override
    public int getInt(int columnIndex) throws SQLException {
        ResultValue rv = getValueAt(columnIndex);
        if (wasNull) return 0;
        return (int) rv.asLong();
    }

    @Override
    public long getLong(int columnIndex) throws SQLException {
        ResultValue rv = getValueAt(columnIndex);
        if (wasNull) return 0;
        return rv.asLong();
    }

    @Override
    public float getFloat(int columnIndex) throws SQLException {
        ResultValue rv = getValueAt(columnIndex);
        if (wasNull) return 0;
        return (float) rv.asDouble();
    }

    @Override
    public double getDouble(int columnIndex) throws SQLException {
        ResultValue rv = getValueAt(columnIndex);
        if (wasNull) return 0;
        return rv.asDouble();
    }

    @Override
    @SuppressWarnings("deprecation")
    public BigDecimal getBigDecimal(int columnIndex, int scale) throws SQLException {
        String s = getString(columnIndex);
        if (s == null) return null;
        return new BigDecimal(s).setScale(scale, java.math.RoundingMode.HALF_UP);
    }

    @Override
    public byte[] getBytes(int columnIndex) throws SQLException {
        ResultValue rv = getValueAt(columnIndex);
        if (wasNull) return null;
        return rv.asBlob();
    }

    @Override
    public Date getDate(int columnIndex) throws SQLException {
        ResultValue rv = getValueAt(columnIndex);
        if (wasNull) return null;
        return new Date(rv.asLong());
    }

    @Override
    public Time getTime(int columnIndex) throws SQLException {
        ResultValue rv = getValueAt(columnIndex);
        if (wasNull) return null;
        return new Time(rv.asLong());
    }

    @Override
    public Timestamp getTimestamp(int columnIndex) throws SQLException {
        ResultValue rv = getValueAt(columnIndex);
        if (wasNull) return null;
        // Stored as epoch microseconds
        long micros = rv.asLong();
        Timestamp ts = new Timestamp(micros / 1000);
        ts.setNanos((int) (micros % 1000) * 1000);
        return ts;
    }

    @Override
    public Object getObject(int columnIndex) throws SQLException {
        ResultValue rv = getValueAt(columnIndex);
        if (wasNull) return null;
        return switch (rv.type) {
            case LibSql.TYPE_INTEGER -> rv.asLong();
            case LibSql.TYPE_REAL -> rv.asDouble();
            case LibSql.TYPE_TEXT -> rv.asString();
            case LibSql.TYPE_BLOB -> rv.asBlob();
            default -> null;
        };
    }

    // ──────────────────────────────────────────────────────────────────────
    // Typed getters by column name
    // ──────────────────────────────────────────────────────────────────────

    @Override
    public String getString(String columnLabel) throws SQLException {
        return getString(findColumn(columnLabel));
    }

    @Override
    public boolean getBoolean(String columnLabel) throws SQLException {
        return getBoolean(findColumn(columnLabel));
    }

    @Override
    public byte getByte(String columnLabel) throws SQLException {
        return getByte(findColumn(columnLabel));
    }

    @Override
    public short getShort(String columnLabel) throws SQLException {
        return getShort(findColumn(columnLabel));
    }

    @Override
    public int getInt(String columnLabel) throws SQLException {
        return getInt(findColumn(columnLabel));
    }

    @Override
    public long getLong(String columnLabel) throws SQLException {
        return getLong(findColumn(columnLabel));
    }

    @Override
    public float getFloat(String columnLabel) throws SQLException {
        return getFloat(findColumn(columnLabel));
    }

    @Override
    public double getDouble(String columnLabel) throws SQLException {
        return getDouble(findColumn(columnLabel));
    }

    @Override
    @SuppressWarnings("deprecation")
    public BigDecimal getBigDecimal(String columnLabel, int scale) throws SQLException {
        return getBigDecimal(findColumn(columnLabel), scale);
    }

    @Override
    public byte[] getBytes(String columnLabel) throws SQLException {
        return getBytes(findColumn(columnLabel));
    }

    @Override
    public Date getDate(String columnLabel) throws SQLException {
        return getDate(findColumn(columnLabel));
    }

    @Override
    public Time getTime(String columnLabel) throws SQLException {
        return getTime(findColumn(columnLabel));
    }

    @Override
    public Timestamp getTimestamp(String columnLabel) throws SQLException {
        return getTimestamp(findColumn(columnLabel));
    }

    @Override
    public Object getObject(String columnLabel) throws SQLException {
        return getObject(findColumn(columnLabel));
    }

    @Override
    public BigDecimal getBigDecimal(int columnIndex) throws SQLException {
        String s = getString(columnIndex);
        if (s == null) return null;
        return new BigDecimal(s);
    }

    @Override
    public BigDecimal getBigDecimal(String columnLabel) throws SQLException {
        return getBigDecimal(findColumn(columnLabel));
    }

    // ──────────────────────────────────────────────────────────────────────
    // Column resolution
    // ──────────────────────────────────────────────────────────────────────

    @Override
    public int findColumn(String columnLabel) throws SQLException {
        if (columnNameIndex == null) {
            // If we haven't fetched any rows yet, build the index from rows metadata
            buildColumnNameIndex();
        }
        Integer index = columnNameIndex.get(columnLabel.toLowerCase());
        if (index == null) {
            throw new SQLException("Column not found: " + columnLabel);
        }
        return index;
    }

    // ──────────────────────────────────────────────────────────────────────
    // Metadata and state
    // ──────────────────────────────────────────────────────────────────────

    @Override
    public ResultSetMetaData getMetaData() throws SQLException {
        checkClosed();
        return new LibSqlResultSetMetaData(arena, rowsStruct, columnCount);
    }

    @Override
    public boolean wasNull() {
        return wasNull;
    }

    @Override
    public boolean isBeforeFirst() {
        return beforeFirst;
    }

    @Override
    public boolean isAfterLast() {
        return !beforeFirst && currentRow == null;
    }

    @Override
    public boolean isFirst() {
        return false; // Not tracked
    }

    @Override
    public boolean isLast() {
        return false; // Not tracked
    }

    @Override
    public void beforeFirst() throws SQLException {
        throw new SQLFeatureNotSupportedException("Forward-only result set");
    }

    @Override
    public void afterLast() throws SQLException {
        throw new SQLFeatureNotSupportedException("Forward-only result set");
    }

    @Override
    public boolean first() throws SQLException {
        throw new SQLFeatureNotSupportedException("Forward-only result set");
    }

    @Override
    public boolean last() throws SQLException {
        throw new SQLFeatureNotSupportedException("Forward-only result set");
    }

    @Override
    public int getRow() {
        return 0;
    }

    @Override
    public boolean absolute(int row) throws SQLException {
        throw new SQLFeatureNotSupportedException("Forward-only result set");
    }

    @Override
    public boolean relative(int rows) throws SQLException {
        throw new SQLFeatureNotSupportedException("Forward-only result set");
    }

    @Override
    public boolean previous() throws SQLException {
        throw new SQLFeatureNotSupportedException("Forward-only result set");
    }

    @Override
    public int getType() {
        return ResultSet.TYPE_FORWARD_ONLY;
    }

    @Override
    public int getConcurrency() {
        return ResultSet.CONCUR_READ_ONLY;
    }

    @Override
    public int getHoldability() {
        return ResultSet.HOLD_CURSORS_OVER_COMMIT;
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
    public boolean isClosed() {
        return closed;
    }

    @Override
    public void close() {
        if (!closed) {
            closed = true;
            if (currentRow != null) {
                try {
                    LibSql.ROW_DEINIT.invokeExact(currentRow);
                } catch (Throwable t) {
                    // Ignore
                }
                currentRow = null;
            }
            try {
                LibSql.ROWS_DEINIT.invokeExact(rowsStruct);
            } catch (Throwable t) {
                // Ignore
            }
        }
    }

    @Override
    public String getCursorName() throws SQLException {
        throw new SQLFeatureNotSupportedException("Cursors not supported");
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
    public Statement getStatement() {
        return null;
    }

    @Override
    public InputStream getAsciiStream(int columnIndex) throws SQLException {
        throw new SQLFeatureNotSupportedException("Streams not supported");
    }

    @Override
    @SuppressWarnings("deprecation")
    public InputStream getUnicodeStream(int columnIndex) throws SQLException {
        throw new SQLFeatureNotSupportedException("Streams not supported");
    }

    @Override
    public InputStream getBinaryStream(int columnIndex) throws SQLException {
        throw new SQLFeatureNotSupportedException("Streams not supported");
    }

    @Override
    public InputStream getAsciiStream(String columnLabel) throws SQLException {
        throw new SQLFeatureNotSupportedException("Streams not supported");
    }

    @Override
    @SuppressWarnings("deprecation")
    public InputStream getUnicodeStream(String columnLabel) throws SQLException {
        throw new SQLFeatureNotSupportedException("Streams not supported");
    }

    @Override
    public InputStream getBinaryStream(String columnLabel) throws SQLException {
        throw new SQLFeatureNotSupportedException("Streams not supported");
    }

    @Override
    public Reader getCharacterStream(int columnIndex) throws SQLException {
        throw new SQLFeatureNotSupportedException("Streams not supported");
    }

    @Override
    public Reader getCharacterStream(String columnLabel) throws SQLException {
        throw new SQLFeatureNotSupportedException("Streams not supported");
    }

    // ──────────────────────────────────────────────────────────────────────
    // Calendar-aware getters
    // ──────────────────────────────────────────────────────────────────────

    @Override
    public Date getDate(int columnIndex, Calendar cal) throws SQLException {
        return getDate(columnIndex);
    }

    @Override
    public Date getDate(String columnLabel, Calendar cal) throws SQLException {
        return getDate(columnLabel);
    }

    @Override
    public Time getTime(int columnIndex, Calendar cal) throws SQLException {
        return getTime(columnIndex);
    }

    @Override
    public Time getTime(String columnLabel, Calendar cal) throws SQLException {
        return getTime(columnLabel);
    }

    @Override
    public Timestamp getTimestamp(int columnIndex, Calendar cal) throws SQLException {
        return getTimestamp(columnIndex);
    }

    @Override
    public Timestamp getTimestamp(String columnLabel, Calendar cal) throws SQLException {
        return getTimestamp(columnLabel);
    }

    // ──────────────────────────────────────────────────────────────────────
    // Object getters with type mapping
    // ──────────────────────────────────────────────────────────────────────

    @Override
    public Object getObject(int columnIndex, Map<String, Class<?>> map) throws SQLException {
        return getObject(columnIndex);
    }

    @Override
    public Object getObject(String columnLabel, Map<String, Class<?>> map) throws SQLException {
        return getObject(columnLabel);
    }

    @Override
    public <T> T getObject(int columnIndex, Class<T> type) throws SQLException {
        Object obj = getObject(columnIndex);
        if (obj == null) return null;
        if (type.isInstance(obj)) return type.cast(obj);
        // Basic type coercions
        if (type == String.class) return type.cast(obj.toString());
        if (type == Long.class && obj instanceof Number n) return type.cast(n.longValue());
        if (type == Integer.class && obj instanceof Number n) return type.cast(n.intValue());
        if (type == Double.class && obj instanceof Number n) return type.cast(n.doubleValue());
        throw new SQLException("Cannot convert " + obj.getClass().getName() + " to " + type.getName());
    }

    @Override
    public <T> T getObject(String columnLabel, Class<T> type) throws SQLException {
        return getObject(findColumn(columnLabel), type);
    }

    // ──────────────────────────────────────────────────────────────────────
    // Unsupported features
    // ──────────────────────────────────────────────────────────────────────

    @Override public Ref getRef(int columnIndex) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }
    @Override public Blob getBlob(int columnIndex) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }
    @Override public Clob getClob(int columnIndex) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }
    @Override public Array getArray(int columnIndex) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }
    @Override public Ref getRef(String columnLabel) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }
    @Override public Blob getBlob(String columnLabel) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }
    @Override public Clob getClob(String columnLabel) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }
    @Override public Array getArray(String columnLabel) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }
    @Override public URL getURL(int columnIndex) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }
    @Override public URL getURL(String columnLabel) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }
    @Override public RowId getRowId(int columnIndex) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }
    @Override public RowId getRowId(String columnLabel) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }
    @Override public NClob getNClob(int columnIndex) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }
    @Override public NClob getNClob(String columnLabel) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }
    @Override public SQLXML getSQLXML(int columnIndex) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }
    @Override public SQLXML getSQLXML(String columnLabel) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }
    @Override public String getNString(int columnIndex) throws SQLException {
        return getString(columnIndex);
    }
    @Override public String getNString(String columnLabel) throws SQLException {
        return getString(columnLabel);
    }
    @Override public Reader getNCharacterStream(int columnIndex) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }
    @Override public Reader getNCharacterStream(String columnLabel) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    // ──────────────────────────────────────────────────────────────────────
    // Update methods (read-only result set)
    // ──────────────────────────────────────────────────────────────────────

    @Override public boolean rowUpdated() { return false; }
    @Override public boolean rowInserted() { return false; }
    @Override public boolean rowDeleted() { return false; }
    @Override public void updateNull(int columnIndex) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateBoolean(int columnIndex, boolean x) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateByte(int columnIndex, byte x) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateShort(int columnIndex, short x) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateInt(int columnIndex, int x) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateLong(int columnIndex, long x) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateFloat(int columnIndex, float x) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateDouble(int columnIndex, double x) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateBigDecimal(int columnIndex, BigDecimal x) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateString(int columnIndex, String x) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateBytes(int columnIndex, byte[] x) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateDate(int columnIndex, Date x) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateTime(int columnIndex, Time x) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateTimestamp(int columnIndex, Timestamp x) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateAsciiStream(int columnIndex, InputStream x, int length) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateBinaryStream(int columnIndex, InputStream x, int length) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateCharacterStream(int columnIndex, Reader x, int length) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateObject(int columnIndex, Object x, int scaleOrLength) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateObject(int columnIndex, Object x) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateNull(String columnLabel) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateBoolean(String columnLabel, boolean x) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateByte(String columnLabel, byte x) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateShort(String columnLabel, short x) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateInt(String columnLabel, int x) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateLong(String columnLabel, long x) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateFloat(String columnLabel, float x) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateDouble(String columnLabel, double x) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateBigDecimal(String columnLabel, BigDecimal x) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateString(String columnLabel, String x) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateBytes(String columnLabel, byte[] x) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateDate(String columnLabel, Date x) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateTime(String columnLabel, Time x) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateTimestamp(String columnLabel, Timestamp x) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateAsciiStream(String columnLabel, InputStream x, int length) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateBinaryStream(String columnLabel, InputStream x, int length) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateCharacterStream(String columnLabel, Reader reader, int length) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateObject(String columnLabel, Object x, int scaleOrLength) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateObject(String columnLabel, Object x) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void insertRow() throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateRow() throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void deleteRow() throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void refreshRow() throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void cancelRowUpdates() throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void moveToInsertRow() throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void moveToCurrentRow() throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateRef(int columnIndex, Ref x) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateRef(String columnLabel, Ref x) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateBlob(int columnIndex, Blob x) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateBlob(String columnLabel, Blob x) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateClob(int columnIndex, Clob x) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateClob(String columnLabel, Clob x) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateArray(int columnIndex, Array x) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateArray(String columnLabel, Array x) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateRowId(int columnIndex, RowId x) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateRowId(String columnLabel, RowId x) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateNString(int columnIndex, String nString) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateNString(String columnLabel, String nString) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateNClob(int columnIndex, NClob nClob) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateNClob(String columnLabel, NClob nClob) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateSQLXML(int columnIndex, SQLXML xmlObject) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateSQLXML(String columnLabel, SQLXML xmlObject) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateNCharacterStream(int columnIndex, Reader x, long length) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateNCharacterStream(String columnLabel, Reader reader, long length) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateAsciiStream(int columnIndex, InputStream x, long length) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateBinaryStream(int columnIndex, InputStream x, long length) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateCharacterStream(int columnIndex, Reader x, long length) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateAsciiStream(String columnLabel, InputStream x, long length) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateBinaryStream(String columnLabel, InputStream x, long length) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateCharacterStream(String columnLabel, Reader reader, long length) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateBlob(int columnIndex, InputStream inputStream, long length) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateBlob(String columnLabel, InputStream inputStream, long length) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateClob(int columnIndex, Reader reader, long length) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateClob(String columnLabel, Reader reader, long length) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateNClob(int columnIndex, Reader reader, long length) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateNClob(String columnLabel, Reader reader, long length) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateNCharacterStream(int columnIndex, Reader x) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateNCharacterStream(String columnLabel, Reader reader) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateAsciiStream(int columnIndex, InputStream x) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateBinaryStream(int columnIndex, InputStream x) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateCharacterStream(int columnIndex, Reader x) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateAsciiStream(String columnLabel, InputStream x) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateBinaryStream(String columnLabel, InputStream x) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateCharacterStream(String columnLabel, Reader reader) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateBlob(int columnIndex, InputStream inputStream) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateBlob(String columnLabel, InputStream inputStream) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateClob(int columnIndex, Reader reader) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateClob(String columnLabel, Reader reader) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateNClob(int columnIndex, Reader reader) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateNClob(String columnLabel, Reader reader) throws SQLException { throw new SQLFeatureNotSupportedException(); }

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
            throw new SQLException("ResultSet is closed");
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    // Internal value representation
    // ──────────────────────────────────────────────────────────────────────

    private record ResultValue(int type, MemorySegment valueStruct) {

        long asLong() {
            if (type == LibSql.TYPE_INTEGER) {
                // Access the integer field of the union at the start of value_t
                return valueStruct.get(java.lang.foreign.ValueLayout.JAVA_LONG, 0);
            }
            if (type == LibSql.TYPE_REAL) {
                return (long) asDouble();
            }
            if (type == LibSql.TYPE_TEXT) {
                return Long.parseLong(asString());
            }
            return 0;
        }

        double asDouble() {
            if (type == LibSql.TYPE_REAL) {
                return valueStruct.get(java.lang.foreign.ValueLayout.JAVA_DOUBLE, 0);
            }
            if (type == LibSql.TYPE_INTEGER) {
                return asLong();
            }
            if (type == LibSql.TYPE_TEXT) {
                return Double.parseDouble(asString());
            }
            return 0;
        }

        String asString() {
            if (type == LibSql.TYPE_TEXT) {
                MemorySegment ptr = valueStruct.get(java.lang.foreign.ValueLayout.ADDRESS, 0);
                long len = valueStruct.get(java.lang.foreign.ValueLayout.JAVA_LONG, 8);
                if (ptr.address() == 0 || len == 0) return "";
                byte[] bytes = ptr.reinterpret(len).toArray(java.lang.foreign.ValueLayout.JAVA_BYTE);
                return new String(bytes, StandardCharsets.UTF_8);
            }
            if (type == LibSql.TYPE_INTEGER) {
                return Long.toString(asLong());
            }
            if (type == LibSql.TYPE_REAL) {
                return Double.toString(asDouble());
            }
            return null;
        }

        byte[] asBlob() {
            if (type == LibSql.TYPE_BLOB) {
                MemorySegment ptr = valueStruct.get(java.lang.foreign.ValueLayout.ADDRESS, 0);
                long len = valueStruct.get(java.lang.foreign.ValueLayout.JAVA_LONG, 8);
                if (ptr.address() == 0 || len == 0) return new byte[0];
                return ptr.reinterpret(len).toArray(java.lang.foreign.ValueLayout.JAVA_BYTE);
            }
            return null;
        }
    }
}
