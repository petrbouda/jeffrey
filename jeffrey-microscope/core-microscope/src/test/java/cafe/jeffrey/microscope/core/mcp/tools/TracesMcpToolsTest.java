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

import cafe.jeffrey.jfr.events.notification.Severity;
import cafe.jeffrey.microscope.mcp.protocol.McpToolResult;
import cafe.jeffrey.microscope.mcp.protocol.McpToolSpec;
import cafe.jeffrey.microscope.mcp.protocol.ToolExecutionException;
import cafe.jeffrey.microscope.model.EventSummary;
import cafe.jeffrey.microscope.model.ProfileInfo;
import cafe.jeffrey.microscope.model.RecordingEventSource;
import cafe.jeffrey.microscope.model.SpanInterval;
import cafe.jeffrey.microscope.model.Type;
import cafe.jeffrey.profile.manager.FlamegraphManager;
import cafe.jeffrey.profile.manager.ProfileManager;
import cafe.jeffrey.profile.manager.TraceManager;
import cafe.jeffrey.profile.manager.model.trace.TraceContext;
import cafe.jeffrey.profile.manager.model.trace.TraceDetail;
import cafe.jeffrey.profile.manager.model.trace.TraceExportSource;
import cafe.jeffrey.profile.manager.model.trace.TraceWindow;
import cafe.jeffrey.profile.manager.model.trace.TraceNotificationGroupRow;
import cafe.jeffrey.profile.manager.model.trace.TraceOperationRow;
import cafe.jeffrey.profile.manager.model.trace.TraceOperationSummary;
import cafe.jeffrey.profile.manager.model.trace.TraceOperationThreads;
import cafe.jeffrey.profile.manager.model.trace.TraceOperationsPage;
import cafe.jeffrey.profile.manager.model.trace.TraceOverview;
import cafe.jeffrey.profile.manager.model.trace.TraceRow;
import cafe.jeffrey.profile.manager.model.trace.TraceSpanRow;
import cafe.jeffrey.profile.mcp.ReflectiveToolset;
import cafe.jeffrey.profile.model.EventSummaryResult;
import cafe.jeffrey.provider.profile.api.TraceNotificationListQuery;
import cafe.jeffrey.provider.profile.api.TraceOperationId;
import cafe.jeffrey.provider.profile.api.TraceOperationListQuery;
import cafe.jeffrey.provider.profile.api.TraceOperationSortField;
import cafe.jeffrey.shared.common.Json;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;

