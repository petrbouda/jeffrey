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

package cafe.jeffrey.microscope.core.mcp.tools.jvm;

import cafe.jeffrey.microscope.core.mcp.AdvertisedFamilies;
import cafe.jeffrey.microscope.core.mcp.tools.FollowUpCalls;
import cafe.jeffrey.microscope.core.mcp.tools.NextSteps;
import cafe.jeffrey.profile.manager.ProfileManager;
import cafe.jeffrey.profile.mcp.McpNextTool;

import java.util.Optional;
import java.util.function.Function;

/**
 * The calls the sections route to, named once: the tools, the arguments they take and the event types
 * those arguments carry, so a section says where to go next with values rather than retyping strings.
 */
final class SectionCalls {

    static final String PROFILE_ID = "profileId";
    static final String EVENT_TYPE = "eventType";
    static final String USE_WEIGHT = "useWeight";
    static final String THREAD_MODE = "threadMode";
    static final String SEARCH = "search";
    static final String KIND = "kind";
    static final String SORT = "sort";
    static final String PAGE = "page";
    static final String SECTION = "section";

    static final String FLAMEGRAPH_EXPORT = "flamegraph_export";
    static final String FLAMEGRAPH_LIST = "flamegraph_list";
    static final String PROFILES_FEATURES = "profiles_features";
    static final String TIMELINE_HOT_WINDOWS = "timeline_hotWindows";
    static final String BLOCKING_MONITORS = "blocking_monitors";
    static final String IO_ENDPOINTS = "io_endpoints";
    static final String IO_OVERVIEW = "io_overview";
    static final String TRACES_OPERATIONS = "traces_operations";
    static final String HEAP_CLASS_LOADER_LEAK_CHAINS = "heap_getClassLoaderLeakChains";
    static final String JVM_GC = "jvm_gc";
    static final String JVM_SAFEPOINTS = "jvm_safepoints";
    static final String JVM_JIT = "jvm_jit";
    static final String JVM_THREADS = "jvm_threads";
    static final String JVM_FLAGS = "jvm_flags";
    static final String JVM_CONTAINER = "jvm_container";
    static final String JVM_GC_DETAIL = "jvm_gcDetail";
    static final String JVM_CONFIGURATION = "jvm_configuration";

    private SectionCalls() {
    }

    /** A call to a tool on the profile being answered about; add the rest of its arguments and a why. */
    static McpNextTool.Call on(String tool, String profileId) {
        return McpNextTool.call(tool).with(PROFILE_ID, profileId);
    }

    /**
     * The allocation flamegraph, weighted by bytes: the call paths that produce the garbage.
     *
     * @param eventType the allocation type the profile recorded
     */
    static McpNextTool allocationPaths(String profileId, String eventType, String why) {
        return on(FLAMEGRAPH_EXPORT, profileId)
                .with(EVENT_TYPE, eventType)
                .with(USE_WEIGHT, true)
                .why(why);
    }

    /**
     * A call on the on-CPU sample type this profile recorded, or - when it recorded none - the guidance
     * that names the catalogue instead, so no call draws an empty graph.
     *
     * @param call the call, given the event type
     */
    static void onCpu(NextSteps.Builder next, ProfileManager profileManager, Function<String, McpNextTool> call) {
        Optional<String> eventType = FollowUpCalls.recordedOnCpuEvent(profileManager);
        if (eventType.isPresent()) {
            next.next(call.apply(eventType.get()));
        } else {
            next.guidanceFor(AdvertisedFamilies.FLAMEGRAPH, FollowUpCalls.NO_ON_CPU_EVENT);
        }
    }

    /**
     * The allocation-paths call on the allocation type this profile recorded, or the guidance that names
     * the catalogue instead when it recorded none.
     */
    static void allocationPaths(NextSteps.Builder next, ProfileManager profileManager, String why) {
        Optional<String> eventType = FollowUpCalls.recordedAllocationEvent(profileManager);
        if (eventType.isPresent()) {
            next.next(allocationPaths(profileManager.info().id(), eventType.get(), why));
        } else {
            next.guidanceFor(AdvertisedFamilies.FLAMEGRAPH, FollowUpCalls.NO_ALLOCATION_EVENT);
        }
    }
}
