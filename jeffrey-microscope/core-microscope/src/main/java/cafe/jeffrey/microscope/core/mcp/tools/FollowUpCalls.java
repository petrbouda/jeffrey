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

package cafe.jeffrey.microscope.core.mcp.tools;

import cafe.jeffrey.microscope.model.Type;
import cafe.jeffrey.profile.manager.ProfileManager;
import cafe.jeffrey.profile.model.EventSummaryResult;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The tools and arguments the trace and technology families hand back as next calls, in one place so
 * a renamed argument is renamed once and every call that names it follows.
 */
public final class FollowUpCalls {

    static final String PROFILE_ID = "profileId";
    static final String EVENT_TYPE = "eventType";
    static final String USE_WEIGHT = "useWeight";
    static final String THREAD_MODE = "threadMode";
    static final String DIRECTION = "direction";
    static final String KIND = "kind";
    static final String NAME = "name";
    static final String SEARCH = "search";
    static final String ERRORS_ONLY = "errorsOnly";
    static final String SORT = "sort";
    static final String LIMIT = "limit";
    static final String CURSOR = "cursor";
    static final String TRACE_ID = "traceId";
    static final String SPAN_ID = "spanId";
    static final String SELF_ONLY = "selfOnly";
    static final String GRAPH_EVENT_TYPE = "graphEventType";
    static final String URI = "uri";
    static final String GROUP = "group";
    static final String SERVICE = "service";
    static final String KEY = "key";
    static final String SOURCE = "source";
    static final String OWNER = "owner";
    static final String OPERATOR = "operator";
    static final String VALUE = "value";
    static final String SCOPE = "scope";

    static final String FLAMEGRAPH_EXPORT = "flamegraph_export";
    static final String FLAMEGRAPH_LIST = "flamegraph_list";
    static final String TIMELINE_HOT_WINDOWS = "timeline_hotWindows";
    static final String PROFILES_FEATURES = "profiles_features";
    static final String JVM_GC = "jvm_gc";

    static final String TRACES_OPERATIONS = "traces_operations";
    static final String TRACES_NOTIFICATIONS = "traces_notifications";
    static final String TRACES_OPERATION_EXPORT = "traces_operationExport";
    static final String TRACES_SLOWEST = "traces_slowestTraces";
    static final String TRACES_TRACE_EXPORT = "traces_traceExport";
    static final String TRACES_SPAN_FLAMEGRAPH = "traces_spanFlamegraphExport";
    static final String TRACES_OPERATION_FLAMEGRAPH = "traces_operationFlamegraphExport";
    static final String TRACES_ATTRIBUTE_KEYS = "traces_attributeKeys";
    static final String TRACES_ATTRIBUTE_VALUES = "traces_attributeValues";
    static final String TRACES_ATTRIBUTE_SEARCH = "traces_attributeSearch";

    static final String HTTP_OVERVIEW = "http_overview";
    static final String HTTP_ENDPOINT = "http_endpoint";
    static final String JDBC_OVERVIEW = "jdbc_overview";
    static final String JDBC_STATEMENT_GROUP = "jdbc_statementGroup";
    static final String JDBC_POOLS = "jdbc_pools";
    static final String GRPC_OVERVIEW = "grpc_overview";
    static final String GRPC_SERVICE = "grpc_service";
    static final String GRPC_TRAFFIC = "grpc_traffic";
    static final String IO_OVERVIEW = "io_overview";
    static final String IO_ENDPOINTS = "io_endpoints";
    static final String IO_SLOWEST = "io_slowest";
    static final String BLOCKING_OVERVIEW = "blocking_overview";
    static final String BLOCKING_MONITORS = "blocking_monitors";
    static final String BLOCKING_PINNED = "blocking_pinnedThreads";
    static final String METHOD_TRACING_OVERVIEW = "methodtracing_overview";
    static final String METHOD_TRACING_SLOWEST = "methodtracing_slowest";
    static final String METHOD_TRACING_TIMING = "methodtracing_timing";

    /**
     * The sample types a "what code was running" call can graph, in the order one is preferred: the
     * execution sampler, the CPU-time sampler, and wall-clock samples when neither was recorded.
     */
    static final List<String> ON_CPU_EVENTS = List.of(
            Type.EXECUTION_SAMPLE.code(), Type.CPU_TIME_SAMPLE.code(), Type.WALL_CLOCK_SAMPLE.code());

    /** Said instead of a flamegraph call when the profile recorded none of {@link #ON_CPU_EVENTS}. */
    public static final String NO_ON_CPU_EVENT =
            "No execution, CPU-time or wall-clock samples were recorded; flamegraph_list names the event "
                    + "types this profile can graph.";

    /** The event types a family's own events are. */
    static final String MONITOR_ENTER_EVENT = Type.JAVA_MONITOR_ENTER.code();
    static final String METHOD_TRACE_EVENT = Type.METHOD_TRACE.code();
    static final String SAMPLED_ALLOCATION_EVENT = Type.OBJECT_ALLOCATION_SAMPLE.code();
    static final String TLAB_ALLOCATION_EVENT = Type.OBJECT_ALLOCATION_IN_NEW_TLAB.code();

    /** The allocation types an allocation-paths call can graph, the sampled one preferred. */
    static final List<String> ALLOCATION_EVENTS = List.of(SAMPLED_ALLOCATION_EVENT, TLAB_ALLOCATION_EVENT);

    /** Said instead of an allocation flamegraph call when the profile recorded none of {@link #ALLOCATION_EVENTS}. */
    public static final String NO_ALLOCATION_EVENT =
            "No allocation samples were recorded; flamegraph_list names the event types this profile can graph.";

    private FollowUpCalls() {
    }

    /**
     * The first of {@link #ON_CPU_EVENTS} this profile recorded samples of, read from the event-type
     * summaries - the same cheap read the comparison and flamegraph catalogues make - so a next call
     * never names a type whose graph would be empty.
     */
    public static Optional<String> recordedOnCpuEvent(ProfileManager profileManager) {
        return firstRecorded(profileManager, ON_CPU_EVENTS);
    }

    /**
     * The allocation type this profile recorded samples of: the sampled allocations when there are
     * any, else the TLAB pair's in-TLAB half - a call naming the other one would draw an empty graph.
     */
    public static Optional<String> recordedAllocationEvent(ProfileManager profileManager) {
        return firstRecorded(profileManager, ALLOCATION_EVENTS);
    }

    private static Optional<String> firstRecorded(ProfileManager profileManager, List<String> preferred) {
        Set<String> recorded = profileManager.flamegraphManager().eventSummaries().stream()
                .filter(summary -> summary.primary().samples() > 0)
                .map(EventSummaryResult::code)
                .collect(Collectors.toSet());
        return preferred.stream().filter(recorded::contains).findFirst();
    }
}
