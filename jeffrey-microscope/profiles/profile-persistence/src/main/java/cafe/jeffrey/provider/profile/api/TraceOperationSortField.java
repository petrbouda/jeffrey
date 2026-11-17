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
    NOTIFICATIONS("notification_count"),
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
