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
import cafe.jeffrey.microscope.core.mcp.tools.heap.HeapObjectIds;
import cafe.jeffrey.microscope.core.mcp.tools.heap.HistogramOrder;
import cafe.jeffrey.microscope.mcp.protocol.McpOutputSchema;
import cafe.jeffrey.microscope.mcp.protocol.McpSchemaGenerator;
import cafe.jeffrey.microscope.mcp.protocol.McpToolResult;
import cafe.jeffrey.microscope.mcp.protocol.McpToolSpec;
import cafe.jeffrey.microscope.mcp.protocol.ToolExecutionException;
import cafe.jeffrey.microscope.mcp.protocol.testing.McpSchemaConformance;
import cafe.jeffrey.microscope.persistence.api.MicroscopeCoreRepositories;
import cafe.jeffrey.profile.heapdump.model.BiggestObjectEntry;
import cafe.jeffrey.profile.heapdump.model.BiggestObjectsReport;
import cafe.jeffrey.profile.heapdump.model.CauseHint;
import cafe.jeffrey.profile.heapdump.model.ClassHistogramEntry;
import cafe.jeffrey.profile.heapdump.model.ClassInstanceEntry;
import cafe.jeffrey.profile.heapdump.model.ClassInstancesResponse;
import cafe.jeffrey.profile.heapdump.model.ClassLoaderLeakChain;
import cafe.jeffrey.profile.heapdump.model.ClassLoaderLeakSummary;
import cafe.jeffrey.profile.heapdump.model.ClassLoaderReport;
import cafe.jeffrey.profile.heapdump.model.CollectionAnalysisReport;
import cafe.jeffrey.profile.heapdump.model.CollectionStats;
import cafe.jeffrey.profile.heapdump.model.ComponentEntry;
import cafe.jeffrey.profile.heapdump.model.ConsumerEntry;
import cafe.jeffrey.profile.heapdump.model.ConsumerReport;
import cafe.jeffrey.profile.heapdump.model.DominatedClassEntry;
import cafe.jeffrey.profile.heapdump.model.DominatorNode;
import cafe.jeffrey.profile.heapdump.model.DominatorTreeResponse;
import cafe.jeffrey.profile.heapdump.model.GCRootPath;
import cafe.jeffrey.profile.heapdump.model.GCRootSummary;
import cafe.jeffrey.profile.heapdump.model.HeapSummary;
import cafe.jeffrey.profile.heapdump.model.HeapThreadInfo;
import cafe.jeffrey.profile.heapdump.model.HeapThreadState;
import cafe.jeffrey.profile.heapdump.model.HintKind;
import cafe.jeffrey.profile.heapdump.model.InstanceDetail;
import cafe.jeffrey.profile.heapdump.model.InstanceField;
import cafe.jeffrey.profile.heapdump.model.InstanceTreeNode;
import cafe.jeffrey.profile.heapdump.model.InstanceTreeResponse;
import cafe.jeffrey.profile.heapdump.model.LeakSuspect;
import cafe.jeffrey.profile.heapdump.model.LeakSuspectsReport;
import cafe.jeffrey.profile.heapdump.model.PathStep;
import cafe.jeffrey.profile.heapdump.model.SortBy;
import cafe.jeffrey.profile.heapdump.model.StringAnalysisReport;
import cafe.jeffrey.profile.heapdump.model.StringDeduplicationEntry;
import cafe.jeffrey.profile.heapdump.view.SqlQueryResult;
import cafe.jeffrey.profile.mcp.McpNextToolConformance;
import cafe.jeffrey.profile.mcp.McpToolOutput;
import cafe.jeffrey.profile.mcp.ReflectiveToolset;
import cafe.jeffrey.shared.common.Json;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
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
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.IntStream;

