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
