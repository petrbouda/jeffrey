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
import cafe.jeffrey.microscope.core.mcp.tools.InvestigationOption.Availability;
import cafe.jeffrey.microscope.core.mcp.tools.ProfileFacts.HeapState;
import cafe.jeffrey.microscope.core.mcp.tools.heap.HeapReport;
import cafe.jeffrey.microscope.core.mcp.tools.jvm.JvmSections.SectionAvailability;
import cafe.jeffrey.microscope.mcp.protocol.McpSchemaGenerator;
import cafe.jeffrey.microscope.mcp.protocol.testing.McpSchemaConformance;
import cafe.jeffrey.microscope.model.RecordingEventSource;
import cafe.jeffrey.profile.feature.FeatureType;
import cafe.jeffrey.profile.mcp.McpNextTool;
import cafe.jeffrey.profile.mcp.McpToolWeight;
import cafe.jeffrey.profile.mcp.finding.McpFinding;
import cafe.jeffrey.shared.common.Json;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static cafe.jeffrey.microscope.core.mcp.AdvertisedFamiliesFixture.EVERY_FAMILY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InvestigationMenuTest {

    private static final String PROFILE_ID = "p-1";
    private static final String EXECUTION = "jdk.ExecutionSample";
    private static final String CPU_TIME = "jdk.CPUTimeSample";
    private static final String WALL = "profiler.WallClockSample";
    private static final String ALLOCATION = "jdk.ObjectAllocationSample";
    private static final String MONITOR = "jdk.JavaMonitorEnter";
    private static final Set<String> WEIGHTED = Set.of(ALLOCATION, MONITOR);
    private static final EpochWindow RECORDING = new EpochWindow(1_000L, 61_000L);
    private static final List<String> JVM_SECTION_IDS =
            List.of("gc", "safepoints", "jit", "threads", "nativeMemory", "container", "configuration", "exceptions");
    private static final Set<HeapReport> EVERY_STORED_REPORT = Set.of(HeapReport.CONSUMERS, HeapReport.BIGGEST,
            HeapReport.LEAKS, HeapReport.CLASSLOADERS, HeapReport.STRINGS, HeapReport.COLLECTIONS);

    /** A recording with every kind of data, built up and narrowed by each test. */
    private static final class Facts {
        private RecordingEventSource source = RecordingEventSource.ASYNC_PROFILER;
        private final Set<FeatureType> disabled = new HashSet<>();
        private final Set<String> eventTypes = new HashSet<>(Set.of(
                "jdk.SocketRead", "jdk.FileWrite", "jdk.OldObjectSample"));
        /** In the grid's own panel order, which decides the panel an area graphs first. */
        private final Map<String, Long> samples = new LinkedHashMap<>();

        Facts() {
            samples.put(EXECUTION, 1_000L);
            samples.put(CPU_TIME, 0L);
            samples.put(WALL, 5_000L);
            samples.put(ALLOCATION, 500L);
            samples.put(MONITOR, 300L);
        }
        private final Map<String, Long> unpanelled = new LinkedHashMap<>();
        private final Set<String> missingSections = new HashSet<>();
        private AutoAnalysisStatus status = AutoAnalysisStatus.COMPUTED;
        private final List<McpFinding> flagged = new ArrayList<>();
        private HeapState heap = HeapState.NOT_A_HEAP_DUMP;

        Facts samples(String eventType, long count) {
            samples.put(eventType, count);
            return this;
        }

        /** A recorded type with stacks that no panel shows: a custom event, a native sampler. */
        Facts graphable(String eventType, long count) {
            unpanelled.put(eventType, count);
            return this;
        }

        Facts disable(FeatureType... features) {
            disabled.addAll(Arrays.asList(features));
            return this;
        }

        Facts noSection(String id) {
            missingSections.add(id);
            return this;
        }

        Facts noEventTypes() {
            eventTypes.clear();
            return this;
        }

        Facts status(AutoAnalysisStatus status) {
            this.status = status;
            return this;
        }

        Facts source(RecordingEventSource source) {
            this.source = source;
            return this;
        }

        Facts heapDump(HeapState heap) {
            this.source = RecordingEventSource.HEAP_DUMP;
            this.heap = heap;
            return this;
        }

        Facts flagged(String topic, McpFinding.Severity severity) {
            flagged.add(McpFinding.of(topic, topic + "-rule").severity(severity).title(topic + " rule").build());
            return this;
        }

        ProfileFacts build() {
            Map<String, SampleKind> kinds = Map.of(EXECUTION, SampleKind.CPU, CPU_TIME, SampleKind.CPU,
                    WALL, SampleKind.WALL, ALLOCATION, SampleKind.ALLOCATION, MONITOR, SampleKind.BLOCKING);
            List<ProfileFacts.Sampled> panels = samples.entrySet().stream()
                    .map(entry -> new ProfileFacts.Sampled(kinds.get(entry.getKey()), entry.getKey(), entry.getValue(),
                            WEIGHTED.contains(entry.getKey())))
                    .toList();
            Set<String> recorded = new HashSet<>(eventTypes);
            samples.forEach((type, count) -> {
                if (count > 0) {
                    recorded.add(type);
                }
            });
            List<SectionAvailability> sections = JVM_SECTION_IDS.stream()
                    .map(id -> new SectionAvailability(id, id, "jvm_" + id, !missingSections.contains(id),
                            List.of("jdk." + id)))
                    .toList();
            List<ProfileFacts.Graphable> graphable = new ArrayList<>();
            samples.forEach((type, count) -> graphable.add(
                    new ProfileFacts.Graphable(type, type + " label", count, WEIGHTED.contains(type))));
            unpanelled.forEach((type, count) -> graphable.add(
                    new ProfileFacts.Graphable(type, type + " label", count, false)));
            return new ProfileFacts(PROFILE_ID, source, disabled, recorded, panels, graphable, sections, status,
                    flagged, heap, RECORDING);
        }
    }

    /** The links are absolute, built against the request being served, as on the MCP endpoint. */
    @BeforeEach
    void bindRequest() {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
    }

    @AfterEach
    void releaseRequest() {
        RequestContextHolder.resetRequestAttributes();
    }

    private static Map<InvestigationArea, InvestigationOption> menu(Facts facts) {
        return menu(facts, EVERY_FAMILY);
    }

    private static Map<InvestigationArea, InvestigationOption> menu(Facts facts, AdvertisedFamilies advertised) {
        Map<InvestigationArea, InvestigationOption> byArea = new EnumMap<>(InvestigationArea.class);
        for (InvestigationOption option : InvestigationMenu.of(facts.build(), advertised)) {
            byArea.put(option.area(), option);
        }
        return byArea;
    }

    private static List<String> tools(InvestigationOption option) {
        return option.nextTools().stream().map(McpNextTool::tool).toList();
    }

    private static String argument(McpNextTool call, String name) {
        return Json.toTree(call.arguments()).get(name).asString();
    }

    private static Set<InvestigationArea> areasOf(MenuSubject subject) {
        return Arrays.stream(InvestigationArea.values())
                .filter(area -> area.group().subject() == subject)
                .collect(Collectors.toSet());
    }

    @Nested
    class Catalogue {

        /** One question per group with up to four options is how a host puts the menu to the user. */
        @Test
        void everyGroupHoldsAtMostFourAreas() {
            Map<InvestigationGroup, Long> sizes = Arrays.stream(InvestigationArea.values())
                    .collect(Collectors.groupingBy(InvestigationArea::group, Collectors.counting()));

            assertEquals(Set.of(InvestigationGroup.values()), sizes.keySet());
            sizes.forEach((group, size) -> assertTrue(size <= 4, group + " holds " + size));
        }

        /** A profile is offered its own subject's groups only, so it is asked at most four questions. */
        @Test
        void everySubjectHoldsAtMostFourGroups() {
            Map<MenuSubject, Long> groups = Arrays.stream(InvestigationGroup.values())
                    .collect(Collectors.groupingBy(InvestigationGroup::subject, Collectors.counting()));

            assertEquals(Set.of(MenuSubject.values()), groups.keySet());
            groups.forEach((subject, size) -> assertTrue(size <= 4, subject + " holds " + size));
        }

        @Test
        void titlesAreShortEnoughForAnOptionLabel() {
            for (InvestigationArea area : InvestigationArea.values()) {
                assertTrue(area.title().split(" ").length <= 5, area.title());
            }
        }

        @Test
        void noTopicIsClaimedByTwoAreas() {
            Set<String> seen = new HashSet<>();
            for (InvestigationArea area : InvestigationArea.values()) {
                for (String topic : area.topics()) {
                    assertTrue(seen.add(topic), topic);
                }
            }
        }

        @Test
        void aTopicNoAreaClaimsPointsAtTheRuleFindings() {
            assertEquals(InvestigationArea.RULE_FINDINGS, InvestigationArea.forTopic("constant_pools"));
            assertEquals(InvestigationArea.GC_PAUSES, InvestigationArea.forTopic("garbage_collection"));
            assertEquals(InvestigationArea.EXCEPTIONS, InvestigationArea.forTopic("exceptions"));
        }
    }

    @Nested
    class Areas {

        @Test
        void aFullRecordingOffersEveryRecordingArea() {
            Map<InvestigationArea, InvestigationOption> menu = menu(new Facts());

            assertEquals(areasOf(MenuSubject.RECORDING), menu.keySet());
            menu.values().forEach(option -> assertEquals(Availability.AVAILABLE, option.availability(), option.title()));
        }

        @Test
        void eachAreaOpensWithItsOwnCalls() {
            Map<InvestigationArea, InvestigationOption> menu = menu(new Facts());

            McpNextTool cpu = menu.get(InvestigationArea.CPU_HOTSPOTS).nextTools().getFirst();
            assertEquals("flamegraph_export", cpu.tool());
            assertEquals(EXECUTION, Json.toTree(cpu.arguments()).get("eventType").asString());
            assertEquals(PROFILE_ID, Json.toTree(cpu.arguments()).get("profileId").asString());
            assertEquals(List.of("jvm_gc", "jvm_safepoints"), tools(menu.get(InvestigationArea.GC_PAUSES)));
            assertEquals(List.of("jdbc_overview", "jdbc_pools"), tools(menu.get(InvestigationArea.DATABASE)));
            assertEquals(List.of("io_overview", "io_overview"), tools(menu.get(InvestigationArea.IO_WAITING)));
            assertEquals(List.of("traces_overview"), tools(menu.get(InvestigationArea.TRACES)));
            assertEquals(List.of("http_overview", "grpc_overview", "http_overview", "grpc_overview",
                    "methodtracing_overview"), tools(menu.get(InvestigationArea.REQUESTS_AND_CALLS)));
            assertEquals(List.of("jvm_exceptions"), tools(menu.get(InvestigationArea.EXCEPTIONS)));
        }

        /**
         * Allocation and lock contention open on their call tree as CPU and wall-clock do, with their
         * table beside it, so a pick of either reads the graph and the ranking at once.
         */
        @Test
        void allocationAndLocksOpenOnTheirGraphThenTheirTable() {
            Map<InvestigationArea, InvestigationOption> menu = menu(new Facts());

            List<McpNextTool> allocation = menu.get(InvestigationArea.ALLOCATION).nextTools();
            assertEquals(List.of("flamegraph_export", "memory_allocations"), tools(menu.get(InvestigationArea.ALLOCATION)));
            assertEquals(ALLOCATION, argument(allocation.getFirst(), "eventType"));
            List<McpNextTool> locks = menu.get(InvestigationArea.LOCK_CONTENTION).nextTools();
            assertEquals(List.of("flamegraph_export", "blocking_overview"), tools(menu.get(InvestigationArea.LOCK_CONTENTION)));
            assertEquals(MONITOR, argument(locks.getFirst(), "eventType"));
            assertFalse(Json.toTree(allocation.getFirst().arguments()).has("useWeight"),
                    "the export weighs by the event type's own default, bytes for allocation");
        }

        /**
         * Each area links the page it is seen on, a flamegraph area the exact graph its export draws -
         * event type, default weighting, the whole recording - and an area not recorded links nothing.
         */
        @Test
        void eachAvailableAreaLinksItsPage() {
            Map<InvestigationArea, InvestigationOption> menu = menu(new Facts().disable(FeatureType.TRACES));

            String cpu = menu.get(InvestigationArea.CPU_HOTSPOTS).uiLink();
            assertTrue(cpu.contains("/profiles/" + PROFILE_ID + "/flamegraph-view?") && cpu.contains("eventType=" + EXECUTION)
                    && cpu.contains("startEpochMs=1000") && cpu.contains("endEpochMs=61000"), cpu);
            assertFalse(cpu.contains("useWeight"), cpu);
            assertTrue(menu.get(InvestigationArea.ALLOCATION).uiLink().contains("useWeight=true"));
            assertTrue(menu.get(InvestigationArea.GC_PAUSES).uiLink().endsWith("/profiles/" + PROFILE_ID + "/garbage-collection"));
            assertTrue(menu.get(InvestigationArea.REQUESTS_AND_CALLS).uiLink().endsWith("/technologies/http/overview"));
            assertTrue(menu.get(InvestigationArea.IO_WAITING).uiLink().endsWith("/socket-io"));
            assertNull(menu.get(InvestigationArea.TRACES).uiLink(), "not recorded, so no page to show");
            menu.values().stream()
                    .filter(option -> option.availability() != Availability.NOT_RECORDED)
                    .forEach(option -> UiLinkRoutes.assertResolves(option.uiLink()));
        }

        /** A traced profile's Traces area links the operations page, where its slowest requests open. */
        @Test
        void tracesLinkTheOperationsPage() {
            InvestigationOption traces = menu(new Facts()).get(InvestigationArea.TRACES);

            assertEquals(Availability.AVAILABLE, traces.availability());
            assertTrue(traces.uiLink().endsWith("/profiles/" + PROFILE_ID + "/traces/operations"), traces.uiLink());
            UiLinkRoutes.assertResolves(traces.uiLink());
        }

        @Test
        void aHeapDumpsAreasLinkTheirReports() {
            Map<InvestigationArea, InvestigationOption> menu = menu(new Facts().heapDump(
                    new HeapState(true, true, EVERY_STORED_REPORT)));

            assertTrue(menu.get(InvestigationArea.LEAK_SUSPECTS).uiLink().endsWith("/heap-dump/leak-suspects"));
            menu.values().forEach(option -> UiLinkRoutes.assertResolves(option.uiLink()));
        }

        /** An imported bytes dimension holds no JFR allocation events, so the types table has nothing. */
        @Test
        void anImportedAllocationOpensOnItsGraphAlone() {
            Map<InvestigationArea, InvestigationOption> menu = menu(new Facts().source(RecordingEventSource.PPROF));

            assertEquals(List.of("flamegraph_export"), tools(menu.get(InvestigationArea.ALLOCATION)));
        }

        /**
         * A traced request's time span by span and an endpoint's figures are two questions, so a
         * profile with both offers both, each under its own name.
         */
        @Test
        void tracesAndTheDashboardsAreTwoAreas() {
            Map<InvestigationArea, InvestigationOption> menu = menu(new Facts());

            assertEquals(Availability.AVAILABLE, menu.get(InvestigationArea.TRACES).availability());
            assertEquals("Traces", menu.get(InvestigationArea.TRACES).title());
            assertEquals(Availability.AVAILABLE, menu.get(InvestigationArea.REQUESTS_AND_CALLS).availability());
            assertFalse(tools(menu.get(InvestigationArea.REQUESTS_AND_CALLS)).contains("traces_overview"));
        }

        @Test
        void aProfileWithoutTracesNamesTheInstrumentationThatRecordsThem() {
            InvestigationOption traces = menu(new Facts().disable(FeatureType.TRACES)).get(InvestigationArea.TRACES);

            assertEquals(Availability.NOT_RECORDED, traces.availability());
            assertTrue(traces.gap().contains("no traces"), traces.gap());
            assertNotNull(traces.remedy());
            assertTrue(traces.nextTools().isEmpty());
        }

        /** The menu holds the four kinds of sample in Code, and no area of its own for when they landed. */
        @Test
        void codeIsTheFourSampleKinds() {
            List<InvestigationArea> code = Arrays.stream(InvestigationArea.values())
                    .filter(area -> area.group() == InvestigationGroup.CODE)
                    .toList();

            assertEquals(List.of(InvestigationArea.CPU_HOTSPOTS, InvestigationArea.WALL_CLOCK,
                    InvestigationArea.ALLOCATION, InvestigationArea.LOCK_CONTENTION), code);
        }

        @Test
        void anAreaWeighsAsItsHeaviestCall() {
            Map<InvestigationArea, InvestigationOption> menu = menu(new Facts());

            assertEquals(McpToolWeight.HEAVY, menu.get(InvestigationArea.CPU_HOTSPOTS).weight());
            assertEquals(McpToolWeight.HEAVY, menu.get(InvestigationArea.ALLOCATION).weight());
            assertEquals(McpToolWeight.HEAVY, menu.get(InvestigationArea.LOCK_CONTENTION).weight());
            assertEquals(McpToolWeight.MEDIUM, menu.get(InvestigationArea.GC_PAUSES).weight());
            assertEquals(McpToolWeight.MEDIUM, menu.get(InvestigationArea.CONTAINER_AND_FLAGS).weight());
        }

        @Test
        void aMissingSamplerNamesItsEventAndHowToRecordIt() {
            InvestigationOption cpu = menu(new Facts().samples(EXECUTION, 0)).get(InvestigationArea.CPU_HOTSPOTS);

            assertEquals(Availability.NOT_RECORDED, cpu.availability());
            assertTrue(cpu.gap().contains(EXECUTION) && cpu.gap().contains(CPU_TIME), cpu.gap());
            assertTrue(cpu.remedy().contains("event=cpu"), cpu.remedy());
            assertNull(cpu.weight());
            assertTrue(cpu.nextTools().isEmpty());
        }

        @Test
        void theCpuTimeSamplerIsGraphedWhenTheOlderOneIsMissing() {
            InvestigationOption cpu = menu(new Facts().samples(EXECUTION, 0).samples(CPU_TIME, 900))
                    .get(InvestigationArea.CPU_HOTSPOTS);

            assertEquals(CPU_TIME, Json.toTree(cpu.nextTools().getFirst().arguments()).get("eventType").asString());
        }

        @Test
        void requestsListEveryRecordedDashboardWithoutTraces() {
            List<McpNextTool> calls = menu(new Facts().disable(FeatureType.TRACES))
                    .get(InvestigationArea.REQUESTS_AND_CALLS).nextTools();

            assertEquals(List.of("http_overview", "grpc_overview", "http_overview", "grpc_overview",
                    "methodtracing_overview"), calls.stream().map(McpNextTool::tool).toList());
            assertEquals("SERVER", argument(calls.get(0), "direction"));
            assertEquals("SERVER", argument(calls.get(1), "direction"));
            assertEquals("CLIENT", argument(calls.get(2), "direction"));
            assertEquals("CLIENT", argument(calls.get(3), "direction"));
        }

        @Test
        void outboundCallsAloneMakeTheAreaAvailable() {
            InvestigationOption calls = menu(new Facts().disable(FeatureType.TRACES, FeatureType.HTTP_SERVER_DASHBOARD,
                    FeatureType.GRPC_SERVER_DASHBOARD, FeatureType.HTTP_CLIENT_DASHBOARD))
                    .get(InvestigationArea.REQUESTS_AND_CALLS);

            assertEquals(Availability.AVAILABLE, calls.availability());
            assertEquals(List.of("grpc_overview", "methodtracing_overview"), tools(calls));
        }

        @Test
        void requestsWithNoTracesAndNoDashboardAreAGap() {
            InvestigationOption requests = menu(new Facts().disable(FeatureType.TRACES, FeatureType.HTTP_SERVER_DASHBOARD,
                    FeatureType.GRPC_SERVER_DASHBOARD, FeatureType.HTTP_CLIENT_DASHBOARD,
                    FeatureType.GRPC_CLIENT_DASHBOARD, FeatureType.METHOD_TRACING_DASHBOARD))
                    .get(InvestigationArea.REQUESTS_AND_CALLS);

            assertEquals(Availability.NOT_RECORDED, requests.availability());
            assertTrue(requests.gap().contains("no served requests"), requests.gap());
        }

        @Test
        void exceptionsWithoutTheirEventsAreAGap() {
            InvestigationOption exceptions = menu(new Facts().noSection("exceptions")).get(InvestigationArea.EXCEPTIONS);

            assertEquals(Availability.NOT_RECORDED, exceptions.availability());
            assertTrue(exceptions.gap().contains("jvm_exceptions"), exceptions.gap());
        }

        @Test
        void aJvmSectionWithoutEventsIsAGap() {
            InvestigationOption jit = menu(new Facts().noSection("jit")).get(InvestigationArea.JIT);

            assertEquals(Availability.NOT_RECORDED, jit.availability());
            assertTrue(jit.gap().contains("jvm_jit"), jit.gap());
        }

        @Test
        void unrecordedIoAndLeakEventsAreGaps() {
            Map<InvestigationArea, InvestigationOption> menu = menu(new Facts().noEventTypes());

            assertEquals(Availability.NOT_RECORDED, menu.get(InvestigationArea.IO_WAITING).availability());
            assertEquals(Availability.NOT_RECORDED, menu.get(InvestigationArea.LEAK_CANDIDATES).availability());
        }

        @Test
        void rulesThatDidNotRunAreOfferedAsTheSlowCall() {
            McpNextTool rules = menu(new Facts().status(AutoAnalysisStatus.NOT_COMPUTED))
                    .get(InvestigationArea.RULE_FINDINGS).nextTools().getFirst();

            assertTrue(Json.toTree(rules.arguments()).get("compute").asBoolean());
        }

        @Test
        void rulesThatCannotRunAreAGap() {
            InvestigationOption rules = menu(new Facts().status(AutoAnalysisStatus.CANNOT_COMPUTE))
                    .get(InvestigationArea.RULE_FINDINGS);

            assertEquals(Availability.NOT_RECORDED, rules.availability());
        }

        @Test
        void anAreaWhoseFamilyIsWithheldIsLeftOut() {
            Set<String> families = new HashSet<>(EVERY_FAMILY.families());
            families.remove(AdvertisedFamilies.FLAMEGRAPH);

            Map<InvestigationArea, InvestigationOption> menu = menu(new Facts(), new AdvertisedFamilies(families));

            assertFalse(menu.containsKey(InvestigationArea.CPU_HOTSPOTS));
            assertFalse(menu.containsKey(InvestigationArea.WALL_CLOCK));
            assertTrue(menu.containsKey(InvestigationArea.ALLOCATION));
        }

        @Test
        void aPerfCountersSnapshotHasNoMenu() {
            assertTrue(InvestigationMenu.of(new Facts().source(RecordingEventSource.PERF_COUNTERS).build(), EVERY_FAMILY)
                    .isEmpty());
        }
    }

    @Nested
    class HeapDumps {

        @Test
        void aPreparedDumpOffersEveryHeapAreaAndNoRecordingArea() {
            Map<InvestigationArea, InvestigationOption> menu =
                    menu(new Facts().heapDump(new HeapState(true, true, EVERY_STORED_REPORT)));

            assertEquals(areasOf(MenuSubject.HEAP_DUMP), menu.keySet());
            menu.values().forEach(option -> assertEquals(Availability.AVAILABLE, option.availability(), option.title()));
            assertEquals(List.of("heap_getGCRootSummary", "heap_getThreads"), tools(menu.get(InvestigationArea.GC_ROOTS)));
            assertEquals(List.of("heap_getLeakSuspects"), tools(menu.get(InvestigationArea.LEAK_SUSPECTS)));
        }

        @Test
        void anUnindexedDumpOffersTheWholePreparationForEveryArea() {
            Map<InvestigationArea, InvestigationOption> menu =
                    menu(new Facts().heapDump(new HeapState(true, false, Set.of())));

            assertEquals(areasOf(MenuSubject.HEAP_DUMP), menu.keySet());
            for (InvestigationOption option : menu.values()) {
                assertEquals(Availability.NEEDS_PREPARATION, option.availability(), option.title());
                assertEquals(List.of("heap_prepare"), tools(option));
                assertFalse(Json.toTree(option.nextTools().getFirst().arguments()).has("report"), option.title());
                assertTrue(option.gap().contains("index"), option.gap());
            }
        }

        @Test
        void anIndexedDumpPreparesOnlyTheReportAnAreaReads() {
            Map<InvestigationArea, InvestigationOption> menu =
                    menu(new Facts().heapDump(new HeapState(true, true, Set.of(HeapReport.LEAKS))));

            assertEquals(Availability.AVAILABLE, menu.get(InvestigationArea.CLASS_HISTOGRAM).availability());
            assertEquals(Availability.AVAILABLE, menu.get(InvestigationArea.LEAK_SUSPECTS).availability());
            InvestigationOption strings = menu.get(InvestigationArea.STRING_WASTE);
            assertEquals(Availability.NEEDS_PREPARATION, strings.availability());
            assertEquals("STRINGS", argument(strings.nextTools().getFirst(), "report"));
            assertTrue(strings.remedy().contains("heap_prepare"), strings.remedy());
        }

        @Test
        void aDumpThatLostItsFileIsAGapEverywhere() {
            Map<InvestigationArea, InvestigationOption> menu =
                    menu(new Facts().heapDump(new HeapState(false, false, Set.of())));

            menu.values().forEach(option -> assertEquals(Availability.NOT_RECORDED, option.availability(), option.title()));
        }

        @Test
        void noHeapAreaWithoutTheHeapFamily() {
            Set<String> families = new HashSet<>(EVERY_FAMILY.families());
            families.remove(AdvertisedFamilies.HEAP);

            assertTrue(menu(new Facts().heapDump(new HeapState(true, true, EVERY_STORED_REPORT)),
                    new AdvertisedFamilies(families)).isEmpty());
        }

        @Test
        void refusesAnIndexWithoutTheDump() {
            assertThrows(IllegalArgumentException.class, () -> new HeapState(false, true, Set.of()));
        }
    }

    @Nested
    class Suggestions {

        @Test
        void aFiredExceptionsRulePointsAtTheExceptions() {
            assertTrue(menu(new Facts().flagged("exceptions", McpFinding.Severity.WARNING))
                    .get(InvestigationArea.EXCEPTIONS).suggested());
        }

        @Test
        void aWarningRulePointsAtItsTopicsArea() {
            InvestigationOption gc = menu(new Facts().flagged("garbage_collection", McpFinding.Severity.WARNING))
                    .get(InvestigationArea.GC_PAUSES);

            assertTrue(gc.suggested());
            assertTrue(gc.evidence().contains("garbage_collection rule"), gc.evidence());
        }

        @Test
        void anInformationalRuleSuggestsNothing() {
            InvestigationOption gc = menu(new Facts().flagged("garbage_collection", McpFinding.Severity.INFO))
                    .get(InvestigationArea.GC_PAUSES);

            assertFalse(gc.suggested());
            assertNull(gc.evidence());
        }

        /** Wall-clock samples count idle threads, so they are not weighed against CPU samples. */
        @Test
        void theDominantCpuSamplesAreSuggestedAndWallClockIsNotWeighedAgainstThem() {
            Map<InvestigationArea, InvestigationOption> menu = menu(new Facts().samples(EXECUTION, 8_000));

            assertTrue(menu.get(InvestigationArea.CPU_HOTSPOTS).suggested());
            assertTrue(menu.get(InvestigationArea.CPU_HOTSPOTS).evidence().contains("(8,000 of 8,800)"),
                    menu.get(InvestigationArea.CPU_HOTSPOTS).evidence());
            assertFalse(menu.get(InvestigationArea.WALL_CLOCK).suggested());
        }

        @Test
        void wallClockIsWeighedWhenThereAreNoCpuSamples() {
            Map<InvestigationArea, InvestigationOption> menu = menu(new Facts().samples(EXECUTION, 0));

            assertTrue(menu.get(InvestigationArea.WALL_CLOCK).suggested());
        }

        @Test
        void noClearMajoritySuggestsNothing() {
            Map<InvestigationArea, InvestigationOption> menu = menu(new Facts()
                    .samples(EXECUTION, 400).samples(ALLOCATION, 400).samples(MONITOR, 400));

            assertTrue(menu.values().stream().noneMatch(InvestigationOption::suggested));
        }

        @Test
        void atMostTwoAreSuggested() {
            Map<InvestigationArea, InvestigationOption> menu = menu(new Facts()
                    .samples(EXECUTION, 8_000)
                    .flagged("garbage_collection", McpFinding.Severity.CRITICAL)
                    .flagged("compilations", McpFinding.Severity.WARNING)
                    .flagged("threads", McpFinding.Severity.WARNING));

            assertEquals(Set.of(InvestigationArea.GC_PAUSES, InvestigationArea.JIT), menu.values().stream()
                    .filter(InvestigationOption::suggested)
                    .map(InvestigationOption::area)
                    .collect(Collectors.toSet()));
        }

        @Test
        void aRulePointingAtAnUnavailableAreaSuggestsNothingThere() {
            InvestigationOption jit = menu(new Facts().noSection("jit").flagged("compilations", McpFinding.Severity.WARNING))
                    .get(InvestigationArea.JIT);

            assertFalse(jit.suggested());
        }
    }

    @Nested
    class Wire {

        @Test
        void conformsToItsGeneratedSchema() {
            List<InvestigationOption> options = new ArrayList<>(
                    InvestigationMenu.of(new Facts().samples(EXECUTION, 0).build(), EVERY_FAMILY));
            options.addAll(InvestigationMenu.of(
                    new Facts().heapDump(new HeapState(true, true, Set.of(HeapReport.LEAKS))).build(), EVERY_FAMILY));
            for (InvestigationOption option : options) {
                McpSchemaConformance.assertConforms(Json.toTree(option), McpSchemaGenerator.schemaOf(InvestigationOption.class));
            }
        }

        @Test
        void refusesAnAvailableAreaWithoutCalls() {
            assertThrows(IllegalArgumentException.class,
                    () -> InvestigationOption.available(InvestigationArea.JIT, List.of(), null));
        }

        @Test
        void refusesASuggestionOfAnUnavailableArea() {
            InvestigationOption gap = InvestigationOption.notRecorded(InvestigationArea.JIT, "no jit", null);

            assertThrows(IllegalArgumentException.class, () -> gap.suggestedBy("a rule"));
        }
    }
    /** The recorded types with stacks that no area opens, listed beside the menu with their export. */
    @Nested
    class MoreFlamegraphsBesideTheMenu {

        private MoreFlamegraphs.Listing listing(Facts facts, AdvertisedFamilies advertised) {
            ProfileFacts built = facts.build();
            return MoreFlamegraphs.of(built, InvestigationMenu.of(built, advertised), advertised);
        }

        private List<String> listedTypes(MoreFlamegraphs.Listing listing) {
            return listing.listed().stream().map(MoreFlamegraphs.MoreFlamegraph::eventType).toList();
        }

        @Test
        void listsWhatNoAreaGraphsMostSamplesFirst() {
            MoreFlamegraphs.Listing listing = listing(new Facts()
                    .samples(CPU_TIME, 800)
                    .graphable("profiler.Malloc", 300)
                    .graphable("com.acme.CheckoutEvent", 40), EVERY_FAMILY);

            assertEquals(List.of(CPU_TIME, "profiler.Malloc", "com.acme.CheckoutEvent"), listedTypes(listing),
                    "the second CPU sampler, native allocation and a custom event; not the types the areas open");
            assertEquals(0, listing.omitted());
            McpNextTool export = listing.listed().getFirst().export();
            assertEquals("flamegraph_export", export.tool());
            assertEquals(CPU_TIME, argument(export, "eventType"));
            assertEquals(PROFILE_ID, argument(export, "profileId"));
            assertEquals(McpToolWeight.HEAVY, export.weight());
            String uiLink = listing.listed().getFirst().uiLink();
            assertTrue(uiLink.contains("eventType=" + CPU_TIME), uiLink);
            UiLinkRoutes.assertResolves(uiLink);
        }

        @Test
        void anUnrecordedTypeIsNotListed() {
            assertTrue(listing(new Facts().graphable("profiler.Malloc", 0), EVERY_FAMILY).listed().isEmpty());
        }

        @Test
        void listsAtMostTenAndCountsTheRest() {
            Facts facts = new Facts();
            for (int i = 0; i < MoreFlamegraphs.MAX_LISTED + 3; i++) {
                facts.graphable("com.acme.Event" + i, 100 + i);
            }

            MoreFlamegraphs.Listing listing = listing(facts, EVERY_FAMILY);

            assertEquals(MoreFlamegraphs.MAX_LISTED, listing.listed().size());
            assertEquals(3, listing.omitted());
            assertEquals("com.acme.Event12", listing.listed().getFirst().eventType());
        }

        @Test
        void nothingIsListedWhenFlamegraphsAreNotServed() {
            Set<String> families = new HashSet<>(EVERY_FAMILY.families());
            families.remove(AdvertisedFamilies.FLAMEGRAPH);

            MoreFlamegraphs.Listing listing = listing(new Facts().graphable("profiler.Malloc", 300),
                    new AdvertisedFamilies(families));

            assertEquals(MoreFlamegraphs.Listing.NONE, listing);
        }
    }
}
