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

package cafe.jeffrey.profile.manager.model.virtualthread;

import cafe.jeffrey.timeseries.TimeseriesData;

import java.util.List;

/**
 * Virtual-thread insight for a single profile, built from the Loom JFR events.
 *
 * @param header             headline counters (pinning, submit failures, lifecycle)
 * @param pinningTimeline     pinning occurrences and total pinned time per second ({@code jdk.VirtualThreadPinned})
 * @param pinningDistribution pinning incidents bucketed by duration
 * @param topPinnedThreads    virtual threads ranked by total pinned time
 * @param submitFailures      carrier-submit failures ({@code jdk.VirtualThreadSubmitFailed})
 * @param lifecycle           per-second started / ended / live counts
 *                            ({@code jdk.VirtualThreadStart}/{@code jdk.VirtualThreadEnd}); empty unless enabled
 */
public record VirtualThreadData(
        VtHeader header,
        TimeseriesData pinningTimeline,
        List<DurationBucket> pinningDistribution,
        List<PinnedThreadStat> topPinnedThreads,
        List<SubmitFailure> submitFailures,
        TimeseriesData lifecycle) {

    public record VtHeader(
            long pinningCount,
            long totalPinnedNanos,
            long maxPinnedNanos,
            long submitFailedCount,
            long startedCount,
            long endedCount,
            long peakLiveCount) {
    }

    public record DurationBucket(String label, long count) {
    }

    public record PinnedThreadStat(String threadName, long count, long totalNanos, long maxNanos) {
    }

    public record SubmitFailure(long timeOffsetMillis, String threadName, String exceptionMessage) {
    }
}
