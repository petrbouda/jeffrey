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

import cafe.jeffrey.profile.heapdump.oql.ast.BinaryOperator;
import cafe.jeffrey.profile.heapdump.oql.ast.FromClause;
import cafe.jeffrey.profile.heapdump.oql.ast.ObjectSource;
import cafe.jeffrey.profile.heapdump.oql.ast.OqlExpr;
import cafe.jeffrey.profile.heapdump.oql.ast.OqlStatement;
import cafe.jeffrey.profile.heapdump.oql.ast.OqlStatement.OqlQuery;
import cafe.jeffrey.profile.heapdump.oql.ast.OqlStatement.UnionQuery;
import cafe.jeffrey.profile.heapdump.oql.ast.OrderItem;
import cafe.jeffrey.profile.heapdump.oql.ast.PathSegment;
import cafe.jeffrey.profile.heapdump.oql.ast.SelectClause;
import cafe.jeffrey.profile.heapdump.oql.ast.SelectClause.Projection;
import cafe.jeffrey.profile.heapdump.oql.ast.UnaryOperator;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Emits DuckDB SQL for queries that the compiler has classified as Plan A
 * (pure SQL) or the SQL fragment of Plan B (Java pre-pass + SQL).
 *
 * <p>The emitter is single-pass and self-contained: every expression visited
 * appends text to {@code sql} and (optionally) values to {@code params}.
 *
 * <p>For a query like {@code SELECT @retainedHeapSize FROM java.lang.String s}
 * the emitter produces:
 * <pre>
 *     SELECT rs.bytes
 *     FROM instance i
 *       JOIN class c ON i.class_id = c.class_id
 *       JOIN retained_size rs ON i.instance_id = rs.instance_id
 *     WHERE c.name = ?
 * </pre>
 * with {@code params=["java.lang.String"]}.
 */
final class SqlEmitter {

    private static final String INSTANCE_ALIAS = "i";
    private static final String CLASS_ALIAS = "c";
    private static final String RETAINED_ALIAS = "rs";

    /** Aliases recognised as "this object's instance id" for @objectId / objectid(o) / instance ref. */
    private static final Set<String> OBJECT_ID_ATTRS = Set.of("objectId");
    private static final Set<String> ADDRESS_ATTRS = Set.of("objectAddress");
    private static final Set<String> USED_SIZE_ATTRS = Set.of("usedHeapSize");
    private static final Set<String> RETAINED_SIZE_ATTRS = Set.of("retainedHeapSize");
    private static final Set<String> DISPLAY_NAME_ATTRS = Set.of("displayName");
    private static final Set<String> CLAZZ_ATTRS = Set.of("clazz");

    private static final Set<String> PASSTHROUGH_NUMERIC_FUNCS = Set.of(
            "abs", "ceil", "floor", "round", "mod", "power", "sqrt",
            "least", "greatest", "coalesce", "nullif", "format"
    );

    private static final Set<String> AGGREGATE_FUNCS = Set.of(
            "count", "sum", "min", "max", "avg"
    );

    private final StringBuilder sql = new StringBuilder(256);
    private final List<Object> params = new ArrayList<>();
    private final String bindingAlias;
    private boolean needsClassJoin;
    private boolean needsRetainedJoin;
    private final ResultShapeBuilder shapeBuilder = new ResultShapeBuilder();
    /** SELECT-list aliases known to the emitter; ORDER BY/HAVING references emit the alias verbatim. */
    private final java.util.Set<String> selectAliases = new java.util.HashSet<>();
    /** Whether the current sub-emission is the ORDER BY clause — used to resolve alias references. */
    private boolean inOrderBy;

    private SqlEmitter(String bindingAlias) {
        this.bindingAlias = bindingAlias;
    }

    /**
     * Top-level entry. {@code classIdParamPlaceholder} is non-null when the
     * outer compiler has decided that this query needs a class-id IN list
     * substituted at execution time (Plan B).
     */
    static EmissionResult emit(OqlStatement stmt, String classIdParamPlaceholder) {
        return switch (stmt) {
            case OqlQuery q -> emitSingle(q, classIdParamPlaceholder);
            case UnionQuery u -> emitUnion(u, classIdParamPlaceholder);
        };
    }

