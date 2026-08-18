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
 * How a caller wants one key's values ranked.
 *
 * @param key        which key to break down
 * @param sort       which column ranks the values
 * @param descending whether that column runs high-to-low
 * @param limit      how many values to return; the caller is told the key's true cardinality
 *                   separately, so a truncated list can say it is one
 */
public record TraceAttributeValueQuery(
        TraceAttributeKeyId key,
        TraceAttributeValueSortField sort,
        boolean descending,
        int limit) {

    public TraceAttributeValueQuery {
        Objects.requireNonNull(key, "key must not be null");
        Objects.requireNonNull(sort, "sort must not be null");
        if (limit < 1) {
            throw new IllegalArgumentException("limit must be at least 1: " + limit);
        }
    }
}
