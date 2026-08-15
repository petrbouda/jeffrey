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
 * How a caller wants the trace list narrowed, ordered and paged.
 * <p>
 * One record rather than seven parameters: the list is read from three places, and every one of them
 * has to say the same seven things. It also keeps the defaults in one spot — {@link #slowest(int)} is
 * what every caller wanting "the slowest N, unfiltered" asks for, which is the shape the list had
 * before there was anything to narrow it by.
 *
 * @param nameContains     matched against the root span's name, case-insensitively, as a substring;
 *                         {@code null} or blank matches everything
 * @param errorsOnly       keep only traces with at least one failed span
 * @param minDurationNanos keep only traces at least this long; {@code 0} keeps everything
 * @param sort             which column orders the result
 * @param descending       whether that column runs high-to-low
 * @param limit            how many rows to return; at least 1
 * @param offset           how many rows to skip first, so a caller can page past {@code limit}
 */
public record TraceListQuery(
        String nameContains,
        boolean errorsOnly,
        long minDurationNanos,
        TraceSortField sort,
        boolean descending,
        int limit,
        int offset) {

    public TraceListQuery {
        Objects.requireNonNull(sort, "sort must not be null");
        if (limit < 1) {
            throw new IllegalArgumentException("limit must be at least 1: " + limit);
        }
        if (offset < 0) {
            throw new IllegalArgumentException("offset must not be negative: " + offset);
        }
        if (minDurationNanos < 0) {
            throw new IllegalArgumentException(
                    "minDurationNanos must not be negative: " + minDurationNanos);
        }
    }

    /** The unfiltered slowest-first list the trace views opened with before filters existed. */
    public static TraceListQuery slowest(int limit) {
        return new TraceListQuery(null, false, 0, TraceSortField.DURATION, true, limit, 0);
    }

    /**
     * Whether a name filter was actually given. A blank string is treated as absent: it arrives from
     * an empty search box, where narrowing to "names containing nothing" is never what was meant.
     */
    public boolean hasNameFilter() {
        return nameContains != null && !nameContains.isBlank();
    }
}
