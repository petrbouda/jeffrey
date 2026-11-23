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

import cafe.jeffrey.microscope.core.mcp.tools.heap.HeapReport;
import cafe.jeffrey.microscope.mcp.protocol.McpOutputSchema;
import cafe.jeffrey.microscope.mcp.protocol.McpSchemaGenerator;
import cafe.jeffrey.microscope.mcp.protocol.testing.McpSchemaConformance;
import cafe.jeffrey.microscope.core.profile.manager.ProfileManager;
import cafe.jeffrey.microscope.mcp.server.McpNextToolConformance;
import cafe.jeffrey.microscope.mcp.server.ReflectiveToolset;
import tools.jackson.databind.JsonNode;
import cafe.jeffrey.microscope.mcp.protocol.McpToolOutcome;
import cafe.jeffrey.microscope.mcp.protocol.McpToolResult;
import cafe.jeffrey.microscope.core.profile.model.ProfileInfo;
import cafe.jeffrey.microscope.common.model.RecordingEventSource;
import cafe.jeffrey.microscope.core.profile.manager.heapdump.HeapDumpInitService;
import cafe.jeffrey.microscope.core.profile.manager.heapdump.HeapDumpManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZoneId;
import java.util.concurrent.CountDownLatch;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import cafe.jeffrey.microscope.core.utils.Json;

