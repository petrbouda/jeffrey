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
package cafe.jeffrey.profile.heapdump.oql.compiler;

import cafe.jeffrey.profile.heapdump.oql.ast.FromClause;
import cafe.jeffrey.profile.heapdump.oql.ast.ObjectSource;
import cafe.jeffrey.profile.heapdump.oql.ast.OqlStatement;
import cafe.jeffrey.profile.heapdump.oql.ast.OqlStatement.OqlQuery;
import cafe.jeffrey.profile.heapdump.oql.ast.OqlStatement.UnionQuery;
import cafe.jeffrey.profile.heapdump.oql.ast.SelectClause;
import cafe.jeffrey.profile.heapdump.oql.compiler.ExecutionPlan.HybridPlan;
import cafe.jeffrey.profile.heapdump.oql.compiler.ExecutionPlan.JavaPlan;
import cafe.jeffrey.profile.heapdump.oql.compiler.ExecutionPlan.PrePass.ClassHierarchyExpansion;
import cafe.jeffrey.profile.heapdump.oql.compiler.ExecutionPlan.RetainedSetPlan;
import cafe.jeffrey.profile.heapdump.oql.compiler.ExecutionPlan.SqlPlan;

/**
 * Classifies a parsed {@link OqlStatement} into a Plan A / Plan B / Plan C
 * {@link ExecutionPlan}.
 *
 * <p>Classification today is straightforward: try emitting Plan A; on
 * {@link SqlEmitter.SqlEmissionException} the query falls into Plan C (a
 * {@link JavaPlan} placeholder until Phase 3 lands the Java executor).
 * INSTANCEOF / IMPLEMENTS on the outermost FROM upgrades the result to
 * Plan B with a class-hierarchy pre-pass.
 */
public final class OqlCompiler {

    private static final String CLASS_IDS_PLACEHOLDER = "__CLASS_IDS__";

    public ExecutionPlan compile(OqlStatement stmt) {
        return compile(stmt, OqlCompileOptions.DEFAULTS);
    }

    public ExecutionPlan compile(OqlStatement stmt, OqlCompileOptions options) {
        if (options == null) {
            options = OqlCompileOptions.DEFAULTS;
        }
        ExecutionPlan plan = compileCore(stmt);
        if (options.scanLargeStrings()
                && plan instanceof SqlPlan sqlPlan
                && involvesStringContentJoin(sqlPlan)
                && stmt instanceof OqlStatement.OqlQuery query) {
            return new ExecutionPlan.StringFallbackPlan(sqlPlan, query);
        }
        return plan;
    }

    private static boolean involvesStringContentJoin(SqlPlan plan) {
        // Cheap signal: the JOIN clause is emitted verbatim by the SqlEmitter
        // when pushdown happens. Checking the SQL text avoids threading a new
        // boolean through every ExecutionPlan record.
        return plan.sql().contains("string_content");
    }

    private ExecutionPlan compileCore(OqlStatement stmt) {
        // UNION over Plan C queries needs more work; reject for now.
        if (stmt instanceof OqlStatement.UnionQuery u && requiresJavaPlan(u)) {
            return new JavaPlan(null, null, false,
                    "UNION combined with Plan C expressions is not yet supported",
                    ResultShape.empty());
        }

        // AS RETAINED SET — compile the inner query normally, then wrap.
        if (containsAsRetainedSet(stmt)) {
            OqlStatement stripped = stripRetainedSetModifier(stmt);
            ExecutionPlan inner = compile(stripped);
            if (inner instanceof JavaPlan jp && jp.reason() != null) {
                // Propagate the unsupported-inner reason rather than wrapping silently.
                return jp;
            }
            return new RetainedSetPlan(inner);
        }

        ClassHierarchyExpansion expansion = expansionFor(stmt);
        String placeholder = expansion != null ? CLASS_IDS_PLACEHOLDER : null;

        try {
            SqlEmitter.EmissionResult result = SqlEmitter.emit(stmt, placeholder);
            SqlPlan sqlPlan = new SqlPlan(
                    result.sql(), result.params(), result.needsRetainedTree(), result.shape());
            if (expansion == null) {
                return sqlPlan;
            }
            return new HybridPlan(expansion, sqlPlan);
        } catch (SqlEmitter.SqlEmissionException e) {
            // Plan A/B couldn't express this query — fall back to Java row-by-row.
            return javaPlanFor(stmt, expansion, null);
        }
    }

