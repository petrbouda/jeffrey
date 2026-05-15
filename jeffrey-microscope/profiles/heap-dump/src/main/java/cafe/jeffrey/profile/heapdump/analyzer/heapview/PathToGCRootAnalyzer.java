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
package cafe.jeffrey.profile.heapdump.analyzer.heapview;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import cafe.jeffrey.profile.heapdump.model.GCRootPath;
import cafe.jeffrey.profile.heapdump.model.PathStep;
import cafe.jeffrey.profile.heapdump.parser.HeapView;
import cafe.jeffrey.profile.heapdump.parser.HprofTag;
import cafe.jeffrey.profile.heapdump.parser.InstanceFieldDescriptor;
import cafe.jeffrey.profile.heapdump.parser.InstanceRow;
import cafe.jeffrey.profile.heapdump.parser.JavaClassRow;
import cafe.jeffrey.profile.heapdump.parser.OutboundRefRow;

/**
 * HeapView-backed equivalent of
 * {@link cafe.jeffrey.profile.heapdump.analyzer.PathToGCRootAnalyzer}.
 *
 * Reverse BFS from the target object via {@code outbound_ref} (queried as
 * inbound — DuckDB's idx_outbound_target makes this cheap). Walks until a GC
 * root is reached or {@code MAX_DEPTH} is exceeded, then reconstructs the
 * path forward to the target.
 *
 * Limitations vs the NetBeans-backed version:
 * <ul>
 *   <li>{@code retainedSize} on each {@link PathStep} is 0 — populated in
 *       PR #12 once the dominator infrastructure lands.</li>
 *   <li>{@code threadName} and {@code stackFrame} on thread-rooted paths are
 *       null until PR #10's {@link cafe.jeffrey.profile.heapdump.analyzer.heapview.ThreadAnalyzer}
 *       is in and the necessary {@code Thread.name} decoder ships with it.</li>
 *   <li>Weak-reference exclusion is best-effort by class name only — the
 *       NetBeans path also walks the super-class chain; we do that via
 *       {@link HeapView#findClassById}.</li>
 * </ul>
 * The path itself (root → target with field names) is exact.
 */
public final class PathToGCRootAnalyzer {

    static final int MAX_DEPTH = 100;

    private static final Set<String> WEAK_REF_CLASSES = Set.of(
            "java.lang.ref.WeakReference",
            "java.lang.ref.SoftReference",
            "java.lang.ref.PhantomReference",
            "java.lang.ref.FinalReference",
            "java.lang.ref.Finalizer");

    private PathToGCRootAnalyzer() {
    }

    public static List<GCRootPath> findPaths(HeapView view, long targetId, boolean excludeWeakRefs, int maxPaths)
            throws SQLException {
        if (maxPaths <= 0) {
            throw new IllegalArgumentException("maxPaths must be positive: maxPaths=" + maxPaths);
        }
        InstanceRow target = view.findInstanceById(targetId).orElse(null);
        if (target == null) {
            return List.of();
        }

        // Cheap GC-root lookup via a single SELECT.
        Map<Long, Integer> rootKindByInstance = loadGcRoots(view);

        // Cache class metadata + weak-ref decisions so we don't re-query for repeat ids.
        Map<Long, JavaClassRow> classCache = new HashMap<>();
        Map<Long, Boolean> weakRefCache = new HashMap<>();

        // BFS frontier carries (currentInstanceId, parentInstanceId, refUsedToReach).
        // We track per-instance parents to reconstruct the eventual path.
        Map<Long, ParentLink> parents = new HashMap<>();
        parents.put(targetId, null);
        Queue<Long> queue = new ArrayDeque<>();
        queue.add(targetId);

        List<Long> foundRoots = new ArrayList<>();
        Map<Long, Integer> depths = new HashMap<>();
        depths.put(targetId, 0);

        while (!queue.isEmpty() && foundRoots.size() < maxPaths) {
            long current = queue.poll();
            int depth = depths.get(current);
            if (depth >= MAX_DEPTH) {
                continue;
            }
            // Don't treat the target itself as a root if it happens to be one.
            if (current != targetId && rootKindByInstance.containsKey(current)) {
                foundRoots.add(current);
                continue;
            }
            for (OutboundRefRow ref : view.inboundRefs(current)) {
                long parent = ref.sourceId();
                if (parents.containsKey(parent)) {
                    continue;
                }
                if (excludeWeakRefs && isWeakReferenceClass(view, parent, classCache, weakRefCache)) {
                    continue;
                }
                parents.put(parent, new ParentLink(current, ref));
                depths.put(parent, depth + 1);
                queue.add(parent);
            }
        }

        List<GCRootPath> paths = new ArrayList<>();
        for (long root : foundRoots) {
            paths.add(reconstructPath(view, targetId, root, parents, rootKindByInstance, classCache));
        }
        return paths;
    }

    private static Map<Long, Integer> loadGcRoots(HeapView view) throws SQLException {
        Map<Long, Integer> out = new HashMap<>();
        try (Statement stmt = view.databaseClient().connection().createStatement();
             ResultSet rs = stmt.executeQuery("SELECT instance_id, root_kind FROM gc_root")) {
            while (rs.next()) {
                // If an instance is rooted multiple ways, the most recent kind wins — fine for path display.
                out.put(rs.getLong(1), rs.getInt(2));
            }
        }
        return out;
    }

