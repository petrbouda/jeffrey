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

package cafe.jeffrey.profile.manager.model.gc.g1;

import cafe.jeffrey.profile.manager.model.gc.tuning.IhopData.MmuEntry;
import cafe.jeffrey.timeseries.TimeseriesData;

import java.util.List;

/**
 * G1 collector deep-dive insight built from the full set of G1-specific JFR events. Gives an
 * exact picture of how G1 behaves: pause anatomy, heap-region composition, evacuation cost and
 * failures, and concurrent-marking (IHOP/MMU) behaviour.
 *
 * @param header            high-level counters (young/mixed/full pauses, pause statistics, region count)
 * @param pausePhases       per-phase pause breakdown ({@code jdk.GCPhasePause(Level1..4)}, {@code jdk.GCPhaseParallel})
 * @param regionComposition Eden/Survivor/Old used-bytes over time ({@code jdk.G1HeapSummary})
 * @param regionSnapshots   per-snapshot region grid for the heatmap ({@code jdk.G1HeapRegionInformation})
 * @param evacuations       per-collection evacuation cost ({@code jdk.EvacuationInformation})
 * @param evacuationFailures to-space-exhaustion events per collection ({@code jdk.EvacuationFailed})
 * @param ihopTimeline      IHOP threshold vs old-gen occupancy ({@code jdk.G1AdaptiveIHOP})
 * @param mmu               pause-target adherence per collection ({@code jdk.G1MMU})
 * @param systemGcs         explicit {@code System.gc()} invocations ({@code jdk.SystemGC})
 * @param gcLockers         JNI-critical-section GC stalls ({@code jdk.GCLocker})
 */
public record G1AnalysisData(
        G1Header header,
        List<PausePhase> pausePhases,
        TimeseriesData regionComposition,
        List<RegionSnapshot> regionSnapshots,
        List<EvacuationEntry> evacuations,
        List<EvacuationFailure> evacuationFailures,
        TimeseriesData ihopTimeline,
        List<MmuEntry> mmu,
        List<SystemGcEntry> systemGcs,
        List<GcLockerEntry> gcLockers) {

    public record G1Header(
            long youngCount,
            long mixedCount,
            long fullCount,
            long totalPauseNanos,
            long avgPauseNanos,
            long maxPauseNanos,
            long p99PauseNanos,
            long evacuationFailureCount,
            int regionCount) {
    }

    /**
     * Aggregated pause sub-phase. {@code level} is the nesting depth (0 = top-level
     * {@code jdk.GCPhasePause}, 1..4 = {@code GCPhasePauseLevelN}; -1 = per-worker
     * {@code jdk.GCPhaseParallel}).
     */
    public record PausePhase(String name, int level, long count, long totalNanos, long maxNanos, long avgNanos) {
    }

    public record RegionSnapshot(long timeOffsetMillis, List<RegionCell> regions) {
    }

    public record RegionCell(int index, String type, long usedBytes) {
    }

    public record EvacuationEntry(
            long gcId,
            int cSetRegions,
            long cSetUsedBefore,
            long cSetUsedAfter,
            int allocationRegions,
            long bytesCopied,
            int regionsFreed) {
    }

    public record EvacuationFailure(long gcId, long count) {
    }

    public record SystemGcEntry(long timeOffsetMillis, long durationNanos, boolean invokedConcurrent) {
    }

    public record GcLockerEntry(long timeOffsetMillis, long durationNanos, int lockCount, int stallCount) {
    }
}
