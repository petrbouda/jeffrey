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

import org.netbeans.lib.profiler.heap.Heap;
import org.netbeans.lib.profiler.heap.Instance;
import org.netbeans.lib.profiler.heap.JavaClass;
import org.netbeans.lib.profiler.heap.PrimitiveArrayInstance;
import org.netbeans.lib.profiler.heap.ObjectArrayInstance;
import org.netbeans.lib.profiler.heap.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pbouda.jeffrey.profile.heapdump.model.BiggestCollectionEntry;
import pbouda.jeffrey.profile.heapdump.model.BiggestCollectionsReport;
import pbouda.jeffrey.profile.heapdump.model.ClassWasteEntry;
import pbouda.jeffrey.profile.heapdump.model.CollectionAnalysisReport;
import pbouda.jeffrey.profile.heapdump.model.CollectionStats;
import pbouda.jeffrey.profile.heapdump.model.FillDistribution;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * Analyzes Java collection fill ratios to identify over-allocated and empty collections.
 * Examines HashMap, ArrayList, HashSet, and other common collection types by
 * inspecting their internal backing arrays.
 */
public class CollectionAnalyzer {

    private static final Logger LOG = LoggerFactory.getLogger(CollectionAnalyzer.class);

    private record CollectionType(String className, String arrayField, String sizeField, boolean isSet) {
    }

    private static final List<CollectionType> COLLECTION_TYPES = List.of(
            new CollectionType("java.util.HashMap", "table", "size", false),
            new CollectionType("java.util.ArrayList", "elementData", "size", false),
            new CollectionType("java.util.LinkedHashMap", "table", "size", false),
            new CollectionType("java.util.Hashtable", "table", "count", false),
            new CollectionType("java.util.concurrent.ConcurrentHashMap", "table", null, false),
            new CollectionType("java.util.HashSet", null, null, true),
            new CollectionType("java.util.LinkedHashSet", null, null, true),
            new CollectionType("java.util.PriorityQueue", "queue", "size", false),
            new CollectionType("java.util.ArrayDeque", "elements", null, false)
    );

    /**
     * Per-instance analysis result used internally during collection traversal.
     */
    private record InstanceFillInfo(
            Instance instance,
            String collectionClassName,
            int capacity,
            int used,
            double fillRatio,
            long wastedBytes
    ) {
    }

    /**
     * Analyze all collection types and return a comprehensive report.
     */
    public CollectionAnalysisReport analyze(Heap heap) {
        return analyze(heap, false);
    }

