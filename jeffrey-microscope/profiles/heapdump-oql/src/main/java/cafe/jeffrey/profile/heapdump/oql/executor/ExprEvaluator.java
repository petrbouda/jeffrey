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

import cafe.jeffrey.profile.heapdump.oql.ast.BinaryOperator;
import cafe.jeffrey.profile.heapdump.oql.ast.OqlExpr;
import cafe.jeffrey.profile.heapdump.oql.ast.UnaryOperator;
import cafe.jeffrey.profile.heapdump.oql.function.DominatorFunctions;
import cafe.jeffrey.profile.heapdump.oql.function.FuzzyTextFunctions;
import cafe.jeffrey.profile.heapdump.oql.function.GraphWalkFunctions;
import cafe.jeffrey.profile.heapdump.oql.function.RootPathFunction;
import cafe.jeffrey.profile.heapdump.oql.function.StringAccessors;
import cafe.jeffrey.profile.heapdump.oql.function.StringFunctions;
import cafe.jeffrey.profile.heapdump.oql.function.StringPredicates;
import cafe.jeffrey.profile.heapdump.parser.InstanceRow;
import cafe.jeffrey.profile.heapdump.parser.JavaClassRow;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Java-side interpreter for OQL expressions. Used by {@link JavaExecutor} to
 * evaluate WHERE predicates and SELECT projections row by row.
 *
 * <p>Most function dispatching lives here so the executor stays a simple
 * iteration loop; the heavy lifting (graph walks, string decoding,
 * dominator-tree lookups) is delegated to the {@code function/} package.
 */
final class ExprEvaluator {

    private final Row row;
    private final PathExprEvaluator pathEval;

    /**
     * Constructs an evaluator for one row. The {@code pathEval} is shared
     * across every row of a query — it owns the per-dump idSize lookup, which
     * costs a SQL round-trip to {@code dump_metadata} and would otherwise be
     * paid per candidate instance.
     */
    ExprEvaluator(Row row, PathExprEvaluator pathEval) {
        this.row = row;
        this.pathEval = pathEval;
    }

    Object eval(OqlExpr expr) throws SQLException {
        return switch (expr) {
            case OqlExpr.Literal lit -> lit.value();
            case OqlExpr.BindingRef b -> resolveBinding(b.name());
            case OqlExpr.AttrRef a -> attrFromRow(a.name());
            case OqlExpr.PathExpr p -> evalPath(p);
            case OqlExpr.FunctionCall fc -> evalFunction(fc);
            case OqlExpr.BinaryOp bop -> evalBinary(bop);
            case OqlExpr.UnaryOp uop -> evalUnary(uop);
            case OqlExpr.InOp in -> evalIn(in);
            case OqlExpr.NullCheck nc -> evalNullCheck(nc);
            case OqlExpr.CaseExpr ce -> evalCase(ce);
            case OqlExpr.SubqueryExpr ignored -> throw new UnsupportedOperationException(
                    "Subquery expressions are not supported in Plan C yet");
        };
    }

    boolean evalPredicate(OqlExpr expr) throws SQLException {
        Object v = eval(expr);
        return Numbers.truthy(v);
    }

    private Object resolveBinding(String name) {
        if (name.equals(row.bindingName())) {
            return row.instance();
        }
        // Unknown binding — surface as null rather than throwing so user error
        // messages from comparisons stay clean.
        return null;
    }

    private Object attrFromRow(String name) throws SQLException {
        return switch (name) {
            case "objectId" -> row.instance().instanceId();
            case "objectAddress" -> String.format("0x%x", row.instance().instanceId());
            case "usedHeapSize" -> (long) row.instance().shallowSize();
            case "retainedHeapSize" -> row.view().retainedSize(row.instance().instanceId());
            case "displayName" -> row.clazz().name() + "@" + Long.toHexString(row.instance().instanceId());
            case "clazz" -> row.clazz();
            default -> throw new IllegalArgumentException("Unknown attribute: @" + name);
        };
    }

    private Object evalPath(OqlExpr.PathExpr path) throws SQLException {
        Object current = eval(path.root());
        return pathEval.walk(current, path.segments(), this::eval);
    }

