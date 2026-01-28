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
import org.netbeans.lib.profiler.heap.Heap;
import org.netbeans.lib.profiler.heap.Instance;
import org.netbeans.lib.profiler.heap.JavaClass;
import org.netbeans.lib.profiler.heap.ObjectFieldValue;
import org.netbeans.modules.profiler.oql.engine.api.OQLEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pbouda.jeffrey.profile.heapdump.model.DominatorNode;
import pbouda.jeffrey.profile.heapdump.model.DominatorTreeResponse;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Provides a retained-size tree view of the heap.
 * Instead of computing a true dominator tree (which requires O(n^2) pre-computation),
 * this shows objects sorted by retained size, and allows expanding each object
 * to see its directly referenced objects also sorted by retained size.
 */
public class DominatorTreeAnalyzer {

    private static final Logger LOG = LoggerFactory.getLogger(DominatorTreeAnalyzer.class);
    private static final int DEFAULT_LIMIT = 50;
    private static final int CANDIDATE_CLASSES = 1;

    /**
     * Get the top-level roots: biggest objects by retained size.
     * This reuses the same approach as BiggestObjectsAnalyzer.
     */
    @SuppressWarnings("unchecked")
    public DominatorTreeResponse getRoots(Heap heap, int limit) {
        if (limit <= 0) {
            limit = DEFAULT_LIMIT;
        }

        OQLEngine engine = new OQLEngine(heap);
        InstanceValueFormatter formatter = new InstanceValueFormatter(engine);
        long totalHeapSize = heap.getSummary().getTotalLiveBytes();

        // Get top classes by total size
        List<JavaClass> allClasses = (List<JavaClass>) heap.getAllClasses();
        List<JavaClass> candidates = allClasses.stream()
                .filter(c -> c.getInstancesCount() > 0)
                .sorted(Comparator.comparingLong(JavaClass::getAllInstancesSize).reversed())
                .limit(CANDIDATE_CLASSES)
                .toList();

        // Compute retained size for instances from top classes
        List<DominatorNode> nodes = new ArrayList<>();
        for (JavaClass javaClass : candidates) {
            List<Instance> instances = (List<Instance>) javaClass.getInstances();
            for (Instance instance : instances) {
                long retainedSize = instance.getRetainedSize();
                if (retainedSize <= 0) {
                    retainedSize = instance.getSize();
                }
                double percent = totalHeapSize > 0 ? (double) retainedSize / totalHeapSize * 100.0 : 0;
                boolean hasChildren = hasObjectReferences(instance);

                nodes.add(new DominatorNode(
                        instance.getInstanceId(),
                        javaClass.getName(),
                        formatter.format(instance),
                        instance.getSize(),
                        retainedSize,
                        percent,
                        hasChildren
                ));
            }
        }

        // Sort by retained size descending and take top N
        nodes.sort(Comparator.comparingLong(DominatorNode::retainedSize).reversed());
        boolean hasMore = nodes.size() > limit;
        List<DominatorNode> result = nodes.subList(0, Math.min(limit, nodes.size()));

        return new DominatorTreeResponse(new ArrayList<>(result), totalHeapSize, hasMore);
    }

    /**
     * Get children of a node: directly referenced objects sorted by retained size.
     */
    @SuppressWarnings("unchecked")
    public DominatorTreeResponse getChildren(Heap heap, long objectId, int limit) {
        if (limit <= 0) {
            limit = DEFAULT_LIMIT;
        }

        Instance parent = heap.getInstanceByID(objectId);
        if (parent == null) {
            return new DominatorTreeResponse(List.of(), 0, false);
        }

        OQLEngine engine = new OQLEngine(heap);
        InstanceValueFormatter formatter = new InstanceValueFormatter(engine);
        long parentRetainedSize = parent.getRetainedSize();
        long totalHeapSize = heap.getSummary().getTotalLiveBytes();

        List<DominatorNode> children = new ArrayList<>();

        // Get referenced objects from fields
        List<FieldValue> fieldValues = (List<FieldValue>) parent.getFieldValues();
        for (FieldValue fv : fieldValues) {
            if (fv instanceof ObjectFieldValue ofv) {
                Instance ref = ofv.getInstance();
                if (ref != null) {
                    long retainedSize = ref.getRetainedSize();
                    if (retainedSize <= 0) {
                        retainedSize = ref.getSize();
                    }
                    double percent = parentRetainedSize > 0
                            ? (double) retainedSize / parentRetainedSize * 100.0 : 0;
                    boolean hasChildRefs = hasObjectReferences(ref);

                    children.add(new DominatorNode(
                            ref.getInstanceId(),
                            ref.getJavaClass().getName(),
                            formatter.format(ref),
                            ref.getSize(),
                            retainedSize,
                            percent,
                            hasChildRefs
                    ));
                }
            }
        }

        // Handle array elements
        if (parent instanceof org.netbeans.lib.profiler.heap.ObjectArrayInstance arrayInstance) {
            List<Instance> values = (List<Instance>) arrayInstance.getValues();
            for (Instance element : values) {
                if (element != null) {
                    long retainedSize = element.getRetainedSize();
                    if (retainedSize <= 0) {
                        retainedSize = element.getSize();
                    }
                    double percent = parentRetainedSize > 0
                            ? (double) retainedSize / parentRetainedSize * 100.0 : 0;
                    boolean hasChildRefs = hasObjectReferences(element);

                    children.add(new DominatorNode(
                            element.getInstanceId(),
                            element.getJavaClass().getName(),
                            formatter.format(element),
                            element.getSize(),
                            retainedSize,
                            percent,
                            hasChildRefs
                    ));
                }
            }
        }

        // Sort by retained size descending
        children.sort(Comparator.comparingLong(DominatorNode::retainedSize).reversed());
        boolean hasMore = children.size() > limit;
        List<DominatorNode> result = children.subList(0, Math.min(limit, children.size()));

        return new DominatorTreeResponse(new ArrayList<>(result), totalHeapSize, hasMore);
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
