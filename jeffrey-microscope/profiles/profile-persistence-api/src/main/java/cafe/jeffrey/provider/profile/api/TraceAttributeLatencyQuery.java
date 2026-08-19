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
