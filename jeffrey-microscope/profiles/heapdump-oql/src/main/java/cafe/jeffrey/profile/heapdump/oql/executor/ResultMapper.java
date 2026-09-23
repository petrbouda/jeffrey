/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
