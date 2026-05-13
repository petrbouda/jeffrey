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
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.duckdb.DuckDBAppender;
import org.duckdb.DuckDBConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cafe.jeffrey.profile.heapdump.model.SubPhaseTiming;
import cafe.jeffrey.shared.common.measure.Elapsed;
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

    /**
     * Number of virtual threads used for the parallel persist phase — one per
     * output table ({@code dominator} and {@code retained_size}). The natural
     * ceiling is 2 because DuckDB serialises commits within a single table,
     * so any per-table sharding beyond that would just contend on the same
     * write lock. Surfaced into the UI sub-phase note via {@link #PERSIST_PARALLELISM_NOTE}.
     */
    private static final int PERSIST_PARALLELISM = 2;

    /** Human-readable form of {@link #PERSIST_PARALLELISM} for the UI accordion. */
    private static final String PERSIST_PARALLELISM_NOTE = PERSIST_PARALLELISM + " virtual threads";

    public record BuildResult(int reachableInstances, int rootEdges, long iterations,
                              java.time.Duration buildTime, List<SubPhaseTiming> subPhases) {

        /** Backwards-compatible factory for tests/callers that don't need sub-phase data. */
        public BuildResult(int reachableInstances, int rootEdges, long iterations, Duration buildTime) {
            this(reachableInstances, rootEdges, iterations, buildTime, List.of());
        }
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

            Elapsed<BuildResult> elapsed = Measuring.s(() -> {
                try {
                    return doBuild(conn);
                } catch (SQLException e) {
                    throw new RuntimeException(e);
                }
            });
            BuildResult r = elapsed.entity();
            BuildResult timed = new BuildResult(
                    r.reachableInstances, r.rootEdges, r.iterations, elapsed.duration(), r.subPhases());

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
        Elapsed<InstanceMeta> metaE = measure(() -> loadInstanceMeta(conn));
        InstanceMeta meta = metaE.entity();
        long[] ids = meta.ids();
        long[] shallow = meta.shallow();
        // Index N = virtual root. We allocate one extra slot at index ids.length.
        int virtualIndex = ids.length;
        int totalNodes = ids.length + 1;

        Elapsed<int[][]> succE = measure(() -> loadSuccessors(conn, ids, virtualIndex));
        int[][] succ = succE.entity();

        Elapsed<int[][]> predE = Measuring.s(() -> invert(succ, totalNodes));
        int[][] pred = predE.entity();

        // DFS from the virtual root: assigns each reachable node a preorder number,
        // captures the spanning-tree parent. Replaces the old RPO setup.
        Elapsed<DfsData> dfsE = Measuring.s(() -> computeDfsData(succ, virtualIndex, totalNodes));
        DfsData dfs = dfsE.entity();
        int reachableNodes = dfs.reachable();

        // semi-NCA dominators (Georgiadis & Tarjan, "Finding Dominators Revisited", 2004).
        // Replaces the old Cooper-Harvey-Kennedy fixed-point loop. On heap reference
        // graphs (heavily cyclic, unlike CFGs) CHK iterated 100s of times until
        // convergence; semi-NCA is essentially linear regardless of graph shape.
        Elapsed<int[]> idomE = Measuring.s(() -> computeDominatorsSemiNCA(pred, dfs, totalNodes));
        int[] idom = idomE.entity();

        // Retained size: bottom-up over the dom tree, processed in reverse DFS
        // preorder so every node sees its descendants finalised before adding
        // into its dominator.
        Elapsed<long[]> retE = Measuring.s(() ->
                computeRetained(idom, dfs, shallow, virtualIndex, totalNodes));
        long[] retained = retE.entity();

        // Stage 1: materialise the per-row data into compact primitive arrays.
        // This is the actual reusable work — both appenders need the same
        // {instanceId, dominatorId, retained} triplets in preorder. Computing
        // them once on a single thread is cheaper than re-running the branchy
        // {v == virtualIndex ? VIRTUAL_ROOT : ids[d]} logic in each shard.
        Elapsed<PersistRowData> rowsE = Measuring.s(() ->
                buildPersistRowData(idom, dfs, retained, ids, virtualIndex, reachableNodes));
        PersistRowData rows = rowsE.entity();
        int reachable = rows.count();

        // Stage 2: PERSIST_PARALLELISM virtual threads, one per output table.
        // Each owns a DuckDBAppender. DuckDB appenders are bound to a single
        // (connection, table) pair and the threads target *different* tables,
        // so they don't contend on table-level locks or ART-index inserts.
        // Net wall time ≈ max(dom_persist, ret_persist) instead of their sum.
        Duration persistDuration = Measuring.r(() -> {
            try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
                Future<?> domFuture = executor.submit(() -> {
                    try (DuckDBAppender app = conn.createAppender("dominator")) {
                        long[] instanceIds = rows.instanceIds();
                        long[] dominatorIds = rows.dominatorIds();
                        for (int i = 0; i < rows.count(); i++) {
                            app.beginRow();
                            app.append(instanceIds[i]);
                            app.append(dominatorIds[i]);
                            app.endRow();
                        }
                    } catch (SQLException e) {
                        throw new RuntimeException(e);
                    }
                    return null;
                });
                Future<?> retFuture = executor.submit(() -> {
                    try (DuckDBAppender app = conn.createAppender("retained_size")) {
                        long[] instanceIds = rows.instanceIds();
                        long[] retainedBytes = rows.retainedBytes();
                        for (int i = 0; i < rows.count(); i++) {
                            app.beginRow();
                            app.append(instanceIds[i]);
                            app.append(retainedBytes[i]);
                            app.endRow();
                        }
                    } catch (SQLException e) {
                        throw new RuntimeException(e);
                    }
                    return null;
                });
                try {
                    domFuture.get();
                    retFuture.get();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(e);
                } catch (ExecutionException e) {
                    Throwable cause = e.getCause();
                    if (cause instanceof RuntimeException re) {
                        throw re;
                    }
                    throw new RuntimeException(cause);
                }
            }
        });

        LOG.debug(
                "Dominator tree phases: load_meta_ms={} load_successors_ms={} invert_ms={} "
                        + "dfs_ms={} semi_nca_ms={} retained_ms={} stage_rows_ms={} persist_ms={} "
                        + "instances={} edges={} reachable={}",
                metaE.duration().toMillis(),
                succE.duration().toMillis(),
                predE.duration().toMillis(),
                dfsE.duration().toMillis(),
                idomE.duration().toMillis(),
                retE.duration().toMillis(),
                rowsE.duration().toMillis(),
                persistDuration.toMillis(),
                ids.length,
                edgeCount(succ),
                reachable);

        List<SubPhaseTiming> subPhases = List.of(
                new SubPhaseTiming("load_meta", metaE.duration().toMillis(), null),
                new SubPhaseTiming("load_successors", succE.duration().toMillis(), null),
                new SubPhaseTiming("invert", predE.duration().toMillis(), null),
                new SubPhaseTiming("dfs", dfsE.duration().toMillis(), null),
                new SubPhaseTiming("semi_nca", idomE.duration().toMillis(), "Lengauer-Tarjan"),
                new SubPhaseTiming("retained", retE.duration().toMillis(), null),
                new SubPhaseTiming("stage_rows", rowsE.duration().toMillis(), null),
                new SubPhaseTiming("persist", persistDuration.toMillis(), PERSIST_PARALLELISM_NOTE));

        // BuildResult.iterations was a CHK leftover (number of fixed-point passes);
        // semi-NCA needs no fixed-point so we report 1 to keep the field honest.
        return new BuildResult(reachable, succ[virtualIndex].length, 1L,
                java.time.Duration.ZERO, subPhases);
    }

    /**
     * DFS spanning tree captured during the preorder walk from the virtual root.
     *
     * @param preorder    {@code preorder[i]} = the node visited at DFS index {@code i}
     * @param dfsNum      {@code dfsNum[v]}   = DFS index of {@code v} ({@code -1} if unreachable)
     * @param parent      {@code parent[v]}   = v's parent in the DFS spanning tree
     *                    ({@code parent[root] = root} as a sentinel)
     * @param reachable   how many nodes were reached from the virtual root
     */
    private record DfsData(int[] preorder, int[] dfsNum, int[] parent, int reachable) {
    }

    /**
     * Compact column-store of the per-row data each appender thread needs.
     * Built once in preorder; both the {@code dominator} and {@code retained_size}
     * appender threads read straight off these flat arrays without re-doing the
     * {@code v == virtualIndex ? VIRTUAL_ROOT : ids[d]} branching.
     */
    private record PersistRowData(
            long[] instanceIds,
            long[] dominatorIds,
            long[] retainedBytes,
            int count) {
    }

    private static PersistRowData buildPersistRowData(
            int[] idom,
            DfsData dfs,
            long[] retained,
            long[] ids,
            int virtualIndex,
            int reachableNodes) {
        // Worst case the virtual root sits in the preorder too, so size with a -1.
        int cap = Math.max(0, reachableNodes - 1);
        long[] instanceIds = new long[cap];
        long[] dominatorIds = new long[cap];
        long[] retainedBytes = new long[cap];
        int[] preorder = dfs.preorder();
        int n = 0;
        for (int i = 0; i < reachableNodes; i++) {
            int v = preorder[i];
            if (v == virtualIndex) {
                continue;
            }
            int d = idom[v];
            instanceIds[n] = ids[v];
            dominatorIds[n] = d == virtualIndex ? VIRTUAL_ROOT : ids[d];
            retainedBytes[n] = retained[v];
            n++;
        }
        return new PersistRowData(instanceIds, dominatorIds, retainedBytes, n);
    }

    /**
     * {@link Measuring#s} adapter for SQL-throwing suppliers. Rethrows
     * {@link SQLException} as an unchecked {@link RuntimeException} which
     * the outer {@link #build} call already routes through.
     */
    private static <T> Elapsed<T> measure(SqlSupplier<T> body) {
        return Measuring.s(() -> {
            try {
                return body.get();
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @FunctionalInterface
    private interface SqlSupplier<T> {
        T get() throws SQLException;
    }

    private static long edgeCount(int[][] succ) {
        long total = 0;
        for (int[] row : succ) {
            total += row.length;
        }
        return total;
    }

    /**
     * Iterative DFS from {@code root}: assigns preorder numbers, captures the
     * spanning-tree parent for each reachable node, and reports the reachable
     * count. Unreachable nodes are left with {@code dfsNum[v] = -1} and
     * {@code parent[v] = -1}.
     */
    private static DfsData computeDfsData(int[][] succ, int root, int totalNodes) {
        int[] preorder = new int[totalNodes];
        int[] dfsNum = new int[totalNodes];
        int[] parent = new int[totalNodes];
        Arrays.fill(dfsNum, -1);
        Arrays.fill(parent, -1);

        int[] stack = new int[totalNodes];
        int[] childIdx = new int[totalNodes];
        int top = -1;

        dfsNum[root] = 0;
        preorder[0] = root;
        parent[root] = root; // self-parent sentinel so the semi-NCA walk terminates cleanly
        int n = 1;
        stack[++top] = root;

        while (top >= 0) {
            int u = stack[top];
            int idx = childIdx[u];
            int[] children = succ[u];
            if (idx < children.length) {
                int v = children[idx];
                childIdx[u] = idx + 1;
                if (dfsNum[v] < 0) {
                    dfsNum[v] = n;
                    preorder[n] = v;
                    parent[v] = u;
                    n++;
                    stack[++top] = v;
                }
            } else {
                top--;
            }
        }
        return new DfsData(preorder, dfsNum, parent, n);
    }

    /**
     * Lengauer-Tarjan-style semi-NCA dominators (Georgiadis &amp; Tarjan 2004).
     *
     * <p>Cooper-Harvey-Kennedy is great for control-flow graphs (a few back-edges,
     * tree-like shape) but pathological on heap reference graphs which are
     * heavily cyclic — measured 375 fixed-point iterations on a 7.6 M-instance
     * dump, ~70 s total. Semi-NCA needs no fixed point: a single DFS, a single
     * reverse-preorder pass to compute semi-dominators via a path-compressed
     * link-eval forest, and a single preorder pass to derive immediate
     * dominators by walking up the DFS spanning tree to the nearest ancestor
     * with the right depth. Total complexity is O(N · α(N)) — essentially
     * linear in N + E.
     *
     * @return {@code idom[v]} = immediate dominator of {@code v}, with
     *         {@code idom[root] = root}. Unreachable nodes have {@code -1}.
     */
    private static int[] computeDominatorsSemiNCA(int[][] pred, DfsData dfs, int totalNodes) {
        int[] preorder = dfs.preorder();
        int[] dfsNum = dfs.dfsNum();
        int[] parent = dfs.parent();
        int reachable = dfs.reachable();
        int root = preorder[0];

        int[] sdom = new int[totalNodes];
        int[] ancestor = new int[totalNodes];
        int[] best = new int[totalNodes];
        int[] idom = new int[totalNodes];
        int[] pathStack = new int[totalNodes];

        Arrays.fill(ancestor, -1);
        Arrays.fill(idom, -1);

        // sdom[v] starts at v's own DFS number — the trivial 0-length path.
        for (int v = 0; v < totalNodes; v++) {
            sdom[v] = dfsNum[v];
            best[v] = v;
        }

        // Reverse-preorder pass: for each v, walk predecessors, refine semi-dominator.
        for (int i = reachable - 1; i > 0; i--) {
            int v = preorder[i];
            for (int u : pred[v]) {
                if (dfsNum[u] < 0) {
                    continue; // unreachable predecessor
                }
                int t = eval(u, ancestor, best, sdom, pathStack);
                if (sdom[t] < sdom[v]) {
                    sdom[v] = sdom[t];
                }
            }
            // Link v into the forest under its DFS-tree parent.
            ancestor[v] = parent[v];
        }

        // semi-NCA: idom[v] = the nearest ancestor in the DFS tree whose DFS
        // number does not exceed sdom[v]. Processing in preorder ensures every
        // ancestor's idom is already finalised by the time we walk up.
        idom[root] = root;
        for (int i = 1; i < reachable; i++) {
            int v = preorder[i];
            int p = parent[v];
            while (dfsNum[p] > sdom[v]) {
                p = idom[p];
            }
            idom[v] = p;
        }
        return idom;
    }

    /**
     * EVAL with iterative path compression on the link-eval forest. Returns the
     * vertex on {@code v}'s forest path with the minimum semi-dominator.
     *
     * <p>The recursive textbook form blows the JVM stack on deep heaps
     * (millions of instances → forest depth in the thousands). The iterative
     * form below uses a pre-allocated {@code pathStack} scratch buffer instead.
     */
    private static int eval(int v, int[] ancestor, int[] best, int[] sdom, int[] pathStack) {
        if (ancestor[v] == -1) {
            return v;
        }
        int top = 0;
        pathStack[top++] = v;
        int curr = v;
        while (ancestor[curr] != -1) {
            curr = ancestor[curr];
            pathStack[top++] = curr;
        }
        // pathStack[top-1] is the forest root (its ancestor is -1).
        int forestRoot = pathStack[top - 1];
        // Compress: every node on the path now points directly at the forest
        // root, and best[] absorbs the minimum-semi node from its sub-path.
        for (int i = top - 2; i >= 0; i--) {
            int u = pathStack[i];
            int origAncestor = pathStack[i + 1];
            if (sdom[best[origAncestor]] < sdom[best[u]]) {
                best[u] = best[origAncestor];
            }
            ancestor[u] = forestRoot;
        }
        return best[v];
    }

    /**
     * Bottom-up retained-size accumulation over the dominator tree. Reverse
     * DFS-preorder is a valid topological order for the dominator tree because
     * {@code idom[v]} is always an ancestor of {@code v} in the DFS spanning
     * tree, so all of v's dominator-tree descendants are processed before v.
     */
    private static long[] computeRetained(
            int[] idom, DfsData dfs, long[] shallow, int virtualIndex, int totalNodes) {
        int[] preorder = dfs.preorder();
        int reachable = dfs.reachable();
        long[] retained = new long[totalNodes];

        for (int i = 0; i < reachable; i++) {
            int v = preorder[i];
            if (v != virtualIndex) {
                retained[v] = shallow[v];
            }
        }
        for (int i = reachable - 1; i >= 1; i--) {
            int v = preorder[i];
            int d = idom[v];
            if (d != -1 && d != virtualIndex) {
                retained[d] += retained[v];
            }
        }
        return retained;
    }

    /**
     * Sorted instance ids paired with their shallow sizes, indices aligned —
     * {@code ids[i]} has size {@code shallow[i]}. Loaded by one ORDER BY
     * instance_id scan so the downstream code can use {@code shallow[index]}
     * directly without a second SQL pass or per-row lookup.
     */
    private record InstanceMeta(long[] ids, long[] shallow) {
    }

    private static InstanceMeta loadInstanceMeta(Connection conn) throws SQLException {
        int count;
        try (Statement s = conn.createStatement();
             ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM instance")) {
            count = rs.next() ? rs.getInt(1) : 0;
        }
        long[] ids = new long[count];
        long[] shallow = new long[count];
        int i = 0;
        try (Statement s = conn.createStatement();
             ResultSet rs = s.executeQuery(
                     "SELECT instance_id, shallow_size FROM instance ORDER BY instance_id")) {
            while (rs.next() && i < count) {
                ids[i] = rs.getLong(1);
                shallow[i] = rs.getInt(2);
                i++;
            }
        }
        if (i != count) {
            // Defensive: COUNT(*) and the scan returned different row counts
            // (concurrent write, or count overflow). Truncate to actually read.
            long[] idsTrimmed = new long[i];
            long[] shallowTrimmed = new long[i];
            System.arraycopy(ids, 0, idsTrimmed, 0, i);
            System.arraycopy(shallow, 0, shallowTrimmed, 0, i);
            return new InstanceMeta(idsTrimmed, shallowTrimmed);
        }
        return new InstanceMeta(ids, shallow);
    }

    private static final String OUTBOUND_REF_SQL =
            "SELECT source_id, target_id FROM outbound_ref";
    private static final String GC_ROOT_DISTINCT_SQL =
            "SELECT DISTINCT instance_id FROM gc_root";

    /**
     * Builds the {@code int[][]} adjacency list in two passes: count out-degrees,
     * then fill exact-sized rows. Avoids the per-edge autoboxing cost of the
     * earlier {@code List<List<Integer>>} variant — every edge on a 20-50 M
     * edge graph used to box both source and target into {@code Integer}.
     */
    private static int[][] loadSuccessors(
            Connection conn, long[] ids, int virtualIndex) throws SQLException {
        int n = ids.length + 1;
        int[] outDeg = new int[n];

        // Pass 1a: count outbound_ref edges by source.
        try (Statement s = conn.createStatement();
             ResultSet rs = s.executeQuery(OUTBOUND_REF_SQL)) {
            while (rs.next()) {
                int src = indexOf(ids, rs.getLong(1));
                int dst = indexOf(ids, rs.getLong(2));
                if (src >= 0 && dst >= 0) {
                    outDeg[src]++;
                }
            }
        }

        // Pass 1b: count gc_root edges from virtual root.
        try (Statement s = conn.createStatement();
             ResultSet rs = s.executeQuery(GC_ROOT_DISTINCT_SQL)) {
            while (rs.next()) {
                int dst = indexOf(ids, rs.getLong(1));
                if (dst >= 0) {
                    outDeg[virtualIndex]++;
                }
            }
        }

        int[][] out = new int[n][];
        for (int i = 0; i < n; i++) {
            out[i] = new int[outDeg[i]];
        }

        // Pass 2: fill edges using cursor[] to track per-row write position.
        int[] cursor = new int[n];

        try (Statement s = conn.createStatement();
             ResultSet rs = s.executeQuery(OUTBOUND_REF_SQL)) {
            while (rs.next()) {
                int src = indexOf(ids, rs.getLong(1));
                int dst = indexOf(ids, rs.getLong(2));
                if (src >= 0 && dst >= 0) {
                    out[src][cursor[src]++] = dst;
                }
            }
        }

        try (Statement s = conn.createStatement();
             ResultSet rs = s.executeQuery(GC_ROOT_DISTINCT_SQL)) {
            while (rs.next()) {
                int dst = indexOf(ids, rs.getLong(1));
                if (dst >= 0) {
                    out[virtualIndex][cursor[virtualIndex]++] = dst;
                }
            }
        }

        return out;
    }

    /**
     * Binary-search lookup over the sorted {@code ids[]} array. Returns the
     * index of {@code id} or {@code -1} when absent. Replaces the per-call
     * autoboxing cost of {@code Map<Long, Integer>.get(...)} that this method
     * historically used; for a 7.6 M-instance heap the cumulative win across
     * the {@code outbound_ref} scan is in the multiple-seconds range.
     */
    private static int indexOf(long[] sortedIds, long id) {
        int idx = Arrays.binarySearch(sortedIds, id);
        return idx >= 0 ? idx : -1;
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

}
