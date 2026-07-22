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

package cafe.jeffrey.profile.manager.heapdump;

import cafe.jeffrey.profile.heapdump.model.ClassDiffEntry;
import cafe.jeffrey.profile.heapdump.model.ClassHistogramEntry;
import cafe.jeffrey.profile.heapdump.model.HeapDumpDiffReport;
import cafe.jeffrey.profile.heapdump.model.HeapSummary;
import cafe.jeffrey.profile.heapdump.model.SortBy;
import cafe.jeffrey.shared.common.exception.Exceptions;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Compares two profiles' heap dumps by class histogram — the before/after leak
 * workflow. Mirrors the differential-flamegraph pattern: both sides keep their
 * own index database and the merge happens in Java keyed by class name.
 */
public final class HeapDumpDiffService {

    /** Effectively "all classes" — a heap has tens of thousands, not millions. */
    private static final int FULL_HISTOGRAM_LIMIT = 1_000_000;

    private HeapDumpDiffService() {
    }

    /**
     * Builds the class-level diff between the primary (current) and baseline
     * dumps. Both dumps must exist and be initialized.
     *
     * @param topN cap for the returned entries (ordered by absolute
     *             shallow-bytes delta descending)
     */
    public static HeapDumpDiffReport diff(HeapDumpManager primary, HeapDumpManager baseline, int topN) {
        if (topN <= 0) {
            throw new IllegalArgumentException("topN must be positive: topN=" + topN);
        }
        requireInitialized(primary, "primary");
        requireInitialized(baseline, "baseline");

        HeapSummary primarySummary = primary.getSummary();
        HeapSummary baselineSummary = baseline.getSummary();

        Map<String, ClassSide> byClass = new LinkedHashMap<>();
        for (ClassHistogramEntry entry : primary.getClassHistogram(FULL_HISTOGRAM_LIMIT, SortBy.SIZE)) {
            byClass.computeIfAbsent(entry.className(), k -> new ClassSide())
                    .primary(entry.instanceCount(), entry.totalSize());
        }
        for (ClassHistogramEntry entry : baseline.getClassHistogram(FULL_HISTOGRAM_LIMIT, SortBy.SIZE)) {
            byClass.computeIfAbsent(entry.className(), k -> new ClassSide())
                    .baseline(entry.instanceCount(), entry.totalSize());
        }

        List<ClassDiffEntry> entries = byClass.entrySet().stream()
                .map(e -> e.getValue().toEntry(e.getKey()))
                .filter(e -> e.countDelta() != 0 || e.bytesDelta() != 0)
                .sorted(Comparator.comparingLong((ClassDiffEntry e) -> Math.abs(e.bytesDelta())).reversed())
                .limit(topN)
                .toList();

        long instanceCountDelta = primarySummary.totalInstances() - baselineSummary.totalInstances();
        long shallowBytesDelta = primarySummary.totalBytes() - baselineSummary.totalBytes();

        return new HeapDumpDiffReport(
                primarySummary, baselineSummary, instanceCountDelta, shallowBytesDelta, entries);
    }

    private static void requireInitialized(HeapDumpManager manager, String side) {
        if (!manager.heapDumpExists()) {
            throw Exceptions.invalidRequest(
                    "No heap dump available for the " + side + " profile");
        }
        if (!manager.isCacheReady()) {
            throw Exceptions.invalidRequest(
                    "Heap dump of the " + side + " profile is not initialized");
        }
    }

    /** Mutable per-class accumulator for the two sides. */
    private static final class ClassSide {

        private long primaryCount;
        private long primaryBytes;
        private long baselineCount;
        private long baselineBytes;

        void primary(long count, long bytes) {
            primaryCount += count;
            primaryBytes += bytes;
        }

        void baseline(long count, long bytes) {
            baselineCount += count;
            baselineBytes += bytes;
        }

        ClassDiffEntry toEntry(String className) {
            return new ClassDiffEntry(
                    className,
                    primaryCount, baselineCount, primaryCount - baselineCount,
                    primaryBytes, baselineBytes, primaryBytes - baselineBytes);
        }
    }
}
