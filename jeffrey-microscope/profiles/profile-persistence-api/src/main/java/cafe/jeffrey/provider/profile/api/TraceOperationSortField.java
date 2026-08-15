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

package cafe.jeffrey.provider.profile.api;

/**
 * What an operation list can be ordered by.
 * <p>
 * Each constant carries an aggregate expression rather than a column: the operations list is a
 * {@code GROUP BY} over the traces table, so what it sorts on is computed, not stored. Closed for the
 * same reason {@link TraceSortField} is — this text reaches the {@code ORDER BY} directly.
 */
public enum TraceOperationSortField {

    TOTAL_TIME("total_ns"),
    P50("p50_ns"),
    P95("p95_ns"),
    P99("p99_ns"),
    MAX("max_ns"),
    COUNT("count"),
    ERRORS("error_count"),
    NAME("name");

    private final String expression;

    TraceOperationSortField(String expression) {
        this.expression = expression;
    }

    /**
     * The projected alias this sorts on. Safe to interpolate because every value is one of these
     * constants; nothing a caller typed ever reaches it.
     */
    public String expression() {
        return expression;
    }
}
