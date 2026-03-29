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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * Provides a dominator tree view of the heap.
 * <p>
 * For root nodes, uses a heuristic approach: selects the largest objects by retained size
 * from the top classes and filters out dominated entries.
 * <p>
 * For children, uses the true dominator tree computed by the NetBeans Profiler library
 * (accessed via {@link DominatorTreeReflection}). Each child is an object whose immediate
 * dominator is the parent node. Falls back to field-reference traversal if reflection
 * is unavailable.
 */
public class DominatorTreeAnalyzer {

    private static final Logger LOG = LoggerFactory.getLogger(DominatorTreeAnalyzer.class);
    private static final int DEFAULT_LIMIT = 50;
    private static final int CANDIDATE_CLASSES = 200;
    private static final int ROOT_OVERSELECT_MULTIPLIER = 2;

    private record CandidateEntry(Instance instance, long retainedSize, String fieldName) {
    }

    // Cached per-heap state to avoid expensive recomputation on every call
    private Heap cachedHeap;
    private DominatorTreeReflection cachedReflection;
    private Map<Long, String> cachedGcRootKindMap;
    private OQLEngine cachedOQLEngine;

    private DominatorTreeReflection getReflection(Heap heap) {
        if (cachedHeap != heap) {
            rebuildCache(heap);
        }
        return cachedReflection;
    }

    private Map<Long, String> getGcRootKindMap(Heap heap) {
        if (cachedHeap != heap) {
            rebuildCache(heap);
        }
        return cachedGcRootKindMap;
    }

    private OQLEngine getOQLEngine(Heap heap) {
        if (cachedHeap != heap) {
            rebuildCache(heap);
        }
        return cachedOQLEngine;
    }

