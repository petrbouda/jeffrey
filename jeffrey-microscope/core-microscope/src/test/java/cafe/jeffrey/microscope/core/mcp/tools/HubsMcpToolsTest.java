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

import cafe.jeffrey.hub.client.DiscoveryClient;
import cafe.jeffrey.hub.client.manager.RepositoryManager;
import cafe.jeffrey.microscope.core.manager.project.ProjectManager;
import cafe.jeffrey.microscope.core.manager.project.ProjectsManager;
import cafe.jeffrey.microscope.core.manager.recordings.RecordingsManager;
import cafe.jeffrey.microscope.core.manager.hub.HubManager;
import cafe.jeffrey.microscope.core.manager.hub.HubsManager;
import cafe.jeffrey.microscope.core.manager.workspace.WorkspaceManager;
import cafe.jeffrey.microscope.core.mcp.tools.hubs.HubSessionRef;
import cafe.jeffrey.microscope.core.web.ProjectManagerResolver;
import cafe.jeffrey.microscope.persistence.api.RecordingTag;
import cafe.jeffrey.recordings.core.RecordingsDownloadManager;
import cafe.jeffrey.profile.mcp.ReflectiveToolset;
import cafe.jeffrey.profile.mcp.McpToolOutput;
import tools.jackson.databind.JsonNode;
import cafe.jeffrey.profile.mcp.ToolDispatchException;
import cafe.jeffrey.profile.manager.ProfileManager;
import cafe.jeffrey.shared.common.Json;
import cafe.jeffrey.shared.common.exception.ErrorCode;
import cafe.jeffrey.shared.common.exception.Exceptions;
import cafe.jeffrey.shared.common.exception.JeffreyException;
import cafe.jeffrey.shared.common.model.ProjectInfo;
import cafe.jeffrey.shared.common.model.ProfileInfo;
import cafe.jeffrey.shared.common.model.Recording;
import cafe.jeffrey.shared.common.model.RecordingEventSource;
import cafe.jeffrey.shared.common.model.hub.HubAddress;
import cafe.jeffrey.shared.common.model.hub.HubInfo;
import cafe.jeffrey.shared.common.model.hub.HubSource;
import cafe.jeffrey.shared.common.model.repository.RecordingSession;
import cafe.jeffrey.shared.common.model.repository.RecordingSessionFilter;
import cafe.jeffrey.shared.common.model.repository.RecordingStatus;
import cafe.jeffrey.shared.common.model.repository.RepositoryFile;
import cafe.jeffrey.shared.common.model.repository.SupportedRecordingFile;
import cafe.jeffrey.shared.common.model.workspace.WorkspaceInfo;
import cafe.jeffrey.shared.common.model.workspace.WorkspaceStatus;
import io.grpc.Context;
import io.grpc.Status;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.InvocationTargetException;
import java.util.stream.IntStream;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class HubsMcpToolsTest {

    private static final Instant NOW = Instant.parse("2026-03-01T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private static final String HUB_ID = "cfg-production";
    private static final String WORKSPACE_ID = "ws-1";
    private static final String PROJECT_ID = "proj-1";
    private static final String SESSION_ID = "session-1";
    private static final HubSessionRef REF =
            new HubSessionRef(HUB_ID, WORKSPACE_ID, PROJECT_ID, SESSION_ID);

    private final HubsManager hubsManager = mock(HubsManager.class);
    private final ProjectManagerResolver resolver = mock(ProjectManagerResolver.class);
    private final RecordingsManager recordingsManager = mock(RecordingsManager.class);

    private final HubsMcpTools tools =
            new HubsMcpTools(hubsManager, resolver, recordingsManager, CLOCK);

    private static HubInfo hubInfo(String id, String name) {
        return new HubInfo(id, name, new HubAddress("hub.example.com", 443, false), NOW, HubSource.CONFIG);
    }

    private static RepositoryFile file(String id, String name, SupportedRecordingFile type) {
        return new RepositoryFile(id, name, NOW, 1024L, type, RecordingStatus.FINISHED, null);
    }

    private static RecordingSession session(String id, Instant createdAt, RepositoryFile... files) {
        return new RecordingSession(id, id, "inst-1", createdAt, createdAt.plusSeconds(600),
                RecordingStatus.FINISHED, null, List.of(files), false);
    }

    private static RecordingSession jfrSession(String id, Instant createdAt) {
        return session(id, createdAt, file("f-1", "recording.jfr", SupportedRecordingFile.JFR));
    }

    private RepositoryManager repositoryWith(RecordingSession... sessions) {
        RepositoryManager repo = mock(RepositoryManager.class);
        when(repo.listRecordingSessions(anyBoolean(), any())).thenReturn(List.of(sessions));
        return repo;
    }

    /**
     * A hub that answers its probe, with one workspace holding one project.
     */
    private HubManager reachableHub(String hubId, String hubName, String projectName, RepositoryManager repo) {
        HubManager hub = mock(HubManager.class);
        when(hub.info()).thenReturn(hubInfo(hubId, hubName));
        when(hub.tryInfo()).thenReturn(Optional.of(new DiscoveryClient.PublicApiInfo("2.1.0", 1)));
        when(hub.infoOrThrow()).thenReturn(new DiscoveryClient.PublicApiInfo("2.1.0", 1));
        WorkspaceInfo workspace = new WorkspaceInfo(
                WORKSPACE_ID, "ref", "repo", "default", null, null, NOW, WorkspaceStatus.AVAILABLE, 1);
        when(hub.workspaces()).thenReturn(List.of(workspace));
        when(hub.workspacesOrThrow()).thenReturn(List.of(workspace));

        ProjectManager project = mock(ProjectManager.class);
        when(project.info()).thenReturn(new ProjectInfo(
                PROJECT_ID, "origin", projectName, projectName, "ns", WORKSPACE_ID, NOW, NOW, Map.of(), null));
        when(project.repositoryManager()).thenReturn(repo);

        ProjectsManager projects = mock(ProjectsManager.class);
        when(projects.findAll()).thenReturn(List.of(project));
        when(projects.findAllOrThrow()).thenReturn(List.of(project));

        WorkspaceManager workspaceManager = mock(WorkspaceManager.class);
        when(workspaceManager.projectsManager()).thenReturn(projects);
        when(hub.workspace(WORKSPACE_ID)).thenReturn(Optional.of(workspaceManager));
        when(hub.workspace(workspace)).thenReturn(workspaceManager);
        return hub;
    }

    private void noLocalRecordings() {
        when(recordingsManager.listRecordings()).thenReturn(List.of());
    }

    private void localRecording(String recordingId, String profileId, HubSessionRef ref) {
        when(recordingsManager.listRecordings()).thenReturn(List.of(new Recording(
                recordingId, recordingId, null, null, RecordingEventSource.JDK, NOW, NOW, NOW,
                profileId != null, profileId, profileId, List.of())));
        when(recordingsManager.tagsForRecordings(any())).thenReturn(Map.of(recordingId, List.of(
                new RecordingTag("origin.hubId", ref.hubId()),
                new RecordingTag("origin.workspaceId", ref.workspaceId()),
                new RecordingTag("origin.projectId", ref.projectId()),
                new RecordingTag("origin.recordingId", ref.sessionId()))));
        if (profileId != null) {
            ProfileInfo profileInfo = mock(ProfileInfo.class);
            when(profileInfo.enabled()).thenReturn(true);
            ProfileManager profileManager = mock(ProfileManager.class);
            when(profileManager.info()).thenReturn(profileInfo);
            when(recordingsManager.profile(profileId)).thenReturn(Optional.of(profileManager));
        }
    }

    @Nested
    class CataloguePages {

        private JsonNode page(HubsMcpTools target, String hub, Integer minutes, int limit, String cursor) {
            var method = assertDoesNotThrow(() -> HubsMcpTools.class.getMethod("sessions",
                    String.class, String.class, String.class, Integer.class, RecordingStatus.class,
                    Integer.class, String.class), "sessions must expose cursor pagination");
            return Json.toTree(assertDoesNotThrow(() -> method.invoke(target, hub, null, null,
                    minutes, null, limit, cursor)));
        }

        @Test
        void returnsCompleteEmptyCatalogueMetadata() {
            when(hubsManager.findAll()).thenReturn(List.of());
            JsonNode result = page(tools, null, null, 1, null).path("structuredContent");
            assertEquals(0, result.path("returned").asInt(-1));
            assertEquals(0, result.path("total").asInt(-1));
            assertEquals(0, result.path("observedTotal").asInt(-1));
            assertTrue(result.path("complete").asBoolean());
            assertFalse(result.path("hasMore").asBoolean());
            assertTrue(result.path("nextCursor").isNull());
            assertTrue(result.path("sessions").isArray());
        }

        @Test
        void pagesTiedTimestampsByFullReferenceAndReadsAllProjectRows() {
            RepositoryManager repo = mock(RepositoryManager.class);
            when(repo.listRecordingSessions(anyBoolean(), any())).thenAnswer(call -> {
                RecordingSessionFilter filter = call.getArgument(1);
                return filter.apply(List.of(jfrSession("c", NOW), jfrSession("b", NOW), jfrSession("a", NOW)));
            });
            HubManager hub = reachableHub(HUB_ID, "production", "checkout", repo);
            when(hubsManager.findAll()).thenReturn(List.of(hub));
            noLocalRecordings();
            JsonNode first = page(tools, " PROD ", null, 1, null).path("structuredContent");
            assertEquals(3, first.path("total").asInt());
            assertEquals(3, first.path("observedTotal").asInt());
            assertEquals("a", HubSessionRef.decode(first.path("sessions").get(0).path("session_ref").asText()).sessionId());
            assertTrue(first.path("hasMore").asBoolean());
            JsonNode second = page(tools, "prod", null, 2, first.path("nextCursor").asText()).path("structuredContent");
            assertEquals(2, second.path("returned").asInt());
            assertEquals("b", HubSessionRef.decode(second.path("sessions").get(0).path("session_ref").asText()).sessionId());
            assertFalse(second.path("hasMore").asBoolean());
            assertTrue(second.path("nextCursor").isNull());
        }

        @Test
        void partialEmptyCatalogueNeverClaimsThereAreZeroSessions() {
            HubManager down = mock(HubManager.class);
            when(down.info()).thenReturn(hubInfo(HUB_ID, "production"));
            when(down.infoOrThrow()).thenThrow(Status.UNAVAILABLE.asRuntimeException());
            when(hubsManager.findAll()).thenReturn(List.of(down));
            noLocalRecordings();
            JsonNode result = page(tools, null, null, 1, null);
            JsonNode data = result.path("structuredContent");
            assertTrue(data.path("total").isNull());
            assertEquals(0, data.path("observedTotal").asInt(-1));
            assertFalse(data.path("complete").asBoolean(true));
            assertFalse(data.path("hasMore").asBoolean());
            assertEquals("production", data.path("failures").get(0).path("hubName").asText());
            assertTrue(result.path("text").asText().contains("Not listed"));
        }

        @Test
        void boundsLargeFailureDetailsWhileKeepingTheScanIncomplete() {
            HubManager down = mock(HubManager.class);
            when(down.info()).thenReturn(hubInfo(HUB_ID, "production".repeat(20000)));
            when(down.infoOrThrow()).thenThrow(new IllegalStateException("failure".repeat(20000)));
            when(hubsManager.findAll()).thenReturn(List.of(down));
            noLocalRecordings();
            JsonNode result = page(tools, null, null, 1, null);
            assertTrue(result.path("text").asText().length() <= McpToolOutput.MAX_CHARS);
            assertTrue(Json.toString(result.path("structuredContent")).length() <= McpToolOutput.MAX_CHARS);
            assertFalse(result.path("structuredContent").path("complete").asBoolean(true));
            assertTrue(result.path("structuredContent").path("total").isNull());
        }

        @Test
        void liveCursorSurvivesDeletionAndSkipsNewerInsertions() {
            RepositoryManager repo = repositoryWith(jfrSession("a", NOW), jfrSession("b", NOW.minusSeconds(1)));
            HubManager hub = reachableHub(HUB_ID, "production", "checkout", repo);
            when(hubsManager.findAll()).thenReturn(List.of(hub));
            noLocalRecordings();
            String cursor = page(tools, null, null, 1, null).path("structuredContent").path("nextCursor").asText();
            when(repo.listRecordingSessions(anyBoolean(), any())).thenReturn(List.of(
                    jfrSession("new", NOW.plusSeconds(1)), jfrSession("b", NOW.minusSeconds(1))));
            JsonNode next = page(tools, null, null, 1, cursor).path("structuredContent");
            assertEquals("b", HubSessionRef.decode(next.path("sessions").get(0).path("session_ref").asText()).sessionId());
            assertFalse(next.path("hasMore").asBoolean());
        }

        @Test
        void cursorKeepsTheOriginalActiveWindowCutoff() {
            RepositoryManager repo = repositoryWith(jfrSession("a", NOW), jfrSession("b", NOW.minusSeconds(1)));
            HubManager hub = reachableHub(HUB_ID, "production", "checkout", repo);
            when(hubsManager.findAll()).thenReturn(List.of(hub));
            noLocalRecordings();
            String cursor = page(tools, null, 60, 1, null).path("structuredContent").path("nextCursor").asText();
            HubsMcpTools later = new HubsMcpTools(hubsManager, resolver, recordingsManager,
                    Clock.fixed(NOW.plusSeconds(120), ZoneOffset.UTC));
            page(later, null, 60, 1, cursor);
            ArgumentCaptor<RecordingSessionFilter> filters = ArgumentCaptor.forClass(RecordingSessionFilter.class);
            verify(repo, times(2)).listRecordingSessions(eq(true), filters.capture());
            assertEquals(filters.getAllValues().get(0).activeFrom(), filters.getAllValues().get(1).activeFrom());
        }

        @Test
        void rejectsMalformedAndFilterMismatchedCursorsBeforeRemoteReads() {
            var method = assertDoesNotThrow(() -> HubsMcpTools.class.getMethod("sessions",
                    String.class, String.class, String.class, Integer.class, RecordingStatus.class,
                    Integer.class, String.class));
            var malformed = assertThrows(InvocationTargetException.class,
                    () -> method.invoke(tools, null, null, null, null, null, 1, "garbage"));
            assertTrue(malformed.getCause() instanceof IllegalArgumentException);
            verifyNoInteractions(hubsManager);
            RepositoryManager repo = repositoryWith(jfrSession("a", NOW), jfrSession("b", NOW));
            HubManager hub = reachableHub(HUB_ID, "production", "checkout", repo);
            when(hubsManager.findAll()).thenReturn(List.of(hub));
            noLocalRecordings();
            String cursor = page(tools, "prod", null, 1, null).path("structuredContent").path("nextCursor").asText();
            var mismatch = assertThrows(InvocationTargetException.class,
                    () -> method.invoke(tools, "stage", null, null, null, null, 1, cursor));
            assertTrue(mismatch.getCause() instanceof IllegalArgumentException);
        }

        @Test
        void characterLimitedPagesKeepIdentitiesAndContinueAfterTheLastReturnedRow() {
            RecordingSession[] sessions = IntStream.range(0, 500)
                    .mapToObj(i -> jfrSession("session-%04d".formatted(i), NOW.minusSeconds(i)))
                    .toArray(RecordingSession[]::new);
            HubManager hub = reachableHub(HUB_ID, "production".repeat(200),
                    "checkout".repeat(200), repositoryWith(sessions));
            when(hubsManager.findAll()).thenReturn(List.of(hub));
            noLocalRecordings();
            JsonNode first = page(tools, null, null, 500, null);
            JsonNode data = first.path("structuredContent");
            int returned = data.path("returned").asInt();
            assertTrue(returned > 0 && returned < 500);
            assertEquals(returned, data.path("sessions").size());
            assertTrue(first.path("text").asText().length() <= McpToolOutput.MAX_CHARS);
            assertTrue(Json.toString(data).length() <= McpToolOutput.MAX_CHARS);
            assertEquals(500, data.path("total").asInt());
            assertTrue(data.path("hasMore").asBoolean());
            JsonNode next = page(tools, null, null, 500, data.path("nextCursor").asText()).path("structuredContent");
            assertEquals("session-%04d".formatted(returned),
                    HubSessionRef.decode(next.path("sessions").get(0).path("session_ref").asText()).sessionId());
        }
    }

    @Nested
    class ListHubs {

        @Test
        void namesEveryConnectedHubWithItsAddress() {
            HubManager production = reachableHub(HUB_ID, "production", "checkout", repositoryWith());
            when(hubsManager.findAll()).thenReturn(List.of(production));

            String result = tools.list();

            assertTrue(result.contains("production"), result);
            assertTrue(result.contains("hub.example.com:443"), result);
            assertTrue(result.contains("ok"), result);
        }

        @Test
        void marksAHubThatDidNotAnswer() {
            HubManager down = mock(HubManager.class);
            when(down.info()).thenReturn(hubInfo(HUB_ID, "production"));
            when(down.tryInfo()).thenReturn(Optional.empty());
            when(hubsManager.findAll()).thenReturn(List.of(down));

            assertTrue(tools.list().contains("unreachable"));
        }

        @Test
        void saysSoWhenNoHubIsConnectedAtAll() {
            when(hubsManager.findAll()).thenReturn(List.of());

            String result = tools.list();

            assertTrue(result.contains("No Jeffrey Hub is connected"), result);
            assertTrue(result.contains("recordings_analyzeFile"), result);
        }

        @Test
        void distinguishesAConfiguredHubFromOneAddedByHand() {
            HubManager configured = reachableHub(HUB_ID, "production", "checkout", repositoryWith());
            when(hubsManager.findAll()).thenReturn(List.of(configured));

            assertTrue(tools.list().contains("config"));
        }
    }

    @Nested
    class Sessions {

        @Test
        void rendersOneRowPerSessionCarryingItsRef() {
            HubManager production = reachableHub(
                    HUB_ID, "production", "checkout", repositoryWith(jfrSession(SESSION_ID, NOW)));
            when(hubsManager.findAll()).thenReturn(List.of(production));
            noLocalRecordings();

            String result = tools.sessions(null, null, null, null, null, null);

            assertTrue(result.contains("production"), result);
            assertTrue(result.contains("checkout"), result);
            assertTrue(result.contains(REF.encode()), result);
        }

        @Test
        void marksASessionAlreadyDownloadedButNotAnalysed() {
            HubManager production = reachableHub(
                    HUB_ID, "production", "checkout", repositoryWith(jfrSession(SESSION_ID, NOW)));
            when(hubsManager.findAll()).thenReturn(List.of(production));
            localRecording("rec-1", null, REF);

            String result = tools.sessions(null, null, null, null, null, null);

            assertTrue(result.contains("recording:rec-1"), result);
        }

        @Test
        void showsTheProfileForASessionAlreadyAnalysed() {
            HubManager production = reachableHub(
                    HUB_ID, "production", "checkout", repositoryWith(jfrSession(SESSION_ID, NOW)));
            when(hubsManager.findAll()).thenReturn(List.of(production));
            localRecording("rec-1", "profile-1", REF);

            String result = tools.sessions(null, null, null, null, null, null);

            assertTrue(result.contains("profile:profile-1"), result);
        }

        @Test
        void reportsAnUnreachableHubUnderTheTable() {
            HubManager down = mock(HubManager.class);
            when(down.info()).thenReturn(hubInfo("cfg-down", "production"));
            when(down.tryInfo()).thenReturn(Optional.empty());
            when(down.infoOrThrow())
                    .thenThrow(Status.UNAVAILABLE.withDescription("connection refused").asRuntimeException());
            HubManager up = reachableHub(
                    "cfg-up", "staging", "search", repositoryWith(jfrSession(SESSION_ID, NOW)));
            when(hubsManager.findAll()).thenReturn(List.of(down, up));
            noLocalRecordings();

            String result = tools.sessions(null, null, null, null, null, null);

            assertTrue(result.contains("Not listed"), result);
            assertTrue(result.contains("production"), result);
        }

        @Test
        void reportsAnUnreachableHubEvenWhenNothingMatched() {
            // The answer that would otherwise mislead: "no sessions" while production is simply down.
            HubManager down = mock(HubManager.class);
            when(down.info()).thenReturn(hubInfo("cfg-down", "production"));
            when(down.tryInfo()).thenReturn(Optional.empty());
            when(down.infoOrThrow())
                    .thenThrow(Status.UNAVAILABLE.withDescription("connection refused").asRuntimeException());
            when(hubsManager.findAll()).thenReturn(List.of(down));
            noLocalRecordings();

            String result = tools.sessions(null, null, null, null, null, null);

            assertTrue(result.contains("Not listed"), result);
            assertFalse(result.contains("No recording sessions matched"), result);
        }

        @Test
        void translatesTheLastHourIntoAWindowOnTheHub() {
            RepositoryManager repo = repositoryWith(jfrSession(SESSION_ID, NOW));
            HubManager production = reachableHub(HUB_ID, "production", "checkout", repo);
            when(hubsManager.findAll()).thenReturn(List.of(production));
            noLocalRecordings();

            tools.sessions(null, null, null, 60, null, null);

            ArgumentCaptor<RecordingSessionFilter> captor =
                    ArgumentCaptor.forClass(RecordingSessionFilter.class);
            verify(repo).listRecordingSessions(eq(true), captor.capture());
            assertEquals(NOW.minus(Duration.ofHours(1)), captor.getValue().activeFrom());
        }

        @Test
        void pushesTheStatusDownToTheHub() {
            RepositoryManager repo = repositoryWith(jfrSession(SESSION_ID, NOW));
            HubManager production = reachableHub(HUB_ID, "production", "checkout", repo);
            when(hubsManager.findAll()).thenReturn(List.of(production));
            noLocalRecordings();

            tools.sessions(null, null, null, null, RecordingStatus.ACTIVE, null);

            ArgumentCaptor<RecordingSessionFilter> captor =
                    ArgumentCaptor.forClass(RecordingSessionFilter.class);
            verify(repo).listRecordingSessions(eq(true), captor.capture());
            assertEquals(RecordingStatus.ACTIVE, captor.getValue().status());
        }

        /**
         * The status is a real {@code enum} argument now, so the schema carries the two constants and
         * the binder refuses anything else. The mistake can only be made through a call, which is
         * where the test now makes it.
         */
        @Test
        void rejectsAStatusThatIsNotOne() {
            ReflectiveToolset toolset = new ReflectiveToolset(tools, "hubs");

            ToolDispatchException e = assertThrows(ToolDispatchException.class,
                    () -> toolset.call("hubs_sessions", Json.createObject().put("status", "RUNNING")));

            assertTrue(e.getMessage().contains("ACTIVE"), e.getMessage());
        }

        @Test
        void rejectsAWindowThatIsNotAWindow() {
            assertThrows(IllegalArgumentException.class,
                    () -> tools.sessions(null, null, null, 0, null, null));
        }

        @Test
        void readsEveryProjectRowBeforeApplyingThePageLimit() {
            RepositoryManager repo = repositoryWith(jfrSession(SESSION_ID, NOW));
            HubManager production = reachableHub(HUB_ID, "production", "checkout", repo);
            when(hubsManager.findAll()).thenReturn(List.of(production));
            noLocalRecordings();

            tools.sessions(null, null, null, null, null, 10_000);

            ArgumentCaptor<RecordingSessionFilter> captor =
                    ArgumentCaptor.forClass(RecordingSessionFilter.class);
            verify(repo).listRecordingSessions(eq(true), captor.capture());
            assertEquals(RecordingSessionFilter.NO_LIMIT, captor.getValue().limit());
        }

        @Test
        void keepsAPipeInAProjectNameOffTheColumnBoundaries() {
            HubManager production = reachableHub(
                    HUB_ID, "production", "check|out", repositoryWith(jfrSession(SESSION_ID, NOW)));
            when(hubsManager.findAll()).thenReturn(List.of(production));
            noLocalRecordings();

            assertTrue(tools.sessions(null, null, null, null, null, null).contains("check/out"));
        }

        @Test
        void saysHowToWidenAWindowThatMatchedNothing() {
            HubManager production = reachableHub(HUB_ID, "production", "checkout", repositoryWith());
            when(hubsManager.findAll()).thenReturn(List.of(production));

            String result = tools.sessions(null, null, null, 60, null, null);

            assertTrue(result.contains("withinLastMinutes"), result);
            assertTrue(result.contains("60"), result);
        }

        @Test
        void pointsAtTheHubsWhenNothingMatchedAndNoWindowWasGiven() {
            HubManager production = reachableHub(HUB_ID, "production", "checkout", repositoryWith());
            when(hubsManager.findAll()).thenReturn(List.of(production));

            assertTrue(tools.sessions(null, null, null, null, null, null).contains("hubs_list"));
        }
    }

    @Nested
    class Download {

        private ProjectManager projectWith(RecordingSession session, RecordingsDownloadManager downloads) {
            RepositoryManager repo = mock(RepositoryManager.class);
            when(repo.recordingSession(SESSION_ID)).thenReturn(session);

            ProjectManager project = mock(ProjectManager.class);
            when(project.repositoryManager()).thenReturn(repo);
            when(project.info()).thenReturn(new ProjectInfo(
                    PROJECT_ID, "origin", "checkout", "checkout", "ns", WORKSPACE_ID, NOW, NOW, Map.of(), null));
            if (downloads != null) {
                when(project.recordingsDownloadManager()).thenReturn(downloads);
            }
            return project;
        }

        private void resolvesTo(ProjectManager project) {
            resolvesTo(REF, project);
        }

        private void resolvesTo(HubSessionRef ref, ProjectManager project) {
            HubManager hub = mock(HubManager.class);
            when(hub.info()).thenReturn(hubInfo(ref.hubId(), "production"));
            when(resolver.resolveHub(ref.hubId())).thenReturn(hub);
            when(resolver.resolveStrict(ref.hubId(), ref.workspaceId(), ref.projectId())).thenReturn(
                    new ProjectManagerResolver.ProjectContext(
                            mock(WorkspaceManager.class), mock(ProjectsManager.class), project));
        }

        private HubsMcpTools toolsWithBudget(Duration responseBudget) {
            return new HubsMcpTools(
                    hubsManager,
                    resolver,
                    recordingsManager,
                    CLOCK,
                    Duration.ofSeconds(1),
                    responseBudget,
                    Duration.ofSeconds(5));
        }

        @Test
        void downloadsTheSessionTheRefNamesAndReturnsTheRecordingId() {
            RecordingsDownloadManager downloads = mock(RecordingsDownloadManager.class);
            when(downloads.mergeAndDownloadSession(SESSION_ID)).thenReturn("rec-new");
            resolvesTo(projectWith(jfrSession(SESSION_ID, NOW), downloads));
            noLocalRecordings();

            String result = tools.download(REF.encode());

            assertTrue(result.contains("\"recordingId\":\"rec-new\""), result);
            verify(downloads).mergeAndDownloadSession(SESSION_ID);
        }

        @Test
        void pointsAtRecordingsAnalyzeRecordingAsTheNextStep() {
            RecordingsDownloadManager downloads = mock(RecordingsDownloadManager.class);
            when(downloads.mergeAndDownloadSession(SESSION_ID)).thenReturn("rec-new");
            resolvesTo(projectWith(jfrSession(SESSION_ID, NOW), downloads));
            noLocalRecordings();

            assertTrue(tools.download(REF.encode()).contains("recordings_analyzeRecording"));
        }

        @Test
        void countsTheRecordingAndArtifactFilesItBrought() {
            RecordingsDownloadManager downloads = mock(RecordingsDownloadManager.class);
            when(downloads.mergeAndDownloadSession(SESSION_ID)).thenReturn("rec-new");
            RecordingSession withHeapDump = session(SESSION_ID, NOW,
                    file("f-1", "recording.jfr", SupportedRecordingFile.JFR),
                    file("f-2", "heap.hprof", SupportedRecordingFile.HEAP_DUMP));
            resolvesTo(projectWith(withHeapDump, downloads));
            noLocalRecordings();

            String result = tools.download(REF.encode());

            assertTrue(result.contains("\"recordingFiles\":1"), result);
            assertTrue(result.contains("\"artifactFiles\":1"), result);
        }

        @Test
        void returnsTheExistingRecordingRatherThanFetchingItTwice() {
            localRecording("rec-existing", null, REF);

            String result = tools.download(REF.encode());

            assertTrue(result.contains("rec-existing"), result);
            verifyNoInteractions(resolver);
        }

        @Test
        void pointsStraightAtAnalysisWhenTheSessionIsAlreadyAProfile() {
            localRecording("rec-existing", "profile-1", REF);

            String result = tools.download(REF.encode());

            assertTrue(result.contains("profile-1"), result);
            verifyNoInteractions(resolver);
        }

        @Test
        void rejectsAMalformedRefWithoutOpeningAHubConnection() {
            assertThrows(IllegalArgumentException.class, () -> tools.download("not-a-ref"));

            verifyNoInteractions(resolver);
            verifyNoInteractions(recordingsManager);
        }

        @Test
        void explainsARefWhoseHubIsGone() {
            noLocalRecordings();
            when(resolver.resolveHub(HUB_ID)).thenThrow(Exceptions.invalidRequest("Hub not found"));

            IllegalArgumentException e = assertThrows(
                    IllegalArgumentException.class, () -> tools.download(REF.encode()));

            assertTrue(e.getMessage().contains("hubs_sessions"), e.getMessage());
        }

        @Test
        void explainsASessionTheHubNoLongerHas() {
            noLocalRecordings();
            RepositoryManager repo = mock(RepositoryManager.class);
            when(repo.recordingSession(SESSION_ID))
                    .thenThrow(Exceptions.invalidRequest("Session not found"));
            ProjectManager project = mock(ProjectManager.class);
            when(project.repositoryManager()).thenReturn(repo);
            resolvesTo(project);

            IllegalArgumentException e = assertThrows(
                    IllegalArgumentException.class, () -> tools.download(REF.encode()));

            assertTrue(e.getMessage().contains("retention"), e.getMessage());
        }

        @Test
        void refusesASessionWithNothingFinishedToDownload() {
            RecordingsDownloadManager downloads = mock(RecordingsDownloadManager.class);
            RecordingSession logsOnly = session(SESSION_ID, NOW,
                    file("f-1", "gc.log", SupportedRecordingFile.JVM_LOG));
            resolvesTo(projectWith(logsOnly, downloads));
            noLocalRecordings();

            IllegalArgumentException e = assertThrows(
                    IllegalArgumentException.class, () -> tools.download(REF.encode()));

            assertTrue(e.getMessage().contains("no finished recording file"), e.getMessage());
            verify(downloads, never()).mergeAndDownloadSession(any());
        }

        @Test
        void sameSessionIdOnDifferentHubCoordinatesStartsIndependentTransfers() throws Exception {
            HubSessionRef otherRef = new HubSessionRef("cfg-staging", "ws-2", "proj-2", SESSION_ID);
            CountDownLatch started = new CountDownLatch(2);
            CountDownLatch release = new CountDownLatch(1);

            RecordingsDownloadManager first = blockingDownload("rec-first", started, release);
            RecordingsDownloadManager second = blockingDownload("rec-second", started, release);
            resolvesTo(REF, projectWith(jfrSession(SESSION_ID, NOW), first));
            resolvesTo(otherRef, projectWith(jfrSession(SESSION_ID, NOW), second));
            noLocalRecordings();

            HubsMcpTools shortBudgetTools = toolsWithBudget(Duration.ofMillis(200));
            try (ExecutorService callers = Executors.newFixedThreadPool(2)) {
                Future<String> firstCall = callers.submit(() -> shortBudgetTools.download(REF.encode()));
                Future<String> secondCall = callers.submit(() -> shortBudgetTools.download(otherRef.encode()));

                boolean bothStarted;
                try {
                    bothStarted = started.await(1, TimeUnit.SECONDS);
                } finally {
                    release.countDown();
                }

                firstCall.get(2, TimeUnit.SECONDS);
                secondCall.get(2, TimeUnit.SECONDS);
                assertTrue(bothStarted, "transfers sharing only sessionId must not join");
            }
        }

        @Test
        void concurrentCallsWithTheSameFullRefJoinOneTransfer() throws Exception {
            CountDownLatch preflights = new CountDownLatch(2);
            CountDownLatch transferStarted = new CountDownLatch(1);
            CountDownLatch release = new CountDownLatch(1);
            AtomicInteger transfers = new AtomicInteger();
            AtomicBoolean persisted = new AtomicBoolean();
            Recording recording = mock(Recording.class);
            when(recordingsManager.findRecording("rec-new"))
                    .thenAnswer(_ -> persisted.get() ? Optional.of(recording) : Optional.empty());

            RepositoryManager repository = mock(RepositoryManager.class);
            when(repository.recordingSession(SESSION_ID)).thenAnswer(_ -> {
                preflights.countDown();
                return jfrSession(SESSION_ID, NOW);
            });
            RecordingsDownloadManager downloads = mock(RecordingsDownloadManager.class);
            when(downloads.mergeAndDownloadSession(SESSION_ID)).thenAnswer(_ -> {
                transfers.incrementAndGet();
                transferStarted.countDown();
                release.await(2, TimeUnit.SECONDS);
                persisted.set(true);
                return "rec-new";
            });
            ProjectManager project = projectWith(jfrSession(SESSION_ID, NOW), downloads);
            when(project.repositoryManager()).thenReturn(repository);
            resolvesTo(project);
            noLocalRecordings();

            HubsMcpTools shortBudgetTools = toolsWithBudget(Duration.ofSeconds(1));
            try (ExecutorService callers = Executors.newFixedThreadPool(2)) {
                Future<String> firstCall = callers.submit(() -> shortBudgetTools.download(REF.encode()));
                Future<String> secondCall = callers.submit(() -> shortBudgetTools.download(REF.encode()));

                assertTrue(preflights.await(1, TimeUnit.SECONDS));
                assertTrue(transferStarted.await(1, TimeUnit.SECONDS));
                release.countDown();

                assertTrue(firstCall.get(2, TimeUnit.SECONDS).contains("rec-new"));
                assertTrue(secondCall.get(2, TimeUnit.SECONDS).contains("rec-new"));
                assertEquals(1, transfers.get());
            } finally {
                release.countDown();
            }
        }

        @Test
        void reusesTransferCompletedWhileAnotherCallWasStillInPreflight() throws Exception {
            CountDownLatch secondPreflight = new CountDownLatch(1);
            CountDownLatch releasePreflight = new CountDownLatch(1);
            CountDownLatch transferStarted = new CountDownLatch(1);
            AtomicInteger preflightCalls = new AtomicInteger();
            AtomicInteger transfers = new AtomicInteger();
            AtomicBoolean persisted = new AtomicBoolean();
            Recording recording = mock(Recording.class);
            when(recordingsManager.findRecording("rec-new"))
                    .thenAnswer(_ -> persisted.get() ? Optional.of(recording) : Optional.empty());
            RepositoryManager repository = mock(RepositoryManager.class);
            when(repository.recordingSession(SESSION_ID)).thenAnswer(_ -> {
                if (preflightCalls.incrementAndGet() == 2) {
                    secondPreflight.countDown();
                    assertTrue(releasePreflight.await(5, TimeUnit.SECONDS));
                }
                return jfrSession(SESSION_ID, NOW);
            });
            RecordingsDownloadManager downloads = mock(RecordingsDownloadManager.class);
            when(downloads.mergeAndDownloadSession(SESSION_ID)).thenAnswer(_ -> {
                transfers.incrementAndGet();
                transferStarted.countDown();
                assertTrue(secondPreflight.await(5, TimeUnit.SECONDS));
                persisted.set(true);
                return "rec-new";
            });
            ProjectManager project = projectWith(jfrSession(SESSION_ID, NOW), downloads);
            when(project.repositoryManager()).thenReturn(repository);
            resolvesTo(project);
            noLocalRecordings();
            try (ExecutorService callers = Executors.newFixedThreadPool(2)) {
                Future<String> first = callers.submit(() -> tools.download(REF.encode()));
                assertTrue(transferStarted.await(2, TimeUnit.SECONDS));
                Future<String> second = callers.submit(() -> tools.download(REF.encode()));
                assertTrue(first.get(3, TimeUnit.SECONDS).contains("rec-new"));
                releasePreflight.countDown();
                assertTrue(second.get(3, TimeUnit.SECONDS).contains("rec-new"));
                assertEquals(1, transfers.get());
            } finally {
                releasePreflight.countDown();
            }
        }

        @Test
        void retryPreflightFailureIsNotReplacedByThePreviousAttemptFailure() {
            RecordingsDownloadManager downloads = mock(RecordingsDownloadManager.class);
            when(downloads.mergeAndDownloadSession(SESSION_ID)).thenThrow(new IllegalStateException("old failure"));
            ProjectManager project = projectWith(jfrSession(SESSION_ID, NOW), downloads);
            when(project.repositoryManager().recordingSession(SESSION_ID))
                    .thenReturn(jfrSession(SESSION_ID, NOW))
                    .thenThrow(Status.UNAVAILABLE.withDescription("new preflight failure").asRuntimeException());
            resolvesTo(project);
            noLocalRecordings();
            assertThrows(IllegalStateException.class, () -> tools.download(REF.encode()));
            JeffreyException failure = assertThrows(JeffreyException.class, () -> tools.download(REF.encode(), true));
            assertEquals(ErrorCode.HUB_UNAVAILABLE, failure.getCode());
            assertTrue(failure.getMessage().contains("UNAVAILABLE"), failure.getMessage());
            verify(downloads, times(1)).mergeAndDownloadSession(SESSION_ID);
        }

        @Test
        void mcpFailuresReturnAnOperationIdAndRequireAnExplicitRetry() {
            RecordingsDownloadManager downloads = mock(RecordingsDownloadManager.class);
            when(downloads.mergeAndDownloadSession(SESSION_ID))
                    .thenThrow(new IllegalStateException("connection lost"))
                    .thenReturn("rec-retried");
            resolvesTo(projectWith(jfrSession(SESSION_ID, NOW), downloads));
            noLocalRecordings();
            var first = Json.mapper().readTree(tools.download(REF.encode(), false));
            String operationId = first.path("operationId").asString();
            assertFalse(operationId.isBlank());
            assertEquals("failed", first.path("status").asString());
            var retained = Json.mapper().readTree(tools.download(REF.encode(), false));
            assertEquals(operationId, retained.path("operationId").asString());
            verify(downloads, times(1)).mergeAndDownloadSession(SESSION_ID);
            var retry = Json.mapper().readTree(tools.download(REF.encode(), true));
            assertFalse(operationId.equals(retry.path("operationId").asString()));
            assertEquals("completed", retry.path("operation").path("status").asString());
        }

        @Test
        void failedTransferCanBeRetriedWithTheSameFullRef() {
            RecordingsDownloadManager downloads = mock(RecordingsDownloadManager.class);
            when(downloads.mergeAndDownloadSession(SESSION_ID))
                    .thenThrow(new IllegalStateException("connection lost"))
                    .thenReturn("rec-retried");
            resolvesTo(projectWith(jfrSession(SESSION_ID, NOW), downloads));
            noLocalRecordings();

            assertThrows(IllegalStateException.class, () -> tools.download(REF.encode()));

            assertThrows(IllegalStateException.class, () -> tools.download(REF.encode()));
            assertTrue(tools.download(REF.encode(), true).contains("rec-retried"));
        }

        @Test
        void lateFailureIsRetainedAcrossPollsUntilRetryIsExplicit() throws Exception {
            CountDownLatch transferStarted = new CountDownLatch(1);
            CountDownLatch release = new CountDownLatch(1);
            CountDownLatch failed = new CountDownLatch(1);
            AtomicInteger attempts = new AtomicInteger();
            RecordingsDownloadManager downloads = mock(RecordingsDownloadManager.class);
            when(downloads.mergeAndDownloadSession(SESSION_ID)).thenAnswer(_ -> {
                int attempt = attempts.incrementAndGet();
                if (attempt == 1) {
                    transferStarted.countDown();
                    release.await(1, TimeUnit.SECONDS);
                    failed.countDown();
                    throw new IllegalStateException("late connection loss");
                }
                return "rec-retried";
            });
            resolvesTo(projectWith(jfrSession(SESSION_ID, NOW), downloads));
            noLocalRecordings();
            HubsMcpTools shortBudgetTools = toolsWithBudget(Duration.ofMillis(50));

            String first = shortBudgetTools.download(REF.encode());
            assertTrue(first.contains("still running"), first);
            assertTrue(transferStarted.await(1, TimeUnit.SECONDS));
            release.countDown();
            assertTrue(failed.await(1, TimeUnit.SECONDS));

            assertThrows(IllegalStateException.class, () -> shortBudgetTools.download(REF.encode()));
            assertThrows(IllegalStateException.class, () -> shortBudgetTools.download(REF.encode()));
            verify(downloads, times(1)).mergeAndDownloadSession(SESSION_ID);

            assertTrue(shortBudgetTools.download(REF.encode(), true).contains("rec-retried"));
            verify(downloads, times(2)).mergeAndDownloadSession(SESSION_ID);
        }

        @Test
        void refetchesACompletedSessionWhenItsRetainedRecordingWasDeleted() {
            RecordingsDownloadManager downloads = mock(RecordingsDownloadManager.class);
            when(downloads.mergeAndDownloadSession(SESSION_ID))
                    .thenReturn("rec-deleted")
                    .thenReturn("rec-refetched");
            resolvesTo(projectWith(jfrSession(SESSION_ID, NOW), downloads));
            noLocalRecordings();
            when(recordingsManager.findRecording("rec-deleted")).thenReturn(Optional.empty());

            assertTrue(tools.download(REF.encode()).contains("rec-deleted"));
            assertTrue(tools.download(REF.encode()).contains("rec-refetched"));

            verify(downloads, times(2)).mergeAndDownloadSession(SESSION_ID);
        }

        @Test
        void failureDuringAPollPreflightDoesNotSilentlyRestartTheTransfer() throws Exception {
            CountDownLatch transferStarted = new CountDownLatch(1);
            CountDownLatch releaseTransfer = new CountDownLatch(1);
            CountDownLatch failurePublished = new CountDownLatch(1);
            CountDownLatch secondPreflight = new CountDownLatch(1);
            CountDownLatch releasePreflight = new CountDownLatch(1);
            AtomicInteger preflightCalls = new AtomicInteger();

            RepositoryManager repository = mock(RepositoryManager.class);
            when(repository.recordingSession(SESSION_ID)).thenAnswer(_ -> {
                if (preflightCalls.incrementAndGet() == 2) {
                    secondPreflight.countDown();
                    releasePreflight.await(2, TimeUnit.SECONDS);
                }
                return jfrSession(SESSION_ID, NOW);
            });
            RecordingsDownloadManager downloads = mock(RecordingsDownloadManager.class);
            when(downloads.mergeAndDownloadSession(SESSION_ID)).thenAnswer(_ -> {
                transferStarted.countDown();
                releaseTransfer.await(2, TimeUnit.SECONDS);
                throw new PublishedFailure(failurePublished);
            });
            ProjectManager project = projectWith(jfrSession(SESSION_ID, NOW), downloads);
            when(project.repositoryManager()).thenReturn(repository);
            resolvesTo(project);
            noLocalRecordings();
            HubsMcpTools shortBudgetTools = toolsWithBudget(Duration.ofMillis(50));

            assertTrue(shortBudgetTools.download(REF.encode()).contains("still running"));
            assertTrue(transferStarted.await(1, TimeUnit.SECONDS));
            try (ExecutorService callers = Executors.newSingleThreadExecutor()) {
                Future<String> poll = callers.submit(() -> shortBudgetTools.download(REF.encode()));
                assertTrue(secondPreflight.await(1, TimeUnit.SECONDS));
                releaseTransfer.countDown();
                assertTrue(failurePublished.await(1, TimeUnit.SECONDS));
                releasePreflight.countDown();

                assertThrows(ExecutionException.class,
                        () -> poll.get(1, TimeUnit.SECONDS));
                verify(downloads, times(1)).mergeAndDownloadSession(SESSION_ID);
            } finally {
                releaseTransfer.countDown();
                releasePreflight.countDown();
            }
        }

        @Test
        void preflightDeadlineCancelsTheRpcAndPreservesDeadlineStatus() throws Exception {
            CountDownLatch cancelled = new CountDownLatch(1);
            RepositoryManager repository = mock(RepositoryManager.class);
            when(repository.recordingSession(SESSION_ID)).thenAnswer(_ -> {
                Context.current().addListener(_ -> cancelled.countDown(), Runnable::run);
                cancelled.await(1, TimeUnit.SECONDS);
                throw Status.DEADLINE_EXCEEDED
                        .withDescription("download preflight deadline elapsed")
                        .asRuntimeException();
            });
            ProjectManager project = mock(ProjectManager.class);
            when(project.repositoryManager()).thenReturn(repository);
            resolvesTo(project);
            noLocalRecordings();

            JeffreyException exception = assertThrows(JeffreyException.class,
                    () -> toolsWithBudget(Duration.ofMillis(50)).download(REF.encode()));

            assertEquals(ErrorCode.HUB_UNAVAILABLE, exception.getCode());
            assertTrue(exception.getMessage().contains("DEADLINE_EXCEEDED"), exception.getMessage());
            assertTrue(cancelled.await(1, TimeUnit.SECONDS));
        }

        @Test
        void unavailableProjectLookupIsNotReportedAsAStaleRef() {
            noLocalRecordings();
            HubManager hub = mock(HubManager.class);
            when(hub.info()).thenReturn(hubInfo(HUB_ID, "production"));
            when(resolver.resolveHub(HUB_ID)).thenReturn(hub);
            when(resolver.resolveStrict(HUB_ID, WORKSPACE_ID, PROJECT_ID))
                    .thenThrow(Status.UNAVAILABLE.withDescription("connection refused").asRuntimeException());

            JeffreyException exception = assertThrows(
                    JeffreyException.class, () -> tools.download(REF.encode()));

            assertEquals(ErrorCode.HUB_UNAVAILABLE, exception.getCode());
            assertTrue(exception.getMessage().contains("UNAVAILABLE"), exception.getMessage());
        }

        private RecordingsDownloadManager blockingDownload(
                String recordingId,
                CountDownLatch started,
                CountDownLatch release) {

            RecordingsDownloadManager downloads = mock(RecordingsDownloadManager.class);
            when(downloads.mergeAndDownloadSession(SESSION_ID)).thenAnswer(_ -> {
                started.countDown();
                release.await(2, TimeUnit.SECONDS);
                return recordingId;
            });
            return downloads;
        }

        private static final class PublishedFailure extends IllegalStateException {

            private final CountDownLatch published;

            private PublishedFailure(CountDownLatch published) {
                super("late connection loss");
                this.published = published;
            }

            @Override
            public String getMessage() {
                published.countDown();
                return super.getMessage();
            }
        }
    }
}
