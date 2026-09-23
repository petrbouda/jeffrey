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
package cafe.jeffrey.profile.mcp;

import java.util.List;

/**
 * The values a client may offer for one argument, and whether there were more than fit.
 * <p>
 * The protocol caps a single response at {@link #MAX_VALUES}; {@link #of(List)} applies the cap rather
 * than leaving each provider to remember it, because a provider that forgets produces a response the
 * client is entitled to reject.
 *
 * @param values  the candidate values, at most {@link #MAX_VALUES} of them
 * @param total   how many candidates existed before the cap
 * @param hasMore whether the cap dropped any
 */
public record McpCompletion(List<String> values, int total, boolean hasMore) {

    /** What one {@code completion/complete} response may carry, fixed by the specification. */
    public static final int MAX_VALUES = 100;

    public static final McpCompletion EMPTY = new McpCompletion(List.of(), 0, false);

    public McpCompletion {
        values = values == null ? List.of() : List.copyOf(values);
    }

    /** Caps a full candidate list and records what the cap cost. */
    public static McpCompletion of(List<String> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return EMPTY;
        }
        int total = candidates.size();
        if (total <= MAX_VALUES) {
            return new McpCompletion(candidates, total, false);
        }
        return new McpCompletion(candidates.subList(0, MAX_VALUES), total, true);
    }
}
