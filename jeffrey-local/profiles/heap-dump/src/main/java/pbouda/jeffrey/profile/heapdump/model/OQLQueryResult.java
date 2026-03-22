/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
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

package pbouda.jeffrey.profile.heapdump.model;

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
