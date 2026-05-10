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
import cafe.jeffrey.profile.heapdump.model.ClassLoaderInfo;
import cafe.jeffrey.profile.heapdump.model.ClassLoaderLeakChain;
import cafe.jeffrey.profile.heapdump.model.ClassLoaderReport;
import cafe.jeffrey.profile.heapdump.model.DuplicateClassInfo;
import cafe.jeffrey.profile.heapdump.parser.HeapView;
import cafe.jeffrey.profile.heapdump.parser.JavaClassRow;

/**
 * HeapView-backed equivalent of
 * {@link cafe.jeffrey.profile.heapdump.analyzer.ClassLoaderAnalyzer}.
 *
 * Tier 2 — exercises the HPROF id 0 → NULL classloader convention plus a
 * GROUP BY classloader_id aggregation.
 *
 * Limitations vs the NetBeans-backed version:
 * <ul>
 *   <li>{@code retainedSize} is currently 0 — the dominator-tree infrastructure
 *       lands in PR #10. Callers that surface this number should display
 *       "n/a" until then.</li>
 *   <li>{@code leakChains} is empty for the same reason.</li>
 * </ul>
 * Counts, totals, and duplicate detection are exact.
 */
public final class ClassLoaderAnalyzer {

    private static final String BOOTSTRAP_CLASS_LOADER = "<bootstrap>";

    private ClassLoaderAnalyzer() {
    }

    public static ClassLoaderReport analyze(HeapView view) throws SQLException {
        Map<Long, Accumulator> byLoader = new HashMap<>();
        Map<String, Map<Long, String>> classToLoaders = new HashMap<>();

        // Resolve each loader's class name once. Loader instance → its class → class name.
        Map<Long, String> loaderNameCache = new HashMap<>();

        try (Statement stmt = view.connection().createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT class_id, name, classloader_id, instance_size FROM class")) {
            while (rs.next()) {
                long classId = rs.getLong(1);
                String className = rs.getString(2);
                long loaderObjectId = rs.getLong(3);
                if (rs.wasNull()) {
                    loaderObjectId = 0L;
                }
                int instanceSize = rs.getInt(4);

                String loaderClassName = loaderObjectId == 0L
                        ? BOOTSTRAP_CLASS_LOADER
                        : resolveLoaderClassName(view, loaderObjectId, loaderNameCache);

                Accumulator acc = byLoader.computeIfAbsent(loaderObjectId,
                        id -> new Accumulator(id, loaderClassName));
                acc.classCount++;
                // shallow class size approximation — total bytes consumed by all instances of this class
                acc.totalClassSize += (long) instanceSize * view.instanceCount(classId);

                classToLoaders.computeIfAbsent(className, k -> new HashMap<>())
                        .putIfAbsent(loaderObjectId, loaderClassName);
            }
        }

        List<ClassLoaderInfo> loaderInfos = byLoader.values().stream()
                .map(a -> new ClassLoaderInfo(
                        a.objectId, a.classLoaderClassName, a.classCount, a.totalClassSize, 0L))
                .sorted(Comparator.comparingInt(ClassLoaderInfo::classCount).reversed())
                .toList();

        List<DuplicateClassInfo> duplicates = new ArrayList<>();
        for (Map.Entry<String, Map<Long, String>> e : classToLoaders.entrySet()) {
            if (e.getValue().size() > 1) {
                duplicates.add(new DuplicateClassInfo(
                        e.getKey(), e.getValue().size(), new ArrayList<>(e.getValue().values())));
            }
        }
        duplicates.sort(Comparator.comparingInt(DuplicateClassInfo::loaderCount).reversed());

        int totalClasses = loaderInfos.stream().mapToInt(ClassLoaderInfo::classCount).sum();
        return new ClassLoaderReport(
                loaderInfos.size(),
                totalClasses,
                duplicates.size(),
                loaderInfos,
                duplicates,
                List.<ClassLoaderLeakChain>of()); // PR #10
    }

    private static String resolveLoaderClassName(
            HeapView view, long loaderInstanceId, Map<Long, String> cache) throws SQLException {
        String cached = cache.get(loaderInstanceId);
        if (cached != null) {
            return cached;
        }
        // Loader instance → instance.class_id → class.name
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

    private static final class Accumulator {
        final long objectId;
        final String classLoaderClassName;
        int classCount;
        long totalClassSize;

        Accumulator(long objectId, String classLoaderClassName) {
            this.objectId = objectId;
            this.classLoaderClassName = classLoaderClassName;
        }
    }
}