    private static EmissionResult emitSingle(OqlQuery q, String classIdParamPlaceholder) {
        SqlEmitter e = new SqlEmitter(q.from().alias() != null ? q.from().alias() : "");
        e.emitQuery(q, classIdParamPlaceholder);
        return new EmissionResult(e.sql.toString(), List.copyOf(e.params),
                e.needsRetainedJoin, e.shapeBuilder.build());
    }

    private static EmissionResult emitUnion(UnionQuery u, String classIdParamPlaceholder) {
        StringBuilder out = new StringBuilder(512);
        List<Object> allParams = new ArrayList<>();
        boolean needsRetained = false;
        ResultShape firstShape = null;
        boolean first = true;
        for (OqlQuery branch : u.branches()) {
            EmissionResult r = emitSingle(branch, classIdParamPlaceholder);
            if (!first) {
                out.append(" UNION ALL ");
            }
            out.append("(").append(r.sql()).append(")");
            allParams.addAll(r.params());
            needsRetained |= r.needsRetainedTree();
            if (first) {
                firstShape = r.shape();
            }
            first = false;
        }
        return new EmissionResult(out.toString(), allParams, needsRetained, firstShape);
    }

    private void emitQuery(OqlQuery q, String classIdParamPlaceholder) {
        // Walk every clause into its own buffer first so the JOIN decisions
        // (needsClassJoin / needsRetainedJoin) reflect every reference, not
        // just the SELECT-list.
        StringBuilder selectBuf = new StringBuilder();
        emitSelect(q.select(), selectBuf);

        StringBuilder whereBuf = new StringBuilder();
        boolean hasClassFilter = appendClassFilter(whereBuf, q.from(), classIdParamPlaceholder);
        if (q.whereExpr() != null) {
            if (hasClassFilter) {
                whereBuf.append(" AND ");
            }
            whereBuf.append("(");
            emitExpr(q.whereExpr(), whereBuf);
            whereBuf.append(")");
        }

        StringBuilder groupBuf = new StringBuilder();
        for (int i = 0; i < q.groupBy().size(); i++) {
            if (i > 0) {
                groupBuf.append(", ");
            }
            emitExpr(q.groupBy().get(i), groupBuf);
        }

        StringBuilder havingBuf = new StringBuilder();
        if (q.having() != null) {
            emitExpr(q.having(), havingBuf);
        }

        StringBuilder orderBuf = new StringBuilder();
        inOrderBy = true;
        for (int i = 0; i < q.orderBy().size(); i++) {
            if (i > 0) {
                orderBuf.append(", ");
            }
            OrderItem oi = q.orderBy().get(i);
            emitExpr(oi.expr(), orderBuf);
            if (oi.descending()) {
                orderBuf.append(" DESC");
            }
        }
        inOrderBy = false;

        // Now assemble — JOIN flags are final.
        sql.append("SELECT ");
        if (q.select().modifier() == SelectClause.SelectModifier.DISTINCT) {
            sql.append("DISTINCT ");
        }
        String selectText = selectBuf.toString();
        if (selectText.contains("__STAR__")) {
            // Star expansion happens here so the retained-size column is
            // included only when the retained JOIN is in play.
            selectText = selectText.replace("__STAR__", renderStarExpansion());
        }
        sql.append(selectText);
        sql.append(" FROM instance ").append(INSTANCE_ALIAS);
        if (needsClassJoin) {
            sql.append(" JOIN class ").append(CLASS_ALIAS)
                    .append(" ON ").append(INSTANCE_ALIAS).append(".class_id = ")
                    .append(CLASS_ALIAS).append(".class_id");
        }
        if (needsRetainedJoin) {
            sql.append(" JOIN retained_size ").append(RETAINED_ALIAS)
                    .append(" ON ").append(INSTANCE_ALIAS).append(".instance_id = ")
                    .append(RETAINED_ALIAS).append(".instance_id");
        }
        if (whereBuf.length() > 0) {
            sql.append(" WHERE ").append(whereBuf);
        }
        if (groupBuf.length() > 0) {
            sql.append(" GROUP BY ").append(groupBuf);
        }
        if (havingBuf.length() > 0) {
            sql.append(" HAVING ").append(havingBuf);
        }
        if (orderBuf.length() > 0) {
            sql.append(" ORDER BY ").append(orderBuf);
        }
        if (q.limit() != null) {
            sql.append(" LIMIT ").append(q.limit().limit());
            if (q.limit().offset() > 0) {
                sql.append(" OFFSET ").append(q.limit().offset());
            }
        }
    }

