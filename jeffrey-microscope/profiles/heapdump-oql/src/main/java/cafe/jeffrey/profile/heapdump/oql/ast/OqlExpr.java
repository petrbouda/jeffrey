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
package cafe.jeffrey.profile.heapdump.oql.ast;

import java.util.List;

/**
 * Root of the expression node hierarchy. Sealed so the compiler can use
 * exhaustive pattern matching to dispatch SQL emission vs Java evaluation.
 *
 * <p>Each node carries a {@link OqlType} populated by {@code OqlTypeResolver}
 * after parsing. Until resolution, types are {@link OqlType#UNKNOWN}.
 */
public sealed interface OqlExpr {

    OqlType type();

    /**
     * Returns a copy of this expression with the given type tag. Used by the
     * type resolver to propagate types upward through the tree.
     */
    OqlExpr withType(OqlType type);

    // ---- Leaves -------------------------------------------------------

    /** Numeric, string, boolean, or null literal. */
    record Literal(Object value, OqlType type) implements OqlExpr {

        public Literal {
            if (type == null) {
                throw new IllegalArgumentException("literal type must be present");
            }
        }

        @Override
        public Literal withType(OqlType newType) {
            return new Literal(value, newType);
        }
    }

    /** {@code @attributeName} — a built-in attribute reference like {@code @retainedHeapSize}. */
    record AttrRef(String name, OqlType type) implements OqlExpr {

        public AttrRef {
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("attribute name must not be blank");
            }
        }

        @Override
        public AttrRef withType(OqlType newType) {
            return new AttrRef(name, newType);
        }
    }

    /**
     * Reference to a bound identifier from a FROM-clause alias or a subquery
     * column. Standalone {@code SELECT o ...} compiles to this.
     */
    record BindingRef(String name, OqlType type) implements OqlExpr {

        public BindingRef {
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("binding name must not be blank");
            }
        }

        @Override
        public BindingRef withType(OqlType newType) {
            return new BindingRef(name, newType);
        }
    }

    // ---- Composite ----------------------------------------------------

    /**
     * Path expression: a root expression followed by an ordered chain of
     * field navigations ({@code .field}) and array indexings ({@code [idx]}).
     * The two segment kinds are intermixed in {@code segments}.
     */
    record PathExpr(OqlExpr root, List<PathSegment> segments, OqlType type) implements OqlExpr {

        public PathExpr {
            if (root == null) {
                throw new IllegalArgumentException("path root must be present");
            }
            if (segments == null || segments.isEmpty()) {
                throw new IllegalArgumentException("path expression must have at least one segment");
            }
            segments = List.copyOf(segments);
        }

        @Override
        public PathExpr withType(OqlType newType) {
            return new PathExpr(root, segments, newType);
        }
    }

    /**
     * Function or aggregate call. {@code star=true} represents {@code count(*)}
     * (only legal for aggregate functions; the type resolver enforces).
     */
    record FunctionCall(String name, List<OqlExpr> args, boolean star, OqlType type) implements OqlExpr {

        public FunctionCall {
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("function name must not be blank");
            }
            args = args == null ? List.of() : List.copyOf(args);
            if (star && !args.isEmpty()) {
                throw new IllegalArgumentException("star function call must not have arguments");
            }
        }

        @Override
        public FunctionCall withType(OqlType newType) {
            return new FunctionCall(name, args, star, newType);
        }
    }

    record BinaryOp(BinaryOperator op, OqlExpr left, OqlExpr right, OqlType type) implements OqlExpr {

        public BinaryOp {
            if (op == null || left == null || right == null) {
                throw new IllegalArgumentException("binary op requires operator and both sides");
            }
        }

        @Override
        public BinaryOp withType(OqlType newType) {
            return new BinaryOp(op, left, right, newType);
        }
    }

    /**
     * Used for the {@code IN (a, b, c)} family. The single {@code values} list
     * carries every right-hand-side operand; the binary operator carries the
     * polarity ({@link BinaryOperator#IN} vs {@link BinaryOperator#NOT_IN}).
     */
    record InOp(OqlExpr left, List<OqlExpr> values, boolean negate, OqlType type) implements OqlExpr {

        public InOp {
            if (left == null) {
                throw new IllegalArgumentException("IN requires a left side");
            }
            if (values == null || values.isEmpty()) {
                throw new IllegalArgumentException("IN requires at least one value");
            }
            values = List.copyOf(values);
        }

        @Override
        public InOp withType(OqlType newType) {
            return new InOp(left, values, negate, newType);
        }
    }

    /** {@code expr IS NULL} / {@code expr IS NOT NULL}. */
    record NullCheck(OqlExpr operand, boolean negate, OqlType type) implements OqlExpr {

        public NullCheck {
            if (operand == null) {
                throw new IllegalArgumentException("null-check requires an operand");
            }
        }

        @Override
        public NullCheck withType(OqlType newType) {
            return new NullCheck(operand, negate, newType);
        }
    }

    record UnaryOp(UnaryOperator op, OqlExpr operand, OqlType type) implements OqlExpr {

        public UnaryOp {
            if (op == null || operand == null) {
                throw new IllegalArgumentException("unary op requires operator and operand");
            }
        }

        @Override
        public UnaryOp withType(OqlType newType) {
            return new UnaryOp(op, operand, newType);
        }
    }

    /**
     * SQL-style {@code CASE WHEN c1 THEN r1 WHEN c2 THEN r2 ELSE e END}.
     * {@code elseExpr} may be null when the user omits {@code ELSE}.
     */
    record CaseExpr(List<WhenClause> whens, OqlExpr elseExpr, OqlType type) implements OqlExpr {

        public CaseExpr {
            if (whens == null || whens.isEmpty()) {
                throw new IllegalArgumentException("case expression requires at least one WHEN");
            }
            whens = List.copyOf(whens);
        }

        @Override
        public CaseExpr withType(OqlType newType) {
            return new CaseExpr(whens, elseExpr, newType);
        }

        public record WhenClause(OqlExpr condition, OqlExpr result) {

            public WhenClause {
                if (condition == null || result == null) {
                    throw new IllegalArgumentException("WHEN requires condition and result");
                }
            }
        }
    }

    /** Subquery used as a scalar/object-set expression, e.g. inside FROM or IN. */
    record SubqueryExpr(OqlStatement.OqlQuery query, OqlType type) implements OqlExpr {

        public SubqueryExpr {
            if (query == null) {
                throw new IllegalArgumentException("subquery must be present");
            }
        }

        @Override
        public SubqueryExpr withType(OqlType newType) {
            return new SubqueryExpr(query, newType);
        }
    }
}
