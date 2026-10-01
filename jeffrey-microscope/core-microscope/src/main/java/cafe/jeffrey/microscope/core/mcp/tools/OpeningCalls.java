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

import cafe.jeffrey.microscope.core.mcp.AdvertisedFamilies;
import cafe.jeffrey.microscope.core.mcp.tools.jvm.ContainerSection;
import cafe.jeffrey.microscope.core.mcp.tools.jvm.GcSection;
import cafe.jeffrey.microscope.core.mcp.tools.jvm.SafepointsSection;
import cafe.jeffrey.profile.feature.FeatureType;
import cafe.jeffrey.profile.mcp.McpNextTool;

import java.util.ArrayList;
import java.util.List;

/**
 * The calls that open each investigation area on one profile, with this profile's ids and recorded
 * event types — what the user's pick sends unchanged. Built only for an area the profile can answer.
 */
final class OpeningCalls {

    private static final String COMPUTE = "compute";

    private static final String CPU_WHY = "the call tree of where the CPU went";
    private static final String WALL_WHY = "the call tree of where the time went, running or waiting";
    private static final String ALLOCATIONS_WHY = "which types were allocated most";
    private static final String HOT_WINDOWS_WHY = "when the samples landed: steady, ramp or spike";
    private static final String TRACES_WHY = "the slowest operations and how their latency splits";
    private static final String HTTP_WHY = "the served endpoints ranked by latency";
    private static final String JDBC_WHY = "the statements the application ran, ranked by time";
    private static final String POOLS_WHY = "whether requests waited for a connection";
    private static final String SOCKET_WHY = "which remote endpoints the application waited on";
    private static final String FILE_WHY = "which files the application waited on";
    private static final String BLOCKING_WHY = "which locks and waits held threads back";
    private static final String GC_WHY = "the stop-the-world budget and the longest collections";
    private static final String SAFEPOINTS_WHY = "the pauses outside collections";
    private static final String JIT_WHY = "slow compilations and deoptimisation";
    private static final String THREADS_WHY = "the busiest threads and their states";
    private static final String NATIVE_WHY = "memory outside the Java heap, by category";
    private static final String CONTAINER_WHY = "whether the container throttled the process";
    private static final String FLAGS_WHY = "the flags the JVM really ran with";
    private static final String LEAKS_WHY = "objects that survived collections, with their allocation sites";
    private static final String RULES_WHY = "the rules' verdicts over the whole recording";
    private static final String RULES_COMPUTE_WHY = "runs the rules; slow, it reads the whole recording";

    private static final String MEMORY_ALLOCATIONS = "memory_allocations";
    private static final String MEMORY_LEAK_CANDIDATES = "memory_leakCandidates";
    private static final String TRACES_OVERVIEW = "traces_overview";
    private static final String JVM_GC = "jvm_gc";
    private static final String JVM_SAFEPOINTS = "jvm_safepoints";
    private static final String JVM_JIT = "jvm_jit";
    private static final String JVM_THREADS = "jvm_threads";
    private static final String JVM_NATIVE_MEMORY = "jvm_nativeMemory";
    private static final String JVM_CONTAINER = "jvm_container";
    private static final String JVM_FLAGS = "jvm_flags";
    private static final String JVM_AUTO_ANALYSIS = "jvm_autoAnalysis";
    private static final String SERVER = "SERVER";
    private static final String SOCKET = "SOCKET";
    private static final String FILE = "FILE";

    private OpeningCalls() {
    }

    static AreaCalls cpu() {
        return flamegraph(SampleKind.CPU, CPU_WHY);
    }

    static AreaCalls wall() {
        return flamegraph(SampleKind.WALL, WALL_WHY);
    }

    static AreaCalls allocations() {
        return one(MEMORY_ALLOCATIONS, ALLOCATIONS_WHY);
    }

    static AreaCalls locks() {
        return one(FollowUpCalls.BLOCKING_OVERVIEW, BLOCKING_WHY);
    }

    static AreaCalls jit() {
        return one(JVM_JIT, JIT_WHY);
    }

    static AreaCalls threads() {
        return one(JVM_THREADS, THREADS_WHY);
    }

    static AreaCalls nativeMemory() {
        return one(JVM_NATIVE_MEMORY, NATIVE_WHY);
    }

    static AreaCalls leaks() {
        return one(MEMORY_LEAK_CANDIDATES, LEAKS_WHY);
    }

    /** One call on the profile and nothing else. */
    private static AreaCalls one(String tool, String why) {
        return (facts, advertised) -> List.of(onProfile(facts, tool).why(why));
    }

