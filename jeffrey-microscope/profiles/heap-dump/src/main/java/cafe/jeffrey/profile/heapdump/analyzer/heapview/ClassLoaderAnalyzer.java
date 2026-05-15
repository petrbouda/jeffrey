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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import cafe.jeffrey.profile.heapdump.model.ClassLoaderHierarchyEdge;
import cafe.jeffrey.profile.heapdump.model.ClassLoaderInfo;
import cafe.jeffrey.profile.heapdump.model.ClassLoaderLeakChain;
import cafe.jeffrey.profile.heapdump.model.ClassLoaderReport;
import cafe.jeffrey.profile.heapdump.model.ClassLoaderUnloadability;
import cafe.jeffrey.profile.heapdump.model.DuplicateClassInfo;
import cafe.jeffrey.profile.heapdump.model.LoaderType;
import cafe.jeffrey.profile.heapdump.parser.HeapView;

/**
 * Per-class-loader byte attribution for the Class Loader page.
 *
 * <p>Single-pass aggregation over the index: each class is attributed to its
 * defining loader (HPROF id {@code 0} → {@code <bootstrap>}), the {@code
 * shallow_size} column of every instance of that class flows into the loader's
 * {@code totalClassSize}, and {@code retained_size.bytes} (populated by the
 * dominator-tree builder; LEFT-joined so missing rows degrade to 0)
 * contributes to {@code retainedSize}. Synthetic primitive-array classes are
 * included — their instances are the largest by-byte contributor to the
 * bootstrap loader on real heaps.
 *
 * <p>Duplicate-class detection runs as a separate query over {@code class}
 * rows grouped by name; any class name claimed by more than one loader
 * surfaces as a {@link DuplicateClassInfo}.
 *
 * <p>Leak-chain analysis is not yet implemented (deferred to a later PR);
 * {@link ClassLoaderReport#leakChains()} is always empty.
 */
public final class ClassLoaderAnalyzer {

    private static final String BOOTSTRAP_CLASS_LOADER = "<bootstrap>";

    /**
     * Per-loader rollup of class count and the cumulative shallow size of all
     * instances of every class the loader defined. Inner aggregate
     * pre-collapses {@code instance} rows by class so the outer LEFT JOIN
     * stays one-row-per-class — no double counting and no need for
     * {@code COUNT(DISTINCT)}.
     */
    private static final String AGGREGATE_SQL = """
            SELECT
                c.classloader_id,
                COUNT(*)                                AS class_count,
                COALESCE(SUM(pc.total_shallow), 0)      AS total_class_size
            FROM class c
            LEFT JOIN (
                SELECT class_id, SUM(shallow_size) AS total_shallow
                FROM instance
                GROUP BY class_id
            ) pc ON pc.class_id = c.class_id
            GROUP BY c.classloader_id
            """;

    /**
     * Retained size of a single loader instance (the {@code java.lang.ClassLoader}
     * object that owns the defined classes). This is the correct semantic for
     * the "Retained Size" column — summing {@code retained_size.bytes} across
     * all instances would double-count shared subgraphs and exceed the heap.
     * Bootstrap (no loader instance) skips this lookup and reports 0.
     */
    private static final String LOADER_RETAINED_SQL =
            "SELECT bytes FROM retained_size WHERE instance_id = ?";

    /**
     * Class names that appear under more than one loader. Two rows per duplicate
     * (one per loader instance) so the analyzer can fan out the loader-name
     * list per entry.
     */
    private static final String DUPLICATE_SQL = """
            WITH names_loaders AS (
                SELECT name, classloader_id
                FROM class
                GROUP BY name, classloader_id
            )
            SELECT n.name, n.classloader_id
            FROM names_loaders n
            WHERE n.name IN (
                SELECT name FROM names_loaders GROUP BY name HAVING COUNT(*) > 1
            )
            ORDER BY n.name, n.classloader_id
            """;

    private ClassLoaderAnalyzer() {
    }

