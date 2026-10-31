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
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import cafe.jeffrey.profile.heapdump.model.ClassLoaderLeakSummary;
import cafe.jeffrey.profile.heapdump.model.GCRootPath;
import cafe.jeffrey.profile.heapdump.model.LeakSuspect;
import cafe.jeffrey.profile.heapdump.model.LeakSuspectsReport;
import cafe.jeffrey.profile.heapdump.view.HeapView;

/**
 * HeapView-backed equivalent of
 * {@link cafe.jeffrey.profile.heapdump.analyzer.LeakSuspectsAnalyzer}.
 *
 * Requires the dominator tree to be built first — call
 * {@link cafe.jeffrey.profile.heapdump.persistence.DominatorTreeBuilder#build}
 * (typically lazy, on first access).
 *
 * Heuristic (basic): the top instances by retained size whose retained size
 * exceeds a configurable percentage of the total heap, and that no other such
 * instance dominates. This is the same starting heuristic MAT uses; the
 * existing NetBeans-backed analyzer in Jeffrey has additional classloader-cluster
 * logic that is not ported.
 *
 * <p>One leak is one suspect. Retained size never grows down the dominator tree,
 * so the objects a big retainer dominates on the way to its contents — a holder,
 * its {@code ArrayList}, the list's {@code Object[]} — all clear the threshold
 * too, and each would otherwise be listed as a suspect of its own, with the
 * same bytes counted once per link. A candidate with an ancestor that is an
 * instance that also clears the threshold is that instance's chain, not a second
 * suspect, so the suspects' retained sets are disjoint and their sum is a real
 * share of the graph. The report total uses the same shallow sizes as the dominator graph:
 * instance sizes plus static-field footprints of class objects absent from the instance table.
 * A candidate dominated by a class object (a static field) is
 * still a suspect unless an instance above the class also qualifies: the class itself is never one.
 *
 * <p>Each suspect carries what a leak claim is checked by, so the answer that names it is enough to
 * make the claim: what keeps it alive ({@link PathToGCRootAnalyzer#selfRoot} for a suspect that is a
 * root itself, with its thread and frame, else the shortest strong path), where its memory
 * accumulates and what that is made of ({@link SuspectClusters}).
 */
public final class LeakSuspectsAnalyzer {

    /** Default suspect threshold: retained size must exceed 5% of total heap. */
    public static final double DEFAULT_THRESHOLD_PERCENT = 5.0;
    public static final int DEFAULT_TOP_N = 10;

    /**
     * The top instance retainers with no qualifying instance ancestor. The ancestor walk stops at
     * the nearest instance; retained sizes cannot grow down a dominator chain. Class nodes do not
     * suppress a candidate, but are walked past so they cannot conceal a qualifying instance above.
     */
    private static final String SUSPECTS_SQL = """
            WITH RECURSIVE candidates AS (
                SELECT r.instance_id, r.bytes, i.class_id
                FROM retained_size r
                JOIN instance i ON i.instance_id = r.instance_id
                JOIN dominator built ON built.instance_id = r.instance_id
                WHERE r.bytes >= ?
            ), ancestors(instance_id, dominator_id) AS (
                SELECT c.instance_id, d.dominator_id
                FROM candidates c
                JOIN dominator d ON d.instance_id = c.instance_id
                UNION ALL
                SELECT a.instance_id, d.dominator_id
                FROM ancestors a
                JOIN dominator d ON d.instance_id = a.dominator_id
                LEFT JOIN instance holder ON holder.instance_id = a.dominator_id
                WHERE a.dominator_id <> 0 AND holder.instance_id IS NULL
            )
            SELECT r.instance_id, r.bytes, c.class_id, c.name, c.classloader_id
            FROM candidates r
            LEFT JOIN class c ON r.class_id = c.class_id
            WHERE NOT EXISTS (
                SELECT 1
                FROM ancestors a
                JOIN instance holder ON holder.instance_id = a.dominator_id
                JOIN retained_size held ON held.instance_id = holder.instance_id
                WHERE a.instance_id = r.instance_id AND held.bytes >= ?)
            ORDER BY r.bytes DESC, r.instance_id
            LIMIT ?
            """;

