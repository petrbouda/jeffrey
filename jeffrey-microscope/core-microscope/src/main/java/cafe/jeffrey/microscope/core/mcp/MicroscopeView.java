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

package cafe.jeffrey.microscope.core.mcp;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Every page inside a profile that an MCP answer links to, by the sub-path the Microscope router
 * serves it at under {@code /profiles/{profileId}/}. A link is built from a constant, never from a
 * typed string, so a path the frontend does not serve cannot be linked to by accident: the
 * enforcement test holds every constant to the router's pinned manifest ({@code profile-routes.json}).
 * <p>
 * {@link #offeredByViewLink()} are the views {@code profiles_viewLink} hands out on request: pages
 * that answer something on their own. The rest are linked by the tool that has the arguments they
 * need in their query, such as the flamegraph view's event type.
 */
public enum MicroscopeView {

    DASHBOARD("dashboard", true),
    AUTO_ANALYSIS("auto-analysis", true),
    OVERVIEW("overview", true),
    EVENT_TYPES("event-types", true),
    FLAGS("flags", true),
    GARBAGE_COLLECTION("garbage-collection", true),
    GARBAGE_COLLECTION_TIMESERIES("garbage-collection/timeseries", true),
    GARBAGE_COLLECTION_CONFIGURATION("garbage-collection/configuration", true),
    ALLOCATIONS("allocations", true),
    NMT("nmt", true),
    NATIVE_MEMORY("native-memory", true),
    MEMORY_LEAK_CANDIDATES("memory-issues/leak-candidates", true),
    THREAD_STATISTICS("thread-statistics", true),
    THREADS_TIMELINE("threads-timeline", true),
    VIRTUAL_THREADS("virtual-threads", true),
    THREAD_DUMPS("thread-dumps", true),
    JIT_COMPILATION("jit-compilation", true),
    CLASS_LOADING("class-loading", true),
    EXCEPTIONS("exceptions", true),
    VM_OPERATIONS("vm-operations", true),
    CONTAINER_CPU_THROTTLING("container/cpu-throttling", true),
    BLOCKING_OPERATIONS("blocking-operations", true),
    SOCKET_IO("socket-io", true),
    FILE_IO("file-io", true),
    HEAP_DUMP_LEAK_SUSPECTS("heap-dump/leak-suspects", true),
    HEAP_DUMP_BIGGEST_OBJECTS("heap-dump/biggest-objects", true),
    HEAP_DUMP_DOMINATOR_TREE("heap-dump/dominator-tree", true),
    HEAP_DUMP_HISTOGRAM("heap-dump/histogram", true),
    HEAP_DUMP_GC_ROOT_PATH("heap-dump/gc-root-path", true),
    SECURITY("security", true),
    SYSTEM("system", true),
    MODULES("modules", true),
    STRING_SYMBOL_TABLES("string-symbol-tables", true),
    GARBAGE_COLLECTION_G1("garbage-collection/g1", true),
    GARBAGE_COLLECTION_ZGC("garbage-collection/zgc", true),
    MEMORY_FINALIZERS("memory-issues/finalizers", true),
    MEMORY_REFERENCE_PROCESSING("memory-issues/reference-processing", true),
    EVENTS("events", true),
    HEAP_DUMP_OQL("heap-dump/oql", true),

    FLAMEGRAPHS_PRIMARY("flamegraphs/primary", false),
    FLAMEGRAPHS_DIFFERENTIAL("flamegraphs/differential", false),
    FLAMEGRAPH_VIEW("flamegraph-view", false),
    SUBSECOND_VIEW("subsecond-view", false),
    HEAP_DUMP_OVERVIEW("heap-dump/overview", false),
    HEAP_DUMP_DIFF("heap-dump/diff", false),
    HEAP_DUMP_CLASSLOADER_ANALYSIS("heap-dump/classloader-analysis", false),
    HEAP_DUMP_CONSUMERS("heap-dump/consumers", false),
    HEAP_DUMP_STRING_ANALYSIS("heap-dump/string-analysis", false),
    HEAP_DUMP_COLLECTION_ANALYSIS("heap-dump/collection-analysis", false),
    HEAP_DUMP_THREADS("heap-dump/threads", false),
    HEAP_DUMP_GC_ROOTS("heap-dump/gc-roots", false),
    CONTAINER_CONFIGURATION("container/configuration", false),
    JDBC_STATEMENTS("technologies/jdbc", false),
    JDBC_STATEMENT_GROUPS("technologies/jdbc/statement-groups", false),
    JDBC_POOL("technologies/jdbc-pool", false),
    HTTP_OVERVIEW("technologies/http/overview", false),
    HTTP_ENDPOINTS("technologies/http/endpoints", false),
    GRPC_OVERVIEW("technologies/grpc/overview", false),
    GRPC_SERVICES("technologies/grpc/services", false),
    GRPC_TRAFFIC("technologies/grpc/traffic", false),
    METHOD_TRACING_TIMESERIES("technologies/method-tracing/timeseries", false),
    METHOD_TRACING_SLOWEST("technologies/method-tracing/slowest", false),
    METHOD_TRACING_TIMING("technologies/method-tracing/timing", false),
    TRACES_OPERATIONS("traces/operations", false),
    TRACES_ATTRIBUTE_SEARCH("traces/attributes/search", false),
    TRACES_ATTRIBUTE_VALUES("traces/attributes/values", false);

    private static final Map<String, MicroscopeView> BY_PATH = Arrays.stream(values())
            .collect(Collectors.toUnmodifiableMap(MicroscopeView::path, Function.identity()));

    private final String path;
    private final boolean offeredByViewLink;

    MicroscopeView(String path, boolean offeredByViewLink) {
        this.path = path;
        this.offeredByViewLink = offeredByViewLink;
    }

    /** The sub-path under {@code /profiles/{profileId}/} the router serves this view at. */
    public String path() {
        return path;
    }

    /** Whether {@code profiles_viewLink} hands this view out on request. */
    public boolean offeredByViewLink() {
        return offeredByViewLink;
    }

    /** The view served at this sub-path, if a tool links to one there. */
    public static Optional<MicroscopeView> ofPath(String path) {
        return Optional.ofNullable(BY_PATH.get(path));
    }
}
