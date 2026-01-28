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
import org.netbeans.lib.profiler.heap.PrimitiveArrayInstance;
import org.netbeans.lib.profiler.heap.ObjectArrayInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pbouda.jeffrey.profile.heapdump.model.CollectionAnalysisReport;
import pbouda.jeffrey.profile.heapdump.model.CollectionStats;
import pbouda.jeffrey.profile.heapdump.model.FillDistribution;

import java.util.ArrayList;
import java.util.List;

/**
 * Analyzes Java collection fill ratios to identify over-allocated and empty collections.
 * Examines HashMap, ArrayList, HashSet, and other common collection types by
 * inspecting their internal backing arrays.
 */
public class CollectionAnalyzer {

    private static final Logger LOG = LoggerFactory.getLogger(CollectionAnalyzer.class);

    private static final long REFERENCE_SIZE = 8; // 64-bit references

    private record CollectionType(String className, String arrayField, String sizeField, boolean isSet) {
    }

    private static final List<CollectionType> COLLECTION_TYPES = List.of(
            new CollectionType("java.util.HashMap", "table", "size", false),
            new CollectionType("java.util.ArrayList", "elementData", "size", false),
            new CollectionType("java.util.LinkedHashMap", "table", "size", false),
            new CollectionType("java.util.Hashtable", "table", "count", false),
            new CollectionType("java.util.concurrent.ConcurrentHashMap", "table", "sizectl", false),
            new CollectionType("java.util.HashSet", null, null, true),
            new CollectionType("java.util.LinkedHashSet", null, null, true),
            new CollectionType("java.util.PriorityQueue", "queue", "size", false),
            new CollectionType("java.util.ArrayDeque", "elements", null, false)
    );

    /**
     * Analyze all collection types and return a comprehensive report.
     */
    public CollectionAnalysisReport analyze(Heap heap) {
        List<CollectionStats> allStats = new ArrayList<>();
        int totalCollections = 0;
        int totalEmpty = 0;
        long totalWasted = 0;
        int[] overallBuckets = new int[5]; // empty, low, medium, high, full

        for (CollectionType type : COLLECTION_TYPES) {
            CollectionStats stats = analyzeCollectionType(heap, type);
            if (stats != null && stats.totalCount() > 0) {
                allStats.add(stats);
                totalCollections += stats.totalCount();
                totalEmpty += stats.emptyCount();
                totalWasted += stats.totalWastedBytes();

                FillDistribution dist = stats.fillDistribution();
                overallBuckets[0] += dist.empty();
                overallBuckets[1] += dist.low();
                overallBuckets[2] += dist.medium();
                overallBuckets[3] += dist.high();
                overallBuckets[4] += dist.full();
            }
        }

        // Sort by wasted bytes descending
        allStats.sort((a, b) -> Long.compare(b.totalWastedBytes(), a.totalWastedBytes()));

        FillDistribution overallDist = new FillDistribution(
                overallBuckets[0], overallBuckets[1], overallBuckets[2],
                overallBuckets[3], overallBuckets[4]);

        LOG.info("Collection analysis complete: totalCollections={} totalWasted={}", totalCollections, totalWasted);

        return new CollectionAnalysisReport(totalCollections, totalEmpty, totalWasted, overallDist, allStats);
    }

