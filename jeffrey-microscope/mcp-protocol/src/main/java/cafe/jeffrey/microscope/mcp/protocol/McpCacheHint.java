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

package cafe.jeffrey.microscope.mcp.protocol;

import tools.jackson.databind.node.ObjectNode;

import java.util.Objects;

/**
 * How long a client may reuse a {@code complete} result, and with whom.
 * <p>
 * {@code 2026-07-28} requires the pair on {@code server/discover}, the four list methods and
 * {@code resources/read}, and forbids it on {@code input_required}.
 *
 * @param ttlMs how long the result stays fresh, in milliseconds; zero means "do not reuse"
 * @param scope who may share a cached copy; {@link Scope#NONE} writes no hint at all
 */
public record McpCacheHint(long ttlMs, Scope scope) {

    /**
     * Lists that are fixed for the life of the process. One hour rather than longer, so a restart with
     * other advertised families is noticed within the hour.
     */
    public static final McpCacheHint STATIC = new McpCacheHint(3_600_000L, Scope.PUBLIC);

    /** A read of something that can change or disappear, so never reuse it. */
    public static final McpCacheHint DYNAMIC = new McpCacheHint(0L, Scope.PRIVATE);

    /** No hint: a result the specification does not ask one for ({@code tools/call}, {@code prompts/get}). */
    public static final McpCacheHint NONE = new McpCacheHint(0L, Scope.NONE);

    private static final String FIELD_TTL_MS = "ttlMs";
    private static final String FIELD_CACHE_SCOPE = "cacheScope";

    public McpCacheHint {
        Objects.requireNonNull(scope, "scope");
        if (ttlMs < 0) {
            throw new IllegalArgumentException("A cache lifetime must not be negative: " + ttlMs);
        }
        if (scope == Scope.NONE && ttlMs != 0) {
            throw new IllegalArgumentException("No cache hint carries no lifetime: " + ttlMs);
        }
    }

    public boolean present() {
        return scope != Scope.NONE;
    }

    /** Adds {@code ttlMs} and {@code cacheScope} to a result, or nothing for {@link #NONE}. */
    public void writeTo(ObjectNode result) {
        if (!present()) {
            return;
        }
        result.put(FIELD_TTL_MS, ttlMs);
        result.put(FIELD_CACHE_SCOPE, scope.wireName());
    }

    public enum Scope {
        PUBLIC("public"),
        PRIVATE("private"),
        NONE(null);

        private final String wireName;

        Scope(String wireName) {
            this.wireName = wireName;
        }

        String wireName() {
            return wireName;
        }
    }
}