    /**
     * Analyze all collection types with compressed oops correction.
     *
     * @param compressedOops whether compressed oops are enabled
     */
    public CollectionAnalysisReport analyze(Heap heap, boolean compressedOops) {
        long referenceSize = CompressedOopsCorrector.referenceSize(compressedOops);
        List<CollectionStats> allStats = new ArrayList<>();
        int totalCollections = 0;
        int totalEmpty = 0;
        long totalWasted = 0;
        int[] overallBuckets = new int[5]; // empty, low, medium, high, full

        // Collect per-instance fill info across all types for waste-by-class aggregation
        List<InstanceFillInfo> allInstanceFills = new ArrayList<>();

        for (CollectionType type : COLLECTION_TYPES) {
            AnalysisResult result = analyzeCollectionType(heap, type, referenceSize);
            if (result == null) {
                continue;
            }

            CollectionStats stats = result.stats();
            if (stats.totalCount() > 0) {
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

            allInstanceFills.addAll(result.instanceFills());
        }

        // Sort by wasted bytes descending
        allStats.sort((a, b) -> Long.compare(b.totalWastedBytes(), a.totalWastedBytes()));

        FillDistribution overallDist = new FillDistribution(
                overallBuckets[0], overallBuckets[1], overallBuckets[2],
                overallBuckets[3], overallBuckets[4]);

        // Compute waste-by-class aggregation
        List<ClassWasteEntry> wasteByClass = computeWasteByClass(allInstanceFills);

        LOG.info("Collection analysis complete: totalCollections={} totalWasted={} wasteByClassEntries={}",
                totalCollections, totalWasted, wasteByClass.size());

        return new CollectionAnalysisReport(totalCollections, totalEmpty, totalWasted, overallDist, allStats, wasteByClass);
    }

    /**
     * Analyze the biggest individual collections by element count and retained size.
     *
     * @param heap           the heap to analyze
     * @param topN           number of top entries to return in each ranking
     * @param compressedOops whether compressed oops are enabled
     * @return report with the biggest collections
     */
    @SuppressWarnings("unchecked")
    public BiggestCollectionsReport analyzeBiggestCollections(Heap heap, int topN, boolean compressedOops) {
        long referenceSize = CompressedOopsCorrector.referenceSize(compressedOops);

        // Use min-heaps to efficiently track top N by element count and retained size
        PriorityQueue<CollectionCandidate> topByElementCount = new PriorityQueue<>(
                topN + 1, Comparator.comparingInt(CollectionCandidate::elementCount));
        PriorityQueue<CollectionCandidate> topByRetainedSize = new PriorityQueue<>(
                topN + 1, Comparator.comparingLong(CollectionCandidate::retainedSize));

        int totalAnalyzed = 0;

        // Trigger retained size computation by accessing the first instance's retained size
        boolean retainedSizeTriggered = false;

        for (CollectionType type : COLLECTION_TYPES) {
            JavaClass javaClass = heap.getJavaClassByName(type.className());
            if (javaClass == null) {
                continue;
            }

            List<Instance> instances = (List<Instance>) javaClass.getInstances();
            if (instances.isEmpty()) {
                continue;
            }

            // Trigger retained size computation once
            if (!retainedSizeTriggered) {
                instances.get(0).getRetainedSize();
                retainedSizeTriggered = true;
            }

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

                totalAnalyzed++;

                long shallowSize = CompressedOopsCorrector.correctedShallowSize(instance, compressedOops);
                long retainedSize = instance.getRetainedSize();
                if (retainedSize <= 0) {
                    retainedSize = shallowSize;
                }

                String ownerClassName = resolveOwnerClassName(instance);
                double fillRatio = fill.capacity > 0 ? (double) fill.used / fill.capacity : 0;

                CollectionCandidate candidate = new CollectionCandidate(
                        instance.getInstanceId(),
                        type.className(),
                        fill.used,
                        fill.capacity,
                        fillRatio,
                        shallowSize,
                        retainedSize,
                        ownerClassName
                );

                // Track top N by element count
                if (fill.used > 0) {
                    topByElementCount.offer(candidate);
                    if (topByElementCount.size() > topN) {
                        topByElementCount.poll();
                    }
                }

                // Track top N by retained size
                topByRetainedSize.offer(candidate);
                if (topByRetainedSize.size() > topN) {
                    topByRetainedSize.poll();
                }
            }
        }

        // Convert min-heaps to sorted lists (descending)
        List<BiggestCollectionEntry> byElementCount = drainToSortedList(topByElementCount,
                Comparator.comparingInt(BiggestCollectionEntry::elementCount).reversed());
        List<BiggestCollectionEntry> byRetainedSize = drainToSortedList(topByRetainedSize,
                Comparator.comparingLong(BiggestCollectionEntry::retainedSize).reversed());

        LOG.info("Biggest collections analysis complete: totalAnalyzed={} topN={}", totalAnalyzed, topN);

        return new BiggestCollectionsReport(totalAnalyzed, byElementCount, byRetainedSize);
    }

    private record CollectionCandidate(
            long objectId,
            String className,
            int elementCount,
            int capacity,
            double fillRatio,
            long shallowSize,
            long retainedSize,
            String ownerClassName
    ) {
    }

