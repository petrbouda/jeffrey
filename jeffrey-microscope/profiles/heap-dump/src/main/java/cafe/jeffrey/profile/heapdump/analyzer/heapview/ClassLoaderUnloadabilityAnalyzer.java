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

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import cafe.jeffrey.profile.heapdump.model.BlockingClass;
import cafe.jeffrey.profile.heapdump.model.ClassLoaderUnloadability;
import cafe.jeffrey.profile.heapdump.model.UnloadabilityVerdict;
import cafe.jeffrey.profile.heapdump.parser.HeapView;

/**
 * Answers, for every class loader, the question "could this loader be
 * garbage-collected on the next metaspace-aware GC cycle?" The verdict
 * combines two facts: whether any instance of any class the loader defined
 * is still allocated, and whether the loader instance itself is a GC root.
 *
 * <p>Implementation runs two whole-heap queries (one rollup of instances per
 * loader, one window-ranked top-5 of blocking classes per loader) plus one
 * {@link HeapView#isGcRoot} probe per loader id. The bootstrap loader is
 * always reported as {@link UnloadabilityVerdict#PINNED_ROOTED} — it has no
 * loader instance, so the {@code isGcRoot} check is skipped.
 */
public final class ClassLoaderUnloadabilityAnalyzer {

    private static final int TOP_BLOCKING_CLASSES_PER_LOADER = 5;

    private static final long BOOTSTRAP_LOADER_ID = 0L;

    private static final String INSTANCE_COUNT_SQL = """
            SELECT
                COALESCE(c.classloader_id, 0) AS loader_id,
                COUNT(i.instance_id)          AS instance_count
            FROM class c
            LEFT JOIN instance i ON i.class_id = c.class_id
            GROUP BY COALESCE(c.classloader_id, 0)
            """;

    private static final String TOP_BLOCKING_CLASSES_SQL = """
            WITH per_class AS (
                SELECT
                    COALESCE(c.classloader_id, 0)    AS loader_id,
                    c.class_id                       AS class_id,
                    c.name                           AS class_name,
                    COUNT(i.instance_id)             AS inst_count,
                    COALESCE(SUM(i.shallow_size), 0) AS total_size
                FROM class c
                LEFT JOIN instance i ON i.class_id = c.class_id
                GROUP BY COALESCE(c.classloader_id, 0), c.class_id, c.name
            ),
            ranked AS (
                SELECT *,
                       ROW_NUMBER() OVER (
                           PARTITION BY loader_id
                           ORDER BY inst_count DESC, class_id ASC
                       ) AS rk
                FROM per_class
            )
            SELECT loader_id, class_id, class_name, inst_count, total_size
            FROM ranked
            WHERE rk <= ? AND inst_count > 0
            ORDER BY loader_id, inst_count DESC
            """;

    private ClassLoaderUnloadabilityAnalyzer() {
    }

    public static Map<Long, ClassLoaderUnloadability> analyze(
            HeapView view, Collection<Long> loaderIds) throws SQLException {

        Map<Long, Long> liveInstancesByLoader = readInstanceCounts(view);
        Map<Long, List<BlockingClass>> blockingByLoader = readTopBlockingClasses(view);

        Map<Long, ClassLoaderUnloadability> result = new HashMap<>();
        for (Long loaderId : loaderIds) {
            if (loaderId == null) {
                continue;
            }
            long live = liveInstancesByLoader.getOrDefault(loaderId, 0L);
            boolean rooted = loaderId == BOOTSTRAP_LOADER_ID || view.isGcRoot(loaderId);
            UnloadabilityVerdict verdict = computeVerdict(live, rooted);
            List<BlockingClass> top = blockingByLoader.getOrDefault(loaderId, List.of());
            result.put(loaderId, new ClassLoaderUnloadability(verdict, live, rooted, top));
        }
        return result;
    }

    private static UnloadabilityVerdict computeVerdict(long liveInstanceCount, boolean rooted) {
        if (rooted) {
            return UnloadabilityVerdict.PINNED_ROOTED;
        }
        if (liveInstanceCount > 0) {
            return UnloadabilityVerdict.PINNED_TRANSITIVE;
        }
        return UnloadabilityVerdict.UNLOADABLE;
    }

    private static Map<Long, Long> readInstanceCounts(HeapView view) throws SQLException {
        Map<Long, Long> counts = new HashMap<>();
        try (Statement stmt = view.databaseClient().connection().createStatement();
             ResultSet rs = stmt.executeQuery(INSTANCE_COUNT_SQL)) {
            while (rs.next()) {
                counts.put(rs.getLong(1), rs.getLong(2));
            }
        }
        return counts;
    }

    private static Map<Long, List<BlockingClass>> readTopBlockingClasses(HeapView view) throws SQLException {
        Map<Long, List<BlockingClass>> blocking = new HashMap<>();
        try (var stmt = view.databaseClient().connection().prepareStatement(TOP_BLOCKING_CLASSES_SQL)) {
            stmt.setInt(1, TOP_BLOCKING_CLASSES_PER_LOADER);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    long loaderId = rs.getLong(1);
                    BlockingClass entry = new BlockingClass(
                            rs.getLong(2),
                            rs.getString(3),
                            rs.getLong(4),
                            rs.getLong(5));
                    blocking.computeIfAbsent(loaderId, k -> new ArrayList<>()).add(entry);
                }
            }
        }
        return blocking;
    }
}
