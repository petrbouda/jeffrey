/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package cafe.jeffrey.profile.heapdump.oql.function;

import cafe.jeffrey.profile.heapdump.view.HeapView;
import cafe.jeffrey.profile.heapdump.view.InstanceRow;
import cafe.jeffrey.profile.heapdump.view.OutboundRefRow;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Heap-graph traversal helpers used by the Plan C executor.
 *
 * <ul>
 *   <li>{@link #inbounds} / {@link #outbounds} — single-hop references via the
 *       {@code outbound_ref} table.</li>
 *   <li>{@link #referrers} / {@link #reachables} — transitive BFS that
 *       short-circuits on a configurable cap so a pathological scan can never
 *       run unbounded.</li>
 * </ul>
 */
public final class GraphWalkFunctions {

    /** Maximum number of instances visited by a transitive walk per call. */
    private static final int MAX_TRAVERSAL_VISITS = 50_000;

    /**
     * The transitive walk, one statement for either direction: {@code %1$s} is the column the walk
     * moves to and {@code %2$s} the one it moves from. Breadth-first, with {@code USING KEY} keeping
     * one row per instance as the visited set, and no level started once the visit cap is reached;
     * the answer is the instances visited, nearest first, without the start itself. Ids that name no
     * instance (a class, the null reference) are walked through but not answered.
     *
     * <p>It replaced a Java walk that issued a query per visited instance -- and in the forward
     * direction a query against {@code source_id}, which has no index, so each one scanned the
     * reference table: {@code reachables} of one thread on a 144 MB dump took over three minutes
     * that way and takes a quarter of a second this way, on DuckDB 2.0's recursive CTEs.
     */
    private static final String TRANSITIVE_WALK_SQL = """
            WITH RECURSIVE walk(instance_id, depth) USING KEY (instance_id) AS (
                SELECT ?::BIGINT, 0
                UNION
                SELECT r.%1$s, min(w.depth) + 1
                FROM walk w
                JOIN outbound_ref r ON r.%2$s = w.instance_id
                WHERE r.%1$s <> 0
                  AND r.%1$s NOT IN (SELECT instance_id FROM recurring.walk)
                  AND (SELECT count(*) FROM recurring.walk) < ?
                GROUP BY r.%1$s
            )
            SELECT i.instance_id, i.class_id, i.file_offset, i.record_kind, i.shallow_size,
                   i.array_length, i.primitive_type
            FROM walk w
            JOIN instance i ON i.instance_id = w.instance_id
            WHERE w.depth > 0
            ORDER BY w.depth, w.instance_id
            LIMIT ?
            """;

    private static final String REACHABLES_SQL = TRANSITIVE_WALK_SQL.formatted("target_id", "source_id");

    private static final String REFERRERS_SQL = TRANSITIVE_WALK_SQL.formatted("source_id", "target_id");

    private GraphWalkFunctions() {
    }

    public static List<InstanceRow> outbounds(HeapView view, long sourceId) throws SQLException {
        List<OutboundRefRow> refs = view.outboundRefs(sourceId);
        return resolveTargets(view, refs);
    }

    public static List<InstanceRow> inbounds(HeapView view, long targetId) throws SQLException {
        List<OutboundRefRow> refs = view.inboundRefs(targetId);
        return resolveSources(view, refs);
    }

    public static List<InstanceRow> referrers(HeapView view, long targetId) throws SQLException {
        return bfs(view, targetId, false);
    }

    public static List<InstanceRow> reachables(HeapView view, long sourceId) throws SQLException {
        return bfs(view, sourceId, true);
    }

    private static List<InstanceRow> bfs(HeapView view, long startId, boolean forward) throws SQLException {
        String sql = forward ? REACHABLES_SQL : REFERRERS_SQL;
        List<InstanceRow> out = new ArrayList<>();
        try (PreparedStatement stmt = view.databaseClient().connection().prepareStatement(sql)) {
            stmt.setLong(1, startId);
            stmt.setInt(2, MAX_TRAVERSAL_VISITS);
            stmt.setInt(3, MAX_TRAVERSAL_VISITS);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    out.add(new InstanceRow(
                            rs.getLong(1),
                            rs.getObject(2, Long.class),
                            rs.getLong(3),
                            InstanceRow.Kind.fromOrdinal(rs.getInt(4)),
                            rs.getInt(5),
                            rs.getObject(6, Integer.class),
                            rs.getObject(7, Integer.class)));
                }
            }
        }
        return out;
    }

    private static List<InstanceRow> resolveTargets(HeapView view, List<OutboundRefRow> refs) throws SQLException {
        List<InstanceRow> out = new ArrayList<>(refs.size());
        for (OutboundRefRow ref : refs) {
            if (ref.targetId() == 0L) {
                continue;
            }
            view.findInstanceById(ref.targetId()).ifPresent(out::add);
        }
        return out;
    }

    private static List<InstanceRow> resolveSources(HeapView view, List<OutboundRefRow> refs) throws SQLException {
        List<InstanceRow> out = new ArrayList<>(refs.size());
        for (OutboundRefRow ref : refs) {
            view.findInstanceById(ref.sourceId()).ifPresent(out::add);
        }
        return out;
    }
}
