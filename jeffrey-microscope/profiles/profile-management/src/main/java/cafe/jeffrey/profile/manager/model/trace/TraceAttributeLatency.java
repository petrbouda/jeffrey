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

package cafe.jeffrey.profile.manager.model.trace;

import java.util.List;

/**
 * How each of a key's values is distributed over trace duration — the latency heatmap.
 * <p>
 * Percentiles hide bimodality: a value whose traces are either fast or catastrophic has the same
 * median as one that is uniformly mediocre, and it is the first that is worth looking at. The grid
 * shows the difference at a glance.
 *
 * @param cells     the populated cells; empty ones are left out, and the caller fills a fixed grid
 * @param minBucket the first bucket of that grid
 * @param maxBucket the last of it — traces beyond either end are clamped into them, so the grid
 *                  still accounts for every trace it claims to cover
 */
public record TraceAttributeLatency(List<Cell> cells, int minBucket, int maxBucket) {

    /**
     * One cell.
     *
     * @param bucket a half-decade of nanoseconds: the bucket covers
     *               {@code [10^(bucket/2), 10^((bucket+1)/2))} ns, which the caller renders as a
     *               duration range. Half-decades rather than decades because a decade cannot
     *               separate a 12 ms population from a 90 ms one, and those are exactly the two a
     *               reader is trying to tell apart.
     */
    public record Cell(String value, int bucket, long traceCount) {
    }
}
