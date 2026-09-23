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

import java.util.List;

/**
 * SELECT clause: optional DISTINCT/AS-RETAINED-SET modifier plus a list of
 * projections. {@link SelectClause#STAR_PROJECTION} represents {@code SELECT *}.
 */
public record SelectClause(SelectModifier modifier, List<Projection> projections) {

    public static final List<Projection> STAR_PROJECTION =
            List.of(new Projection(null, null, true, false));

    public SelectClause {
        modifier = modifier == null ? SelectModifier.NONE : modifier;
        if (projections == null || projections.isEmpty()) {
            throw new IllegalArgumentException("select must have at least one projection");
        }
        projections = List.copyOf(projections);
    }

    public enum SelectModifier {
        NONE,
        DISTINCT,
        AS_RETAINED_SET
    }

    /**
     * A single SELECT-list item.
     *
     * @param expr     the expression to evaluate (null iff {@code star} is true)
     * @param alias    explicit {@code AS alias} (may be null)
     * @param star     true when this is the {@code *} projection
     * @param objects  true when {@code OBJECTS} prefix was used
     *                 (advisory only — the engine treats this as a no-op
     *                 because all expressions already evaluate to object refs
     *                 or scalar values without further mediation)
     */
    public record Projection(OqlExpr expr, String alias, boolean star, boolean objects) {

        public Projection {
            if (star && expr != null) {
                throw new IllegalArgumentException("star projection must not carry an expression");
            }
            if (!star && expr == null) {
                throw new IllegalArgumentException("non-star projection must carry an expression");
            }
        }
    }
}