    /** Matches the dominator builder's node-set accounting, including class-only static footprints. */
    private static final String TOTAL_GRAPH_SHALLOW_SQL = """
            SELECT coalesce(sum(shallow), 0)
            FROM (
                SELECT CAST(shallow_size AS BIGINT) AS shallow FROM instance
                UNION ALL
                SELECT CAST(static_fields_size AS BIGINT) FROM class
                WHERE class_id NOT IN (SELECT instance_id FROM instance)
            ) nodes
            """;

    private static final String REASON =
            "One %s object retains %.1f%% of the heap, and no other suspect dominates it.";

    private static final String ACCUMULATES_IN_SUSPECT = "The %s itself";

    private static final String ACCUMULATES_BELOW = "A %s, %d %s below the suspect,";

    private static final String HOLDS = " directly holds %,d objects of %d %s that retain %.1f%% of it.";

    private static final String DESCENT_CAPPED = " The descent stopped after %d levels; the chain goes deeper.";

    private static final String LEVEL = "level";

    private static final String LEVELS = "levels";

    private static final String CLASS = "class";

    private static final String CLASSES = "classes";

    private LeakSuspectsAnalyzer() {
    }

    public static LeakSuspectsReport analyze(HeapView view) throws SQLException {
        return analyze(view, DEFAULT_THRESHOLD_PERCENT, DEFAULT_TOP_N);
    }

    public static LeakSuspectsReport analyze(HeapView view, double thresholdPercent, int topN)
            throws SQLException {
        if (!view.hasDominatorTree()) {
            throw new IllegalStateException(
                    "Dominator tree not built; call DominatorTreeBuilder.build(indexDb) first");
        }
        if (thresholdPercent < 0 || thresholdPercent > 100) {
            throw new IllegalArgumentException(
                    "thresholdPercent must be in [0, 100]: thresholdPercent=" + thresholdPercent);
        }
        if (topN <= 0) {
            throw new IllegalArgumentException("topN must be positive: topN=" + topN);
        }

        long totalHeapSize = graphShallowSize(view);
        long thresholdBytes = (long) (totalHeapSize * (thresholdPercent / 100.0));

        List<LeakSuspect> suspects = new ArrayList<>();
        long analyzedBytes = 0;
        Map<Long, LoaderAcc> byLoader = new HashMap<>();
        Map<Long, String> loaderNameCache = new HashMap<>();

        try (PreparedStatement stmt = view.databaseClient().connection().prepareStatement(SUSPECTS_SQL)) {
            stmt.setLong(1, thresholdBytes);
            stmt.setLong(2, thresholdBytes);
            stmt.setInt(3, topN);
            try (ResultSet rs = stmt.executeQuery()) {
                int rank = 1;
                while (rs.next()) {
                    long instanceId = rs.getLong(1);
                    long retained = rs.getLong(2);
                    long classId = rs.getLong(3);
                    boolean classMissing = rs.wasNull();
                    String className = rs.getString(4);
                    if (className == null) {
                        className = "<unknown>";
                    }
                    long loaderId = rs.getLong(5);
                    if (rs.wasNull()) {
                        loaderId = 0L;
                    }
                    String loaderName = loaderId == 0L
                            ? "<bootstrap>"
                            : resolveLoaderName(view, loaderId, loaderNameCache);

                    int instanceCount = classMissing
                            ? 1
                            : (int) Math.min(view.instanceCount(classId), Integer.MAX_VALUE);
                    double percent = totalHeapSize == 0 ? 0.0 : (retained * 100.0) / totalHeapSize;
                    double score = percent; // simple score: retained percentage

                    GCRootPath rootPath = rootPath(view, instanceId);
                    SuspectClusters.Cluster cluster = SuspectClusters.of(view, instanceId, className, retained);

                    suspects.add(new LeakSuspect(
                            rank++,
                            className,
                            instanceId,
                            retained,
                            percent,
                            instanceCount,
                            buildReason(percent, className),
                            describe(cluster, retained),
                            rootPath,
                            cluster.objectId(),
                            cluster.className(),
                            cluster.contents(),
                            cluster.classCount(),
                            score,
                            loaderId,
                            loaderName));
                    analyzedBytes += retained;
                    byLoader.computeIfAbsent(loaderId, id -> new LoaderAcc(loaderName))
                            .add(retained);
                }
            }
        }

        List<ClassLoaderLeakSummary> topLoaders = byLoader.entrySet().stream()
                .map(e -> new ClassLoaderLeakSummary(
                        e.getKey(), e.getValue().name, e.getValue().retained, e.getValue().count))
                .sorted(Comparator.comparingLong(ClassLoaderLeakSummary::totalRetainedSize).reversed())
                .toList();

        return new LeakSuspectsReport(totalHeapSize, analyzedBytes, suspects, topLoaders);
    }

