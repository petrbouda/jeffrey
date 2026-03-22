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

import org.netbeans.lib.profiler.heap.FieldValue;
import org.netbeans.lib.profiler.heap.GCRoot;
import org.netbeans.lib.profiler.heap.Heap;
import org.netbeans.lib.profiler.heap.Instance;
import org.netbeans.lib.profiler.heap.JavaClass;
import org.netbeans.lib.profiler.heap.ObjectFieldValue;
import org.netbeans.lib.profiler.heap.Value;
import org.netbeans.modules.profiler.oql.engine.api.OQLEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pbouda.jeffrey.profile.heapdump.model.DominatorNode;
import pbouda.jeffrey.profile.heapdump.model.DominatorTreeResponse;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * Provides a retained-size tree view of the heap.
 * Instead of computing a true dominator tree (which requires O(n^2) pre-computation),
 * this shows objects sorted by retained size, and allows expanding each object
 * to see its directly referenced objects also sorted by retained size.
 */
public class DominatorTreeAnalyzer {

    private static final Logger LOG = LoggerFactory.getLogger(DominatorTreeAnalyzer.class);
    private static final int DEFAULT_LIMIT = 50;
    private static final int CANDIDATE_CLASSES = 200;
    private static final int CANDIDATE_MULTIPLIER = 3;
    private static final int ROOT_OVERSELECT_MULTIPLIER = 2;

    private record CandidateEntry(Instance instance, long retainedSize, String fieldName) {
    }

    private record ChildCandidate(Instance instance, long shallowSize, String fieldName) {
    }

    /**
     * Computes the total overcount across all instances in the heap.
     * @see CompressedOopsCorrector#computeTotalOvercount(Heap)
     */
    public static long computeTotalOvercount(Heap heap) {
        return CompressedOopsCorrector.computeTotalOvercount(heap);
    }

    @SuppressWarnings("unchecked")
    private static Map<Long, String> buildGcRootKindMap(Heap heap) {
        Map<Long, String> map = new HashMap<>();
        Collection<GCRoot> gcRoots = (Collection<GCRoot>) heap.getGCRoots();
        for (GCRoot root : gcRoots) {
            Instance inst = root.getInstance();
            if (inst != null) {
                map.putIfAbsent(inst.getInstanceId(), root.getKind());
            }
        }
        return map;
    }

