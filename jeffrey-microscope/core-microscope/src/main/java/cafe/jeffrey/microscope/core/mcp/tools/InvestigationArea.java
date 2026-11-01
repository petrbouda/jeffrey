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
import cafe.jeffrey.microscope.core.mcp.tools.heap.HeapReport;
import cafe.jeffrey.microscope.core.mcp.tools.jvm.ConfigurationSection;
import cafe.jeffrey.microscope.core.mcp.tools.jvm.ContainerSection;
import cafe.jeffrey.microscope.core.mcp.tools.jvm.ExceptionsSection;
import cafe.jeffrey.microscope.core.mcp.tools.jvm.GcSection;
import cafe.jeffrey.microscope.core.mcp.tools.jvm.JitSection;
import cafe.jeffrey.microscope.core.mcp.tools.jvm.NativeMemorySection;
import cafe.jeffrey.microscope.core.mcp.tools.jvm.SafepointsSection;
import cafe.jeffrey.microscope.core.mcp.tools.jvm.ThreadsSection;
import cafe.jeffrey.microscope.model.Type;
import cafe.jeffrey.profile.feature.FeatureType;

import java.util.List;
import java.util.Set;

/**
 * One thing a user can choose to investigate in a recording or a heap dump — the lines of the menu an
 * open question is answered with. Each area knows its group, what it answers, where its data comes from and so
 * whether a profile can answer it, the calls that open it, the families those calls belong to, and the
 * auto-analysis topics whose fired rules point at it.
 */
enum InvestigationArea {

    CPU_HOTSPOTS(InvestigationGroup.CODE, "CPU hotspots", "Which code burned the CPU",
            new AreaSource.Samples(Set.of(SampleKind.CPU), Remedies.CPU),
            OpeningCalls.cpu(),
            Set.of(AdvertisedFamilies.FLAMEGRAPH),
            Set.of("method_profiling")),

    WALL_CLOCK(InvestigationGroup.CODE, "Wall-clock time", "Where the time went, running or waiting",
            new AreaSource.Samples(Set.of(SampleKind.WALL), Remedies.WALL),
            OpeningCalls.wall(),
            Set.of(AdvertisedFamilies.FLAMEGRAPH),
            Set.of()),

    ALLOCATION(InvestigationGroup.CODE, "Allocation", "Which types allocate most, then from where",
            new AreaSource.Samples(Set.of(SampleKind.ALLOCATION), Remedies.ALLOCATION),
            OpeningCalls.allocations(),
            Set.of(AdvertisedFamilies.FLAMEGRAPH, AdvertisedFamilies.MEMORY),
            Set.of("tlab")),

    LOCK_CONTENTION(InvestigationGroup.CODE, "Lock contention", "Which locks and waits held threads back",
            new AreaSource.Samples(Set.of(SampleKind.BLOCKING), Remedies.LOCKS),
            OpeningCalls.locks(),
            Set.of(AdvertisedFamilies.FLAMEGRAPH, AdvertisedFamilies.BLOCKING),
            Set.of("lock_instances", "biased_locking")),

    /**
     * Its own line whenever the profile has traces, rather than folded into the dashboards: a request's
     * time span by span is a different question from an endpoint's figures, and a menu that never says
     * "traces" is one a user does not pick them from.
     */
    TRACES(InvestigationGroup.WAITING, "Traces",
            "Which requests were slow, and where each one's time went, span by span",
            new AreaSource.Features(List.of(FeatureType.TRACES)),
            OpeningCalls::traces,
            Set.of(AdvertisedFamilies.TRACES),
            Set.of()),

    REQUESTS_AND_CALLS(InvestigationGroup.WAITING, "Requests and calls",
            "Which endpoints, outbound calls and traced methods took the time",
            new AreaSource.Features(List.of(FeatureType.HTTP_SERVER_DASHBOARD,
                    FeatureType.GRPC_SERVER_DASHBOARD, FeatureType.HTTP_CLIENT_DASHBOARD,
                    FeatureType.GRPC_CLIENT_DASHBOARD, FeatureType.METHOD_TRACING_DASHBOARD)),
            OpeningCalls::requestsAndCalls,
            Set.of(AdvertisedFamilies.HTTP, AdvertisedFamilies.GRPC, AdvertisedFamilies.METHOD_TRACING),
            Set.of()),

    DATABASE(InvestigationGroup.WAITING, "Database", "Which statements ran, and whether the pool made requests wait",
            new AreaSource.Features(List.of(FeatureType.JDBC_STATEMENTS_DASHBOARD, FeatureType.JDBC_POOL_DASHBOARD)),
            OpeningCalls::database,
            Set.of(AdvertisedFamilies.JDBC),
            Set.of()),

