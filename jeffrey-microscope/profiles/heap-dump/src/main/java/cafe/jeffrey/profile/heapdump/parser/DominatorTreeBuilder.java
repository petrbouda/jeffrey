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
package cafe.jeffrey.profile.heapdump.parser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import org.duckdb.DuckDBAppender;
import org.duckdb.DuckDBConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cafe.jeffrey.shared.common.measure.Measuring;

/**
 * Computes the dominator tree of the heap reference graph and the retained
 * size of every instance, persisting both into the index DB.
 *
 * Algorithm: Cooper-Harvey-Kennedy iterative dominators
 * (https://www.cs.rice.edu/~keith/EMBED/dom.pdf). The graph is built from a
 * synthetic virtual root (id 0) connecting to every GC root, plus every
 * outbound_ref edge. Instances unreachable from any GC root are excluded.
 *
 * Tables populated:
 * <ul>
 *   <li>{@code dominator(instance_id, dominator_id)} — one row per reachable
 *       instance; {@code dominator_id = 0} marks instances directly rooted
 *       at the virtual root.</li>
 *   <li>{@code retained_size(instance_id, bytes)} — bottom-up sum over the
 *       dominator tree.</li>
 * </ul>
 *
 * Memory cost: roughly 64 bytes per reachable instance for the in-memory
 * working set. Fine for heaps up to ~50M instances; for larger heaps the
 * implementation needs to spill, deferred to a future PR.
 *
 * The build is idempotent — calling it again clears and recomputes both
 * tables.
 */
public final class DominatorTreeBuilder {

    private static final Logger LOG = LoggerFactory.getLogger(DominatorTreeBuilder.class);

    /** Reserved id for the synthetic virtual root. HPROF id 0 means null, never a real instance. */
    public static final long VIRTUAL_ROOT = 0L;

    public record BuildResult(int reachableInstances, int rootEdges, long iterations,
                              java.time.Duration buildTime) {
    }

    private DominatorTreeBuilder() {
    }

    /**
     * Builds (or rebuilds) the dominator tree and retained-size tables for the
     * given index DB.
     */
    public static BuildResult build(Path indexDbPath) throws SQLException, IOException {
        if (indexDbPath == null) {
            throw new IllegalArgumentException("indexDbPath must not be null");
        }
        if (!Files.exists(indexDbPath)) {
            throw new IOException("Heap dump index file does not exist: path=" + indexDbPath);
        }

        String url = "jdbc:duckdb:" + indexDbPath.toAbsolutePath();
        Properties props = new Properties();
        try (Connection raw = DriverManager.getConnection(url, props);
             DuckDBConnection conn = raw.unwrap(DuckDBConnection.class)) {

            try (Statement s = conn.createStatement()) {
                s.execute("DELETE FROM dominator");
                s.execute("DELETE FROM retained_size");
            }

            var elapsed = Measuring.s(() -> {
                try {
                    return doBuild(conn);
                } catch (SQLException e) {
                    throw new RuntimeException(e);
                }
            });
            BuildResult r = elapsed.entity();
            BuildResult timed = new BuildResult(
                    r.reachableInstances, r.rootEdges, r.iterations, elapsed.duration());

            try (Statement s = conn.createStatement()) {
                s.execute("CHECKPOINT");
            }
            LOG.debug("Dominator tree built: path={} reachable={} roots={} iterations={} duration_ms={}",
                    indexDbPath, timed.reachableInstances, timed.rootEdges, timed.iterations,
                    timed.buildTime.toMillis());
            return timed;
        }
    }

    private static BuildResult doBuild(DuckDBConnection conn) throws SQLException {
        long[] ids = loadInstanceIds(conn);
        Map<Long, Integer> idToIndex = new HashMap<>(ids.length * 2);
        for (int i = 0; i < ids.length; i++) {
            idToIndex.put(ids[i], i);
        }
        // Index N = virtual root. We allocate one extra slot at index ids.length.
        int virtualIndex = ids.length;
        int totalNodes = ids.length + 1;

        int[][] succ = loadSuccessors(conn, idToIndex, virtualIndex);
        int[][] pred = invert(succ, totalNodes);

        // DFS from virtual root, build post-order then reverse for RPO.
        int[] postOrder = postOrderFromRoot(succ, virtualIndex, totalNodes);
        // rpo[k] = node at reverse-post-order position k.
        int[] rpo = new int[postOrder.length];
        for (int i = 0; i < postOrder.length; i++) {
            rpo[i] = postOrder[postOrder.length - 1 - i];
        }
        int[] rpoOf = new int[totalNodes];
        Arrays.fill(rpoOf, -1);
        for (int k = 0; k < rpo.length; k++) {
            rpoOf[rpo[k]] = k;
        }

        // idom[v] holds the index of v's immediate dominator, or -1 if unset.
        int[] idom = new int[totalNodes];
        Arrays.fill(idom, -1);
        idom[virtualIndex] = virtualIndex;

        long iterations = 0;
        boolean changed = true;
        while (changed) {
            changed = false;
            iterations++;
            for (int k = 1; k < rpo.length; k++) { // skip entry (k=0)
                int n = rpo[k];
                int newIdom = -1;
                for (int p : pred[n]) {
                    if (idom[p] != -1) {
                        if (newIdom == -1) {
                            newIdom = p;
                        } else {
                            newIdom = intersect(p, newIdom, idom, rpoOf);
                        }
                    }
                }
                if (newIdom != idom[n]) {
                    idom[n] = newIdom;
                    changed = true;
                }
            }
        }

        // Retained size: bottom-up over the dom tree.
        long[] shallow = loadShallowSizes(conn, ids);
        long[] retained = new long[totalNodes];
        // Each reachable node starts with its shallow size.
        for (int v : rpo) {
            if (v != virtualIndex) {
                retained[v] = shallow[v];
            }
        }
        // Process in reverse RPO so children come before their dominator.
        for (int k = rpo.length - 1; k >= 1; k--) {
            int v = rpo[k];
            int d = idom[v];
            if (d != -1 && d != virtualIndex) {
                retained[d] += retained[v];
            }
        }

        // Persist.
        int reachable = 0;
        try (DuckDBAppender domApp = conn.createAppender("dominator");
             DuckDBAppender retApp = conn.createAppender("retained_size")) {
            for (int v : rpo) {
                if (v == virtualIndex) {
                    continue;
                }
                int d = idom[v];
                long instanceId = ids[v];
                long dominatorId = d == virtualIndex ? VIRTUAL_ROOT : ids[d];
                domApp.beginRow();
                domApp.append(instanceId);
                domApp.append(dominatorId);
                domApp.endRow();

                retApp.beginRow();
                retApp.append(instanceId);
                retApp.append(retained[v]);
                retApp.endRow();
                reachable++;
            }
        }
        return new BuildResult(reachable, succ[virtualIndex].length, iterations, java.time.Duration.ZERO);
    }

