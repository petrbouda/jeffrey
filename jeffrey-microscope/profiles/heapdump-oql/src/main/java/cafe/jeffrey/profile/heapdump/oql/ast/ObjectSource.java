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

import cafe.jeffrey.profile.heapdump.oql.ast.OqlStatement.OqlQuery;

import java.util.List;

/**
 * Origin of the rows that the FROM clause iterates over.
 *
 * <ul>
 *   <li>{@link ClassSource} — a Java class name (qualified, may include {@code []} dims).</li>
 *   <li>{@link RegexSource} — a quoted regex matched against class names.</li>
 *   <li>{@link SubquerySource} — a nested {@code (SELECT …)} producing object IDs.</li>
 *   <li>{@link FunctionSource} — a helper-function call like {@code heap.objects(...)} or
 *       {@code heap.classes()}; the compiler picks the right execution path.</li>
 * </ul>
 */
public sealed interface ObjectSource {

    record ClassSource(String className) implements ObjectSource {

        public ClassSource {
            if (className == null || className.isBlank()) {
                throw new IllegalArgumentException("class name must not be blank");
            }
        }
    }

    record RegexSource(String pattern) implements ObjectSource {

        public RegexSource {
            if (pattern == null) {
                throw new IllegalArgumentException("regex pattern must not be null");
            }
        }
    }

    record SubquerySource(OqlQuery query) implements ObjectSource {

        public SubquerySource {
            if (query == null) {
                throw new IllegalArgumentException("subquery must be present");
            }
        }
    }

    /**
     * A function-call source — typically a {@code heap.*} helper or a
     * top-level helper that returns a collection of instances. Compiler
     * dispatches based on {@code name}.
     */
    record FunctionSource(String name, List<OqlExpr> args) implements ObjectSource {

        public FunctionSource {
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("function name must not be blank");
            }
            args = args == null ? List.of() : List.copyOf(args);
        }
    }
}
