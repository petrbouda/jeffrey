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
 * @param pinningReasons      pinning incidents grouped by reported reason ({@code pinnedReason}, JDK 26+)
 * @param submitFailures      carrier-submit failures ({@code jdk.VirtualThreadSubmitFailed})
 * @param lifecycle           per-second started / ended / live counts
 *                            ({@code jdk.VirtualThreadStart}/{@code jdk.VirtualThreadEnd}); empty unless enabled
 */
public record VirtualThreadData(
        VtHeader header,
        TimeseriesData pinningTimeline,
        List<DurationBucket> pinningDistribution,
        List<PinnedThreadStat> topPinnedThreads,
        List<PinningReasonStat> pinningReasons,
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

    public record PinningReasonStat(String reason, long count, long totalNanos, long maxNanos) {
    }

    public record SubmitFailure(long timeOffsetMillis, String threadName, String exceptionMessage) {
    }
}
