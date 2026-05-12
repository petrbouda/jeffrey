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

/**
 * A single navigation step inside a {@link OqlExpr.PathExpr}.
 *
 * <p>Two flavours: dot-access ({@code .field}) and index-access ({@code [i]}).
 * The two are intermixed inside a path so {@code m.table[3].key} parses to a
 * three-segment chain.
 */
public sealed interface PathSegment {

    /** {@code .fieldName} — heap-field access on the preceding instance. */
    record Field(String name) implements PathSegment {

        public Field {
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("field name must not be blank");
            }
        }
    }

    /**
     * {@code .@attributeName} — built-in bean-attribute access. The compiler
     * resolves these to columns or computed values (e.g.
     * {@code o.@retainedHeapSize} → JOIN on {@code retained_size}).
     */
    record AttrField(String name) implements PathSegment {

        public AttrField {
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("attribute name must not be blank");
            }
        }
    }

    /** {@code [indexExpr]} — the index expression is evaluated lazily per row. */
    record Index(OqlExpr index) implements PathSegment {

        public Index {
            if (index == null) {
                throw new IllegalArgumentException("index expression must be present");
            }
        }
    }
}
