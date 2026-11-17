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
 * How a caller wants one key's values ranked.
 *
 * @param key        which key to break down
 * @param sort       which column ranks the values
 * @param descending whether that column runs high-to-low
 * @param limit      how many values to return; the caller is told the key's true cardinality
 *                   separately, so a truncated list can say it is one
 * @param eventType  which event type's spans to read the key on, or null for every span carrying it.
 *                   The picker reaches a key through an event type, and a breakdown that ignored it
 *                   would answer a wider question than the one asked — the same key on a different
 *                   type is a different set of values
 */
public record TraceAttributeValueQuery(
        TraceAttributeKeyId key,
        TraceAttributeValueSortField sort,
        boolean descending,
        int limit,
        String eventType) {

    public TraceAttributeValueQuery {
        Objects.requireNonNull(key, "key must not be null");
        Objects.requireNonNull(sort, "sort must not be null");
        if (limit < 1) {
            throw new IllegalArgumentException("limit must be at least 1: " + limit);
        }
    }
}
