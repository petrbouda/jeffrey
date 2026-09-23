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
