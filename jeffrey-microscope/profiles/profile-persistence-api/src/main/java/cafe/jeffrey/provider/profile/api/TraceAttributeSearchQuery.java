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
