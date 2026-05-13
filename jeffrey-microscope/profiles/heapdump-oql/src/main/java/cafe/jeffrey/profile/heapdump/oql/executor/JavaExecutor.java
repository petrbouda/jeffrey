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
import cafe.jeffrey.profile.heapdump.oql.ast.FromClause;
import cafe.jeffrey.profile.heapdump.oql.ast.ObjectSource;
import cafe.jeffrey.profile.heapdump.oql.ast.OqlExpr;
import cafe.jeffrey.profile.heapdump.oql.ast.OqlStatement.OqlQuery;
import cafe.jeffrey.profile.heapdump.oql.ast.OrderItem;
import cafe.jeffrey.profile.heapdump.oql.ast.PathSegment;
import cafe.jeffrey.profile.heapdump.oql.ast.SelectClause.Projection;
import cafe.jeffrey.profile.heapdump.oql.compiler.ClassHierarchyResolver;
import cafe.jeffrey.profile.heapdump.oql.compiler.ExecutionPlan;
import cafe.jeffrey.profile.heapdump.oql.compiler.ExecutionPlan.JavaPlan;
import cafe.jeffrey.profile.heapdump.oql.compiler.ExecutionPlan.PrePass.ClassHierarchyExpansion;
import cafe.jeffrey.profile.heapdump.parser.HeapView;
import cafe.jeffrey.profile.heapdump.parser.InstanceRow;
import cafe.jeffrey.profile.heapdump.parser.JavaClassRow;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Plan C executor — iterates candidate instances Java-side and evaluates
 * WHERE / SELECT row by row.
 *
 * <p>Materializes all surviving rows before applying ORDER BY because the
 * sort key may reference Java-evaluated expressions; the {@code limit}
 * passed to the executor caps the final row count.
 */
public final class JavaExecutor {

    private static final ClassHierarchyResolver HIERARCHY = new ClassHierarchyResolver();

    private JavaExecutor() {
    }

    public static OQLQueryResult execute(JavaPlan plan, HeapView view, int limit) throws SQLException {
        if (plan.reason() != null) {
            return OQLQueryResult.error(
                    "OQL feature requires the Java executor (Phase 3+): " + plan.reason(), 0);
        }
        OqlQuery query = plan.query();
        if (query == null) {
            return OQLQueryResult.error("OQL feature not supported yet", 0);
        }

        List<Long> classIds = resolveCandidateClasses(view, query.from(), plan.expansion());
        if (classIds.isEmpty()) {
            return OQLQueryResult.success(List.of(), 0, false, 0);
        }

        List<OQLResultEntry> entries = new ArrayList<>();
        // Hoist per-query setup out of the inner loop. PathExprEvaluator
        // queries dump_metadata once for the id-size; constructing it per
        // row would issue one SQL round-trip per candidate instance, which
        // dominates Plan-C runtime on multi-million-string heaps.
        PathExprEvaluator pathEval = new PathExprEvaluator(view);
        // ORDER BY requires materializing every surviving row before sorting.
        // Without ORDER BY we can short-circuit at the user's limit — this
        // turns "first 50 matching Strings" from a 5M-instance scan into
        // ~thousands of instances in the common case.
        boolean hasOrderBy = !query.orderBy().isEmpty();
        int gatherBudget = hasOrderBy ? Integer.MAX_VALUE : limit + 1;
        outer:
        for (long classId : classIds) {
            JavaClassRow clazz = view.findClassById(classId).orElse(null);
            if (clazz == null) {
                continue;
            }
            try (Stream<InstanceRow> stream = view.instances(classId)) {
                for (InstanceRow inst : (Iterable<InstanceRow>) stream::iterator) {
                    if (entries.size() >= gatherBudget) {
                        break outer;
                    }
                    Row row = new Row(view, inst, clazz, query.from().alias());
                    ExprEvaluator eval = new ExprEvaluator(row, pathEval);
                    if (query.whereExpr() != null && !eval.evalPredicate(query.whereExpr())) {
                        continue;
                    }
                    appendRowEntries(eval, query, row, entries);
                }
            }
        }

        applyOrderBy(query, view, entries);

        boolean hasMore = entries.size() > limit;
        if (hasMore) {
            entries = new ArrayList<>(entries.subList(0, limit));
        }
        return OQLQueryResult.success(entries, entries.size(), hasMore, 0);
    }