    /** The flamegraph of the first recorded event type of the kind. */
    private static AreaCalls flamegraph(SampleKind kind, String why) {
        return (facts, advertised) -> facts.recorded(kind)
                .map(panel -> List.of(onProfile(facts, FollowUpCalls.FLAMEGRAPH_EXPORT)
                        .with(FollowUpCalls.EVENT_TYPE, panel.eventType())
                        .why(why)))
                .orElse(List.of());
    }

    static List<McpNextTool> hotWindows(ProfileFacts facts, AdvertisedFamilies advertised) {
        return facts.busiest()
                .map(panel -> List.of(onProfile(facts, FollowUpCalls.TIMELINE_HOT_WINDOWS)
                        .with(FollowUpCalls.EVENT_TYPE, panel.eventType())
                        .why(HOT_WINDOWS_WHY)))
                .orElse(List.of());
    }

    /** The traces when the profile has them and they are served; the served endpoints otherwise. */
    static List<McpNextTool> endpoints(ProfileFacts facts, AdvertisedFamilies advertised) {
        if (facts.enabled(FeatureType.TRACES) && advertised.servesTool(TRACES_OVERVIEW)) {
            return List.of(onProfile(facts, TRACES_OVERVIEW).why(TRACES_WHY));
        }
        return List.of(onProfile(facts, FollowUpCalls.HTTP_OVERVIEW)
                .with(FollowUpCalls.DIRECTION, SERVER)
                .why(HTTP_WHY));
    }

    static List<McpNextTool> database(ProfileFacts facts, AdvertisedFamilies advertised) {
        List<McpNextTool> calls = new ArrayList<>();
        if (facts.enabled(FeatureType.JDBC_STATEMENTS_DASHBOARD)) {
            calls.add(onProfile(facts, FollowUpCalls.JDBC_OVERVIEW).why(JDBC_WHY));
        }
        if (facts.enabled(FeatureType.JDBC_POOL_DASHBOARD)) {
            calls.add(onProfile(facts, FollowUpCalls.JDBC_POOLS).why(POOLS_WHY));
        }
        return calls;
    }

    static List<McpNextTool> io(ProfileFacts facts, AdvertisedFamilies advertised) {
        List<McpNextTool> calls = new ArrayList<>();
        if (facts.recordsAny(InvestigationArea.Events.SOCKET)) {
            calls.add(onProfile(facts, FollowUpCalls.IO_OVERVIEW).with(FollowUpCalls.KIND, SOCKET).why(SOCKET_WHY));
        }
        if (facts.recordsAny(InvestigationArea.Events.FILE)) {
            calls.add(onProfile(facts, FollowUpCalls.IO_OVERVIEW).with(FollowUpCalls.KIND, FILE).why(FILE_WHY));
        }
        return calls;
    }

    static List<McpNextTool> pauses(ProfileFacts facts, AdvertisedFamilies advertised) {
        List<McpNextTool> calls = new ArrayList<>();
        if (facts.jvmSectionAvailable(GcSection.ID)) {
            calls.add(onProfile(facts, JVM_GC).why(GC_WHY));
        }
        if (facts.jvmSectionAvailable(SafepointsSection.ID)) {
            calls.add(onProfile(facts, JVM_SAFEPOINTS).why(SAFEPOINTS_WHY));
        }
        return calls;
    }

    static List<McpNextTool> setup(ProfileFacts facts, AdvertisedFamilies advertised) {
        List<McpNextTool> calls = new ArrayList<>();
        if (facts.jvmSectionAvailable(ContainerSection.ID)) {
            calls.add(onProfile(facts, JVM_CONTAINER).why(CONTAINER_WHY));
        }
        calls.add(onProfile(facts, JVM_FLAGS).why(FLAGS_WHY));
        return calls;
    }

    /**
     * The rules' verdicts: read when they ran; run them first when they have not — the slow call
     * offered only because there is nothing to read yet.
     */
    static List<McpNextTool> rules(ProfileFacts facts, AdvertisedFamilies advertised) {
        if (facts.autoAnalysis() == AutoAnalysisStatus.NOT_COMPUTED) {
            return List.of(onProfile(facts, JVM_AUTO_ANALYSIS).with(COMPUTE, true).why(RULES_COMPUTE_WHY));
        }
        return List.of(onProfile(facts, JVM_AUTO_ANALYSIS).why(RULES_WHY));
    }

    private static McpNextTool.Call onProfile(ProfileFacts facts, String tool) {
        return NextCalls.to(tool).with(FollowUpCalls.PROFILE_ID, facts.profileId());
    }
}