import static cafe.jeffrey.microscope.core.mcp.AdvertisedFamiliesFixture.EVERY_FAMILY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TracesMcpToolsTest {

    private static final TraceOverview TRACED = new TraceOverview(12, 340, 3, 5, 7, 2, 0, 0, 0, 0, 0, 8);
    private static final TraceOverview TRACED_CALMLY = new TraceOverview(12, 340, 3, 5, 0, 0, 0, 0, 0, 0, 0, 8);
    private static final TraceOverview UNTRACED = new TraceOverview(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);

    private static final String NAME = "GET /orders";
    private static final String KIND = "SERVER";
    private static final String EVENT_TYPE = "jeffrey.HttpServerExchange";
    private static final String TRACE_ID = "7f3a910000000001";
    private static final String ROOT_SPAN = "00000000000000a1";
    private static final String BUSY_SPAN = "00000000000000a2";
    private static final String CPU = "jdk.ExecutionSample";
    private static final long RECORDING_START_MS = 1_772_366_400_000L;
    private static final long TRACE_START_EPOCH_MS = RECORDING_START_MS + 1_200;
    private static final String MARKDOWN = "# Flamegraph\n\nframes";

    @Mock
    ProfileManager profileManager;

    @Mock
    TraceManager traceManager;

    @Mock
    FlamegraphManager flamegraphManager;

    /**
     * The tools build a UI link off the incoming request, the way ProfileMcpTools#link does.
     */
    @BeforeEach
    void bindRequest() {
        RequestContextHolder.setRequestAttributes(
                new ServletRequestAttributes(new MockHttpServletRequest()));
        when(profileManager.traceManager()).thenReturn(traceManager);
        when(profileManager.flamegraphManager()).thenReturn(flamegraphManager);
        when(flamegraphManager.generateAiExport(any())).thenReturn(MARKDOWN);
        recorded(CPU);
        when(profileManager.info()).thenReturn(new ProfileInfo(
                "p-1", "project-1", "workspace-1", "Profile", RecordingEventSource.JDK,
                Instant.ofEpochMilli(RECORDING_START_MS), Instant.ofEpochMilli(RECORDING_START_MS).plusSeconds(600),
                Instant.EPOCH, true, false, "recording-1"));
    }

    @AfterEach
    void unbindRequest() {
        RequestContextHolder.resetRequestAttributes();
    }

    private TracesMcpTools tools() {
        return new TracesMcpTools(profileManager, EVERY_FAMILY);
    }

    private static JsonNode json(String method, McpToolResult result) {
        return StructuredAnswers.json(TracesMcpTools.class, method, result);
    }

    private static JsonNode markdown(String method, McpToolResult result) {
        return StructuredAnswers.markdown(TracesMcpTools.class, method, result);
    }

    /** The event types this profile recorded samples of, as the cheap event-types read reports them. */
    private void recorded(String... eventTypes) {
        when(flamegraphManager.eventSummaries()).thenReturn(Arrays.stream(eventTypes)
                .map(code -> new EventSummaryResult(new EventSummary(
                        code, code, null, null, 1, 0, true, false, List.of(), null, null)))
                .toList());
    }

    private void traceRecorded(boolean hasPlatformSpan) {
        TraceRow root = new TraceRow(TRACE_ID, NAME, KIND, EVENT_TYPE, 1_200, TRACE_START_EPOCH_MS, 4_000_000, 9, 0,
                hasPlatformSpan);
        TraceDetail detail = new TraceDetail(
                root, new TraceWindow(0, 4_000), List.of(span(ROOT_SPAN, null, 1_000), span(BUSY_SPAN, ROOT_SPAN, 8_000_000)),
                List.of(), 1, List.of(), List.of(), Map.of());
        when(traceManager.export(anyLong())).thenReturn(
                Optional.of(new TraceExportSource(detail, TraceContext.EMPTY, List.of())));
    }

    private static TraceOperationRow operation(String name) {
        return new TraceOperationRow(name, KIND, EVENT_TYPE, 10, 0, 0, 0, 30, 1_000, 100, 200, 300, 400);
    }

    private static TraceRow trace(String traceId) {
        return new TraceRow(traceId, NAME, KIND, EVENT_TYPE, 1_200, TRACE_START_EPOCH_MS, 4_000_000, 9, 0, true);
    }

    private static TraceNotificationGroupRow poolPressure() {
        return new TraceNotificationGroupRow(
                "POOL_PRESSURE", "HIGH", "RESOURCE", "hikari", "Connection pool has no idle connections",
                4, 3, 60_012, 61_200, List.of("7f3a91", "7f3a92"));
    }

    private static TraceSpanRow span(String spanId, String parent, long selfNanos) {
        return new TraceSpanRow(spanId, parent, NAME, KIND, "UNSET", null, 0, RECORDING_START_MS * 1_000,
                9_000_000, selfNanos, 9_000_000, parent == null ? 0 : 1, "1", "worker", false, EVENT_TYPE,
                null, null, false, null);
    }

    private void traceRecorded() {
        traceRecorded(true);
    }

    private void operationRecorded() {
        when(traceManager.operation(any())).thenReturn(Optional.of(operation(NAME)));
        when(traceManager.operationSummary(any(), anyInt()))
                .thenReturn(new TraceOperationSummary(List.of(), new TraceOperationThreads(1, 0, 0, 0)));
        when(traceManager.notifications(any())).thenReturn(List.of());
        when(traceManager.slowestTracesOfOperation(any(), anyInt())).thenReturn(List.of(trace(TRACE_ID)));
    }

    private TraceOperationListQuery lastOperationsQuery() {
        ArgumentCaptor<TraceOperationListQuery> query = ArgumentCaptor.forClass(TraceOperationListQuery.class);
        verify(traceManager, atLeastOnce()).operations(query.capture());
        return query.getValue();
    }

    @Test
    void everyToolDeclaresAnOutputSchema() {
        assertEquals(List.of(), StructuredAnswers.unschematised(TracesMcpTools.class));
    }

    /**
     * The severities are the four of the Severity enum a notification is raised with, so the schema
     * carries them rather than a sentence listing them.
     */
    @Test
    void advertisesTheNotificationSeveritiesAsAnEnum() {
        JsonNode values = new ReflectiveToolset(tools(), "traces").specs().stream()
                .filter(spec -> spec.name().equals("traces_notifications"))
                .findFirst()
                .map(McpToolSpec::inputSchema)
                .orElseThrow()
                .path("properties").path("severity").path("enum");

        List<String> advertised = new ArrayList<>();
        for (JsonNode value : values) {
            advertised.add(value.asString());
        }
        assertEquals(Arrays.stream(Severity.values()).map(Severity::name).toList(), advertised);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "\t\n", " "})
    void refusesABlankRequiredKindInsideTheTool(String kind) {
        ReflectiveToolset toolset = new ReflectiveToolset(tools(), "traces");

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> toolset.call("traces_slowestTraces", Json.createObject()
                        .put("name", NAME)
                        .put("kind", kind)
                        .put("eventType", EVENT_TYPE)));

        assertEquals("kind is required", error.getCause().getMessage());
    }

    @Nested
    class Overview {

        @Test
        void carriesTheNotificationTotals() {
            when(traceManager.overview()).thenReturn(TRACED);

            JsonNode out = json("overview", tools().overview());

            assertEquals("OK", out.get("status").asString());
            assertEquals(7, out.get("overview").get("notificationCount").asLong());
            assertEquals(2, out.get("overview").get("urgentNotificationCount").asLong());
            assertTrue(out.get("uiLink").asString().endsWith("/profiles/p-1/traces/operations"));
        }

        @Test
        void routesToTheNotificationsOnlyWhenAnUrgentOneWasRaised() {
            when(traceManager.overview()).thenReturn(TRACED);
            assertEquals(List.of("traces_notifications", "traces_operations"),
                    StructuredAnswers.nextTools(json("overview", tools().overview())));

            when(traceManager.overview()).thenReturn(TRACED_CALMLY);
            assertEquals(List.of("traces_operations"), StructuredAnswers.nextTools(json("overview", tools().overview())));
        }

        /** A probe answers either way: a profile without traces is a status, not an empty dashboard. */
        @Test
        void anUntracedProfileIsAStatus() {
            when(traceManager.overview()).thenReturn(UNTRACED);

            JsonNode out = json("overview", tools().overview());

            assertEquals("NO_TRACES", out.get("status").asString());
            assertTrue(out.get("reason").asString().contains("contains no traces"), out.toString());
            assertTrue(out.get("overview").isNull());
            assertEquals(List.of(), StructuredAnswers.nextTools(out));
        }
    }

    @Nested
    class Operations {

        @Test
        void pagesWithACursorAndSaysWhetherMoreRemain() {
            when(traceManager.operations(any())).thenReturn(new TraceOperationsPage(
                    List.of(operation("GET /a"), operation("GET /b")), 7));

            JsonNode out = json("operations", tools().operations(null, null, null, 2, null));

            assertEquals(0, lastOperationsQuery().offset());
            assertEquals(2, lastOperationsQuery().limit());
            assertEquals(2, out.get("operations").size());
            assertEquals(7, out.get("totalMatching").asLong());
            assertTrue(out.get("hasMore").asBoolean());
            assertFalse(out.has("paging"), "no prose paging line");

            JsonNode next = StructuredAnswers.call(out, "traces_operations");
            assertEquals(out.get("nextCursor").asString(), next.get("cursor").asString());
            assertEquals(2, next.get("limit").asInt());
        }

        /** A row names an operation the user can open, so it carries that operation's page, not the list's. */
        @Test
        void linksEveryOperationToItsOwnPage() {
            when(traceManager.operations(any())).thenReturn(new TraceOperationsPage(
                    List.of(operation("GET /a"), operation("GET /b")), 2));

            JsonNode out = json("operations", tools().operations(null, null, null, 2, null));

            for (JsonNode row : out.get("operations")) {
                String uiLink = row.get("uiLink").asString();
                assertTrue(uiLink.contains("operation=GET"), uiLink);
                assertTrue(uiLink.contains("kind=" + KIND), uiLink);
                UiLinkRoutes.assertResolves(uiLink);
            }
            assertNotEquals(out.get("operations").get(0).get("uiLink").asString(),
                    out.get("operations").get(1).get("uiLink").asString(), "each row links its own operation");
        }

        @Test
        void continuesFromTheCursorItHandedOut() {
            when(traceManager.operations(any())).thenReturn(new TraceOperationsPage(
                    List.of(operation("GET /a"), operation("GET /b")), 7));
            String cursor = json("operations", tools().operations(null, null, null, 2, null))
                    .get("nextCursor").asString();

            tools().operations(null, null, null, 2, cursor);

            assertEquals(2, lastOperationsQuery().offset());
        }

        @Test
        void refusesACursorHandedOutForOtherFilters() {
            when(traceManager.operations(any())).thenReturn(new TraceOperationsPage(
                    List.of(operation("GET /a"), operation("GET /b")), 7));
            String cursor = json("operations", tools().operations(null, null, null, 2, null))
                    .get("nextCursor").asString();

            assertThrows(IllegalArgumentException.class,
                    () -> tools().operations("orders", null, TraceOperationSortField.P99, 2, cursor));
        }

        @Test
        void routesToTheFirstOperationsExportWithItsWholeIdentity() {
            when(traceManager.operations(any())).thenReturn(new TraceOperationsPage(List.of(operation(NAME)), 1));

            JsonNode out = json("operations", tools().operations(null, null, null, null, null));
            JsonNode call = StructuredAnswers.call(out, "traces_operationExport");

            assertEquals(NAME, call.get("name").asString());
            assertEquals(KIND, call.get("kind").asString());
            assertEquals(EVENT_TYPE, call.get("eventType").asString());
            assertFalse(out.get("hasMore").asBoolean());
            assertTrue(out.get("nextCursor").isNull());
        }

        @Test
        void aTotalThatDriftedBelowTheRowsIsRaisedToThem() {
            when(traceManager.operations(any())).thenReturn(new TraceOperationsPage(
                    List.of(operation("GET /a"), operation("GET /b")), 1));

            JsonNode out = json("operations", tools().operations(null, null, null, null, null));

            assertEquals(2, out.get("totalMatching").asLong());
            assertFalse(out.get("hasMore").asBoolean());
        }

        @Test
        void fitsAPageOfLongNamesAndContinuesAfterTheLastOneShown() {
            String longName = "GET /" + "segment/".repeat(60);
            when(traceManager.operations(any())).thenReturn(new TraceOperationsPage(
                    IntStream.range(0, 1_000).mapToObj(index -> operation(longName + index)).toList(), 5_000));

            JsonNode out = json("operations", tools().operations(null, null, null, 1_000, null));
            int shown = out.get("operations").size();
            tools().operations(null, null, null, 1_000, out.get("nextCursor").asString());

            assertTrue(shown < 1_000, "shown " + shown);
            assertEquals(shown, lastOperationsQuery().offset());
        }

        @Test
        void anUntracedProfileIsAStatusNotAnError() {
            when(traceManager.operations(any())).thenReturn(new TraceOperationsPage(List.of(), 0));

            JsonNode out = json("operations", tools().operations(null, null, null, null, null));

            assertEquals("NO_TRACES", out.get("status").asString());
            assertTrue(out.get("reason").asString().contains("contains no traces"), out.toString());
            assertTrue(out.get("uiLink").asString().endsWith("/profiles/p-1/traces/operations"));
        }

        @Test
        void aFilterThatMatchedNothingRoutesToTheUnfilteredList() {
            when(traceManager.operations(any())).thenReturn(new TraceOperationsPage(List.of(), 0));

            JsonNode out = json("operations", tools().operations("nothing", true, null, null, null));

            assertEquals("NO_MATCH", out.get("status").asString());
            JsonNode call = StructuredAnswers.call(out, "traces_operations");
            assertFalse(call.has("search"));
            assertFalse(call.has("errorsOnly"));
        }
    }

    @Nested
    class Notifications {

        @Test
        void returnsTheGroupsWithTheirInstantsOnTheEpochClock() {
            when(traceManager.notifications(any())).thenReturn(List.of(poolPressure()));

            JsonNode out = json("notifications",
                    tools().notifications(null, null, null, null, null, null, null, null, null));
            JsonNode group = out.get("groups").get(0);

            assertEquals("OK", out.get("status").asString());
            assertEquals("POOL_PRESSURE", group.get("type").asString());
            JsonNode exemplar = group.get("exemplarTraces").get(0);
            assertEquals("7f3a91", exemplar.get("traceId").asString());
            assertTrue(exemplar.get("uiLink").asString().contains("trace=7f3a91"), exemplar.get("uiLink").asString());
            UiLinkRoutes.assertResolves(exemplar.get("uiLink").asString());
            assertEquals(RECORDING_START_MS + 60_012, group.get("firstEpochMs").asLong());
            assertEquals(RECORDING_START_MS + 61_200, group.get("lastEpochMs").asLong());
            assertFalse(out.get("uiLinkNote").isNull(), "no page lists notifications, and the answer says so");
        }

        /** Every column of a notification but its instants is nullable, and the answer says so. */
        @Test
        void aNotificationThatWroteNothingButItsInstantsConformsWithNulls() {
            when(traceManager.notifications(any())).thenReturn(List.of(new TraceNotificationGroupRow(
                    null, null, null, null, null, 1, 1, 10, 10, List.of())));

            JsonNode group = json("notifications",
                    tools().notifications(null, null, null, null, null, null, null, null, null)).get("groups").get(0);

            for (String component : List.of("type", "severity", "category", "source", "message")) {
                assertTrue(group.get(component).isNull(), component + " in " + group);
            }
        }

        @Test
        void routesToTheTraceThatExemplifiesTheMostSevereGroup() {
            when(traceManager.notifications(any())).thenReturn(List.of(poolPressure()));

            JsonNode out = json("notifications",
                    tools().notifications(null, null, null, null, null, null, null, null, null));

            assertEquals("7f3a91", StructuredAnswers.call(out, "traces_traceExport").get("traceId").asString());
        }

        @Test
        void passesEveryFilterThroughAndLinksTheOperation() {
            when(traceManager.notifications(any())).thenReturn(List.of(poolPressure()));

            JsonNode out = json("notifications", tools().notifications("HIGH", "POOL_PRESSURE", "RESOURCE", "hikari",
                    "idle", NAME, KIND, EVENT_TYPE, 5));

            ArgumentCaptor<TraceNotificationListQuery> query = ArgumentCaptor.forClass(TraceNotificationListQuery.class);
            verify(traceManager).notifications(query.capture());
            assertEquals("HIGH", query.getValue().severity());
            assertEquals("POOL_PRESSURE", query.getValue().type());
            assertEquals("RESOURCE", query.getValue().category());
            assertEquals("hikari", query.getValue().source());
            assertEquals("idle", query.getValue().messageContains());
            assertEquals(new TraceOperationId(NAME, KIND, EVENT_TYPE), query.getValue().operation());
            assertEquals(5, query.getValue().limit());
            assertTrue(out.get("uiLink").asString().contains("operation=GET"), out.get("uiLink").asString());
        }

        @Test
        void leavesTheOperationOutWhenNoneWasGiven() {
            when(traceManager.notifications(any())).thenReturn(List.of(poolPressure()));

            tools().notifications(null, null, null, null, null, null, null, null, null);

            ArgumentCaptor<TraceNotificationListQuery> query = ArgumentCaptor.forClass(TraceNotificationListQuery.class);
            verify(traceManager).notifications(query.capture());
            assertNull(query.getValue().operation());
            assertEquals(50, query.getValue().limit(), "the default limit applies");
        }

        @Test
        void refusesHalfAnOperation() {
            IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                    () -> tools().notifications(null, null, null, null, null, NAME, null, null, null));

            assertTrue(e.getMessage().contains("all three"), e.getMessage());
        }

        @Test
        void tellsAnUntracedProfileFromOneWithoutNotifications() {
            when(traceManager.notifications(any())).thenReturn(List.of());

            when(traceManager.overview()).thenReturn(UNTRACED);
            JsonNode untraced = json("notifications",
                    tools().notifications(null, null, null, null, null, null, null, null, null));
            assertEquals("NO_TRACES", untraced.get("status").asString());
            assertTrue(untraced.get("reason").asString().contains("contains no traces"));

            when(traceManager.overview()).thenReturn(TRACED);
            JsonNode quiet = json("notifications",
                    tools().notifications(null, null, null, null, null, null, null, null, null));
            assertEquals("NO_NOTIFICATIONS", quiet.get("status").asString());
            assertTrue(quiet.get("reason").asString().contains("raised no notifications"));
        }

        @Test
        void saysWhenAFilterMatchedNothing() {
            when(traceManager.notifications(any())).thenReturn(List.of());

            JsonNode out = json("notifications",
                    tools().notifications("CRITICAL", null, null, null, null, null, null, null, null));

            assertEquals("NO_MATCH", out.get("status").asString());
            assertTrue(out.get("reason").asString().contains("No notification matches"), out.toString());
            assertEquals(List.of("traces_notifications"), StructuredAnswers.nextTools(out));
        }
    }

    @Nested
    class SlowestTraces {

        @Test
        void listsTheTracesWithOneInstantOnTheEpochClock() {
            when(traceManager.slowestTracesOfOperation(any(), anyInt())).thenReturn(List.of(trace(TRACE_ID)));

            JsonNode out = json("slowestTraces", tools().slowestTraces(NAME, KIND, EVENT_TYPE, null));
            JsonNode trace = out.get("traces").get(0);

            assertEquals(TRACE_ID, trace.get("traceId").asString());
            assertEquals(TRACE_START_EPOCH_MS, trace.get("startEpochMs").asLong());
            assertEquals(4_000_000, trace.get("durationNanos").asLong());
            assertFalse(trace.has("startMillisFromBeginning"));
            assertFalse(trace.has("startEpochMillis"));
            assertTrue(out.get("uiLink").asString().contains("tab=slowest"), out.get("uiLink").asString());
        }

        /**
         * The chronological page is the recording's first traces; ranking it named a 17 ms trace the
         * slowest of an operation whose worst took 425 ms, so the tool must ask for the ranking.
         */
        @Test
        void asksForTheRankingAndNeverForTheChronologicalPage() {
            when(traceManager.slowestTracesOfOperation(any(), anyInt())).thenReturn(List.of(trace(TRACE_ID)));

            tools().slowestTraces(NAME, KIND, EVENT_TYPE, 7);

            verify(traceManager).slowestTracesOfOperation(any(), eq(7));
            verify(traceManager, never()).tracesOfOperation(any(), anyInt());
        }

        /** The root's name, kind and event type are NOT NULL columns, so the schema promises them. */
        @Test
        void aTracesRootIsNeverNull() {
            for (String component : List.of("rootName", "rootKind", "rootEventType")) {
                assertEquals("string", StructuredAnswers.schemaTypeOf(
                        TracesMcpTools.class, "slowestTraces", "traces", component).asString(), component);
            }
        }

        /**
         * The agent names a trace from this list to the user; without a link of its own that trace could
         * only be opened after an expensive export.
         */
        @Test
        void linksEveryTraceToItsWaterfall() {
            when(traceManager.slowestTracesOfOperation(any(), anyInt())).thenReturn(List.of(trace("a1"), trace("b2")));

            JsonNode out = json("slowestTraces", tools().slowestTraces(NAME, KIND, EVENT_TYPE, null));

            for (JsonNode trace : out.get("traces")) {
                String uiLink = trace.get("uiLink").asString();
                assertTrue(uiLink.contains("trace=" + trace.get("traceId").asString()), uiLink);
                UiLinkRoutes.assertResolves(uiLink);
            }
        }

        @Test
        void routesToTheSlowestTraceAndToTheWholeOperation() {
            when(traceManager.slowestTracesOfOperation(any(), anyInt())).thenReturn(List.of(trace(TRACE_ID)));

            JsonNode out = json("slowestTraces", tools().slowestTraces(NAME, KIND, EVENT_TYPE, null));

            assertEquals(List.of("traces_traceExport", "traces_operationExport"), StructuredAnswers.nextTools(out));
            assertEquals(TRACE_ID, StructuredAnswers.call(out, "traces_traceExport").get("traceId").asString());
        }

        @Test
        void aListThatReachedItsLimitCannotSayWhatItLeftOut() {
            when(traceManager.slowestTracesOfOperation(any(), anyInt())).thenReturn(List.of(trace("a"), trace("b")));

            JsonNode out = json("slowestTraces", tools().slowestTraces(NAME, KIND, EVENT_TYPE, 2));

            assertTrue(out.get("omittedTraces").isNull(), out.get("omittedTraces").toString());
        }

        @Test
        void aListShorterThanItsLimitLeftNothingOut() {
            when(traceManager.slowestTracesOfOperation(any(), anyInt())).thenReturn(List.of(trace("a")));

            JsonNode out = json("slowestTraces", tools().slowestTraces(NAME, KIND, EVENT_TYPE, 5));

            assertEquals(0, out.get("omittedTraces").asInt());
        }

        /** An operation that has no traces is not an operation of this profile: the caller named it wrongly. */
        @Test
        void refusesAnOperationThisProfileDoesNotHold() {
            when(traceManager.slowestTracesOfOperation(any(), anyInt())).thenReturn(List.of());

            ToolExecutionException error = assertThrows(ToolExecutionException.class,
                    () -> tools().slowestTraces("GET /nope", KIND, EVENT_TYPE, null));

            assertTrue(error.getMessage().contains("GET /nope"), error.getMessage());
            assertTrue(error.getMessage().contains("traces_operations"), error.getMessage());
        }
    }

    @Nested
    class Exports {

        @Test
        void aTraceExportKeepsItsMarkdownAndEndsWithTheFooter() {
            traceRecorded();

            McpToolResult result = tools().traceExport(TRACE_ID);
            JsonNode out = markdown("traceExport", result);

            assertTrue(result.text().contains(NAME), result.text());
            assertEquals(TRACE_ID, out.get("traceId").asString());
            assertTrue(out.get("uiLink").asString().contains("trace=" + TRACE_ID), out.get("uiLink").asString());
            assertFalse(out.get("truncated").asBoolean());
        }

        @Test
        void aTraceExportRoutesToTheFramesOfItsBusiestSpanAndToItsOperation() {
            traceRecorded();

            JsonNode out = markdown("traceExport", tools().traceExport(TRACE_ID));
            JsonNode frames = StructuredAnswers.call(out, "traces_spanFlamegraphExport");

            assertEquals(TRACE_ID, frames.get("traceId").asString());
            assertEquals(BUSY_SPAN, frames.get("spanId").asString());
            assertEquals(CPU, frames.get("eventType").asString());
            assertEquals(NAME, StructuredAnswers.call(out, "traces_operationExport").get("name").asString());
        }

        @Test
        void aTraceExportGraphsTheOnCpuTypeThisProfileRecorded() {
            recorded(Type.CPU_TIME_SAMPLE.code());
            traceRecorded();

            JsonNode out = markdown("traceExport", tools().traceExport(TRACE_ID));

            assertEquals(Type.CPU_TIME_SAMPLE.code(),
                    StructuredAnswers.call(out, "traces_spanFlamegraphExport").get("eventType").asString());
        }

        @Test
        void aTraceExportWithoutAnOnCpuTypeOffersNoGraphAndSaysWhereTheTypesAre() {
            recorded("jdk.ObjectAllocationSample");
            traceRecorded();

            JsonNode out = markdown("traceExport", tools().traceExport(TRACE_ID));

            assertFalse(StructuredAnswers.nextTools(out).contains("traces_spanFlamegraphExport"));
            assertTrue(StructuredAnswers.guidance(out).contains("flamegraph_list"), StructuredAnswers.guidance(out));
        }

        /** A trace that never left its virtual threads has no sample attributed to it, as the UI says. */
        @Test
        void aTraceWithNoPlatformSpanOffersNoSpanGraph() {
            traceRecorded(false);

            JsonNode out = markdown("traceExport", tools().traceExport(TRACE_ID));

            assertFalse(StructuredAnswers.nextTools(out).contains("traces_spanFlamegraphExport"));
        }

        @Test
        void anOperationExportGraphsTheOnCpuTypeThisProfileRecorded() {
            recorded(Type.CPU_TIME_SAMPLE.code());
            operationRecorded();

            JsonNode out = markdown("operationExport", tools().operationExport(NAME, KIND, EVENT_TYPE));

            assertEquals(Type.CPU_TIME_SAMPLE.code(), StructuredAnswers.call(out, "traces_operationFlamegraphExport")
                    .get("graphEventType").asString());
        }

        @Test
        void anOperationExportWithoutAnOnCpuTypeOffersNoGraphAndSaysWhereTheTypesAre() {
            recorded();
            operationRecorded();

            JsonNode out = markdown("operationExport", tools().operationExport(NAME, KIND, EVENT_TYPE));

            assertFalse(StructuredAnswers.nextTools(out).contains("traces_operationFlamegraphExport"));
            assertTrue(StructuredAnswers.guidance(out).contains("flamegraph_list"), StructuredAnswers.guidance(out));
        }

        @Test
        void refusesATraceThisProfileDoesNotHold() {
            when(traceManager.export(anyLong())).thenReturn(Optional.empty());

            ToolExecutionException error = assertThrows(ToolExecutionException.class,
                    () -> tools().traceExport(TRACE_ID));

            assertTrue(error.getMessage().contains(TRACE_ID), error.getMessage());
        }

        @Test
        void anOperationExportRoutesToItsSlowestTraceAndItsFlamegraph() {
            operationRecorded();

            McpToolResult result = tools().operationExport(NAME, KIND, EVENT_TYPE);
            JsonNode out = markdown("operationExport", result);

            assertEquals(NAME, out.get("name").asString());
            assertEquals(TRACE_ID, StructuredAnswers.call(out, "traces_traceExport").get("traceId").asString());
            assertEquals(CPU, StructuredAnswers.call(out, "traces_operationFlamegraphExport")
                    .get("graphEventType").asString());
            assertTrue(out.get("uiLink").asString().contains("operation=GET"), out.get("uiLink").asString());
        }

        @Test
        void refusesAnOperationThisProfileDoesNotHold() {
            when(traceManager.operation(any())).thenReturn(Optional.empty());

            assertThrows(ToolExecutionException.class, () -> tools().operationExport(NAME, KIND, EVENT_TYPE));
            assertThrows(ToolExecutionException.class,
                    () -> tools().operationFlamegraphExport(NAME, KIND, EVENT_TYPE, CPU, null, null));
        }

        @Test
        void aSpanFlamegraphLinksTheTraceAndSaysWhatTheLinkCannotChoose() {
            when(traceManager.spanIntervals(anyLong(), anyLong(), anyBoolean()))
                    .thenReturn(List.of(new SpanInterval(1L, 0L, 60_000L)));

            McpToolResult result = tools().spanFlamegraphExport(TRACE_ID, BUSY_SPAN, CPU, null, null, null);
            JsonNode out = markdown("spanFlamegraphExport", result);

            assertEquals("OK", out.get("status").asString());
            assertTrue(result.text().startsWith(MARKDOWN), "the Markdown stays the text");
            assertTrue(out.get("uiLink").asString().contains("trace=" + TRACE_ID), out.get("uiLink").asString());
            assertFalse(out.get("uiLinkNote").isNull());
            assertEquals(Boolean.TRUE, StructuredAnswers.call(out, "traces_spanFlamegraphExport")
                    .get("selfOnly").asBoolean());
        }

        /** A span whose children covered all of its time has no self time: a valid question with no data. */
        @Test
        void aSpanWithNoSelfTimeIsAStatusThatOffersTheWholeSpan() {
            when(traceManager.spanIntervals(anyLong(), anyLong(), eq(true))).thenReturn(List.of());
            when(traceManager.spanIntervals(anyLong(), anyLong(), eq(false)))
                    .thenReturn(List.of(new SpanInterval(1L, 0L, 60_000L)));

            McpToolResult result = tools().spanFlamegraphExport(TRACE_ID, BUSY_SPAN, CPU, true, null, null);
            JsonNode out = markdown("spanFlamegraphExport", result);

            assertEquals("NO_SELF_TIME", out.get("status").asString());
            assertTrue(out.get("reason").asString().contains(BUSY_SPAN), out.toString());
            assertTrue(result.text().startsWith("status: NO_SELF_TIME"), result.text());
            assertEquals(List.of("traces_spanFlamegraphExport", "traces_traceExport"), StructuredAnswers.nextTools(out));
            assertFalse(StructuredAnswers.call(out, "traces_spanFlamegraphExport").has("selfOnly"));
            assertEquals(0, out.get("markdownChars").asInt());
        }

        /** Ids that name no span of this profile are the caller's to correct, not an empty answer. */
        @Test
        void refusesASpanThisProfileDoesNotHold() {
            when(traceManager.spanIntervals(anyLong(), anyLong(), anyBoolean())).thenReturn(List.of());

            ToolExecutionException error = assertThrows(ToolExecutionException.class,
                    () -> tools().spanFlamegraphExport(TRACE_ID, BUSY_SPAN, CPU, true, null, null));

            assertTrue(error.getMessage().contains(BUSY_SPAN), error.getMessage());
            assertTrue(error.getMessage().contains("traces_traceExport"), error.getMessage());
        }

        @Test
        void anOperationFlamegraphLinksTheFlamesTabAndSaysWhatTheLinkCannotChoose() {
            operationRecorded();

            JsonNode out = markdown("operationFlamegraphExport",
                    tools().operationFlamegraphExport(NAME, KIND, EVENT_TYPE, CPU, true, null));

            assertTrue(out.get("uiLink").asString().contains("tab=flames"), out.get("uiLink").asString());
            assertFalse(out.get("uiLinkNote").isNull());
            assertEquals(CPU, out.get("graphEventType").asString());
            assertTrue(out.get("threadMode").asBoolean());
        }
    }
}
