/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package cafe.jeffrey.microscope.core.mcp.tools;

import cafe.jeffrey.profile.manager.IoManager;
import cafe.jeffrey.profile.manager.ProfileManager;
import cafe.jeffrey.profile.manager.model.io.IoEndpoint;
import cafe.jeffrey.profile.manager.model.io.IoKind;
import cafe.jeffrey.profile.manager.model.io.IoOperation;
import cafe.jeffrey.profile.manager.model.io.IoOverview;
import cafe.jeffrey.profile.mcp.ReflectiveToolset;
import cafe.jeffrey.profile.mcp.ToolDispatchException;
import cafe.jeffrey.shared.common.Json;
import cafe.jeffrey.shared.common.model.ProfileInfo;
import cafe.jeffrey.shared.common.model.RecordingEventSource;
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

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
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
        return new IoMcpTools(profileManager);
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

    @Nested
    class Overview {

        @Test
        void carriesTheThroughputAndTheSlowestTargetForSockets() {
            when(ioManager.overview(IoKind.SOCKET)).thenReturn(overview(SOCKET_PEER));

            String out = tools().overview(IoKind.SOCKET);

            assertTrue(out.contains("\"kind\":\"SOCKET\""), out);
            assertTrue(out.contains("\"bytesRead\":4096000"), out);
            assertTrue(out.contains(SOCKET_PEER), out);
            assertTrue(out.contains(SOCKET_VIEW_LINK), out);
        }

        /**
         * The two kinds are separate pages, so an answer about files must not link the reader at the
         * socket dashboard - the figures would not be the ones they just read.
         */
        @Test
        void linksTheFileDashboardWhenTheQuestionWasAboutFiles() {
            when(ioManager.overview(IoKind.FILE)).thenReturn(overview(FILE_PATH));

            String out = tools().overview(IoKind.FILE);

            assertTrue(out.contains("\"kind\":\"FILE\""), out);
            assertTrue(out.contains(FILE_VIEW_LINK), out);
            assertFalse(out.contains(SOCKET_VIEW_LINK), out);
        }

        @Test
        void saysThereIsNoSocketDataRatherThanRenderingAZeroDashboard() {
            when(ioManager.overview(IoKind.SOCKET)).thenReturn(nothingRecorded());

            String out = tools().overview(IoKind.SOCKET);

            assertTrue(out.contains("recorded no socket I/O events"), out);
            assertFalse(out.contains("\"bytesRead\""), out);
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

            String out = tools().endpoints(IoKind.SOCKET);

            assertTrue(out.contains(SOCKET_PEER), out);
            assertTrue(out.contains("\"opCount\":620"), out);
            assertTrue(out.contains("\"maxNanos\":9000000"), out);
        }

        @Test
        void saysThereIsNoFileDataWhenNoFileEndpointWasRecorded() {
            when(ioManager.endpoints(IoKind.FILE)).thenReturn(List.of());

            String out = tools().endpoints(IoKind.FILE);

            assertTrue(out.contains("recorded no file I/O events"), out);
            assertFalse(out.contains("\"endpoints\""), out);
        }

        /**
         * A service that puts identifiers in its paths produces one endpoint per request, so the
         * ranking is rendered head-first and the tail is left out of the answer.
         */
        @Test
        void rendersOnlyTheHeadOfALongEndpointRanking() {
            when(ioManager.endpoints(IoKind.SOCKET)).thenReturn(manyEndpoints());

            String out = tools().endpoints(IoKind.SOCKET);

            assertTrue(out.contains("\"target\":\"peer-39\""), out);
            assertFalse(out.contains("\"target\":\"peer-40\""), out);
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

            String out = tools().slowest(IoKind.SOCKET);

            assertTrue(out.contains("\"thread\":\"http-nio-8080-exec-3\""), out);
            assertTrue(out.contains("\"durationNanos\":96000000"), out);
            assertTrue(out.contains(SOCKET_PEER), out);
        }

        @Test
        void saysThereIsNoDataWhenNoOperationWasRecorded() {
            when(ioManager.slowestOperations(IoKind.FILE)).thenReturn(List.of());

            assertTrue(tools().slowest(IoKind.FILE).contains("recorded no file I/O events"));
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
