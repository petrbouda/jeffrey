/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package cafe.jeffrey.profile.heapdump.analyzer.heapview;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import cafe.jeffrey.profile.heapdump.model.GCRootPath;
import cafe.jeffrey.profile.heapdump.model.PathStep;
import cafe.jeffrey.profile.heapdump.view.HeapView;
import cafe.jeffrey.profile.heapdump.view.HprofTag;
import cafe.jeffrey.profile.heapdump.view.InstanceFieldDescriptor;
import cafe.jeffrey.profile.heapdump.view.InstanceRow;
import cafe.jeffrey.profile.heapdump.view.JavaClassRow;
import cafe.jeffrey.profile.heapdump.view.OutboundRefRow;

/**
 * HeapView-backed equivalent of
 * {@link cafe.jeffrey.profile.heapdump.analyzer.PathToGCRootAnalyzer}.
 *
 * Breadth-first search backwards from the target along {@code outbound_ref}, until
 * {@code maxPaths} GC roots are reached or {@code MAX_DEPTH} is exceeded, then the path
 * forward from each root to the target. Being breadth-first, the paths found are the
 * shortest ones, shortest first.
 *
 * The whole search is one recursive query ({@link #FIND_PATHS_SQL}) rather than a walk
 * in Java issuing a query per visited instance: a target deep in a large heap visits
 * hundreds of thousands of instances, and the round trips, not the walk, were the cost --
 * on a 144 MB dump the slowest target took 5.4 s that way and takes 0.2 s this way. It
 * leans on DuckDB 2.0's rewritten recursive CTEs: on 1.5 the same query took 2.4 s there.
 *
 * Limitations vs the NetBeans-backed version:
 * <ul>
 *   <li>{@code retainedSize} on each {@link PathStep} is 0 — populated in
 *       PR #12 once the dominator infrastructure lands.</li>
 *   <li>{@code threadName} and {@code stackFrame} on thread-rooted paths are
 *       null until PR #10's {@link cafe.jeffrey.profile.heapdump.analyzer.heapview.ThreadAnalyzer}
 *       is in and the necessary {@code Thread.name} decoder ships with it.</li>
 *   <li>Weak-reference exclusion is best-effort by class name only, the classes below and
 *       every subclass of them.</li>
 * </ul>
 * The path itself (root → target with field names) is exact.
 */
public final class PathToGCRootAnalyzer {

    /** How many hops back from the target the search walks before it gives up on a branch. */
    public static final int MAX_DEPTH = 100;

    private static final Set<String> WEAK_REF_CLASSES = Set.of(
            "java.lang.ref.WeakReference",
            "java.lang.ref.SoftReference",
            "java.lang.ref.PhantomReference",
            "java.lang.ref.FinalReference",
            "java.lang.ref.Finalizer");

    private static final String WEAK_REF_CLASS_LITERALS = WEAK_REF_CLASSES.stream()
            .sorted()
            .map(name -> "'" + name + "'")
            .collect(Collectors.joining(", "));

    /**
     * The search, parameterised by target, depth limit, path limit and whether weak references
     * are skipped, answering one row per step of each found path, root first.
     *
     * <ul>
     *   <li>{@code weak_class} -- the weak-reference classes and all their subclasses; empty
     *       unless they are excluded. An instance of one is never a step on a path.</li>
     *   <li>{@code walk} -- the BFS. {@code USING KEY (instance_id)} makes the recursion's state
     *       one row per instance, which is the visited set: an instance already in
     *       {@code recurring.walk} is not reached again. Each row keeps the reference it was
     *       reached through ({@code child_id}, {@code field_kind}, {@code field_id}); when one
     *       level reaches an instance through several references the lowest child id wins, so
     *       the answer does not depend on the engine's scheduling. A root is not walked past, and
     *       the next level is not started once {@code max_paths} roots are in hand -- the search
     *       answers the shortest paths, so it stops where the Java walk it replaces stopped. The
     *       target itself never counts as a root: it is the start, not a path.</li>
     *   <li>{@code chosen} -- the shallowest {@code max_paths} roots, ties by id.</li>
     *   <li>{@code path} -- each root's chain of {@code child_id}s forward to the target.</li>
     * </ul>
     * A root is accepted only below {@link #MAX_DEPTH} hops, as it was when the walk lived in
     * Java: the last level the search starts is {@code MAX_DEPTH - 2}, whose instances are one
     * hop short of the limit.
     */
    private static final String FIND_PATHS_SQL = """
            WITH RECURSIVE search AS (
                SELECT ?::BIGINT AS target_id, ?::INTEGER AS max_depth, ?::BIGINT AS max_paths,
                       ?::BOOLEAN AS exclude_weak
            ),
            weak_class(class_id) AS (
                SELECT c.class_id
                FROM class c, search s
                WHERE s.exclude_weak AND c.name IN (%s)
                UNION
                SELECT c.class_id
                FROM class c
                JOIN weak_class w ON c.super_class_id = w.class_id
            ),
            walk(instance_id, child_id, field_kind, field_id, depth, is_root) USING KEY (instance_id) AS (
                SELECT target_id, NULL::BIGINT, NULL::TINYINT, NULL::INTEGER, 0, FALSE
                FROM search
                UNION
                SELECT r.source_id,
                       arg_min(r.target_id, (r.target_id, r.field_id)),
                       arg_min(r.field_kind, (r.target_id, r.field_id)),
                       arg_min(r.field_id, (r.target_id, r.field_id)),
                       min(w.depth) + 1,
                       r.source_id IN (SELECT instance_id FROM gc_root)
                FROM walk w
                CROSS JOIN search s
                JOIN outbound_ref r ON r.target_id = w.instance_id
                LEFT JOIN instance i ON i.instance_id = r.source_id
                WHERE NOT w.is_root
                  AND w.depth < s.max_depth - 1
                  AND r.source_id NOT IN (SELECT instance_id FROM recurring.walk)
                  AND (i.class_id IS NULL OR i.class_id NOT IN (SELECT class_id FROM weak_class))
                  AND (SELECT count(*) FROM recurring.walk x WHERE x.is_root) < s.max_paths
                GROUP BY r.source_id
            ),
            chosen AS (
                SELECT w.instance_id AS root_id, row_number() OVER (ORDER BY w.depth, w.instance_id) AS path_no
                FROM walk w
                WHERE w.is_root
                ORDER BY w.depth, w.instance_id
                LIMIT (SELECT max_paths FROM search)
            ),
            path(path_no, step, instance_id, child_id, field_kind, field_id) AS (
                SELECT ch.path_no, 0, w.instance_id, w.child_id, w.field_kind, w.field_id
                FROM chosen ch
                JOIN walk w ON w.instance_id = ch.root_id
                UNION ALL
                SELECT p.path_no, p.step + 1, w.instance_id, w.child_id, w.field_kind, w.field_id
                FROM path p
                JOIN walk w ON w.instance_id = p.child_id
            )
            SELECT p.path_no, p.instance_id, p.field_kind, p.field_id,
                   (SELECT min(g.root_kind) FROM gc_root g WHERE g.instance_id = p.instance_id AND p.step = 0)
            FROM path p
            ORDER BY p.path_no, p.step
            """.formatted(WEAK_REF_CLASS_LITERALS);

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

