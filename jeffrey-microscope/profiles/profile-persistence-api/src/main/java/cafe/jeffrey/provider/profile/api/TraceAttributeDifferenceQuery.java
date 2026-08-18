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

/**
 * Which traces to treat as the selection when ranking what is different about them. Everything else
 * in the profile is the baseline.
 * <p>
 * The selection is defined by properties of the trace rather than by an attribute filter on purpose:
 * this is the screen for when there is no theory yet, and starting from an attribute would assume
 * the answer.
 *
 * @param minDurationNanos      keep traces at least this long; {@code 0} keeps every duration
 * @param errorsOnly            keep only traces with at least one failed span
 * @param minSelectionTraces    how many traces of the selection a value has to appear in before it
 *                              is ranked at all — without a floor, a value seen three times at 100%
 *                              outranks everything real
 * @param minLift               how much more common a value has to be inside the selection than
 *                              outside it; at 1.0 every value in the profile is "a difference"
 * @param maxDistinctValues     skip keys with more values than this: a key with a value per trace
 *                              produces one row per trace, each of them a perfect and meaningless
 *                              correlation
 * @param numericBucketAbove    values of a numeric key are bucketed to their order of magnitude once
 *                              the key has more distinct values than this, so {@code rows} ranks as
 *                              {@code >= 10000} rather than as ten thousand rows of one trace each
 * @param limit                 how many differences to return
 */
public record TraceAttributeDifferenceQuery(
        long minDurationNanos,
        boolean errorsOnly,
        int minSelectionTraces,
        double minLift,
        long maxDistinctValues,
        long numericBucketAbove,
        int limit) {

    public TraceAttributeDifferenceQuery {
        if (minDurationNanos < 0) {
            throw new IllegalArgumentException("minDurationNanos must not be negative: " + minDurationNanos);
        }
        if (minSelectionTraces < 1) {
            throw new IllegalArgumentException("minSelectionTraces must be at least 1: " + minSelectionTraces);
        }
        if (limit < 1) {
            throw new IllegalArgumentException("limit must be at least 1: " + limit);
        }
    }
}
