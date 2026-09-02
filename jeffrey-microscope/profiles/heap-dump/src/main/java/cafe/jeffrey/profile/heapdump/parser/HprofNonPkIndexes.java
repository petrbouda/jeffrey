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

import cafe.jeffrey.jfr.events.trace.Tracer;
import cafe.jeffrey.profile.heapdump.persistence.HeapDumpDatabaseClient;
import cafe.jeffrey.profile.heapdump.persistence.HeapDumpStatement;
import cafe.jeffrey.shared.persistence.GroupLabel;
import org.duckdb.DuckDBConnection;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Non-PK index management for the heap-dump index DB.
 *
 * <p>The index-build pipeline drops every non-PK index up front so per-row
 * inserts skip ART-tree updates, then recreates them in bulk once all rows are
 * present. Bulk index creation over a fully populated table is dramatically
 * faster than per-row insertion into an existing index — DuckDB sorts the
 * source column once and walks, rather than 30 M individual ART-tree inserts.
 * This is also why {@code instance} and {@code string_content} carry no primary
 * key: a primary key is an ART index the bulk load would have to maintain row by
 * row, so the id lookup each table needs is an ordinary index managed here.
 *
 * <p>Same-table indexes share a write lock on the table's ART tree, so they
 * run sequentially on one worker; different-table groups run on their own
 * worker connections to the same {@code .idx.duckdb} file in parallel virtual
 * threads.
 *
 * <p>Every statement is issued through {@link HeapDumpDatabaseClient} so it emits its own JFR event,
 * on the parallel path as much as the sequential one. The worker connections make that take a little
 * arranging — a span lives in a {@code ScopedValue} and a plain executor does not inherit one, so
 * each task is wrapped with {@link Tracer#fork} to re-establish the phase's span on the thread that
 * actually runs the DDL. Without it this phase is a single opaque bar: it was the longest sub-phase
 * of the whole index build and the only one that could not say which index its time went to.
 */
public final class HprofNonPkIndexes {

    private static final String[] DROP_DDL = {
            "DROP INDEX IF EXISTS idx_outbound_source",
            "DROP INDEX IF EXISTS idx_outbound_target",
            "DROP INDEX IF EXISTS idx_instance_id",
            "DROP INDEX IF EXISTS idx_instance_class",
            "DROP INDEX IF EXISTS idx_string_content_instance",
            "DROP INDEX IF EXISTS idx_gc_root_instance",
            "DROP INDEX IF EXISTS idx_class_name",
            "DROP INDEX IF EXISTS idx_class_super",
            "DROP INDEX IF EXISTS idx_class_is_array",
            "DROP INDEX IF EXISTS idx_stack_trace_frame_thread"
    };

    /**
     * One table's indexes, and the table they belong to.
     *
     * @param table the target table, which also names the worker's span — six fixed values, so the
     *              names stay the low-cardinality set JFR's string pool wants
     * @param ddl   the statements, issued in order on a single worker because same-table indexes
     *              share a write lock on that table's ART tree
     */
    private record IndexGroup(String table, List<String> ddl) {
    }

    /**
     * The non-PK indexes, grouped by target table. Different-table groups run on their own workers
     * in parallel.
     *
     * <p>A {@link List} rather than a map: the groups are read in order and never looked up by name,
     * and the previous {@code Map.copyOf} of a {@code LinkedHashMap} was documented as order-preserving
     * when {@code Map.copyOf} explicitly does not promise iteration order at all.
     */
    private static final List<IndexGroup> CREATE_DDL_BY_TABLE = List.of(
            new IndexGroup("outbound_ref", List.of(
                    "CREATE INDEX IF NOT EXISTS idx_outbound_source ON outbound_ref(source_id)",
                    "CREATE INDEX IF NOT EXISTS idx_outbound_target ON outbound_ref(target_id)")),
            new IndexGroup("instance", List.of(
                    "CREATE INDEX IF NOT EXISTS idx_instance_id ON instance(instance_id)",
                    "CREATE INDEX IF NOT EXISTS idx_instance_class ON instance(class_id)")),
            new IndexGroup("string_content", List.of(
                    "CREATE INDEX IF NOT EXISTS idx_string_content_instance ON string_content(instance_id)")),
            new IndexGroup("gc_root", List.of(
                    "CREATE INDEX IF NOT EXISTS idx_gc_root_instance ON gc_root(instance_id)")),
            new IndexGroup("class", List.of(
                    "CREATE INDEX IF NOT EXISTS idx_class_name ON class(name)",
                    "CREATE INDEX IF NOT EXISTS idx_class_super ON class(super_class_id)",
                    "CREATE INDEX IF NOT EXISTS idx_class_is_array ON class(is_array)")),
            new IndexGroup("stack_trace_frame", List.of(
                    "CREATE INDEX IF NOT EXISTS idx_stack_trace_frame_thread ON stack_trace_frame(thread_serial)")));

    /** Prefix of a worker's span name; the table follows, giving one stable name per group. */
    private static final String CREATE_INDEXES_SPAN_PREFIX = "create_indexes_";

    private HprofNonPkIndexes() {
    }

    /**
     * Drops every non-PK index DuckDB maintains for this heap-dump index DB.
     * Called before the bulk-load phases so per-row writes don't incur
     * per-insert ART-tree updates.
     */
    public static void dropAll(HeapDumpDatabaseClient client) {
        for (String ddl : DROP_DDL) {
            client.execute(HeapDumpStatement.DROP_INDEXES, ddl);
        }
    }

    /**
     * Recreates the indexes dropped by {@link #dropAll}. Parallelised across
     * tables via {@code requestedWorkers} virtual-thread connections to
     * {@code indexDbPath}; clamped at 1 and at the number of table-groups so
     * the worker count never exceeds the available parallel work.
     */
    public static void createAll(
            HeapDumpDatabaseClient client, Path indexDbPath, int requestedWorkers) {
        int n = Math.max(1, Math.min(requestedWorkers, CREATE_DDL_BY_TABLE.size()));
        if (n == 1) {
            // The caller's client already runs on the phase's own thread, so its events land under
            // the phase's span with nothing to re-establish.
            for (IndexGroup group : CREATE_DDL_BY_TABLE) {
                createGroup(client, group);
            }
            return;
        }

        String url = "jdbc:duckdb:" + indexDbPath.toAbsolutePath();
        List<Future<?>> futures = new ArrayList<>(CREATE_DDL_BY_TABLE.size());
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (IndexGroup group : CREATE_DDL_BY_TABLE) {
                // fork() is called here, on the coordinator, because that is where the phase's span
                // is bound; the Runnable it returns re-opens that span on the worker.
                futures.add(executor.submit(
                        Tracer.fork(CREATE_INDEXES_SPAN_PREFIX + group.table(),
                                () -> createGroupOnOwnConnection(url, group))));
            }
        }
        for (Future<?> f : futures) {
            FutureJoin.unwrap(f);
        }
    }

    /**
     * Runs one group on a connection of its own, so the workers do not contend on the caller's.
     * The connection is wrapped in a {@link HeapDumpDatabaseClient} rather than used raw, which is
     * the whole difference between this phase reporting one bar and reporting a statement each.
     */
    private static void createGroupOnOwnConnection(String url, IndexGroup group) {
        try (Connection raw = DriverManager.getConnection(url);
             DuckDBConnection conn = raw.unwrap(DuckDBConnection.class)) {
            createGroup(new HeapDumpDatabaseClient(conn, GroupLabel.HEAP_DUMP_INDEX), group);
        } catch (SQLException e) {
            throw new RuntimeException(
                    "Heap-dump create-index failed: table=" + group.table() + ": " + e.getMessage(), e);
        }
    }

    private static void createGroup(HeapDumpDatabaseClient client, IndexGroup group) {
        for (String ddl : group.ddl()) {
            client.execute(HeapDumpStatement.CREATE_INDEXES, ddl);
        }
    }
}