        Map<Long, JavaClassRow> classCache = new HashMap<>();
        List<GCRootPath> paths = new ArrayList<>();
        for (List<PathRow> rows : searchPaths(view, targetId, excludeWeakRefs, maxPaths)) {
            paths.add(toPath(view, targetId, rows, classCache));
        }
        return paths;
    }

    /** Each found path's steps, root first, in the order the search ranked the paths. */
    private static List<List<PathRow>> searchPaths(
            HeapView view, long targetId, boolean excludeWeakRefs, int maxPaths) throws SQLException {
        Map<Long, List<PathRow>> byPath = new LinkedHashMap<>();
        try (PreparedStatement stmt = view.databaseClient().connection().prepareStatement(FIND_PATHS_SQL)) {
            stmt.setLong(1, targetId);
            stmt.setInt(2, MAX_DEPTH);
            stmt.setLong(3, maxPaths);
            stmt.setBoolean(4, excludeWeakRefs);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    PathRow row = new PathRow(
                            rs.getLong(2),
                            rs.getObject(3, Integer.class),
                            rs.getObject(4, Integer.class),
                            rs.getObject(5, Integer.class));
                    byPath.computeIfAbsent(rs.getLong(1), _ -> new ArrayList<>()).add(row);
                }
            }
        }
        return List.copyOf(byPath.values());
    }

    private static GCRootPath toPath(
            HeapView view, long targetId, List<PathRow> rows, Map<Long, JavaClassRow> classCache)
            throws SQLException {
        List<PathStep> steps = new ArrayList<>(rows.size());
        for (int i = 0; i < rows.size(); i++) {
            PathRow row = rows.get(i);
            String fieldName = null;
            if (i + 1 < rows.size()) {
                OutboundRefRow ref = new OutboundRefRow(
                        row.instanceId(), rows.get(i + 1).instanceId(), row.fieldKind(), row.fieldId());
                fieldName = resolveFieldName(view, row.instanceId(), ref, classCache);
            }
            steps.add(buildStep(view, row.instanceId(), fieldName, row.instanceId() == targetId, classCache));
        }

        PathRow root = rows.getFirst();
        int rootKind = root.rootKind() != null ? root.rootKind() : HprofTag.Sub.ROOT_UNKNOWN;
        String rootClassName = optionalClassName(view, root.instanceId(), classCache);
        Optional<GcRootHolders.Holder> holder = GcRootHolders.of(view, root.instanceId());
        return new GCRootPath(
                root.instanceId(),
                rootClassName,
                HprofTag.Sub.rootKindName(rootKind),
                holder.map(GcRootHolders.Holder::threadName).orElse(null),
                holder.map(GcRootHolders.Holder::stackFrame).orElse(null),
                steps);
    }

    /**
     * The path of an object that is itself a GC root: the root is the object, and the one step is the
     * object. {@link #findPaths} finds no longer path for it, so a caller that wants to say what keeps
     * a root alive — its kind, thread and frame — asks here. Empty when the object is not a root.
     */
    public static Optional<GCRootPath> selfRoot(HeapView view, long instanceId) throws SQLException {
        Optional<GcRootHolders.Holder> holder = GcRootHolders.of(view, instanceId);
        if (holder.isEmpty()) {
            return Optional.empty();
        }
        Map<Long, JavaClassRow> classCache = new HashMap<>();
        PathStep self = buildStep(view, instanceId, null, true, classCache);
        return Optional.of(new GCRootPath(
                instanceId,
                optionalClassName(view, instanceId, classCache),
                holder.get().rootType(),
                holder.get().threadName(),
                holder.get().stackFrame(),
                List.of(self)));
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

    /**
     * One step of a found path as the search returns it: the instance, the reference it holds to the
     * next step ({@code fieldKind}/{@code fieldId}, null on the target), and on the root step the
     * kind of root it is.
     */
    private record PathRow(long instanceId, Integer fieldKind, Integer fieldId, Integer rootKind) {
    }
}