    private static long graphShallowSize(HeapView view) throws SQLException {
        try (PreparedStatement stmt = view.databaseClient().connection().prepareStatement(TOTAL_GRAPH_SHALLOW_SQL);
             ResultSet rs = stmt.executeQuery()) {
            return rs.next() ? rs.getLong(1) : 0L;
        }
    }

    private static String resolveLoaderName(
            HeapView view, long loaderInstanceId, Map<Long, String> cache) throws SQLException {
        String cached = cache.get(loaderInstanceId);
        if (cached != null) {
            return cached;
        }
        try (PreparedStatement stmt = view.databaseClient().connection().prepareStatement(
                "SELECT c.name FROM instance i JOIN class c ON i.class_id = c.class_id "
                        + "WHERE i.instance_id = ?")) {
            stmt.setLong(1, loaderInstanceId);
            try (ResultSet rs = stmt.executeQuery()) {
                String name = rs.next() ? rs.getString(1) : "<unresolved>";
                cache.put(loaderInstanceId, name);
                return name;
            }
        }
    }

    /**
     * What keeps the suspect alive: its own root, when it is one, else the shortest strong path from
     * one; null when no strong path is found within the search limit.
     */
    private static GCRootPath rootPath(HeapView view, long instanceId) throws SQLException {
        Optional<GCRootPath> self = PathToGCRootAnalyzer.selfRoot(view, instanceId);
        if (self.isPresent()) {
            return self.get();
        }
        List<GCRootPath> paths = PathToGCRootAnalyzer.findPaths(view, instanceId, true, 1);
        return paths.isEmpty() ? null : paths.getFirst();
    }

    /** Where the suspect's memory accumulates, in a sentence. */
    private static String describe(SuspectClusters.Cluster cluster, long suspectRetained) {
        double share = suspectRetained == 0 ? 0.0 : (cluster.heldBytes() * 100.0) / suspectRetained;
        String where = cluster.depth() == 0
                ? String.format(ACCUMULATES_IN_SUSPECT, cluster.className())
                : String.format(ACCUMULATES_BELOW, cluster.className(), cluster.depth(),
                        cluster.depth() == 1 ? LEVEL : LEVELS);
        String held = String.format(HOLDS, cluster.objects(), cluster.classCount(),
                cluster.classCount() == 1 ? CLASS : CLASSES, share);
        return where + held + (cluster.descentCapped() ? String.format(DESCENT_CAPPED, SuspectClusters.MAX_DESCENT) : "");
    }

    private static String buildReason(double percent, String className) {
        return String.format(REASON, className, percent);
    }

    private static final class LoaderAcc {
        final String name;
        long retained;
        int count;

        LoaderAcc(String name) {
            this.name = name;
        }

        void add(long bytes) {
            retained += bytes;
            count++;
        }
    }
}
