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
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import cafe.jeffrey.profile.heapdump.model.ClassLoaderLeakSummary;
import cafe.jeffrey.profile.heapdump.model.DominatedClassEntry;
import cafe.jeffrey.profile.heapdump.model.LeakSuspect;
import cafe.jeffrey.profile.heapdump.model.LeakSuspectsReport;
import cafe.jeffrey.profile.heapdump.model.PathStep;
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
 * and accumulation-point logic that is not yet ported (see field-level
 * limitations below).
 *
 * <p>One leak is one suspect. Retained size never grows down the dominator tree,
 * so the objects a big retainer dominates on the way to its contents — a holder,
 * its {@code ArrayList}, the list's {@code Object[]} — all clear the threshold
 * too, and each would otherwise be listed as a suspect of its own, with the
 * same bytes counted once per link. A candidate whose immediate dominator is an
 * instance that also clears the threshold is that instance's chain, not a second
 * suspect, so the suspects' retained sets are disjoint and their sum is a real
 * share of the heap. A candidate dominated by a class object (a static field) is
 * still a suspect: the class itself is never one.
 *
 * Limitations vs the existing NetBeans-backed version (documented in the
 * model-level fields they map to):
 * <ul>
 *   <li>{@code accumulationPoint} / {@code accumulationPointId} /
 *       {@code accumulationPointClass}: null. The existing analyzer walks
 *       the dominated set looking for a clustering "neck" object (often a
 *       collection); deferring to a follow-up PR.</li>
 *   <li>{@code dominatedHistogram}: empty list. Computing this requires a
 *       per-suspect descendant scan which is straightforward but unmigrated.</li>
 *   <li>{@code pathSteps}: empty list. Once
 *       {@link PathToGCRootAnalyzer} is wired into this flow, populated.</li>
 *   <li>{@code reason}: a brief, generic message. The NetBeans path produces
 *       richer text including the dominated cluster's character.</li>
 * </ul>
 */
public final class LeakSuspectsAnalyzer {

    /** Default suspect threshold: retained size must exceed 5% of total heap. */
    public static final double DEFAULT_THRESHOLD_PERCENT = 5.0;
    public static final int DEFAULT_TOP_N = 10;

    /**
     * The top retainers above the threshold (bound twice) that no other retainer above it
     * dominates, joined to their class, largest first, at most topN. A dominator that is not an
     * instance — the virtual root, or a class object holding a static — never suppresses one.
     */
    private static final String SUSPECTS_SQL = """
            SELECT r.instance_id, r.bytes, c.class_id, c.name, c.classloader_id
            FROM retained_size r
            JOIN instance i ON i.instance_id = r.instance_id
            LEFT JOIN class c ON i.class_id = c.class_id
            JOIN dominator d ON d.instance_id = r.instance_id
            WHERE r.bytes >= ?
              AND NOT EXISTS (
                  SELECT 1
                  FROM instance holder
                  JOIN retained_size held ON held.instance_id = holder.instance_id
                  WHERE holder.instance_id = d.dominator_id
                    AND held.bytes >= ?)
            ORDER BY r.bytes DESC
            LIMIT ?
            """;

    private static final String REASON =
            "One %s object retains %.1f%% of the heap, and no other suspect dominates it.";

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

        long totalHeapSize = view.totalShallowSize();
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

                    suspects.add(new LeakSuspect(
                            rank++,
                            className,
                            instanceId,
                            retained,
                            percent,
                            instanceCount,
                            buildReason(percent, className),
                            null, // accumulationPoint
                            List.<PathStep>of(),
                            null, null,
                            List.<DominatedClassEntry>of(),
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
