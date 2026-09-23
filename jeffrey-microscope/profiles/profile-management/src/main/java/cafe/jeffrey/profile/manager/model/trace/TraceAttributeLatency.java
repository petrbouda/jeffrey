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
