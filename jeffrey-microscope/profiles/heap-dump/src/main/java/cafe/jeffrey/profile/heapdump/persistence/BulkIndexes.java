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
package cafe.jeffrey.profile.heapdump.persistence;

import cafe.jeffrey.jfr.events.trace.Tracer;
import cafe.jeffrey.profile.heapdump.parser.FutureJoin;
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
 * Builds a set of indexes over populated tables, one worker connection per table.
 *
 * <p>Bulk index creation over a full table is DuckDB's fast path -- it sorts the column once and
 * walks -- so every builder in this module drops its indexes before loading rows and recreates
 * them here afterwards. Same-table indexes share a write lock on the table's ART tree and run
 * sequentially on one worker; different tables run on their own connections to the same database
 * file in parallel virtual threads. The phase therefore costs what its slowest table costs, and
 * nothing more, which is why the groups are worth reading with that in mind.
 *
 * <p>Every statement is issued through {@link HeapDumpDatabaseClient} so it emits its own JFR
 * event, on the parallel path as much as the sequential one. The span in progress is bound to the
 * thread and a plain executor does not inherit it, so each task is wrapped with {@link Tracer#fork} to
 * re-establish the phase's span on the thread that actually runs the DDL. Without it the phase is
 * a single opaque bar that cannot say which index its time went to.
 */
public final class BulkIndexes {

    /**
     * One table's indexes, and the table they belong to.
     *
     * @param table the target table, which also names the worker's span -- a small fixed set of
     *              values, so the names stay the low-cardinality set JFR's string pool wants
     * @param ddl   the statements, issued in order on a single worker because same-table indexes
     *              share a write lock on that table's ART tree
     */
    public record IndexGroup(String table, List<String> ddl) {

        public IndexGroup {
            if (table == null || table.isBlank()) {
                throw new IllegalArgumentException("table must not be blank");
            }
            if (ddl == null || ddl.isEmpty()) {
                throw new IllegalArgumentException("ddl must not be empty: table=" + table);
            }
            ddl = List.copyOf(ddl);
        }
    }

    /** Prefix of a worker's span name; the table follows, giving one stable name per group. */
    private static final String CREATE_INDEXES_SPAN_PREFIX = "create_indexes_";

    private static final String JDBC_URL_PREFIX = "jdbc:duckdb:";

    private BulkIndexes() {
    }

    /**
     * Creates every index in {@code groups}, parallelised across tables via up to
     * {@code requestedWorkers} virtual-thread connections to {@code dbPath}. Clamped at 1 and at
     * the number of groups so the worker count never exceeds the available parallel work.
     */
    public static void createAll(
            HeapDumpDatabaseClient client, Path dbPath, List<IndexGroup> groups, int requestedWorkers) {
        int n = Math.max(1, Math.min(requestedWorkers, groups.size()));
        if (n == 1) {
            // The caller's client already runs on the phase's own thread, so its events land under
            // the phase's span with nothing to re-establish.
            for (IndexGroup group : groups) {
                createGroup(client, group);
            }
            return;
        }

        String url = JDBC_URL_PREFIX + dbPath.toAbsolutePath();
        List<Future<?>> futures = new ArrayList<>(groups.size());
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (IndexGroup group : groups) {
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
