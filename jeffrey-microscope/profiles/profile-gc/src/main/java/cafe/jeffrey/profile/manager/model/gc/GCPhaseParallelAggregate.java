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

package cafe.jeffrey.profile.manager.model.gc;

/**
 * Aggregated timing for one parallel GC sub-phase ({@code jdk.GCPhaseParallel}), summed across all GC
 * worker threads and all collections — e.g. "Ext Root Scanning", "Object Copy", "Termination".
 *
 * @param name          the sub-phase name
 * @param count         number of (worker, collection) samples for this phase
 * @param totalNanos    summed duration across all samples
 * @param avgNanos      mean sample duration
 * @param maxNanos      slowest single sample
 * @param percentOfTotal share of total parallel sub-phase time
 */
public record GCPhaseParallelAggregate(
        String name,
        long count,
        long totalNanos,
        long avgNanos,
        long maxNanos,
        double percentOfTotal) {
}