    private static boolean isWeakReferenceClass(
            HeapView view, long instanceId, Map<Long, JavaClassRow> classCache,
            Map<Long, Boolean> weakRefCache) throws SQLException {
        InstanceRow inst = view.findInstanceById(instanceId).orElse(null);
        if (inst == null || inst.classId() == null) {
            return false;
        }
        Long classId = inst.classId();
        Boolean cached = weakRefCache.get(classId);
        if (cached != null) {
            return cached;
        }
        long current = classId;
        boolean weak = false;
        while (current != 0L) {
            JavaClassRow cls = classCache.computeIfAbsent(current, id -> {
                try {
                    return view.findClassById(id).orElse(null);
                } catch (SQLException e) {
                    throw new RuntimeException(e);
                }
            });
            if (cls == null) {
                break;
            }
            if (WEAK_REF_CLASSES.contains(cls.name())) {
                weak = true;
                break;
            }
            current = cls.superClassId() == null ? 0L : cls.superClassId();
        }
        weakRefCache.put(classId, weak);
        return weak;
    }

    private static GCRootPath reconstructPath(
            HeapView view, long targetId, long rootId, Map<Long, ParentLink> parents,
            Map<Long, Integer> rootKindByInstance, Map<Long, JavaClassRow> classCache) throws SQLException {
        // Walk from root forward to target by following the parent chain.
        List<Long> ids = new ArrayList<>();
        List<OutboundRefRow> edges = new ArrayList<>();
        long cursor = rootId;
        while (cursor != targetId) {
            ParentLink link = parents.get(cursor);
            if (link == null) {
                break; // shouldn't happen if BFS reached cursor
            }
            ids.add(cursor);
            edges.add(link.refToChild());
            cursor = link.child();
        }
        ids.add(targetId);
        // edges[i] is the outbound ref from ids[i] to ids[i+1]. ids has one more entry than edges.

        List<PathStep> steps = new ArrayList<>(ids.size());
        for (int i = 0; i < ids.size(); i++) {
            long id = ids.get(i);
            String fieldName = i + 1 < ids.size() ? resolveFieldName(view, id, edges.get(i), classCache) : null;
            steps.add(buildStep(view, id, fieldName, id == targetId, classCache));
        }
        // ids are already root→target by construction (BFS reverses parent-pointers).

        int rootKind = rootKindByInstance.getOrDefault(rootId, HprofTag.Sub.ROOT_UNKNOWN);
        String rootClassName = optionalClassName(view, rootId, classCache);
        return new GCRootPath(
                rootId,
                rootClassName,
                HprofTag.Sub.rootKindName(rootKind),
                null,           // threadName — populated once ThreadAnalyzer (PR #10) lands
                null,           // stackFrame — same
                steps);
    }

    private static String resolveFieldName(
            HeapView view, long sourceInstanceId, OutboundRefRow ref, Map<Long, JavaClassRow> classCache)
            throws SQLException {
        if (ref.fieldKind() == 1) {
            return "[" + ref.fieldId() + "]";
        }
        // Instance field — walk the source instance's class chain to find the field at this global index.
        InstanceRow source = view.findInstanceById(sourceInstanceId).orElse(null);
        if (source == null || source.classId() == null) {
            return "?";
        }
        List<InstanceFieldDescriptor> chain = view.instanceFieldsWithChain(source.classId());
        int idx = ref.fieldId();
        if (idx < 0 || idx >= chain.size()) {
            return "?[" + idx + "]";
        }
        return chain.get(idx).name();
    }

    private static PathStep buildStep(
            HeapView view, long instanceId, String fieldName, boolean isTarget,
            Map<Long, JavaClassRow> classCache) throws SQLException {
        InstanceRow inst = view.findInstanceById(instanceId).orElse(null);
        long shallow = inst != null ? inst.shallowSize() : 0L;
        String className = inst != null && inst.classId() != null
                ? Optional.ofNullable(classCache.computeIfAbsent(inst.classId(), id -> {
                    try {
                        return view.findClassById(id).orElse(null);
                    } catch (SQLException e) {
                        throw new RuntimeException(e);
                    }
                })).map(JavaClassRow::name).orElse("<unknown>")
                : "<unknown>";
        // retainedSize 0 until PR #12; objectParams empty (NetBeans path populates display strings here).
        return new PathStep(instanceId, className, fieldName, shallow, 0L, Map.of(), isTarget);
    }

    private static String optionalClassName(
            HeapView view, long instanceId, Map<Long, JavaClassRow> classCache) throws SQLException {
        InstanceRow inst = view.findInstanceById(instanceId).orElse(null);
        if (inst == null || inst.classId() == null) {
            // GC roots can be classes themselves (ROOT_STICKY_CLASS), which means the
            // instance-table row is missing but the class table has the entry.
            try (PreparedStatement stmt = view.databaseClient().connection().prepareStatement(
                    "SELECT name FROM class WHERE class_id = ?")) {
                stmt.setLong(1, instanceId);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return rs.getString(1);
                    }
                }
            }
            return "<unknown>";
        }
        return Optional.ofNullable(classCache.computeIfAbsent(inst.classId(), id -> {
            try {
                return view.findClassById(id).orElse(null);
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        })).map(JavaClassRow::name).orElse("<unknown>");
    }

    /** A back-pointer in the BFS: this node was reached via {@code refToChild} from {@code child}. */
    private record ParentLink(long child, OutboundRefRow refToChild) {
    }
}