    /** Cooper-Harvey-Kennedy intersect: walk both finger pointers up the dom tree until they meet. */
    private static int intersect(int b1, int b2, int[] idom, int[] rpoOf) {
        while (b1 != b2) {
            while (rpoOf[b1] > rpoOf[b2]) {
                b1 = idom[b1];
            }
            while (rpoOf[b2] > rpoOf[b1]) {
                b2 = idom[b2];
            }
        }
        return b1;
    }

    private static long[] loadInstanceIds(Connection conn) throws SQLException {
        List<Long> list = new ArrayList<>();
        try (Statement s = conn.createStatement();
             ResultSet rs = s.executeQuery("SELECT instance_id FROM instance ORDER BY instance_id")) {
            while (rs.next()) {
                list.add(rs.getLong(1));
            }
        }
        long[] arr = new long[list.size()];
        for (int i = 0; i < arr.length; i++) {
            arr[i] = list.get(i);
        }
        return arr;
    }

    private static int[][] loadSuccessors(
            Connection conn, Map<Long, Integer> idToIndex, int virtualIndex) throws SQLException {
        int n = idToIndex.size() + 1;
        List<List<Integer>> tmp = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            tmp.add(new ArrayList<>());
        }

        // Outbound refs from real instances.
        try (Statement s = conn.createStatement();
             ResultSet rs = s.executeQuery("SELECT source_id, target_id FROM outbound_ref")) {
            while (rs.next()) {
                Integer src = idToIndex.get(rs.getLong(1));
                Integer dst = idToIndex.get(rs.getLong(2));
                if (src != null && dst != null) {
                    tmp.get(src).add(dst);
                }
            }
        }

        // Virtual root → every GC-rooted instance present in the instance table.
        try (Statement s = conn.createStatement();
             ResultSet rs = s.executeQuery("SELECT DISTINCT instance_id FROM gc_root")) {
            while (rs.next()) {
                Integer dst = idToIndex.get(rs.getLong(1));
                if (dst != null) {
                    tmp.get(virtualIndex).add(dst);
                }
            }
        }

        int[][] out = new int[n][];
        for (int i = 0; i < n; i++) {
            List<Integer> l = tmp.get(i);
            out[i] = new int[l.size()];
            for (int j = 0; j < l.size(); j++) {
                out[i][j] = l.get(j);
            }
        }
        return out;
    }

    private static int[][] invert(int[][] succ, int n) {
        int[] degree = new int[n];
        for (int u = 0; u < n; u++) {
            for (int v : succ[u]) {
                degree[v]++;
            }
        }
        int[][] pred = new int[n][];
        int[] cursor = new int[n];
        for (int v = 0; v < n; v++) {
            pred[v] = new int[degree[v]];
        }
        for (int u = 0; u < n; u++) {
            for (int v : succ[u]) {
                pred[v][cursor[v]++] = u;
            }
        }
        return pred;
    }

    /** Iterative DFS (avoid recursion blowup), returning post-order for nodes reachable from {@code root}. */
    private static int[] postOrderFromRoot(int[][] succ, int root, int totalNodes) {
        boolean[] seen = new boolean[totalNodes];
        int[] childIdx = new int[totalNodes];
        Deque<Integer> stack = new ArrayDeque<>();
        List<Integer> order = new ArrayList<>();

        stack.push(root);
        seen[root] = true;
        while (!stack.isEmpty()) {
            int u = stack.peek();
            int idx = childIdx[u];
            int[] children = succ[u];
            if (idx < children.length) {
                int v = children[idx];
                childIdx[u] = idx + 1;
                if (!seen[v]) {
                    seen[v] = true;
                    stack.push(v);
                }
            } else {
                stack.pop();
                order.add(u);
            }
        }
        int[] arr = new int[order.size()];
        for (int i = 0; i < arr.length; i++) {
            arr[i] = order.get(i);
        }
        return arr;
    }

    private static long[] loadShallowSizes(Connection conn, long[] ids) throws SQLException {
        long[] sizes = new long[ids.length + 1]; // +1 for virtual root slot, stays 0
        try (Statement s = conn.createStatement();
             ResultSet rs = s.executeQuery("SELECT instance_id, shallow_size FROM instance")) {
            while (rs.next()) {
                long id = rs.getLong(1);
                int idx = Arrays.binarySearch(ids, id);
                if (idx >= 0) {
                    sizes[idx] = rs.getInt(2);
                }
            }
        }
        return sizes;
    }
}
