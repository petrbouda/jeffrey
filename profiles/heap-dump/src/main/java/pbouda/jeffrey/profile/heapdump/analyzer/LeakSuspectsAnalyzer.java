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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pbouda.jeffrey.profile.heapdump.model.LeakSuspect;
import pbouda.jeffrey.profile.heapdump.model.LeakSuspectsReport;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Identifies potential memory leak suspects using heuristics:
 * <ul>
 *   <li>Single objects with disproportionate retained size (>10% of heap)</li>
 *   <li>Classes with a large number of instances that collectively hold significant memory</li>
 *   <li>Large collections holding many objects of the same type</li>
 * </ul>
 */
public class LeakSuspectsAnalyzer {

    private static final Logger LOG = LoggerFactory.getLogger(LeakSuspectsAnalyzer.class);

    private static final double SINGLE_OBJECT_THRESHOLD = 0.10; // 10% of heap
    private static final double CLASS_TOTAL_THRESHOLD = 0.15;   // 15% of heap by class total
    private static final int MAX_SUSPECTS = 10;
    private static final int TOP_CLASSES_TO_CHECK = 50;

    /**
     * Analyze the heap and identify potential leak suspects.
     */
    @SuppressWarnings("unchecked")
    public LeakSuspectsReport analyze(Heap heap) {
        long totalHeapSize = heap.getSummary().getTotalLiveBytes();

        List<LeakSuspect> suspects = new ArrayList<>();

        // Get top classes by total size
        List<JavaClass> allClasses = (List<JavaClass>) heap.getAllClasses();
        List<JavaClass> topClasses = allClasses.stream()
                .filter(c -> c.getInstancesCount() > 0)
                .sorted(Comparator.comparingLong(JavaClass::getAllInstancesSize).reversed())
                .limit(TOP_CLASSES_TO_CHECK)
                .toList();

        long analyzedBytes = 0;
        int rank = 0;

        for (JavaClass javaClass : topClasses) {
            long classTotalSize = javaClass.getAllInstancesSize();
            int instanceCount = (int) javaClass.getInstancesCount();
            analyzedBytes += classTotalSize;

            double classPercent = totalHeapSize > 0 ? (double) classTotalSize / totalHeapSize : 0;

            // Heuristic 1: Class total size exceeds threshold
            if (classPercent >= CLASS_TOTAL_THRESHOLD) {
                // Check if a single instance dominates
                List<Instance> instances = (List<Instance>) javaClass.getInstances();
                Instance largest = null;
                long largestRetained = 0;

                // For classes with few instances, check individual retained sizes
                if (instanceCount <= 100) {
                    for (Instance inst : instances) {
                        long retained = inst.getRetainedSize();
                        if (retained > largestRetained) {
                            largestRetained = retained;
                            largest = inst;
                        }
                    }
                }

                double largestPercent = totalHeapSize > 0
                        ? (double) largestRetained / totalHeapSize : 0;

                if (largest != null && largestPercent >= SINGLE_OBJECT_THRESHOLD) {
                    // Single dominating object
                    rank++;
                    suspects.add(new LeakSuspect(
                            rank,
                            javaClass.getName(),
                            largest.getInstanceId(),
                            largestRetained,
                            largestPercent * 100,
                            instanceCount,
                            String.format("Single %s object holds %.1f%% of heap (%d bytes retained)",
                                    simpleClassName(javaClass.getName()),
                                    largestPercent * 100,
                                    largestRetained),
                            "Single large instance",
                            List.of()
                    ));
                } else if (classPercent >= CLASS_TOTAL_THRESHOLD) {
                    // Many instances collectively large
                    rank++;
                    suspects.add(new LeakSuspect(
                            rank,
                            javaClass.getName(),
                            null,
                            classTotalSize,
                            classPercent * 100,
                            instanceCount,
                            String.format("%,d instances of %s collectively hold %.1f%% of heap",
                                    instanceCount,
                                    simpleClassName(javaClass.getName()),
                                    classPercent * 100),
                            String.format("%,d instances accumulated", instanceCount),
                            List.of()
                    ));
                }

                if (suspects.size() >= MAX_SUSPECTS) {
                    break;
                }
            }
        }

        LOG.info("Leak suspects analysis complete: suspectsFound={} analyzedBytes={}", suspects.size(), analyzedBytes);

        return new LeakSuspectsReport(totalHeapSize, analyzedBytes, suspects);
    }

    private String simpleClassName(String fullClassName) {
        int lastDot = fullClassName.lastIndexOf('.');
        return lastDot > 0 ? fullClassName.substring(lastDot + 1) : fullClassName;
    }
}
