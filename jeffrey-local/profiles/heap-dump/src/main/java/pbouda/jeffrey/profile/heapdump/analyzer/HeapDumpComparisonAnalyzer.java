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

package pbouda.jeffrey.profile.heapdump.analyzer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pbouda.jeffrey.profile.heapdump.model.ClassComparisonEntry;
import pbouda.jeffrey.profile.heapdump.model.ClassComparisonEntry.ComparisonStatus;
import pbouda.jeffrey.profile.heapdump.model.ClassHistogramEntry;
import pbouda.jeffrey.profile.heapdump.model.HeapDumpComparisonReport;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Compares two class histograms from different heap dumps to identify
 * classes that grew, shrank, appeared, or disappeared between snapshots.
 */
public class HeapDumpComparisonAnalyzer {

    private static final Logger LOG = LoggerFactory.getLogger(HeapDumpComparisonAnalyzer.class);

    /**
     * Compare two class histograms and produce a comparison report.
     *
     * @param baseline           class histogram entries from the baseline (older) heap dump
     * @param current            class histogram entries from the current (newer) heap dump
     * @param baselineTotalBytes total heap size in bytes of the baseline heap dump
     * @param currentTotalBytes  total heap size in bytes of the current heap dump
     * @return comparison report with per-class deltas sorted by absolute size delta descending
     */
    public HeapDumpComparisonReport compare(
            List<ClassHistogramEntry> baseline,
            List<ClassHistogramEntry> current,
            long baselineTotalBytes,
            long currentTotalBytes) {

        LOG.debug("Comparing heap dump histograms: baselineClasses={} currentClasses={} baselineBytes={} currentBytes={}",
                baseline.size(), current.size(), baselineTotalBytes, currentTotalBytes);

        // Index baseline entries by class name for O(1) lookup
        Map<String, ClassHistogramEntry> baselineMap = new LinkedHashMap<>();
        for (ClassHistogramEntry entry : baseline) {
            baselineMap.put(entry.className(), entry);
        }

        // Index current entries by class name for O(1) lookup
        Map<String, ClassHistogramEntry> currentMap = new LinkedHashMap<>();
        for (ClassHistogramEntry entry : current) {
            currentMap.put(entry.className(), entry);
        }

        List<ClassComparisonEntry> entries = new ArrayList<>();

        // Process all classes present in the current histogram
        for (ClassHistogramEntry curr : current) {
            ClassHistogramEntry base = baselineMap.get(curr.className());
            if (base != null) {
                // Class exists in both histograms
                long sizeDelta = curr.totalSize() - base.totalSize();
                long countDelta = curr.instanceCount() - base.instanceCount();
                ComparisonStatus status = determineStatus(sizeDelta, countDelta);

                entries.add(new ClassComparisonEntry(
                        curr.className(),
                        base.totalSize(),
                        curr.totalSize(),
                        sizeDelta,
                        base.instanceCount(),
                        curr.instanceCount(),
                        countDelta,
                        status
                ));
            } else {
                // Class is new (only in current)
                entries.add(new ClassComparisonEntry(
                        curr.className(),
                        0,
                        curr.totalSize(),
                        curr.totalSize(),
                        0,
                        curr.instanceCount(),
                        curr.instanceCount(),
                        ComparisonStatus.NEW
                ));
            }
        }

        // Process classes only in the baseline (removed)
        for (ClassHistogramEntry base : baseline) {
            if (!currentMap.containsKey(base.className())) {
                entries.add(new ClassComparisonEntry(
                        base.className(),
                        base.totalSize(),
                        0,
                        -base.totalSize(),
                        base.instanceCount(),
                        0,
                        -base.instanceCount(),
                        ComparisonStatus.REMOVED
                ));
            }
        }

        // Sort by absolute size delta descending (biggest changes first)
        entries.sort(Comparator.comparingLong((ClassComparisonEntry e) -> Math.abs(e.sizeDelta())).reversed());

        long totalBytesDelta = currentTotalBytes - baselineTotalBytes;

        LOG.debug("Heap dump comparison complete: totalEntries={} totalBytesDelta={}",
                entries.size(), totalBytesDelta);

        return new HeapDumpComparisonReport(
                baselineTotalBytes,
                currentTotalBytes,
                totalBytesDelta,
                baseline.size(),
                current.size(),
                entries
        );
    }

    private static ComparisonStatus determineStatus(long sizeDelta, long countDelta) {
        if (sizeDelta > 0 || countDelta > 0) {
            return ComparisonStatus.GREW;
        } else if (sizeDelta < 0 || countDelta < 0) {
            return ComparisonStatus.SHRANK;
        } else {
            return ComparisonStatus.UNCHANGED;
        }
    }
}
