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