    private static OqlStatement stripRetainedSetModifier(OqlStatement stmt) {
        return switch (stmt) {
            case OqlStatement.OqlQuery q -> stripQuery(q);
            case OqlStatement.UnionQuery u -> new OqlStatement.UnionQuery(
                    u.branches().stream().map(OqlCompiler::stripQuery).toList());
        };
    }

    private static OqlStatement.OqlQuery stripQuery(OqlStatement.OqlQuery q) {
        cafe.jeffrey.profile.heapdump.oql.ast.SelectClause select = q.select();
        if (select.modifier() != cafe.jeffrey.profile.heapdump.oql.ast.SelectClause.SelectModifier.AS_RETAINED_SET) {
            return q;
        }
        cafe.jeffrey.profile.heapdump.oql.ast.SelectClause stripped =
                new cafe.jeffrey.profile.heapdump.oql.ast.SelectClause(
                        cafe.jeffrey.profile.heapdump.oql.ast.SelectClause.SelectModifier.NONE,
                        select.projections());
        return new OqlStatement.OqlQuery(stripped, q.from(), q.whereExpr(),
                q.groupBy(), q.having(), q.orderBy(), q.limit());
    }

    private static JavaPlan javaPlanFor(OqlStatement stmt, ClassHierarchyExpansion expansion, String reason) {
        OqlStatement.OqlQuery query = firstQuery(stmt);
        // Aggregate detection — Plan C doesn't currently support aggregates / GROUP BY.
        if (query != null && hasAggregateOrGroup(query)) {
            return new JavaPlan(null, null, false,
                    "Aggregates and GROUP BY are not yet supported on Plan C queries",
                    ResultShape.empty());
        }
        boolean needsDom = query != null && referencesRetainedSize(query);
        return new JavaPlan(query, expansion, needsDom, reason, ResultShape.empty());
    }

    private static boolean requiresJavaPlan(OqlStatement.UnionQuery u) {
        return u.branches().stream().anyMatch(OqlCompiler::cannotEmitAsSql);
    }

    private static boolean cannotEmitAsSql(OqlStatement.OqlQuery q) {
        try {
            SqlEmitter.emit(q, null);
            return false;
        } catch (SqlEmitter.SqlEmissionException e) {
            return true;
        }
    }

    private static boolean hasAggregateOrGroup(OqlStatement.OqlQuery q) {
        if (!q.groupBy().isEmpty() || q.having() != null) {
            return true;
        }
        return AstScanner.containsAggregate(q);
    }

    private static boolean referencesRetainedSize(OqlStatement.OqlQuery q) {
        return AstScanner.referencesRetained(q);
    }

    private static ClassHierarchyExpansion expansionFor(OqlStatement stmt) {
        OqlQuery q = firstQuery(stmt);
        if (q == null) {
            return null;
        }
        FromClause from = q.from();
        if (from.kind() == FromClause.FromKind.NONE) {
            return null;
        }
        if (!(from.source() instanceof ObjectSource.ClassSource cs)) {
            // Non-class-name source under INSTANCEOF/IMPLEMENTS — defer to Plan C.
            return null;
        }
        return new ClassHierarchyExpansion(cs.className(), from.kind() == FromClause.FromKind.IMPLEMENTS);
    }

    private static OqlQuery firstQuery(OqlStatement stmt) {
        return switch (stmt) {
            case OqlQuery q -> q;
            case UnionQuery u -> u.branches().isEmpty() ? null : u.branches().get(0);
        };
    }

    private static boolean containsAsRetainedSet(OqlStatement stmt) {
        return switch (stmt) {
            case OqlQuery q -> q.select().modifier() == SelectClause.SelectModifier.AS_RETAINED_SET;
            case UnionQuery u -> u.branches().stream()
                    .anyMatch(b -> b.select().modifier() == SelectClause.SelectModifier.AS_RETAINED_SET);
        };
    }

    public static String classIdsPlaceholder() {
        return CLASS_IDS_PLACEHOLDER;
    }
}
