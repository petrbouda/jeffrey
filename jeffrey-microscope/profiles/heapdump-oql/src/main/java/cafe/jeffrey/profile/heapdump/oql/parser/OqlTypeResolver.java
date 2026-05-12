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
package cafe.jeffrey.profile.heapdump.oql.parser;

import cafe.jeffrey.profile.heapdump.oql.ast.ObjectSource;
import cafe.jeffrey.profile.heapdump.oql.ast.OqlExpr;
import cafe.jeffrey.profile.heapdump.oql.ast.OqlStatement;
import cafe.jeffrey.profile.heapdump.oql.ast.OqlStatement.OqlQuery;
import cafe.jeffrey.profile.heapdump.oql.ast.OqlStatement.UnionQuery;
import cafe.jeffrey.profile.heapdump.oql.ast.OrderItem;
import cafe.jeffrey.profile.heapdump.oql.ast.PathSegment;
import cafe.jeffrey.profile.heapdump.oql.ast.SelectClause.Projection;

import java.util.Set;

/**
 * Validates names referenced in an {@link OqlStatement}.
 *
 * <p>Phase 1 keeps this deliberately light:
 * <ul>
 *   <li>Unknown {@code @attr} names → parse error with the list of known attrs.</li>
 *   <li>Unknown function names (not in the known function set) → parse error.</li>
 *   <li>Mismatched aggregate use of {@code count(*)} (must be aggregate) → enforced.</li>
 * </ul>
 *
 * <p>Type propagation up through the tree is deferred to the compiler — it
 * needs HeapView access to resolve {@code o.field} types anyway.
 */
final class OqlTypeResolver {

    /** Built-in {@code @attribute} names known to the engine. */
    static final Set<String> KNOWN_ATTRIBUTES = Set.of(
            "retainedHeapSize",
            "usedHeapSize",
            "objectId",
            "objectAddress",
            "displayName",
            "clazz"
    );

    /** Known plain-function names. Names containing {@code .} (like {@code heap.objects}) are checked separately. */
    static final Set<String> KNOWN_FUNCTIONS = Set.of(
            // Object/graph
            "sizeof", "rsizeof", "objectid", "classof", "toString", "toHex",
            "inbounds", "outbounds", "referrers", "reachables",
            "dominators", "dominatorof", "root",
            // Aggregates
            "count", "sum", "min", "max", "avg",
            // String predicates
            "startsWith", "endsWith", "contains", "matchesRegex",
            "equalsString", "equalsIgnoreCase", "isEmptyString",
            // String accessors
            "stringLength", "substring", "lower", "upper", "trim",
            "indexOf", "lastIndexOf", "charAt",
            // Fuzzy text
            "levenshtein", "jaroWinklerSimilarity",
            // Numeric
            "abs", "ceil", "floor", "round", "mod", "power", "sqrt",
            // Control flow
            "coalesce", "nullif", "least", "greatest", "format"
    );

    /** Subset of {@link #KNOWN_FUNCTIONS} that are aggregates (legal with {@code count(*)} etc.). */
    static final Set<String> AGGREGATE_FUNCTIONS = Set.of(
            "count", "sum", "min", "max", "avg"
    );

    /** {@code heap.*} helpers that take a class name or id. */
    static final Set<String> HEAP_HELPERS = Set.of(
            "heap.objects", "heap.findClass", "heap.classes", "heap.roots", "heap.findObject"
    );

    void resolve(OqlStatement stmt) {
        switch (stmt) {
            case OqlQuery q -> resolveQuery(q);
            case UnionQuery u -> u.branches().forEach(this::resolveQuery);
        }
    }

    private void resolveQuery(OqlQuery q) {
        resolveFromSource(q.from().source());
        for (Projection p : q.select().projections()) {
            if (p.expr() != null) {
                walk(p.expr());
            }
        }
        if (q.whereExpr() != null) {
            walk(q.whereExpr());
        }
        for (OqlExpr e : q.groupBy()) {
            walk(e);
        }
        if (q.having() != null) {
            walk(q.having());
        }
        for (OrderItem oi : q.orderBy()) {
            walk(oi.expr());
        }
    }

    private void resolveFromSource(ObjectSource source) {
        switch (source) {
            case ObjectSource.SubquerySource sq -> resolveQuery(sq.query());
            case ObjectSource.FunctionSource fs -> {
                String name = fs.name();
                boolean isHeapHelper = name.startsWith("heap.");
                if (isHeapHelper) {
                    if (!HEAP_HELPERS.contains(name)) {
                        throw new OqlParseException(
                                "Unknown heap helper: " + name + ". Known: " + HEAP_HELPERS);
                    }
                } else if (!KNOWN_FUNCTIONS.contains(name)) {
                    throw new OqlParseException("Unknown function: " + name);
                }
                for (OqlExpr arg : fs.args()) {
                    walk(arg);
                }
            }
            case ObjectSource.ClassSource ignored -> {}
            case ObjectSource.RegexSource ignored -> {}
        }
    }

    private void walk(OqlExpr expr) {
        switch (expr) {
            case OqlExpr.AttrRef a -> {
                if (!KNOWN_ATTRIBUTES.contains(a.name())) {
                    throw new OqlParseException(
                            "Unknown attribute: @" + a.name() + ". Known: " + KNOWN_ATTRIBUTES);
                }
            }
            case OqlExpr.FunctionCall f -> {
                String name = f.name();
                boolean isHeapHelper = name.startsWith("heap.");
                if (isHeapHelper) {
                    if (!HEAP_HELPERS.contains(name)) {
                        throw new OqlParseException(
                                "Unknown heap helper: " + name + ". Known: " + HEAP_HELPERS);
                    }
                } else if (!KNOWN_FUNCTIONS.contains(name)) {
                    throw new OqlParseException(
                            "Unknown function: " + name);
                }
                if (f.star() && !AGGREGATE_FUNCTIONS.contains(name)) {
                    throw new OqlParseException(
                            "Function does not accept '*': " + name);
                }
                for (OqlExpr arg : f.args()) {
                    walk(arg);
                }
            }
            case OqlExpr.BinaryOp b -> {
                walk(b.left());
                walk(b.right());
            }
            case OqlExpr.UnaryOp u -> walk(u.operand());
            case OqlExpr.PathExpr p -> {
                walk(p.root());
                for (PathSegment s : p.segments()) {
                    switch (s) {
                        case PathSegment.Index idx -> walk(idx.index());
                        case PathSegment.AttrField attr -> {
                            if (!KNOWN_ATTRIBUTES.contains(attr.name())) {
                                throw new OqlParseException(
                                        "Unknown attribute: @" + attr.name()
                                                + ". Known: " + KNOWN_ATTRIBUTES);
                            }
                        }
                        case PathSegment.Field ignored -> {}
                    }
                }
            }
            case OqlExpr.InOp in -> {
                walk(in.left());
                for (OqlExpr v : in.values()) {
                    walk(v);
                }
            }
            case OqlExpr.NullCheck n -> walk(n.operand());
            case OqlExpr.CaseExpr c -> {
                for (OqlExpr.CaseExpr.WhenClause w : c.whens()) {
                    walk(w.condition());
                    walk(w.result());
                }
                if (c.elseExpr() != null) {
                    walk(c.elseExpr());
                }
            }
            case OqlExpr.SubqueryExpr s -> resolveQuery(s.query());
            case OqlExpr.Literal ignored -> {
                // nothing to validate
            }
            case OqlExpr.BindingRef ignored -> {
                // binding resolution happens at compile time against the FROM alias
            }
        }
    }
}
