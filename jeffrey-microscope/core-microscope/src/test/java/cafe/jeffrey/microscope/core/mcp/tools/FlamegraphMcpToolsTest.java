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

import cafe.jeffrey.flamegraph.export.AiExportConfig;
import cafe.jeffrey.flamegraph.export.AiExportView;
import cafe.jeffrey.microscope.core.mcp.AdvertisedFamilies;
import cafe.jeffrey.microscope.mcp.protocol.McpOutputSchema;
import cafe.jeffrey.microscope.mcp.protocol.McpSchemaGenerator;
import cafe.jeffrey.microscope.mcp.protocol.McpToolResult;
import cafe.jeffrey.microscope.mcp.protocol.McpToolSpec;
import cafe.jeffrey.microscope.mcp.protocol.testing.McpSchemaConformance;
import cafe.jeffrey.microscope.model.ProfileInfo;
import cafe.jeffrey.microscope.model.RecordingEventSource;
import cafe.jeffrey.profile.common.config.GraphParameters;
import cafe.jeffrey.profile.manager.FlamegraphManager;
import cafe.jeffrey.profile.manager.ProfileManager;
import cafe.jeffrey.profile.mcp.McpNextToolConformance;
import cafe.jeffrey.profile.mcp.McpToolOutput;
import cafe.jeffrey.profile.mcp.ReflectiveToolset;
import cafe.jeffrey.profile.model.EventSummaryResult;
import cafe.jeffrey.profile.panel.JfrFlamegraphPanelProvider;
import cafe.jeffrey.profile.panel.StackSampleFlamegraphPanelProvider;
import cafe.jeffrey.shared.common.Json;
import cafe.jeffrey.shared.common.model.EventTypeName;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import java.lang.reflect.Method;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static cafe.jeffrey.microscope.core.mcp.AdvertisedFamiliesFixture.EVERY_FAMILY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FlamegraphMcpToolsTest {

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


    private static final String SAMPLE_TYPE_EXTRA = "sampleType";
    private static final String CPU = EventTypeName.EXECUTION_SAMPLE;

    /** Not the epoch itself, so an offset from the start and an instant can never be confused. */
    private static final Instant START = Instant.parse("2026-03-01T12:00:00Z");
    private static final long START_MS = START.toEpochMilli();
    private static final long END_MS = START_MS + 60_000;

    @Mock
    ProfileManager profileManager;

    @Mock
    FlamegraphManager flamegraphManager;

    private FlamegraphMcpTools tools() {
        return tools(EVERY_FAMILY);
    }

    private FlamegraphMcpTools tools(AdvertisedFamilies advertised) {
        return new FlamegraphMcpTools(
                profileManager, new JfrFlamegraphPanelProvider(), new StackSampleFlamegraphPanelProvider(),
                advertised);
    }

    private static EventSummaryResult summary(String code, long samples, long weight) {
        return summary(code, samples, weight, Map.of());
    }

    private static EventSummaryResult summary(
            String code, long samples, long weight, Map<String, String> extras) {

        EventSummaryResult.SingleResult primary =
                new EventSummaryResult.SingleResult(code, code, null, null, samples, weight, false, extras);
        return new EventSummaryResult(code, code, primary, null);
    }

    private static ProfileInfo info(RecordingEventSource source) {
        return new ProfileInfo(
                "profile-1", "project-1", "workspace-1", "Profile", source,
                START, START.plusSeconds(60), START, true, false, "recording-1");
    }

    private void profileOf(RecordingEventSource source, List<EventSummaryResult> summaries) {
        when(profileManager.info()).thenReturn(info(source));
        when(profileManager.flamegraphManager()).thenReturn(flamegraphManager);
        when(flamegraphManager.eventSummaries()).thenReturn(summaries);
    }

    private static JsonNode entry(JsonNode result, String eventType) {
        for (JsonNode node : result.get("available")) {
            if (node.get("eventType").asString().equals(eventType)) {
                return node;
            }
        }
        throw new AssertionError("no entry for " + eventType + " in " + result);
    }

    /** The answer's structured content, checked against the schema the tool advertises and its link. */
    private static JsonNode conforming(String method, McpToolResult result) {
        JsonNode structured = result.structuredContent();
        McpSchemaConformance.assertConforms(structured, schemaOf(method));
        UiLinkRoutes.assertResolves(structured.get("uiLink").asString());
        return structured;
    }

    private static JsonNode schemaOf(String method) {
        Method tool = Arrays.stream(FlamegraphMcpTools.class.getMethods())
                .filter(candidate -> candidate.getName().equals(method))
                .findFirst()
                .orElseThrow();
        return McpSchemaGenerator.schemaOf(tool.getAnnotation(McpOutputSchema.class).value());
    }

    /** Every family a flamegraph answer routes to. */
    private static List<McpToolSpec> reachable() {
        return CatalogueSpecs.of(
                CatalogueSpecs.profileScoped(FlamegraphMcpTools.class, AdvertisedFamilies.FLAMEGRAPH),
                CatalogueSpecs.profileScoped(TimelineMcpTools.class, AdvertisedFamilies.TIMELINE),
                CatalogueSpecs.profileScoped(JvmMcpTools.class, AdvertisedFamilies.JVM),
                CatalogueSpecs.profileScoped(ProfileMcpTools.class, AdvertisedFamilies.PROFILES));
    }

    private static List<String> nextTools(JsonNode structured) {
        return structured.get("followUp").get("nextTools").valueStream()
                .map(call -> call.get("tool").asString())
                .toList();
    }

    @Nested
    class JfrProfile {

        /**
         * The JFR grid always emits all eight sections, filling the empty ones with a zero-sample
         * placeholder. Offering those as valid eventTypes would send the caller after an empty tree,
         * so they are reported as gaps in what the profiler captured instead.
         */
        @Test
        void separatesRecordedTypesFromTheSectionsWithNoSamples() {
            profileOf(RecordingEventSource.JDK, List.of(
                    summary(EventTypeName.EXECUTION_SAMPLE, 4200, 0L),
                    summary(EventTypeName.OBJECT_ALLOCATION_SAMPLE, 130, 9_000_000L)));

            JsonNode root = conforming("list", tools().list());

            assertEquals("OK", root.get("status").asString());
            assertTrue(root.get("reason").isNull());
            List<String> available = root.get("available").valueStream()
                    .map(node -> node.get("eventType").asString())
                    .toList();
            assertEquals(
                    List.of(EventTypeName.EXECUTION_SAMPLE, EventTypeName.OBJECT_ALLOCATION_SAMPLE),
                    available);

            List<String> notRecorded = root.get("notRecorded").valueStream()
                    .map(node -> node.get("section").asString())
                    .toList();
            assertEquals(
                    List.of("cpu-time", "method", "wall", "native-alloc", "native-leak", "blocking"),
                    notRecorded);
            assertTrue(root.get("uiLink").asString().endsWith("/profiles/profile-1/flamegraphs/primary"));
        }

        @Test
        void carriesTheExportArgumentDefaultsOfEachEventType() {
            profileOf(RecordingEventSource.JDK, List.of(
                    summary(EventTypeName.EXECUTION_SAMPLE, 4200, 0L),
                    summary(EventTypeName.OBJECT_ALLOCATION_SAMPLE, 130, 9_000_000L),
                    summary(EventTypeName.JAVA_MONITOR_ENTER, 12, 5_000_000_000L)));

            JsonNode result = conforming("list", tools().list());

            JsonNode allocation = entry(result, EventTypeName.OBJECT_ALLOCATION_SAMPLE);
            assertEquals(130, allocation.get("samples").asLong());
            assertEquals(9_000_000L, allocation.get("weight").asLong());
            assertEquals("bytes", allocation.get("weightUnit").asString());
            assertTrue(allocation.get("defaultUseWeight").asBoolean());

            JsonNode blocking = entry(result, EventTypeName.JAVA_MONITOR_ENTER);
            assertEquals("nanoseconds", blocking.get("weightUnit").asString());
            assertTrue(blocking.get("defaultUseWeight").asBoolean());

            // execution samples are counted, never weighed — the number and its unit are absent together
            JsonNode execution = entry(result, EventTypeName.EXECUTION_SAMPLE);
            assertTrue(execution.get("weight").isNull());
            assertTrue(execution.get("weightUnit").isNull());
            assertFalse(execution.get("defaultUseWeight").asBoolean());
        }

        /** The first recorded type, drawn at the defaults the list itself advertises for it. */
        @Test
        void namesTheExportOfARecordedTypeAtItsDefaults() {
            profileOf(RecordingEventSource.JDK, List.of(
                    summary(EventTypeName.OBJECT_ALLOCATION_SAMPLE, 130, 9_000_000L)));

            JsonNode result = conforming("list", tools().list());

            JsonNode export = result.get("followUp").get("nextTools").get(0);
            assertEquals("flamegraph_export", export.get("tool").asString());
            assertEquals(EventTypeName.OBJECT_ALLOCATION_SAMPLE, export.get("arguments").get("eventType").asString());
            assertTrue(export.get("arguments").get("useWeight").asBoolean());
            assertEquals("profile-1", export.get("arguments").get("profileId").asString());
            assertFalse(result.has("nextSteps"), result.toString());
            assertEquals(1, McpNextToolConformance.assertFollowable(result, reachable()));
        }

        /**
         * Reachable only because the placeholders are filtered out: the grid itself is never empty.
         * Nothing to graph is a status with a reason, not a sentence in place of the record.
         */
        @Test
        void saysSoWhenNothingWasRecorded() {
            profileOf(RecordingEventSource.HEAP_DUMP, List.of());

            JsonNode result = conforming("list", tools().list());

            assertEquals("EMPTY", result.get("status").asString());
            assertTrue(result.get("reason").asString().startsWith("This profile has no flamegraph-capable event types."));
            assertEquals(0, result.get("available").size());
            assertEquals(List.of("profiles_features"), nextTools(result));
            assertEquals(1, McpNextToolConformance.assertFollowable(result, reachable()));
        }
    }

    @Nested
    class StackSampleImport {

        /**
         * pprof and OTLP carry their own sample dimensions rather than JFR event types. Running them
         * through the JFR catalog would report every dimension they do have as missing.
         */
        @Test
        void listsTheDimensionsOfAnImportedProfile() {
            profileOf(RecordingEventSource.PPROF, List.of(
                    summary("cpu", 900, 0L, Map.of(SAMPLE_TYPE_EXTRA, "cpu/nanoseconds")),
                    summary("alloc_space", 40, 2048L, Map.of(SAMPLE_TYPE_EXTRA, "alloc_space/bytes"))));

            JsonNode root = conforming("list", tools().list());

            List<String> available = root.get("available").valueStream()
                    .map(node -> node.get("eventType").asString())
                    .toList();
            assertEquals(List.of("cpu", "alloc_space"), available);
            assertTrue(root.get("notRecorded").isEmpty());

            JsonNode alloc = entry(root, "alloc_space");
            assertEquals("bytes", alloc.get("weightUnit").asString());
            assertEquals(2048L, alloc.get("weight").asLong());
        }
    }

    /**
     * {@code detail} picks how much of the tree a caller is handed: a summary that fits in a few
     * kilobytes, the tree at the configured threshold, or a finer tree. An explicit threshold still
     * wins over the one a detail level implies, since it is the more specific request.
     */
    @Nested
    class ExportDetail {

        @BeforeEach
        void exportableProfile() {
            when(profileManager.info()).thenReturn(info(RecordingEventSource.JDK));
            when(profileManager.flamegraphManager()).thenReturn(flamegraphManager);
            when(flamegraphManager.generateAiExport(any(), any())).thenReturn("tree");
        }

        @Test
        void summaryAsksForTheSummaryView() {
            AiExportConfig config = exportWith(arguments().put("detail", "summary"));

            assertEquals(AiExportView.SUMMARY, config.view());
        }

        @Test
        void omittedDetailKeepsTheConfiguredThreshold() {
            assertNull(exportWith(arguments()));
        }

        @Test
        void standardKeepsTheConfiguredThreshold() {
            assertNull(exportWith(arguments().put("detail", "standard")));
        }

        @Test
        void standardHonoursAnExplicitThreshold() {
            AiExportConfig config = exportWith(arguments().put("detail", "standard").put("thresholdPct", 5.0));

            assertEquals(5.0, config.minFrameThresholdPct());
            assertEquals(AiExportView.TREE, config.view());
        }

        @Test
        void fullIsTheTreeAtHalfAPercent() {
            AiExportConfig config = exportWith(arguments().put("detail", "full"));

            assertEquals(0.5, config.minFrameThresholdPct());
            assertEquals(AiExportView.TREE, config.view());
        }

        @Test
        void fullYieldsToAnExplicitThreshold() {
            AiExportConfig config = exportWith(arguments().put("detail", "full").put("thresholdPct", 3.0));

            assertEquals(3.0, config.minFrameThresholdPct());
        }

        private ObjectNode arguments() {
            return Json.createObject().put("eventType", CPU);
        }

        private AiExportConfig exportWith(ObjectNode arguments) {
            new ReflectiveToolset(tools(), "flamegraph").call("flamegraph_export", arguments);
            ArgumentCaptor<AiExportConfig> config = ArgumentCaptor.forClass(AiExportConfig.class);
            verify(flamegraphManager).generateAiExport(any(), config.capture());
            return config.getValue();
        }
    }

    /**
     * The Markdown stays the text the agent reads; the record beside it carries what the export was
     * built with, the next calls and the page that shows the same graph.
     */
    @Nested
    class ExportAnswer {

        private static final String TREE = "## Call tree\n- root";

        /** Lenient: a refused window never reaches the generator. */
        @BeforeEach
        void exportableProfile() {
            when(profileManager.info()).thenReturn(info(RecordingEventSource.JDK));
            lenient().when(profileManager.flamegraphManager()).thenReturn(flamegraphManager);
            lenient().when(flamegraphManager.generateAiExport(any(), any())).thenReturn(TREE);
        }

        private McpToolResult export(Long startEpochMs, Long endEpochMs, Boolean threadMode, String search) {
            return tools().export(CPU, null, null, startEpochMs, endEpochMs, threadMode, true, search, null, true);
        }

        @Test
        void theTextIsTheMarkdownWithTheLinkAndTheRecordDescribesIt() {
            McpToolResult result = export(null, null, null, null);
            JsonNode export = conforming("export", result);

            assertTrue(result.text().startsWith(TREE), result.text());
            MarkdownFooters.assertRenderedFrom(result.text(), export);
            assertFalse(result.text().contains("Where to go next"), result.text());
            assertEquals("profile-1", export.get("profileId").asString());
            assertEquals(CPU, export.get("eventType").asString());
            assertEquals("STANDARD", export.get("detail").asString());
            assertTrue(export.get("thresholdPct").isNull());
            assertTrue(export.get("window").isNull());
            assertTrue(export.get("useWeight").asBoolean());
            assertTrue(export.get("excludeNonJava").asBoolean());
            assertFalse(export.get("excludeIdle").asBoolean());
            assertEquals(TREE.length(), export.get("markdownChars").asInt());
            assertFalse(export.get("truncated").asBoolean());
            assertFalse(export.has("uiLinkNote"), "the link reproduces every export, so there is nothing to note");
            assertFalse(result.text().contains("Link note:"), result.text());
        }

        /** The same event type and flags, on the view that draws one graph. */
        @Test
        void linksTheFlamegraphViewWithTheSameEventTypeAndFlags() {
            String uiLink = conforming("export", export(null, null, true, null)).get("uiLink").asString();

            assertTrue(uiLink.contains("/profiles/profile-1/flamegraph-view?"), uiLink);
            assertTrue(uiLink.contains("eventType=" + CPU), uiLink);
            assertTrue(uiLink.contains("graphMode=PRIMARY"), uiLink);
            assertTrue(uiLink.contains("useWeight=true"), uiLink);
            assertTrue(uiLink.contains("useThreadMode=true"), uiLink);
            assertTrue(uiLink.contains("excludeNonJavaSamples=true"), uiLink);
            assertFalse(uiLink.contains("excludeIdleSamples"), uiLink);
        }

        @Test
        void aWindowIsGivenInEpochMillisecondsAndGraphedAsItsOffsets() {
            McpToolResult result = export(START_MS + 10_000, START_MS + 20_000, null, null);
            JsonNode export = conforming("export", result);

            ArgumentCaptor<GraphParameters> params = ArgumentCaptor.forClass(GraphParameters.class);
            verify(flamegraphManager).generateAiExport(params.capture(), any());
            assertEquals(Duration.ofSeconds(10), params.getValue().timeRange().start());
            assertEquals(Duration.ofSeconds(20), params.getValue().timeRange().end());
            assertEquals(START_MS + 10_000, export.get("window").get("startEpochMs").asLong());
            assertEquals(START_MS + 20_000, export.get("window").get("endEpochMs").asLong());
        }

        /**
         * The view opens on the window the export graphed: the link names it, so the reader sees the
         * graph the agent read rather than the whole recording.
         */
        @Test
        void aWindowedExportLinksTheSameWindow() {
            McpToolResult result = export(START_MS + 10_000, null, null, null);
            JsonNode export = conforming("export", result);

            String uiLink = export.get("uiLink").asString();
            assertTrue(uiLink.contains("startEpochMs=" + (START_MS + 10_000)), uiLink);
            assertTrue(uiLink.contains("endEpochMs=" + END_MS), uiLink);
            assertFalse(result.text().contains("Link note:"), result.text());
            MarkdownFooters.assertRenderedFrom(result.text(), export);
            assertEquals(END_MS, export.get("window").get("endEpochMs").asLong());
        }

        /**
         * A whole-recording export names the whole recording too: without a window the view opens
         * zoomed to its first hour, which on a longer recording is not the graph the agent read.
         */
        @Test
        void aWholeRecordingExportLinksTheWholeRecording() {
            String uiLink = conforming("export", export(null, null, null, null)).get("uiLink").asString();

            assertTrue(uiLink.contains("startEpochMs=" + START_MS), uiLink);
            assertTrue(uiLink.contains("endEpochMs=" + END_MS), uiLink);
        }

        /** The search travels as the view's search box would take it, encoded. */
        @Test
        void aSearchedExportLinksTheSameSearch() {
            McpToolResult result = export(null, null, null, "OrderService$Batch");
            JsonNode export = conforming("export", result);

            assertEquals("OrderService$Batch", export.get("search").asString());
            assertTrue(export.get("uiLink").asString().contains("search=OrderService%24Batch"),
                    export.get("uiLink").asString());
            assertFalse(result.text().contains("Link note:"), result.text());
        }

        /** Trimmed once, at the boundary: the graph searches, the record reports and the link opens one value. */
        @Test
        void aSearchIsTrimmedOnceForTheGraphTheRecordAndTheLink() {
            JsonNode export = conforming("export", export(null, null, null, "  OrderService  "));

            ArgumentCaptor<GraphParameters> params = ArgumentCaptor.forClass(GraphParameters.class);
            verify(flamegraphManager).generateAiExport(params.capture(), any());
            assertEquals("OrderService", params.getValue().searchPattern());
            assertEquals("OrderService", export.get("search").asString());
            String uiLink = export.get("uiLink").asString();
            assertTrue(uiLink.contains("search=OrderService"), uiLink);
            assertFalse(uiLink.contains("%20"), uiLink);
        }

        /** A blank search is no search, for the graph as much as for the link. */
        @Test
        void aBlankSearchSearchesNothing() {
            export(null, null, null, "   ");

            ArgumentCaptor<GraphParameters> params = ArgumentCaptor.forClass(GraphParameters.class);
            verify(flamegraphManager).generateAiExport(params.capture(), any());
            assertNull(params.getValue().searchPattern());
        }

        @Test
        void anUnsearchedExportLinksNoSearch() {
            String uiLink = conforming("export", export(null, null, null, "  ")).get("uiLink").asString();

            assertFalse(uiLink.contains("search="), uiLink);
        }

        @Test
        void refusesAWindowOutsideTheRecordingAndNamesItsSpan() {
            IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                    () -> export(10_000L, 20_000L, null, null));

            assertTrue(thrown.getMessage().contains(START_MS + ".." + END_MS), thrown.getMessage());
            verify(flamegraphManager, never()).generateAiExport(any(), any());
        }

        @Test
        void anExportPastTheCapSaysItWasTruncated() {
            when(flamegraphManager.generateAiExport(any(), any()))
                    .thenReturn("x".repeat(McpToolOutput.MAX_CHARS + 10));

            JsonNode export = conforming("export", export(null, null, null, null));

            assertTrue(export.get("truncated").asBoolean());
            assertEquals(McpToolOutput.MAX_CHARS + 10, export.get("markdownChars").asInt());
        }

        /**
         * The footer's length is reserved out of the cap, so a Markdown export exactly at the cap is cut,
         * says so, and still ends with its link and next calls inside what the envelope sends.
         */
        @Test
        void anExportAtTheCapIsCutAndKeepsItsFooter() {
            when(flamegraphManager.generateAiExport(any(), any())).thenReturn("x".repeat(McpToolOutput.MAX_CHARS));

            McpToolResult result = export(null, null, null, null);
            JsonNode export = conforming("export", result);

            assertTrue(export.get("truncated").asBoolean());
            assertTrue(result.text().length() <= McpToolOutput.MAX_CHARS, "length " + result.text().length());
            MarkdownFooters.assertRenderedFrom(result.text(), export);
        }

        /**
         * Omitted, the weighting is the event type's own default - allocation is weighed by bytes - and the
         * link and every next call carry that resolved value, so they draw the graph just described.
         */
        @Test
        void anOmittedWeightingIsResolvedIntoTheLinkAndTheNextCalls() {
            when(flamegraphManager.eventSummaries()).thenReturn(List.of(
                    summary(EventTypeName.OBJECT_ALLOCATION_SAMPLE, 130, 9_000_000L)));

            JsonNode export = conforming("export", tools().export(EventTypeName.OBJECT_ALLOCATION_SAMPLE,
                    null, null, null, null, null, null, null, null, null));

            assertTrue(export.get("useWeight").asBoolean());
            assertTrue(export.get("uiLink").asString().contains("useWeight=true"), export.get("uiLink").asString());
            for (JsonNode call : export.get("followUp").get("nextTools")) {
                if (call.get("arguments").has("eventType")) {
                    assertTrue(call.get("arguments").get("useWeight").asBoolean(), call.toString());
                }
            }
            ArgumentCaptor<GraphParameters> params = ArgumentCaptor.forClass(GraphParameters.class);
            verify(flamegraphManager).generateAiExport(params.capture(), any());
            assertEquals(Boolean.TRUE, params.getValue().useWeight());
        }

        @Test
        void anOmittedWeightingOfCpuSamplesIsCounting() {
            JsonNode export = conforming("export", tools().export(CPU, null, null, null, null, null, null, null, null, null));

            assertFalse(export.get("useWeight").asBoolean());
            assertFalse(export.get("uiLink").asString().contains("useWeight"), export.get("uiLink").asString());
            JsonNode timeline = export.get("followUp").get("nextTools").get(2).get("arguments");
            assertFalse(timeline.get("useWeight").asBoolean(), timeline.toString());
        }

        /** The detail an answer reports is a value the input takes back unchanged. */
        @Test
        void theReportedDetailIsAcceptedAsInput() {
            JsonNode full = new ReflectiveToolset(tools(), "flamegraph").callResult("flamegraph_export",
                    Json.createObject().put("eventType", CPU).put("detail", "full")).structuredContent();
            String echoed = full.get("detail").asString();

            JsonNode again = new ReflectiveToolset(tools(), "flamegraph").callResult("flamegraph_export",
                    Json.createObject().put("eventType", CPU).put("detail", echoed)).structuredContent();

            assertEquals("FULL", echoed);
            assertEquals(echoed, again.get("detail").asString());
        }

        /**
         * A whole-recording export routes to the timeline that finds the window to re-export with, and
         * to the per-thread split; each call carries this answer's own event type.
         */
        @Test
        void aWholeRecordingExportNamesTheTimelineAndThePerThreadSplit() {
            JsonNode export = conforming("export", export(null, null, null, null));

            assertEquals(List.of("jvm_threads", "flamegraph_export", "timeline_hotWindows"), nextTools(export));
            JsonNode perThread = export.get("followUp").get("nextTools").get(1).get("arguments");
            assertTrue(perThread.get("threadMode").asBoolean());
            assertEquals(CPU, perThread.get("eventType").asString());
            JsonNode timeline = export.get("followUp").get("nextTools").get(2).get("arguments");
            assertEquals(CPU, timeline.get("eventType").asString());
            assertTrue(timeline.get("useWeight").asBoolean());
            assertEquals(3, McpNextToolConformance.assertFollowable(export, reachable()));
            assertEquals(1, export.get("followUp").get("guidance").size());
        }

        /** A windowed per-thread export has been through the timeline and is already split: only the threads remain. */
        @Test
        void aWindowedPerThreadExportLeavesOnlyTheThreads() {
            JsonNode export = conforming("export", export(START_MS + 10_000, START_MS + 20_000, true, null));

            assertEquals(List.of("jvm_threads"), nextTools(export));
        }

        @Test
        void thePerThreadSplitKeepsTheWindow() {
            JsonNode export = conforming("export", export(START_MS + 10_000, START_MS + 20_000, null, null));

            JsonNode perThread = export.get("followUp").get("nextTools").get(1).get("arguments");
            assertEquals(START_MS + 10_000, perThread.get("startEpochMs").asLong());
            assertEquals(START_MS + 20_000, perThread.get("endEpochMs").asLong());
            assertEquals(2, McpNextToolConformance.assertFollowable(export, reachable()));
        }

        @Test
        void aCallToAWithheldFamilyIsLeftOut() {
            JsonNode export = tools(new AdvertisedFamilies(Set.of(AdvertisedFamilies.FLAMEGRAPH)))
                    .export(CPU, null, null, null, null, null, null, null, null, null)
                    .structuredContent();

            assertEquals(List.of("flamegraph_export"), nextTools(export));
            assertEquals(0, export.get("followUp").get("guidance").size(), export.toString());
        }
    }

    /**
     * A recording whose bounds fall between milliseconds: the link names the whole recording on the
     * epoch-millisecond base, and the graph has to be drawn over exactly those offsets, or the page and
     * the answer describe windows a millisecond apart.
     */
    @Nested
    class WholeRecordingWindow {

        private static final Instant SUB_MS_START = START.plusNanos(900_000);
        private static final Instant SUB_MS_END = START.plusSeconds(60).plusNanos(1_100_000);

        @BeforeEach
        void subMillisecondRecording() {
            when(profileManager.info()).thenReturn(new ProfileInfo(
                    "profile-1", "project-1", "workspace-1", "Profile", RecordingEventSource.JDK,
                    SUB_MS_START, SUB_MS_END, SUB_MS_START, true, false, "recording-1"));
            when(profileManager.flamegraphManager()).thenReturn(flamegraphManager);
            when(flamegraphManager.generateAiExport(any(), any())).thenReturn("## Call tree");
        }

        @Test
        void aWholeRecordingExportGraphsTheOffsetsItsLinkNames() {
            JsonNode export = conforming("export",
                    tools().export(CPU, null, null, null, null, null, null, null, null, null));

            ArgumentCaptor<GraphParameters> params = ArgumentCaptor.forClass(GraphParameters.class);
            verify(flamegraphManager).generateAiExport(params.capture(), any());
            long startMs = SUB_MS_START.toEpochMilli();
            long endMs = SUB_MS_END.toEpochMilli();
            String uiLink = export.get("uiLink").asString();
            assertTrue(uiLink.contains("startEpochMs=" + startMs), uiLink);
            assertTrue(uiLink.contains("endEpochMs=" + endMs), uiLink);
            assertEquals(Duration.ZERO, params.getValue().timeRange().start());
            assertEquals(Duration.ofMillis(endMs - startMs), params.getValue().timeRange().end());
            assertTrue(export.get("window").isNull(), "no window was asked for");
        }
    }

    @Nested
    class ExportSchema {

        @Test
        void theSchemaNamesTheThreeLevels() {
            McpToolSpec spec = new ReflectiveToolset(tools(), "flamegraph").specs().stream()
                    .filter(candidate -> candidate.name().equals("flamegraph_export"))
                    .findFirst()
                    .orElseThrow();

            JsonNode values = spec.inputSchema().path("properties").path("detail").path("enum");
            assertEquals(List.of("SUMMARY", "STANDARD", "FULL"),
                    List.of(values.get(0).asString(), values.get(1).asString(), values.get(2).asString()));
        }

        /** The window inputs are instants on the recording's own clock, never offsets from its start. */
        @Test
        void theWindowIsTakenInEpochMilliseconds() {
            JsonNode properties = new ReflectiveToolset(tools(), "flamegraph").specs().stream()
                    .filter(candidate -> candidate.name().equals("flamegraph_export"))
                    .findFirst()
                    .orElseThrow()
                    .inputSchema().path("properties");

            assertTrue(properties.has("startEpochMs"), properties.toString());
            assertTrue(properties.has("endEpochMs"), properties.toString());
            assertFalse(properties.has("startMs"), properties.toString());
            assertFalse(properties.has("endMs"), properties.toString());
        }

        /** aiExportConfig refuses 0 and 100 themselves, so the schema must not advertise them as allowed. */
        @Test
        void theThresholdRangeIsOpenAtBothEnds() {
            JsonNode threshold = new ReflectiveToolset(tools(), "flamegraph").specs().stream()
                    .filter(candidate -> candidate.name().equals("flamegraph_export"))
                    .findFirst()
                    .orElseThrow()
                    .inputSchema().path("properties").path("thresholdPct");

            assertEquals(0.0, threshold.path("exclusiveMinimum").asDouble(), threshold.toString());
            assertEquals(100.0, threshold.path("exclusiveMaximum").asDouble(), threshold.toString());
            assertFalse(threshold.has("minimum"), threshold.toString());
            assertFalse(threshold.has("maximum"), threshold.toString());
        }
    }
}
