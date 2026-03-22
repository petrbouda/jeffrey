/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
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

import org.netbeans.lib.profiler.heap.Heap;
import org.netbeans.lib.profiler.heap.JavaClass;
import pbouda.jeffrey.profile.heapdump.model.ClassHistogramEntry;
import pbouda.jeffrey.profile.heapdump.model.SortBy;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Generates class histogram from a heap dump.
 */
public class ClassHistogramAnalyzer {

    private static final int DEFAULT_TOP_N = 100;

    /**
     * Generate class histogram with default sorting (by size).
     *
     * @param heap the loaded heap dump
     * @param topN number of top entries to return
     * @return list of histogram entries sorted by size descending
     */
    public List<ClassHistogramEntry> analyze(Heap heap, int topN) {
        return analyze(heap, topN, SortBy.SIZE);
    }

    /**
     * Generate class histogram with custom sorting.
     *
     * @param heap   the loaded heap dump
     * @param topN   number of top entries to return (use -1 for all)
     * @param sortBy sort criteria
     * @return list of histogram entries
     */
    @SuppressWarnings("unchecked")
    public List<ClassHistogramEntry> analyze(Heap heap, int topN, SortBy sortBy) {
        return analyze(heap, topN, sortBy, false, 1.0);
    }

    /**
     * Generate class histogram with custom sorting and compressed oops correction.
     *
     * @param heap            the loaded heap dump
     * @param topN            number of top entries to return (use -1 for all)
     * @param sortBy          sort criteria
     * @param compressedOops  whether compressed oops are enabled
     * @param correctionRatio correction ratio for size values
     * @return list of histogram entries
     */
    @SuppressWarnings("unchecked")
    public List<ClassHistogramEntry> analyze(Heap heap, int topN, SortBy sortBy,
                                              boolean compressedOops, double correctionRatio) {
        if (topN <= 0) {
            topN = DEFAULT_TOP_N;
        }

        List<ClassHistogramEntry> entries = new ArrayList<>();

        List<JavaClass> allClasses = (List<JavaClass>) heap.getAllClasses();
        for (JavaClass javaClass : allClasses) {
            long instanceCount = javaClass.getInstancesCount();
            if (instanceCount > 0) {
                long totalSize = CompressedOopsCorrector.correctedTotalSize(
                        javaClass.getAllInstancesSize(), compressedOops, correctionRatio);
                entries.add(new ClassHistogramEntry(
                        javaClass.getName(),
                        instanceCount,
                        totalSize
                ));
            }
        }

        // Sort based on criteria
        Comparator<ClassHistogramEntry> comparator = switch (sortBy) {
            case SIZE -> Comparator.comparingLong(ClassHistogramEntry::totalSize).reversed();
            case COUNT -> Comparator.comparingLong(ClassHistogramEntry::instanceCount).reversed();
            case CLASS_NAME -> Comparator.comparing(ClassHistogramEntry::className);
        };

        entries.sort(comparator);

        // Return top N entries
        return entries.subList(0, Math.min(topN, entries.size()));
    }

    /**
     * Get total number of classes with instances.
     *
     * @param heap the loaded heap dump
     * @return number of classes with at least one instance
     */
    @SuppressWarnings("unchecked")
    public int getTotalClassCount(Heap heap) {
        int count = 0;
        List<JavaClass> allClasses = (List<JavaClass>) heap.getAllClasses();
        for (JavaClass javaClass : allClasses) {
            if (javaClass.getInstancesCount() > 0) {
                count++;
            }
        }
        return count;
    }
}
