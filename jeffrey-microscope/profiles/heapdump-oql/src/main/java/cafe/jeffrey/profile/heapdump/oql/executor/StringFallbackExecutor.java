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
import cafe.jeffrey.profile.heapdump.oql.ast.OqlStatement.OqlQuery;
import cafe.jeffrey.profile.heapdump.oql.compiler.ExecutionPlan.StringFallbackPlan;
import cafe.jeffrey.profile.heapdump.parser.HeapView;
import cafe.jeffrey.profile.heapdump.parser.InstanceRow;
import cafe.jeffrey.profile.heapdump.parser.JavaClassRow;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Executes a {@link StringFallbackPlan}: runs the primary SQL pushdown plan
 * (catches Strings whose content fits the indexer's cap), then iterates the
 * "uncovered tail" — Strings whose decoded length exceeded the cap and have
 * {@code string_content.content IS NULL} — evaluating the WHERE predicate in
 * Java per instance.
 *
 * <p>The result merges the two streams, dedup'd by {@code instance_id} and
 * trimmed to the caller's {@code limit}. The fallback path is bounded by
 * design: it only visits Strings actually beyond the cap, which is a small
 * fraction of total Strings on typical heaps.
 */
public final class StringFallbackExecutor {

    private static final String UNCAPPED_STRINGS_SQL =
            "SELECT sc.instance_id FROM string_content sc WHERE sc.content IS NULL";

    private StringFallbackExecutor() {
    }

    public static OQLQueryResult execute(StringFallbackPlan plan, HeapView view, int limit) throws SQLException {
        // 1. Primary SQL pushdown — covers in-cap Strings.
        OQLQueryResult primary = SqlExecutor.execute(plan.primary(), view, limit);
        if (primary.errorMessage() != null) {
            return primary;
        }

        // 2. Iterate uncovered Strings (content NULL), evaluate WHERE in Java.
        OqlQuery query = plan.query();
        List<OQLResultEntry> fallback = scanUncovered(query, view, limit);

        // 3. Merge with primary, dedupe by instance_id (LinkedHashMap preserves
        //    primary order; fallback rows append after).
        Map<Long, OQLResultEntry> merged = new LinkedHashMap<>();
        for (OQLResultEntry e : primary.results()) {
            Long id = e.objectId();
            merged.put(id != null ? id : System.identityHashCode(e), e);
        }
        for (OQLResultEntry e : fallback) {
            Long id = e.objectId();
            merged.putIfAbsent(id != null ? id : System.identityHashCode(e), e);
            if (merged.size() >= limit) {
                break;
            }
        }
        List<OQLResultEntry> out = new ArrayList<>(merged.values());
        boolean hasMore = primary.hasMore() || out.size() > limit;
        if (out.size() > limit) {
            out = out.subList(0, limit);
        }
        return OQLQueryResult.success(out, out.size(), hasMore, 0);
    }

    private static List<OQLResultEntry> scanUncovered(OqlQuery query, HeapView view, int limit) throws SQLException {
        Connection conn = view.connection();
        List<OQLResultEntry> entries = new ArrayList<>();
        PathExprEvaluator pathEval = new PathExprEvaluator(view);
        try (PreparedStatement stmt = conn.prepareStatement(UNCAPPED_STRINGS_SQL);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                if (entries.size() >= limit) {
                    break;
                }
                long instanceId = rs.getLong(1);
                InstanceRow inst = view.findInstanceById(instanceId).orElse(null);
                if (inst == null || inst.classId() == null) {
                    continue;
                }
                JavaClassRow clazz = view.findClassById(inst.classId()).orElse(null);
                if (clazz == null) {
                    continue;
                }
                Row row = new Row(view, inst, clazz, query.from().alias());
                ExprEvaluator eval = new ExprEvaluator(row, pathEval);
                if (query.whereExpr() != null && !eval.evalPredicate(query.whereExpr())) {
                    continue;
                }
                JavaExecutor.appendRowEntries(eval, query, row, entries);
            }
        }
        return entries;
    }
}
