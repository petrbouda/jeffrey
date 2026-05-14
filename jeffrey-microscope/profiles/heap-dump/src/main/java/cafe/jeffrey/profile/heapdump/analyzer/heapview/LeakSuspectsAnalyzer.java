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
import cafe.jeffrey.profile.heapdump.parser.HeapView;

/**
 * HeapView-backed equivalent of
 * {@link cafe.jeffrey.profile.heapdump.analyzer.LeakSuspectsAnalyzer}.
 *
 * Requires the dominator tree to be built first — call
 * {@link cafe.jeffrey.profile.heapdump.parser.DominatorTreeBuilder#build}
 * (typically lazy, on first access).
 *
 * Heuristic (basic): the top instances by retained size whose retained size
 * exceeds a configurable percentage of the total heap. This is the same
 * starting heuristic MAT uses; the existing NetBeans-backed analyzer in
 * Jeffrey has additional classloader-cluster and accumulation-point logic
 * that is not yet ported (see field-level limitations below).
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

        // Single SQL: top retainers above threshold, joined to class metadata.
        // ORDER BY retained DESC, take topN.
        String sql = "SELECT r.instance_id, r.bytes, c.class_id, c.name, c.classloader_id "
                + "FROM retained_size r "
                + "JOIN instance i ON i.instance_id = r.instance_id "
                + "LEFT JOIN class c ON i.class_id = c.class_id "
                + "WHERE r.bytes >= ? "
                + "ORDER BY r.bytes DESC "
                + "LIMIT ?";

        List<LeakSuspect> suspects = new ArrayList<>();
        long analyzedBytes = 0;
        Map<Long, LoaderAcc> byLoader = new HashMap<>();
        Map<Long, String> loaderNameCache = new HashMap<>();

        try (PreparedStatement stmt = view.databaseClient().connection().prepareStatement(sql)) {
            stmt.setLong(1, thresholdBytes);
            stmt.setInt(2, topN);
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
        return String.format(
                "Single instance of %s retains %.1f%% of the heap. Likely a leak suspect.",
                className, percent);
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