    IO_WAITING(InvestigationGroup.WAITING, "I/O waiting", "What it talked to over sockets and files, and how long it waited",
            new AreaSource.EventTypes(Events.IO, Remedies.IO),
            OpeningCalls::io,
            Set.of(AdvertisedFamilies.IO),
            Set.of("file_io", "socket_io")),

    GC_PAUSES(InvestigationGroup.JVM, "GC and pauses", "Whether collections and safepoints stopped the application",
            new AreaSource.JvmSectionsOf(Set.of(GcSection.ID, SafepointsSection.ID)),
            OpeningCalls::pauses,
            Set.of(AdvertisedFamilies.JVM),
            Set.of("garbage_collection", "gc_summary", "gc_configuration", "heap", "vm_operations")),

    JIT(InvestigationGroup.JVM, "JIT compilation", "Slow compilations and deoptimisation",
            new AreaSource.JvmSectionsOf(Set.of(JitSection.ID)),
            OpeningCalls.jit(),
            Set.of(AdvertisedFamilies.JVM),
            Set.of("code_cache", "compilations")),

    THREADS(InvestigationGroup.JVM, "Threads", "Which threads were busiest, and in which states",
            new AreaSource.JvmSectionsOf(Set.of(ThreadsSection.ID)),
            OpeningCalls.threads(),
            Set.of(AdvertisedFamilies.JVM),
            Set.of("threads", "thread_dumps", "java_application")),

    NATIVE_MEMORY(InvestigationGroup.JVM, "Native memory", "Memory outside the Java heap",
            new AreaSource.JvmSectionsOf(Set.of(NativeMemorySection.ID)),
            OpeningCalls.nativeMemory(),
            Set.of(AdvertisedFamilies.JVM),
            Set.of("native_library")),

    RULE_FINDINGS(InvestigationGroup.OVERALL, "Rule findings", "What Jeffrey's rules flag across the whole recording",
            new AreaSource.Rules(),
            OpeningCalls::rules,
            Set.of(AdvertisedFamilies.JVM),
            Set.of()),

    CONTAINER_AND_FLAGS(InvestigationGroup.OVERALL, "Container and flags", "Throttling, and what the JVM really ran with",
            new AreaSource.JvmSectionsOf(Set.of(ContainerSection.ID, ConfigurationSection.ID)),
            OpeningCalls::setup,
            Set.of(AdvertisedFamilies.JVM),
            Set.of("jvm_information", "environment_variables", "system_properties", "agent_information",
                    "system_information", "processes")),

    LEAK_CANDIDATES(InvestigationGroup.OVERALL, "Leak candidates", "Objects that survived collections",
            new AreaSource.EventTypes(Events.OLD_OBJECTS, Remedies.LEAKS),
            OpeningCalls.leaks(),
            Set.of(AdvertisedFamilies.MEMORY),
            Set.of("memoryleak")),

    EXCEPTIONS(InvestigationGroup.OVERALL, "Exceptions", "What the application threw, and from where",
            new AreaSource.JvmSectionsOf(Set.of(ExceptionsSection.ID)),
            OpeningCalls.exceptions(),
            Set.of(AdvertisedFamilies.JVM),
            Set.of("exceptions")),

    HEAP_OVERVIEW(InvestigationGroup.HEAP_CONTENTS, "Heap overview", "How big the heap is, and how the dump was taken",
            AreaSource.HeapReports.indexOnly(),
            OpeningCalls.heapOverview(),
            Set.of(AdvertisedFamilies.HEAP),
            Set.of()),

    CLASS_HISTOGRAM(InvestigationGroup.HEAP_CONTENTS, "Class histogram",
            "Which classes hold the most instances and bytes",
            AreaSource.HeapReports.indexOnly(),
            OpeningCalls.classHistogram(),
            Set.of(AdvertisedFamilies.HEAP),
            Set.of()),

    TOP_CONSUMERS(InvestigationGroup.HEAP_CONTENTS, "Top consumers", "Which packages and class loaders own the memory",
            new AreaSource.HeapReports(HeapReport.CONSUMERS),
            OpeningCalls.topConsumers(),
            Set.of(AdvertisedFamilies.HEAP),
            Set.of()),

    BIGGEST_OBJECTS(InvestigationGroup.HEAP_CONTENTS, "Biggest objects", "Which single objects retain the most",
            new AreaSource.HeapReports(HeapReport.BIGGEST),
            OpeningCalls.biggestObjects(),
            Set.of(AdvertisedFamilies.HEAP),
            Set.of()),

    LEAK_SUSPECTS(InvestigationGroup.HEAP_RETENTION, "Leak suspects", "What looks like a leak, and what holds it",
            new AreaSource.HeapReports(HeapReport.LEAKS),
            OpeningCalls.leakSuspects(),
            Set.of(AdvertisedFamilies.HEAP),
            Set.of()),

