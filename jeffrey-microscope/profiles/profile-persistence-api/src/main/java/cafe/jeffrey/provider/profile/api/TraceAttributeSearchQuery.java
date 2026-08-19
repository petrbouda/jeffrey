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

import java.util.List;
import java.util.Objects;

/**
 * How a caller wants traces narrowed by their spans' attributes, ordered and paged.
 * <p>
 * Conditions are ANDed. An empty list matches every trace, which is what the page opens with before
 * anything has been typed — the same shape as the unfiltered trace list, so the two agree.
 *
 * @param conditions what has to hold, all of it
 * @param scope      whether the conditions have to hold on one span or anywhere in the trace
 * @param sort       which column orders the result
 * @param descending whether that column runs high-to-low
 * @param limit      how many rows to return; at least 1
 * @param offset     how many rows to skip first
 */
public record TraceAttributeSearchQuery(
        List<TraceAttributeCondition> conditions,
        TraceAttributeScope scope,
        TraceSortField sort,
        boolean descending,
        int limit,
        int offset) {

    public TraceAttributeSearchQuery {
        Objects.requireNonNull(conditions, "conditions must not be null");
        Objects.requireNonNull(scope, "scope must not be null");
        Objects.requireNonNull(sort, "sort must not be null");
        conditions = List.copyOf(conditions);
        if (limit < 1) {
            throw new IllegalArgumentException("limit must be at least 1: " + limit);
        }
        if (offset < 0) {
            throw new IllegalArgumentException("offset must not be negative: " + offset);
        }
    }

    /** Whether anything narrows the result at all. */
    public boolean isUnfiltered() {
        return conditions.isEmpty();
    }
}
