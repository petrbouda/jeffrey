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
package cafe.jeffrey.profile.heapdump.oql.compiler;

import cafe.jeffrey.profile.heapdump.oql.ast.OqlExpr;
import cafe.jeffrey.profile.heapdump.oql.ast.OqlStatement.OqlQuery;
import cafe.jeffrey.profile.heapdump.oql.ast.OrderItem;
import cafe.jeffrey.profile.heapdump.oql.ast.PathSegment;
import cafe.jeffrey.profile.heapdump.oql.ast.SelectClause.Projection;

import java.util.Set;

/**
 * Lightweight read-only walkers used by the compiler to inspect the AST
 * before deciding on an execution plan. Each scanner answers one boolean
 * question and short-circuits on first hit.
 */
final class AstScanner {

    private static final Set<String> AGGREGATE_FUNCS = Set.of("count", "sum", "min", "max", "avg");
    private static final Set<String> RETAINED_FUNCS = Set.of("rsizeof");
    private static final Set<String> RETAINED_ATTRS = Set.of("retainedHeapSize");

    private AstScanner() {
    }

    static boolean containsAggregate(OqlQuery q) {
        for (Projection p : q.select().projections()) {
            if (p.expr() != null && hasAggregate(p.expr())) {
                return true;
            }
        }
        if (q.whereExpr() != null && hasAggregate(q.whereExpr())) {
            return true;
        }
        for (OqlExpr e : q.groupBy()) {
            if (hasAggregate(e)) {
                return true;
            }
        }
        if (q.having() != null && hasAggregate(q.having())) {
            return true;
        }
        for (OrderItem oi : q.orderBy()) {
            if (hasAggregate(oi.expr())) {
                return true;
            }
        }
        return false;
    }

    static boolean referencesRetained(OqlQuery q) {
        for (Projection p : q.select().projections()) {
            if (p.expr() != null && hasRetained(p.expr())) {
                return true;
            }
        }
        if (q.whereExpr() != null && hasRetained(q.whereExpr())) {
            return true;
        }
        for (OqlExpr e : q.groupBy()) {
            if (hasRetained(e)) {
                return true;
            }
        }
        if (q.having() != null && hasRetained(q.having())) {
            return true;
        }
        for (OrderItem oi : q.orderBy()) {
            if (hasRetained(oi.expr())) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasAggregate(OqlExpr expr) {
        return scan(expr, AstScanner::isAggregate);
    }

    private static boolean hasRetained(OqlExpr expr) {
        return scan(expr, AstScanner::isRetained);
    }

    private static boolean isAggregate(OqlExpr e) {
        return e instanceof OqlExpr.FunctionCall fc && AGGREGATE_FUNCS.contains(fc.name());
    }

    private static boolean isRetained(OqlExpr e) {
        if (e instanceof OqlExpr.FunctionCall fc && RETAINED_FUNCS.contains(fc.name())) {
            return true;
        }
        if (e instanceof OqlExpr.AttrRef a && RETAINED_ATTRS.contains(a.name())) {
            return true;
        }
        if (e instanceof OqlExpr.PathExpr p) {
            for (PathSegment seg : p.segments()) {
                if (seg instanceof PathSegment.AttrField af && RETAINED_ATTRS.contains(af.name())) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean scan(OqlExpr expr, java.util.function.Predicate<OqlExpr> matcher) {
        if (matcher.test(expr)) {
            return true;
        }
        return switch (expr) {
            case OqlExpr.BinaryOp b -> scan(b.left(), matcher) || scan(b.right(), matcher);
            case OqlExpr.UnaryOp u -> scan(u.operand(), matcher);
            case OqlExpr.FunctionCall fc -> {
                for (OqlExpr a : fc.args()) {
                    if (scan(a, matcher)) {
                        yield true;
                    }
                }
                yield false;
            }
            case OqlExpr.PathExpr p -> {
                if (scan(p.root(), matcher)) {
                    yield true;
                }
                for (PathSegment seg : p.segments()) {
                    if (seg instanceof PathSegment.Index idx && scan(idx.index(), matcher)) {
                        yield true;
                    }
                }
                yield false;
            }
            case OqlExpr.InOp in -> {
                if (scan(in.left(), matcher)) {
                    yield true;
                }
                for (OqlExpr v : in.values()) {
                    if (scan(v, matcher)) {
                        yield true;
                    }
                }
                yield false;
            }
            case OqlExpr.NullCheck nc -> scan(nc.operand(), matcher);
            case OqlExpr.CaseExpr ce -> {
                for (OqlExpr.CaseExpr.WhenClause w : ce.whens()) {
                    if (scan(w.condition(), matcher) || scan(w.result(), matcher)) {
                        yield true;
                    }
                }
                yield ce.elseExpr() != null && scan(ce.elseExpr(), matcher);
            }
            case OqlExpr.SubqueryExpr ignored -> false;
            default -> false;
        };
    }
}