    @SuppressWarnings("unchecked")
    private CollectionStats analyzeCollectionType(Heap heap, CollectionType type) {
        JavaClass javaClass = heap.getJavaClassByName(type.className());
        if (javaClass == null) {
            return null;
        }

        List<Instance> instances = (List<Instance>) javaClass.getInstances();
        if (instances.isEmpty()) {
            return null;
        }

        int total = instances.size();
        int emptyCount = 0;
        long totalWasted = 0;
        double fillRatioSum = 0;
        int[] buckets = new int[5]; // empty, low, medium, high, full

        for (Instance instance : instances) {
            FillInfo fill;
            if (type.isSet()) {
                fill = analyzeSet(instance);
            } else if ("java.util.ArrayDeque".equals(type.className())) {
                fill = analyzeArrayDeque(instance);
            } else if ("java.util.concurrent.ConcurrentHashMap".equals(type.className())) {
                fill = analyzeConcurrentHashMap(instance);
            } else {
                fill = analyzeStandardCollection(instance, type.arrayField(), type.sizeField());
            }

            if (fill == null) {
                continue;
            }

            double ratio = fill.capacity > 0 ? (double) fill.used / fill.capacity : 0;
            fillRatioSum += ratio;

            if (fill.used == 0) {
                emptyCount++;
                buckets[0]++;
            } else if (ratio <= 0.25) {
                buckets[1]++;
            } else if (ratio <= 0.50) {
                buckets[2]++;
            } else if (ratio <= 0.75) {
                buckets[3]++;
            } else {
                buckets[4]++;
            }

            // Wasted = unused slots * reference size
            long unused = fill.capacity - fill.used;
            if (unused > 0) {
                totalWasted += unused * REFERENCE_SIZE;
            }
        }

        double avgFillRatio = total > 0 ? fillRatioSum / total : 0;
        FillDistribution dist = new FillDistribution(buckets[0], buckets[1], buckets[2], buckets[3], buckets[4]);

        return new CollectionStats(type.className(), total, emptyCount, totalWasted, avgFillRatio, dist);
    }

    private record FillInfo(int capacity, int used) {
    }

    private FillInfo analyzeStandardCollection(Instance instance, String arrayField, String sizeField) {
        Object arrayValue = instance.getValueOfField(arrayField);
        Object sizeValue = instance.getValueOfField(sizeField);

        int capacity = 0;
        if (arrayValue instanceof ObjectArrayInstance array) {
            capacity = array.getLength();
        } else if (arrayValue instanceof Instance arrayInst) {
            // Some implementations wrap the array
            capacity = guessArrayLength(arrayInst);
        }

        int used = 0;
        if (sizeValue instanceof Number n) {
            used = n.intValue();
        }

        if (capacity == 0 && used == 0) {
            return new FillInfo(0, 0);
        }

        return new FillInfo(Math.max(capacity, used), used);
    }

    private FillInfo analyzeSet(Instance instance) {
        // HashSet delegates to internal HashMap
        Object mapValue = instance.getValueOfField("map");
        if (mapValue instanceof Instance mapInstance) {
            return analyzeStandardCollection(mapInstance, "table", "size");
        }
        return null;
    }

    private FillInfo analyzeArrayDeque(Instance instance) {
        Object elementsValue = instance.getValueOfField("elements");
        int capacity = 0;
        if (elementsValue instanceof ObjectArrayInstance array) {
            capacity = array.getLength();
        }

        // ArrayDeque: head and tail pointers determine size
        Object head = instance.getValueOfField("head");
        Object tail = instance.getValueOfField("tail");
        if (head instanceof Number h && tail instanceof Number t) {
            int headVal = h.intValue();
            int tailVal = t.intValue();
            int used;
            if (tailVal >= headVal) {
                used = tailVal - headVal;
            } else {
                used = capacity - headVal + tailVal;
            }
            return new FillInfo(capacity, used);
        }

        return new FillInfo(capacity, 0);
    }

    @SuppressWarnings("unchecked")
    private FillInfo analyzeConcurrentHashMap(Instance instance) {
        Object tableValue = instance.getValueOfField("table");
        int capacity = 0;
        if (tableValue instanceof ObjectArrayInstance array) {
            capacity = array.getLength();
        }

        // Count non-null entries to determine used slots
        int used = 0;
        if (tableValue instanceof ObjectArrayInstance array) {
            List<Instance> values = (List<Instance>) array.getValues();
            for (Instance v : values) {
                if (v != null) {
                    used++;
                }
            }
        }

        return new FillInfo(capacity, used);
    }

    private int guessArrayLength(Instance instance) {
        if (instance instanceof ObjectArrayInstance array) {
            return array.getLength();
        }
        if (instance instanceof PrimitiveArrayInstance array) {
            return array.getLength();
        }
        return 0;
    }
}
