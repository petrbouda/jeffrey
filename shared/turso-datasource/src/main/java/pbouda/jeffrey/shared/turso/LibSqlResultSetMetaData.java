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
import java.nio.charset.StandardCharsets;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Types;

/**
 * Minimal {@link ResultSetMetaData} implementation for libsql result sets.
 */
public class LibSqlResultSetMetaData implements ResultSetMetaData {

    private final Arena arena;
    private final MemorySegment rowsStruct;
    private final int columnCount;

    LibSqlResultSetMetaData(Arena arena, MemorySegment rowsStruct, int columnCount) {
        this.arena = arena;
        this.rowsStruct = rowsStruct;
        this.columnCount = columnCount;
    }

    @Override
    public int getColumnCount() {
        return columnCount;
    }

    @Override
    public String getColumnName(int column) throws SQLException {
        return getColumnLabel(column);
    }

    @Override
    public String getColumnLabel(int column) throws SQLException {
        try {
            MemorySegment nameSlice = (MemorySegment) LibSql.ROWS_COLUMN_NAME.invokeExact(
                    (SegmentAllocator) arena, rowsStruct, column - 1);
            MemorySegment ptr = (MemorySegment) LibSql.SLICE_PTR.get(nameSlice, 0L);
            long len = (long) LibSql.SLICE_LEN.get(nameSlice, 0L);
            if (ptr.address() == 0 || len == 0) {
                return "column" + column;
            }
            byte[] bytes = ptr.reinterpret(len).toArray(java.lang.foreign.ValueLayout.JAVA_BYTE);
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (Throwable t) {
            throw new SQLException("Failed to get column name for index " + column, t);
        }
    }

    @Override
    public int getColumnType(int column) {
        // libsql uses dynamic typing; default to VARCHAR
        return Types.VARCHAR;
    }

    @Override
    public String getColumnTypeName(int column) {
        return "TEXT";
    }

    @Override
    public String getColumnClassName(int column) {
        return String.class.getName();
    }

    @Override
    public boolean isAutoIncrement(int column) {
        return false;
    }

    @Override
    public boolean isCaseSensitive(int column) {
        return true;
    }

    @Override
    public boolean isSearchable(int column) {
        return true;
    }

    @Override
    public boolean isCurrency(int column) {
        return false;
    }

    @Override
    public int isNullable(int column) {
        return ResultSetMetaData.columnNullableUnknown;
    }

    @Override
    public boolean isSigned(int column) {
        return true;
    }

    @Override
    public int getColumnDisplaySize(int column) {
        return 256;
    }

    @Override
    public String getSchemaName(int column) {
        return "";
    }

    @Override
    public int getPrecision(int column) {
        return 0;
    }

    @Override
    public int getScale(int column) {
        return 0;
    }

    @Override
    public String getTableName(int column) {
        return "";
    }

    @Override
    public String getCatalogName(int column) {
        return "";
    }

    @Override
    public boolean isReadOnly(int column) {
        return true;
    }

    @Override
    public boolean isWritable(int column) {
        return false;
    }

    @Override
    public boolean isDefinitelyWritable(int column) {
        return false;
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
