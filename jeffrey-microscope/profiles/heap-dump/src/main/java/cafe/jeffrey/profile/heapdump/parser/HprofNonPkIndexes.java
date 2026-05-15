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

import cafe.jeffrey.profile.heapdump.persistence.HeapDumpDatabaseClient;
import cafe.jeffrey.profile.heapdump.persistence.HeapDumpStatement;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
 *
 * <p>Same-table indexes share a write lock on the table's ART tree, so they
 * run sequentially on one worker; different-table groups run on their own
 * worker connections to the same {@code .idx.duckdb} file in parallel virtual
 * threads.
 */
public final class HprofNonPkIndexes {

    private static final String[] DROP_DDL = {
            "DROP INDEX IF EXISTS idx_outbound_source",
            "DROP INDEX IF EXISTS idx_outbound_target",
            "DROP INDEX IF EXISTS idx_instance_class",
            "DROP INDEX IF EXISTS idx_gc_root_instance",
            "DROP INDEX IF EXISTS idx_class_name",
            "DROP INDEX IF EXISTS idx_class_super",
            "DROP INDEX IF EXISTS idx_class_is_array",
            "DROP INDEX IF EXISTS idx_stack_trace_frame_thread"
    };

    /**
     * Non-PK indexes grouped by target table. Same-table indexes share a DuckDB
     * write lock on the table's ART tree, so they're issued sequentially on
     * one worker; different-table groups run on their own workers in parallel.
     * Iteration order is preserved via {@link LinkedHashMap}.
     */
    private static final Map<String, List<String>> CREATE_DDL_BY_TABLE;

    static {
        Map<String, List<String>> m = new LinkedHashMap<>();
        m.put("outbound_ref", List.of(
                "CREATE INDEX IF NOT EXISTS idx_outbound_source ON outbound_ref(source_id)",
                "CREATE INDEX IF NOT EXISTS idx_outbound_target ON outbound_ref(target_id)"));
        m.put("instance", List.of(
                "CREATE INDEX IF NOT EXISTS idx_instance_class ON instance(class_id)"));
        m.put("gc_root", List.of(
                "CREATE INDEX IF NOT EXISTS idx_gc_root_instance ON gc_root(instance_id)"));
        m.put("class", List.of(
                "CREATE INDEX IF NOT EXISTS idx_class_name ON class(name)",
                "CREATE INDEX IF NOT EXISTS idx_class_super ON class(super_class_id)",
                "CREATE INDEX IF NOT EXISTS idx_class_is_array ON class(is_array)"));
        m.put("stack_trace_frame", List.of(
                "CREATE INDEX IF NOT EXISTS idx_stack_trace_frame_thread ON stack_trace_frame(thread_serial)"));
        CREATE_DDL_BY_TABLE = Map.copyOf(m);
    }

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
        List<List<String>> groups = new ArrayList<>(CREATE_DDL_BY_TABLE.values());
        int n = Math.max(1, Math.min(requestedWorkers, groups.size()));
        if (n == 1) {
            for (List<String> group : groups) {
                for (String ddl : group) {
                    client.execute(HeapDumpStatement.CREATE_INDEXES, ddl);
                }
            }
            return;
        }

        String url = "jdbc:duckdb:" + indexDbPath.toAbsolutePath();
        List<Future<?>> futures = new ArrayList<>(groups.size());
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (List<String> group : groups) {
                futures.add(executor.submit(() -> {
                    try (Connection conn = DriverManager.getConnection(url);
                         Statement s = conn.createStatement()) {
                        for (String ddl : group) {
                            s.execute(ddl);
                        }
                    } catch (SQLException e) {
                        throw new RuntimeException(
                                "Heap-dump create-index failed: ddl=" + group + ": " + e.getMessage(), e);
                    }
                    return null;
                }));
            }
        }
        for (Future<?> f : futures) {
            FutureJoin.unwrap(f);
        }
    }
}
