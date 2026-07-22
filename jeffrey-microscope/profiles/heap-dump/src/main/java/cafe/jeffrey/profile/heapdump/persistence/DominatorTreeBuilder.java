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
package cafe.jeffrey.profile.heapdump.persistence;

import cafe.jeffrey.profile.heapdump.model.SubPhaseTiming;
import cafe.jeffrey.shared.common.measure.Elapsed;
import cafe.jeffrey.shared.common.measure.Measuring;
import cafe.jeffrey.shared.persistence.GroupLabel;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.duckdb.DuckDBAppender;
import org.duckdb.DuckDBConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cafe.jeffrey.profile.heapdump.parser.FutureJoin;

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
     * Raises the WAL auto-checkpoint threshold so DuckDB doesn't flush WAL → main
     * file every 16 MB (the default) while we stream the dominator/retained-size
     * rows. Each auto-checkpoint stalls the appenders; for a bulk load they're
     * pure overhead because we issue an explicit {@code CHECKPOINT} at the end of
     * the build, before the read-only HeapView reopens. {@code 1TB} is DuckDB's
     * documented "effectively disabled" value.
     */
    private static final String PRAGMA_WAL_AUTOCHECKPOINT = "PRAGMA wal_autocheckpoint = '1TB'";

    /**
     * The dominator/retained-size bulk loads never rely on row order (reads are
     * keyed or explicitly ordered), so let DuckDB parallelise the parquet load
     * without the insertion-order bookkeeping.
     */
    private static final String PRAGMA_PRESERVE_INSERTION_ORDER = "SET preserve_insertion_order = false";

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

            HeapDumpDatabaseClient client = new HeapDumpDatabaseClient(conn, GroupLabel.HEAP_DUMP_INDEX);

            client.execute(HeapDumpStatement.WAL_AUTOCHECKPOINT_PRAGMA, PRAGMA_WAL_AUTOCHECKPOINT);
            client.execute(HeapDumpStatement.PRESERVE_INSERTION_ORDER_PRAGMA, PRAGMA_PRESERVE_INSERTION_ORDER);
            client.execute(HeapDumpStatement.DELETE_DOMINATOR, "DELETE FROM dominator");
            client.execute(HeapDumpStatement.DELETE_RETAINED_SIZE, "DELETE FROM retained_size");

            Path stagingDir = HeapDumpIndexPaths.stagingForIndex(indexDbPath);
            Elapsed<BuildResult> elapsed = Measuring.s(() -> {
                try {
                    return doBuild(client, stagingDir);
                } catch (SQLException | IOException e) {
                    throw new RuntimeException(e);
                }
            });
            BuildResult r = elapsed.entity();
            BuildResult timed = new BuildResult(
                    r.reachableInstances, r.rootEdges, r.iterations, elapsed.duration(), r.subPhases());

            client.execute(HeapDumpStatement.CHECKPOINT, "CHECKPOINT");
            LOG.debug("Dominator tree built: path={} reachable={} roots={} iterations={} duration_ms={}",
                    indexDbPath, timed.reachableInstances, timed.rootEdges, timed.iterations,
                    timed.buildTime.toMillis());
            return timed;
        }
    }

    private static BuildResult doBuild(HeapDumpDatabaseClient client, Path stagingDir)
            throws SQLException, IOException {
        Elapsed<InstanceMeta> metaE = measure(() -> loadInstanceMeta(client));
        InstanceMeta meta = metaE.entity();
        long[] ids = meta.ids();
        long[] shallow = meta.shallow();
        // Index N = virtual root. We allocate one extra slot at index ids.length.
        int virtualIndex = ids.length;
        int totalNodes = ids.length + 1;

        Elapsed<Csr> succE = measure(() -> loadSuccessors(client, ids, virtualIndex));
        Csr succ = succE.entity();

        Elapsed<Csr> predE = Measuring.s(() -> invert(succ, totalNodes));
        Csr pred = predE.entity();

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

        // Stage 2: parallel parquet staging + serial bulk-load. Each writer
        // owns an in-memory DuckDB instance (so the appender state is isolated
        // — sidesteps the JDBC single-connection appender contention that
        // forced the previous parallel-persist attempt to be reverted, see
        // a03fd5d8c). The two staging tables are flushed to parquet shards in
        // parallel virtual threads. The coordinator then merges each shard
        // back into the index DB via INSERT INTO ... SELECT * FROM
        // read_parquet(...).
        Duration persistDuration = Measuring.r(() -> {
            try {
                persistViaParquet(client, stagingDir, rows);
            } catch (IOException | SQLException e) {
                throw new RuntimeException(e);
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
                succ.edges().length,
                reachable);

        List<SubPhaseTiming> subPhases = List.of(
                new SubPhaseTiming("load_meta", metaE.duration().toMillis(), null),
                new SubPhaseTiming("load_successors", succE.duration().toMillis(), null),
                new SubPhaseTiming("invert", predE.duration().toMillis(), null),
                new SubPhaseTiming("dfs", dfsE.duration().toMillis(), null),
                new SubPhaseTiming("semi_nca", idomE.duration().toMillis(), "Lengauer-Tarjan"),
                new SubPhaseTiming("retained", retE.duration().toMillis(), null),
                new SubPhaseTiming("stage_rows", rowsE.duration().toMillis(), null),
                new SubPhaseTiming("persist", persistDuration.toMillis(), null));

        // BuildResult.iterations was a CHK leftover (number of fixed-point passes);
        // semi-NCA needs no fixed-point so we report 1 to keep the field honest.
        return new BuildResult(reachable, succ.outDegree(virtualIndex), 1L,
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

    private static final String DOMINATOR_TABLE = "dominator";

    private static final String RETAINED_SIZE_TABLE = "retained_size";

    private static final String DOMINATOR_STAGING_DDL =
            "instance_id BIGINT, dominator_id BIGINT";

    private static final String RETAINED_SIZE_STAGING_DDL =
            "instance_id BIGINT, bytes BIGINT";

    /**
     * Fans the dominator + retained-size row arrays out to two virtual-thread
     * workers, each writing its own parquet shard from a private in-memory
     * DuckDB, then bulk-loads both shards into the real index DB.
     */
    private static void persistViaParquet(
            HeapDumpDatabaseClient client, Path stagingDir, PersistRowData rows)
            throws IOException, SQLException {
        try (ParquetStaging staging = ParquetStaging.open(stagingDir)) {
            staging.prepareTable(DOMINATOR_TABLE);
            staging.prepareTable(RETAINED_SIZE_TABLE);

            Path dominatorOutput = staging.partFile(DOMINATOR_TABLE, 0);
            Path retainedOutput = staging.partFile(RETAINED_SIZE_TABLE, 0);

            // ParquetSink + its underlying DuckDBAppender are thread-confined —
            // each worker must open its own sink inside the worker thread.
            try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
                Future<?> domF = executor.submit(() -> {
                    writeDominatorShard(rows, dominatorOutput);
                    return null;
                });
                Future<?> retF = executor.submit(() -> {
                    writeRetainedSizeShard(rows, retainedOutput);
                    return null;
                });
                FutureJoin.unwrap(domF);
                FutureJoin.unwrap(retF);
            }

            // Bulk-load: two different target tables, no constraint overlap,
            // so the two INSERT statements could run concurrently on separate
            // connections. Keeping it sequential for now — the bulk-load is
            // already vector-pipelined inside DuckDB and the second statement
            // typically lands in <5 s. Revisit if profiling shows it as the
            // remaining bottleneck after this front lands.
            staging.bulkLoad(client, HeapDumpStatement.BULK_LOAD_DOMINATOR, DOMINATOR_TABLE);
            staging.bulkLoad(client, HeapDumpStatement.BULK_LOAD_RETAINED_SIZE, RETAINED_SIZE_TABLE);
        }
    }

    private static void writeDominatorShard(PersistRowData rows, Path outputPath) {
        try (ParquetSink sink = ParquetSink.open(
                Map.of(DOMINATOR_TABLE, DOMINATOR_STAGING_DDL),
                Map.of(DOMINATOR_TABLE, outputPath))) {
            DuckDBAppender app = sink.appender(DOMINATOR_TABLE);
            long[] instanceIds = rows.instanceIds();
            long[] dominatorIds = rows.dominatorIds();
            int count = rows.count();
            for (int i = 0; i < count; i++) {
                app.beginRow();
                app.append(instanceIds[i]);
                app.append(dominatorIds[i]);
                app.endRow();
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private static void writeRetainedSizeShard(PersistRowData rows, Path outputPath) {
        try (ParquetSink sink = ParquetSink.open(
                Map.of(RETAINED_SIZE_TABLE, RETAINED_SIZE_STAGING_DDL),
                Map.of(RETAINED_SIZE_TABLE, outputPath))) {
            DuckDBAppender app = sink.appender(RETAINED_SIZE_TABLE);
            long[] instanceIds = rows.instanceIds();
            long[] retainedBytes = rows.retainedBytes();
            int count = rows.count();
            for (int i = 0; i < count; i++) {
                app.beginRow();
                app.append(instanceIds[i]);
                app.append(retainedBytes[i]);
                app.endRow();
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
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

    /**
     * Compressed Sparse Row adjacency layout. {@code edges} is one flat int[]
     * holding every outbound destination; node {@code u}'s edges live at
     * indices {@code [offsets[u], offsets[u + 1])}. Replaces the per-node
     * {@code int[]} adjacency lists that allocated one int[] per graph node —
     * typically tens of millions of allocations on a real heap dump.
     *
     * <p>{@code offsets.length == nodes + 1}; the trailing slot acts as the
     * end-sentinel so {@code outDegree(u) = offsets[u + 1] - offsets[u]} stays
     * branch-free.
     */
    private record Csr(int[] offsets, int[] edges) {

        int outDegree(int u) {
            return offsets[u + 1] - offsets[u];
        }
    }

    /**
     * Iterative DFS from {@code root}: assigns preorder numbers, captures the
     * spanning-tree parent for each reachable node, and reports the reachable
     * count. Unreachable nodes are left with {@code dfsNum[v] = -1} and
     * {@code parent[v] = -1}.
     */
    private static DfsData computeDfsData(Csr succ, int root, int totalNodes) {
        int[] offsets = succ.offsets();
        int[] edges = succ.edges();
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
            int start = offsets[u];
            int end = offsets[u + 1];
            if (start + idx < end) {
                int v = edges[start + idx];
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
    private static int[] computeDominatorsSemiNCA(Csr pred, DfsData dfs, int totalNodes) {
        int[] predOffsets = pred.offsets();
        int[] predEdges = pred.edges();
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
            int startEdge = predOffsets[v];
            int endEdge = predOffsets[v + 1];
            for (int k = startEdge; k < endEdge; k++) {
                int u = predEdges[k];
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

    private static InstanceMeta loadInstanceMeta(HeapDumpDatabaseClient client) {
        int count = (int) client.queryLong(HeapDumpStatement.TOTAL_INSTANCE_COUNT, NODE_COUNT_SQL);
        long[] ids = new long[count];
        long[] shallow = new long[count];
        int[] iBox = {0};
        client.rawStream(HeapDumpStatement.STREAM_INSTANCES_BY_CLASS, NODE_META_SQL,
                rs -> {
                    while (rs.next() && iBox[0] < count) {
                        ids[iBox[0]] = rs.getLong(1);
                        shallow[iBox[0]] = rs.getLong(2);
                        iBox[0]++;
                    }
                    return iBox[0];
                });
        int i = iBox[0];
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

    /**
     * Builds a connection-local temp table mapping every {@code instance_id} to
     * its dense node index — the position in the in-memory {@code ids[]} array.
     * Both use {@code ORDER BY instance_id}, so {@code ROW_NUMBER() OVER
     * (ORDER BY instance_id) - 1} produces the same indices as the Java-side
     * {@code loadInstanceMeta} scan.
     *
     * <p>Materialising the mapping in DuckDB lets the edge-loading queries push
     * the translation into the engine's parallel hash join, replacing 87 M ×
     * two Java-side binary searches per pass with a single multi-threaded scan.
     */
    /**
     * The dominator graph's node set: every instance plus every class object.
     * Class objects are graph nodes so class-static references (outbound_ref
     * {@code field_kind = 2}, source = class id) and class-targeted GC roots
     * (e.g. ROOT_STICKY_CLASS) contribute edges — without them, objects
     * retained only through statics would be unreachable and get no retained
     * size. A class node's "shallow size" is its static-field footprint.
     */
    private static final String NODE_SET_SQL = """
            SELECT instance_id AS id, CAST(shallow_size AS BIGINT) AS shallow FROM instance
            UNION ALL
            SELECT class_id, CAST(static_fields_size AS BIGINT) FROM class
            WHERE class_id NOT IN (SELECT instance_id FROM instance)
            """;

    private static final String NODE_COUNT_SQL =
            "SELECT COUNT(*) FROM (" + NODE_SET_SQL + ")";

    private static final String NODE_META_SQL =
            "SELECT id, shallow FROM (" + NODE_SET_SQL + ") ORDER BY id";

    private static final String CREATE_ID_INDEX_SQL = """
            CREATE TEMP TABLE id_index AS
            SELECT id AS instance_id,
                   CAST(ROW_NUMBER() OVER (ORDER BY id) - 1 AS INTEGER) AS node_index
            FROM (
            """ + NODE_SET_SQL + ")";

    private static final String DROP_ID_INDEX_SQL = "DROP TABLE id_index";

    private static final String OUTBOUND_REFS_JOIN_SQL = """
            SELECT s.node_index, t.node_index
            FROM outbound_ref o
            JOIN id_index s ON o.source_id = s.instance_id
            JOIN id_index t ON o.target_id = t.instance_id
            """;

    private static final String GC_ROOTS_JOIN_SQL = """
            SELECT DISTINCT t.node_index
            FROM gc_root g
            JOIN id_index t ON g.instance_id = t.instance_id
            """;

    /**
     * Builds the CSR adjacency in two passes: count out-degrees → prefix-sum
     * them into {@code offsets}, then fill {@code edges} in source order using
     * a {@code cursor[]} that walks each node's slot in the flat array.
     *
     * <p>The per-edge {@code instance_id → node_index} translation runs in DuckDB
     * via a temp {@code id_index} table and INNER JOINs against {@code outbound_ref}
     * / {@code gc_root}. INNER JOIN naturally drops orphan references that point
     * to instances absent from the {@code instance} table, matching the old
     * {@code if (src >= 0 && dst >= 0)} filter.
     */
    private static Csr loadSuccessors(
            HeapDumpDatabaseClient client, long[] ids, int virtualIndex) {
        int n = ids.length + 1;
        int[] offsets = new int[n + 1];

        client.execute(HeapDumpStatement.BUILD_ID_INDEX, CREATE_ID_INDEX_SQL);
        try {
            // Pass 1a: count outbound_ref edges by source. Stash counts at
            // offsets[u + 1] so the prefix-sum below converts them in place.
            client.rawStream(HeapDumpStatement.JOIN_OUTBOUND_REFS, OUTBOUND_REFS_JOIN_SQL, rs -> {
                long rows = 0;
                while (rs.next()) {
                    int src = rs.getInt(1);
                    offsets[src + 1]++;
                    rows++;
                }
                return rows;
            });

            // Pass 1b: count gc_root edges from virtual root.
            client.rawStream(HeapDumpStatement.JOIN_GC_ROOTS, GC_ROOTS_JOIN_SQL, rs -> {
                long rows = 0;
                while (rs.next()) {
                    offsets[virtualIndex + 1]++;
                    rows++;
                }
                return rows;
            });

            // Prefix-sum the degrees in offsets[1..n] → CSR start offsets.
            // offsets[0] stays 0; offsets[n] becomes the total edge count.
            for (int i = 1; i <= n; i++) {
                offsets[i] += offsets[i - 1];
            }
            int totalEdges = offsets[n];
            int[] edges = new int[totalEdges];

            // Pass 2: fill edges using cursor[] to track per-source write position.
            // cursor[u] starts at offsets[u]; advances within [offsets[u], offsets[u + 1]).
            int[] cursor = new int[n];
            System.arraycopy(offsets, 0, cursor, 0, n);

            client.rawStream(HeapDumpStatement.JOIN_OUTBOUND_REFS, OUTBOUND_REFS_JOIN_SQL, rs -> {
                long rows = 0;
                while (rs.next()) {
                    int src = rs.getInt(1);
                    int dst = rs.getInt(2);
                    edges[cursor[src]++] = dst;
                    rows++;
                }
                return rows;
            });

            client.rawStream(HeapDumpStatement.JOIN_GC_ROOTS, GC_ROOTS_JOIN_SQL, rs -> {
                long rows = 0;
                while (rs.next()) {
                    int dst = rs.getInt(1);
                    edges[cursor[virtualIndex]++] = dst;
                    rows++;
                }
                return rows;
            });

            return new Csr(offsets, edges);
        } finally {
            client.execute(HeapDumpStatement.DROP_ID_INDEX, DROP_ID_INDEX_SQL);
        }
    }

    /**
     * Builds the inverted CSR via the same two-pass count-then-fill shape used
     * by {@link #loadSuccessors}: count in-degrees → prefix-sum into
     * {@code predOffsets}, then fill {@code predEdges} by walking the forward
     * CSR once more.
     */
    private static Csr invert(Csr succ, int n) {
        int[] succOffsets = succ.offsets();
        int[] succEdges = succ.edges();
        int[] predOffsets = new int[n + 1];

        for (int k = 0; k < succEdges.length; k++) {
            predOffsets[succEdges[k] + 1]++;
        }
        for (int i = 1; i <= n; i++) {
            predOffsets[i] += predOffsets[i - 1];
        }
        int[] predEdges = new int[succEdges.length];
        int[] cursor = new int[n];
        System.arraycopy(predOffsets, 0, cursor, 0, n);

        for (int u = 0; u < n; u++) {
            int startEdge = succOffsets[u];
            int endEdge = succOffsets[u + 1];
            for (int k = startEdge; k < endEdge; k++) {
                int v = succEdges[k];
                predEdges[cursor[v]++] = u;
            }
        }
        return new Csr(predOffsets, predEdges);
    }

}