    private static List<Long> resolveCandidateClasses(
            HeapView view, FromClause from, ExecutionPlan.PrePass expansion) throws SQLException {
        if (expansion instanceof ClassHierarchyExpansion che) {
            return che.isInterface()
                    ? HIERARCHY.resolveImplements(view, che.rootClassName())
                    : HIERARCHY.resolveInstanceOf(view, che.rootClassName());
        }
        if (from.source() instanceof ObjectSource.ClassSource cs) {
            return view.findClassesByName(cs.className()).stream()
                    .map(JavaClassRow::classId)
                    .toList();
        }
        return List.of();
    }

    /**
     * Visible to {@code StringFallbackExecutor} so it can reuse the same row
     * → projection logic when scanning large-content Strings that were
     * skipped by the SQL pushdown path.
     */
    static void appendRowEntries(ExprEvaluator eval, OqlQuery query, Row row, List<OQLResultEntry> entries) throws SQLException {
        List<Projection> projections = query.select().projections();
        // SELECT * → canonical instance triple
        if (projections.size() == 1 && projections.get(0).star()) {
            entries.add(canonicalInstance(row));
            return;
        }
        // Single projection that is a graph function (returns a list) → fan out.
        if (projections.size() == 1 && isGraphFunction(projections.get(0).expr())) {
            Object result = eval.eval(projections.get(0).expr());
            if (result instanceof List<?> list) {
                for (Object item : list) {
                    if (item instanceof InstanceRow ref) {
                        JavaClassRow refClass = ref.classId() == null
                                ? null
                                : row.view().findClassById(ref.classId()).orElse(null);
                        entries.add(new OQLResultEntry(
                                ref.instanceId(),
                                refClass == null ? null : refClass.name(),
                                projections.get(0).expr() instanceof OqlExpr.FunctionCall fc
                                        ? fc.name() + "(" + row.instance().instanceId() + ") -> "
                                                + (refClass == null ? "?" : refClass.name()) + "@"
                                                + Long.toHexString(ref.instanceId())
                                        : String.valueOf(ref),
                                ref.shallowSize(),
                                null));
                    }
                }
                return;
            }
        }
        // Generic: evaluate each projection, stitch into one OQLResultEntry.
        entries.add(genericProjection(eval, query, row, projections));
    }

    private static OQLResultEntry canonicalInstance(Row row) {
        return new OQLResultEntry(
                row.instance().instanceId(),
                row.clazz().name(),
                row.clazz().name() + "@" + Long.toHexString(row.instance().instanceId()),
                row.instance().shallowSize(),
                null);
    }

    private static OQLResultEntry genericProjection(ExprEvaluator eval, OqlQuery query, Row row, List<Projection> projections) throws SQLException {
        Long objectId = null;
        String className = null;
        long size = 0;
        Long retained = null;
        StringBuilder value = new StringBuilder();
        for (int i = 0; i < projections.size(); i++) {
            Projection p = projections.get(i);
            Object v = eval.eval(p.expr());
            if (i > 0) {
                value.append(" | ");
            }
            String label = p.alias() != null ? p.alias() : describeProjection(p.expr(), i);
            value.append(label).append('=').append(renderValue(v));

            // Detect distinguished projections so the result row carries
            // drill-in identity when possible.
            if (objectId == null && p.expr() instanceof OqlExpr.BindingRef) {
                objectId = row.instance().instanceId();
                className = row.clazz().name();
                size = row.instance().shallowSize();
            } else if (objectId == null && v instanceof InstanceRow inst) {
                objectId = inst.instanceId();
                size = inst.shallowSize();
                JavaClassRow rc = inst.classId() == null
                        ? null
                        : row.view().findClassById(inst.classId()).orElse(null);
                if (rc != null) {
                    className = rc.name();
                }
            }
            if (retained == null && isRetainedSizeProjection(p.expr()) && v instanceof Number n) {
                retained = n.longValue();
            }
            if (size == 0 && isSizeProjection(p.expr()) && v instanceof Number n) {
                size = n.longValue();
            }
        }
        return new OQLResultEntry(objectId, className, value.toString(), size, retained);
    }