    private Object evalFunction(OqlExpr.FunctionCall fc) throws SQLException {
        String name = fc.name();
        List<OqlExpr> argExprs = fc.args();
        return switch (name) {
            // ---- Instance / class / size helpers ------------------------
            case "sizeof" -> {
                InstanceRow i = asInstance(eval(argExprs.get(0)));
                yield i == null ? null : (long) i.shallowSize();
            }
            case "rsizeof" -> {
                InstanceRow i = asInstance(eval(argExprs.get(0)));
                yield i == null ? null : row.view().retainedSize(i.instanceId());
            }
            case "objectid" -> {
                InstanceRow i = asInstance(eval(argExprs.get(0)));
                yield i == null ? null : i.instanceId();
            }
            case "classof" -> {
                InstanceRow i = asInstance(eval(argExprs.get(0)));
                if (i == null || i.classId() == null) yield null;
                yield row.view().findClassById(i.classId()).orElse(null);
            }
            case "toString" -> {
                InstanceRow i = asInstance(eval(argExprs.get(0)));
                if (i == null) yield null;
                JavaClassRow c = i.classId() == null
                        ? null
                        : row.view().findClassById(i.classId()).orElse(null);
                yield StringFunctions.toStringValue(row.view(), i, c);
            }
            case "toHex" -> StringFunctions.toHex(eval(argExprs.get(0)));

            // ---- Graph traversal ----------------------------------------
            case "inbounds" -> {
                InstanceRow i = asInstance(eval(argExprs.get(0)));
                yield i == null ? List.of() : GraphWalkFunctions.inbounds(row.view(), i.instanceId());
            }
            case "outbounds" -> {
                InstanceRow i = asInstance(eval(argExprs.get(0)));
                yield i == null ? List.of() : GraphWalkFunctions.outbounds(row.view(), i.instanceId());
            }
            case "referrers" -> {
                InstanceRow i = asInstance(eval(argExprs.get(0)));
                yield i == null ? List.of() : GraphWalkFunctions.referrers(row.view(), i.instanceId());
            }
            case "reachables" -> {
                InstanceRow i = asInstance(eval(argExprs.get(0)));
                yield i == null ? List.of() : GraphWalkFunctions.reachables(row.view(), i.instanceId());
            }

            // ---- Dominator tree ----------------------------------------
            case "dominatorof" -> {
                InstanceRow i = asInstance(eval(argExprs.get(0)));
                yield i == null ? null : DominatorFunctions.dominatorOf(row.view(), i.instanceId()).orElse(null);
            }
            case "dominators" -> {
                InstanceRow i = asInstance(eval(argExprs.get(0)));
                yield i == null ? List.of() : DominatorFunctions.dominators(row.view(), i.instanceId());
            }
            case "root" -> {
                InstanceRow i = asInstance(eval(argExprs.get(0)));
                yield i == null ? null : RootPathFunction.renderRootPath(row.view(), i.instanceId());
            }

            // ---- String predicates --------------------------------------
            case "startsWith" -> StringPredicates.startsWith(coerceToString(eval(argExprs.get(0))), eval(argExprs.get(1)));
            case "endsWith" -> StringPredicates.endsWith(coerceToString(eval(argExprs.get(0))), eval(argExprs.get(1)));
            case "contains" -> StringPredicates.contains(coerceToString(eval(argExprs.get(0))), eval(argExprs.get(1)));
            case "matchesRegex" -> StringPredicates.matchesRegex(coerceToString(eval(argExprs.get(0))), eval(argExprs.get(1)));
            case "equalsString" -> StringPredicates.equalsString(coerceToString(eval(argExprs.get(0))), eval(argExprs.get(1)));
            case "equalsIgnoreCase" -> StringPredicates.equalsIgnoreCase(coerceToString(eval(argExprs.get(0))), eval(argExprs.get(1)));
            case "isEmptyString" -> StringPredicates.isEmptyString(coerceToString(eval(argExprs.get(0))));

            // ---- String accessors --------------------------------------
            case "stringLength" -> StringAccessors.stringLength(coerceToString(eval(argExprs.get(0))));
            case "substring" -> StringAccessors.substring(
                    coerceToString(eval(argExprs.get(0))),
                    eval(argExprs.get(1)),
                    argExprs.size() > 2 ? eval(argExprs.get(2)) : null);
            case "lower" -> StringAccessors.lower(eval(argExprs.get(0)));
            case "upper" -> StringAccessors.upper(eval(argExprs.get(0)));
            case "trim" -> StringAccessors.trim(eval(argExprs.get(0)));
            case "indexOf" -> StringAccessors.indexOf(eval(argExprs.get(0)), eval(argExprs.get(1)));
            case "lastIndexOf" -> StringAccessors.lastIndexOf(eval(argExprs.get(0)), eval(argExprs.get(1)));
            case "charAt" -> StringAccessors.charAt(eval(argExprs.get(0)), eval(argExprs.get(1)));

            // ---- Fuzzy text --------------------------------------------
            case "levenshtein" -> FuzzyTextFunctions.levenshtein(eval(argExprs.get(0)), eval(argExprs.get(1)));
            case "jaroWinklerSimilarity" -> FuzzyTextFunctions.jaroWinklerSimilarity(eval(argExprs.get(0)), eval(argExprs.get(1)));

            // ---- Numeric / control-flow passthroughs (mirror SQL behaviour for Plan C) ----
            case "abs" -> Math.abs(Numbers.toLong(eval(argExprs.get(0))));
            case "ceil" -> (long) Math.ceil(Numbers.toDouble(eval(argExprs.get(0))));
            case "floor" -> (long) Math.floor(Numbers.toDouble(eval(argExprs.get(0))));
            case "round" -> {
                double v = Numbers.toDouble(eval(argExprs.get(0)));
                int digits = argExprs.size() > 1 ? ((Number) eval(argExprs.get(1))).intValue() : 0;
                double scale = Math.pow(10, digits);
                yield Math.round(v * scale) / scale;
            }
            case "mod" -> Numbers.toLong(eval(argExprs.get(0))) % Numbers.toLong(eval(argExprs.get(1)));
            case "power" -> Math.pow(Numbers.toDouble(eval(argExprs.get(0))), Numbers.toDouble(eval(argExprs.get(1))));
            case "sqrt" -> Math.sqrt(Numbers.toDouble(eval(argExprs.get(0))));
            case "coalesce" -> {
                for (OqlExpr a : argExprs) {
                    Object v = eval(a);
                    if (v != null) yield v;
                }
                yield null;
            }
            case "nullif" -> {
                Object a = eval(argExprs.get(0));
                Object b = eval(argExprs.get(1));
                yield (a != null && a.equals(b)) ? null : a;
            }
            case "least" -> reduce(argExprs, true);
            case "greatest" -> reduce(argExprs, false);
            case "format" -> {
                String template = String.valueOf(eval(argExprs.get(0)));
                Object[] args = new Object[argExprs.size() - 1];
                for (int i = 0; i < args.length; i++) args[i] = eval(argExprs.get(i + 1));
                yield applyTemplate(template, args);
            }

            default -> throw new UnsupportedOperationException(
                    "Function not implemented in Plan C: " + name);
        };
    }