import static cafe.jeffrey.microscope.core.mcp.AdvertisedFamiliesFixture.EVERY_FAMILY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class HeapDumpMcpToolsTest {

    private static final String PROFILE_ID = "p-1";
    private static final String NODE = "com.acme.Node";
    private static final String HOLDER = "com.acme.Holder";
    private static final Instant DUMPED_AT = Instant.parse("2026-03-01T12:00:00Z");

    /** The tools whose text stays the fixed-width rendering, ending with the shared footer. */
    private static final Set<String> TEXT_TOOLS = Set.of(
            "getHeapSummary", "getClassHistogram", "getBiggestObjects", "getLeakSuspects",
            "getClassLoaderLeakChains", "getTopConsumers", "getStringAnalysis", "getCollectionAnalysis",
            "getThreads", "getGCRootSummary", "browseClassInstances", "getInstanceDetail",
            "getDominatorTreeRoots", "getDominatorTreeChildren", "getPathToGCRoot", "getReferrers");

    @Mock
    HeapDumpToolsDelegate delegate;

    @BeforeEach
    void bindRequest() {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
        when(delegate.objectExists(anyLong())).thenReturn(true);
        when(delegate.gcRootKind(anyLong())).thenReturn(Optional.empty());
    }

    @AfterEach
    void unbindRequest() {
        RequestContextHolder.resetRequestAttributes();
    }

    private HeapDumpMcpTools tools() {
        return tools(EVERY_FAMILY);
    }

    private HeapDumpMcpTools tools(AdvertisedFamilies advertised) {
        return new HeapDumpMcpTools(delegate, PROFILE_ID, advertised);
    }

    /**
     * The answer's structured content against the schema the tool advertises, its link against the
     * router, its text against the record, and every next call against the tools that take it.
     */
    private static JsonNode conforming(String method, McpToolResult result) {
        JsonNode structured = result.structuredContent();
        assertNotNull(structured, method + " answered without structured content");
        McpSchemaConformance.assertConforms(structured, schemaOf(method));
        if (structured.has("uiLink")) {
            UiLinkRoutes.assertResolves(structured.get("uiLink").asString());
        }
        if (TEXT_TOOLS.contains(method)) {
            MarkdownFooters.assertRenderedFrom(result.text(), structured);
        } else {
            assertEquals(Json.toString(structured), result.text(), "the text is the record's own JSON");
        }
        McpNextToolConformance.assertFollowable(structured, reachable());
        return structured;
    }

    private static JsonNode schemaOf(String method) {
        Method tool = Arrays.stream(HeapDumpMcpTools.class.getMethods())
                .filter(candidate -> candidate.getName().equals(method))
                .findFirst()
                .orElseThrow();
        return McpSchemaGenerator.schemaOf(tool.getAnnotation(McpOutputSchema.class).value());
    }

    /** Every tool the family routes to, as the installation would advertise it. */
    static List<McpToolSpec> reachable() {
        return CatalogueSpecs.of(
                CatalogueSpecs.profileScoped(HeapDumpMcpTools.class, AdvertisedFamilies.HEAP),
                CatalogueSpecs.profileScoped(HeapComputeMcpTools.class, AdvertisedFamilies.HEAP),
                CatalogueSpecs.profileScoped(HeapOqlMcpTools.class, AdvertisedFamilies.HEAP),
                CatalogueSpecs.profileScoped(HeapDiffMcpTools.class, AdvertisedFamilies.HEAP),
                CatalogueSpecs.profileScoped(ProfileMcpTools.class, AdvertisedFamilies.PROFILES),
                CatalogueSpecs.served(new ProfilesMcpTools(mock(MicroscopeCoreRepositories.class), EVERY_FAMILY),
                        AdvertisedFamilies.PROFILES),
                CatalogueSpecs.served(new OperationsMcpTools(new McpOperationRegistry(Clock.systemUTC()), kind -> true),
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

    private static DominatorNode node(long objectId, boolean hasChildren) {
        return new DominatorNode(objectId, NODE, Map.of(), null, 16, 64, 0.1, hasChildren, null, List.of());
    }

    private static InstanceTreeNode referrer(long objectId) {
        return new InstanceTreeNode(objectId, HOLDER, null, 16, null, "value", "REFERRER", false, 0);
    }

    private static ClassInstanceEntry instance(long objectId) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("size", "3");
        return new ClassInstanceEntry(objectId, 16, null, params, null, null);
    }

    private static PathStep step(long objectId, String className, String field) {
        return new PathStep(objectId, className, field, 16, 0, Map.of(), false);
    }

    /**
     * An HPROF object id is a 64-bit address, beyond the 2^53 a JSON number carries exactly, so every
     * id is a decimal string, in and out, and one handed out is accepted back unchanged.
     */
    @Nested
    class ObjectIds {

        @Test
        void anIdPastTheExactJsonRangeRoundTripsAsADecimalString() {
            long beyondJson = (1L << 60) + 7;
            when(delegate.getPathsToGCRoot(beyondJson, true, 3)).thenReturn(List.of());

            JsonNode out = conforming("getPathToGCRoot",
                    tools().getPathToGCRoot(HeapObjectIds.format(beyondJson), null));

            assertEquals(Long.toString(beyondJson), out.get("objectId").asString());
            verify(delegate).getPathsToGCRoot(beyondJson, true, 3);
        }

        @Test
        void aHighAddressIsWrittenUnsignedAndReadBack() {
            String written = HeapObjectIds.format(-1L);

            assertEquals("18446744073709551615", written);
            assertEquals(-1L, HeapObjectIds.parse(written));
        }

        @Test
        void refusesANonNumericIdWithTheFormItExpects() {
            IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                    () -> tools().getInstanceDetail("0x1f"));

            assertTrue(error.getMessage().contains("decimal"), error.getMessage());
            assertTrue(error.getMessage().contains("0x1f"), error.getMessage());
        }

        @Test
        void aMissingObjectIdNamesTheToolsThatHandOutObjectIds() {
            IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                    () -> tools().getReferrers(null, null, null));

            assertTrue(error.getMessage().contains("heap_browseClassInstances"), error.getMessage());
            assertTrue(error.getMessage().contains("heap_getDominatorTreeRoots"), error.getMessage());
        }

        /** An id no object has is a correctable argument error, on every tool that takes one. */
        @Test
        void refusesAnIdNoObjectHasOnEveryObjectTool() {
            when(delegate.objectExists(42L)).thenReturn(false);
            HeapDumpMcpTools tools = tools();

            List<IllegalArgumentException> errors = List.of(
                    assertThrows(IllegalArgumentException.class, () -> tools.getInstanceDetail("42")),
                    assertThrows(IllegalArgumentException.class, () -> tools.getPathToGCRoot("42", null)),
                    assertThrows(IllegalArgumentException.class, () -> tools.getReferrers("42", null, null)),
                    assertThrows(IllegalArgumentException.class, () -> tools.getDominatorTreeChildren("42", null, null)));

            for (IllegalArgumentException error : errors) {
                assertTrue(error.getMessage().contains("42"), error.getMessage());
                assertTrue(error.getMessage().contains("heap_browseClassInstances"), error.getMessage());
            }
            verify(delegate, never()).getInstanceDetail(anyLong(), anyBoolean());
            verify(delegate, never()).getPathsToGCRoot(anyLong(), anyBoolean(), anyInt());
            verify(delegate, never()).getReferrers(anyLong(), anyInt(), anyInt());
            verify(delegate, never()).getDominatorTreeChildren(anyLong(), anyInt(), anyInt());
        }

        @Test
        void everyObjectIdInputIsAString() {
            for (McpToolSpec spec : new ReflectiveToolset(tools(), "heap").specs()) {
                JsonNode objectId = spec.inputSchema().path("properties").path("objectId");
                if (!objectId.isMissingNode()) {
                    assertEquals("string", objectId.path("type").asString(), spec.name());
                }
            }
        }
    }

    @Nested
    class Overview {

        @Test
        void summarisesTheHeapWithTheDumpTimeAsEpochMilliseconds() {
            when(delegate.getSummary()).thenReturn(new HeapSummary(1_024, 10, 3, 2, DUMPED_AT));

            JsonNode out = conforming("getHeapSummary", tools().getHeapSummary());

            assertEquals(1_024, out.get("totalLiveBytes").asLong());
            assertEquals(DUMPED_AT.toEpochMilli(), out.get("dumpTakenAtEpochMs").asLong());
            assertTrue(out.get("uiLink").asString().endsWith("/profiles/" + PROFILE_ID + "/heap-dump/overview"));
            assertTrue(nextTools(out).contains("heap_getClassHistogram"), out.toString());
        }

        @Test
        void aDumpWithoutATimeSaysNullRatherThanUnknown() {
            when(delegate.getSummary()).thenReturn(new HeapSummary(1_024, 10, 3, 2, null));

            JsonNode out = conforming("getHeapSummary", tools().getHeapSummary());

            assertTrue(out.get("dumpTakenAtEpochMs").isNull(), out.toString());
        }

        @Test
        void propagatesDelegateFailuresAsToolErrors() {
            when(delegate.getSummary()).thenThrow(new IllegalStateException("broken heap index"));

            ToolExecutionException error = assertThrows(ToolExecutionException.class, () -> tools().getHeapSummary());

            assertTrue(error.getMessage().contains("broken heap index"), error.getMessage());
        }

        @Test
        void ranksTheHistogramAndRoutesTheTopClassToItsInstances() {
            when(delegate.getClassHistogram(2, SortBy.COUNT)).thenReturn(List.of(
                    new ClassHistogramEntry(NODE, 900, 14_400, List.of()),
                    new ClassHistogramEntry(HOLDER, 10, 160, List.of())));

            JsonNode out = conforming("getClassHistogram", tools().getClassHistogram(2, HistogramOrder.COUNT));

            assertEquals("COUNT", out.get("order").asString());
            assertEquals(NODE, out.get("classes").get(0).get("className").asString());
            assertEquals(14_400, out.get("classes").get(0).get("shallowBytes").asLong());
            assertEquals(NODE, call(out, "heap_browseClassInstances").get("className").asString());
            assertTrue(out.get("uiLink").asString().endsWith("/heap-dump/histogram"), out.toString());
        }

        /** A list short of its cap left nothing out; one at the cap cannot tell, and says null. */
        @Test
        void countsWhatTheHistogramCapLeftOutOnlyWhenItCanKnow() {
            when(delegate.getClassHistogram(anyInt(), any())).thenReturn(
                    List.of(new ClassHistogramEntry(NODE, 900, 14_400, List.of())));

            JsonNode shortOfCap = conforming("getClassHistogram", tools().getClassHistogram(5, null));
            JsonNode atCap = conforming("getClassHistogram", tools().getClassHistogram(1, null));

            assertEquals(0, shortOfCap.get("omittedClasses").asInt());
            assertTrue(atCap.get("omittedClasses").isNull(), atCap.toString());
            assertTrue(tools().getClassHistogram(1, null).text().contains("more classes may exist"));
        }

        @ParameterizedTest
        @ValueSource(strings = {"", " ", "\t\n", " "})
        void usesTheDefaultHistogramOrderForABlankArgumentThroughTheReflectiveAdapter(String sortBy) {
            when(delegate.getClassHistogram(anyInt(), any())).thenReturn(List.of());

            McpToolResult result = new ReflectiveToolset(tools(), "heap").callResult(
                    "heap_getClassHistogram", Json.createObject().put("sortBy", sortBy));

            assertEquals("SIZE", result.structuredContent().get("order").asString());
            verify(delegate).getClassHistogram(50, SortBy.SIZE);
        }

        /** The order an answer reports is a value the input takes back, in any case. */
        @Test
        void theReportedOrderIsAcceptedAsInput() {
            when(delegate.getClassHistogram(anyInt(), any())).thenReturn(List.of());
            ReflectiveToolset toolset = new ReflectiveToolset(tools(), "heap");

            String order = toolset.callResult("heap_getClassHistogram", Json.createObject().put("sortBy", "count"))
                    .structuredContent().get("order").asString();
            toolset.callResult("heap_getClassHistogram", Json.createObject().put("sortBy", order));

            verify(delegate, times(2)).getClassHistogram(50, SortBy.COUNT);
        }

        @Test
        void listsTheGcRootTypesLargestFirst() {
            Map<String, Long> roots = new LinkedHashMap<>();
            roots.put("JNI_GLOBAL", 3L);
            roots.put("THREAD_OBJ", 40L);
            when(delegate.getGCRootSummary()).thenReturn(new GCRootSummary(roots, 43));

            JsonNode out = conforming("getGCRootSummary", tools().getGCRootSummary());

            assertEquals(43, out.get("totalRoots").asLong());
            assertEquals("THREAD_OBJ", out.get("rootTypes").get(0).get("rootType").asString());
            assertTrue(out.get("uiLink").asString().endsWith("/heap-dump/gc-roots"), out.toString());
        }

        @Test
        void listsTheThreadsWithTheirObjectIdsAndKeepsTheOnesRetainingMostWhenCut() {
            List<HeapThreadInfo> threads = new ArrayList<>();
            for (int i = 0; i < 250; i++) {
                threads.add(new HeapThreadInfo(1_000 + i, "worker-" + i, false, 5, (long) i, 3, 2, 64L,
                        HeapThreadState.RUNNABLE));
            }
            threads.add(new HeapThreadInfo(7, null, true, 5, null, null, null, null, null));
            when(delegate.getThreads()).thenReturn(threads);

            JsonNode out = conforming("getThreads", tools().getThreads());

            assertEquals(251, out.get("totalThreads").asInt());
            assertEquals(51, out.get("omittedThreads").asInt());
            assertEquals("worker-249", out.get("threads").get(0).get("name").asString());
            assertEquals("1249", out.get("threads").get(0).get("objectId").asString());
            assertEquals("1249", call(out, "heap_getInstanceDetail").get("objectId").asString());
            assertTrue(out.get("uiLink").asString().endsWith("/heap-dump/threads"), out.toString());
        }
    }

    /**
     * A report never computed answers NOT_RUN_YET, with the call that computes it and the report's own
     * page, rather than a prose sentence.
     */
    @Nested
    class Reports {

        private void assertNotRunYet(String method, McpToolResult result, String report, String page) {
            JsonNode out = conforming(method, result);

            assertEquals("NOT_RUN_YET", out.get("status").asString(), out.toString());
            assertTrue(out.get("reason").asString().contains("heap_prepare"), out.toString());
            JsonNode prepare = call(out, "heap_prepare");
            assertEquals(report, prepare.get("report").asString(), out.toString());
            assertEquals(PROFILE_ID, prepare.get("profileId").asString());
            assertTrue(nextTools(out).contains("heap_status"), out.toString());
            assertTrue(out.get("uiLink").asString().endsWith(page), out.toString());
            assertTrue(result.text().startsWith("status: NOT_RUN_YET\n"), result.text());
        }

        @Test
        void everyReportNeverComputedAnswersNotRunYet() {
            assertNotRunYet("getBiggestObjects", tools().getBiggestObjects(null), "BIGGEST", "/heap-dump/biggest-objects");
            assertNotRunYet("getLeakSuspects", tools().getLeakSuspects(), "LEAKS", "/heap-dump/leak-suspects");
            assertNotRunYet("getClassLoaderLeakChains", tools().getClassLoaderLeakChains(), "CLASSLOADERS",
                    "/heap-dump/classloader-analysis");
            assertNotRunYet("getTopConsumers", tools().getTopConsumers(), "CONSUMERS", "/heap-dump/consumers");
            assertNotRunYet("getStringAnalysis", tools().getStringAnalysis(), "STRINGS", "/heap-dump/string-analysis");
            assertNotRunYet("getCollectionAnalysis", tools().getCollectionAnalysis(), "COLLECTIONS",
                    "/heap-dump/collection-analysis");
        }

        @Test
        void aWithheldOperationsFamilyStillNamesTheHeapCalls() {
            JsonNode out = conforming("getLeakSuspects",
                    tools(new AdvertisedFamilies(Set.of("heap"))).getLeakSuspects());

            assertEquals(Set.of("heap_prepare", "heap_status"), nextTools(out));
        }

        @Test
        void ranksTheBiggestObjectsByIdAndRoutesTheFirstToItsGcRootPath() {
            when(delegate.getBiggestObjects(20)).thenReturn(new BiggestObjectsReport(10_000, 4_000, List.of(
                    new BiggestObjectEntry(NODE, 16, 4_000, 4711))));

            JsonNode out = conforming("getBiggestObjects", tools().getBiggestObjects(null));

            assertEquals("OK", out.get("status").asString());
            assertEquals("4711", out.get("objects").get(0).get("objectId").asString());
            assertEquals("4711", call(out, "heap_getPathToGCRoot").get("objectId").asString());
            assertEquals(0, out.get("omittedObjects").asInt());
            when(delegate.getBiggestObjects(1)).thenReturn(new BiggestObjectsReport(10_000, 4_000, List.of(
                    new BiggestObjectEntry(NODE, 16, 4_000, 4711))));
            assertTrue(tools().getBiggestObjects(1).text().contains("more objects may exist"));
        }

        @Test
        void reportsLeakSuspectsWithStringIdsAndLeavesAnUnknownAccumulationPointNull() {
            LeakSuspect suspect = new LeakSuspect(1, NODE, 4711L, 8_000, 80.0, 1, "retains 80%", null, List.of(),
                    null, null, List.of(
                    new DominatedClassEntry("a.A", 1, 10, 1.0), new DominatedClassEntry("b.B", 1, 10, 1.0),
                    new DominatedClassEntry("c.C", 1, 10, 1.0), new DominatedClassEntry("d.D", 1, 10, 1.0),
                    new DominatedClassEntry("e.E", 1, 10, 1.0), new DominatedClassEntry("f.F", 1, 10, 1.0)),
                    80.0, 99, "<bootstrap>");
            when(delegate.getLeakSuspects()).thenReturn(new LeakSuspectsReport(10_000, 8_000, List.of(suspect),
                    List.of(new ClassLoaderLeakSummary(99, "<bootstrap>", 8_000, 1))));

            McpToolResult result = tools().getLeakSuspects();
            JsonNode out = conforming("getLeakSuspects", result);

            JsonNode first = out.get("suspects").get(0);
            assertEquals("4711", first.get("objectId").asString());
            assertEquals("99", first.get("classLoaderId").asString());
            assertTrue(first.get("accumulationPoint").isNull(), first.toString());
            assertEquals(5, first.get("topContributors").size());
            assertEquals(1, first.get("omittedContributors").asInt());
            assertTrue(result.text().contains("1 more contributing class"), result.text());
            assertEquals("4711", call(out, "heap_getPathToGCRoot").get("objectId").asString());
            assertFalse(result.text().contains("Accumulation Point: null"), result.text());
        }

        @Test
        void classLoaderLeakChainsCarryTheirGcRootPath() {
            GCRootPath path = new GCRootPath(1, "java.lang.Thread", "THREAD_OBJ", "main", null, List.of(
                    step(1, "java.lang.Thread", "contextClassLoader"),
                    step(2, "org.apache.catalina.loader.WebappClassLoader", null)));
            ClassLoaderLeakChain chain = new ClassLoaderLeakChain(2, "org.apache.catalina.loader.WebappClassLoader",
                    10, 100, 1_000, path, List.of(new CauseHint(HintKind.CONTEXT_CLASSLOADER, "held by main", 1)),
                    false);
            when(delegate.getClassLoaderAnalysis()).thenReturn(new ClassLoaderReport(
                    1, 10, 0, List.of(), List.of(), List.of(chain), List.of(), Map.of(), Map.of()));

            McpToolResult result = tools().getClassLoaderLeakChains();
            JsonNode out = conforming("getClassLoaderLeakChains", result);

            JsonNode first = out.get("chains").get(0);
            assertEquals("2", first.get("classLoaderId").asString());
            assertEquals("1", first.get("gcRootPath").get("steps").get(0).get("objectId").asString());
            assertEquals("CONTEXT_CLASSLOADER", first.get("causeHints").get(0).get("kind").asString());
            assertTrue(result.text().contains("-> java.lang.Thread.contextClassLoader (ID: 1)"), result.text());
        }

        @Test
        void noSuspiciousClassLoaderIsAnEmptyListNotAStatus() {
            when(delegate.getClassLoaderAnalysis()).thenReturn(new ClassLoaderReport(
                    1, 10, 0, List.of(), List.of(), List.of(), List.of(), Map.of(), Map.of()));

            JsonNode out = conforming("getClassLoaderLeakChains", tools().getClassLoaderLeakChains());

            assertEquals("OK", out.get("status").asString());
            assertEquals(0, out.get("chains").size());
        }

        @Test
        void topConsumersKeepTwentyOfEachAndCountTheRest() {
            List<ConsumerEntry> consumers = IntStream.range(0, 25)
                    .mapToObj(i -> new ConsumerEntry("com.acme.p" + i, 7, "app", 1_000 - i, 10, 2, 3))
                    .toList();
            List<ComponentEntry> components = IntStream.range(0, 3)
                    .mapToObj(i -> new ComponentEntry("com.acme.p" + i, 1_000 - i, 10, 2, 3))
                    .toList();
            when(delegate.getConsumerReport()).thenReturn(new ConsumerReport(50_000, consumers, components));

            McpToolResult result = tools().getTopConsumers();
            JsonNode out = conforming("getTopConsumers", result);

            assertEquals(20, out.get("consumers").size());
            assertEquals(5, out.get("omittedConsumers").asInt());
            assertEquals("7", out.get("consumers").get(0).get("classLoaderId").asString());
            assertTrue(result.text().contains("5 more"), result.text());
        }

        /**
         * The engine ranks consumers by shallow size and computes neither their retained size nor the
         * per-package component report, so the answer carries neither rather than zeros.
         */
        @Test
        void topConsumersClaimOnlyWhatTheEngineComputes() {
            when(delegate.getConsumerReport()).thenReturn(new ConsumerReport(50_000,
                    List.of(new ConsumerEntry("com.acme", 7, "app", 0, 4_096, 2, 3)), List.of()));

            McpToolResult result = tools().getTopConsumers();
            JsonNode out = conforming("getTopConsumers", result);

            assertFalse(out.get("consumers").get(0).has("retainedBytes"), out.toString());
            assertEquals(4_096, out.get("consumers").get(0).get("shallowBytes").asLong());
            assertTrue(out.get("components").isNull(), out.toString());
            assertTrue(out.get("omittedComponents").isNull(), out.toString());
            assertTrue(result.text().contains("shallow"), result.text());
            assertTrue(result.text().contains("not computed"), result.text());
            assertTrue(schemaOf("getTopConsumers").path("properties").path("consumers").path("description")
                    .asString().contains("shallow"));
        }

        /** The engine keeps its own top 100; a list at that cap cannot say what it left out. */
        @Test
        void aConsumerListAtTheEnginesCapCountsNothingItCannotKnow() {
            List<ConsumerEntry> consumers = IntStream.range(0, 100)
                    .mapToObj(i -> new ConsumerEntry("com.acme.p" + i, 7, "app", 0, 1_000 - i, 2, 3))
                    .toList();
            when(delegate.getConsumerReport()).thenReturn(new ConsumerReport(50_000, consumers, List.of()));

            JsonNode out = conforming("getTopConsumers", tools().getTopConsumers());

            assertTrue(out.get("omittedConsumers").isNull(), out.toString());
        }

        @Test
        void aReportNotRunYetCountsNothingItLeftOut() {
            assertTrue(conforming("getTopConsumers", tools().getTopConsumers()).get("omittedConsumers").isNull());
            assertTrue(conforming("getTopConsumers", tools().getTopConsumers()).get("omittedComponents").isNull());
            assertTrue(conforming("getStringAnalysis", tools().getStringAnalysis()).get("omittedOpportunities").isNull());
            assertTrue(conforming("getClassLoaderLeakChains", tools().getClassLoaderLeakChains())
                    .get("omittedChains").isNull());
        }

        /**
         * The engine keeps a 200-character preview of a long string and marks the cut with an
         * ellipsis; the answer passes the preview on as it is and says whether it was cut.
         */
        @Test
        void aStringOpportunitySaysWhetherItsPreviewWasCut() {
            String preview = "x".repeat(200) + "\u2026";
            when(delegate.getStringAnalysis()).thenReturn(new StringAnalysisReport(10, 400, 5, 5, 2, 100, 300,
                    List.of(), List.of(), List.of(), List.of(
                    new StringDeduplicationEntry(preview, 4, 5_000, 15_000),
                    new StringDeduplicationEntry("short", 3, 40, 80)),
                    List.of(), 100));

            JsonNode out = conforming("getStringAnalysis", tools().getStringAnalysis());

            JsonNode cut = out.get("opportunities").get(0);
            assertEquals(preview, cut.get("content").asString());
            assertTrue(cut.get("contentTruncated").asBoolean());
            assertFalse(out.get("opportunities").get(1).get("contentTruncated").asBoolean());
            assertFalse(cut.has("contentLength"), cut.toString());
            assertEquals(0, out.get("omittedOpportunities").asInt());
            assertEquals(300, out.get("totals").get("potentialSavingsBytes").asLong());
        }

        private StringAnalysisReport opportunities(int count, Integer topN) {
            List<StringDeduplicationEntry> entries = IntStream.range(0, count)
                    .mapToObj(i -> new StringDeduplicationEntry("s" + i, 2, 40, 40))
                    .toList();
            return new StringAnalysisReport(10, 400, 5, 5, 2, 100, 300, List.of(), List.of(), List.of(), entries,
                    List.of(), topN);
        }

        /** A report built from the UI with topN=10 and ten opportunities may have cut more: unknown. */
        @Test
        void aStringReportAtItsOwnCapCannotSayWhatItLeftOut() {
            when(delegate.getStringAnalysis()).thenReturn(opportunities(10, 10));

            JsonNode out = conforming("getStringAnalysis", tools().getStringAnalysis());

            assertEquals(10, out.get("opportunities").size());
            assertTrue(out.get("omittedOpportunities").isNull(), out.toString());
        }

        /** A pipeline-built report short of its cap of 100 is whole: the count is exact. */
        @Test
        void aStringReportShortOfItsCapCountsExactlyWhatTheAnswerLeftOut() {
            when(delegate.getStringAnalysis()).thenReturn(opportunities(25, 100));

            JsonNode out = conforming("getStringAnalysis", tools().getStringAnalysis());

            assertEquals(20, out.get("opportunities").size());
            assertEquals(5, out.get("omittedOpportunities").asInt());
        }

        /** A report stored before the cap was recorded has unknown provenance, so its count is unknown. */
        @Test
        void aStringReportStoredBeforeItsCapWasRecordedCountsNothing() {
            StringAnalysisReport stored = Json.mapper().readValue(
                    "{\"totalStrings\":1,\"opportunities\":[{\"content\":\"a\",\"count\":2,"
                            + "\"arraySize\":8,\"savings\":8}]}", StringAnalysisReport.class);
            when(delegate.getStringAnalysis()).thenReturn(stored);

            JsonNode out = conforming("getStringAnalysis", tools().getStringAnalysis());

            assertTrue(stored.topN() == null, "a stored report without the field reads its cap as unknown");
            assertTrue(out.get("omittedOpportunities").isNull(), out.toString());
        }

        @Test
        void reportsCollectionsByType() {
            when(delegate.getCollectionAnalysis()).thenReturn(new CollectionAnalysisReport(12, 4, 900, null,
                    List.of(new CollectionStats("java.util.HashMap", 12, 4, 900, 0.25, null)), List.of()));

            JsonNode out = conforming("getCollectionAnalysis", tools().getCollectionAnalysis());

            assertEquals(4, out.get("totals").get("emptyCollections").asInt());
            assertEquals("java.util.HashMap", out.get("types").get(0).get("collectionType").asString());
            assertEquals("java.util.HashMap", call(out, "heap_browseClassInstances").get("className").asString());
        }
    }

    /**
     * A page hands back the cursor that reads the next one instead of an offset to compute, bound to
     * the filters it was returned for.
     */
    @Nested
    class Paging {

        @Test
        void classInstancesHandBackACursorThatReadsTheNextPage() {
            when(delegate.getClassInstances(NODE, 2, 0, false)).thenReturn(
                    new ClassInstancesResponse(NODE, 5, List.of(instance(1), instance(2)), true));
            when(delegate.getClassInstances(NODE, 2, 2, false)).thenReturn(
                    new ClassInstancesResponse(NODE, 5, List.of(instance(3), instance(4)), true));

            JsonNode first = conforming("browseClassInstances", tools().browseClassInstances(NODE, 2, null));
            String cursor = first.get("nextCursor").asString();
            JsonNode second = conforming("browseClassInstances", tools().browseClassInstances(NODE, 2, cursor));

            assertTrue(first.get("hasMore").asBoolean());
            assertEquals(5, first.get("totalInstances").asInt());
            assertEquals("1", first.get("instances").get(0).get("objectId").asString());
            assertEquals("3", second.get("instances").get(0).get("objectId").asString());
            JsonNode next = call(first, "heap_browseClassInstances");
            assertEquals(cursor, next.get("cursor").asString());
            assertEquals(NODE, next.get("className").asString());
        }

        @Test
        void theLastPageHasNoCursor() {
            when(delegate.getClassInstances(NODE, 20, 0, false)).thenReturn(
                    new ClassInstancesResponse(NODE, 1, List.of(instance(1)), false));

            JsonNode out = conforming("browseClassInstances", tools().browseClassInstances(NODE, null, null));

            assertFalse(out.get("hasMore").asBoolean());
            assertTrue(out.get("nextCursor").isNull());
            assertFalse(nextTools(out).contains("heap_browseClassInstances"), out.toString());
        }

        @Test
        void aCursorForAnotherClassIsRefused() {
            when(delegate.getClassInstances(NODE, 2, 0, false)).thenReturn(
                    new ClassInstancesResponse(NODE, 5, List.of(instance(1), instance(2)), true));
            String cursor = tools().browseClassInstances(NODE, 2, null).structuredContent().get("nextCursor").asString();

            IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                    () -> tools().browseClassInstances(HOLDER, 2, cursor));

            assertTrue(error.getMessage().contains("other filters"), error.getMessage());
        }

        @Test
        void referrersPageWithACursor() {
            when(delegate.getReferrers(42, 2, 0)).thenReturn(
                    InstanceTreeResponse.of(null, List.of(referrer(1), referrer(2)), true, 30));
            when(delegate.getReferrers(42, 2, 2)).thenReturn(
                    InstanceTreeResponse.of(null, List.of(referrer(3)), false, 30));

            JsonNode first = conforming("getReferrers", tools().getReferrers("42", 2, null));
            JsonNode second = conforming("getReferrers",
                    tools().getReferrers("42", 2, first.get("nextCursor").asString()));

            assertEquals(30, first.get("totalReferrers").asInt());
            assertEquals("42", first.get("objectId").asString());
            assertEquals("1", first.get("referrers").get(0).get("objectId").asString());
            assertEquals("3", second.get("referrers").get(0).get("objectId").asString());
            assertTrue(first.get("uiLink").asString().contains("heap-dump/gc-root-path?objectId=42"), first.toString());
        }

        /** No referrers at all is an answer about the object: an empty list, and no next page. */
        @Test
        void anObjectWithNoReferrersAnswersWithAnEmptyList() {
            when(delegate.getReferrers(42, 20, 0)).thenReturn(InstanceTreeResponse.of(null, List.of(), false, 0));

            JsonNode out = conforming("getReferrers", tools().getReferrers("42", null, null));

            assertEquals(0, out.get("referrers").size());
            assertFalse(out.get("hasMore").asBoolean());
        }

        /** The engine does not count a node's children, so the page continues on its own say-so. */
        @Test
        void dominatorChildrenPageWithoutATotal() {
            when(delegate.getDominatorTreeChildren(42, 2, 0)).thenReturn(
                    new DominatorTreeResponse(List.of(node(1, true), node(2, false)), 1024, true, true));
            when(delegate.getDominatorTreeChildren(42, 2, 2)).thenReturn(
                    new DominatorTreeResponse(List.of(node(3, false)), 1024, true, false));

            JsonNode first = conforming("getDominatorTreeChildren", tools().getDominatorTreeChildren("42", 2, null));
            JsonNode second = conforming("getDominatorTreeChildren",
                    tools().getDominatorTreeChildren("42", 2, first.get("nextCursor").asString()));

            assertEquals("42", first.get("parentObjectId").asString());
            assertEquals("1", call(first, "heap_getDominatorTreeChildren").get("objectId").asString());
            assertEquals("3", second.get("children").get(0).get("objectId").asString());
            assertFalse(second.get("hasMore").asBoolean());
        }

        @Test
        void noPagedToolTakesAnOffsetAnyMore() {
            for (McpToolSpec spec : new ReflectiveToolset(tools(), "heap").specs()) {
                assertFalse(spec.inputSchema().path("properties").has("offset"), spec.name());
            }
        }

        /**
         * One clamp convention across every tool: zero or below is the default, above the cap is the
         * cap.
         */
        @Test
        void aNonPositiveLimitTakesTheDefaultAndALargeOneTheCap() {
            when(delegate.getReferrers(anyLong(), anyInt(), anyInt())).thenReturn(
                    InstanceTreeResponse.of(null, List.of(referrer(1)), false, 1));
            when(delegate.getClassHistogram(anyInt(), any())).thenReturn(List.of());
            when(delegate.getPathsToGCRoot(anyLong(), anyBoolean(), anyInt())).thenReturn(List.of());

            HeapDumpMcpTools tools = tools();
            tools.getReferrers("42", 0, null);
            tools.getReferrers("42", 5_000, null);
            tools.getClassHistogram(-3, null);
            tools.getPathToGCRoot("42", 0);

            verify(delegate).getReferrers(42, 20, 0);
            verify(delegate).getReferrers(42, 50, 0);
            verify(delegate).getClassHistogram(50, SortBy.SIZE);
            verify(delegate).getPathsToGCRoot(42, true, 3);
        }
    }

    @Nested
    class SingleObjects {

        @Test
        void opensAnInstanceWithItsReferencesAsObjectIds() {
            when(delegate.getInstanceDetail(42, false)).thenReturn(new InstanceDetail(42, HOLDER, null, null, null,
                    24, null, List.of(
                    InstanceField.primitive("count", "int", "3"),
                    InstanceField.reference("next", NODE, NODE + "@4711", 4711L, NODE)), List.of()));

            McpToolResult result = tools().getInstanceDetail("42");
            JsonNode out = conforming("getInstanceDetail", result);

            assertFalse(out.has("status"), "an unknown id is refused, so a found object needs no status");
            assertEquals(HOLDER, out.get("instance").get("className").asString());
            JsonNode next = out.get("instance").get("fields").get(1);
            assertEquals("4711", next.get("referencedObjectId").asString());
            assertTrue(out.get("instance").get("fields").get(0).get("referencedObjectId").isNull());
            assertEquals("42", call(out, "heap_getPathToGCRoot").get("objectId").asString());
            assertTrue(out.get("uiLink").asString().contains("heap-dump/gc-root-path?objectId=42"), out.toString());
            assertTrue(result.text().contains("Instance Detail (Object ID: 42)"), result.text());
        }

        @Test
        void dominatorRootsSayWhenMoreRootsExist() {
            when(delegate.getDominatorTreeRoots(2)).thenReturn(
                    new DominatorTreeResponse(List.of(node(1, true), node(2, true)), 1024, true, true));

            JsonNode out = conforming("getDominatorTreeRoots", tools().getDominatorTreeRoots(2));

            assertTrue(out.get("truncated").asBoolean());
            assertEquals("1", out.get("roots").get(0).get("objectId").asString());
            assertEquals("1", call(out, "heap_getDominatorTreeChildren").get("objectId").asString());
            assertTrue(out.get("uiLink").asString().endsWith("/heap-dump/dominator-tree"), out.toString());
        }

        @Test
        void dominatorRootsDefaultToFiftyAndCapThere() {
            when(delegate.getDominatorTreeRoots(anyInt())).thenReturn(
                    new DominatorTreeResponse(List.of(), 0, false, false));

            HeapDumpMcpTools tools = tools();
            tools.getDominatorTreeRoots(null);
            tools.getDominatorTreeRoots(5_000);

            verify(delegate, times(2)).getDominatorTreeRoots(50);
        }

        @Test
        void aPathToAGcRootListsItsHopsByObjectId() {
            when(delegate.getPathsToGCRoot(42, true, 3)).thenReturn(List.of(new GCRootPath(1, "java.lang.Thread",
                    "THREAD_OBJ", "main", null, List.of(step(1, "java.lang.Thread", "target"), step(42, NODE, null)))));

            McpToolResult result = tools().getPathToGCRoot("42", null);
            JsonNode out = conforming("getPathToGCRoot", result);

            assertEquals("OK", out.get("status").asString());
            assertEquals("1", out.get("paths").get(0).get("rootObjectId").asString());
            assertEquals(0, out.get("paths").get(0).get("omittedSteps").asInt());
            assertEquals("42", call(out, "heap_getReferrers").get("objectId").asString());
            assertTrue(result.text().contains("-> java.lang.Thread.target (ID: 1)"), result.text());
        }

        /** A path through a long linked list keeps both ends and counts the hops between them. */
        @Test
        void aVeryLongPathKeepsItsEndsAndCountsTheMiddle() {
            List<PathStep> steps = new ArrayList<>();
            for (int i = 0; i < 10_000; i++) {
                steps.add(step(i + 1, NODE, "next"));
            }
            when(delegate.getPathsToGCRoot(eq(42L), anyBoolean(), anyInt()))
                    .thenReturn(List.of(new GCRootPath(1, NODE, "STATIC", null, null, steps)));

            McpToolResult result = tools().getPathToGCRoot("42", null);
            JsonNode out = conforming("getPathToGCRoot", result);

            JsonNode path = out.get("paths").get(0);
            assertEquals(40, path.get("steps").size());
            assertEquals(9_960, path.get("omittedSteps").asInt());
            assertEquals("1", path.get("steps").get(0).get("objectId").asString());
            assertEquals("10000", path.get("steps").get(39).get("objectId").asString());
            assertTrue(result.text().length() <= McpToolOutput.MAX_CHARS, "text " + result.text().length());
        }

        @Test
        void anObjectWithNoPathToARootIsAStatus() {
            when(delegate.getPathsToGCRoot(42, true, 3)).thenReturn(List.of());

            McpToolResult result = tools().getPathToGCRoot("42", null);
            JsonNode out = conforming("getPathToGCRoot", result);

            assertEquals("NO_PATH", out.get("status").asString());
            assertTrue(out.get("reason").asString().contains("within 100 hops"), out.toString());
            assertTrue(out.get("reason").asString().contains("weak references"), out.toString());
            assertFalse(out.toString().contains("not a leak"), out.toString());
            assertTrue(out.get("targetRootKind").isNull());
            assertTrue(result.text().startsWith("status: NO_PATH\n"), result.text());
        }

        /** An object that is itself a GC root is kept alive directly, and the answer says so. */
        @Test
        void anObjectThatIsItselfAGcRootSaysSo() {
            when(delegate.getPathsToGCRoot(42, true, 3)).thenReturn(List.of());
            when(delegate.gcRootKind(42L)).thenReturn(Optional.of("Thread object"));

            McpToolResult result = tools().getPathToGCRoot("42", null);
            JsonNode out = conforming("getPathToGCRoot", result);

            assertEquals("IS_GC_ROOT", out.get("status").asString());
            assertEquals("Thread object", out.get("targetRootKind").asString());
            assertTrue(out.get("reason").asString().contains("itself a GC root"), out.toString());
        }
    }

    @Nested
    class Sql {

        @Test
        void listsTheTablesWithoutALink() {
            when(delegate.executeSql(any(), anyInt())).thenReturn(new SqlQueryResult(List.of("table_name"),
                    List.of(List.of("class"), List.of("instance")), false));

            JsonNode out = conforming("listTables", tools().listTables());

            assertEquals(List.of("class", "instance"), Json.mapper().convertValue(out.get("tables"), List.class));
            assertFalse(out.has("uiLink"));
            assertEquals("class", call(out, "heap_describeTable").get("tableName").asString());
        }

        @Test
        void describesATableColumnByColumn() {
            when(delegate.executeSql(any(), anyInt())).thenReturn(new SqlQueryResult(
                    List.of("column_name", "data_type", "is_nullable"),
                    List.of(List.of("instance_id", "BIGINT", "NO"), List.of("class_id", "BIGINT", "YES")), false));

            JsonNode out = conforming("describeTable", tools().describeTable("instance"));

            assertEquals("instance_id", out.get("columns").get(0).get("name").asString());
            assertFalse(out.get("columns").get(0).get("nullable").asBoolean());
            assertTrue(out.get("columns").get(1).get("nullable").asBoolean());
        }

        @Test
        void refusesAnUnknownTable() {
            when(delegate.executeSql(any(), anyInt())).thenReturn(new SqlQueryResult(List.of(), List.of(), false));

            ToolExecutionException error = assertThrows(ToolExecutionException.class,
                    () -> tools().describeTable("nope"));

            assertTrue(error.getMessage().contains("heap_listTables"), error.getMessage());
        }

        /** A SQL NULL stays a JSON null, and a capped result says it was capped. */
        @Test
        void aQueryKeepsItsNullsAndSaysWhenItWasCapped() {
            List<String> withNull = new ArrayList<>();
            withNull.add("1");
            withNull.add(null);
            when(delegate.executeSql(any(), anyInt())).thenReturn(
                    new SqlQueryResult(List.of("a", "b"), List.of(withNull), true));

            JsonNode out = conforming("executeQuery", tools().executeQuery("SELECT 1, NULL"));

            assertTrue(out.get("rows").get(0).get(1).isNull(), out.toString());
            assertTrue(out.get("capped").asBoolean());
            assertEquals(100, out.get("rowCap").asInt());
        }

        /** Wide cells are cut, and a result that still outgrows the answer keeps the rows that fit. */
        @Test
        void fitsAQueryOfWideCellsAndSaysItWasCapped() {
            List<List<String>> rows = IntStream.range(0, 100)
                    .mapToObj(i -> Collections.nCopies(10, "v".repeat(5_000)))
                    .toList();
            when(delegate.executeSql(any(), anyInt())).thenReturn(new SqlQueryResult(
                    IntStream.range(0, 10).mapToObj(i -> "c" + i).toList(), rows, false));

            JsonNode out = conforming("executeQuery", tools().executeQuery("SELECT * FROM instance"));

            int shown = out.get("rows").size();
            assertTrue(shown > 0 && shown < 100, "rows shown: " + shown);
            assertTrue(out.get("capped").asBoolean());
            assertTrue(out.get("rows").get(0).get(0).asString().length() <= 1_000);
        }

        @Test
        void readsTheDumpMetadataWithItsParseTimeAsEpochMilliseconds() {
            when(delegate.executeSql(any(), anyInt())).thenReturn(new SqlQueryResult(
                    List.of("id_size", "hprof_version", "compressed_oops", "bytes_parsed", "record_count",
                            "warning_count", "truncated", "parser_version", "parsed_at_ms"),
                    List.of(List.of("8", "JAVA PROFILE 1.0.2", "true", "1024", "10", "0", "false", "3",
                            Long.toString(DUMPED_AT.toEpochMilli()))), false));

            JsonNode out = conforming("getDumpMetadata", tools().getDumpMetadata());

            assertEquals("OK", out.get("status").asString());
            assertEquals(8, out.get("metadata").get("idSizeBytes").asInt());
            assertTrue(out.get("metadata").get("compressedOops").asBoolean());
            assertEquals(DUMPED_AT.toEpochMilli(), out.get("metadata").get("parsedAtEpochMs").asLong());
        }

        @Test
        void aDumpWithoutMetadataIsAStatus() {
            when(delegate.executeSql(any(), anyInt())).thenReturn(new SqlQueryResult(List.of("id_size"), List.of(), false));

            JsonNode out = conforming("getDumpMetadata", tools().getDumpMetadata());

            assertEquals("NO_METADATA", out.get("status").asString());
            assertTrue(out.get("metadata").isNull());
        }

        @Test
        void theSqlToolSaysItsCapIsAlwaysOneHundredRows() {
            McpToolSpec spec = new ReflectiveToolset(tools(), "heap").specs().stream()
                    .filter(candidate -> candidate.name().equals("heap_executeQuery"))
                    .findFirst()
                    .orElseThrow();

            String schema = spec.description() + spec.inputSchema();
            assertTrue(schema.contains("always capped at 100 rows"), schema);
        }
    }

    @Test
    void everyToolDeclaresAnOutputSchema() {
        List<String> missing = Arrays.stream(HeapDumpMcpTools.class.getMethods())
                .filter(method -> method.isAnnotationPresent(Tool.class))
                .filter(method -> !method.isAnnotationPresent(McpOutputSchema.class))
                .map(Method::getName)
                .toList();

        assertEquals(List.of(), missing);
    }
}
