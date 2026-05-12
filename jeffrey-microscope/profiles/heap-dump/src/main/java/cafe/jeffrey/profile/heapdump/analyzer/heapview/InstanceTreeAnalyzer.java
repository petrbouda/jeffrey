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
import cafe.jeffrey.profile.heapdump.model.InstanceTreeNode;
import cafe.jeffrey.profile.heapdump.model.InstanceTreeRequest;
import cafe.jeffrey.profile.heapdump.model.InstanceTreeResponse;
import cafe.jeffrey.profile.heapdump.parser.HeapView;
import cafe.jeffrey.profile.heapdump.parser.InstanceFieldDescriptor;
import cafe.jeffrey.profile.heapdump.parser.InstanceRow;
import cafe.jeffrey.profile.heapdump.parser.JavaClassRow;

/**
 * HeapView-backed equivalent of
 * {@link cafe.jeffrey.profile.heapdump.analyzer.InstanceTreeAnalyzer}.
 *
 * Renders one level of the heap reference graph at a time — root + direct
 * children — for the UI's referrers / reachables tree views.
 *
 * Two modes:
 * <ul>
 *   <li>REFERRERS: who points <em>at</em> {@code objectId}? Backed by
 *       {@link HeapView#inboundRefs}.</li>
 *   <li>REACHABLES: what does {@code objectId} point to? Backed by
 *       {@link HeapView#outboundRefs}.</li>
 * </ul>
 *
 * Field-name resolution for {@code REACHABLES}: walks the source instance's
 * class chain and picks the descriptor at the global field index that
 * {@code outbound_ref.field_id} carries (mirrors PathToGCRootAnalyzer).
 *
 * For {@code REFERRERS}, the field name is left null because resolution
 * would require looking up <em>each referring object's</em> class chain
 * to identify which field points at the target — straightforward but
 * costly per page; deferred until callers ask for it.
 */
public final class InstanceTreeAnalyzer {

    private InstanceTreeAnalyzer() {
    }