    private boolean appendClassFilter(StringBuilder where, FromClause from, String classIdParamPlaceholder) {
        // Plan B: a placeholder will be substituted with the class_id IN list at execution time.
        if (classIdParamPlaceholder != null) {
            where.append(INSTANCE_ALIAS).append(".class_id IN (").append(classIdParamPlaceholder).append(")");
            needsClassJoin = true;
            return true;
        }
        ObjectSource src = from.source();
        if (src instanceof ObjectSource.ClassSource cs) {
            needsClassJoin = true;
            where.append(CLASS_ALIAS).append(".name = ?");
            params.add(cs.className());
            return true;
        }
        if (src instanceof ObjectSource.RegexSource rs) {
            needsClassJoin = true;
            where.append("regexp_matches(").append(CLASS_ALIAS).append(".name, ?)");
            params.add(rs.pattern());
            return true;
        }
        // FunctionSource / SubquerySource: handled at higher levels or already passed to JavaPlan
        return false;
    }

    private void emitSelect(SelectClause select, StringBuilder out) {
        List<Projection> projections = select.projections();
        if (projections.size() == 1 && projections.get(0).star()) {
            // The star expansion is deferred to assembly time so we know
            // whether the retained JOIN is in play and should be included.
            // We emit a placeholder here and patch it later.
            out.append("__STAR__");
            return;
        }
        for (int i = 0; i < projections.size(); i++) {
            if (i > 0) {
                out.append(", ");
            }
            Projection p = projections.get(i);
            emitProjection(p, out);
            if (p.alias() != null) {
                selectAliases.add(p.alias());
            }
        }
    }

    private String renderStarExpansion() {
        StringBuilder out = new StringBuilder();
        out.append(INSTANCE_ALIAS).append(".instance_id, ")
                .append(CLASS_ALIAS).append(".name, ")
                .append(INSTANCE_ALIAS).append(".shallow_size");
        needsClassJoin = true;
        shapeBuilder.add("instance_id", true, false, false, false);
        shapeBuilder.add("class_name", false, true, false, false);
        shapeBuilder.add("shallow_size", false, false, true, false);
        if (needsRetainedJoin) {
            out.append(", ").append(RETAINED_ALIAS).append(".bytes AS retained_size");
            shapeBuilder.add("retained_size", false, false, false, true);
        }
        return out.toString();
    }

    private void emitProjection(Projection p, StringBuilder out) {
        OqlExpr expr = p.expr();
        // Detect whether this projection has a special role for the result mapper.
        ProjectionKind kind = classify(expr);
        switch (kind) {
            case OBJECT_ID -> {
                out.append(INSTANCE_ALIAS).append(".instance_id");
                shapeBuilder.add(p.alias() != null ? p.alias() : "instance_id",
                        true, false, false, false);
            }
            case CLASS_NAME -> {
                out.append(CLASS_ALIAS).append(".name");
                needsClassJoin = true;
                shapeBuilder.add(p.alias() != null ? p.alias() : "class_name",
                        false, true, false, false);
            }
            case SHALLOW_SIZE -> {
                out.append(INSTANCE_ALIAS).append(".shallow_size");
                shapeBuilder.add(p.alias() != null ? p.alias() : "shallow_size",
                        false, false, true, false);
            }
            case RETAINED_SIZE -> {
                out.append(RETAINED_ALIAS).append(".bytes");
                needsRetainedJoin = true;
                shapeBuilder.add(p.alias() != null ? p.alias() : "retained_size",
                        false, false, false, true);
            }
            case INSTANCE_TRIPLE -> {
                out.append(INSTANCE_ALIAS).append(".instance_id, ")
                        .append(CLASS_ALIAS).append(".name, ")
                        .append(INSTANCE_ALIAS).append(".shallow_size");
                needsClassJoin = true;
                shapeBuilder.add("instance_id", true, false, false, false);
                shapeBuilder.add("class_name", false, true, false, false);
                shapeBuilder.add("shallow_size", false, false, true, false);
            }
            case GENERIC -> {
                emitExpr(expr, out);
                String alias = p.alias() != null ? p.alias() : defaultAlias(expr);
                out.append(" AS ").append(alias);
                shapeBuilder.add(alias, false, false, false, false);
            }
        }
    }

