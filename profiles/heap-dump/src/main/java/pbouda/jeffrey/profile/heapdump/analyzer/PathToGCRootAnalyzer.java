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
import org.netbeans.lib.profiler.heap.JavaFrameGCRoot;
import org.netbeans.lib.profiler.heap.ObjectFieldValue;
import org.netbeans.lib.profiler.heap.ThreadObjectGCRoot;
import org.netbeans.lib.profiler.heap.Value;
import org.netbeans.modules.profiler.oql.engine.api.OQLEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pbouda.jeffrey.profile.heapdump.model.GCRootPath;
import pbouda.jeffrey.profile.heapdump.model.PathStep;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

/**
 * Finds the shortest reference chain(s) from GC roots to a target object.
 * Uses BFS traversal from the target object backwards through referrers
 * to find the nearest GC root.
 */
public class PathToGCRootAnalyzer {

    private static final Logger LOG = LoggerFactory.getLogger(PathToGCRootAnalyzer.class);

    private static final int MAX_DEPTH = 100;
    private static final Set<String> WEAK_REF_CLASSES = Set.of(
            "java.lang.ref.WeakReference",
            "java.lang.ref.SoftReference",
            "java.lang.ref.PhantomReference",
            "java.lang.ref.FinalReference",
            "java.lang.ref.Finalizer"
    );

    /**
     * Find shortest paths from GC roots to the target object.
     *
     * @param heap            the loaded heap dump
     * @param objectId        target object ID
     * @param excludeWeakRefs whether to exclude paths through weak/soft/phantom references
     * @param maxPaths        maximum number of paths to return
     * @return list of GC root paths (shortest first)
     */
    @SuppressWarnings("unchecked")
    public List<GCRootPath> findPaths(Heap heap, long objectId, boolean excludeWeakRefs, int maxPaths) {
        Instance target = heap.getInstanceByID(objectId);
        if (target == null) {
            LOG.warn("Instance not found: objectId={}", objectId);
            return List.of();
        }

        // Build GC root instance ID set for fast lookup
        Set<Long> gcRootIds = new HashSet<>();
        Map<Long, GCRoot> gcRootsByInstanceId = new HashMap<>();
        Collection<GCRoot> gcRoots = (Collection<GCRoot>) heap.getGCRoots();
        for (GCRoot root : gcRoots) {
            Instance rootInstance = root.getInstance();
            if (rootInstance != null) {
                gcRootIds.add(rootInstance.getInstanceId());
                gcRootsByInstanceId.put(rootInstance.getInstanceId(), root);
            }
        }

        OQLEngine engine = new OQLEngine(heap);
        InstanceValueFormatter formatter = new InstanceValueFormatter(engine);

        List<GCRootPath> results = new ArrayList<>();

        // BFS from target backwards through referrers
        Queue<List<Instance>> queue = new ArrayDeque<>();
        Set<Long> visited = new HashSet<>();
        visited.add(target.getInstanceId());

        List<Instance> initialPath = new ArrayList<>();
        initialPath.add(target);
        queue.add(initialPath);

        while (!queue.isEmpty() && results.size() < maxPaths) {
            List<Instance> path = queue.poll();

            if (path.size() > MAX_DEPTH) {
                continue;
            }

            Instance current = path.getLast();

            // Check if current is a GC root
            if (gcRootIds.contains(current.getInstanceId())) {
                GCRootPath gcRootPath = buildGCRootPath(
                        path, gcRootsByInstanceId.get(current.getInstanceId()), formatter);
                results.add(gcRootPath);
                continue;
            }

            // Traverse referrers — getReferences() returns List<Value>, use getDefiningInstance()
            // to get the object that owns the reference (the actual referrer), not the referenced object
            @SuppressWarnings("unchecked")
            List<Value> references = (List<Value>) current.getReferences();
            for (Value ref : references) {
                Instance referrer = ref.getDefiningInstance();
                if (referrer == null) continue;

                long refId = referrer.getInstanceId();
                if (visited.contains(refId)) continue;

                if (excludeWeakRefs && isWeakReference(referrer)) {
                    continue;
                }

                visited.add(refId);
                List<Instance> newPath = new ArrayList<>(path);
                newPath.add(referrer);
                queue.add(newPath);
            }
        }

        LOG.debug("Path to GC root analysis: objectId={} pathsFound={}", objectId, results.size());
        return results;
    }

