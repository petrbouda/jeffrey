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
