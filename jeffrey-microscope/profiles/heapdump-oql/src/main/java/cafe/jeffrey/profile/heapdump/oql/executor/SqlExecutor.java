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
import cafe.jeffrey.profile.heapdump.oql.compiler.ExecutionPlan.SqlPlan;
import cafe.jeffrey.profile.heapdump.parser.HeapView;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/**
 * Executes a {@link SqlPlan} against the heap-dump-index DuckDB. Binds
 * parameters in order, applies a query timeout, and hands the result set off
 * to {@link ResultMapper}.
 */
public final class SqlExecutor {

    private static final int QUERY_TIMEOUT_SECONDS = 30;

    private SqlExecutor() {
    }

    public static OQLQueryResult execute(SqlPlan plan, HeapView view, int limit) throws SQLException {
        Connection conn = view.databaseClient().connection();
        String sql = ensureLimit(plan.sql(), limit);
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            bindParams(stmt, plan.params());
            stmt.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
            try (ResultSet rs = stmt.executeQuery()) {
                return ResultMapper.map(rs, plan.resultShape(), limit);
            }
        }
    }

    private static String ensureLimit(String sql, int limit) {
        // The compiler emits LIMIT only when the user wrote one; otherwise we
        // bound the result set defensively to keep the response payload finite.
        String lower = sql.toLowerCase();
        // Look for a standalone LIMIT keyword — "limit" inside a string literal
        // would false-positive but those are rare in compiler-emitted SQL.
        if (lower.contains(" limit ")) {
            return sql;
        }
        return sql + " LIMIT " + limit;
    }

    private static void bindParams(PreparedStatement stmt, List<Object> params) throws SQLException {
        for (int i = 0; i < params.size(); i++) {
            Object v = params.get(i);
            int idx = i + 1;
            if (v == null) {
                stmt.setObject(idx, null);
            } else if (v instanceof String s) {
                stmt.setString(idx, s);
            } else if (v instanceof Long l) {
                stmt.setLong(idx, l);
            } else if (v instanceof Integer iv) {
                stmt.setInt(idx, iv);
            } else if (v instanceof Double d) {
                stmt.setDouble(idx, d);
            } else if (v instanceof Float f) {
                stmt.setFloat(idx, f);
            } else if (v instanceof Boolean b) {
                stmt.setBoolean(idx, b);
            } else {
                stmt.setObject(idx, v);
            }
        }
    }
}
