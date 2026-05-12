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
package cafe.jeffrey.profile.heapdump.oql.function;

import cafe.jeffrey.profile.heapdump.parser.HeapView;
import cafe.jeffrey.profile.heapdump.parser.InstanceRow;
import cafe.jeffrey.profile.heapdump.parser.OutboundRefRow;

import java.sql.SQLException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

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
        Set<Long> visited = new HashSet<>();
        Deque<Long> frontier = new ArrayDeque<>();
        List<InstanceRow> out = new ArrayList<>();
        frontier.add(startId);
        visited.add(startId);
        while (!frontier.isEmpty() && visited.size() < MAX_TRAVERSAL_VISITS) {
            long id = frontier.poll();
            List<OutboundRefRow> refs = forward
                    ? view.outboundRefs(id)
                    : view.inboundRefs(id);
            for (OutboundRefRow ref : refs) {
                long next = forward ? ref.targetId() : ref.sourceId();
                if (next == 0L) continue;
                if (visited.add(next)) {
                    Optional<InstanceRow> inst = view.findInstanceById(next);
                    inst.ifPresent(out::add);
                    frontier.add(next);
                }
            }
        }
        return out;
    }

    private static List<InstanceRow> resolveTargets(HeapView view, List<OutboundRefRow> refs) throws SQLException {
        List<InstanceRow> out = new ArrayList<>(refs.size());
        for (OutboundRefRow ref : refs) {
            if (ref.targetId() == 0L) continue;
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
