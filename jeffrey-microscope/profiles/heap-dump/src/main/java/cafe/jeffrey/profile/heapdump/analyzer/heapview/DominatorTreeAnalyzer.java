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
import cafe.jeffrey.profile.heapdump.model.DominatorNode;
import cafe.jeffrey.profile.heapdump.model.DominatorTreeResponse;
import cafe.jeffrey.profile.heapdump.parser.DominatorTreeBuilder;
import cafe.jeffrey.profile.heapdump.parser.HeapView;
import cafe.jeffrey.profile.heapdump.parser.HprofTag;

/**
 * HeapView-backed equivalent of
 * {@link cafe.jeffrey.profile.heapdump.analyzer.DominatorTreeAnalyzer}.
 *
 * Renders one level of the dominator tree at a time — children of a given
 * parent — for the UI's lazy-expand pattern. Sort by retained size desc.
 *
 * Caller passes {@code parentId = 0L} to fetch the top level (instances
 * directly dominated by the virtual root); any other id fetches its
 * immediate children.
 *
 * Requires the dominator tables to be populated
 * ({@link DominatorTreeBuilder#build}); throws {@link IllegalStateException}
 * otherwise.
 */
public final class DominatorTreeAnalyzer {

    private static final int DEFAULT_LIMIT = 100;

    private DominatorTreeAnalyzer() {
    }

    public static DominatorTreeResponse children(HeapView view, long parentId) throws SQLException {
        return children(view, parentId, DEFAULT_LIMIT);
    }

    public static DominatorTreeResponse children(HeapView view, long parentId, int limit) throws SQLException {
        if (!view.hasDominatorTree()) {
            throw new IllegalStateException(
                    "Dominator tree not built; call DominatorTreeBuilder.build(indexDb) first");
        }
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be positive: limit=" + limit);
        }

        long totalHeapSize = view.totalShallowSize();

        // GC root kind lookup (for top-level nodes) cached in one query.
        Map<Long, Integer> rootKindByInstance = parentId == DominatorTreeBuilder.VIRTUAL_ROOT
                ? loadGcRootKinds(view)
                : Map.of();

        // children: dominator(d.dominator_id = parent) joined to instance + class + retained.
        // Fetch limit+1 to detect hasMore.
        String sql = "SELECT d.instance_id, c.name, i.shallow_size, r.bytes, "
                + "       EXISTS (SELECT 1 FROM dominator d2 WHERE d2.dominator_id = d.instance_id) AS has_children "
                + "FROM dominator d "
                + "JOIN instance i ON i.instance_id = d.instance_id "
                + "LEFT JOIN class c ON i.class_id = c.class_id "
                + "JOIN retained_size r ON r.instance_id = d.instance_id "
                + "WHERE d.dominator_id = ? "
                + "ORDER BY r.bytes DESC "
                + "LIMIT ?";

        List<DominatorNode> nodes = new ArrayList<>();
        try (PreparedStatement stmt = view.connection().prepareStatement(sql)) {
            stmt.setLong(1, parentId);
            stmt.setInt(2, limit + 1);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next() && nodes.size() < limit) {
                    long instanceId = rs.getLong(1);
                    String className = rs.getString(2);
                    if (className == null) {
                        className = "<unknown>";
                    }
                    long shallow = rs.getInt(3);
                    long retained = rs.getLong(4);
                    boolean hasChildren = rs.getBoolean(5);
                    double percent = totalHeapSize == 0 ? 0.0 : (retained * 100.0) / totalHeapSize;
                    String rootKind = rootKindByInstance.containsKey(instanceId)
                            ? kindName(rootKindByInstance.get(instanceId))
                            : null;
                    nodes.add(new DominatorNode(
                            instanceId, className, Map.of(),
                            null, // fieldName: not applicable at this aggregation level
                            shallow, retained, percent, hasChildren, rootKind));
                }
            }
        }
        boolean hasMore = nodes.size() == limit && countChildren(view, parentId) > limit;
        boolean compressedOops = view.metadata().compressedOops();

        return new DominatorTreeResponse(nodes, totalHeapSize, compressedOops, hasMore);
    }

    private static long countChildren(HeapView view, long parentId) throws SQLException {
        try (PreparedStatement stmt = view.connection().prepareStatement(
                "SELECT COUNT(*) FROM dominator WHERE dominator_id = ?")) {
            stmt.setLong(1, parentId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0L;
            }
        }
    }

    private static Map<Long, Integer> loadGcRootKinds(HeapView view) throws SQLException {
        Map<Long, Integer> out = new HashMap<>();
        try (PreparedStatement stmt = view.connection().prepareStatement(
                "SELECT instance_id, root_kind FROM gc_root");
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                out.put(rs.getLong(1), rs.getInt(2));
            }
        }
        return out;
    }

    private static String kindName(int rootKind) {
        return switch (rootKind) {
            case HprofTag.Sub.ROOT_UNKNOWN -> "Unknown";
            case HprofTag.Sub.ROOT_JNI_GLOBAL -> "JNI global";
            case HprofTag.Sub.ROOT_JNI_LOCAL -> "JNI local";
            case HprofTag.Sub.ROOT_JAVA_FRAME -> "Java frame";
            case HprofTag.Sub.ROOT_NATIVE_STACK -> "Native stack";
            case HprofTag.Sub.ROOT_STICKY_CLASS -> "Sticky class";
            case HprofTag.Sub.ROOT_THREAD_BLOCK -> "Thread block";
            case HprofTag.Sub.ROOT_MONITOR_USED -> "Monitor used";
            case HprofTag.Sub.ROOT_THREAD_OBJECT -> "Thread object";
            default -> "Other";
        };
    }
}
