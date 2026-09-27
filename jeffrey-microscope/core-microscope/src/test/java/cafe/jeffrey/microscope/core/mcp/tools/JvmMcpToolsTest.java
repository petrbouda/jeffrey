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
import cafe.jeffrey.microscope.core.mcp.tools.jvm.AutoAnalysisSection;
import cafe.jeffrey.microscope.core.mcp.tools.jvm.ConfigurationTab;
import cafe.jeffrey.microscope.core.mcp.tools.jvm.GcDetailPage;
import cafe.jeffrey.microscope.core.mcp.tools.jvm.GcDetailPages;
import cafe.jeffrey.microscope.core.mcp.tools.jvm.JvmSection;
import cafe.jeffrey.microscope.core.mcp.tools.jvm.SectionHeader;
import cafe.jeffrey.microscope.mcp.protocol.McpDescription;
import cafe.jeffrey.microscope.mcp.protocol.McpNullable;
import cafe.jeffrey.microscope.mcp.protocol.McpOutputSchema;
import cafe.jeffrey.microscope.mcp.protocol.McpSchemaGenerator;
import cafe.jeffrey.microscope.mcp.protocol.McpTaskState;
import cafe.jeffrey.microscope.mcp.protocol.McpTaskStatus;
import cafe.jeffrey.microscope.mcp.protocol.McpToolOutcome;
import cafe.jeffrey.microscope.mcp.protocol.McpToolResult;
import cafe.jeffrey.microscope.mcp.protocol.McpToolSpec;
import cafe.jeffrey.microscope.mcp.protocol.ToolExecutionException;
import cafe.jeffrey.microscope.mcp.protocol.testing.McpSchemaConformance;
import cafe.jeffrey.microscope.model.EventSummary;
import cafe.jeffrey.microscope.model.ProfileInfo;
import cafe.jeffrey.microscope.model.RecordingEventSource;
import cafe.jeffrey.microscope.model.ThreadInfo;
import cafe.jeffrey.microscope.model.Type;
import cafe.jeffrey.profile.common.analysis.AnalysisResult;
import cafe.jeffrey.profile.common.analysis.AutoAnalysisResult;
import cafe.jeffrey.profile.common.event.ContainerConfiguration;
import cafe.jeffrey.profile.common.event.GarbageCollectorType;
import cafe.jeffrey.profile.common.event.JITCompilationStats;
import cafe.jeffrey.profile.common.event.JITDeoptimizationMethodAggregate;
import cafe.jeffrey.profile.common.event.JITDeoptimizationStats;
import cafe.jeffrey.profile.common.operation.OperationState;
import cafe.jeffrey.profile.manager.AutoAnalysisManager;
import cafe.jeffrey.profile.manager.ClassLoadingManager;
import cafe.jeffrey.profile.manager.ContainerManager;
import cafe.jeffrey.profile.manager.ExceptionsManager;
import cafe.jeffrey.profile.manager.FlagsData;
import cafe.jeffrey.profile.manager.FlagsManager;
import cafe.jeffrey.profile.manager.FlamegraphManager;
import cafe.jeffrey.profile.manager.JITCompilationManager;
import cafe.jeffrey.profile.manager.JITDeoptimizationManager;
import cafe.jeffrey.profile.manager.ProfileConfigurationManager;
import cafe.jeffrey.profile.manager.ProfileManager;
import cafe.jeffrey.profile.manager.SecurityManager;
import cafe.jeffrey.profile.manager.SystemResourcesManager;
import cafe.jeffrey.profile.manager.VmOperationManager;
import cafe.jeffrey.profile.manager.gc.GarbageCollectionManager;
import cafe.jeffrey.profile.manager.memory.NativeMemoryManager;
import cafe.jeffrey.profile.manager.memory.NativeMemoryTrackingManager;
import cafe.jeffrey.profile.manager.model.classloading.ClassLoadActivity;
import cafe.jeffrey.profile.manager.model.classloading.ClassLoaderStat;
import cafe.jeffrey.profile.manager.model.classloading.ClassLoadingOverview;
import cafe.jeffrey.profile.manager.model.classloading.RedefinitionData;
import cafe.jeffrey.profile.manager.model.container.ContainerConfigurationData;
import cafe.jeffrey.profile.manager.model.container.ContainerCpuThrottlingData;
import cafe.jeffrey.profile.manager.model.exceptions.ExceptionsOverview;
import cafe.jeffrey.profile.manager.model.gc.GCEfficiency;
import cafe.jeffrey.profile.manager.model.gc.GCHeader;
import cafe.jeffrey.profile.manager.model.gc.GCOverviewData;
import cafe.jeffrey.profile.manager.model.gc.GCPauseDistribution;
import cafe.jeffrey.profile.manager.model.gc.ManualGCCalls;
import cafe.jeffrey.profile.manager.model.gc.g1.G1AnalysisData;
import cafe.jeffrey.profile.manager.model.gc.tuning.TenuringData;
import cafe.jeffrey.profile.manager.model.jit.CodeCacheData;
import cafe.jeffrey.profile.manager.model.nativememory.NativeMemoryOverview;
import cafe.jeffrey.profile.manager.model.nmt.NmtCategory;
import cafe.jeffrey.profile.manager.model.nmt.NmtOverview;
import cafe.jeffrey.profile.manager.model.security.SecurityData;
import cafe.jeffrey.profile.manager.model.system.LaunchedProcessInfo;
import cafe.jeffrey.profile.manager.model.system.SystemOverview;
import cafe.jeffrey.profile.manager.model.thread.ThreadCpuLoads;
import cafe.jeffrey.profile.manager.model.thread.ThreadStats;
import cafe.jeffrey.profile.manager.model.thread.ThreadWithCpuLoad;
import cafe.jeffrey.profile.manager.model.thread.dump.ParsedDump;
import cafe.jeffrey.profile.manager.model.thread.dump.ThreadDumpAnalysis;
import cafe.jeffrey.profile.manager.model.thread.dump.ThreadState;
import cafe.jeffrey.profile.manager.model.virtualthread.VirtualThreadData;
import cafe.jeffrey.profile.manager.model.vmoperation.SafepointLatencyData;
import cafe.jeffrey.profile.manager.model.vmoperation.SafepointOffender;
import cafe.jeffrey.profile.manager.model.vmoperation.VmOperationStat;
import cafe.jeffrey.profile.manager.model.vmoperation.VmOverview;
import cafe.jeffrey.profile.manager.thread.ThreadManager;
import cafe.jeffrey.profile.manager.thread.VirtualThreadManager;
import cafe.jeffrey.profile.mcp.McpNextToolConformance;
import cafe.jeffrey.profile.mcp.McpToolOutput;
import cafe.jeffrey.profile.mcp.ReflectiveToolset;
import cafe.jeffrey.profile.model.EventSummaryResult;
import cafe.jeffrey.provider.profile.api.FlagValueChange;
import cafe.jeffrey.provider.profile.api.JvmFlagDetail;
import cafe.jeffrey.shared.common.Json;
import cafe.jeffrey.shared.common.model.EventTypeName;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import tools.jackson.databind.JsonNode;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static cafe.jeffrey.microscope.core.mcp.AdvertisedFamiliesFixture.EVERY_FAMILY;
import static cafe.jeffrey.microscope.core.mcp.tools.McpCallContexts.SHORT_TASK_WAIT;
import static cafe.jeffrey.microscope.core.mcp.tools.McpCallContexts.TASKS;
import static cafe.jeffrey.microscope.mcp.protocol.McpCallContext.RESOURCE_READ;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeout;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class JvmMcpToolsTest {

    private static final long MILLI_IN_NANOS = 1_000_000L;

    /** The recording's start, far from the epoch so an offset passed through unconverted is caught. */
    private static final Instant START = Instant.parse("2026-03-01T12:00:00Z");
    private static final long START_MS = START.toEpochMilli();

    private static final Clock CLOCK = Clock.systemUTC();

    private static final String PROFILE_ID = "p-1";
    private static final String UI_BASE = "http://localhost/profiles/p-1/";

    /**
     * The tools build a UI link off the incoming request, the way ProfileMcpTools#link does.
     */
    @BeforeEach
    void bindRequest() {
        RequestContextHolder.setRequestAttributes(
                new ServletRequestAttributes(new MockHttpServletRequest()));
    }

    @AfterEach
    void unbindRequest() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Mock
    ProfileManager profileManager;

    @Mock
    FlamegraphManager flamegraphManager;

    @Mock
    GarbageCollectionManager gcManager;

    @Mock
    VmOperationManager vmOperationManager;

    @Mock
    AutoAnalysisManager autoAnalysisManager;

    @Mock
    ExceptionsManager exceptionsManager;

    @Mock
    ClassLoadingManager classLoadingManager;

    @Mock
    SystemResourcesManager systemResourcesManager;

    @Mock
    SecurityManager securityManager;

    @Mock
    ProfileConfigurationManager configurationManager;

    @Mock
    ThreadManager threadManager;

    @Mock
    VirtualThreadManager virtualThreadManager;

    @Mock
    JITCompilationManager jitCompilationManager;

    @Mock
    JITDeoptimizationManager jitDeoptimizationManager;

    @Mock
    NativeMemoryManager nativeMemoryManager;

    @Mock
    NativeMemoryTrackingManager nativeMemoryTrackingManager;

    @Mock
    ContainerManager containerManager;

    @Mock
    FlagsManager flagsManager;

    private final McpOperationRegistry operations = new McpOperationRegistry(CLOCK);

    private final AtomicInteger leases = new AtomicInteger();

    private Duration waitBudget = Duration.ofSeconds(5);

    /**
     * The sample types this recording can graph, which decide the event type a next call names: the
     * execution sampler and the allocation sampler unless a test says otherwise.
     */
    private List<String> sampled = List.of(EventTypeName.EXECUTION_SAMPLE, EventTypeName.OBJECT_ALLOCATION_SAMPLE);

    private OperationAnswers answers = ToolFixtures.answers();

    private JvmMcpTools tools() {
        return tools(EVERY_FAMILY);
    }

    private JvmMcpTools tools(AdvertisedFamilies advertised) {
        when(profileManager.info()).thenReturn(new ProfileInfo(
                PROFILE_ID, "project-1", "workspace-1", "Profile", RecordingEventSource.JDK,
                START, START.plusSeconds(60), START, true, false, "recording-1"));
        BoundedOperation<AutoAnalysisSection.AutoAnalysisDashboard> autoAnalysis = new BoundedOperation<>(
                OperationKind.JVM_AUTO_ANALYSIS,
                ToolFixtures.jobs(waitBudget, BoundedJobs.COMPLETED_RETENTION, CLOCK),
                operations, answers);
        return new JvmMcpTools(profileManager, autoAnalysis, () -> {
            leases.incrementAndGet();
            return leases::decrementAndGet;
        }, advertised);
    }

    /**
     * A recording holds only what the profiler was told to capture, and every section is gated on
     * that, so each test says which event types this recording carries.
     */
    private void recorded(String... eventTypes) {
        when(profileManager.flamegraphManager()).thenReturn(flamegraphManager);
        when(profileManager.gcManager()).thenReturn(gcManager);
        when(profileManager.vmOperationManager()).thenReturn(vmOperationManager);
        when(profileManager.autoAnalysisManager()).thenReturn(autoAnalysisManager);
        when(profileManager.profileConfigurationManager()).thenReturn(configurationManager);
        when(profileManager.exceptionsManager()).thenReturn(exceptionsManager);
        when(profileManager.classLoadingManager()).thenReturn(classLoadingManager);
        when(profileManager.systemResourcesManager()).thenReturn(systemResourcesManager);
        when(profileManager.securityManager()).thenReturn(securityManager);
        when(profileManager.threadManager()).thenReturn(threadManager);
        when(profileManager.virtualThreadManager()).thenReturn(virtualThreadManager);
        when(profileManager.jitCompilationManager()).thenReturn(jitCompilationManager);
        when(profileManager.jitDeoptimizationManager()).thenReturn(jitDeoptimizationManager);
        when(profileManager.nativeMemoryManager()).thenReturn(nativeMemoryManager);
        when(profileManager.nativeMemoryTrackingManager()).thenReturn(nativeMemoryTrackingManager);
        when(profileManager.containerManager()).thenReturn(containerManager);
        when(profileManager.flagsManager()).thenReturn(flagsManager);

        when(flamegraphManager.allEventSummaries()).thenReturn(
                List.of(eventTypes).stream()
                        .map(JvmMcpToolsTest::summary)
                        .toList());
        when(flamegraphManager.eventSummaries()).thenReturn(
                sampled.stream().map(JvmMcpToolsTest::summary).toList());
    }

    /** A recording whose graphable samples are only these types; call before {@link #recorded}. */
    private void sampledOnly(String... eventTypes) {
        sampled = List.of(eventTypes);
    }

    private static EventSummaryResult summary(String eventType) {
        return new EventSummaryResult(new EventSummary(
                eventType, eventType, null, null, 1, 0, false, false, List.of(), null, null));
    }

    /** The answer's structured content, checked against the schema the tool advertises and its link. */
    private static JsonNode conforming(String method, McpToolOutcome outcome) {
        McpToolResult result = assertInstanceOf(McpToolResult.class, outcome);
        JsonNode structured = result.structuredContent();
        McpSchemaConformance.assertConforms(structured, schemaOf(method));
        UiLinkRoutes.assertResolves(structured.get("uiLink").asString());
        assertEquals(Json.toString(structured), result.text(), "the text is the record's own JSON");
        McpNextToolConformance.assertFollowable(structured, reachable());
        return structured;
    }

    private static JsonNode schemaOf(String method) {
        Method tool = Arrays.stream(JvmMcpTools.class.getMethods())
                .filter(candidate -> candidate.getName().equals(method))
                .findFirst()
                .orElseThrow();
        return McpSchemaGenerator.schemaOf(tool.getAnnotation(McpOutputSchema.class).value());
    }

    /** Every tool the family routes to, as the installation would advertise it. */
    private static List<McpToolSpec> reachable() {
        return CatalogueSpecs.of(
                CatalogueSpecs.profileScoped(JvmMcpTools.class, AdvertisedFamilies.JVM),
                CatalogueSpecs.profileScoped(FlamegraphMcpTools.class, AdvertisedFamilies.FLAMEGRAPH),
                CatalogueSpecs.profileScoped(ProfileMcpTools.class, AdvertisedFamilies.PROFILES),
                CatalogueSpecs.profileScoped(BlockingMcpTools.class, AdvertisedFamilies.BLOCKING),
                CatalogueSpecs.profileScoped(IoMcpTools.class, AdvertisedFamilies.IO),
                CatalogueSpecs.profileScoped(TracesMcpTools.class, AdvertisedFamilies.TRACES),
                CatalogueSpecs.profileScoped(TimelineMcpTools.class, AdvertisedFamilies.TIMELINE),
                CatalogueSpecs.profileScoped(MemoryMcpTools.class, AdvertisedFamilies.MEMORY),
                CatalogueSpecs.profileScoped(HeapDumpMcpTools.class, AdvertisedFamilies.HEAP),
                CatalogueSpecs.served(new OperationsMcpTools(new McpOperationRegistry(CLOCK), kind -> true),
                        AdvertisedFamilies.OPERATIONS));
    }

    private static Set<String> nextTools(JsonNode structured) {
        Set<String> tools = new TreeSet<>();
        structured.get("followUp").get("nextTools").forEach(call -> tools.add(call.get("tool").asString()));
        return tools;
    }

    private static JsonNode call(JsonNode structured, String tool) {
        for (JsonNode call : structured.get("followUp").get("nextTools")) {
            if (call.get("tool").asString().equals(tool)) {
                return call.get("arguments");
            }
        }
        throw new AssertionError("no call to " + tool + " in " + structured);
    }

    private static String guidance(JsonNode structured) {
        return structured.get("followUp").get("guidance").toString();
    }

    private static AutoAnalysisResult longGcPauses() {
        return new AutoAnalysisResult(
                "Long GC Pauses", AnalysisResult.Severity.WARNING,
                "Pauses above 100ms were observed", "GC pauses are long",
                "Consider a larger young generation", "78", "garbage_collection");
    }

    @Nested
    class Sections {

        @Test
        void listsEverySectionWithItsToolAndWhetherTheRecordingCarriesIt() {
            recorded(EventTypeName.GARBAGE_COLLECTION);

            JsonNode out = conforming("sections", tools().sections());

            Map<String, JsonNode> byId = new LinkedHashMap<>();
            out.get("sections").forEach(section -> byId.put(section.get("id").asString(), section));
            assertEquals("jvm_gc", byId.get("gc").get("tool").asString());
            assertTrue(byId.get("gc").get("available").asBoolean());
            assertFalse(byId.get("safepoints").get("available").asBoolean());
            assertTrue(byId.get("safepoints").get("eventTypes").toString().contains(EventTypeName.SAFEPOINT_LATENCY));
            assertEquals(PROFILE_ID, out.get("profileId").asString());
            assertEquals(UI_BASE + "event-types", out.get("uiLink").asString());
        }

        /**
         * Auto analysis runs over the recording file rather than over parsed events, so no particular
         * event has to be present for it to be answerable.
         */
        @Test
        void reportsAutoAnalysisAsAvailableWithoutAnyEvents() {
            recorded();

            JsonNode out = conforming("sections", tools().sections());

            assertTrue(out.get("sections").get(0).get("available").asBoolean(), out.toString());
            assertEquals("autoAnalysis", out.get("sections").get(0).get("id").asString());
        }

        /**
         * Each row already names its tool, so the follow-up does not repeat them: it names the one
         * section every reader can start from, and says the rest take only the profile.
         */
        @Test
        void startsTheReaderAtTheAutoAnalysisRatherThanRepeatingEveryRow() {
            recorded(EventTypeName.GARBAGE_COLLECTION);

            JsonNode out = conforming("sections", tools().sections());

            assertEquals(Set.of("jvm_autoAnalysis"), nextTools(out));
            assertEquals(PROFILE_ID, call(out, "jvm_autoAnalysis").get("profileId").asString());
            assertTrue(guidance(out).contains("sections[].tool"), guidance(out));
        }
    }

    @Nested
    class NotRecorded {

        /**
         * A dashboard rendered from events the profiler never captured is a page of zeroes, which
         * reads like a finding. The status answer has to come before the manager is touched.
         */
        @Test
        void aSectionTheRecordingHasNoEventsForIsAStatusAndIsNeverRendered() {
            recorded(EventTypeName.EXECUTION_SAMPLE);

            JsonNode out = conforming("gc", tools().gc());

            assertEquals("NOT_RECORDED", out.get("status").asString());
            assertTrue(out.get("reason").asString().contains("no data for the Garbage Collection section"));
            assertTrue(out.get("reason").asString().contains(EventTypeName.GARBAGE_COLLECTION));
            assertTrue(out.get("dashboard").isNull(), out.toString());
            assertEquals(UI_BASE + "garbage-collection", out.get("uiLink").asString());
            verify(gcManager, never()).overviewData();
        }

        @Test
        void pointsTheReaderAtTheDiscoveryToolAndTheEventsToRecord() {
            recorded();

            JsonNode out = conforming("safepoints", tools().safepoints());

            assertEquals(Set.of("jvm_sections"), nextTools(out));
            assertTrue(guidance(out).contains(EventTypeName.SAFEPOINT_LATENCY), guidance(out));
            assertEquals(1, McpNextToolConformance.assertFollowable(out, reachable()));
        }

        /** Every section answers the same status shape when its events were not recorded. */
        @ParameterizedTest
        @ValueSource(strings = {"gc", "safepoints", "jit", "threads", "nativeMemory", "classLoading",
                "exceptions", "system", "security", "container"})
        void everySectionAnswersTheStatusInItsOwnSchema(String method) throws Exception {
            recorded();
            JvmMcpTools tools = tools();

            JsonNode out = conforming(method, (McpToolOutcome) JvmMcpTools.class.getMethod(method).invoke(tools));

            assertEquals("NOT_RECORDED", out.get("status").asString());
            assertEquals(method, out.get("section").asString());
            assertTrue(out.get("dashboard").isNull(), out.toString());
        }

        @Test
        void theGcPagesAndTheConfigurationAnswerTheStatusToo() {
            recorded();

            JsonNode page = conforming("gcDetail", tools().gcDetail(GcDetailPage.G1));
            JsonNode configuration = conforming("configuration", tools().configuration(ConfigurationTab.OS_INFORMATION));

            assertEquals("NOT_RECORDED", page.get("status").asString());
            assertEquals(UI_BASE + "garbage-collection/g1", page.get("uiLink").asString());
            assertEquals("NOT_RECORDED", configuration.get("status").asString());
            verify(gcManager, never()).g1Analysis();
        }
    }

    @Nested
    class Gc {

        /**
         * The whole reason this is a tool: pause figures come from sumOfPauses and longestPause, the
         * fields a hand-written query passes over in favour of the event's duration.
         */
        @Test
        void reportsThePauseBudgetInMilliseconds() {
            recorded(EventTypeName.GARBAGE_COLLECTION);
            when(gcManager.garbageCollectorType()).thenReturn(Optional.of(GarbageCollectorType.G1));
            when(gcManager.overviewData()).thenReturn(overview());

            JsonNode out = conforming("gc", tools().gc());

            JsonNode dashboard = out.get("dashboard");
            assertEquals("OK", out.get("status").asString());
            assertTrue(out.get("reason").isNull());
            assertEquals("G1", dashboard.get("collector").asString());
            assertEquals(250.0, dashboard.get("pauseBudget").get("totalPauseMs").asDouble());
            assertEquals(40.0, dashboard.get("pauseBudget").get("longestPauseMs").asDouble());
            assertEquals(12, dashboard.get("pauseBudget").get("collections").asInt());
            assertEquals(99.0, dashboard.get("pauseBudget").get("throughputPct").asDouble());
            assertEquals(3, dashboard.get("systemGcCalls").asInt());
            assertEquals(1, dashboard.get("diagnosticGcCalls").asInt());
        }

        /**
         * No GC event names the code producing the garbage, so the answer hands on the allocation
         * flamegraph as a call, weighted by bytes, beside the pauses that are not collections.
         */
        @Test
        void handsOnTheAllocationFlamegraphAndTheOtherPauses() {
            recorded(EventTypeName.GARBAGE_COLLECTION);
            when(gcManager.garbageCollectorType()).thenReturn(Optional.of(GarbageCollectorType.G1));
            when(gcManager.overviewData()).thenReturn(overview());

            JsonNode out = conforming("gc", tools().gc());

            assertEquals(Set.of("flamegraph_export", "jvm_safepoints", "profiles_features"), nextTools(out));
            JsonNode export = call(out, "flamegraph_export");
            assertEquals(EventTypeName.OBJECT_ALLOCATION_SAMPLE, export.get("eventType").asString());
            assertTrue(export.get("useWeight").asBoolean());
            assertEquals(3, McpNextToolConformance.assertFollowable(out, reachable()));
        }

        /** A recording that allocated only through the TLAB events graphs those, not the sampler it lacks. */
        @Test
        void graphsTheAllocationTypeThisProfileRecorded() {
            sampledOnly(EventTypeName.EXECUTION_SAMPLE, EventTypeName.OBJECT_ALLOCATION_IN_NEW_TLAB);
            recorded(EventTypeName.GARBAGE_COLLECTION);
            when(gcManager.garbageCollectorType()).thenReturn(Optional.of(GarbageCollectorType.G1));
            when(gcManager.overviewData()).thenReturn(overview());

            JsonNode out = conforming("gc", tools().gc());

            assertEquals(EventTypeName.OBJECT_ALLOCATION_IN_NEW_TLAB,
                    call(out, "flamegraph_export").get("eventType").asString());
        }

        @Test
        void aProfileWithoutAllocationSamplesGetsGuidanceInsteadOfAGraph() {
            sampledOnly(EventTypeName.EXECUTION_SAMPLE);
            recorded(EventTypeName.GARBAGE_COLLECTION);
            when(gcManager.garbageCollectorType()).thenReturn(Optional.of(GarbageCollectorType.G1));
            when(gcManager.overviewData()).thenReturn(overview());

            JsonNode out = conforming("gc", tools().gc());

            assertEquals(Set.of("jvm_safepoints", "profiles_features"), nextTools(out));
            assertTrue(guidance(out).contains("No allocation samples"), guidance(out));
        }

        /**
         * Under a trimmed family list the section keeps its own routing and drops the calls that
         * would send the reader to a family this installation does not serve.
         */
        @Test
        void leavesOutTheCallsToFamiliesThatAreNotAdvertised() {
            recorded(EventTypeName.GARBAGE_COLLECTION);
            when(gcManager.garbageCollectorType()).thenReturn(Optional.of(GarbageCollectorType.G1));
            when(gcManager.overviewData()).thenReturn(overview());

            JsonNode out = conforming("gc", tools(new AdvertisedFamilies(Set.of("jvm", "operations"))).gc());

            assertEquals(Set.of("jvm_safepoints"), nextTools(out));
        }

        static GCOverviewData overview() {
            GCHeader header = new GCHeader(
                    12, 9, 2, 1,
                    40 * MILLI_IN_NANOS, 30 * MILLI_IN_NANOS, 35 * MILLI_IN_NANOS,
                    2048, 170, BigDecimal.valueOf(99), BigDecimal.ONE,
                    250 * MILLI_IN_NANOS, BigDecimal.TEN,
                    new ManualGCCalls(0, 3, 1));

            return new GCOverviewData(
                    header,
                    List.of(),
                    new GCPauseDistribution(List.of()),
                    new GCEfficiency(0, 0, BigDecimal.valueOf(99), BigDecimal.ONE),
                    List.of(),
                    List.of());
        }
    }

    @Nested
    class Safepoints {

        /**
         * The thread state is what turns a slow thread into a diagnosis, so it has to survive into
         * the rendered dashboard rather than being aggregated away.
         */
        @Test
        void namesTheOffendingThreadsWithTheStateTheyWereIn() {
            recorded(EventTypeName.SAFEPOINT_LATENCY);
            when(vmOperationManager.overview()).thenReturn(
                    new VmOverview(4, 12 * MILLI_IN_NANOS, 9 * MILLI_IN_NANOS, "G1CollectForAllocation",
                            true, true, true));
            when(vmOperationManager.vmOperations()).thenReturn(List.of(
                    new VmOperationStat("G1CollectForAllocation", 4, 12 * MILLI_IN_NANOS,
                            9 * MILLI_IN_NANOS, true, true)));
            when(vmOperationManager.safepointOffenders()).thenReturn(new SafepointLatencyData(
                    List.of(new SafepointOffender("worker-3", "_thread_in_native", 4,
                            8 * MILLI_IN_NANOS, 7 * MILLI_IN_NANOS, 20 * MILLI_IN_NANOS)),
                    1, 8 * MILLI_IN_NANOS, 20 * MILLI_IN_NANOS));

            JsonNode out = conforming("safepoints", tools().safepoints());

            JsonNode offender = out.get("dashboard").get("offenders").get(0);
            assertEquals("worker-3", offender.get("threadName").asString());
            assertEquals("_thread_in_native", offender.get("threadState").asString());
            assertEquals(12.0, out.get("dashboard").get("totalSafepointPauseMs").asDouble());
            assertEquals(Set.of("jvm_gc"), nextTools(out));
            assertTrue(guidance(out).contains("_thread_in_Java"), guidance(out));
            assertEquals(UI_BASE + "vm-operations", out.get("uiLink").asString());
        }
    }

    @Nested
    class Jit {

        /** The compiler's times come in nanoseconds and its sizes in bytes; the names now say so. */
        @Test
        void carriesTheCompilerTotalsWithTheirUnits() {
            recorded(EventTypeName.COMPILATION);
            when(jitCompilationManager.statistics()).thenReturn(
                    new JITCompilationStats(100, 1, 2, 3, 97, 4096, 2048, 5 * MILLI_IN_NANOS, 90 * MILLI_IN_NANOS, 0));
            when(jitCompilationManager.codeCache()).thenReturn(new CodeCacheData(List.of(), 0));

            JsonNode out = conforming("jit", tools().jit());

            JsonNode statistics = out.get("dashboard").get("statistics");
            assertEquals(5 * MILLI_IN_NANOS, statistics.get("peakTimeSpentNanos").asLong());
            assertEquals(4096, statistics.get("nmethodsSizeBytes").asLong());
            assertTrue(out.get("dashboard").get("deoptimization").get("statistics").isNull(), out.toString());
            assertEquals(0, out.get("dashboard").get("deoptimization").get("omittedMethods").asInt());
            assertEquals(Set.of("flamegraph_export"), nextTools(out));
            assertEquals(1, McpNextToolConformance.assertFollowable(out, reachable()));
        }

        /**
         * The compiler threads' share of the CPU is graphed on the sample type this recording holds: a
         * JDK 25 recording made with the CPU-time sampler carries no execution samples.
         */
        @Test
        void graphsTheOnCpuTypeThisProfileRecorded() {
            sampledOnly(EventTypeName.CPU_TIME_SAMPLE);
            recorded(EventTypeName.COMPILATION);
            when(jitCompilationManager.codeCache()).thenReturn(new CodeCacheData(List.of(), 0));

            JsonNode out = conforming("jit", tools().jit());

            assertEquals(EventTypeName.CPU_TIME_SAMPLE, call(out, "flamegraph_export").get("eventType").asString());
        }

        /** With no on-CPU samples at all, no call draws an empty graph: a guidance line says why. */
        @Test
        void aProfileWithoutOnCpuSamplesGetsGuidanceInsteadOfAGraph() {
            sampledOnly();
            recorded(EventTypeName.COMPILATION);
            when(jitCompilationManager.codeCache()).thenReturn(new CodeCacheData(List.of(), 0));

            JsonNode out = conforming("jit", tools().jit());

            assertEquals(Set.of(), nextTools(out));
            assertTrue(guidance(out).contains("flamegraph_list"), guidance(out));
        }

        /** The deoptimised methods are the worst fifteen; the other distinct methods are counted. */
        @Test
        void countsTheDeoptimisedMethodsBeyondTheWorst() {
            recorded(EventTypeName.COMPILATION);
            when(jitCompilationManager.codeCache()).thenReturn(new CodeCacheData(List.of(), 0));
            when(jitDeoptimizationManager.statistics()).thenReturn(
                    new JITDeoptimizationStats(90, 40, 3, "unstable_if", 50, "Foo.bar", 20, 10, 80, 60_000));
            List<JITDeoptimizationMethodAggregate> methods = new ArrayList<>();
            for (int i = 0; i < 15; i++) {
                methods.add(new JITDeoptimizationMethodAggregate("Foo.m" + i, 2, 1, "unstable_if", 2, List.of(), 0, 0));
            }
            when(jitDeoptimizationManager.topMethods(15)).thenReturn(methods);

            JsonNode out = conforming("jit", tools().jit());

            assertEquals(25, out.get("dashboard").get("deoptimization").get("omittedMethods").asInt());
        }

        /**
         * Without the statistics event the number of distinct deoptimised methods is unknown, so a list
         * at its cap cannot say how many it left out: that is null, never a false zero.
         */
        @Test
        void anUnknownRemainderIsNullNotZero() {
            recorded(EventTypeName.COMPILATION);
            when(jitCompilationManager.codeCache()).thenReturn(new CodeCacheData(List.of(), 0));
            List<JITDeoptimizationMethodAggregate> methods = new ArrayList<>();
            for (int i = 0; i < 15; i++) {
                methods.add(new JITDeoptimizationMethodAggregate("Foo.m" + i, 2, 1, "unstable_if", 2, List.of(), 0, 0));
            }
            when(jitDeoptimizationManager.topMethods(15)).thenReturn(methods);

            JsonNode out = conforming("jit", tools().jit());

            assertTrue(out.get("dashboard").get("deoptimization").get("omittedMethods").isNull(), out.toString());
        }

        /** A list short of its cap left nothing out, whether or not the statistics were recorded. */
        @Test
        void aListShortOfItsCapLeftNothingOut() {
            recorded(EventTypeName.COMPILATION);
            when(jitCompilationManager.codeCache()).thenReturn(new CodeCacheData(List.of(), 0));
            when(jitDeoptimizationManager.topMethods(15)).thenReturn(List.of(
                    new JITDeoptimizationMethodAggregate("Foo.m", 2, 1, "unstable_if", 2, List.of(), 0, 0)));

            JsonNode out = conforming("jit", tools().jit());

            assertEquals(0, out.get("dashboard").get("deoptimization").get("omittedMethods").asInt(), out.toString());
        }

        /** A recording without compiler statistics still has a dashboard, with the totals absent. */
        @Test
        void aRecordingWithoutCompilerStatisticsSaysSoWithNull() {
            recorded(EventTypeName.COMPILATION);
            when(jitCompilationManager.codeCache()).thenReturn(new CodeCacheData(List.of(), 0));

            JsonNode out = conforming("jit", tools().jit());

            assertTrue(out.get("dashboard").get("statistics").isNull(), out.toString());
        }
    }

    @Nested
    class Threads {

        private void threads(VirtualThreadData.VtHeader virtualHeader) {
            recorded(EventTypeName.THREAD_CPU_LOAD);
            when(threadManager.threadStatistics()).thenReturn(new ThreadStats(40, 20, 1, 2, 3));
            when(threadManager.threadCpuLoads(15)).thenReturn(new ThreadCpuLoads(
                    List.of(new ThreadWithCpuLoad(0, new ThreadInfo(1, 1, "worker-1"), new BigDecimal("0.25"))),
                    List.of()));
            when(threadManager.resolveAllocationType()).thenReturn(Type.THREAD_ALLOCATION_STATISTICS);
            when(virtualThreadManager.virtualThreadData()).thenReturn(new VirtualThreadData(
                    virtualHeader, null, List.of(), List.of(), List.of(), List.of(), null));
        }

        @Test
        void reportsTheCpuLoadAsANumberAndNoVirtualThreadsWithoutTheirEvents() {
            threads(null);

            JsonNode out = conforming("threads", tools().threads());

            assertEquals(0.25, out.get("dashboard").get("topUserCpu").get(0).get("cpuLoad").asDouble());
            assertTrue(out.get("dashboard").get("virtualThreads").isNull(), out.toString());
            assertEquals(Set.of("flamegraph_export"), nextTools(out));
            assertTrue(call(out, "flamegraph_export").get("threadMode").asBoolean());
            assertFalse(guidance(out).contains("pinning reason"), guidance(out));
        }

        @Test
        void theThreadFlamegraphUsesTheOnCpuTypeThisProfileRecorded() {
            sampledOnly(EventTypeName.WALL_CLOCK_SAMPLE);
            threads(null);

            JsonNode out = conforming("threads", tools().threads());

            assertEquals(EventTypeName.WALL_CLOCK_SAMPLE, call(out, "flamegraph_export").get("eventType").asString());
        }

        @Test
        void noOnCpuSamplesMeansNoThreadFlamegraph() {
            sampledOnly();
            threads(null);

            JsonNode out = conforming("threads", tools().threads());

            assertEquals(Set.of(), nextTools(out));
            assertTrue(guidance(out).contains("flamegraph_list"), guidance(out));
        }

        /** The pinning advice belongs only where a carrier was actually pinned. */
        @Test
        void addsThePinningAdviceWhenACarrierWasPinned() {
            threads(new VirtualThreadData.VtHeader(3, 3 * MILLI_IN_NANOS, 2 * MILLI_IN_NANOS, 0, 10, 10, 5));

            JsonNode out = conforming("threads", tools().threads());

            assertEquals(3, out.get("dashboard").get("virtualThreads").get("pinningCount").asInt());
            assertTrue(guidance(out).contains("pinning reason"), guidance(out));
            assertEquals(UI_BASE + "thread-statistics", out.get("uiLink").asString());
        }
    }

    @Nested
    class NativeMemory {

        @Test
        void carriesNoTrackingWithoutNativeMemoryTracking() {
            recorded(EventTypeName.RESIDENT_SET_SIZE);
            when(nativeMemoryManager.overview()).thenReturn(new NativeMemoryOverview(4096, 2048, 1024, 1, 64, 128, 3));
            when(nativeMemoryTrackingManager.overview()).thenReturn(NmtOverview.empty());

            JsonNode out = conforming("nativeMemory", tools().nativeMemory());

            assertTrue(out.get("dashboard").get("tracking").isNull(), out.toString());
            assertEquals(4096, out.get("dashboard").get("process").get("peakRssBytes").asLong());
            assertEquals(Set.of("flamegraph_list"), nextTools(out));
            assertEquals(1, McpNextToolConformance.assertFollowable(out, reachable()));
        }

        /** NMT can report totals with no category breakdown, so the largest category is honestly null. */
        @Test
        void reportsTrackingWithoutCategoriesWithANullLargestCategory() {
            recorded(EventTypeName.RESIDENT_SET_SIZE);
            when(nativeMemoryManager.overview()).thenReturn(new NativeMemoryOverview(4096, 2048, 1024, 1, 64, 128, 3));
            when(nativeMemoryTrackingManager.overview()).thenReturn(
                    new NmtOverview(true, 2048, 4096, 2048, null, 0, 0, 512));

            JsonNode out = conforming("nativeMemory", tools().nativeMemory());

            assertTrue(out.get("dashboard").get("tracking").get("largestCategory").isNull(), out.toString());
            assertEquals(0, out.get("dashboard").get("omittedCategories").asInt());
        }

        /** The categories are the largest twenty; the rest are counted rather than dropped silently. */
        @Test
        void countsTheCategoriesBeyondTheLargest() {
            recorded(EventTypeName.RESIDENT_SET_SIZE);
            when(nativeMemoryManager.overview()).thenReturn(new NativeMemoryOverview(4096, 2048, 1024, 1, 64, 128, 3));
            when(nativeMemoryTrackingManager.overview()).thenReturn(
                    new NmtOverview(true, 2048, 4096, 2048, "Java Heap", 1024, 25, 512));
            List<NmtCategory> categories = new ArrayList<>();
            for (int i = 0; i < 25; i++) {
                categories.add(new NmtCategory("Category " + i, 100, 50, 40, 10));
            }
            when(nativeMemoryTrackingManager.categories()).thenReturn(categories);

            JsonNode out = conforming("nativeMemory", tools().nativeMemory());

            assertEquals(20, out.get("dashboard").get("categories").size());
            assertEquals(5, out.get("dashboard").get("omittedCategories").asInt());
        }
    }

    @Nested
    class ClassLoading {

        /** The bootstrap loader has no parent, which the answer says with null rather than failing its schema. */
        @Test
        void reportsALoaderWithoutAParent() {
            recorded(EventTypeName.CLASS_LOAD);
            when(classLoadingManager.overview()).thenReturn(
                    new ClassLoadingOverview(100, 120, 20, 2, 4096, 3, false, false));
            when(classLoadingManager.classLoadActivity()).thenReturn(ClassLoadActivity.empty());
            when(classLoadingManager.redefinitions()).thenReturn(new RedefinitionData(List.of(), List.of()));
            when(classLoadingManager.classLoaders()).thenReturn(List.of(
                    new ClassLoaderStat("bootstrap", null, 80, 2048, 0, 1, 0)));

            JsonNode out = conforming("classLoading", tools().classLoading());

            assertTrue(out.get("dashboard").get("loaders").get(0).get("parentName").isNull(), out.toString());
            assertEquals(0, out.get("dashboard").get("omittedLoaders").asInt());
            assertEquals(Set.of("heap_getClassLoaderLeakChains", "timeline_hotWindows"), nextTools(out));
            assertEquals(2, McpNextToolConformance.assertFollowable(out, reachable()));
            assertEquals(UI_BASE + "class-loading", out.get("uiLink").asString());
        }

        /** The start-up window is found on the sample type the profile recorded, or not offered at all. */
        @Test
        void theStartUpWindowIsFoundOnTheRecordedOnCpuType() {
            sampledOnly(EventTypeName.CPU_TIME_SAMPLE);
            recorded(EventTypeName.CLASS_LOAD);
            when(classLoadingManager.overview()).thenReturn(
                    new ClassLoadingOverview(100, 120, 20, 2, 4096, 3, false, false));
            when(classLoadingManager.classLoadActivity()).thenReturn(ClassLoadActivity.empty());
            when(classLoadingManager.redefinitions()).thenReturn(new RedefinitionData(List.of(), List.of()));
            when(classLoadingManager.classLoaders()).thenReturn(List.of());

            JsonNode out = conforming("classLoading", tools().classLoading());

            assertEquals(EventTypeName.CPU_TIME_SAMPLE, call(out, "timeline_hotWindows").get("eventType").asString());

            sampledOnly();
            recorded(EventTypeName.CLASS_LOAD);
            JsonNode none = conforming("classLoading", tools().classLoading());
            assertEquals(Set.of("heap_getClassLoaderLeakChains"), nextTools(none));
            assertTrue(guidance(none).contains("flamegraph_list"), guidance(none));
        }
    }

    @Nested
    class ClassLoaders {

        @Test
        void countsTheLoadersBeyondTheLargestTwenty() {
            recorded(EventTypeName.CLASS_LOAD);
            when(classLoadingManager.overview()).thenReturn(
                    new ClassLoadingOverview(100, 120, 20, 30, 4096, 3, false, false));
            when(classLoadingManager.classLoadActivity()).thenReturn(ClassLoadActivity.empty());
            when(classLoadingManager.redefinitions()).thenReturn(new RedefinitionData(List.of(), List.of()));
            List<ClassLoaderStat> loaders = new ArrayList<>();
            for (int i = 0; i < 30; i++) {
                loaders.add(new ClassLoaderStat("loader-" + i, "app", 10, 1024, 0, 0, 0));
            }
            when(classLoadingManager.classLoaders()).thenReturn(loaders);

            JsonNode out = conforming("classLoading", tools().classLoading());

            assertEquals(20, out.get("dashboard").get("loaders").size());
            assertEquals(10, out.get("dashboard").get("omittedLoaders").asInt());
            verify(classLoadingManager, times(1)).classLoaders();
        }
    }

    @Nested
    class Exceptions {

        /**
         * A counted total with no throw events is a gap in the recording, which is advice about the next
         * recording rather than a call.
         */
        @Test
        void saysTheAttributionIsMissingWhenTheThrowEventsWereNotRecorded() {
            recorded(EventTypeName.EXCEPTION_STATISTICS);
            when(exceptionsManager.overview()).thenReturn(new ExceptionsOverview(5000, 0, 0, 0, false, false));

            JsonNode out = conforming("exceptions", tools().exceptions());

            assertTrue(guidance(out).contains("jdk.JavaExceptionThrow"), guidance(out));
            assertEquals("fillInStackTrace", call(out, "flamegraph_export").get("search").asString());
            assertEquals("ERRORS", call(out, "traces_operations").get("sort").asString());
            assertEquals(2, McpNextToolConformance.assertFollowable(out, reachable()));
        }

        @Test
        void theThrowSiteGraphNeedsOnCpuSamples() {
            sampledOnly();
            recorded(EventTypeName.EXCEPTION_STATISTICS);
            when(exceptionsManager.overview()).thenReturn(new ExceptionsOverview(5000, 0, 0, 0, false, false));

            JsonNode out = conforming("exceptions", tools().exceptions());

            assertEquals(Set.of("traces_operations"), nextTools(out));
            assertTrue(guidance(out).contains("flamegraph_list"), guidance(out));
        }
    }

    @Nested
    class SystemAndHost {

        /** A process the JVM started is reported at the instant it started, not at an offset. */
        @Test
        void placesALaunchedProcessOnTheEpochBase() {
            recorded(EventTypeName.CPU_LOAD);
            when(systemResourcesManager.overview()).thenReturn(new SystemOverview(5000, 9000, 2000, 3000, 100, 2, 1));
            when(systemResourcesManager.launchedProcesses()).thenReturn(List.of(
                    new LaunchedProcessInfo(1_500, 42, "git status", "/tmp", "main")));

            JsonNode out = conforming("system", tools().system());

            JsonNode launched = out.get("dashboard").get("launchedProcesses").get(0);
            assertEquals(START_MS + 1_500, launched.get("startedAtEpochMs").asLong());
            assertEquals(50.0, out.get("dashboard").get("avgMachineCpuPercent").asDouble());
            assertEquals(Set.of("blocking_monitors", "jvm_container"), nextTools(out));
            assertEquals(2, McpNextToolConformance.assertFollowable(out, reachable()));
        }
    }

    @Nested
    class Security {

        @Test
        void reportsACertificatesExpiryAsAnInstantAndRoutesAHandshakeHeavyRunToTheSockets() {
            recorded(EventTypeName.TLS_HANDSHAKE);
            long expiry = START_MS + Duration.ofDays(3).toMillis();
            when(securityManager.securityData()).thenReturn(new SecurityData(
                    new SecurityData.SecurityHeader(400, 2, 2, 1, 0, 0),
                    null, List.of(), List.of(), List.of(),
                    List.of(new SecurityData.CertificateStat("CN=a", "CN=ca", "RSA", 1024, "SHA1withRSA",
                                    0, expiry, 10, true, true, false, true),
                            new SecurityData.CertificateStat("CN=b", null, "EC", 256, "SHA256withECDSA",
                                    0, 0, 1, false, false, false, false)),
                    new SecurityData.DeserializationSummary(0, 0, 0, 0),
                    List.of(), List.of(), List.of()));

            JsonNode out = conforming("security", tools().security());

            JsonNode flagged = out.get("dashboard").get("flagged");
            assertEquals(1, flagged.size(), flagged.toString());
            assertEquals(expiry, flagged.get(0).get("validUntilEpochMs").asLong());
            assertEquals("SOCKET", call(out, "io_endpoints").get("kind").asString());
            assertTrue(guidance(out).contains("deployment"), guidance(out));
            assertEquals(2, McpNextToolConformance.assertFollowable(out, reachable()));
        }
    }

    /**
     * The throttling verdict is the other judgement the surface makes on its own, so it comes back
     * in the same finding shape as the rules — same id discipline, the counters as evidence.
     */
    @Nested
    class Container {

        private void throttling(ContainerCpuThrottlingData.Verdict verdict) {
            recorded(EventTypeName.CONTAINER_CONFIGURATION);
            when(containerManager.configuration()).thenReturn(new ContainerConfigurationData(
                    new ContainerConfiguration("cgroupv2", 100 * MILLI_IN_NANOS, 200 * MILLI_IN_NANOS, 1024L, 2L, 0L,
                            -1L, -1L, null, null)));
            when(containerManager.throttling()).thenReturn(new ContainerCpuThrottlingData(
                    verdict,
                    new ContainerCpuThrottlingData.Summary(1000, 250, 4200.0, 25.0, 80.0, 2.0, 100.0, 2L),
                    List.of(),
                    List.of()));
        }

        @Test
        void reportsTheVerdictAsAFindingWithItsCounters() {
            throttling(new ContainerCpuThrottlingData.Verdict(
                    true, ContainerCpuThrottlingData.Severity.HIGH, "CPU throttled",
                    "The scheduler throttled a quarter of the periods"));

            JsonNode out = conforming("container", tools().container());

            JsonNode finding = out.get("dashboard").get("findings").get(0);
            assertEquals("container:cpu-throttling", finding.get("id").asString());
            assertEquals("CRITICAL", finding.get("severity").asString());
            assertEquals("jvm_container", finding.get("source").asString());
            assertEquals(250, finding.get("evidence").get("throttledPeriods").asInt());
            assertEquals("jvm_threads", finding.get("nextTool").get("tool").asString());
            assertTrue(finding.get("action").asString().startsWith("Compare the CPU limit"));
            assertEquals(Set.of("jvm_threads"), nextTools(out));
            assertEquals(2, McpNextToolConformance.assertFollowable(out, reachable()));
            assertEquals(UI_BASE + "container/cpu-throttling", out.get("uiLink").asString());
        }

        /** The cgroup figures carry their units, and one the container did not set is null. */
        @Test
        void reportsTheLimitsWithTheirUnits() {
            throttling(new ContainerCpuThrottlingData.Verdict(
                    false, ContainerCpuThrottlingData.Severity.NONE, "Not throttled", "No throttled periods"));

            JsonNode out = conforming("container", tools().container());

            JsonNode limits = out.get("dashboard").get("configuration");
            assertEquals(200 * MILLI_IN_NANOS, limits.get("cpuQuotaNanos").asLong());
            assertEquals(100 * MILLI_IN_NANOS, limits.get("cpuSlicePeriodNanos").asLong());
            assertEquals(out.get("dashboard").get("summary").get("cfsPeriodMs").asDouble(),
                    limits.get("cpuSlicePeriodNanos").asLong() / (double) MILLI_IN_NANOS,
                    "the period and the throttling summary's period are one figure in two units");
            assertTrue(limits.get("hostTotalMemoryBytes").isNull(), limits.toString());
            assertEquals(80.0, out.get("dashboard").get("summary").get("peakRatioPct").asDouble());
            assertEquals("OK", out.get("dashboard").get("findings").get(0).get("severity").asString());
            assertTrue(out.get("dashboard").get("findings").get(0).get("action").isNull());
        }

        /**
         * A verdict the data cannot support is not a finding of any severity: the recording lacks what
         * the question needs, and the summary's capability gaps say so.
         */
        @Test
        void reportsNoFindingWhenTheDataCannotSupportAVerdict() {
            throttling(new ContainerCpuThrottlingData.Verdict(
                    false, ContainerCpuThrottlingData.Severity.NOT_APPLICABLE, "Unknown", "No throttling events"));

            JsonNode out = conforming("container", tools().container());

            assertEquals(0, out.get("dashboard").get("findings").size());
            assertEquals(UI_BASE + "container/configuration", out.get("uiLink").asString());
        }
    }

    @Nested
    class Configuration {

        @Test
        void listsTheSectionsByTheNamesTheInputTakes() {
            recorded(EventTypeName.JVM_INFORMATION);
            when(configurationManager.configuration()).thenReturn(configuration());

            JsonNode out = conforming("configuration", tools().configuration(null));

            assertEquals("[\"JVM_INFORMATION\",\"GC_HEAP_CONFIGURATION\"]", out.get("dashboard").get("sections").toString());
            assertTrue(out.get("dashboard").get("values").isNull());
            assertEquals(Set.of("jvm_configuration"), nextTools(out));
            List<String> tabs = new ArrayList<>();
            out.get("followUp").get("nextTools").forEach(call -> tabs.add(call.get("arguments").get("section").asString()));
            assertEquals(List.of("JVM_INFORMATION", "GC_HEAP_CONFIGURATION"), tabs);
            assertEquals(UI_BASE + "overview", out.get("uiLink").asString());
        }

        @Test
        void returnsOneSectionsValuesWhenItIsNamed() {
            recorded(EventTypeName.JVM_INFORMATION);
            when(configurationManager.configuration()).thenReturn(configuration());

            JsonNode out = conforming("configuration", tools().configuration(ConfigurationTab.GC_HEAP_CONFIGURATION));

            assertEquals("GC_HEAP_CONFIGURATION", out.get("dashboard").get("section").asString());
            assertEquals("4096", out.get("dashboard").get("values").get("Maximum Heap Size").asString());
            assertFalse(out.toString().contains("JVM Version"), out.toString());
            assertTrue(guidance(out).contains("deployment manifest"), guidance(out));
            assertEquals(Set.of("jvm_flags", "jvm_gc", "jvm_jit"), nextTools(out));
        }

        /** A section the input names but this recording did not carry is a status, not a failed call. */
        @Test
        void aSectionTheRecordingLacksIsNotRecorded() {
            recorded(EventTypeName.JVM_INFORMATION);
            when(configurationManager.configuration()).thenReturn(configuration());

            JsonNode out = conforming("configuration", tools().configuration(ConfigurationTab.CPU_INFORMATION));

            assertEquals("NOT_RECORDED", out.get("status").asString());
            assertTrue(out.get("reason").asString().contains("CPU_INFORMATION"), out.toString());
            assertEquals(Set.of("jvm_configuration"), nextTools(out));
            assertEquals(1, McpNextToolConformance.assertFollowable(out, reachable()));
        }

        /**
         * The input advertises the constant names the answer lists, and a listed name goes straight
         * back in, in any case.
         */
        @Test
        void aListedSectionNameIsAcceptedAsInput() {
            recorded(EventTypeName.JVM_INFORMATION);
            when(configurationManager.configuration()).thenReturn(configuration());
            ReflectiveToolset toolset = new ReflectiveToolset(tools(), "jvm");
            JsonNode values = toolset.specs().stream()
                    .filter(spec -> spec.name().equals("jvm_configuration"))
                    .findFirst()
                    .orElseThrow()
                    .inputSchema().path("properties").path("section").path("enum");
            String listed = Json.readTree(toolset.call("jvm_configuration", Json.createObject()))
                    .get("dashboard").get("sections").get(1).asString();

            JsonNode exact = Json.readTree(toolset.call("jvm_configuration", Json.createObject().put("section", listed)));
            JsonNode lower = Json.readTree(toolset.call("jvm_configuration",
                    Json.createObject().put("section", listed.toLowerCase())));

            assertEquals(11, values.size(), values.toString());
            assertTrue(values.toString().contains("\"TLAB_CONFIGURATION\""), values.toString());
            assertEquals("OK", exact.get("status").asString());
            assertEquals(exact, lower);
        }

        static JsonNode configuration() {
            return Json.readObjectNode("""
                    {
                      "JVM Information": {"JVM Version": "25.0.1"},
                      "GC Heap Configuration": {"Maximum Heap Size": "4096"}
                    }
                    """);
        }
    }

    @Nested
    class GcDetail {

        @Test
        void listsThePagesByTheNamesTheInputTakes() {
            recorded(EventTypeName.GARBAGE_COLLECTION);

            JsonNode out = conforming("gcDetail", tools().gcDetail(null));

            JsonNode pages = out.get("dashboard").get("pages");
            assertEquals(GcDetailPage.values().length, pages.size());
            assertTrue(pages.toString().contains("\"STRING_TABLES\""), pages.toString());
            assertTrue(out.get("dashboard").get("page").isNull());
            assertEquals(UI_BASE + "garbage-collection", out.get("uiLink").asString());
            List<String> pagesCalled = new ArrayList<>();
            out.get("followUp").get("nextTools").forEach(call -> pagesCalled.add(call.get("arguments").path("page").asString()));
            assertEquals(Arrays.stream(GcDetailPage.values()).map(Enum::name).toList(), pagesCalled);
            assertEquals(Set.of("jvm_gcDetail"), nextTools(out));
        }

        /** A blank page is how a model spells "none", so it lists the pages rather than failing. */
        @ParameterizedTest
        @ValueSource(strings = {"", " ", "\t\n", "\u2003"})
        void listsThePagesForABlankArgumentThroughTheReflectiveAdapter(String page) {
            recorded(EventTypeName.GARBAGE_COLLECTION);
            JvmMcpTools target = tools();

            String result = new ReflectiveToolset(target, "jvm").call(
                    "jvm_gcDetail", Json.createObject().put("page", page));

            assertEquals(assertInstanceOf(McpToolResult.class, target.gcDetail(null)).text(), result);
        }

        /** Every page renders into its own component and links its own page, even with nothing in it. */
        @ParameterizedTest
        @EnumSource(GcDetailPage.class)
        void everyPageRendersInItsSchema(GcDetailPage page) {
            recorded(EventTypeName.GARBAGE_COLLECTION);

            JsonNode out = conforming("gcDetail", tools().gcDetail(page));

            assertEquals(page.name(), out.get("dashboard").get("page").asString());
            assertEquals(UI_BASE + page.view().path(), out.get("uiLink").asString());
            assertEquals(Set.of("flamegraph_export", "jvm_flags", "jvm_gc"), nextTools(out));
        }

        /**
         * The tenuring table keeps the most recent collections, in order, and counts the earlier ones:
         * the age distribution the application ended the run with is the one a survivor decision is
         * made against.
         */
        @Test
        void keepsTheMostRecentCollectionsAndCountsTheEarlierOnes() {
            recorded(EventTypeName.GARBAGE_COLLECTION);
            List<TenuringData.TenuringGcSummary> collections = new ArrayList<>();
            for (int gc = 0; gc < GcDetailPages.ROWS_LIMIT + 5; gc++) {
                collections.add(new TenuringData.TenuringGcSummary(gc, 1024, List.of()));
            }
            when(gcManager.tenuring()).thenReturn(new TenuringData(collections));

            JsonNode tenuring = conforming("gcDetail", tools().gcDetail(GcDetailPage.TENURING))
                    .get("dashboard").get("tenuring");

            assertEquals(GcDetailPages.ROWS_LIMIT, tenuring.get("collections").size());
            assertEquals(5, tenuring.get("collections").get(0).get("gcId").asInt());
            assertEquals(GcDetailPages.ROWS_LIMIT + 4, tenuring.get("collections").get(GcDetailPages.ROWS_LIMIT - 1)
                    .get("gcId").asInt());
            assertEquals(5, tenuring.get("omittedRows").get("collections").asInt());
        }

        /** An evacuation table keeps the collections that copied the most, largest first. */
        @Test
        void keepsTheEvacuationsThatCopiedTheMost() {
            recorded(EventTypeName.GARBAGE_COLLECTION);
            List<G1AnalysisData.EvacuationEntry> evacuations = new ArrayList<>();
            for (int gc = 0; gc < GcDetailPages.ROWS_LIMIT + 10; gc++) {
                evacuations.add(new G1AnalysisData.EvacuationEntry(gc, 1, 0, 0, 1, gc * 1024L, 1));
            }
            when(gcManager.g1Analysis()).thenReturn(new G1AnalysisData(null, List.of(), null, List.of(),
                    evacuations, List.of(), null, List.of(), List.of(), List.of()));

            JsonNode g1 = conforming("gcDetail", tools().gcDetail(GcDetailPage.G1)).get("dashboard").get("g1");

            assertEquals(GcDetailPages.ROWS_LIMIT, g1.get("evacuations").size());
            assertEquals(GcDetailPages.ROWS_LIMIT + 9, g1.get("evacuations").get(0).get("gcId").asInt());
            assertEquals(10, g1.get("omittedRows").get("evacuations").asInt());
        }

        /** Each page says which rows it keeps and which tables its omittedRows can name. */
        @Test
        void eachPageDescribesItsCutInTheSchema() {
            String schema = schemaOf("gcDetail").toString();

            assertTrue(schema.contains("most recent"), schema);
            assertTrue(schema.contains("evacuations, evacuationFailures, mmu, systemGcs, gcLockers"), schema);
        }

        /** The G1 page's events are placed on the recording's clock rather than left as offsets. */
        @Test
        void placesTheG1EventsOnTheEpochBase() {
            recorded(EventTypeName.GARBAGE_COLLECTION);
            when(gcManager.g1Analysis()).thenReturn(new G1AnalysisData(
                    new G1AnalysisData.G1Header(1, 0, 0, MILLI_IN_NANOS, MILLI_IN_NANOS, MILLI_IN_NANOS,
                            MILLI_IN_NANOS, 0, 64),
                    List.of(), null, List.of(), List.of(), List.of(), null, List.of(),
                    List.of(new G1AnalysisData.SystemGcEntry(2_000, MILLI_IN_NANOS, false)),
                    List.of()));

            JsonNode g1 = conforming("gcDetail", tools().gcDetail(GcDetailPage.G1)).get("dashboard").get("g1");

            assertEquals(START_MS + 2_000, g1.get("systemGcs").get(0).get("atEpochMs").asLong());
            assertEquals(64, g1.get("header").get("regionCount").asInt());
        }

        /** A listed page name goes straight back in, and so does any spelling of its case. */
        @Test
        void aListedPageIsAcceptedAsInputInAnyCase() {
            recorded(EventTypeName.GARBAGE_COLLECTION);
            when(gcManager.tenuring()).thenReturn(new TenuringData(List.of()));
            ReflectiveToolset toolset = new ReflectiveToolset(tools(), "jvm");
            String listed = Json.readTree(toolset.call("jvm_gcDetail", Json.createObject()))
                    .get("dashboard").get("pages").get(1).asString();

            JsonNode upper = Json.readTree(toolset.call("jvm_gcDetail", Json.createObject().put("page", listed)));
            JsonNode lower = Json.readTree(toolset.call("jvm_gcDetail", Json.createObject().put("page", "tenuring")));

            assertEquals("TENURING", upper.get("dashboard").get("page").asString());
            assertEquals(upper, lower);
        }
    }

    @Nested
    class AutoAnalysis {

        private void rules(boolean computed, boolean canGenerate, List<AutoAnalysisResult> results) {
            recorded();
            when(autoAnalysisManager.isComputed()).thenReturn(computed);
            when(autoAnalysisManager.canGenerate()).thenReturn(canGenerate);
            when(autoAnalysisManager.analysisResults()).thenReturn(results);
        }

        /**
         * Generating it loads the whole recording through the JMC toolkit, so an empty cache is
         * reported rather than quietly paid for inside an MCP call.
         */
        @Test
        void saysItIsNotComputedAndNamesTheCallThatComputesIt() {
            rules(false, true, List.of());

            JsonNode out = conforming("autoAnalysis", tools().autoAnalysis(null, RESOURCE_READ));

            assertEquals("NOT_COMPUTED", out.get("status").asString());
            assertTrue(out.get("reason").asString().contains("has not been computed"));
            assertTrue(out.get("dashboard").isNull());
            assertTrue(call(out, "jvm_autoAnalysis").get("compute").asBoolean());
            assertEquals(2, McpNextToolConformance.assertFollowable(out, reachable()));
            assertEquals(UI_BASE + "auto-analysis", out.get("uiLink").asString());
            verify(autoAnalysisManager, never()).analysisResults();
            verify(autoAnalysisManager, never()).generate();
        }

        /**
         * A run that flagged nothing is an answer, not a missing one: the manager says it ran, and the
         * cached list happens to be empty.
         */
        @Test
        void aRunThatFlaggedNothingIsComputed() {
            rules(true, true, List.of());

            JsonNode out = conforming("autoAnalysis", tools().autoAnalysis(null, RESOURCE_READ));

            assertEquals("COMPUTED", out.get("status").asString());
            assertEquals(0, out.get("dashboard").get("findings").size());
            assertEquals("{\"CRITICAL\":0,\"WARNING\":0,\"INFO\":0,\"OK\":0}",
                    out.get("dashboard").get("findingCounts").toString());
            assertTrue(out.get("operationId").isNull());
            verify(autoAnalysisManager, never()).generate();
        }

        /** Without a recording file the rules cannot run, so compute is not attempted and not offered. */
        @Test
        void cannotComputeWithoutARecordingFile() {
            rules(false, false, List.of());

            JsonNode out = conforming("autoAnalysis", tools().autoAnalysis(true, RESOURCE_READ));

            assertEquals("CANNOT_COMPUTE", out.get("status").asString());
            assertEquals(Set.of("jvm_sections"), nextTools(out));
            verify(autoAnalysisManager, never()).generate();
        }

        /**
         * The rules are reported in the shared finding shape: a stable id from the JMC topic and the
         * rule, the call that carries the figures, and the score as evidence.
         */
        @Test
        void returnsTheCachedFindingsInTheSharedShape() {
            rules(true, true, List.of(longGcPauses()));

            JsonNode out = conforming("autoAnalysis", tools().autoAnalysis(null, RESOURCE_READ));

            JsonNode finding = out.get("dashboard").get("findings").get(0);
            assertEquals("garbage_collection:long-gc-pauses", finding.get("id").asString());
            assertEquals("WARNING", finding.get("severity").asString());
            assertEquals("jvm_autoAnalysis", finding.get("source").asString());
            assertEquals("jvm_gc", finding.get("nextTool").get("tool").asString());
            assertEquals(PROFILE_ID, finding.get("nextTool").get("arguments").get("profileId").asString());
            assertEquals("78", finding.get("evidence").get("score").asString());
            assertEquals(1, out.get("dashboard").get("findingCounts").get("WARNING").asInt());
            assertEquals(1, McpNextToolConformance.assertFollowable(out, reachable()));
        }

        /**
         * A finding's call to a family this installation withholds is left out and the finding kept,
         * the same gate every other tool that hands findings out applies.
         */
        @Test
        void dropsAFindingsCallToAWithheldFamily() {
            rules(true, true, List.of(longGcPauses(), new AutoAnalysisResult(
                    "Lock Instances", AnalysisResult.Severity.WARNING, "Contended", "Locks are contended",
                    null, "60", "lock_instances")));

            JsonNode out = conforming("autoAnalysis",
                    tools(new AdvertisedFamilies(Set.of("jvm", "operations"))).autoAnalysis(null, RESOURCE_READ));

            Map<String, JsonNode> byId = new LinkedHashMap<>();
            out.get("dashboard").get("findings").forEach(finding -> byId.put(finding.get("id").asString(), finding));
            assertTrue(byId.get("lock_instances:lock-instances").get("nextTool").isNull(), out.toString());
            assertEquals("jvm_gc", byId.get("garbage_collection:long-gc-pauses").get("nextTool").get("tool").asString());
        }

        /**
         * A rule with no events to run on did not pass. It is listed apart from the findings, so a
         * reader never takes "not evaluated" for "checked and fine".
         */
        @Test
        void listsTheRulesThatCouldNotRunApartFromTheFindings() {
            rules(true, true, List.of(
                    new AutoAnalysisResult(
                            "Allocated Classes", AnalysisResult.Severity.NA,
                            "No allocation events", "Not applicable", null, null, "tlab"),
                    new AutoAnalysisResult(
                            "Thrown Errors", AnalysisResult.Severity.OK,
                            "No errors were thrown", "No errors thrown", null, "0", "exceptions")));

            JsonNode out = conforming("autoAnalysis", tools().autoAnalysis(null, RESOURCE_READ));

            assertEquals("[\"Allocated Classes\"]", out.get("dashboard").get("notEvaluated").toString());
            assertEquals("exceptions:thrown-errors", out.get("dashboard").get("findings").get(0).get("id").asString());
            assertTrue(guidance(out).contains("notEvaluated"), guidance(out));
        }

        /**
         * Computing is asked for rather than assumed, and the answer carries the operation that ran it,
         * in the snapshot shape operations_status reports.
         */
        @Test
        void computesItOnRequest() {
            recorded();
            AtomicBoolean generated = new AtomicBoolean();
            when(autoAnalysisManager.canGenerate()).thenReturn(true);
            when(autoAnalysisManager.isComputed()).thenAnswer(invocation -> generated.get());
            when(autoAnalysisManager.analysisResults()).thenReturn(List.of(longGcPauses()));
            doAnswer(invocation -> {
                generated.set(true);
                return null;
            }).when(autoAnalysisManager).generate();

            JsonNode out = conforming("autoAnalysis", tools().autoAnalysis(true, RESOURCE_READ));

            verify(autoAnalysisManager).generate();
            assertEquals("COMPUTED", out.get("status").asString());
            assertEquals("Long GC Pauses", out.get("dashboard").get("findings").get(0).get("evidence").get("rule").asString());
            assertEquals(out.get("operationId").asString(), out.get("operation").get("operationId").asString());
            assertEquals("JVM_AUTO_ANALYSIS", out.get("operation").get("kind").asString());
            assertEquals("COMPLETED", out.get("operation").get("status").asString());
            assertTrue(out.get("operation").get("result").isNull(),
                    "the findings are the answer's dashboard, not repeated as the operation's result");
            assertTrue(Json.toString(operations.status(out.get("operationId").asString())).contains("Long GC Pauses"),
                    "operations_status still carries them");
            await().atMost(5, TimeUnit.SECONDS).until(() -> leases.get() == 0);
        }

        /**
         * The rule set reads the whole recording, which on a large one outlasts the point where a
         * client gives up on a call. Past the wait budget the call answers NOT_COMPUTED with the
         * running operation and the call that polls it.
         */
        @Test
        void answersWithTheRunningOperationWhenComputingOutlastsTheWaitBudget() throws Exception {
            recorded();
            waitBudget = Duration.ofMillis(50);
            CountDownLatch release = new CountDownLatch(1);
            AtomicBoolean generated = new AtomicBoolean();
            when(autoAnalysisManager.canGenerate()).thenReturn(true);
            when(autoAnalysisManager.isComputed()).thenAnswer(invocation -> generated.get());
            when(autoAnalysisManager.analysisResults()).thenReturn(List.of(longGcPauses()));
            doAnswer(invocation -> {
                assertTrue(release.await(5, TimeUnit.SECONDS));
                generated.set(true);
                return null;
            }).when(autoAnalysisManager).generate();

            JsonNode started = conforming("autoAnalysis", tools().autoAnalysis(true, RESOURCE_READ));
            String operationId = started.path("operationId").asString();

            assertEquals("NOT_COMPUTED", started.get("status").asString(), started.toString());
            assertEquals("RUNNING", started.get("operation").get("status").asString());
            assertEquals(operationId, call(started, "operations_status").get("operationId").asString());
            assertEquals(2, McpNextToolConformance.assertFollowable(started, reachable()),
                    "the answer's poll and the operation's own poll");
            assertEquals(1, leases.get(), "the worker holds the profile open while it computes");

            release.countDown();
            await().atMost(5, TimeUnit.SECONDS)
                    .until(() -> operations.status(operationId).status() == OperationState.COMPLETED);
            String polled = Json.toString(operations.status(operationId));
            assertTrue(polled.contains("\"rule\":\"Long GC Pauses\""), polled);
            await().atMost(5, TimeUnit.SECONDS).until(() -> leases.get() == 0);
        }

        @Test
        void aFailedComputationIsAnErrorCarryingItsOperation() {
            rules(false, true, List.of());
            doThrow(new IllegalStateException("recording file is gone")).when(autoAnalysisManager).generate();

            ToolExecutionException failure = assertThrows(ToolExecutionException.class,
                    () -> tools().autoAnalysis(true, RESOURCE_READ));

            assertTrue(failure.getMessage().contains("recording file is gone"), failure.getMessage());
            assertTrue(failure.getMessage().contains("operationId"), failure.getMessage());
        }

        /** A failed run's operation names the call that starts it again, rather than a retry sentence. */
        @Test
        void aFailedComputationNamesTheRetryCall() {
            rules(false, true, List.of());
            doThrow(new IllegalStateException("recording file is gone")).when(autoAnalysisManager).generate();

            ToolExecutionException failure = assertThrows(ToolExecutionException.class,
                    () -> tools().autoAnalysis(true, RESOURCE_READ));

            String message = failure.getMessage();
            JsonNode operation = Json.readTree(message.substring(message.indexOf("Operation: ") + "Operation: ".length()));
            JsonNode retry = operation.get("followUp").get("nextTools").get(0);
            assertEquals("jvm_autoAnalysis", retry.get("tool").asString());
            assertTrue(retry.get("arguments").get("compute").asBoolean());
            assertEquals(1, McpNextToolConformance.assertFollowable(operation, reachable()));
        }

        @Test
        void declaresItselfAWriterThatIsSafeToRepeat() {
            McpToolSpec spec = new ReflectiveToolset(tools(), "jvm").specs().stream()
                    .filter(candidate -> candidate.name().equals("jvm_autoAnalysis"))
                    .findFirst()
                    .orElseThrow();

            assertFalse(spec.annotations().readOnly());
            assertTrue(spec.annotations().idempotent());
        }
    }

    /**
     * A client that declared the tasks extension is not held for the standard 45 s: past the task
     * budget the computation is handed back as the task that follows it, and the task answers with
     * what a caller that waited would have got.
     */
    @Nested
    class TaskCapableClient {

        @Test
        void handsBackTheRunningComputationAsATaskWellBeforeTheStandardWait() throws Exception {
            recorded();
            waitBudget = BoundedJobs.WAIT_BUDGET;
            answers = new OperationAnswers(SHORT_TASK_WAIT);
            CountDownLatch release = new CountDownLatch(1);
            AtomicBoolean generated = new AtomicBoolean();
            when(autoAnalysisManager.canGenerate()).thenReturn(true);
            when(autoAnalysisManager.isComputed()).thenAnswer(invocation -> generated.get());
            when(autoAnalysisManager.analysisResults()).thenReturn(List.of(longGcPauses()));
            doAnswer(invocation -> {
                assertTrue(release.await(60, TimeUnit.SECONDS));
                generated.set(true);
                return null;
            }).when(autoAnalysisManager).generate();
            JvmMcpTools tools = tools();

            McpToolOutcome outcome;
            try {
                outcome = assertTimeout(Duration.ofSeconds(20), () -> tools.autoAnalysis(true, TASKS));
            } catch (AssertionError e) {
                release.countDown();
                throw e;
            }

            String taskId = assertInstanceOf(McpToolOutcome.Deferred.class, outcome).taskId();
            assertEquals(OperationKind.JVM_AUTO_ANALYSIS, operations.status(taskId).kind());
            assertEquals(McpTaskStatus.WORKING, operations.task(taskId, kind -> true).status());

            release.countDown();
            await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> assertEquals(
                    McpTaskStatus.COMPLETED, operations.task(taskId, kind -> true).status()));
            McpToolResult answer = assertInstanceOf(McpTaskState.Completed.class,
                    operations.task(taskId, kind -> true).state()).result();
            JsonNode out = conforming("autoAnalysis", answer);
            assertEquals("COMPUTED", out.get("status").asString());
            assertEquals(taskId, out.get("operationId").asString());
            assertEquals("COMPLETED", out.get("operation").get("status").asString());
            await().atMost(5, TimeUnit.SECONDS).until(() -> leases.get() == 0);
        }

        /** A computation that finishes inside the task budget is answered, not deferred. */
        @Test
        void answersAComputationThatFinishesInsideTheTaskBudget() {
            recorded();
            waitBudget = BoundedJobs.WAIT_BUDGET;
            AtomicBoolean generated = new AtomicBoolean();
            when(autoAnalysisManager.canGenerate()).thenReturn(true);
            when(autoAnalysisManager.isComputed()).thenAnswer(invocation -> generated.get());
            when(autoAnalysisManager.analysisResults()).thenReturn(List.of(longGcPauses()));
            doAnswer(invocation -> {
                generated.set(true);
                return null;
            }).when(autoAnalysisManager).generate();

            JsonNode out = conforming("autoAnalysis", tools().autoAnalysis(true, TASKS));

            assertEquals("COMPUTED", out.get("status").asString());
            assertFalse(out.get("operationId").isNull());
        }
    }

    @Nested
    class ThreadDumps {

        @Test
        void aProfileWithoutThreadDumpsSaysSoWithAStatus() {
            recorded();
            when(threadManager.threadDumpAnalysis()).thenReturn(null);

            JsonNode out = conforming("threadDumps", tools().threadDumps());

            assertEquals("NO_THREAD_DUMPS", out.get("status").asString());
            assertTrue(out.get("header").isNull());
            assertEquals(Set.of("jvm_threads"), nextTools(out));
            assertTrue(guidance(out).contains("jdk.ThreadDump"), guidance(out));
            assertEquals(UI_BASE + "thread-dumps", out.get("uiLink").asString());
        }

        /**
         * The dumps and the deadlocks are placed on the recording's clock, and a deadlock hands on the
         * call that opens its dump narrowed to the threads queueing.
         */
        @Test
        void placesTheDumpsOnTheEpochBaseAndOpensTheDeadlockedDump() {
            recorded();
            when(threadManager.threadDumpAnalysis()).thenReturn(new ThreadDumpAnalysis(
                    new ThreadDumpAnalysis.Header(2, 30, 1, 1, 1_000, 11_000),
                    List.of(new ThreadDumpAnalysis.DumpDescriptor(0, 1_000, 30, 0),
                            new ThreadDumpAnalysis.DumpDescriptor(1, 11_000, 28, 1)),
                    null,
                    List.of(new ThreadDumpAnalysis.FrameStat("java.lang.Object.wait", 12, 6)),
                    List.of(new ThreadDumpAnalysis.DeadlockEntry(1, 11_000, "cycle", List.of("a", "b"))),
                    List.of(new ThreadDumpAnalysis.LockContention("0x1", "java.lang.Object", 3, null)),
                    List.of(new ThreadDumpAnalysis.StuckThread("worker", ThreadState.BLOCKED, null, 2, 10_000)),
                    null));

            JsonNode out = conforming("threadDumps", tools().threadDumps());

            assertEquals(START_MS + 1_000, out.get("header").get("firstDumpEpochMs").asLong());
            assertEquals(START_MS + 11_000, out.get("dumps").get(1).get("capturedAtEpochMs").asLong());
            assertEquals(START_MS + 11_000, out.get("deadlocks").get(0).get("capturedAtEpochMs").asLong());
            JsonNode open = call(out, "jvm_threadDump");
            assertEquals(1, open.get("index").asInt());
            assertEquals("BLOCKED", open.get("state").asString());
            assertEquals(Set.of("blocking_monitors", "jvm_threadDump"), nextTools(out));
            assertEquals(2, McpNextToolConformance.assertFollowable(out, reachable()));
        }
    }

    @Nested
    class ThreadDumpsSize {

        /**
         * A deadlock that never clears is found again in every dump; it is reported once, with how often
         * and until when, and a recording with many dumps and many stuck threads still fits the answer,
         * the cut counted per table.
         */
        @Test
        void collapsesARecurringDeadlockAndFitsAnAnswerThatWouldOutgrowTheLimit() {
            recorded();
            int dumpCount = 200;
            List<ThreadDumpAnalysis.DumpDescriptor> dumps = new ArrayList<>();
            List<ThreadDumpAnalysis.DeadlockEntry> deadlocks = new ArrayList<>();
            for (int i = 0; i < dumpCount; i++) {
                dumps.add(new ThreadDumpAnalysis.DumpDescriptor(i, i * 1_000L, 300, 1));
                deadlocks.add(new ThreadDumpAnalysis.DeadlockEntry(i, i * 1_000L, "cycle between a and b",
                        List.of("a", "b")));
            }
            List<ThreadDumpAnalysis.StuckThread> stuck = new ArrayList<>();
            for (int i = 0; i < 2_000; i++) {
                stuck.add(new ThreadDumpAnalysis.StuckThread("pool-worker-" + i, ThreadState.WAITING,
                        "com.example.service.handler.VeryLongPackageName.SomeClassWithAName.awaitSomething(Handler.java:"
                                + i + ")", dumpCount, dumpCount * 1_000L));
            }
            when(threadManager.threadDumpAnalysis()).thenReturn(new ThreadDumpAnalysis(
                    new ThreadDumpAnalysis.Header(dumpCount, 300, dumpCount, stuck.size(), 0, (dumpCount - 1) * 1_000L),
                    dumps, null, List.of(), deadlocks, List.of(), stuck, null));

            McpToolResult result = assertInstanceOf(McpToolResult.class, tools().threadDumps());
            JsonNode out = conforming("threadDumps", result);

            assertTrue(result.text().length() <= McpToolOutput.MAX_CHARS, "length " + result.text().length());
            JsonNode deadlock = out.get("deadlocks").get(0);
            assertEquals(1, out.get("deadlocks").size());
            assertEquals(dumpCount, deadlock.get("occurrences").asInt());
            assertEquals(START_MS, deadlock.get("capturedAtEpochMs").asLong());
            assertEquals(START_MS + (dumpCount - 1) * 1_000L, deadlock.get("lastSeenEpochMs").asLong());
            JsonNode omitted = out.get("omittedRows");
            assertEquals(stuck.size(), out.get("stuckThreads").size() + omitted.path("stuckThreads").asInt(),
                    omitted.toString());
            assertTrue(omitted.path("stuckThreads").asInt() > 0, omitted.toString());
            assertEquals(dumpCount, out.get("dumps").size() + omitted.path("dumps").asInt(), omitted.toString());
        }

        /**
         * A cut keeps what a hang is diagnosed from: the most recent dumps, in order, and the threads
         * stuck longest, worst first.
         */
        @Test
        void aCutKeepsTheLastDumpsAndTheLongestStuckThreads() {
            recorded();
            int dumpCount = 200;
            List<ThreadDumpAnalysis.DumpDescriptor> dumps = new ArrayList<>();
            for (int i = 0; i < dumpCount; i++) {
                dumps.add(new ThreadDumpAnalysis.DumpDescriptor(i, i * 1_000L, 300, 0));
            }
            List<ThreadDumpAnalysis.StuckThread> stuck = new ArrayList<>();
            for (int i = 0; i < 2_000; i++) {
                stuck.add(new ThreadDumpAnalysis.StuckThread("pool-worker-" + i, ThreadState.WAITING,
                        "com.example.service.handler.Nested.awaitSomething".repeat(20) + i, 1 + i % 3, i * 10L));
            }
            when(threadManager.threadDumpAnalysis()).thenReturn(new ThreadDumpAnalysis(
                    new ThreadDumpAnalysis.Header(dumpCount, 300, 0, stuck.size(), 0, (dumpCount - 1) * 1_000L),
                    dumps, null, List.of(), List.of(), List.of(), stuck, null));

            JsonNode out = conforming("threadDumps", tools().threadDumps());

            assertTrue(out.get("omittedRows").path("stuckThreads").asInt() > 0, out.get("omittedRows").toString());
            assertTrue(out.get("omittedRows").path("dumps").asInt() > 0, "the dumps are cut too");
            JsonNode kept = out.get("dumps");
            assertEquals(dumpCount - 1, kept.get(kept.size() - 1).get("index").asInt(), "the last dump is kept");
            assertTrue(kept.get(0).get("index").asInt() < kept.get(kept.size() - 1).get("index").asInt(),
                    "the kept dumps stay in order");
            JsonNode worst = out.get("stuckThreads");
            assertEquals("pool-worker-1999", worst.get(0).get("name").asString(), "the longest-stuck thread leads");
            assertTrue(worst.get(0).get("stuckForMs").asLong() >= worst.get(1).get("stuckForMs").asLong());
            JsonNode schema = schemaOf("threadDumps").get("properties");
            assertTrue(schema.get("stuckThreads").path("description").asString().contains("longest"), schema.toString());
            assertTrue(schema.get("dumps").path("description").asString().contains("most recent"), schema.toString());
        }
    }

    /**
     * A thread dump of a busy server holds hundreds of threads, each with its whole stack; handed over
     * whole it is the largest answer the family gives. {@code state} narrows it to the threads worth
     * reading and {@code limit} bounds it, and the answer counts how many it left out.
     */
    @Nested
    class ThreadDumpSize {

        private List<ParsedDump.ParsedThread> threads;

        @BeforeEach
        void dumpOfSeventyThreads() {
            recorded();
            threads = new ArrayList<>();
            for (int i = 0; i < 60; i++) {
                threads.add(thread("worker-" + i, ThreadState.RUNNABLE, List.of("java.lang.Object.wait")));
            }
            for (int i = 0; i < 10; i++) {
                threads.add(thread("blocked-" + i, ThreadState.BLOCKED, List.of("java.lang.Object.wait")));
            }
            when(threadManager.threadDump(0)).thenAnswer(invocation -> new ParsedDump(1_000, threads, List.of(), "raw"));
        }

        @Test
        void answersFiftyThreadsByDefaultCountsTheRestAndOffersBoth() {
            JsonNode dump = conforming("threadDump", tools().threadDump(0, null, null));

            assertEquals(50, dump.get("threads").size());
            assertEquals(70, dump.get("totalThreads").asInt());
            assertEquals(70, dump.get("matchingThreads").asInt());
            assertEquals(20, dump.get("omittedThreads").asInt());
            assertEquals(START_MS + 1_000, dump.get("capturedAtEpochMs").asLong());
            List<JsonNode> calls = new ArrayList<>();
            dump.get("followUp").get("nextTools").forEach(calls::add);
            assertTrue(calls.stream().anyMatch(call -> "BLOCKED".equals(call.get("arguments").path("state").asString())),
                    calls.toString());
            assertTrue(calls.stream().anyMatch(call -> call.get("arguments").path("limit").asInt() == 500),
                    calls.toString());
            assertEquals(3, McpNextToolConformance.assertFollowable(dump, reachable()));
        }

        @Test
        void narrowsToOneStateAndTheStateItReportsGoesStraightBackIn() {
            JsonNode dump = call(Json.createObject().put("index", 0).put("state", "blocked"));
            String reported = dump.get("threads").get(0).get("state").asString();

            JsonNode again = call(Json.createObject().put("index", 0).put("state", reported));

            assertEquals(10, dump.get("threads").size());
            assertEquals(0, dump.get("omittedThreads").asInt());
            assertEquals("BLOCKED", reported);
            assertEquals(dump, again);
        }

        @Test
        void honoursASmallerLimit() {
            JsonNode dump = call(Json.createObject().put("index", 0).put("limit", 5));

            assertEquals(5, dump.get("threads").size());
            assertEquals(65, dump.get("omittedThreads").asInt());
        }

        /** Deep stacks can outgrow the answer limit well inside 500 threads; the answer is cut to fit. */
        @Test
        void cutsADumpThatOutgrowsTheAnswerLimitAndCountsTheCut() {
            List<String> deepStack = new ArrayList<>();
            for (int frame = 0; frame < 400; frame++) {
                deepStack.add("com.example.service.handler.DeeplyNestedInvocation.method" + frame + "(Handler.java:1)");
            }
            threads.replaceAll(thread -> thread(thread.name(), thread.state(), deepStack));

            McpToolResult result = assertInstanceOf(McpToolResult.class, tools().threadDump(0, null, 70));
            JsonNode dump = conforming("threadDump", result);

            assertTrue(result.text().length() <= McpToolOutput.MAX_CHARS, "length " + result.text().length());
            assertTrue(dump.get("threads").size() < 70, "shown " + dump.get("threads").size());
            assertEquals(70 - dump.get("threads").size(), dump.get("omittedThreads").asInt());
        }

        @Test
        void aDumpIndexThatIsNotThereIsAStatus() {
            JsonNode dump = conforming("threadDump", tools().threadDump(7, null, null));

            assertEquals("NO_SUCH_DUMP", dump.get("status").asString());
            assertTrue(dump.get("reason").asString().contains("index 7"), dump.toString());
            assertTrue(dump.get("capturedAtEpochMs").isNull());
            assertEquals(Set.of("jvm_threadDumps"), nextTools(dump));
        }

        @Test
        void advertisesTheLimitAndTheStates() {
            JsonNode schema = new ReflectiveToolset(tools(), "jvm").specs().stream()
                    .filter(spec -> spec.name().equals("jvm_threadDump"))
                    .findFirst()
                    .orElseThrow()
                    .inputSchema()
                    .path("properties");

            assertEquals(50, schema.path("limit").path("default").asInt());
            assertTrue(schema.path("state").path("enum").toString().contains("BLOCKED"), schema.toString());
        }

        private JsonNode call(JsonNode arguments) {
            return Json.readTree(new ReflectiveToolset(tools(), "jvm").call("jvm_threadDump", arguments));
        }

        private ParsedDump.ParsedThread thread(String name, ThreadState state, List<String> frames) {
            return new ParsedDump.ParsedThread(name, null, state, frames, List.of());
        }
    }

    @Nested
    class Flags {

        @Test
        void aProfileWithoutFlagEventsSaysSoWithAStatus() {
            recorded();

            JsonNode out = conforming("flags", tools().flags());

            assertEquals("NO_FLAGS", out.get("status").asString());
            assertEquals(Set.of("jvm_configuration"), nextTools(out));
            assertEquals(UI_BASE + "flags", out.get("uiLink").asString());
        }

        /** A flag's changes are reported at the instants they happened. */
        @Test
        void groupsTheFlagsByOriginWithTheirChangesAsInstants() {
            recorded();
            long changedAt = START_MS + 4_000;
            when(flagsManager.getAllFlags()).thenReturn(new FlagsData(Map.of(
                    "Command line", List.of(new JvmFlagDetail("MaxHeapSize", "4g", "ulong", "Command line",
                            List.of("2g"), true, "Maximum heap size",
                            List.of(new FlagValueChange("4g", changedAt))))), 1, 1));

            JsonNode out = conforming("flags", tools().flags());

            JsonNode flag = out.get("flagsByOrigin").get("Command line").get(0);
            assertEquals(changedAt, flag.get("changeHistory").get(0).get("changedAtEpochMs").asLong());
            assertEquals(0, out.get("omittedFlags").asInt());
            assertEquals(Set.of("jvm_gc", "jvm_jit"), nextTools(out));
            assertEquals(2, McpNextToolConformance.assertFollowable(out, reachable()));
        }

        /** Every flag with its description can outgrow the answer; defaults go first. */
        @Test
        void cutsTheDefaultsFirstWhenTheFlagsOutgrowTheAnswer() {
            recorded();
            String description = "x".repeat(400);
            List<JvmFlagDetail> defaults = new ArrayList<>();
            for (int i = 0; i < 800; i++) {
                defaults.add(new JvmFlagDetail("Flag" + i, "0", "int", "Default", List.of(), false, description, List.of()));
            }
            Map<String, List<JvmFlagDetail>> byOrigin = new LinkedHashMap<>();
            byOrigin.put("Command line", List.of(new JvmFlagDetail("Xmx", "4g", "ulong", "Command line",
                    List.of(), false, null, null)));
            byOrigin.put("Default", defaults);
            when(flagsManager.getAllFlags()).thenReturn(new FlagsData(byOrigin, 801, 0));

            McpToolResult result = assertInstanceOf(McpToolResult.class, tools().flags());
            JsonNode out = conforming("flags", result);

            assertTrue(result.text().length() <= McpToolOutput.MAX_CHARS, "length " + result.text().length());
            assertEquals(1, out.get("flagsByOrigin").get("Command line").size());
            int shownDefaults = out.get("flagsByOrigin").get("Default").size();
            assertEquals(800 - shownDefaults, out.get("omittedFlags").asInt());
            assertTrue(shownDefaults < 800, "shown " + shownDefaults);
        }
    }

    /** The data paths the family's tools take, each checked against the schema it advertises. */
    @Nested
    class Schemas {

        /** A fixed ranking says how many it ranks, so a short list is never read as the whole. */
        @Test
        void aFixedRankingStatesItsCapInTheSchema() {
            JsonNode gc = schemaOf("gc").at("/properties/dashboard/properties/longestCollections/description");
            JsonNode offenders = schemaOf("safepoints").at("/properties/dashboard/properties/offenders/description");
            JsonNode flagged = schemaOf("security").at("/properties/dashboard/properties/flagged/description");

            assertTrue(gc.asString().contains("10"), gc.toString());
            assertTrue(offenders.asString().contains("15"), offenders.toString());
            assertTrue(flagged.asString().contains("15"), flagged.toString());
        }

        /**
         * The twelve plain sections publish the envelope SectionHeader defines, plus their dashboard,
         * and nothing drifts: same components, same order, same types and descriptions.
         */
        @Test
        void everySectionAnswerIsTheSharedEnvelopeAroundItsDashboard() throws Exception {
            List<String> envelope = Arrays.stream(SectionHeader.class.getRecordComponents())
                    .map(RecordComponent::getName)
                    .toList();
            List<String> expected = new ArrayList<>(envelope);
            expected.add(expected.indexOf("followUp"), "dashboard");
            int checked = 0;
            for (Class<?> section : JvmSection.class.getPermittedSubclasses()) {
                if (section == AutoAnalysisSection.class) {
                    continue;
                }
                Class<?> answer = Class.forName(section.getName() + "$Answer");
                RecordComponent[] components = answer.getRecordComponents();
                assertEquals(expected, Arrays.stream(components).map(RecordComponent::getName).toList(),
                        answer.getName());
                for (RecordComponent component : components) {
                    if (component.getName().equals("dashboard")) {
                        assertEquals(dashboardOf(section), component.getType(), answer.getName());
                        assertTrue(component.isAnnotationPresent(McpNullable.class), answer.getName());
                        assertEquals(SectionHeader.DASHBOARD, component.getAnnotation(McpDescription.class).value());
                        continue;
                    }
                    RecordComponent shared = SectionHeader.class.getRecordComponents()[envelope.indexOf(component.getName())];
                    assertEquals(shared.getType(), component.getType(), answer.getName() + "." + component.getName());
                    assertEquals(SECTION_DESCRIPTIONS.get(component.getName()), descriptionOf(component),
                            answer.getName() + "." + component.getName());
                    assertEquals(NULLABLE_ENVELOPE.contains(component.getName()),
                            component.isAnnotationPresent(McpNullable.class), answer.getName() + "." + component.getName());
                }
                checked++;
            }
            assertEquals(12, checked);
        }

        /** The auto-analysis answer differs in its status and operation, and keeps the shared parts' wording. */
        @Test
        void theAutoAnalysisAnswerSharesTheEnvelopesDescriptions() {
            for (RecordComponent component : AutoAnalysisSection.Answer.class.getRecordComponents()) {
                if (SECTION_DESCRIPTIONS.containsKey(component.getName())) {
                    assertEquals(SECTION_DESCRIPTIONS.get(component.getName()), descriptionOf(component),
                            component.getName());
                }
            }
        }

        private static final Map<String, String> SECTION_DESCRIPTIONS = new LinkedHashMap<>();
        private static final Set<String> NULLABLE_ENVELOPE = Set.of("reason");

        static {
            SECTION_DESCRIPTIONS.put("status", null);
            SECTION_DESCRIPTIONS.put("reason", SectionHeader.REASON);
            SECTION_DESCRIPTIONS.put("profileId", null);
            SECTION_DESCRIPTIONS.put("section", SectionHeader.SECTION);
            SECTION_DESCRIPTIONS.put("title", null);
            SECTION_DESCRIPTIONS.put("followUp", null);
            SECTION_DESCRIPTIONS.put("uiLink", SectionHeader.UI_LINK);
        }

        private static String descriptionOf(RecordComponent component) {
            McpDescription description = component.getAnnotation(McpDescription.class);
            return description == null ? null : description.value();
        }

        private static Class<?> dashboardOf(Class<?> section) {
            for (var type : section.getGenericInterfaces()) {
                if (type instanceof ParameterizedType parameterized && parameterized.getRawType() == JvmSection.class) {
                    return (Class<?>) parameterized.getActualTypeArguments()[0];
                }
            }
            throw new AssertionError("no JvmSection<D> on " + section);
        }

        @Test
        void everyToolDeclaresAnOutputSchema() {
            List<Method> tools = Arrays.stream(JvmMcpTools.class.getMethods())
                    .filter(method -> method.isAnnotationPresent(Tool.class))
                    .toList();

            assertEquals(17, tools.size(), tools.toString());
            tools.forEach(method -> assertTrue(method.isAnnotationPresent(McpOutputSchema.class),
                    method.getName() + " declares no outputSchema"));
        }
    }
}
