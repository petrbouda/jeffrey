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
import pbouda.jeffrey.profile.heapdump.model.HeapSummary;

import java.time.Instant;

/**
 * Analyzes heap dump to extract summary statistics.
 */
public class HeapSummaryAnalyzer {

    /**
     * Extract summary statistics from the heap.
     *
     * @param heap the loaded heap dump
     * @return summary statistics
     */
    public HeapSummary analyze(Heap heap) {
        return analyze(heap, false, 0);
    }

    /**
     * Extract summary statistics from the heap with compressed oops correction.
     *
     * @param heap           the loaded heap dump
     * @param compressedOops whether compressed oops are enabled
     * @param totalOvercount heap-wide overcount (only used when compressedOops is true)
     * @return summary statistics with corrected total bytes
     */
    public HeapSummary analyze(Heap heap, boolean compressedOops, long totalOvercount) {
        org.netbeans.lib.profiler.heap.HeapSummary nativeSummary = heap.getSummary();

        long totalBytes = CompressedOopsCorrector.correctedTotalHeap(
                nativeSummary.getTotalLiveBytes(), compressedOops, totalOvercount);

        return new HeapSummary(
                totalBytes,
                nativeSummary.getTotalLiveInstances(),
                heap.getAllClasses().size(),
                heap.getGCRoots().size(),
                Instant.ofEpochMilli(nativeSummary.getTime())
        );
    }
}
