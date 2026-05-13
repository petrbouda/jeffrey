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

import cafe.jeffrey.profile.heapdump.oql.ast.OqlStatement.OqlQuery;

import java.util.List;

/**
 * Compiled representation of an OQL statement ready to execute.
 *
 * <ul>
 *   <li>{@link SqlPlan} — emit one DuckDB SQL string with bound parameters (Plan A).</li>
 *   <li>{@link HybridPlan} — run a Java pre-pass to collect identifiers, then a SQL filter (Plan B).</li>
 *   <li>{@link JavaPlan} — run entirely in Java; reserved for Plan C work in Phase 3.</li>
 * </ul>
 */
public sealed interface ExecutionPlan {

    /** True iff the plan needs the dominator tree to be built before execution. */
    boolean needsDominatorTree();

    /**
     * Mapping from result-set column index (0-based) to the kind of value
     * the column carries — used by the result mapper to pick out the
     * instance id, class name, and size columns.
     */
    ResultShape resultShape();

    record SqlPlan(String sql, List<Object> params, boolean needsDominatorTree, ResultShape resultShape) implements ExecutionPlan {

        public SqlPlan {
            if (sql == null || sql.isBlank()) {
                throw new IllegalArgumentException("SQL must not be blank");
            }
            params = params == null ? List.of() : List.copyOf(params);
            if (resultShape == null) {
                resultShape = ResultShape.empty();
            }
        }
    }

    record HybridPlan(PrePass prePass, SqlPlan sqlPlan) implements ExecutionPlan {

        public HybridPlan {
            if (prePass == null || sqlPlan == null) {
                throw new IllegalArgumentException("HybridPlan requires pre-pass and sqlPlan");
            }
        }

        @Override
        public boolean needsDominatorTree() {
            return sqlPlan.needsDominatorTree();
        }

        @Override
        public ResultShape resultShape() {
            return sqlPlan.resultShape();
        }
    }

    /**
     * Plan C: Java-side row-by-row evaluation. The executor iterates candidate
     * instances (single class or {@link PrePass.ClassHierarchyExpansion}),
     * evaluates WHERE in Java, projects SELECT in Java, applies ORDER BY/LIMIT
     * after materializing surviving rows.
     *
     * <p>{@code reason} is non-null only when the compiler refused the query
     * outright (e.g. aggregates combined with Plan-C-only expressions); the
     * executor surfaces it as an error to the user. {@code query} is null in
     * that case.
     */
    record JavaPlan(OqlQuery query,
                    PrePass expansion,
                    boolean needsDominatorTree,
                    String reason,
                    ResultShape resultShape) implements ExecutionPlan {
    }

    /**
     * Plan A SQL pushdown for string predicates on a {@code java.lang.String}
     * binding, paired with a Plan-C fallback over Strings whose decoded
     * content exceeded the indexer's content cap. Only built when the user
     * has opted in via {@code scanLargeStrings} AND the inner SQL plan
     * actually JOINs the {@code string_content} table.
     */
    record StringFallbackPlan(SqlPlan primary, OqlQuery query) implements ExecutionPlan {

        public StringFallbackPlan {
            if (primary == null || query == null) {
                throw new IllegalArgumentException("primary and query must be present");
            }
        }

        @Override
        public boolean needsDominatorTree() {
            return primary.needsDominatorTree();
        }

        @Override
        public ResultShape resultShape() {
            return primary.resultShape();
        }
    }

    /**
     * Post-pass wrapper: runs {@code inner} to collect a seed set of instance
     * IDs, then expands that set by BFS over the dominator tree to produce
     * the full retained set. Triggered by {@code AS RETAINED SET}.
     */
    record RetainedSetPlan(ExecutionPlan inner) implements ExecutionPlan {

        public RetainedSetPlan {
            if (inner == null) {
                throw new IllegalArgumentException("inner plan must be present");
            }
        }

        @Override
        public boolean needsDominatorTree() {
            return true;
        }

        @Override
        public ResultShape resultShape() {
            return inner.resultShape();
        }
    }

    /** Pre-pass step that produces values to be substituted into the SQL plan's parameters. */
    sealed interface PrePass {

        /**
         * Walks the class hierarchy starting from {@code rootClassName} and
         * accumulates every descendant {@code class_id}. The collected list
         * substitutes the {@code :classIds} placeholder in the SQL plan.
         */
        record ClassHierarchyExpansion(String rootClassName, boolean isInterface) implements PrePass {

            public ClassHierarchyExpansion {
                if (rootClassName == null || rootClassName.isBlank()) {
                    throw new IllegalArgumentException("rootClassName must not be blank");
                }
            }
        }
    }
}
