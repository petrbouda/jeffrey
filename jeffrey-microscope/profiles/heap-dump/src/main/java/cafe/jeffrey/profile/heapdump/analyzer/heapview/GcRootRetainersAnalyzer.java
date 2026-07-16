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
import java.util.List;
import cafe.jeffrey.profile.heapdump.model.GCRootRetainer;
import cafe.jeffrey.profile.heapdump.view.HeapView;
import cafe.jeffrey.profile.heapdump.view.HprofTag;

/**
 * Ranks individual GC roots by retained size — the "Top Retainers" view.
 *
 * <p>Joins {@code gc_root} with {@code instance}, {@code class}, and
 * {@code retained_size}. Requires the dominator tree to be built (so retained
 * sizes are available); throws {@link IllegalStateException} otherwise.
 *
 * <p>An optional root-kind filter (raw HPROF sub-tag bytes) lets a caller
 * scope the result to e.g. just native references (the "Native / JNI" tab).
 */
public final class GcRootRetainersAnalyzer {

    private static final String UNKNOWN_NAME = "<unknown>";

    private GcRootRetainersAnalyzer() {
    }

    public static List<GCRootRetainer> analyze(HeapView view, int limit) throws SQLException {
        return analyze(view, limit, List.of());
    }

    /**
     * @param rootKinds raw HPROF sub-tag bytes to keep ({@link HprofTag.Sub}); empty
     *                  list disables the filter.
     */
    public static List<GCRootRetainer> analyze(HeapView view, int limit, List<Integer> rootKinds)
            throws SQLException {
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be positive: limit=" + limit);
        }
        if (!view.hasDominatorTree()) {
            throw new IllegalStateException(
                    "Top Retainers requires the dominator tree; call DominatorTreeBuilder.build(...) first");
        }

        StringBuilder sql = new StringBuilder()
                .append("SELECT g.instance_id, c.name, g.root_kind, i.shallow_size, ")
                .append("       COALESCE(r.bytes, 0) AS retained_bytes ")
                .append("FROM gc_root g ")
                .append("JOIN instance i ON i.instance_id = g.instance_id ")
                .append("LEFT JOIN class c ON c.class_id = i.class_id ")
                .append("LEFT JOIN retained_size r ON r.instance_id = g.instance_id ");
        if (!rootKinds.isEmpty()) {
            sql.append("WHERE g.root_kind IN (")
                    .append(String.join(",", rootKinds.stream().map(k -> "?").toList()))
                    .append(") ");
        }
        sql.append("ORDER BY retained_bytes DESC, g.instance_id ")
                .append("LIMIT ?");

        List<GCRootRetainer> out = new ArrayList<>(Math.min(limit, 256));
        try (PreparedStatement stmt = view.databaseClient().connection().prepareStatement(sql.toString())) {
            int idx = 1;
            for (Integer kind : rootKinds) {
                stmt.setInt(idx++, kind);
            }
            stmt.setInt(idx, limit);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    long id = rs.getLong(1);
                    String name = rs.getString(2);
                    int kind = rs.getInt(3);
                    long shallow = rs.getLong(4);
                    long retained = rs.getLong(5);
                    if (name == null) {
                        name = UNKNOWN_NAME;
                    }
                    out.add(new GCRootRetainer(
                            id, name, HprofTag.Sub.rootKindName(kind), shallow, retained));
                }
            }
        }
        return List.copyOf(out);
    }
}