    private boolean isWeakReference(Instance instance) {
        String className = instance.getJavaClass().getName();
        if (WEAK_REF_CLASSES.contains(className)) {
            return true;
        }
        // Check superclasses
        var superClass = instance.getJavaClass().getSuperClass();
        while (superClass != null) {
            if (WEAK_REF_CLASSES.contains(superClass.getName())) {
                return true;
            }
            superClass = superClass.getSuperClass();
        }
        return false;
    }

    private GCRootPath buildGCRootPath(List<Instance> path, GCRoot gcRoot, InstanceValueFormatter formatter) {
        // Path is from target → ... → GC root, reverse it to root → ... → target
        List<Instance> reversedPath = new ArrayList<>(path);
        Collections.reverse(reversedPath);

        Instance root = reversedPath.getFirst();
        List<PathStep> steps = new ArrayList<>();

        for (int i = 0; i < reversedPath.size(); i++) {
            Instance current = reversedPath.get(i);
            String fieldName = null;

            // Find the field name connecting this instance to the next one in the chain
            if (i < reversedPath.size() - 1) {
                Instance next = reversedPath.get(i + 1);
                fieldName = findFieldName(current, next);
            }

            steps.add(new PathStep(
                    current.getInstanceId(),
                    current.getJavaClass().getName(),
                    fieldName,
                    current.getSize(),
                    formatter.format(current),
                    i == reversedPath.size() - 1
            ));
        }

        String rootKind = gcRoot != null ? gcRoot.getKind() : "Unknown";
        String threadName = null;
        String stackFrame = null;

        if (gcRoot instanceof JavaFrameGCRoot jf) {
            ThreadObjectGCRoot threadRoot = jf.getThreadGCRoot();
            if (threadRoot != null) {
                Instance threadInstance = threadRoot.getInstance();
                if (threadInstance != null) {
                    threadName = extractThreadName(threadInstance, formatter);
                }

                StackTraceElement[] stackTrace = threadRoot.getStackTrace();
                int frameNumber = jf.getFrameNumber();
                if (stackTrace != null && frameNumber >= 0 && frameNumber < stackTrace.length) {
                    stackFrame = stackTrace[frameNumber].toString();
                }
            }
        }

        return new GCRootPath(
                root.getInstanceId(),
                root.getJavaClass().getName(),
                rootKind,
                threadName,
                stackFrame,
                steps
        );
    }

    private String extractThreadName(Instance threadInstance, InstanceValueFormatter formatter) {
        Object nameField = threadInstance.getValueOfField("name");
        if (nameField instanceof Instance nameInstance
                && "java.lang.String".equals(nameInstance.getJavaClass().getName())) {
            String formatted = formatter.format(nameInstance);
            // Strip surrounding quotes added by the formatter
            if (formatted.startsWith("\"") && formatted.endsWith("\"") && formatted.length() >= 2) {
                return formatted.substring(1, formatted.length() - 1);
            }
            return formatted;
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private String findFieldName(Instance from, Instance to) {
        long targetId = to.getInstanceId();

        List<FieldValue> fieldValues = (List<FieldValue>) from.getFieldValues();
        for (FieldValue fv : fieldValues) {
            if (fv instanceof ObjectFieldValue ofv) {
                Instance ref = ofv.getInstance();
                if (ref != null && ref.getInstanceId() == targetId) {
                    return "." + fv.getField().getName();
                }
            }
        }

        // Check if it's an array element
        if (from instanceof org.netbeans.lib.profiler.heap.ObjectArrayInstance arrayInstance) {
            List<Instance> values = (List<Instance>) arrayInstance.getValues();
            for (int i = 0; i < values.size(); i++) {
                Instance element = values.get(i);
                if (element != null && element.getInstanceId() == targetId) {
                    return "[" + i + "]";
                }
            }
        }

        return null;
    }
}