    private enum ProjectionKind {
        OBJECT_ID,
        CLASS_NAME,
        SHALLOW_SIZE,
        RETAINED_SIZE,
        /** A binding reference like {@code o} expands to the three-column instance row. */
        INSTANCE_TRIPLE,
        GENERIC
    }

    private ProjectionKind classify(OqlExpr expr) {
        return switch (expr) {
            case OqlExpr.BindingRef ignored -> ProjectionKind.INSTANCE_TRIPLE;
            case OqlExpr.AttrRef a -> classifyAttr(a.name());
            case OqlExpr.PathExpr path -> classifyPath(path);
            case OqlExpr.FunctionCall f -> classifyFunc(f);
            default -> ProjectionKind.GENERIC;
        };
    }

    private ProjectionKind classifyAttr(String name) {
        if (OBJECT_ID_ATTRS.contains(name)) {
            return ProjectionKind.OBJECT_ID;
        }
        if (USED_SIZE_ATTRS.contains(name)) {
            return ProjectionKind.SHALLOW_SIZE;
        }
        if (RETAINED_SIZE_ATTRS.contains(name)) {
            return ProjectionKind.RETAINED_SIZE;
        }
        // @objectAddress, @displayName, @clazz → generic via expression emitter
        return ProjectionKind.GENERIC;
    }

    private ProjectionKind classifyPath(OqlExpr.PathExpr path) {
        // o.@attr — same as standalone @attr when root is a binding ref
        if (path.root() instanceof OqlExpr.BindingRef
                && path.segments().size() == 1
                && path.segments().get(0) instanceof PathSegment.AttrField attr) {
            return classifyAttr(attr.name());
        }
        // classof(o).name — class name column
        if (path.segments().size() == 1
                && path.segments().get(0) instanceof PathSegment.Field f
                && "name".equals(f.name())
                && path.root() instanceof OqlExpr.FunctionCall fc
                && "classof".equals(fc.name())) {
            return ProjectionKind.CLASS_NAME;
        }
        return ProjectionKind.GENERIC;
    }

    private ProjectionKind classifyFunc(OqlExpr.FunctionCall f) {
        return switch (f.name()) {
            case "sizeof" -> ProjectionKind.SHALLOW_SIZE;
            case "rsizeof" -> ProjectionKind.RETAINED_SIZE;
            case "objectid" -> ProjectionKind.OBJECT_ID;
            default -> ProjectionKind.GENERIC;
        };
    }

    /**
     * Synthesises a clean column alias from the projection expression so the
     * DuckDB result-set carries a readable column label (otherwise the label
     * is the raw SQL text and the row preview ends up showing things like
     * {@code (c."name" || '@' || printf(...))=java.lang.Thread@…}).
     */
    private int genericAliasCounter;

    private String defaultAlias(OqlExpr expr) {
        String hint = aliasHint(expr);
        if (hint != null && !hint.isEmpty()) {
            return hint;
        }
        return "col" + (genericAliasCounter++);
    }

    private static String aliasHint(OqlExpr expr) {
        return switch (expr) {
            case OqlExpr.AttrRef a -> a.name();
            case OqlExpr.PathExpr p -> pathAlias(p);
            case OqlExpr.FunctionCall fc -> fc.name().replace('.', '_');
            case OqlExpr.BindingRef b -> b.name();
            default -> null;
        };
    }

    private static String pathAlias(OqlExpr.PathExpr p) {
        PathSegment last = p.segments().get(p.segments().size() - 1);
        return switch (last) {
            case PathSegment.AttrField a -> a.name();
            case PathSegment.Field f -> f.name();
            case PathSegment.Index ignored -> null;
        };
    }

    // ---- Expression emission ----------------------------------------

