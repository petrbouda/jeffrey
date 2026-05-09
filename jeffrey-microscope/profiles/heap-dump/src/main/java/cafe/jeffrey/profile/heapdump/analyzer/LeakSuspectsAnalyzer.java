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

package cafe.jeffrey.profile.heapdump.analyzer;

import org.netbeans.lib.profiler.heap.FieldValue;
import org.netbeans.lib.profiler.heap.Heap;
import org.netbeans.lib.profiler.heap.Instance;
import org.netbeans.lib.profiler.heap.ObjectArrayInstance;
import org.netbeans.lib.profiler.heap.ObjectFieldValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cafe.jeffrey.profile.heapdump.model.DominatedClassEntry;
import cafe.jeffrey.profile.heapdump.model.DominatorNode;
import cafe.jeffrey.profile.heapdump.model.DominatorTreeResponse;
import cafe.jeffrey.profile.heapdump.model.LeakSuspect;
import cafe.jeffrey.profile.heapdump.model.LeakSuspectsReport;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Identifies memory-leak suspects using MAT-style dominator-cluster analysis.
 * <p>
 * Algorithm:
 * <ol>
 *   <li>Get top dominator-tree roots ordered by retained size (via {@link DominatorTreeAnalyzer}).</li>
 *   <li>Treat each root with retained ≥ {@value #CLUSTER_THRESHOLD_PERCENT}% of heap as a candidate cluster.</li>
 *   <li>For each cluster, walk down the dominator chain following the biggest dominated child as long as
 *       its retained size is ≥ {@value #ACCUMULATION_DESCENT_PERCENT}% of its parent's — the deepest
 *       such descendant is the <em>accumulation point</em>.</li>
 *   <li>Compute a class histogram over the cluster subtree (top-{@value #HISTOGRAM_TOP_N} classes
 *       by retained size).</li>
 *   <li>Compute a leak score = {@code heapPercentage × penalty(className)}, penalising container-internal
 *       classes (e.g. {@code HashMap$Node}) that are usually noise rather than the real leak.</li>
 *   <li>Stop when {@value #MAX_ACCUMULATED_PERCENT}% of the heap is covered by suspects, or when retained size
 *       drops below the cluster threshold, or when {@value #MAX_SUSPECTS} suspects have been collected.</li>
 * </ol>
 * Defaults track Eclipse MAT's "Leak Suspects" report.
 */
public class LeakSuspectsAnalyzer {

    private static final Logger LOG = LoggerFactory.getLogger(LeakSuspectsAnalyzer.class);

    private static final double CLUSTER_THRESHOLD_PERCENT = 2.0;
    private static final double MAX_ACCUMULATED_PERCENT = 80.0;
    private static final double ACCUMULATION_DESCENT_PERCENT = 80.0;
    private static final int MAX_SUSPECTS = 10;
    private static final int CANDIDATE_ROOTS = 50;
    private static final int HISTOGRAM_TOP_N = 10;
    private static final int MAX_VISITED_PER_CLUSTER = 50_000;
    private static final int MAX_ACCUMULATION_HOPS = 30;

    /** Container-internal classes whose presence as a suspect is usually noise. */
    private static final Set<String> CONTAINER_INTERNAL_CLASSES = Set.of(
            "java.util.HashMap$Node",
            "java.util.HashMap$TreeNode",
            "java.util.LinkedHashMap$Entry",
            "java.util.concurrent.ConcurrentHashMap$Node",
            "java.util.Hashtable$Entry",
            "java.util.AbstractMap$SimpleEntry",
            "java.util.AbstractMap$SimpleImmutableEntry"
    );
    private static final double CONTAINER_PENALTY = 0.3;

    private final DominatorTreeAnalyzer dominatorTreeAnalyzer;

    public LeakSuspectsAnalyzer(DominatorTreeAnalyzer dominatorTreeAnalyzer) {
        this.dominatorTreeAnalyzer = dominatorTreeAnalyzer;
    }

    public LeakSuspectsReport analyze(Heap heap) {
        return analyze(heap, false, 1.0, 0);
    }

    public LeakSuspectsReport analyze(Heap heap, boolean compressedOops, double correctionRatio, long totalOvercount) {
        long rawTotalHeapSize = heap.getSummary().getTotalLiveBytes();
        long totalHeapSize = CompressedOopsCorrector.correctedTotalHeap(rawTotalHeapSize, compressedOops, totalOvercount);

        if (totalHeapSize <= 0) {
            return new LeakSuspectsReport(totalHeapSize, 0, List.of());
        }

        long clusterThreshold = (long) (totalHeapSize * CLUSTER_THRESHOLD_PERCENT / 100.0);
        long maxAccumulated = (long) (totalHeapSize * MAX_ACCUMULATED_PERCENT / 100.0);

        // Reuse the strict dominator-tree analyzer for candidate root selection
        DominatorTreeResponse rootsResponse =
                dominatorTreeAnalyzer.getRoots(heap, CANDIDATE_ROOTS, compressedOops, totalOvercount);
        List<DominatorNode> candidates = rootsResponse.nodes();

        if (candidates.isEmpty()) {
            LOG.info("No dominator-tree roots found, leak suspects report is empty");
            return new LeakSuspectsReport(totalHeapSize, 0, List.of());
        }

        DominatorTreeReflection reflection = new DominatorTreeReflection(heap);
        if (!reflection.isAvailable()) {
            LOG.error("Dominator tree reflection unavailable, cannot compute leak suspect clusters");
            return new LeakSuspectsReport(totalHeapSize, 0, List.of());
        }

        List<LeakSuspect> suspects = new ArrayList<>();
        long accumulated = 0;
        int rank = 0;

        for (DominatorNode rootNode : candidates) {
            if (suspects.size() >= MAX_SUSPECTS) {
                break;
            }
            if (rootNode.retainedSize() < clusterThreshold) {
                break;
            }
            if (accumulated >= maxAccumulated) {
                break;
            }

            Instance rootInstance = heap.getInstanceByID(rootNode.objectId());
            if (rootInstance == null) {
                continue;
            }

            AccumulationPoint accPoint = findAccumulationPoint(
                    rootInstance, rootNode.retainedSize(),
                    heap, reflection, compressedOops, correctionRatio);

            List<DominatedClassEntry> histogram = computeDominatedHistogram(
                    rootInstance, rootNode.retainedSize(),
                    heap, reflection, compressedOops, correctionRatio);

            double percentage = (double) rootNode.retainedSize() / totalHeapSize * 100.0;
            double leakScore = percentage * penalty(rootNode.className());

            int instanceCount = (int) rootInstance.getJavaClass().getInstancesCount();
            String simpleName = simpleClassName(rootNode.className());
            String reason = String.format(
                    "Dominator cluster rooted at %s holds %.1f%% of heap (%d bytes retained)",
                    simpleName, percentage, rootNode.retainedSize());
            String accDescription = describeAccumulationPoint(accPoint, totalHeapSize);

            rank++;
            suspects.add(new LeakSuspect(
                    rank,
                    rootNode.className(),
                    rootNode.objectId(),
                    rootNode.retainedSize(),
                    percentage,
                    instanceCount,
                    reason,
                    accDescription,
                    List.of(),
                    accPoint != null ? accPoint.id() : null,
                    accPoint != null ? accPoint.className() : null,
                    histogram,
                    leakScore
            ));

            accumulated += rootNode.retainedSize();
        }

        LOG.info("Leak suspects analysis complete: suspects={} accumulatedBytes={} totalHeap={}",
                suspects.size(), accumulated, totalHeapSize);

        return new LeakSuspectsReport(totalHeapSize, accumulated, suspects);
    }

    private record AccumulationPoint(long id, String className, long retainedSize) {
    }

    /**
     * Walks the dominator tree downward from {@code rootInstance}, following the largest dominated
     * child as long as its retained size is at least {@value #ACCUMULATION_DESCENT_PERCENT}% of its
     * parent's. The deepest such descendant is the accumulation point.
     */
    private AccumulationPoint findAccumulationPoint(
            Instance rootInstance, long rootRetained,
            Heap heap, DominatorTreeReflection reflection,
            boolean compressedOops, double correctionRatio) {

        Instance current = rootInstance;
        long currentRetained = rootRetained;

        for (int hop = 0; hop < MAX_ACCUMULATION_HOPS; hop++) {
            List<Long> childIds = dominatedChildrenOf(current.getInstanceId(), heap, reflection);
            if (childIds.isEmpty()) {
                break;
            }

            Instance biggestChild = null;
            long biggestRetained = 0;
            for (long childId : childIds) {
                Instance child = heap.getInstanceByID(childId);
                if (child == null) {
                    continue;
                }
                long retained = correctedRetained(child, compressedOops, correctionRatio);
                if (retained > biggestRetained) {
                    biggestRetained = retained;
                    biggestChild = child;
                }
            }

            if (biggestChild == null) {
                break;
            }
            if (biggestRetained < currentRetained * (ACCUMULATION_DESCENT_PERCENT / 100.0)) {
                break;
            }

            current = biggestChild;
            currentRetained = biggestRetained;
        }

        return new AccumulationPoint(
                current.getInstanceId(),
                current.getJavaClass().getName(),
                currentRetained);
    }

    /**
     * Computes a histogram of classes dominated by {@code rootInstance}, sorted by retained size.
     * Capped at {@value #MAX_VISITED_PER_CLUSTER} visited instances to bound memory and CPU.
     */
    @SuppressWarnings("unchecked")
    private List<DominatedClassEntry> computeDominatedHistogram(
            Instance rootInstance, long clusterRetained,
            Heap heap, DominatorTreeReflection reflection,
            boolean compressedOops, double correctionRatio) {

        Map<String, long[]> stats = new HashMap<>();
        Set<Long> visited = new HashSet<>();
        Deque<Long> queue = new ArrayDeque<>();
        queue.add(rootInstance.getInstanceId());

        int visitedCount = 0;
        while (!queue.isEmpty() && visitedCount < MAX_VISITED_PER_CLUSTER) {
            long id = queue.poll();
            if (!visited.add(id)) {
                continue;
            }
            Instance instance = heap.getInstanceByID(id);
            if (instance == null) {
                continue;
            }
            visitedCount++;

            String className = instance.getJavaClass().getName();
            long retained = correctedRetained(instance, compressedOops, correctionRatio);
            long[] entry = stats.computeIfAbsent(className, k -> new long[2]);
            entry[0]++;
            entry[1] += retained;

            queue.addAll(dominatedChildrenOf(id, heap, reflection));
        }

        if (visitedCount >= MAX_VISITED_PER_CLUSTER) {
            LOG.debug("Cluster histogram truncated: rootId={} visited={}", rootInstance.getInstanceId(), visitedCount);
        }

        return stats.entrySet().stream()
                .map(e -> new DominatedClassEntry(
                        e.getKey(),
                        (int) e.getValue()[0],
                        e.getValue()[1],
                        clusterRetained > 0 ? (double) e.getValue()[1] / clusterRetained * 100.0 : 0.0))
                .sorted(Comparator.comparingLong(DominatedClassEntry::retainedSize).reversed())
                .limit(HISTOGRAM_TOP_N)
                .toList();
    }

    /**
     * Returns all dominator-tree children of {@code parentId}: multi-parent children from the
     * NetBeans dominator hash table, plus single-parent field references whose sole referrer is
     * this parent ({@code idom == -1}).
     */
    @SuppressWarnings("unchecked")
    private List<Long> dominatedChildrenOf(long parentId, Heap heap, DominatorTreeReflection reflection) {
        List<Long> result = new ArrayList<>(reflection.findDominatedChildren(parentId));
        Set<Long> seen = new HashSet<>(result);

        Instance parent = heap.getInstanceByID(parentId);
        if (parent == null) {
            return result;
        }

        for (FieldValue fv : (List<FieldValue>) parent.getFieldValues()) {
            if (fv instanceof ObjectFieldValue ofv) {
                Instance ref = ofv.getInstance();
                if (ref != null && reflection.getIdom(ref.getInstanceId()) == -1
                        && seen.add(ref.getInstanceId())) {
                    result.add(ref.getInstanceId());
                }
            }
        }
        if (parent instanceof ObjectArrayInstance arrayInstance) {
            for (Instance v : (List<Instance>) arrayInstance.getValues()) {
                if (v != null && reflection.getIdom(v.getInstanceId()) == -1
                        && seen.add(v.getInstanceId())) {
                    result.add(v.getInstanceId());
                }
            }
        }
        return result;
    }

    private static long correctedRetained(Instance instance, boolean compressedOops, double correctionRatio) {
        long retained = instance.getRetainedSize();
        if (retained <= 0) {
            retained = instance.getSize();
        }
        return CompressedOopsCorrector.correctedRetainedSize(retained, compressedOops, correctionRatio);
    }

    private static double penalty(String className) {
        if (CONTAINER_INTERNAL_CLASSES.contains(className)) {
            return CONTAINER_PENALTY;
        }
        return 1.0;
    }

    private static String describeAccumulationPoint(AccumulationPoint accPoint, long totalHeapSize) {
        if (accPoint == null) {
            return "Unknown";
        }
        double accPercent = totalHeapSize > 0
                ? (double) accPoint.retainedSize() / totalHeapSize * 100.0
                : 0.0;
        return String.format("%s retains %.1f%% of heap (%d bytes)",
                simpleClassName(accPoint.className()), accPercent, accPoint.retainedSize());
    }

    private static String simpleClassName(String fullClassName) {
        int lastDot = fullClassName.lastIndexOf('.');
        return lastDot > 0 ? fullClassName.substring(lastDot + 1) : fullClassName;
    }
}
