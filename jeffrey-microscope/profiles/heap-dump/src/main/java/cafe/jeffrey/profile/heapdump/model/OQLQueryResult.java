/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
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

package cafe.jeffrey.profile.heapdump.model;

import java.util.List;

/**
 * Result of an OQL query execution.
 *
 * @param results         list of query results (serialized to strings or objects)
 * @param totalCount      total number of results (before pagination)
 * @param hasMore         true if there are more results beyond the current page
 * @param executionTimeMs time taken to execute the query in milliseconds
 * @param errorMessage    error message if the query failed, null otherwise
 */
public record OQLQueryResult(
        List<OQLResultEntry> results,
        int totalCount,
        boolean hasMore,
        long executionTimeMs,
        String errorMessage
) {
    /**
     * Create a successful result.
     */
    public static OQLQueryResult success(List<OQLResultEntry> results, int totalCount, boolean hasMore, long executionTimeMs) {
        return new OQLQueryResult(results, totalCount, hasMore, executionTimeMs, null);
    }

    /**
     * Create an error result.
     */
    public static OQLQueryResult error(String errorMessage, long executionTimeMs) {
        return new OQLQueryResult(List.of(), 0, false, executionTimeMs, errorMessage);
    }

    /**
     * Check if the query was successful.
     */
    public boolean isSuccess() {
        return errorMessage == null;
    }
}