    /**
     * Get the top-level roots: biggest objects by retained size.
     * Uses a min-heap to efficiently select the top N objects from instances
     * of the top classes by total allocation size.
     */
    @SuppressWarnings("unchecked")
    public DominatorTreeResponse getRoots(Heap heap, int limit, boolean compressedOops, long totalOvercount) {
        if (limit <= 0) {
            limit = DEFAULT_LIMIT;
        }

        OQLEngine engine = new OQLEngine(heap);
        InstanceValueFormatter formatter = new InstanceValueFormatter(engine);
        long totalHeapSize = heap.getSummary().getTotalLiveBytes();

        // Compute heap-wide correction ratio for compressed oops
        double correctionRatio = 1.0;
        long correctedTotalHeap = totalHeapSize;
        if (compressedOops) {
            correctedTotalHeap = totalHeapSize - totalOvercount;
            correctionRatio = totalHeapSize > 0
                    ? (double) correctedTotalHeap / totalHeapSize
                    : 1.0;
        }

        // Get top classes by total size
        List<JavaClass> allClasses = (List<JavaClass>) heap.getAllClasses();
        List<JavaClass> candidates = allClasses.stream()
                .filter(c -> c.getInstancesCount() > 0)
                .sorted(Comparator.comparingLong(JavaClass::getAllInstancesSize).reversed())
                .limit(CANDIDATE_CLASSES)
                .toList();

        // Over-select candidates to compensate for filtering dominated entries
        int overselectLimit = ROOT_OVERSELECT_MULTIPLIER * limit;

        // Use a min-heap to keep only the top N by retained size
        PriorityQueue<CandidateEntry> minHeap = new PriorityQueue<>(
                overselectLimit + 1, Comparator.comparingLong(CandidateEntry::retainedSize));

        for (JavaClass javaClass : candidates) {
            List<Instance> instances = (List<Instance>) javaClass.getInstances();
            for (Instance instance : instances) {
                long retainedSize = instance.getRetainedSize();
                if (retainedSize <= 0) {
                    retainedSize = instance.getSize();
                }

                if (minHeap.size() < overselectLimit || retainedSize > minHeap.peek().retainedSize()) {
                    minHeap.offer(new CandidateEntry(instance, retainedSize, null));
                    if (minHeap.size() > overselectLimit) {
                        minHeap.poll();
                    }
                }
            }
        }

        // Filter out entries whose referrer is also a candidate (dominated objects)
        Set<Long> candidateIds = new HashSet<>();
        for (CandidateEntry entry : minHeap) {
            candidateIds.add(entry.instance().getInstanceId());
        }

        List<CandidateEntry> filtered = new ArrayList<>();
        for (CandidateEntry entry : minHeap) {
            if (!isDominatedByCandidate(entry.instance(), candidateIds)) {
                filtered.add(entry);
            }
        }

        // Cap to the requested limit
        filtered.sort(Comparator.comparingLong(CandidateEntry::retainedSize).reversed());
        if (filtered.size() > limit) {
            filtered = filtered.subList(0, limit);
        }

        // Build GC root kind map for annotating nodes
        Map<Long, String> gcRootKindMap = buildGcRootKindMap(heap);

        // Build DominatorNode only for the final set
        List<DominatorNode> nodes = new ArrayList<>(filtered.size());
        for (CandidateEntry entry : filtered) {
            Instance instance = entry.instance();
            long shallowSize = CompressedOopsCorrector.correctedShallowSize(instance, compressedOops);
            long retainedSize = (long) (entry.retainedSize() * correctionRatio);
            double percent = correctedTotalHeap > 0 ? (double) retainedSize / correctedTotalHeap * 100.0 : 0;
            boolean hasChildren = hasObjectReferences(instance);

            Map<String, String> objectParams = formatter.format(instance);
            nodes.add(new DominatorNode(
                    instance.getInstanceId(),
                    instance.getJavaClass().getName(),
                    objectParams,
                    null,
                    shallowSize,
                    retainedSize,
                    percent,
                    hasChildren,
                    gcRootKindMap.get(instance.getInstanceId())
            ));
        }

        // Already sorted above
        boolean hasMore = nodes.size() >= limit;

        return new DominatorTreeResponse(nodes, correctedTotalHeap, compressedOops, hasMore);
    }

