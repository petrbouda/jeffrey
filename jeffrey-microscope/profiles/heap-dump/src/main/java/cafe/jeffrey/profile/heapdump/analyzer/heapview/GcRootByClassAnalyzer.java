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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import cafe.jeffrey.profile.heapdump.model.GCRootClassAggregate;
import cafe.jeffrey.profile.heapdump.view.HeapView;
import cafe.jeffrey.profile.heapdump.view.HprofTag;

/**
 * Aggregates GC roots by the class of the referenced instance — the
 * "Roots by Class" view. Surfaces classes with many roots (likely singletons
 * that are actually multitons, framework state, etc.).
 *
 * <p>Two passes:
 * <ol>
 *   <li>Aggregate query: per class, sum of retained sizes and total root count.</li>
 *   <li>Per-class kinds query (a single query over the top-N classes) to
 *       attach the distinct {@code rootKinds} list.</li>
 * </ol>
 */
public final class GcRootByClassAnalyzer {

    private static final String UNKNOWN_NAME = "<unknown>";

    private GcRootByClassAnalyzer() {
    }

    public static List<GCRootClassAggregate> analyze(HeapView view, int limit) throws SQLException {
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be positive: limit=" + limit);
        }
        if (!view.hasDominatorTree()) {
            throw new IllegalStateException(
                    "Roots by Class requires the dominator tree to be built");
        }

        String aggSql = "SELECT i.class_id, c.name, COUNT(*) AS root_count, "
                + "       SUM(COALESCE(r.bytes, 0)) AS retained_bytes "
                + "FROM gc_root g "
                + "JOIN instance i ON i.instance_id = g.instance_id "
                + "LEFT JOIN class c ON c.class_id = i.class_id "
                + "LEFT JOIN retained_size r ON r.instance_id = g.instance_id "
                + "GROUP BY i.class_id, c.name "
                + "ORDER BY retained_bytes DESC, root_count DESC "
                + "LIMIT ?";

        record Row(Long classId, String className, long rootCount, long retainedBytes) {
        }
        List<Row> rows = new ArrayList<>();
        try (PreparedStatement stmt = view.databaseClient().connection().prepareStatement(aggSql)) {
            stmt.setInt(1, limit);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Long classId = rs.getLong(1);
                    if (rs.wasNull()) {
                        classId = null;
                    }
                    String name = rs.getString(2);
                    if (name == null) {
                        name = UNKNOWN_NAME;
                    }
                    rows.add(new Row(classId, name, rs.getLong(3), rs.getLong(4)));
                }
            }
        }
        if (rows.isEmpty()) {
            return List.of();
        }

        Map<Long, List<String>> kindsByClassId = loadKindsForClasses(
                view, rows.stream().map(Row::classId).filter(id -> id != null).toList());

        List<GCRootClassAggregate> out = new ArrayList<>(rows.size());
        for (Row r : rows) {
            List<String> kinds = r.classId() == null ? List.of() : kindsByClassId.getOrDefault(r.classId(), List.of());
            out.add(new GCRootClassAggregate(r.className(), r.rootCount(), kinds, r.retainedBytes()));
        }
        return List.copyOf(out);
    }

    /**
     * For each of the given class ids, return the distinct root kinds seen,
     * ordered by frequency descending. One batched query keeps this cheap.
     */
    private static Map<Long, List<String>> loadKindsForClasses(HeapView view, List<Long> classIds)
            throws SQLException {
        if (classIds.isEmpty()) {
            return Map.of();
        }
        String placeholders = String.join(",", classIds.stream().map(c -> "?").toList());
        String sql = "SELECT i.class_id, g.root_kind, COUNT(*) AS ct "
                + "FROM gc_root g "
                + "JOIN instance i ON i.instance_id = g.instance_id "
                + "WHERE i.class_id IN (" + placeholders + ") "
                + "GROUP BY i.class_id, g.root_kind "
                + "ORDER BY i.class_id, ct DESC";

        Map<Long, List<String>> out = new LinkedHashMap<>();
        try (PreparedStatement stmt = view.databaseClient().connection().prepareStatement(sql)) {
            for (int i = 0; i < classIds.size(); i++) {
                stmt.setLong(i + 1, classIds.get(i));
            }
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    long classId = rs.getLong(1);
                    int kind = rs.getInt(2);
                    out.computeIfAbsent(classId, k -> new ArrayList<>())
                            .add(HprofTag.Sub.rootKindName(kind));
                }
            }
        }
        // Make each list immutable for downstream consumers.
        out.replaceAll((k, v) -> List.copyOf(v));
        return out;
    }
}
