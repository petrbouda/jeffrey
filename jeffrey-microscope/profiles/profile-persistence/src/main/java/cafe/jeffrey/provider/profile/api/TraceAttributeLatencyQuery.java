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
 * How a caller wants one key's values spread over trace duration.
 *
 * @param key       which key to distribute
 * @param maxValues how many of the key's values to cover, most-carried first
 * @param eventType which event type's spans to read the key on, or null for every span carrying it —
 *                  the same scoping {@link TraceAttributeValueQuery} states, for the same reason
 */
public record TraceAttributeLatencyQuery(
        TraceAttributeKeyId key,
        int maxValues,
        String eventType) {

    public TraceAttributeLatencyQuery {
        Objects.requireNonNull(key, "key must not be null");
        if (maxValues < 1) {
            throw new IllegalArgumentException("maxValues must be at least 1: " + maxValues);
        }
    }
}
