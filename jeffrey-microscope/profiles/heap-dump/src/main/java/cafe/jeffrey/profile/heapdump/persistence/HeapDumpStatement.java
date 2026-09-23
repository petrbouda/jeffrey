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

/**
 * Module-local statement labels for heap-dump DB operations. Tagged onto every
 * JFR JDBC event so dashboards can attribute traffic back to a specific
 * operation. Kept local to heap-dump so the global {@code StatementLabel} enum
 * doesn't grow each time a new heap-dump query is added.
 */
public enum HeapDumpStatement {
    // ---- schema + index lifecycle ----
    APPLY_SCHEMA,
    DROP_INDEXES,
    CREATE_INDEXES,
    CHECKPOINT,
    BEGIN_TRANSACTION,
    COMMIT_TRANSACTION,
    ROLLBACK_TRANSACTION,
    WAL_AUTOCHECKPOINT_PRAGMA,
    PRESERVE_INSERTION_ORDER_PRAGMA,
    READ_ONLY_PRAGMA,

    // ---- appender writes (HprofIndex Pass 1/2A/2B + content phase) ----
    APPEND_STRING,
    APPEND_CLASS,
    APPEND_CLASS_INSTANCE_FIELD,
    APPEND_INSTANCE,
    APPEND_GC_ROOT,
    APPEND_OUTBOUND_REF,
    APPEND_STACK_FRAME,
    APPEND_STACK_TRACE_FRAME,
    APPEND_DUMP_METADATA,
    APPEND_PARSE_WARNING,
    APPEND_STRING_CONTENT,
    APPEND_CLASS_CHAIN_OOP,

    // ---- DominatorTreeBuilder appenders ----
    APPEND_DOMINATOR,
    APPEND_RETAINED_SIZE,

    // ---- DominatorTreeBuilder id-index temp table (load_successors hash-join) ----
    BUILD_ID_INDEX,
    DROP_ID_INDEX,
    COUNT_OUTBOUND_DEGREES,
    JOIN_OUTBOUND_REFS,
    JOIN_GC_ROOTS,

    // ---- parquet staging bulk-load (parallel walks + parallel persist) ----
    BULK_LOAD_INSTANCE,
    BULK_LOAD_GC_ROOT,
    BULK_LOAD_OUTBOUND_REF,
    BULK_LOAD_STRING_CONTENT,
    BULK_LOAD_DOMINATOR,
    BULK_LOAD_RETAINED_SIZE,

    // ---- corrections + temp tables ----
    CREATE_TEMP_CLASS_CHAIN_OOP,
    DROP_TEMP_CLASS_CHAIN_OOP,
    UPDATE_INSTANCE_SHALLOW_SIZE_OOPS,
    UPDATE_INSTANCE_SHALLOW_SIZE_ALIGN,

    // ---- dominator reset ----
    DELETE_DOMINATOR,
    DELETE_RETAINED_SIZE,

    // ---- read-side (DuckDbHeapView) ----
    READ_DUMP_METADATA,
    LIST_CLASSES,
    FIND_CLASS_BY_ID,
    FIND_CLASSES_BY_NAME,
    FIND_INSTANCE_BY_ID,
    STREAM_INSTANCES_BY_CLASS,
    INSTANCE_COUNT_BY_CLASS,
    TOTAL_INSTANCE_COUNT,
    TOTAL_SHALLOW_SIZE,
    CLASS_COUNT,
    GC_ROOTS,
    IS_GC_ROOT,
    GC_ROOT_COUNT,
    OUTBOUND_REFS,
    INBOUND_REFS,
    OUTBOUND_REF_COUNT,
    DOMINATOR_OF,
    RETAINED_SIZE_OF,
    HAS_DOMINATOR_TREE,
    INSTANCE_FIELDS_BY_CLASS,
    INSTANCE_FIELDS_WITH_CHAIN,
    FIND_STRING_BY_ID,
    FIND_STRING_CONTENT_BY_INSTANCE,
    CLASS_HISTOGRAM,

    // ---- analyzers — one entry per distinct analyzer query ----
    LEAK_SUSPECTS_DOMINATOR_RANK,
    BIGGEST_COLLECTIONS_BY_SIZE,
    CLASSLOADER_DUPLICATE_CLASSES,
    CLASSLOADER_LEAK_CHAINS,
    CLASSLOADER_SUMMARY,
    THREAD_INSTANCES,
    THREAD_STACK_FRAMES,
    STRING_DEDUP_OPPORTUNITIES,
    STRING_ALREADY_DEDUPED,
    STRING_TOTALS,
    CONSUMER_REPORT_BY_PACKAGE,
    PATH_TO_GC_ROOT_BFS,
    PATH_TO_GC_ROOT_ROOT_KIND,
    INSTANCE_DETAIL_LOOKUP,
    INSTANCE_TREE_REFERRERS,
    INSTANCE_TREE_REACHABLES,
    DOMINATOR_TREE_CHILDREN,
    DOMINATOR_TREE_ROOT_CHILDREN,
    CLASS_INSTANCE_BROWSER,
    GC_ROOT_SUMMARY,
    HEAP_SUMMARY_AGGREGATE;

    public String label() {
        return name().toLowerCase();
    }
}