    private synchronized void rebuildCache(Heap heap) {
        if (cachedHeap == heap) {
            return;
        }
        cachedReflection = new DominatorTreeReflection(heap);
        cachedGcRootKindMap = buildGcRootKindMap(heap);
        cachedOQLEngine = new OQLEngine(heap);
        cachedHeap = heap;
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

        InstanceValueFormatter formatter = new InstanceValueFormatter(getOQLEngine(heap));
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

        // Trigger retained size computation for the first instance to ensure dominator tree is built
        if (!candidates.isEmpty()) {
            List<Instance> firstInstances = (List<Instance>) candidates.get(0).getInstances();
            if (!firstInstances.isEmpty()) {
                firstInstances.get(0).getRetainedSize();
            }
        }

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

        // Filter out entries that are truly dominated by another candidate
        DominatorTreeReflection reflection = getReflection(heap);
        Set<Long> candidateIds = new HashSet<>();
        for (CandidateEntry entry : minHeap) {
            candidateIds.add(entry.instance().getInstanceId());
        }

        List<CandidateEntry> filtered = new ArrayList<>();
        for (CandidateEntry entry : minHeap) {
            if (!isDominatedByCandidate(entry.instance(), candidateIds, reflection)) {
                filtered.add(entry);
            }
        }

        // Cap to the requested limit
        filtered.sort(Comparator.comparingLong(CandidateEntry::retainedSize).reversed());
        if (filtered.size() > limit) {
            filtered = filtered.subList(0, limit);
        }

        // Use cached GC root kind map for annotating nodes
        Map<Long, String> gcRootKindMap = getGcRootKindMap(heap);

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
     * Get children of a node in the dominator tree: objects whose immediate dominator
     * is the given parent, sorted by retained size with offset-based pagination.
     * <p>
     * Uses a hybrid approach:
     * <ol>
     *   <li>Collects direct field references (for field name attribution)</li>
     *   <li>Queries the NetBeans DominatorTree via reflection for true dominated children</li>
     *   <li>Merges both sources, deduplicates, sorts by retained size, and returns the requested page</li>
     * </ol>
     * Falls back to field-reference-only behavior if reflection is unavailable.
     */
    @SuppressWarnings("unchecked")
    public DominatorTreeResponse getChildren(Heap heap, long objectId, int offset, int limit, boolean compressedOops, long totalOvercount) {
        if (limit <= 0) {
            limit = DEFAULT_LIMIT;
        }
        if (offset < 0) {
            offset = 0;
        }

        Instance parent = heap.getInstanceByID(objectId);
        if (parent == null) {
            return new DominatorTreeResponse(List.of(), 0, compressedOops, false);
        }

        InstanceValueFormatter formatter = new InstanceValueFormatter(getOQLEngine(heap));
        long totalHeapSize = heap.getSummary().getTotalLiveBytes();

        // Compute heap-wide correction ratio for compressed oops
        double correctionRatio = 1.0;
        if (compressedOops) {
            long correctedTotalHeap = totalHeapSize - totalOvercount;
            correctionRatio = totalHeapSize > 0
                    ? (double) correctedTotalHeap / totalHeapSize
                    : 1.0;
        }

        // Triggers retained size computation (and dominator tree construction) if not yet done
        long parentRetainedSize = (long) (parent.getRetainedSize() * correctionRatio);

        // Phase 1: Collect direct field references (for field name attribution)
        // LinkedHashMap preserves insertion order for stable field name lookup
        Map<Long, String> fieldRefNames = new LinkedHashMap<>();

        List<FieldValue> fieldValues = (List<FieldValue>) parent.getFieldValues();
        for (FieldValue fv : fieldValues) {
            if (fv instanceof ObjectFieldValue ofv) {
                Instance ref = ofv.getInstance();
                if (ref != null) {
                    fieldRefNames.putIfAbsent(ref.getInstanceId(), ofv.getField().getName());
                }
            }
        }

        if (parent instanceof org.netbeans.lib.profiler.heap.ObjectArrayInstance arrayInstance) {
            List<Instance> values = (List<Instance>) arrayInstance.getValues();
            for (int i = 0; i < values.size(); i++) {
                Instance element = values.get(i);
                if (element != null) {
                    fieldRefNames.putIfAbsent(element.getInstanceId(), "[" + i + "]");
                }
            }
        }

        // Phase 2: Get true dominated children from the dominator tree via reflection (cached)
        DominatorTreeReflection reflection = getReflection(heap);
        List<Long> dominatedIds = reflection.isAvailable()
                ? reflection.findDominatedChildren(objectId)
                : List.of();

        // Phase 3: Merge candidates (instanceId -> fieldName, null = dominated-only)
        Map<Long, String> candidates = new HashMap<>();

        for (Map.Entry<Long, String> entry : fieldRefNames.entrySet()) {
            long childId = entry.getKey();
            String fieldName = entry.getValue();

            if (!reflection.isAvailable()) {
                // Fallback: include all field references (original behavior)
                candidates.put(childId, fieldName);
            } else {
                long idom = reflection.getIdom(childId);
                if (idom == -1) {
                    // Single-parent instance: idom is its sole referrer (the parent)
                    candidates.put(childId, fieldName);
                } else if (idom == objectId) {
                    // Multi-parent instance dominated by this parent
                    candidates.put(childId, fieldName);
                }
                // else: multi-parent instance dominated by someone else -> skip
            }
        }

        // Add dominated children not found via field references
        for (long dominatedId : dominatedIds) {
            candidates.putIfAbsent(dominatedId, null);
        }

        // Phase 4: Compute retained size for all candidates and sort by retained size descending
        List<CandidateEntry> allSorted = new ArrayList<>(candidates.size());
        for (Map.Entry<Long, String> entry : candidates.entrySet()) {
            Instance instance = heap.getInstanceByID(entry.getKey());
            if (instance == null) {
                continue;
            }

            long retainedSize = instance.getRetainedSize();
            if (retainedSize <= 0) {
                retainedSize = instance.getSize();
            }
            allSorted.add(new CandidateEntry(instance, retainedSize, entry.getValue()));
        }
        allSorted.sort(Comparator.comparingLong(CandidateEntry::retainedSize).reversed());

        // Phase 5: Apply offset and limit (pagination)
        int totalCount = allSorted.size();
        int fromIndex = Math.min(offset, totalCount);
        int toIndex = Math.min(offset + limit, totalCount);
        List<CandidateEntry> page = allSorted.subList(fromIndex, toIndex);

        // Use cached GC root kind map for annotating nodes
        Map<Long, String> gcRootKindMap = getGcRootKindMap(heap);

        // Phase 6: Format the results for this page
        List<DominatorNode> children = new ArrayList<>(page.size());
        for (CandidateEntry entry : page) {
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

        boolean hasMore = toIndex < totalCount;
        return new DominatorTreeResponse(children, totalHeapSize, compressedOops, hasMore);
    }

    /**
     * Checks if an instance is truly dominated by another candidate using the dominator tree.
     * Falls back to referrer heuristic when reflection is unavailable.
     */
    @SuppressWarnings("unchecked")
    private boolean isDominatedByCandidate(Instance instance, Set<Long> candidateIds,
                                           DominatorTreeReflection reflection) {
        long instanceId = instance.getInstanceId();

        if (reflection.isAvailable()) {
            // Use true dominator: check if the immediate dominator is in the candidate set
            long idom = reflection.getIdom(instanceId);
            if (idom > 0) {
                return candidateIds.contains(idom);
            }
            // idom == -1 means single-parent; check the sole referrer
            List<Value> references = (List<Value>) instance.getReferences();
            if (references.size() == 1) {
                Instance referrer = references.get(0).getDefiningInstance();
                return referrer != null && candidateIds.contains(referrer.getInstanceId());
            }
            return false;
        }

        // Fallback: referrer heuristic (original behavior)
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
