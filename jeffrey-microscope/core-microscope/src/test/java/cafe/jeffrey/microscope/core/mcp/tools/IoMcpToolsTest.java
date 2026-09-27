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

import cafe.jeffrey.microscope.mcp.protocol.McpToolResult;
import cafe.jeffrey.microscope.mcp.protocol.ToolDispatchException;
import cafe.jeffrey.microscope.model.ProfileInfo;
import cafe.jeffrey.microscope.model.RecordingEventSource;
import cafe.jeffrey.profile.manager.IoManager;
import cafe.jeffrey.profile.manager.ProfileManager;
import cafe.jeffrey.profile.manager.model.io.IoEndpoint;
import cafe.jeffrey.profile.manager.model.io.IoKind;
import cafe.jeffrey.profile.manager.model.io.IoOperation;
import cafe.jeffrey.profile.manager.model.io.IoOverview;
import cafe.jeffrey.profile.mcp.ReflectiveToolset;
import cafe.jeffrey.shared.common.Json;
import org.junit.jupiter.api.AfterEach;
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
import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static cafe.jeffrey.microscope.core.mcp.AdvertisedFamiliesFixture.EVERY_FAMILY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class IoMcpToolsTest {

    private static final String PROFILE_ID = "p-1";
    private static final String SOCKET_VIEW_LINK = "/profiles/p-1/socket-io";
    private static final String FILE_VIEW_LINK = "/profiles/p-1/file-io";
    private static final String SOCKET_PEER = "orders-db:5432";
    private static final String FILE_PATH = "/var/lib/app/journal.log";
    private static final String IO_PREFIX = "io";
    private static final String OVERVIEW_TOOL = "io_overview";
    private static final String KIND_ARGUMENT = "kind";

    /** Above the tool's own endpoint cap, so the head of a long list can be told from the whole. */
    private static final int MORE_ENDPOINTS_THAN_THE_CAP = 45;
    private static final int ENDPOINT_CAP = 40;

    @Mock
    ProfileManager profileManager;

    @Mock
    IoManager ioManager;

    /**
     * Every answer carries a link into the UI, and {@code UiLinks} reads the request bound to the
     * current thread to build it.
     */
    @BeforeEach
    void bindRequest() {
        RequestContextHolder.setRequestAttributes(
                new ServletRequestAttributes(new MockHttpServletRequest()));

        when(profileManager.info()).thenReturn(new ProfileInfo(
                PROFILE_ID, "project-1", "workspace-1", "Profile", RecordingEventSource.JDK,
                Instant.EPOCH, Instant.EPOCH.plusSeconds(60), Instant.EPOCH, true, false, "recording-1"));
        when(profileManager.ioManager()).thenReturn(ioManager);
    }

    @AfterEach
    void unbindRequest() {
        RequestContextHolder.resetRequestAttributes();
    }

    private IoMcpTools tools() {
        return new IoMcpTools(profileManager, EVERY_FAMILY);
    }

    private static JsonNode answer(String method, McpToolResult result) {
        return StructuredAnswers.json(IoMcpTools.class, method, result);
    }

    private static IoOverview overview(String slowestTarget) {
        return new IoOverview(4_096_000L, 512_000L, 1_820L, 96_000_000L, slowestTarget, true);
    }

    private static IoOverview nothingRecorded() {
        return new IoOverview(0, 0, 0, 0, null, false);
    }

    private static IoEndpoint endpoint(String target) {
        return new IoEndpoint(target, 620, 3_100_000L, 45_000_000L, 9_000_000L);
    }

    private static List<IoEndpoint> manyEndpoints() {
        List<IoEndpoint> endpoints = new ArrayList<>();
        for (int i = 0; i < MORE_ENDPOINTS_THAN_THE_CAP; i++) {
            endpoints.add(endpoint("peer-" + i));
        }
        return endpoints;
    }

    @Test
    void everyToolDeclaresAnOutputSchema() {
        assertEquals(List.of(), StructuredAnswers.unschematised(IoMcpTools.class));
    }

    @Nested
    class Overview {

        @Test
        void carriesTheThroughputAndTheSlowestTargetForSockets() {
            when(ioManager.overview(IoKind.SOCKET)).thenReturn(overview(SOCKET_PEER));

            JsonNode out = answer("overview", tools().overview(IoKind.SOCKET));

            assertEquals("OK", out.get("status").asString());
            assertEquals("SOCKET", out.get("kind").asString());
            assertEquals(4_096_000L, out.get("overview").get("bytesRead").asLong());
            assertEquals(SOCKET_PEER, out.get("overview").get("slowestTarget").asString());
            assertTrue(out.get("uiLink").asString().endsWith(SOCKET_VIEW_LINK), out.get("uiLink").asString());
        }

        /**
         * The two kinds are separate pages, so an answer about files must not link the reader at the
         * socket dashboard - the figures would not be the ones they just read.
         */
        @Test
        void linksTheFileDashboardWhenTheQuestionWasAboutFiles() {
            when(ioManager.overview(IoKind.FILE)).thenReturn(overview(FILE_PATH));

            JsonNode out = answer("overview", tools().overview(IoKind.FILE));

            assertEquals("FILE", out.get("kind").asString());
            assertTrue(out.get("uiLink").asString().endsWith(FILE_VIEW_LINK), out.get("uiLink").asString());
        }

        @Test
        void routesToTheTargetsAndTheSlowestOperationsOfTheSameKind() {
            when(ioManager.overview(IoKind.FILE)).thenReturn(overview(FILE_PATH));

            JsonNode out = answer("overview", tools().overview(IoKind.FILE));

            assertEquals("FILE", StructuredAnswers.call(out, "io_endpoints").get("kind").asString());
            assertEquals("FILE", StructuredAnswers.call(out, "io_slowest").get("kind").asString());
            assertTrue(StructuredAnswers.guidance(out).contains("off-CPU"), StructuredAnswers.guidance(out));
        }

        @Test
        void saysThereIsNoSocketDataAsAStatusRatherThanRenderingAZeroDashboard() {
            when(ioManager.overview(IoKind.SOCKET)).thenReturn(nothingRecorded());

            JsonNode out = answer("overview", tools().overview(IoKind.SOCKET));

            assertEquals("NOT_RECORDED", out.get("status").asString());
            assertTrue(out.get("reason").asString().contains("recorded no socket I/O events"), out.toString());
            assertTrue(out.get("overview").isNull());
            assertTrue(out.get("uiLink").asString().endsWith(SOCKET_VIEW_LINK));
            assertEquals("FILE", StructuredAnswers.call(out, "io_overview").get("kind").asString());
        }

        /**
         * There is no sensible default between sockets and files: either choice would answer half the
         * question asked and nothing in the answer would say which half.
         */
        @Test
        void refusesAMissingKindRatherThanPickingOne() {
            IllegalArgumentException thrown =
                    assertThrows(IllegalArgumentException.class, () -> tools().overview(null));

            assertTrue(thrown.getMessage().contains("kind is required"), thrown.getMessage());
            assertTrue(thrown.getMessage().contains("SOCKET, FILE"), thrown.getMessage());
        }
    }

    @Nested
    class Endpoints {

        @Test
        void ranksTheTargetsWithWhatEachCost() {
            when(ioManager.endpoints(IoKind.SOCKET)).thenReturn(List.of(endpoint(SOCKET_PEER)));

            JsonNode endpoint = answer("endpoints", tools().endpoints(IoKind.SOCKET)).get("endpoints").get(0);

            assertEquals(SOCKET_PEER, endpoint.get("target").asString());
            assertEquals(620, endpoint.get("opCount").asLong());
            assertEquals(9_000_000L, endpoint.get("maxNanos").asLong());
        }

        @Test
        void saysThereIsNoFileDataWhenNoFileEndpointWasRecorded() {
            when(ioManager.endpoints(IoKind.FILE)).thenReturn(List.of());

            JsonNode out = answer("endpoints", tools().endpoints(IoKind.FILE));

            assertEquals("NOT_RECORDED", out.get("status").asString());
            assertTrue(out.get("reason").asString().contains("recorded no file I/O events"), out.toString());
            assertEquals(0, out.get("endpoints").size());
            assertTrue(out.get("omittedEndpoints").isNull());
        }

        /**
         * A service that puts identifiers in its paths produces one endpoint per request, so the
         * ranking keeps its head and counts the tail it left out.
         */
        @Test
        void keepsTheHeadOfALongEndpointRankingAndCountsTheRest() {
            when(ioManager.endpoints(IoKind.SOCKET)).thenReturn(manyEndpoints());

            JsonNode out = answer("endpoints", tools().endpoints(IoKind.SOCKET));

            assertEquals(ENDPOINT_CAP, out.get("endpoints").size());
            assertEquals("peer-39", out.get("endpoints").get(39).get("target").asString());
            assertEquals(MORE_ENDPOINTS_THAN_THE_CAP - ENDPOINT_CAP, out.get("omittedEndpoints").asInt());
        }

        /** The event fields name an unknown host or path with their own label, so a target is never null. */
        @Test
        void aTargetIsNeverNull() {
            assertEquals("string", StructuredAnswers.schemaTypeOf(
                    IoMcpTools.class, "endpoints", "endpoints", "target").asString());
            assertEquals("string", StructuredAnswers.schemaTypeOf(
                    IoMcpTools.class, "slowest", "operations", "target").asString());
        }

        /** The slowest operation is only set by an operation that took time, so an all-zero recording has none. */
        @Test
        void anOverviewWithNoSlowestTargetConformsAsNull() {
            when(ioManager.overview(IoKind.SOCKET)).thenReturn(new IoOverview(10, 0, 3, 0, null, true));

            JsonNode out = answer("overview", tools().overview(IoKind.SOCKET));

            assertTrue(out.get("overview").get("slowestTarget").isNull());
        }

        @Test
        void refusesAMissingKindTheSameWayTheOverviewDoes() {
            assertThrows(IllegalArgumentException.class, () -> tools().endpoints(null));
        }
    }

    @Nested
    class Slowest {

        @Test
        void namesTheThreadThatWaitedOnEachOperation() {
            when(ioManager.slowestOperations(IoKind.SOCKET)).thenReturn(List.of(
                    new IoOperation("Socket Read", SOCKET_PEER, 8_192L, 96_000_000L, "http-nio-8080-exec-3")));

            JsonNode operation = answer("slowest", tools().slowest(IoKind.SOCKET)).get("operations").get(0);

            assertEquals("http-nio-8080-exec-3", operation.get("thread").asString());
            assertEquals(96_000_000L, operation.get("durationNanos").asLong());
            assertEquals(SOCKET_PEER, operation.get("target").asString());
        }

        @Test
        void anOperationWithoutAThreadConformsAsNull() {
            when(ioManager.slowestOperations(IoKind.SOCKET)).thenReturn(List.of(
                    new IoOperation("Socket Read", SOCKET_PEER, 8_192L, 96_000_000L, null)));

            JsonNode operation = answer("slowest", tools().slowest(IoKind.SOCKET)).get("operations").get(0);

            assertTrue(operation.get("thread").isNull());
        }

        @Test
        void saysThereIsNoDataWhenNoOperationWasRecorded() {
            when(ioManager.slowestOperations(IoKind.FILE)).thenReturn(List.of());

            JsonNode out = answer("slowest", tools().slowest(IoKind.FILE));

            assertEquals("NOT_RECORDED", out.get("status").asString());
            assertTrue(out.get("reason").asString().contains("recorded no file I/O events"), out.toString());
        }
    }

    /**
     * The kind is a real {@code enum}, so the binder holds the constants and names them itself; a
     * direct call can no longer express the mistake, which is why this one goes through a toolset.
     */
    @Nested
    class KindByName {

        @Test
        void refusesAnUnknownKindByName() {
            ReflectiveToolset toolset = new ReflectiveToolset(tools(), IO_PREFIX);

            ToolDispatchException thrown = assertThrows(ToolDispatchException.class,
                    () -> toolset.call(OVERVIEW_TOOL, Json.createObject().put(KIND_ARGUMENT, "network")));

            assertTrue(thrown.getMessage().contains("SOCKET, FILE"), thrown.getMessage());
        }

        @Test
        void acceptsAKindInAnyCase() {
            when(ioManager.overview(IoKind.FILE)).thenReturn(overview(FILE_PATH));
            ReflectiveToolset toolset = new ReflectiveToolset(tools(), IO_PREFIX);

            String out = toolset.call(OVERVIEW_TOOL, Json.createObject().put(KIND_ARGUMENT, "file"));

            assertTrue(out.contains("\"kind\":\"FILE\""), out);
        }
    }
}
