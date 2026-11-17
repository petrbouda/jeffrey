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

import java.util.Objects;

/**
 * How a caller wants the operations list narrowed, ordered and paged.
 * <p>
 * One record rather than six parameters, which also keeps the defaults in one spot — see
 * {@link #busiest(int)}. It has no duration floor: an operation's duration is a distribution rather
 * than a number, so "at least this long" would have to name which percentile it meant, and the sort
 * already answers that question better.
 *
 * @param nameContains matched against the operation's name, case-insensitively, as a substring;
 *                     {@code null} or blank matches everything
 * @param errorsOnly   keep only operations that failed at least once
 * @param sort         which aggregate orders the result
 * @param descending   whether that aggregate runs high-to-low
 * @param limit        how many rows to return; at least 1
 * @param offset       how many rows to skip first
 */
public record TraceOperationListQuery(
        String nameContains,
        boolean errorsOnly,
        TraceOperationSortField sort,
        boolean descending,
        int limit,
        int offset) {

    public TraceOperationListQuery {
        Objects.requireNonNull(sort, "sort must not be null");
        if (limit < 1) {
            throw new IllegalArgumentException("limit must be at least 1: " + limit);
        }
        if (offset < 0) {
            throw new IllegalArgumentException("offset must not be negative: " + offset);
        }
    }

    /** The unfiltered, busiest-first list the operations view opened with before filters existed. */
    public static TraceOperationListQuery busiest(int limit) {
        return new TraceOperationListQuery(
                null, false, TraceOperationSortField.TOTAL_TIME, true, limit, 0);
    }

    /** Whether a name filter was actually given; a blank search box is not one. */
    public boolean hasNameFilter() {
        return nameContains != null && !nameContains.isBlank();
    }
}