    DOMINATORS(InvestigationGroup.HEAP_RETENTION, "Dominator tree", "Which objects keep the most memory alive",
            AreaSource.HeapReports.indexOnly(),
            OpeningCalls.dominators(),
            Set.of(AdvertisedFamilies.HEAP),
            Set.of()),

    CLASSLOADER_LEAKS(InvestigationGroup.HEAP_RETENTION, "Class loader leaks", "Which class loaders outlived their use",
            new AreaSource.HeapReports(HeapReport.CLASSLOADERS),
            OpeningCalls.classLoaderLeaks(),
            Set.of(AdvertisedFamilies.HEAP),
            Set.of()),

    GC_ROOTS(InvestigationGroup.HEAP_RETENTION, "GC roots and threads",
            "What anchors the heap, and what each thread holds",
            AreaSource.HeapReports.indexOnly(),
            OpeningCalls.gcRoots(),
            Set.of(AdvertisedFamilies.HEAP),
            Set.of()),

    STRING_WASTE(InvestigationGroup.HEAP_WASTE, "Duplicate strings", "Which strings are stored many times over",
            new AreaSource.HeapReports(HeapReport.STRINGS),
            OpeningCalls.stringWaste(),
            Set.of(AdvertisedFamilies.HEAP),
            Set.of()),

    COLLECTION_WASTE(InvestigationGroup.HEAP_WASTE, "Wasteful collections",
            "Which collections sit empty, near-empty or oversized",
            new AreaSource.HeapReports(HeapReport.COLLECTIONS),
            OpeningCalls.collectionWaste(),
            Set.of(AdvertisedFamilies.HEAP),
            Set.of());

    /**
     * Event types the areas read, in a holder of their own: an enum's constants are built before its
     * static fields, so a constant cannot take one of those.
     */
    static final class Events {
        static final Set<String> SOCKET = Set.of(Type.SOCKET_READ.code(), Type.SOCKET_WRITE.code());
        static final Set<String> FILE = Set.of(Type.FILE_READ.code(), Type.FILE_WRITE.code());
        static final Set<String> IO = Set.of(
                Type.SOCKET_READ.code(), Type.SOCKET_WRITE.code(), Type.FILE_READ.code(), Type.FILE_WRITE.code());
        static final Set<String> OLD_OBJECTS = Set.of(Type.OLD_OBJECT_SAMPLE.code());

        private Events() {
        }
    }

    /** What records each kind of data next time, in the profiler's own option names. */
    private static final class Remedies {
        static final String CPU = "Record CPU samples next time: async-profiler event=cpu (or event=ctimer in a "
                + "container), or JFR's jdk.ExecutionSample / jdk.CPUTimeSample.";
        static final String WALL = "Record wall-clock samples next time: async-profiler wall=10ms.";
        static final String ALLOCATION = "Record allocation samples next time: async-profiler alloc=512k, or JFR's "
                + "jdk.ObjectAllocationSample.";
        static final String LOCKS = "Record lock samples next time: async-profiler lock=10ms, or JFR's "
                + "jdk.JavaMonitorEnter and jdk.ThreadPark.";
        static final String IO = "Enable jdk.SocketRead, jdk.SocketWrite, jdk.FileRead and jdk.FileWrite in the "
                + "recording settings for the next run.";
        static final String LEAKS = "Enable jdk.OldObjectSample in the recording settings (JFR's profile settings "
                + "do) for the next run.";

        private Remedies() {
        }
    }

    private final InvestigationGroup group;
    private final String title;
    private final String question;
    private final AreaSource source;
    private final AreaCalls calls;
    private final Set<String> families;
    private final Set<String> topics;

    InvestigationArea(
            InvestigationGroup group,
            String title,
            String question,
            AreaSource source,
            AreaCalls calls,
            Set<String> families,
            Set<String> topics) {

        this.group = group;
        this.title = title;
        this.question = question;
        this.source = source;
        this.calls = calls;
        this.families = families;
        this.topics = topics;
    }

    InvestigationGroup group() {
        return group;
    }

    String title() {
        return title;
    }

    String question() {
        return question;
    }

    AreaSource source() {
        return source;
    }

    AreaCalls calls() {
        return calls;
    }

    /** Whether any family the area's calls belong to is served on this installation. */
    boolean servedBy(AdvertisedFamilies advertised) {
        return families.stream().anyMatch(advertised::has);
    }

    /**
     * The area a fired rule in this auto-analysis topic points at: the one that claims the topic, or
     * the rule findings themselves for a topic no area claims.
     */
    static InvestigationArea forTopic(String topic) {
        for (InvestigationArea area : values()) {
            if (area.topics.contains(topic)) {
                return area;
            }
        }
        return RULE_FINDINGS;
    }

    Set<String> topics() {
        return topics;
    }
}