    public static ClassLoaderReport analyze(HeapView view) throws SQLException {
        Map<Long, String> loaderNameCache = new HashMap<>();

        List<ClassLoaderInfo> loaderInfos = aggregateLoaders(view, loaderNameCache);
        List<DuplicateClassInfo> duplicates = findDuplicateClasses(view, loaderNameCache);

        Map<Long, LoaderType> loaderTypes = new HashMap<>(loaderInfos.size());
        for (ClassLoaderInfo info : loaderInfos) {
            loaderTypes.put(info.objectId(), LoaderTypeClassifier.classify(info.objectId(), info.classLoaderClassName()));
        }

        int totalClasses = loaderInfos.stream().mapToInt(ClassLoaderInfo::classCount).sum();
        return new ClassLoaderReport(
                loaderInfos.size(),
                totalClasses,
                duplicates.size(),
                loaderInfos,
                duplicates,
                List.<ClassLoaderLeakChain>of(),
                List.<ClassLoaderHierarchyEdge>of(),
                Map.<Long, ClassLoaderUnloadability>of(),
                loaderTypes);
    }

    private static List<ClassLoaderInfo> aggregateLoaders(
            HeapView view, Map<Long, String> loaderNameCache) throws SQLException {
        record LoaderAggregate(long loaderId, int classCount, long totalShallow) {}

        List<LoaderAggregate> aggregates = new ArrayList<>();
        try (Statement stmt = view.databaseClient().connection().createStatement();
             ResultSet rs = stmt.executeQuery(AGGREGATE_SQL)) {
            while (rs.next()) {
                long loaderObjectId = rs.getLong(1);
                if (rs.wasNull()) {
                    loaderObjectId = 0L;
                }
                aggregates.add(new LoaderAggregate(loaderObjectId, rs.getInt(2), rs.getLong(3)));
            }
        }

        List<ClassLoaderInfo> rows = new ArrayList<>(aggregates.size());
        try (PreparedStatement retainedStmt = view.databaseClient().connection().prepareStatement(LOADER_RETAINED_SQL)) {
            for (LoaderAggregate a : aggregates) {
                long retained = a.loaderId == 0L ? 0L : lookupRetained(retainedStmt, a.loaderId);
                String loaderClassName = a.loaderId == 0L
                        ? BOOTSTRAP_CLASS_LOADER
                        : resolveLoaderClassName(view, a.loaderId, loaderNameCache);
                rows.add(new ClassLoaderInfo(
                        a.loaderId, loaderClassName, a.classCount, a.totalShallow, retained));
            }
        }
        rows.sort(Comparator.comparingInt(ClassLoaderInfo::classCount).reversed()
                .thenComparing(Comparator.comparingLong(ClassLoaderInfo::totalClassSize).reversed()));
        return rows;
    }

    private static long lookupRetained(PreparedStatement stmt, long instanceId) throws SQLException {
        stmt.setLong(1, instanceId);
        try (ResultSet rs = stmt.executeQuery()) {
            return rs.next() ? rs.getLong(1) : 0L;
        }
    }

    private static List<DuplicateClassInfo> findDuplicateClasses(
            HeapView view, Map<Long, String> loaderNameCache) throws SQLException {
        Map<String, List<Long>> byName = new LinkedHashMap<>();
        try (Statement stmt = view.databaseClient().connection().createStatement();
             ResultSet rs = stmt.executeQuery(DUPLICATE_SQL)) {
            while (rs.next()) {
                String name = rs.getString(1);
                long loaderId = rs.getLong(2);
                if (rs.wasNull()) {
                    loaderId = 0L;
                }
                byName.computeIfAbsent(name, k -> new ArrayList<>()).add(loaderId);
            }
        }
        List<DuplicateClassInfo> out = new ArrayList<>(byName.size());
        for (Map.Entry<String, List<Long>> e : byName.entrySet()) {
            List<String> loaderNames = new ArrayList<>(e.getValue().size());
            for (Long loaderId : e.getValue()) {
                loaderNames.add(loaderId == 0L
                        ? BOOTSTRAP_CLASS_LOADER
                        : resolveLoaderClassName(view, loaderId, loaderNameCache));
            }
            out.add(new DuplicateClassInfo(e.getKey(), e.getValue().size(), loaderNames));
        }
        out.sort(Comparator.comparingInt(DuplicateClassInfo::loaderCount).reversed());
        return out;
    }

    private static String resolveLoaderClassName(
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
}
