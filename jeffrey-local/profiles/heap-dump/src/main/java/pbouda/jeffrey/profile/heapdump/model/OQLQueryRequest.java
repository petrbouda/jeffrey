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

/**
 * Request to execute an OQL query.
 *
 * @param query               the OQL query to execute
 * @param limit               maximum number of results to return
 * @param offset              number of results to skip (for pagination)
 * @param includeRetainedSize whether to calculate retained heap size for each result
 */
public record OQLQueryRequest(
        String query,
        int limit,
        int offset,
        boolean includeRetainedSize
) {
    private static final int DEFAULT_LIMIT = 100;
    private static final int DEFAULT_OFFSET = 0;

    public OQLQueryRequest {
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("Query must not be null or blank");
        }
        if (limit <= 0) {
            limit = DEFAULT_LIMIT;
        }
        if (offset < 0) {
            offset = DEFAULT_OFFSET;
        }
    }

    /**
     * Create a request with default limit, offset, and no retained size calculation.
     */
    public OQLQueryRequest(String query) {
        this(query, DEFAULT_LIMIT, DEFAULT_OFFSET, false);
    }
}