    private void emitExpr(OqlExpr expr, StringBuilder out) {
        switch (expr) {
            case OqlExpr.Literal lit -> emitLiteral(lit, out);
            case OqlExpr.BindingRef b -> {
                // Inside ORDER BY a bare identifier may be a SELECT-list alias.
                // (WHERE/HAVING use original expressions; SQL-style aliasing in
                // those clauses is non-standard and ambiguous with FROM-alias
                // bindings.)
                if (inOrderBy && selectAliases.contains(b.name())) {
                    out.append(b.name());
                } else {
                    out.append(INSTANCE_ALIAS).append(".instance_id");
                }
            }
            case OqlExpr.AttrRef a -> emitAttr(a.name(), out);
            case OqlExpr.PathExpr p -> emitPath(p, out);
            case OqlExpr.FunctionCall fc -> emitFunction(fc, out);
            case OqlExpr.BinaryOp bop -> emitBinaryOp(bop, out);
            case OqlExpr.UnaryOp uop -> emitUnaryOp(uop, out);
            case OqlExpr.InOp in -> emitInOp(in, out);
            case OqlExpr.NullCheck nc -> emitNullCheck(nc, out);
            case OqlExpr.CaseExpr c -> emitCase(c, out);
            case OqlExpr.SubqueryExpr ignored -> throw new SqlEmissionException(
                    "Subqueries are not supported yet in Plan A/B");
        }
    }

    private void emitLiteral(OqlExpr.Literal lit, StringBuilder out) {
        Object v = lit.value();
        if (v == null) {
            out.append("NULL");
            return;
        }
        if (v instanceof Boolean b) {
            out.append(b ? "TRUE" : "FALSE");
            return;
        }
        if (v instanceof Long l && l == l.intValue()) {
            // DuckDB picks function overloads by parameter type. A literal that
            // fits in an int is bound as INTEGER so calls like {@code round(x, 1)}
            // match the (DOUBLE, INTEGER) signature rather than (DOUBLE, BIGINT).
            out.append("?");
            params.add(l.intValue());
            return;
        }
        out.append("?");
        params.add(v);
    }

    private void emitAttr(String name, StringBuilder out) {
        switch (name) {
            case "objectId" -> out.append(INSTANCE_ALIAS).append(".instance_id");
            case "objectAddress" -> {
                out.append("printf('0x%x', ").append(INSTANCE_ALIAS).append(".instance_id)");
            }
            case "usedHeapSize" -> out.append(INSTANCE_ALIAS).append(".shallow_size");
            case "retainedHeapSize" -> {
                needsRetainedJoin = true;
                out.append(RETAINED_ALIAS).append(".bytes");
            }
            case "displayName" -> {
                needsClassJoin = true;
                out.append(CLASS_ALIAS).append(".name || '@' || printf('%x', ")
                        .append(INSTANCE_ALIAS).append(".instance_id)");
            }
            case "clazz" -> {
                needsClassJoin = true;
                out.append(CLASS_ALIAS).append(".class_id");
            }
            default -> throw new SqlEmissionException("Unknown attribute: @" + name);
        }
    }

    private void emitPath(OqlExpr.PathExpr p, StringBuilder out) {
        // Only paths the SQL emitter handles: classof(o).name, o.@attr, classof(o).@attr
        // Anything else is Plan C.
        // Special: terminal .length on the binding's array → i.array_length
        if (p.root() instanceof OqlExpr.BindingRef
                && p.segments().size() == 1
                && p.segments().get(0) instanceof PathSegment.Field f
                && "length".equals(f.name())) {
            out.append(INSTANCE_ALIAS).append(".array_length");
            return;
        }
        if (p.root() instanceof OqlExpr.BindingRef
                && p.segments().size() == 1
                && p.segments().get(0) instanceof PathSegment.AttrField attr) {
            emitAttr(attr.name(), out);
            return;
        }
        if (p.root() instanceof OqlExpr.FunctionCall fc
                && "classof".equals(fc.name())
                && p.segments().size() == 1
                && p.segments().get(0) instanceof PathSegment.Field f
                && "name".equals(f.name())) {
            needsClassJoin = true;
            out.append(CLASS_ALIAS).append(".name");
            return;
        }
        if (p.root() instanceof OqlExpr.FunctionCall fc
                && "classof".equals(fc.name())
                && p.segments().size() == 1
                && p.segments().get(0) instanceof PathSegment.AttrField attr
                && "displayName".equals(attr.name())) {
            needsClassJoin = true;
            out.append(CLASS_ALIAS).append(".name");
            return;
        }
        throw new SqlEmissionException("Path expression not SQL-compilable (needs Plan C)");
    }

