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
 * Top-level OQL statement. Either a single query or a chain joined with
 * {@code UNION}. Produced by {@code OqlParser} after parsing and desugaring.
 */
public sealed interface OqlStatement {

    /** A single SELECT … FROM … query, possibly with WHERE/GROUP BY/etc. */
    record OqlQuery(
            SelectClause select,
            FromClause from,
            OqlExpr whereExpr,
            List<OqlExpr> groupBy,
            OqlExpr having,
            List<OrderItem> orderBy,
            LimitClause limit
    ) implements OqlStatement {

        public OqlQuery {
            if (select == null) {
                throw new IllegalArgumentException("select clause must be present");
            }
            if (from == null) {
                throw new IllegalArgumentException("from clause must be present");
            }
            groupBy = groupBy == null ? List.of() : List.copyOf(groupBy);
            orderBy = orderBy == null ? List.of() : List.copyOf(orderBy);
        }
    }

    /**
     * {@code (SELECT ...) UNION (SELECT ...) UNION (...)} — wrapped by the
     * parser when the input contains a top-level UNION. Single queries land
     * as {@link OqlQuery} directly.
     */
    record UnionQuery(List<OqlQuery> branches) implements OqlStatement {

        public UnionQuery {
            if (branches == null || branches.size() < 2) {
                throw new IllegalArgumentException("UNION requires at least two branches");
            }
            branches = List.copyOf(branches);
        }
    }
}
