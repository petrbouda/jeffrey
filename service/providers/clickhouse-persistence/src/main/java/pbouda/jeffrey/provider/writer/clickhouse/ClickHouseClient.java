/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
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

package pbouda.jeffrey.provider.writer.clickhouse;

import com.clickhouse.client.api.Client;
import com.clickhouse.client.api.insert.InsertResponse;
import com.clickhouse.client.api.metadata.TableSchema;
import com.clickhouse.data.ClickHouseColumn;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pbouda.jeffrey.provider.writer.clickhouse.model.*;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public class ClickHouseClient {

    private static final Logger LOG = LoggerFactory.getLogger(ClickHouseClient.class);

    private static final TableSchema FRAMES_SCHEMA = createFramesSchema();
    private static final TableSchema STACKTRACES_SCHEMA = createStacktracesSchema();
    private static final TableSchema STACKTRACE_TAGS_SCHEMA = createStacktraceTagsSchema();
    private static final TableSchema THREADS_SCHEMA = createThreadsSchema();
    private static final TableSchema EVENTS_SCHEMA = createEventsSchema();
    private static final TableSchema EVENT_TYPES_SCHEMA = createEventTypesSchema();

    private final Client client;

    public ClickHouseClient(String clickHouseUri) {
        this.client = new Client.Builder()
                .addEndpoint(clickHouseUri)
                .setDefaultDatabase("default")
                .build();

        // Register model classes with their table schemas
        client.register(ClickHouseFrame.class, FRAMES_SCHEMA);
        client.register(ClickHouseStacktrace.class, STACKTRACES_SCHEMA);
        client.register(ClickHouseStacktraceTag.class, STACKTRACE_TAGS_SCHEMA);
        client.register(ClickHouseThread.class, THREADS_SCHEMA);
        client.register(ClickHouseEventType.class, EVENT_TYPES_SCHEMA);
        client.register(ClickHouseEvent.class, EVENTS_SCHEMA);
    }

    public <T> CompletableFuture<InsertResponse> batchInsert(String table, List<T> batch) {
        if (batch.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }

        return client.insert(table, batch);
    }

    /**
     * It starts deduplication of the given tables
     */
    public void optimizeTables() {
        try {
            // Optimize critical tables for better query performance
            client.execute("OPTIMIZE TABLE frames FINAL").get();
            client.execute("OPTIMIZE TABLE stacktraces FINAL").get();
            client.execute("OPTIMIZE TABLE events FINAL").get();
        } catch (Exception e) {
            LOG.error("Warning: Table optimization failed: {}", e.getMessage(), e);
        }
    }

    /**
     * Close the ClickHouse client and cleanup resources.
     */
    public void close() {
        try {
            client.close();
        } catch (Exception e) {
            throw new RuntimeException("Failed to close ClickHouse client", e);
        }
    }

    // Schema creation methods for each table
    private static TableSchema createFramesSchema() {
        return new TableSchema(List.of(
                ClickHouseColumn.of("frame_hash", "UInt64"),
                ClickHouseColumn.of("class_name", "LowCardinality(String)"),
                ClickHouseColumn.of("method_name", "LowCardinality(String)"),
                ClickHouseColumn.of("frame_type", "LowCardinality(String)"),
                ClickHouseColumn.of("line_number", "UInt32"),
                ClickHouseColumn.of("bytecode_index", "UInt32")
        ));
    }

    private static TableSchema createStacktracesSchema() {
        return new TableSchema(List.of(
                ClickHouseColumn.of("profile_id", "String"),
                ClickHouseColumn.of("stack_hash", "UInt64"),
                ClickHouseColumn.of("frame_hashes", "Array(UInt64)"),
                ClickHouseColumn.of("tag_ids", "Array(UInt32)")
        ));
    }

    private static TableSchema createStacktraceTagsSchema() {
        return new TableSchema(List.of(
                ClickHouseColumn.of("profile_id", "String"),
                ClickHouseColumn.of("stack_hash", "UInt64"),
                ClickHouseColumn.of("tag_id", "UInt32")
        ));
    }

    private static TableSchema createThreadsSchema() {
        return new TableSchema(List.of(
                ClickHouseColumn.of("profile_id", "String"),
                ClickHouseColumn.of("thread_hash", "UInt32"),
                ClickHouseColumn.of("name", "String"),
                ClickHouseColumn.of("os_id", "Nullable(UInt32)"),
                ClickHouseColumn.of("java_id", "Nullable(UInt32)"),
                ClickHouseColumn.of("is_virtual", "Bool")
        ));
    }

    private static TableSchema createEventTypesSchema() {
        return new TableSchema(List.of(
                ClickHouseColumn.of("profile_id", "String"),
                ClickHouseColumn.of("name", "LowCardinality(String)"),
                ClickHouseColumn.of("label", "String"),
                ClickHouseColumn.of("type_id", "Nullable(UInt32)"),
                ClickHouseColumn.of("description", "Nullable(String)"),
                ClickHouseColumn.of("categories", "Nullable(String)"),
                ClickHouseColumn.of("source", "LowCardinality(String)"),
                ClickHouseColumn.of("subtype", "Nullable(LowCardinality(String))"),
                ClickHouseColumn.of("samples", "UInt64"),
                ClickHouseColumn.of("weight", "Nullable(UInt64)"),
                ClickHouseColumn.of("has_stacktrace", "Bool"),
                ClickHouseColumn.of("calculated", "Bool"),
                ClickHouseColumn.of("extras", "Nullable(String)"),
                ClickHouseColumn.of("settings", "Nullable(String)"),
                ClickHouseColumn.of("columns", "Nullable(String)")
        ));
    }

    private static TableSchema createEventsSchema() {
        return new TableSchema(List.of(
                ClickHouseColumn.of("profile_id", "String"),
                ClickHouseColumn.of("event_type", "LowCardinality(String)"),
                ClickHouseColumn.of("start_timestamp", "DateTime64(9)"),
                ClickHouseColumn.of("start_timestamp_from_beginning", "UInt64"),
                ClickHouseColumn.of("end_timestamp", "Nullable(DateTime64(9))"),
                ClickHouseColumn.of("end_timestamp_from_beginning", "Nullable(UInt64)"),
                ClickHouseColumn.of("duration", "Nullable(UInt64)"),
                ClickHouseColumn.of("samples", "UInt32"),
                ClickHouseColumn.of("weight", "Nullable(UInt64)"),
                ClickHouseColumn.of("weight_entity", "LowCardinality(String)"),
                ClickHouseColumn.of("stacktrace_hash", "Nullable(UInt64)"),
                ClickHouseColumn.of("thread_hash", "Nullable(UInt64)"),
                ClickHouseColumn.of("fields", "String")
        ));
    }
}
