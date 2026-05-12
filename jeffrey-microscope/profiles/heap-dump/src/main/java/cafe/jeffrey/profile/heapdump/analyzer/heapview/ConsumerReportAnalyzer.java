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
import cafe.jeffrey.profile.heapdump.model.ComponentEntry;
import cafe.jeffrey.profile.heapdump.model.ConsumerEntry;
import cafe.jeffrey.profile.heapdump.model.ConsumerReport;
import cafe.jeffrey.profile.heapdump.parser.HeapView;

/**
 * HeapView-backed equivalent of
 * {@link cafe.jeffrey.profile.heapdump.analyzer.ConsumerReportAnalyzer}.
 *
 * Aggregates instance shallow sizes by (package, classloader) — a single
 * GROUP BY followed by Java-side package derivation. Top N consumers
 * returned ordered by total shallow size descending.
 *
 * Limitations vs the NetBeans-backed version:
 * <ul>
 *   <li>{@code retainedSize} on each {@link ConsumerEntry} is 0 — needs the
 *       dominator infrastructure (PR #12).</li>
 *   <li>{@code componentReport} returns empty until {@code Component}
 *       grouping logic is ported (lower-priority feature).</li>
 *   <li>{@code classLoaderClassName} comes from {@link ClassLoaderAnalyzer}'s
 *       resolution path; resolved lazily here via instance JOIN class.</li>
 * </ul>
 */
public final class ConsumerReportAnalyzer {

    private static final int DEFAULT_TOP_N = 100;
    private static final String DEFAULT_PACKAGE = "<default>";
    private static final String BOOTSTRAP_LOADER = "<bootstrap>";

    private ConsumerReportAnalyzer() {
    }

    public static ConsumerReport analyze(HeapView view) throws SQLException {
        return analyze(view, DEFAULT_TOP_N);
    }

    public static ConsumerReport analyze(HeapView view, int topN) throws SQLException {
        if (topN <= 0) {
            throw new IllegalArgumentException("topN must be positive: topN=" + topN);
        }

        Map<Key, Accumulator> byPackage = new HashMap<>();
        long totalHeapSize = 0;

        // One pass: per-class shallow totals, joined to class name + classloader.
        try (Statement stmt = view.connection().createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT c.name, c.classloader_id, COUNT(*) AS instance_count, "
                             + "SUM(i.shallow_size) AS total_shallow "
                             + "FROM instance i JOIN class c ON i.class_id = c.class_id "
                             + "GROUP BY c.name, c.classloader_id")) {
            while (rs.next()) {
                String className = rs.getString(1);
                long loaderId = rs.getLong(2);
                if (rs.wasNull()) {
                    loaderId = 0L;
                }
                long instanceCount = rs.getLong(3);
                long totalShallow = rs.getLong(4);

                Key key = new Key(packageOf(className), loaderId);
                Accumulator a = byPackage.computeIfAbsent(key, k -> new Accumulator());
                a.instanceCount += instanceCount;
                a.shallowSize += totalShallow;
                a.classCount++;
                totalHeapSize += totalShallow;
            }
        }

        // Resolve classloader names lazily.
        Map<Long, String> loaderNameCache = new HashMap<>();
        List<ConsumerEntry> entries = new ArrayList<>(byPackage.size());
        for (Map.Entry<Key, Accumulator> e : byPackage.entrySet()) {
            String loaderName = e.getKey().loaderId == 0L
                    ? BOOTSTRAP_LOADER
                    : resolveLoaderName(view, e.getKey().loaderId, loaderNameCache);
            entries.add(new ConsumerEntry(
                    e.getKey().packageName,
                    e.getKey().loaderId,
                    loaderName,
                    0L, // retained size — PR #12
                    e.getValue().shallowSize,
                    e.getValue().classCount,
                    e.getValue().instanceCount));
        }
        entries.sort(Comparator.comparingLong(ConsumerEntry::shallowSize).reversed());
        List<ConsumerEntry> top = entries.size() > topN ? entries.subList(0, topN) : entries;

        return new ConsumerReport(totalHeapSize, List.copyOf(top), List.<ComponentEntry>of());
    }

    private static String packageOf(String className) {
        if (className == null || className.isEmpty()) {
            return DEFAULT_PACKAGE;
        }
        // Array classes: "[Lcom.foo.Bar;" or "[I" — use the element type's package.
        String name = className;
        while (name.startsWith("[")) {
            name = name.substring(1);
        }
        if (name.startsWith("L") && name.endsWith(";")) {
            name = name.substring(1, name.length() - 1);
        } else if (name.length() == 1) {
            // Primitive descriptor (e.g. "I" for int[]) — no real package.
            return "<primitive>";
        }
        int lastDot = name.lastIndexOf('.');
        return lastDot < 0 ? DEFAULT_PACKAGE : name.substring(0, lastDot);
    }

    private static String resolveLoaderName(
            HeapView view, long loaderInstanceId, Map<Long, String> cache) throws SQLException {
        String cached = cache.get(loaderInstanceId);
        if (cached != null) {
            return cached;
        }
        try (PreparedStatement stmt = view.connection().prepareStatement(
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

    private record Key(String packageName, long loaderId) {
    }

    private static final class Accumulator {
        long instanceCount;
        long shallowSize;
        int classCount;
    }
}