    /**
     * Get children of a node: directly referenced objects sorted by retained size.
     * Uses a two-phase approach: first collects all children with shallow size (cheap),
     * over-selects candidates, then computes retained size only for the candidates.
     */
    @SuppressWarnings("unchecked")
    public DominatorTreeResponse getChildren(Heap heap, long objectId, int limit, boolean compressedOops, long totalOvercount) {
        if (limit <= 0) {
            limit = DEFAULT_LIMIT;
        }

        Instance parent = heap.getInstanceByID(objectId);
        if (parent == null) {
            return new DominatorTreeResponse(List.of(), 0, compressedOops, false);
        }

        OQLEngine engine = new OQLEngine(heap);
        InstanceValueFormatter formatter = new InstanceValueFormatter(engine);
        long totalHeapSize = heap.getSummary().getTotalLiveBytes();

        // Compute heap-wide correction ratio for compressed oops
        double correctionRatio = 1.0;
        if (compressedOops) {
            long correctedTotalHeap = totalHeapSize - totalOvercount;
            correctionRatio = totalHeapSize > 0
                    ? (double) correctedTotalHeap / totalHeapSize
                    : 1.0;
        }

        long parentRetainedSize = (long) (parent.getRetainedSize() * correctionRatio);

        // Phase 1: Collect all children with shallow size (cheap)
        List<ChildCandidate> allChildren = new ArrayList<>();

        List<FieldValue> fieldValues = (List<FieldValue>) parent.getFieldValues();
        for (FieldValue fv : fieldValues) {
            if (fv instanceof ObjectFieldValue ofv) {
                Instance ref = ofv.getInstance();
                if (ref != null) {
                    allChildren.add(new ChildCandidate(ref, ref.getSize(), ofv.getField().getName()));
                }
            }
        }

        if (parent instanceof org.netbeans.lib.profiler.heap.ObjectArrayInstance arrayInstance) {
            List<Instance> values = (List<Instance>) arrayInstance.getValues();
            for (int i = 0; i < values.size(); i++) {
                Instance element = values.get(i);
                if (element != null) {
                    allChildren.add(new ChildCandidate(element, element.getSize(), "[" + i + "]"));
                }
            }
        }

        // Phase 2: Over-select by shallow size
        int candidateCount = CANDIDATE_MULTIPLIER * limit;
        List<ChildCandidate> selected;
        if (allChildren.size() <= candidateCount) {
            selected = allChildren;
        } else {
            allChildren.sort(Comparator.comparingLong(ChildCandidate::shallowSize).reversed());
            selected = allChildren.subList(0, candidateCount);
        }

        // Phase 3: Compute retained size for candidates only, use min-heap for top N
        PriorityQueue<CandidateEntry> minHeap = new PriorityQueue<>(
                limit + 1, Comparator.comparingLong(CandidateEntry::retainedSize));

        for (ChildCandidate candidate : selected) {
            Instance instance = candidate.instance();
            long retainedSize = instance.getRetainedSize();
            if (retainedSize <= 0) {
                retainedSize = instance.getSize();
            }

            if (minHeap.size() < limit || retainedSize > minHeap.peek().retainedSize()) {
                minHeap.offer(new CandidateEntry(instance, retainedSize, candidate.fieldName()));
                if (minHeap.size() > limit) {
                    minHeap.poll();
                }
            }
        }

        // Build GC root kind map for annotating nodes
        Map<Long, String> gcRootKindMap = buildGcRootKindMap(heap);

        // Phase 4: Format only the final results
        List<DominatorNode> children = new ArrayList<>(minHeap.size());
        for (CandidateEntry entry : minHeap) {
            Instance ref = entry.instance();
            long shallowSize = CompressedOopsCorrector.correctedShallowSize(ref, compressedOops);
            long retainedSize = (long) (entry.retainedSize() * correctionRatio);
            double percent = parentRetainedSize > 0
                    ? (double) retainedSize / parentRetainedSize * 100.0 : 0;
            boolean hasChildRefs = hasObjectReferences(ref);

            Map<String, String> objectParams = formatter.format(ref);
            children.add(new DominatorNode(
                    ref.getInstanceId(),
                    ref.getJavaClass().getName(),
                    objectParams,
                    entry.fieldName(),
                    shallowSize,
                    retainedSize,
                    percent,
                    hasChildRefs,
                    gcRootKindMap.get(ref.getInstanceId())
            ));
        }

        // Sort by retained size descending
        children.sort(Comparator.comparingLong(DominatorNode::retainedSize).reversed());
        boolean hasMore = allChildren.size() > limit;

        return new DominatorTreeResponse(children, totalHeapSize, compressedOops, hasMore);
    }

    @SuppressWarnings("unchecked")
    private boolean isDominatedByCandidate(Instance instance, Set<Long> candidateIds) {
        List<Value> references = (List<Value>) instance.getReferences();
        for (Value ref : references) {
            Instance referrer = ref.getDefiningInstance();
            if (referrer != null && candidateIds.contains(referrer.getInstanceId())) {
                return true;
            }
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private boolean hasObjectReferences(Instance instance) {
        List<FieldValue> fieldValues = (List<FieldValue>) instance.getFieldValues();
        for (FieldValue fv : fieldValues) {
            if (fv instanceof ObjectFieldValue ofv) {
                if (ofv.getInstance() != null) {
                    return true;
                }
            }
        }
        if (instance instanceof org.netbeans.lib.profiler.heap.ObjectArrayInstance arrayInstance) {
            List<Instance> values = (List<Instance>) arrayInstance.getValues();
            for (Instance v : values) {
                if (v != null) return true;
            }
        }
        return false;
    }
}