    private void emitFunction(OqlExpr.FunctionCall fc, StringBuilder out) {
        String name = fc.name();
        if (AGGREGATE_FUNCS.contains(name)) {
            out.append(name.toUpperCase()).append("(");
            if (fc.star()) {
                out.append("*");
            } else {
                for (int i = 0; i < fc.args().size(); i++) {
                    if (i > 0) {
                        out.append(", ");
                    }
                    emitExpr(fc.args().get(i), out);
                }
            }
            out.append(")");
            return;
        }
        switch (name) {
            case "sizeof" -> out.append(INSTANCE_ALIAS).append(".shallow_size");
            case "rsizeof" -> {
                needsRetainedJoin = true;
                out.append(RETAINED_ALIAS).append(".bytes");
            }
            case "objectid" -> out.append(INSTANCE_ALIAS).append(".instance_id");
            case "classof" -> {
                needsClassJoin = true;
                out.append(CLASS_ALIAS).append(".class_id");
            }
            case "dominatorof" -> {
                out.append("(SELECT dominator_id FROM dominator WHERE instance_id = ");
                emitExpr(fc.args().get(0), out);
                out.append(")");
            }
            case "toHex" -> {
                out.append("printf('0x%x', ");
                emitExpr(fc.args().get(0), out);
                out.append(")");
            }
            default -> {
                if (PASSTHROUGH_NUMERIC_FUNCS.contains(name)) {
                    out.append(name).append("(");
                    for (int i = 0; i < fc.args().size(); i++) {
                        if (i > 0) {
                            out.append(", ");
                        }
                        emitExpr(fc.args().get(i), out);
                    }
                    out.append(")");
                } else {
                    throw new SqlEmissionException("Function not SQL-compilable: " + name);
                }
            }
        }
    }

    private void emitBinaryOp(OqlExpr.BinaryOp bop, StringBuilder out) {
        if (bop.op() == BinaryOperator.LIKE) {
            out.append("regexp_matches(");
            emitExpr(bop.left(), out);
            out.append(", ");
            emitExpr(bop.right(), out);
            out.append(")");
            return;
        }
        out.append("(");
        emitExpr(bop.left(), out);
        out.append(" ").append(sqlOp(bop.op())).append(" ");
        emitExpr(bop.right(), out);
        out.append(")");
    }

    private static String sqlOp(BinaryOperator op) {
        return switch (op) {
            case AND -> "AND";
            case OR -> "OR";
            case EQ -> "=";
            case NEQ -> "!=";
            case LT -> "<";
            case LTE -> "<=";
            case GT -> ">";
            case GTE -> ">=";
            case ADD -> "+";
            case SUB -> "-";
            case MUL -> "*";
            case DIV -> "/";
            default -> throw new SqlEmissionException("Operator not SQL-compilable: " + op);
        };
    }

    private void emitUnaryOp(OqlExpr.UnaryOp uop, StringBuilder out) {
        out.append(uop.op() == UnaryOperator.NOT ? "NOT (" : "-(");
        emitExpr(uop.operand(), out);
        out.append(")");
    }

    private void emitInOp(OqlExpr.InOp in, StringBuilder out) {
        emitExpr(in.left(), out);
        out.append(in.negate() ? " NOT IN (" : " IN (");
        for (int i = 0; i < in.values().size(); i++) {
            if (i > 0) {
                out.append(", ");
            }
            emitExpr(in.values().get(i), out);
        }
        out.append(")");
    }

    private void emitNullCheck(OqlExpr.NullCheck nc, StringBuilder out) {
        emitExpr(nc.operand(), out);
        out.append(nc.negate() ? " IS NOT NULL" : " IS NULL");
    }

    private void emitCase(OqlExpr.CaseExpr c, StringBuilder out) {
        out.append("CASE");
        for (OqlExpr.CaseExpr.WhenClause w : c.whens()) {
            out.append(" WHEN ");
            emitExpr(w.condition(), out);
            out.append(" THEN ");
            emitExpr(w.result(), out);
        }
        if (c.elseExpr() != null) {
            out.append(" ELSE ");
            emitExpr(c.elseExpr(), out);
        }
        out.append(" END");
    }

    /** Result of the emission, ready to slot into an {@code SqlPlan}. */
    record EmissionResult(String sql, List<Object> params, boolean needsRetainedTree, ResultShape shape) {
    }

    /** Thrown when the emitter hits a node it can't handle in pure SQL. */
    static final class SqlEmissionException extends RuntimeException {

        SqlEmissionException(String message) {
            super(message);
        }
    }
}
