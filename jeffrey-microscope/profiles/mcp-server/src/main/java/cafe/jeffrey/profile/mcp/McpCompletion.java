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