    private Object reduce(List<OqlExpr> argExprs, boolean least) throws SQLException {
        Object best = null;
        for (OqlExpr a : argExprs) {
            Object v = eval(a);
            if (v == null) continue;
            if (best == null) {
                best = v;
                continue;
            }
            int cmp = Numbers.compare(v, best);
            if ((least && cmp < 0) || (!least && cmp > 0)) {
                best = v;
            }
        }
        return best;
    }

    /** Replaces {@code {}} placeholders left-to-right — SLF4J-style. */
    private static String applyTemplate(String template, Object[] args) {
        StringBuilder out = new StringBuilder(template.length() + 16);
        int argIdx = 0;
        for (int i = 0; i < template.length(); i++) {
            char c = template.charAt(i);
            if (c == '{' && i + 1 < template.length() && template.charAt(i + 1) == '}') {
                out.append(argIdx < args.length ? String.valueOf(args[argIdx++]) : "{}");
                i++;
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }

    private Object evalBinary(OqlExpr.BinaryOp bop) throws SQLException {
        // Short-circuit AND / OR
        if (bop.op() == BinaryOperator.AND) {
            return Numbers.truthy(eval(bop.left())) && Numbers.truthy(eval(bop.right()));
        }
        if (bop.op() == BinaryOperator.OR) {
            return Numbers.truthy(eval(bop.left())) || Numbers.truthy(eval(bop.right()));
        }
        Object l = eval(bop.left());
        Object r = eval(bop.right());
        return switch (bop.op()) {
            case EQ -> equalsOp(l, r);
            case NEQ -> !equalsOp(l, r);
            case LT -> compareSafe(l, r) < 0;
            case LTE -> compareSafe(l, r) <= 0;
            case GT -> compareSafe(l, r) > 0;
            case GTE -> compareSafe(l, r) >= 0;
            case LIKE -> {
                if (l == null || r == null) yield null;
                yield Pattern.compile(String.valueOf(r)).matcher(String.valueOf(l)).matches();
            }
            case ADD -> Numbers.add(l, r);
            case SUB -> Numbers.sub(l, r);
            case MUL -> Numbers.mul(l, r);
            case DIV -> Numbers.div(l, r);
            default -> throw new UnsupportedOperationException("Unsupported binary op: " + bop.op());
        };
    }

    private static boolean equalsOp(Object l, Object r) {
        if (l == null || r == null) return false;
        if (Numbers.isNumber(l) && Numbers.isNumber(r)) {
            if (Numbers.isFractional(l, r)) {
                return Numbers.toDouble(l) == Numbers.toDouble(r);
            }
            return Numbers.toLong(l) == Numbers.toLong(r);
        }
        return l.equals(r);
    }

    private static int compareSafe(Object l, Object r) {
        if (l == null || r == null) {
            return Integer.MIN_VALUE; // any comparison with null short-circuits the predicate as false
        }
        return Numbers.compare(l, r);
    }

    private Object evalUnary(OqlExpr.UnaryOp uop) throws SQLException {
        Object v = eval(uop.operand());
        return switch (uop.op()) {
            case NOT -> !Numbers.truthy(v);
            case NEG -> v == null ? null : Numbers.sub(0L, v);
        };
    }

    private Object evalIn(OqlExpr.InOp in) throws SQLException {
        Object left = eval(in.left());
        if (left == null) return null;
        boolean any = false;
        for (OqlExpr v : in.values()) {
            if (equalsOp(left, eval(v))) {
                any = true;
                break;
            }
        }
        return in.negate() ? !any : any;
    }

    private Object evalNullCheck(OqlExpr.NullCheck nc) throws SQLException {
        Object v = eval(nc.operand());
        boolean isNull = v == null;
        return nc.negate() ? !isNull : isNull;
    }

    private Object evalCase(OqlExpr.CaseExpr ce) throws SQLException {
        for (OqlExpr.CaseExpr.WhenClause w : ce.whens()) {
            if (Numbers.truthy(eval(w.condition()))) {
                return eval(w.result());
            }
        }
        return ce.elseExpr() == null ? null : eval(ce.elseExpr());
    }

    private InstanceRow asInstance(Object v) throws SQLException {
        if (v instanceof InstanceRow r) return r;
        if (v instanceof Long ref && ref != 0L) {
            return row.view().findInstanceById(ref).orElse(null);
        }
        return null;
    }

    /**
     * Coerces a value to a string for string-predicate/accessor inputs.
     * {@link InstanceRow}s are decoded via {@link StringFunctions#toStringValue}
     * so {@code startsWith(s, "java.")} works when {@code s} is bound to a
     * Java-heap {@code String} instance without an explicit {@code toString(s)}.
     */
    private String coerceToString(Object v) throws SQLException {
        if (v == null) return null;
        if (v instanceof String s) return s;
        if (v instanceof InstanceRow inst) {
            JavaClassRow c = inst.classId() == null
                    ? null
                    : row.view().findClassById(inst.classId()).orElse(null);
            return StringFunctions.toStringValue(row.view(), inst, c);
        }
        return v.toString();
    }

    @SuppressWarnings("unused")
    private static List<Object> evalArgs(List<Object> args) {
        return new ArrayList<>(args);
    }
}