import static cafe.jeffrey.microscope.core.mcp.AdvertisedFamiliesFixture.EVERY_FAMILY;
import static cafe.jeffrey.microscope.core.mcp.tools.McpCallContexts.complete;
import static cafe.jeffrey.microscope.mcp.protocol.McpCallContext.RESOURCE_READ;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeout;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.ArgumentMatchers.eq;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class HeapComputeMcpToolsTest {

    private static final String PROFILE_ID = "profile-1";
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-03-01T12:00:00Z"), ZoneOffset.UTC);

    @Mock
    ProfileManager profileManager;

    @Mock
    HeapDumpManager heapDumpManager;

    private HeapDumpInitService initService;

    @BeforeEach
    void setUp() {
        initService = new HeapDumpInitService(CLOCK);
        when(profileManager.info()).thenReturn(profileInfo());
        when(profileManager.heapDumpManager()).thenReturn(heapDumpManager);
        // UiLinks builds from the current servlet request, so the tools need one bound.
        RequestContextHolder.setRequestAttributes(
                new ServletRequestAttributes(new MockHttpServletRequest()));
    }

    private HeapComputeMcpTools tools() {
        return new HeapComputeMcpTools(profileManager, initService, EVERY_FAMILY);
    }

    /** A second preparation without retry reuses the completed work rather than rebuilding it. */
    @Test
    void omittingRetryDoesNotRestartACompletedPreparation() {
        when(heapDumpManager.heapDumpExists()).thenReturn(true);
        HeapComputeMcpTools tools = tools();
        complete(tools.prepare(HeapReport.LEAKS, false, RESOURCE_READ));

        var inspected = Json.mapper().readTree(complete(tools.prepare(HeapReport.LEAKS, null, RESOURCE_READ)));

        assertFalse(inspected.path("started").asBoolean(), inspected.toString());
        assertEquals("heap_getLeakSuspects",
                inspected.path("followUp").path("nextTools").get(0).path("tool").asString(), inspected.toString());
        assertNoRebuildOffered(inspected);
        verify(heapDumpManager, times(1)).initialize(eq(null), any());

        complete(tools.prepare(HeapReport.LEAKS, true, RESOURCE_READ));
        verify(heapDumpManager, times(2)).initialize(eq(null), any());
    }

    /**
     * The registry is keyed by profile, not by report, so a report the last run never computed starts
     * its own preparation rather than reusing another report's finished run.
     */
    @Test
    void aReportTheLastRunNeverComputedStartsItsOwnPreparation() {
        when(heapDumpManager.heapDumpExists()).thenReturn(true);
        HeapComputeMcpTools tools = tools();
        complete(tools.prepare(HeapReport.LEAKS, false, RESOURCE_READ));

        var other = Json.mapper().readTree(complete(tools.prepare(HeapReport.STRINGS, null, RESOURCE_READ)));

        assertTrue(other.path("started").asBoolean(), other.toString());
        assertEquals(List.of("STRINGS"), Json.mapper().convertValue(other.path("computing"), List.class),
                other.toString());
    }

    /** A success is never offered as a rebuild: a retry call belongs to a failure alone. */
    private static void assertNoRebuildOffered(JsonNode answer) {
        for (JsonNode call : answer.path("followUp").path("nextTools")) {
            assertFalse(call.path("arguments").path("retry").asBoolean(), answer.toString());
        }
    }

    @Nested
    class Prepare {

        /**
         * A JFR recording asked to prepare a heap dump must be told it asked the wrong family, rather
         * than starting a run that fails on its first stage and reads like a broken dump.
         */
        @Test
        void refusesAJfrProfilePointingAtItsBundlesHeapDump() {
            when(profileManager.info()).thenReturn(new ProfileInfo(
                    PROFILE_ID, "Session", RecordingEventSource.JFR,
                    Instant.EPOCH, Instant.EPOCH.plusSeconds(60), Instant.EPOCH, true, false, "recording-1"));

            IllegalArgumentException e =
                    assertThrows(IllegalArgumentException.class,
                            () -> complete(tools().prepare(null, true, RESOURCE_READ)));

            assertTrue(e.getMessage().contains("is not a heap dump"), e.getMessage());
            assertTrue(e.getMessage().contains("connector_download"), e.getMessage());
        }

        /** A heap dump profile whose copy of the dump is gone is rebuilt by analysing its recording again. */
        @Test
        void refusesAHeapDumpProfileThatLostItsDump() {
            when(heapDumpManager.heapDumpExists()).thenReturn(false);

            IllegalArgumentException e =
                    assertThrows(IllegalArgumentException.class,
                            () -> complete(tools().prepare(null, true, RESOURCE_READ)));

            assertTrue(e.getMessage().contains("lost its copy"), e.getMessage());
            assertTrue(e.getMessage().contains("recordings_analyzeRecording"), e.getMessage());
        }

        @Test
        void refusesAnUnknownReportNamingTheOnesItHas() {
            when(heapDumpManager.heapDumpExists()).thenReturn(true);

            RuntimeException e = assertThrows(RuntimeException.class, () -> new ReflectiveToolset(tools(), "heap")
                    .call("heap_prepare", Json.createObject().put("report", "nonsense")));

            assertTrue(e.getMessage().contains("LEAKS"), e.getMessage());
        }

        /** The report an answer lists is a value the input takes back, in any case. */
        @Test
        void theReportedReportIsAcceptedAsInput() {
            when(heapDumpManager.heapDumpExists()).thenReturn(true);
            ReflectiveToolset toolset = new ReflectiveToolset(tools(), "heap");

            JsonNode first = toolset.callResult("heap_prepare", Json.createObject().put("report", "leaks"))
                    .structuredContent();
            String report = first.path("computing").get(0).asString();
            JsonNode again = toolset.callResult("heap_prepare", Json.createObject().put("report", report))
                    .structuredContent();

            assertEquals("LEAKS", report);
            assertEquals("LEAKS", again.path("computing").get(0).asString());
        }

        /** The answer lists the stages; the heavy work runs on the call. */
        @Test
        void returnsTheStageList() {
            when(heapDumpManager.heapDumpExists()).thenReturn(true);

            String result = complete(tools().prepare(null, true, RESOURCE_READ));

            assertTrue(result.contains("\"stages\""));
            assertTrue(result.contains("dominator"));
        }
    }

    @Nested
    class Status {

        @Test
        void reportsAnIdleProfileRatherThanFailing() {
            String result = tools().status().text();

            assertTrue(result.contains("\"running\":false"));
            assertTrue(result.contains("\"stages\""));
        }

        @Test
        void anIdleProfileIsSentToPrepareItsDump() {
            JsonNode out = conforming("status", tools().status());

            assertEquals("IDLE", out.path("state").asString());
            assertEquals(PROFILE_ID, call(out, "heap_prepare").path("profileId").asString());
        }

        @Test
        void aCompletedPreparationRoutesToTheReportsItBuilt() {
            HeapComputeMcpTools tools = tools();
            when(heapDumpManager.heapDumpExists()).thenReturn(true);
            complete(tools.prepare(null, false, RESOURCE_READ));

            JsonNode out = conforming("status", tools.status());

            assertEquals("COMPLETED", out.path("state").asString());
            assertEquals(Set.of("heap_getHeapSummary", "heap_getLeakSuspects", "heap_getDominatorTreeRoots"),
                    nextTools(out));
        }
    }

    /** Every answer is the tool's record: checked against its schema, its link and its next calls. */
    @Nested
    class StructuredAnswers {

        @Test
        void aPreparationListsItsStagesAndRoutesToItsReport() {
            when(heapDumpManager.heapDumpExists()).thenReturn(true);

            JsonNode out = conforming("prepare", tools().prepare(HeapReport.LEAKS, false, RESOURCE_READ));

            assertTrue(out.path("started").asBoolean(), out.toString());
            assertEquals("LEAKS", out.path("computing").get(0).asString());
            assertEquals("heap_getLeakSuspects",
                    out.path("followUp").path("nextTools").get(0).path("tool").asString(), out.toString());
            assertTrue(out.path("uiLink").asString().endsWith("/heap-dump/overview"), out.toString());
            assertEquals("LEAKS", stage(out, "leaks").path("report").asString(), out.toString());
            assertTrue(stage(out, "load").path("report").isNull(), out.toString());
        }

        @Test
        void aFinishedRunRoutesToTheReportItComputed() {
            HeapComputeMcpTools tools = tools();
            when(heapDumpManager.heapDumpExists()).thenReturn(true);
            complete(tools.prepare(HeapReport.BIGGEST, false, RESOURCE_READ));

            JsonNode out = conforming("prepare", tools.prepare(HeapReport.BIGGEST, null, RESOURCE_READ));

            assertFalse(out.path("started").asBoolean());
            assertEquals("heap_getBiggestObjects", out.path("followUp").path("nextTools").get(0).path("tool").asString());
        }
    }

    /** The answer, checked against the schema the tool advertises, its link and its next calls. */
    private static JsonNode conforming(String method, McpToolOutcome outcome) {
        McpToolResult result = assertInstanceOf(McpToolResult.class, outcome);
        JsonNode structured = result.structuredContent();
        Method tool = Arrays.stream(HeapComputeMcpTools.class.getMethods())
                .filter(candidate -> candidate.getName().equals(method))
                .findFirst()
                .orElseThrow();
        McpSchemaConformance.assertConforms(structured,
                McpSchemaGenerator.schemaOf(tool.getAnnotation(McpOutputSchema.class).value()));
        UiLinkRoutes.assertResolves(structured.get("uiLink").asString());
        assertEquals(Json.toString(structured), result.text(), "the text is the record's own JSON");
        McpNextToolConformance.assertFollowable(structured, HeapDumpMcpToolsTest.reachable());
        return structured;
    }

    private static JsonNode stage(JsonNode structured, String id) {
        for (JsonNode stage : structured.get("stages")) {
            if (stage.get("id").asString().equals(id)) {
                return stage;
            }
        }
        throw new AssertionError("no stage " + id + " in " + structured);
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

    private static ProfileInfo profileInfo() {
        return new ProfileInfo(
                PROFILE_ID, "Heap dump", RecordingEventSource.HEAP_DUMP,
                Instant.EPOCH, Instant.EPOCH.plusSeconds(60), Instant.EPOCH, true, false, "recording-1");
    }
}