    public static InstanceTreeResponse analyze(HeapView view, InstanceTreeRequest request) throws SQLException {
        if (request == null) {
            throw new IllegalArgumentException("request must not be null");
        }
        if (request.limit() <= 0) {
            throw new IllegalArgumentException("limit must be positive: limit=" + request.limit());
        }

        InstanceRow root = view.findInstanceById(request.objectId()).orElse(null);
        if (root == null) {
            return InstanceTreeResponse.notFound();
        }
        InstanceTreeNode rootNode = buildRootNode(view, root);

        // Pull children with the same shape regardless of mode — just swap source/target.
        String sql = request.mode() == InstanceTreeRequest.TreeMode.REFERRERS
                ? "SELECT source_id, field_kind, field_id FROM outbound_ref WHERE target_id = ? "
                        + "ORDER BY source_id LIMIT ? OFFSET ?"
                : "SELECT target_id, field_kind, field_id FROM outbound_ref WHERE source_id = ? "
                        + "ORDER BY field_kind, field_id LIMIT ? OFFSET ?";

        long totalChildren = countChildren(view, request);
        List<InstanceTreeNode> children = new ArrayList<>();
        try (PreparedStatement stmt = view.connection().prepareStatement(sql)) {
            stmt.setLong(1, request.objectId());
            stmt.setInt(2, request.limit());
            stmt.setInt(3, request.offset());
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    long otherId = rs.getLong(1);
                    int fieldKind = rs.getInt(2);
                    int fieldId = rs.getInt(3);
                    children.add(buildChild(view, request, otherId, fieldKind, fieldId));
                }
            }
        }

        boolean hasMore = (long) request.offset() + children.size() < totalChildren;
        return new InstanceTreeResponse(rootNode, List.copyOf(children), hasMore,
                (int) Math.min(totalChildren, Integer.MAX_VALUE));
    }

    private static long countChildren(HeapView view, InstanceTreeRequest req) throws SQLException {
        String column = req.mode() == InstanceTreeRequest.TreeMode.REFERRERS ? "target_id" : "source_id";
        try (PreparedStatement stmt = view.connection().prepareStatement(
                "SELECT COUNT(*) FROM outbound_ref WHERE " + column + " = ?")) {
            stmt.setLong(1, req.objectId());
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0L;
            }
        }
    }

    private static InstanceTreeNode buildRootNode(HeapView view, InstanceRow inst) throws SQLException {
        String className = classNameFor(view, inst);
        Long retained = view.hasDominatorTree() ? probeRetained(view, inst.instanceId()) : null;
        long childCount = countChildrenAny(view, inst.instanceId());
        return new InstanceTreeNode(
                inst.instanceId(),
                className,
                className + "@" + Long.toHexString(inst.instanceId()),
                inst.shallowSize(),
                retained,
                null,
                "ROOT",
                childCount > 0,
                (int) Math.min(childCount, Integer.MAX_VALUE));
    }

    private static long countChildrenAny(HeapView view, long instanceId) throws SQLException {
        try (PreparedStatement stmt = view.connection().prepareStatement(
                "SELECT COUNT(*) FROM outbound_ref WHERE source_id = ? OR target_id = ?")) {
            stmt.setLong(1, instanceId);
            stmt.setLong(2, instanceId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0L;
            }
        }
    }

    private static InstanceTreeNode buildChild(
            HeapView view, InstanceTreeRequest request, long otherId, int fieldKind, int fieldId)
            throws SQLException {
        InstanceRow other = view.findInstanceById(otherId).orElse(null);
        String className = other != null ? classNameFor(view, other) : "<unknown>";
        long shallow = other != null ? other.shallowSize() : 0L;
        Long retained = view.hasDominatorTree() ? probeRetained(view, otherId) : null;

        String fieldName = null;
        if (request.mode() == InstanceTreeRequest.TreeMode.REACHABLES) {
            fieldName = resolveFieldName(view, request.objectId(), fieldKind, fieldId);
        }
        // Children of this node = its own outbound + inbound refs (best signal of "expandable").
        long childCount = countChildrenAny(view, otherId);

        String relationship = request.mode() == InstanceTreeRequest.TreeMode.REFERRERS
                ? "REFERRER"
                : "REACHABLE";

        return new InstanceTreeNode(
                otherId,
                className,
                className + "@" + Long.toHexString(otherId),
                shallow,
                retained,
                fieldName,
                relationship,
                childCount > 0,
                (int) Math.min(childCount, Integer.MAX_VALUE));
    }

    private static String resolveFieldName(
            HeapView view, long sourceInstanceId, int fieldKind, int fieldId) throws SQLException {
        if (fieldKind == 1) {
            return "[" + fieldId + "]";
        }
        InstanceRow source = view.findInstanceById(sourceInstanceId).orElse(null);
        if (source == null || source.classId() == null) {
            return null;
        }
        List<InstanceFieldDescriptor> chain = view.instanceFieldsWithChain(source.classId());
        if (fieldId < 0 || fieldId >= chain.size()) {
            return null;
        }
        return chain.get(fieldId).name();
    }

    private static String classNameFor(HeapView view, InstanceRow inst) throws SQLException {
        if (inst.classId() == null) {
            return switch (inst.kind()) {
                case PRIMITIVE_ARRAY -> "<primitive[]>";
                case OBJECT_ARRAY -> "Object[]";
                case INSTANCE -> "<unknown>";
            };
        }
        return view.findClassById(inst.classId()).map(JavaClassRow::name).orElse("<unknown>");
    }

    private static Long probeRetained(HeapView view, long instanceId) throws SQLException {
        try (PreparedStatement stmt = view.connection().prepareStatement(
                "SELECT bytes FROM retained_size WHERE instance_id = ?")) {
            stmt.setLong(1, instanceId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? rs.getLong(1) : null;
            }
        }
    }
}
