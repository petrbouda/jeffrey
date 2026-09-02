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

import cafe.jeffrey.profile.heapdump.persistence.BulkIndexes;
import cafe.jeffrey.profile.heapdump.persistence.BulkIndexes.IndexGroup;
import cafe.jeffrey.profile.heapdump.persistence.HeapDumpDatabaseClient;
import cafe.jeffrey.profile.heapdump.persistence.HeapDumpStatement;

import java.nio.file.Path;
import java.util.List;

/**
 * Non-PK index management for the heap-dump index DB.
 *
 * <p>The index-build pipeline drops every non-PK index up front so per-row
 * inserts skip ART-tree updates, then recreates them in bulk once all rows are
 * present through {@link BulkIndexes}. Bulk index creation over a fully
 * populated table is dramatically faster than per-row insertion into an
 * existing index — DuckDB sorts the source column once and walks, rather than
 * 30 M individual ART-tree inserts. This is also why {@code instance} and
 * {@code string_content} carry no primary key: a primary key is an ART index
 * the bulk load would have to maintain row by row, so the id lookup each table
 * needs is an ordinary index managed here.
 *
 * <p>{@code outbound_ref} is indexed on {@code target_id} only. Same-table
 * indexes build one after the other on a single worker, and on a 42 M-instance
 * heap the two edge-table indexes took 21 s and 15 s while every other group
 * finished within 13 s -- the whole phase cost what that one table cost. The
 * {@code source_id} lookups that the dropped index served read the edges of one
 * object, and Pass B writes an object's edges contiguously in instance order, so
 * DuckDB's per-row-group min/max statistics narrow such a lookup to a few row
 * groups without an ART tree. The joins never used the index at all.
 */
public final class HprofNonPkIndexes {

    private static final String[] DROP_DDL = {
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
     * The non-PK indexes, grouped by target table. Different-table groups run on their own workers
     * in parallel; the order here is the order the groups are submitted in.
     */
    private static final List<IndexGroup> CREATE_DDL_BY_TABLE = List.of(
            new IndexGroup("outbound_ref", List.of(
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
     * Recreates the indexes dropped by {@link #dropAll}, parallelised across
     * tables via up to {@code requestedWorkers} connections to {@code indexDbPath}.
     */
    public static void createAll(
            HeapDumpDatabaseClient client, Path indexDbPath, int requestedWorkers) {
        BulkIndexes.createAll(client, indexDbPath, CREATE_DDL_BY_TABLE, requestedWorkers);
    }
}
