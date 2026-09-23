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
