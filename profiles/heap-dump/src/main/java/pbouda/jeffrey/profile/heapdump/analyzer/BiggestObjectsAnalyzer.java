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
import org.netbeans.lib.profiler.heap.Instance;
import org.netbeans.lib.profiler.heap.JavaClass;
import org.netbeans.modules.profiler.oql.engine.api.OQLEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pbouda.jeffrey.profile.heapdump.model.BiggestObjectEntry;
import pbouda.jeffrey.profile.heapdump.model.BiggestObjectsReport;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;

/**
 * Analyzes a heap dump to find the biggest individual objects by retained size.
 * <p>
 * Strategy: Iterate instances from the top classes by total size from the histogram,
 * compute retained size for each, and keep a top-N selection using a min-heap.
 * This avoids computing retained size for every object in the heap (which would be very slow).
 */
public class BiggestObjectsAnalyzer {

    private static final Logger LOG = LoggerFactory.getLogger(BiggestObjectsAnalyzer.class);

    private static final int DEFAULT_TOP_N = 50;
    private static final int CANDIDATE_CLASSES = 200;

    /**
     * Find the biggest objects in the heap by retained size.
     *
     * @param heap the loaded heap dump
     * @param topN number of biggest objects to return
     * @return report with the top N biggest objects
     */
    @SuppressWarnings("unchecked")
    public BiggestObjectsReport analyze(Heap heap, int topN) {
        if (topN <= 0) {
            topN = DEFAULT_TOP_N;
        }

        OQLEngine engine = new OQLEngine(heap);
        InstanceValueFormatter formatter = new InstanceValueFormatter(engine);

        // Get the top classes by total size to limit the search space
        List<JavaClass> allClasses = (List<JavaClass>) heap.getAllClasses();
        List<JavaClass> candidateClasses = allClasses.stream()
                .filter(c -> c.getInstancesCount() > 0)
                .sorted(Comparator.comparingLong(JavaClass::getAllInstancesSize).reversed())
                .limit(CANDIDATE_CLASSES)
                .toList();

        // Use a min-heap of size topN for efficient selection
        PriorityQueue<BiggestObjectEntry> minHeap = new PriorityQueue<>(
                topN + 1, Comparator.comparingLong(BiggestObjectEntry::retainedSize));

        long totalHeapSize = heap.getSummary().getTotalLiveBytes();
        int processedInstances = 0;

        for (JavaClass javaClass : candidateClasses) {
            List<Instance> instances = (List<Instance>) javaClass.getInstances();
            for (Instance instance : instances) {
                long retainedSize = instance.getRetainedSize();
                if (retainedSize <= 0) {
                    retainedSize = instance.getSize();
                }

                processedInstances++;

                // Only add to the heap if it could be in the top N
                if (minHeap.size() < topN || retainedSize > minHeap.peek().retainedSize()) {
                    BiggestObjectEntry entry = new BiggestObjectEntry(
                            instance.getInstanceId(),
                            javaClass.getName(),
                            instance.getSize(),
                            retainedSize,
                            formatter.format(instance)
                    );
                    minHeap.offer(entry);
                    if (minHeap.size() > topN) {
                        minHeap.poll();
                    }
                }
            }
        }

        LOG.info("Biggest objects analysis complete: processedInstances={} topN={}", processedInstances, topN);

        // Extract results sorted by retained size descending
        List<BiggestObjectEntry> results = new ArrayList<>(minHeap);
        results.sort(Comparator.comparingLong(BiggestObjectEntry::retainedSize).reversed());

        long totalRetainedSize = results.stream()
                .mapToLong(BiggestObjectEntry::retainedSize)
                .sum();

        return new BiggestObjectsReport(totalRetainedSize, totalHeapSize, results);
    }
}
