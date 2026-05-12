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
package cafe.jeffrey.profile.heapdump.oql.executor;

import cafe.jeffrey.profile.heapdump.model.OQLQueryResult;
import cafe.jeffrey.profile.heapdump.model.OQLResultEntry;
import cafe.jeffrey.profile.heapdump.oql.compiler.ResultShape;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Maps a {@link ResultSet} produced by the SQL executor to an
 * {@link OQLQueryResult}. Uses {@link ResultShape} hints from the compiler
 * to identify which columns carry distinguished roles (instance id, class
 * name, sizes).
 */
public final class ResultMapper {

    private static final int ROW_PREVIEW_CAP_CHARS = 500;

    private ResultMapper() {
    }

    public static OQLQueryResult map(ResultSet rs, ResultShape shape, int limit) throws SQLException {
        ResultSetMetaData md = rs.getMetaData();
        int colCount = md.getColumnCount();
        List<OQLResultEntry> entries = new ArrayList<>();
        boolean hasMore = false;
        while (rs.next()) {
            if (entries.size() >= limit) {
                hasMore = true;
                break;
            }
            entries.add(mapRow(rs, md, colCount, shape));
        }
        return OQLQueryResult.success(entries, entries.size(), hasMore, 0);
    }

    private static OQLResultEntry mapRow(ResultSet rs, ResultSetMetaData md, int colCount, ResultShape shape) throws SQLException {
        Long objectId = shape.objectIdColumn() >= 0 ? safeLong(rs, shape.objectIdColumn() + 1) : null;
        String className = shape.classNameColumn() >= 0 ? rs.getString(shape.classNameColumn() + 1) : null;
        long size = shape.shallowSizeColumn() >= 0 ? safeLong0(rs, shape.shallowSizeColumn() + 1) : 0L;
        Long retained = shape.retainedSizeColumn() >= 0 ? safeLong(rs, shape.retainedSizeColumn() + 1) : null;
        String value = renderRowValue(rs, md, colCount);
        return new OQLResultEntry(objectId, className, value, size, retained);
    }

    /**
     * Concatenates all column values into a single string suitable for display
     * in the UI's "value" column. Caps total length to keep the response
     * payload bounded.
     */
    private static String renderRowValue(ResultSet rs, ResultSetMetaData md, int colCount) throws SQLException {
        StringBuilder out = new StringBuilder();
        for (int i = 1; i <= colCount; i++) {
            if (i > 1) {
                out.append(" | ");
            }
            String label = md.getColumnLabel(i);
            Object val = rs.getObject(i);
            String rendered = val == null ? "null" : val.toString();
            out.append(label).append('=').append(rendered);
            if (out.length() >= ROW_PREVIEW_CAP_CHARS) {
                out.setLength(ROW_PREVIEW_CAP_CHARS);
                out.append("...");
                break;
            }
        }
        return out.toString();
    }

    private static Long safeLong(ResultSet rs, int column) throws SQLException {
        long v = rs.getLong(column);
        return rs.wasNull() ? null : v;
    }

    private static long safeLong0(ResultSet rs, int column) throws SQLException {
        long v = rs.getLong(column);
        return rs.wasNull() ? 0L : v;
    }
}
