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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import cafe.jeffrey.profile.heapdump.model.GCRootClassLoaderAggregate;
import cafe.jeffrey.profile.heapdump.view.HeapView;

/**
 * Aggregates GC roots by the classloader that loaded the rooted class —
 * the "Roots by ClassLoader" view. Surfaces classloader-leak patterns
 * (loaders that should have died but still root classes).
 *
 * <p>A rooted object can be either an instance or a class (sticky-class
 * roots). For each gc_root row we resolve the rooted class via:
 * <ul>
 *   <li>{@code class.class_id = gc_root.instance_id} (the rooted object IS a
 *       class — typical of Sticky Class roots)</li>
 *   <li>else {@code instance.class_id} of {@code gc_root.instance_id} (the
 *       rooted object is a regular instance)</li>
 * </ul>
 * The {@code COALESCE} picks whichever path resolves first.
 */
public final class GcRootByClassLoaderAnalyzer {

    private static final String UNKNOWN_NAME = "<unknown>";
    private static final String BOOTSTRAP = "Bootstrap";

    private GcRootByClassLoaderAnalyzer() {
    }

    public static List<GCRootClassLoaderAggregate> analyze(HeapView view, int limit) throws SQLException {
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be positive: limit=" + limit);
        }
        if (!view.hasDominatorTree()) {
            throw new IllegalStateException(
                    "Roots by ClassLoader requires the dominator tree to be built");
        }

        String sql = "WITH root_loader AS ("
                + "  SELECT g.instance_id, "
                + "         COALESCE(c_self.classloader_id, c_inst.classloader_id) AS classloader_id, "
                + "         COALESCE(r.bytes, 0) AS retained_bytes "
                + "  FROM gc_root g "
                + "  LEFT JOIN class c_self ON c_self.class_id = g.instance_id "
                + "  LEFT JOIN instance i ON i.instance_id = g.instance_id "
                + "  LEFT JOIN class c_inst ON c_inst.class_id = i.class_id "
                + "  LEFT JOIN retained_size r ON r.instance_id = g.instance_id "
                + ") "
                + "SELECT classloader_id, COUNT(*) AS root_count, SUM(retained_bytes) AS retained_bytes "
                + "FROM root_loader "
                + "GROUP BY classloader_id "
                + "ORDER BY retained_bytes DESC, root_count DESC "
                + "LIMIT ?";

        record Row(Long classloaderId, long rootCount, long retainedBytes) {
        }
        List<Row> rows = new ArrayList<>();
        try (PreparedStatement stmt = view.databaseClient().connection().prepareStatement(sql)) {
            stmt.setInt(1, limit);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Long classloaderId = rs.getLong(1);
                    if (rs.wasNull()) {
                        classloaderId = null;
                    }
                    rows.add(new Row(classloaderId, rs.getLong(2), rs.getLong(3)));
                }
            }
        }
        if (rows.isEmpty()) {
            return List.of();
        }

        Map<Long, String> loaderClassByObjectId = loadLoaderClassNames(
                view, rows.stream().map(Row::classloaderId).filter(id -> id != null).toList());

        List<GCRootClassLoaderAggregate> out = new ArrayList<>(rows.size());
        for (Row r : rows) {
            String name = r.classloaderId() == null
                    ? BOOTSTRAP
                    : loaderClassByObjectId.getOrDefault(r.classloaderId(), UNKNOWN_NAME);
            out.add(new GCRootClassLoaderAggregate(
                    r.classloaderId(), name, r.rootCount(), r.retainedBytes()));
        }
        return List.copyOf(out);
    }

    /** Look up the class-name for each classloader instance id in one batched query. */
    private static Map<Long, String> loadLoaderClassNames(HeapView view, List<Long> classloaderIds)
            throws SQLException {
        if (classloaderIds.isEmpty()) {
            return Map.of();
        }
        String placeholders = String.join(",", classloaderIds.stream().map(c -> "?").toList());
        String sql = "SELECT i.instance_id, c.name "
                + "FROM instance i "
                + "LEFT JOIN class c ON c.class_id = i.class_id "
                + "WHERE i.instance_id IN (" + placeholders + ")";

        Map<Long, String> out = new HashMap<>();
        try (PreparedStatement stmt = view.databaseClient().connection().prepareStatement(sql)) {
            for (int i = 0; i < classloaderIds.size(); i++) {
                stmt.setLong(i + 1, classloaderIds.get(i));
            }
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String name = rs.getString(2);
                    out.put(rs.getLong(1), name != null ? name : UNKNOWN_NAME);
                }
            }
        }
        return out;
    }
}
