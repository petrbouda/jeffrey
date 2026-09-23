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

/**
 * Request to execute an OQL query.
 *
 * @param query               the OQL query to execute
 * @param limit               maximum number of results to return
 * @param offset              number of results to skip (for pagination)
 * @param includeRetainedSize whether to calculate retained heap size for each result
 * @param scanLargeStrings    whether to also scan {@code java.lang.String} instances whose
 *                            decoded content exceeded the indexer's content cap (the
 *                            {@code string_content.content IS NULL} rows). Off by default —
 *                            the SQL pushdown path is fast and covers all in-cap Strings;
 *                            flip this on when the target Strings might be large.
 */
public record OQLQueryRequest(
        String query,
        int limit,
        int offset,
        boolean includeRetainedSize,
        boolean scanLargeStrings
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
     * Backwards-compatible four-arg constructor — existing callers default
     * {@code scanLargeStrings} to false.
     */
    public OQLQueryRequest(String query, int limit, int offset, boolean includeRetainedSize) {
        this(query, limit, offset, includeRetainedSize, false);
    }

    /**
     * Create a request with default limit, offset, and no retained size calculation.
     */
    public OQLQueryRequest(String query) {
        this(query, DEFAULT_LIMIT, DEFAULT_OFFSET, false, false);
    }
}
