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

package cafe.jeffrey.profile.manager.model.gc.zgc;

import cafe.jeffrey.timeseries.TimeseriesData;

import java.util.List;

/**
 * ZGC (generational) deep-dive insight built from the ZGC-specific JFR events. The headline
 * signal is allocation stalls — the only place where the otherwise-concurrent collector becomes
 * visible to application threads.
 *
 * @param header        high-level counters (cycles, allocation-stall totals, pages, uncommit)
 * @param stallTimeline allocation-stall count and total stall time per second ({@code jdk.ZAllocationStall})
 * @param stallTypes    stall breakdown by page type (Small/Medium/Large)
 * @param stallSites    stalling threads ranked by total stall time
 * @param cycles        young/old collection cycles ({@code jdk.ZYoungGarbageCollection}, {@code jdk.ZOldGarbageCollection})
 * @param pageAllocation page-allocation throughput over time ({@code jdk.ZPageAllocation})
 * @param uncommits     memory returned to the OS ({@code jdk.ZUncommit})
 * @param relocations   relocation-set sizes per cycle ({@code jdk.ZRelocationSet})
 */
public record ZgcAnalysisData(
        ZgcHeader header,
        TimeseriesData stallTimeline,
        List<StallType> stallTypes,
        List<StallSite> stallSites,
        List<ZCycle> cycles,
        TimeseriesData pageAllocation,
        List<ZUncommitEntry> uncommits,
        List<ZRelocationEntry> relocations) {

    public record ZgcHeader(
            long youngCycles,
            long oldCycles,
            long stallCount,
            long totalStallNanos,
            long maxStallNanos,
            long pagesAllocatedBytes,
            long uncommittedBytes) {
    }

    public record StallType(String type, long count, long totalNanos, long maxNanos) {
    }

    public record StallSite(String threadName, long count, long totalNanos) {
    }

    public record ZCycle(long gcId, String generation, long durationNanos, int tenuringThreshold) {
    }

    public record ZUncommitEntry(long timeOffsetMillis, long uncommittedBytes, long durationNanos) {
    }

    public record ZRelocationEntry(long timeOffsetMillis, long total, long empty, long relocate) {
    }
}
