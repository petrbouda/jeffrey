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
import cafe.jeffrey.profile.heapdump.oql.compiler.ExecutionPlan.SqlPlan;
import cafe.jeffrey.profile.heapdump.view.HeapView;

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