    private static boolean isGraphFunction(OqlExpr expr) {
        return expr instanceof OqlExpr.FunctionCall fc
                && switch (fc.name()) {
                    case "inbounds", "outbounds", "referrers", "reachables", "dominators" -> true;
                    default -> false;
                };
    }

    private static boolean isRetainedSizeProjection(OqlExpr expr) {
        if (expr instanceof OqlExpr.AttrRef a && "retainedHeapSize".equals(a.name())) {
            return true;
        }
        if (expr instanceof OqlExpr.FunctionCall fc && "rsizeof".equals(fc.name())) {
            return true;
        }
        return endsWithAttr(expr, "retainedHeapSize");
    }

    private static boolean isSizeProjection(OqlExpr expr) {
        if (expr instanceof OqlExpr.AttrRef a && "usedHeapSize".equals(a.name())) {
            return true;
        }
        if (expr instanceof OqlExpr.FunctionCall fc && "sizeof".equals(fc.name())) {
            return true;
        }
        return endsWithAttr(expr, "usedHeapSize");
    }

    /**
     * Matches a path expression like {@code s.@retainedHeapSize} — a {@code PathExpr}
     * whose final segment is an {@link PathSegment.AttrField} with the given name.
     * The bare {@code @attr} form is already covered by the {@code AttrRef} checks above.
     */
    private static boolean endsWithAttr(OqlExpr expr, String attrName) {
        if (!(expr instanceof OqlExpr.PathExpr path)) {
            return false;
        }
        List<PathSegment> segments = path.segments();
        if (segments.isEmpty()) {
            return false;
        }
        PathSegment last = segments.get(segments.size() - 1);
        return last instanceof PathSegment.AttrField attr && attrName.equals(attr.name());
    }

    private static String describeProjection(OqlExpr expr, int index) {
        return switch (expr) {
            case OqlExpr.BindingRef b -> b.name();
            case OqlExpr.AttrRef a -> "@" + a.name();
            case OqlExpr.FunctionCall fc -> fc.name();
            default -> "col" + index;
        };
    }

    private static String renderValue(Object v) {
        if (v == null) {
            return "null";
        }
        if (v instanceof InstanceRow ir) {
            return "instance@" + Long.toHexString(ir.instanceId());
        }
        if (v instanceof JavaClassRow jc) {
            return "class:" + jc.name();
        }
        if (v instanceof List<?> list) {
            return "[" + list.size() + " items]";
        }
        return String.valueOf(v);
    }

    private static void applyOrderBy(OqlQuery query, HeapView view, List<OQLResultEntry> entries) throws SQLException {
        if (query.orderBy().isEmpty()) {
            return;
        }
        // ORDER BY in Plan C is best-effort: we can only sort on values
        // computable from each result entry independent of its origin row
        // (since we've already lost the original Row). For now we sort by
        // the entry's value preview which gives stable, deterministic ordering
        // even if not always the user-intended one. Phase 4 can do better by
        // attaching projection-value vectors to entries.
        // The user's most common ORDER BY in Plan C will be on retained/used
        // size which we already populate on the entry, so cover those.
        OqlExpr key = query.orderBy().get(0).expr();
        boolean desc = query.orderBy().get(0).descending();
        Comparator<OQLResultEntry> cmp;
        if (isRetainedSizeProjection(key) || isSizeProjection(key)) {
            cmp = Comparator.comparing(
                    e -> e.retainedSize() != null ? e.retainedSize() : (Long) e.size(),
                    Comparator.nullsLast(Long::compareTo));
        } else {
            cmp = Comparator.comparing(OQLResultEntry::value, Comparator.nullsLast(String::compareTo));
        }
        if (desc) {
            cmp = cmp.reversed();
        }
        entries.sort(cmp);
    }
}