    private List<BiggestCollectionEntry> drainToSortedList(
            PriorityQueue<CollectionCandidate> heap,
            Comparator<BiggestCollectionEntry> comparator) {

        List<BiggestCollectionEntry> entries = new ArrayList<>(heap.size());
        for (CollectionCandidate candidate : heap) {
            entries.add(new BiggestCollectionEntry(
                    candidate.objectId(),
                    candidate.className(),
                    candidate.elementCount(),
                    candidate.capacity(),
                    candidate.fillRatio(),
                    candidate.shallowSize(),
                    candidate.retainedSize(),
                    candidate.ownerClassName()
            ));
        }
        entries.sort(comparator);
        return entries;
    }

    /**
     * Resolves the owner class name for a collection instance by looking at its first referrer.
     */
    @SuppressWarnings("unchecked")
    private String resolveOwnerClassName(Instance instance) {
        List<Value> references = (List<Value>) instance.getReferences();
        if (references.isEmpty()) {
            return null;
        }
        Instance referrer = references.getFirst().getDefiningInstance();
        if (referrer == null) {
            return null;
        }
        return referrer.getJavaClass().getName();
    }

    /**
     * Aggregates waste metrics by owner class across all collection instances.
     */
    private List<ClassWasteEntry> computeWasteByClass(List<InstanceFillInfo> instanceFills) {
        // Group by owner class
        Map<String, OwnerWasteAccumulator> accumulators = new HashMap<>();

        for (InstanceFillInfo fill : instanceFills) {
            String ownerClassName = resolveOwnerClassName(fill.instance());
            if (ownerClassName == null) {
                ownerClassName = "<no referrer>";
            }

            OwnerWasteAccumulator acc = accumulators.computeIfAbsent(
                    ownerClassName, k -> new OwnerWasteAccumulator());
            acc.collectionCount++;
            acc.wastedBytes += fill.wastedBytes();
            if (fill.used() == 0) {
                acc.emptyCount++;
            }
            acc.collectionTypeCounts.merge(fill.collectionClassName(), 1, Integer::sum);
        }

        // Convert to sorted list (by wasted bytes descending), limit to top 100
        return accumulators.entrySet().stream()
                .map(e -> new ClassWasteEntry(
                        e.getKey(),
                        e.getValue().collectionCount,
                        e.getValue().emptyCount,
                        e.getValue().wastedBytes,
                        e.getValue().collectionTypeCounts
                ))
                .sorted(Comparator.comparingLong(ClassWasteEntry::wastedBytes).reversed())
                .limit(100)
                .toList();
    }

    private static class OwnerWasteAccumulator {
        int collectionCount;
        int emptyCount;
        long wastedBytes;
        Map<String, Integer> collectionTypeCounts = new HashMap<>();
    }

    /**
     * Holds per-type analysis result: the aggregated stats and per-instance fill info.
     */
    private record AnalysisResult(CollectionStats stats, List<InstanceFillInfo> instanceFills) {
    }

    @SuppressWarnings("unchecked")
    private AnalysisResult analyzeCollectionType(Heap heap, CollectionType type, long referenceSize) {
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
        List<InstanceFillInfo> instanceFills = new ArrayList<>(total);

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
            long wastedBytes = 0;
            if (unused > 0) {
                wastedBytes = unused * referenceSize;
                totalWasted += wastedBytes;
            }

            instanceFills.add(new InstanceFillInfo(
                    instance, type.className(), fill.capacity, fill.used, ratio, wastedBytes));
        }

        double avgFillRatio = total > 0 ? fillRatioSum / total : 0;
        FillDistribution dist = new FillDistribution(buckets[0], buckets[1], buckets[2], buckets[3], buckets[4]);
        CollectionStats stats = new CollectionStats(type.className(), total, emptyCount, totalWasted, avgFillRatio, dist);

        return new AnalysisResult(stats, instanceFills);
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
