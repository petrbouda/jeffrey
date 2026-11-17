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

package cafe.jeffrey.profile.manager.model.gc.tuning;

import cafe.jeffrey.timeseries.TimeseriesData;

import java.util.List;

/**
 * G1 IHOP and GC CPU insight for the GC deep-tuning tab.
 *
 * @param ihopTimeline IHOP threshold vs current old-gen occupancy over the recording
 *                     (from {@code jdk.G1AdaptiveIHOP}); explains when and why concurrent
 *                     cycles start
 * @param cpuTimes     per-collection CPU cost (from {@code jdk.GCCPUTime}), most recent first
 * @param mmu          minimum-mutator-utilisation samples (from {@code jdk.G1MMU}): GC time vs the
 *                     pause target per collection, most recent first
 */
public record IhopData(TimeseriesData ihopTimeline, List<GcCpuEntry> cpuTimes, List<MmuEntry> mmu) {

    /**
     * CPU time of one garbage collection. Durations are in nanoseconds; a missing component is 0.
     */
    public record GcCpuEntry(long gcId, long userNanos, long systemNanos, long realNanos) {
    }

    /**
     * Pause-target adherence for one collection: how long GC ran within the MMU time slice versus
     * the configured pause target. {@code gcTimeNanos > pauseTargetNanos} means the target was missed.
     * {@code timeOffsetMillis} is the collection's offset from the recording start, used to place a
     * missed-target marker on the IHOP timeline.
     */
    public record MmuEntry(long gcId, long gcTimeNanos, long pauseTargetNanos, long timeOffsetMillis) {
    }
}
