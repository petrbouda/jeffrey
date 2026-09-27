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

import cafe.jeffrey.hub.client.DiscoveryClient;
import cafe.jeffrey.hub.client.manager.RepositoryManager;
import cafe.jeffrey.microscope.core.manager.hub.HubManager;
import cafe.jeffrey.microscope.core.manager.hub.HubsManager;
import cafe.jeffrey.microscope.core.manager.project.ProjectManager;
import cafe.jeffrey.microscope.core.manager.project.ProjectsManager;
import cafe.jeffrey.microscope.core.manager.recordings.RecordingsManager;
import cafe.jeffrey.microscope.core.manager.workspace.WorkspaceManager;
import cafe.jeffrey.microscope.core.mcp.AdvertisedFamiliesFixture;
import cafe.jeffrey.microscope.core.mcp.tools.hubs.DownloadWindowQuestion;
import cafe.jeffrey.microscope.core.mcp.tools.hubs.HubAnswers;
import cafe.jeffrey.microscope.core.mcp.tools.hubs.HubSessionRef;
import cafe.jeffrey.microscope.core.web.ProjectManagerResolver;
import cafe.jeffrey.microscope.mcp.protocol.McpCallContext;
import cafe.jeffrey.microscope.mcp.protocol.McpInputResponse;
import cafe.jeffrey.microscope.mcp.protocol.McpSchemaGenerator;
import cafe.jeffrey.microscope.mcp.protocol.McpTaskState;
import cafe.jeffrey.microscope.mcp.protocol.McpTaskStatus;
import cafe.jeffrey.microscope.mcp.protocol.McpToolOutcome;
import cafe.jeffrey.microscope.mcp.protocol.McpToolResult;
import cafe.jeffrey.microscope.mcp.protocol.testing.McpSchemaConformance;
import cafe.jeffrey.microscope.persistence.api.RecordingTag;
import cafe.jeffrey.profile.mcp.McpToolOutput;
import cafe.jeffrey.profile.mcp.ReflectiveToolset;
import cafe.jeffrey.recordings.core.RecordingsDownloadManager;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;
import cafe.jeffrey.microscope.mcp.protocol.ToolDispatchException;
import cafe.jeffrey.microscope.model.ProfileInfo;
import cafe.jeffrey.microscope.model.ProjectInfo;
import cafe.jeffrey.microscope.model.RecordingEventSource;
import cafe.jeffrey.microscope.model.hub.HubAddress;
import cafe.jeffrey.microscope.model.hub.HubInfo;
import cafe.jeffrey.microscope.model.hub.HubSource;
import cafe.jeffrey.microscope.model.repository.ChunkWindow;
import cafe.jeffrey.microscope.model.repository.RecordingSession;
import cafe.jeffrey.microscope.model.repository.RecordingSessionFilter;
import cafe.jeffrey.microscope.model.repository.RecordingStatus;
import cafe.jeffrey.microscope.model.repository.RepositoryFile;
import cafe.jeffrey.microscope.model.workspace.WorkspaceInfo;
import cafe.jeffrey.microscope.model.workspace.WorkspaceStatus;
import cafe.jeffrey.profile.manager.ProfileManager;
import cafe.jeffrey.shared.common.Json;
import cafe.jeffrey.shared.common.exception.ErrorCode;
import cafe.jeffrey.shared.common.exception.Exceptions;
import cafe.jeffrey.shared.common.exception.JeffreyException;
import cafe.jeffrey.storage.recording.api.file.FileCategory;
import cafe.jeffrey.storage.recording.api.file.ManagedFile;
import cafe.jeffrey.storage.recording.api.file.Recording;
import io.grpc.Context;
import io.grpc.Status;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
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
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicBoolean;

import static cafe.jeffrey.microscope.core.mcp.tools.McpCallContexts.ELICITING;
import static cafe.jeffrey.microscope.core.mcp.tools.McpCallContexts.ELICITING_TASKS;
import static cafe.jeffrey.microscope.core.mcp.tools.McpCallContexts.SHORT_TASK_WAIT;
import static cafe.jeffrey.microscope.core.mcp.tools.McpCallContexts.answering;
import static cafe.jeffrey.microscope.core.mcp.tools.McpCallContexts.TASKS;
import static cafe.jeffrey.microscope.core.mcp.tools.McpCallContexts.complete;
import static cafe.jeffrey.microscope.mcp.protocol.McpCallContext.RESOURCE_READ;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTimeout;
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
            HubsMcpToolsFixture.of(hubsManager, resolver, recordingsManager, CLOCK).build();

    /**
     * The request every link is built from, inherited by the threads a test starts: a tool is always
     * called on a request thread, and a test that calls it concurrently stands in for several requests.
     */
    @BeforeEach
    void bindRequest() {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()), true);
    }

    @AfterEach
    void unbindRequest() {
        RequestContextHolder.resetRequestAttributes();
    }

    private static HubInfo hubInfo(String id, String name) {
        return new HubInfo(id, name, new HubAddress("hub.example.com", 443, false), NOW, HubSource.CONFIG);
    }

    private static RepositoryFile file(String id, String name, ManagedFile type) {
        return new RepositoryFile(id, name, NOW, 1024L, type.fileCategory() == FileCategory.RECORDING, null);
    }

    private static RecordingSession session(String id, Instant createdAt, RepositoryFile... files) {
        return new RecordingSession(id, id, "inst-1", createdAt, createdAt.plusSeconds(600),
                RecordingStatus.FINISHED, null, List.of(files), false);
    }

    private static RecordingSession jfrSession(String id, Instant createdAt) {
        return session(id, createdAt, file("f-1", "recording.jfr", ManagedFile.JFR));
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
                PROJECT_ID, "origin", projectName, "ns", WORKSPACE_ID, NOW, NOW, Map.of(), null));
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
                recordingId, recordingId, null, RecordingEventSource.JDK, NOW, NOW, NOW,
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

        private final JsonNode schema = McpSchemaGenerator.schemaOf(HubAnswers.Sessions.class);

        private JsonNode page(HubsMcpTools target, String hub, Integer minutes, int limit, String cursor) {
            var method = assertDoesNotThrow(() -> HubsMcpTools.class.getMethod("sessions",
                    String.class, String.class, String.class, Integer.class, RecordingStatus.class,
                    Integer.class, String.class), "sessions must expose cursor pagination");
            McpToolResult result = (McpToolResult) assertDoesNotThrow(() -> method.invoke(target, hub, null, null,
                    minutes, null, limit, cursor));
            StructuredAnswers.markdown(HubsMcpTools.class, "sessions", result);
            return Json.toTree(result);
        }

        @Test
        void returnsCompleteEmptyCatalogueMetadata() {
            when(hubsManager.findAll()).thenReturn(List.of());
            JsonNode result = page(tools, null, null, 1, null).path("structuredContent");
            McpSchemaConformance.assertConforms(result, schema);
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
            assertEquals("a", HubSessionRef.decode(first.path("sessions").get(0).path("sessionRef").asText()).sessionId());
            assertTrue(first.path("hasMore").asBoolean());
            JsonNode second = page(tools, "prod", null, 2, first.path("nextCursor").asText()).path("structuredContent");
            assertEquals(2, second.path("returned").asInt());
            assertEquals("b", HubSessionRef.decode(second.path("sessions").get(0).path("sessionRef").asText()).sessionId());
            assertFalse(second.path("hasMore").asBoolean());
            assertTrue(second.path("nextCursor").isNull());
        }

        /**
         * The formatted strings are for reading; a caller that sorts, filters or adds up sessions needs
         * the numbers, and should not have to parse "10m0s" or "2.0 KiB" back into them.
         */
        @Test
        void carriesTheNumbersBesideTheFormattedStrings() {
            RepositoryManager repo = repositoryWith(session(SESSION_ID, NOW,
                    file("f-1", "recording.jfr", ManagedFile.JFR), file("f-2", "gc.log", ManagedFile.JFR)));
            HubManager production = reachableHub(HUB_ID, "production", "checkout", repo);
            when(hubsManager.findAll()).thenReturn(List.of(production));
            noLocalRecordings();

            JsonNode row = page(tools, null, null, 1, null).path("structuredContent").path("sessions").get(0);

            assertEquals(2048, row.path("sizeBytes").asLong());
            assertEquals(600_000, row.path("durationMs").asLong());
            assertEquals(NOW.toEpochMilli(), row.path("startedAtEpochMs").asLong());
            assertFalse(row.has("duration"), "the readable span stays in the table; the record has durationMs");
            assertFalse(row.has("size"), "the readable size stays in the table; the record has sizeBytes");
            assertEquals("FINISHED", row.path("status").asString());
            assertFalse(row.has("started"), row.toString());
            assertFalse(row.has("startedAtMs"), row.toString());
        }

        /** A session still recording has no duration yet, and says so with null rather than zero. */
        @Test
        void aRunningSessionHasNoDuration() {
            RecordingSession running = new RecordingSession(SESSION_ID, SESSION_ID, "inst-1", NOW, null,
                    RecordingStatus.ACTIVE, null, List.of(file("f-1", "recording.jfr", ManagedFile.JFR)), false);
            HubManager production = reachableHub(HUB_ID, "production", "checkout", repositoryWith(running));
            when(hubsManager.findAll()).thenReturn(List.of(production));
            noLocalRecordings();

            JsonNode catalogue = page(tools, null, null, 1, null).path("structuredContent");
            JsonNode row = catalogue.path("sessions").get(0);

            McpSchemaConformance.assertConforms(catalogue, schema);
            assertTrue(row.path("durationMs").isNull(), row.toString());
            assertEquals(NOW.toEpochMilli(), row.path("startedAtEpochMs").asLong());
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
            McpSchemaConformance.assertConforms(data, schema);
            assertEquals("UNREACHABLE", data.path("failures").get(0).path("kind").asString());
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
            assertEquals("b", HubSessionRef.decode(next.path("sessions").get(0).path("sessionRef").asText()).sessionId());
            assertFalse(next.path("hasMore").asBoolean());
        }

        @Test
        void cursorKeepsTheOriginalActiveWindowCutoff() {
            RepositoryManager repo = repositoryWith(jfrSession("a", NOW), jfrSession("b", NOW.minusSeconds(1)));
            HubManager hub = reachableHub(HUB_ID, "production", "checkout", repo);
            when(hubsManager.findAll()).thenReturn(List.of(hub));
            noLocalRecordings();
            String cursor = page(tools, null, 60, 1, null).path("structuredContent").path("nextCursor").asText();
            HubsMcpTools later = HubsMcpToolsFixture.of(hubsManager, resolver, recordingsManager,
                    Clock.fixed(NOW.plusSeconds(120), ZoneOffset.UTC)).build();
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
            for (var refused : List.of(malformed, mismatch)) {
                assertTrue(refused.getCause().getMessage().contains("omit cursor to start again"),
                        refused.getCause().getMessage());
            }
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
            McpSchemaConformance.assertConforms(data, schema);
            int returned = data.path("returned").asInt();
            assertTrue(returned > 0 && returned < 500);
            assertEquals(returned, data.path("sessions").size());
            assertTrue(first.path("text").asText().length() <= McpToolOutput.MAX_CHARS);
            assertTrue(Json.toString(data).length() <= McpToolOutput.MAX_CHARS);
            assertEquals(500, data.path("total").asInt());
            assertTrue(data.path("hasMore").asBoolean());
            JsonNode next = page(tools, null, null, 500, data.path("nextCursor").asText()).path("structuredContent");
            assertEquals("session-%04d".formatted(returned),
                    HubSessionRef.decode(next.path("sessions").get(0).path("sessionRef").asText()).sessionId());
        }
    }

    @Nested
    class ListHubs {

        private JsonNode hubs(McpToolResult result) {
            return StructuredAnswers.markdown(HubsMcpTools.class, "list", result);
        }

        @Test
        void namesEveryConnectedHubWithItsAddress() {
            HubManager production = reachableHub(HUB_ID, "production", "checkout", repositoryWith());
            when(hubsManager.findAll()).thenReturn(List.of(production));

            McpToolResult result = tools.list(null, null);
            JsonNode answer = hubs(result);

            JsonNode row = answer.get("hubs").get(0);
            assertEquals("production", row.get("name").asString());
            assertEquals("hub.example.com:443", row.get("address").asString());
            assertEquals("REACHABLE", row.get("status").asString());
            assertEquals("2.1.0", row.get("hubVersion").asString());
            assertEquals("OK", answer.get("status").asString());
            assertTrue(answer.get("uiLink").asString().endsWith("/hubs"), answer.get("uiLink").asString());
            assertEquals(List.of("hubs_sessions"), StructuredAnswers.nextTools(answer));
            assertTrue(result.text().contains("| production |"), result.text());
        }

        /** Paged by an opaque cursor, which the footer's next call carries, not by a skip count. */
        @Test
        void pagesTheHubsWithACursor() {
            HubManager first = reachableHub(HUB_ID, "production", "checkout", repositoryWith());
            HubManager second = reachableHub("hub-2", "staging", "checkout", repositoryWith());
            HubManager third = reachableHub("hub-3", "qa", "checkout", repositoryWith());
            when(hubsManager.findAll()).thenReturn(List.of(first, second, third));

            JsonNode page = hubs(tools.list(1, null));
            assertEquals("production", page.get("hubs").get(0).get("name").asString());
            assertTrue(page.get("hasMore").asBoolean());
            assertEquals(3, page.get("total").asInt());
            String cursor = StructuredAnswers.call(page, "hubs_list").get("cursor").asString();
            assertEquals(page.get("nextCursor").asString(), cursor);

            JsonNode next = hubs(tools.list(1, cursor));
            assertEquals("staging", next.get("hubs").get(0).get("name").asString());
            JsonNode last = hubs(tools.list(1, next.get("nextCursor").asString()));
            assertEquals("qa", last.get("hubs").get(0).get("name").asString());
            assertFalse(last.get("hasMore").asBoolean());
            assertTrue(last.get("nextCursor").isNull());
        }

        @Test
        void refusesACursorOfAnotherList() {
            when(hubsManager.findAll()).thenReturn(List.of());

            assertThrows(IllegalArgumentException.class, () -> tools.list(1, "garbage"));
        }

        @Test
        void marksAHubThatDidNotAnswer() {
            HubManager down = mock(HubManager.class);
            when(down.info()).thenReturn(hubInfo(HUB_ID, "production"));
            when(down.tryInfo()).thenReturn(Optional.empty());
            when(hubsManager.findAll()).thenReturn(List.of(down));

            JsonNode answer = hubs(tools.list(null, null));

            assertEquals("UNREACHABLE", answer.get("hubs").get(0).get("status").asString());
            assertTrue(answer.get("hubs").get(0).get("hubVersion").isNull());
            assertEquals(List.of(), StructuredAnswers.nextTools(answer), "no hub answers, so no session list either");
            assertTrue(StructuredAnswers.guidance(answer).contains("UNREACHABLE"), answer.toString());
        }

        @Test
        void saysSoWhenNoHubIsConnectedAtAll() {
            when(hubsManager.findAll()).thenReturn(List.of());

            McpToolResult result = tools.list(null, null);
            JsonNode answer = hubs(result);

            assertEquals("EMPTY", answer.get("status").asString());
            assertTrue(answer.get("reason").asString().contains("No Jeffrey Hub is connected"), answer.toString());
            assertTrue(result.text().contains("recordings_analyzeFile"), result.text());
        }

        @Test
        void distinguishesAConfiguredHubFromOneAddedByHand() {
            HubManager configured = reachableHub(HUB_ID, "production", "checkout", repositoryWith());
            when(hubsManager.findAll()).thenReturn(List.of(configured));

            assertEquals("CONFIG", hubs(tools.list(null, null)).get("hubs").get(0).get("source").asString());
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

            String result = tools.sessions(null, null, null, null, null, null, null).text();

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

            String result = tools.sessions(null, null, null, null, null, null, null).text();

            assertTrue(result.contains("recording:rec-1"), result);
        }

        @Test
        void showsTheProfileForASessionAlreadyAnalysed() {
            HubManager production = reachableHub(
                    HUB_ID, "production", "checkout", repositoryWith(jfrSession(SESSION_ID, NOW)));
            when(hubsManager.findAll()).thenReturn(List.of(production));
            localRecording("rec-1", "profile-1", REF);

            String result = tools.sessions(null, null, null, null, null, null, null).text();

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

            String result = tools.sessions(null, null, null, null, null, null, null).text();

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

            String result = tools.sessions(null, null, null, null, null, null, null).text();

            assertTrue(result.contains("Not listed"), result);
            assertFalse(result.contains("No recording sessions matched"), result);
        }

        @Test
        void translatesTheLastHourIntoAWindowOnTheHub() {
            RepositoryManager repo = repositoryWith(jfrSession(SESSION_ID, NOW));
            HubManager production = reachableHub(HUB_ID, "production", "checkout", repo);
            when(hubsManager.findAll()).thenReturn(List.of(production));
            noLocalRecordings();

            tools.sessions(null, null, null, 60, null, null, null).text();

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

            tools.sessions(null, null, null, null, RecordingStatus.ACTIVE, null, null).text();

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
                    () -> tools.sessions(null, null, null, 0, null, null, null).text());
        }

        @Test
        void readsEveryProjectRowBeforeApplyingThePageLimit() {
            RepositoryManager repo = repositoryWith(jfrSession(SESSION_ID, NOW));
            HubManager production = reachableHub(HUB_ID, "production", "checkout", repo);
            when(hubsManager.findAll()).thenReturn(List.of(production));
            noLocalRecordings();

            tools.sessions(null, null, null, null, null, 10_000, null).text();

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

            assertTrue(tools.sessions(null, null, null, null, null, null, null).text().contains("check\\|out"));
        }

        @Test
        void saysHowToWidenAWindowThatMatchedNothing() {
            HubManager production = reachableHub(HUB_ID, "production", "checkout", repositoryWith());
            when(hubsManager.findAll()).thenReturn(List.of(production));

            String result = tools.sessions(null, null, null, 60, null, null, null).text();

            assertTrue(result.contains("withinLastMinutes"), result);
            assertTrue(result.contains("60"), result);
        }

        @Test
        void pointsAtTheHubsWhenNothingMatchedAndNoWindowWasGiven() {
            HubManager production = reachableHub(HUB_ID, "production", "checkout", repositoryWith());
            when(hubsManager.findAll()).thenReturn(List.of(production));

            assertTrue(tools.sessions(null, null, null, null, null, null, null).text().contains("hubs_list"));
        }
    }

    @Nested
    class SessionAnswers {

        private JsonNode sessions(McpToolResult result) {
            return StructuredAnswers.markdown(HubsMcpTools.class, "sessions", result);
        }

        /**
         * A session not here yet leads to a look at its files; downloading it is guidance, because
         * which part of a session to bring is the user's choice and a transfer is heavy.
         */
        @Test
        void aSessionNotHereLeadsToItsFilesAndNeverStraightToADownload() {
            HubManager production = reachableHub(
                    HUB_ID, "production", "checkout", repositoryWith(jfrSession(SESSION_ID, NOW)));
            when(hubsManager.findAll()).thenReturn(List.of(production));
            noLocalRecordings();

            JsonNode answer = sessions(tools.sessions(null, null, null, null, null, null, null));

            assertEquals(REF.encode(), StructuredAnswers.call(answer, "hubs_files").get("sessionRef").asString());
            assertFalse(StructuredAnswers.nextTools(answer).contains("hubs_download"), answer.toString());
            assertTrue(StructuredAnswers.guidance(answer).contains("startEpochMs"), answer.toString());
            assertTrue(answer.get("uiLink").asString().endsWith("/hubs"), answer.get("uiLink").asString());
            JsonNode row = answer.get("sessions").get(0);
            assertTrue(row.get("recordingId").isNull());
            assertTrue(row.get("profileId").isNull());
        }

        @Test
        void aSessionDownloadedButNotAnalysedLeadsToItsAnalysis() {
            HubManager production = reachableHub(
                    HUB_ID, "production", "checkout", repositoryWith(jfrSession(SESSION_ID, NOW)));
            when(hubsManager.findAll()).thenReturn(List.of(production));
            localRecording("rec-1", null, REF);

            JsonNode answer = sessions(tools.sessions(null, null, null, null, null, null, null));

            assertEquals("rec-1", answer.get("sessions").get(0).get("recordingId").asString());
            assertEquals("rec-1", StructuredAnswers.call(answer, "recordings_analyzeRecording")
                    .get("recordingId").asString());
        }

        @Test
        void aSessionAlreadyAnalysedLeadsToItsProfile() {
            HubManager production = reachableHub(
                    HUB_ID, "production", "checkout", repositoryWith(jfrSession(SESSION_ID, NOW)));
            when(hubsManager.findAll()).thenReturn(List.of(production));
            localRecording("rec-1", "profile-1", REF);

            JsonNode answer = sessions(tools.sessions(null, null, null, null, null, null, null));

            assertEquals("profile-1", answer.get("sessions").get(0).get("profileId").asString());
            assertEquals("profile-1", StructuredAnswers.call(answer, "profiles_summary").get("profileId").asString());
        }

        /** The next page is named once, by the footer's call, with every filter it repeats. */
        @Test
        void theNextPageIsACallWithTheSameFilters() {
            RepositoryManager repo = repositoryWith(jfrSession("a", NOW), jfrSession("b", NOW.minusSeconds(1)));
            HubManager hub = reachableHub(HUB_ID, "production", "checkout", repo);
            when(hubsManager.findAll()).thenReturn(List.of(hub));
            noLocalRecordings();

            McpToolResult result = tools.sessions("prod", null, null, 60, RecordingStatus.FINISHED, 1, null);
            JsonNode answer = sessions(result);

            JsonNode next = StructuredAnswers.call(answer, "hubs_sessions");
            assertEquals("prod", next.get("hub").asString());
            assertEquals(60, next.get("withinLastMinutes").asInt());
            assertEquals("FINISHED", next.get("status").asString());
            assertEquals(answer.get("nextCursor").asString(), next.get("cursor").asString());
            assertFalse(result.text().contains("nextCursor: `"), "the cursor is not repeated in prose");
        }

        @Test
        void nothingMatchedWithEveryHubAnsweringIsEmpty() {
            HubManager production = reachableHub(HUB_ID, "production", "checkout", repositoryWith());
            when(hubsManager.findAll()).thenReturn(List.of(production));

            JsonNode answer = sessions(tools.sessions(null, null, null, 60, null, null, null));

            assertEquals("EMPTY", answer.get("status").asString());
            assertTrue(answer.get("reason").asString().contains("withinLastMinutes"), answer.toString());
        }

        @Test
        void aHubThatDidNotAnswerIsNeverAnEmptyCatalogue() {
            HubManager down = mock(HubManager.class);
            when(down.info()).thenReturn(hubInfo("cfg-down", "production"));
            when(down.infoOrThrow()).thenThrow(Status.UNAVAILABLE.asRuntimeException());
            when(hubsManager.findAll()).thenReturn(List.of(down));
            noLocalRecordings();

            JsonNode answer = sessions(tools.sessions(null, null, null, null, null, null, null));

            assertEquals("OK", answer.get("status").asString());
            assertTrue(answer.get("reason").isNull());
            assertEquals(List.of("hubs_list"), StructuredAnswers.nextTools(answer));
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
                    PROJECT_ID, "origin", "checkout", "ns", WORKSPACE_ID, NOW, NOW, Map.of(), null));
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
            return HubsMcpToolsFixture.of(hubsManager, resolver, recordingsManager, CLOCK)
                    .withBudgets(Duration.ofSeconds(1), responseBudget, Duration.ofSeconds(5))
                    .build();
        }

        /**
         * A client that declared the tasks extension is handed a long transfer as a task after the
         * task budget. Only the wait on the transfer is shortened: the preflight that reads the
         * session from the hub keeps the whole response budget.
         */
        @Nested
        class TaskCapableClient {

            @Test
            void handsBackARunningTransferAsATaskWhileThePreflightKeepsTheFullBudget() throws Exception {
                McpOperationRegistry operations = new McpOperationRegistry(CLOCK);
                HubsMcpTools standard = HubsMcpToolsFixture.of(hubsManager, resolver, recordingsManager, CLOCK)
                        .withBudgets(Duration.ofSeconds(1), BoundedJobs.WAIT_BUDGET, Duration.ofMinutes(5))
                        .withOperations(operations).withAnswers(new OperationAnswers(SHORT_TASK_WAIT))
                        .build();
                AtomicLong preflightSecondsLeft = new AtomicLong(-1);
                RepositoryManager repo = mock(RepositoryManager.class);
                when(repo.recordingSession(SESSION_ID)).thenAnswer(_ -> {
                    preflightSecondsLeft.set(Context.current().getDeadline().timeRemaining(TimeUnit.SECONDS));
                    return jfrSession(SESSION_ID, NOW);
                });
                CountDownLatch release = new CountDownLatch(1);
                RecordingsDownloadManager downloads = mock(RecordingsDownloadManager.class);
                when(downloads.downloadSession(SESSION_ID)).thenAnswer(_ -> {
                    assertTrue(release.await(60, TimeUnit.SECONDS));
                    return "rec-new";
                });
                ProjectManager project = mock(ProjectManager.class);
                when(project.repositoryManager()).thenReturn(repo);
                when(project.info()).thenReturn(new ProjectInfo(
                        PROJECT_ID, "origin", "checkout", "ns", WORKSPACE_ID, NOW, NOW, Map.of(), null));
                when(project.recordingsDownloadManager()).thenReturn(downloads);
                resolvesTo(project);
                noLocalRecordings();

                McpToolOutcome outcome;
                try {
                    outcome = assertTimeout(Duration.ofSeconds(20),
                            () -> standard.download(REF.encode(), null, null, null, null, TASKS));
                } catch (AssertionError e) {
                    release.countDown();
                    throw e;
                }

                String taskId = assertInstanceOf(McpToolOutcome.Deferred.class, outcome).taskId();
                assertEquals(OperationKind.HUB_DOWNLOAD, operations.status(taskId).kind());
                assertTrue(preflightSecondsLeft.get() > BoundedJobs.TASK_WAIT_BUDGET.toSeconds(),
                        "the preflight ran under " + preflightSecondsLeft.get() + " s, not the full budget");

                release.countDown();
                await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> assertEquals(
                        McpTaskStatus.COMPLETED, operations.task(taskId, kind -> true).status()));
                JsonNode answer = Json.readTree(assertInstanceOf(McpTaskState.Completed.class,
                        operations.task(taskId, kind -> true).state()).result().text());
                assertEquals("rec-new", answer.path("recordingId").asString(), answer.toString());
                assertEquals(taskId, answer.path("operationId").asString(), answer.toString());
            }

            /** A transfer that lands inside the task budget answers the call with the task's own text. */
            @Test
            void answersATransferThatLandsInsideTheTaskBudgetWithTheTasksOwnAnswer() {
                McpOperationRegistry operations = new McpOperationRegistry(CLOCK);
                HubsMcpTools standard = HubsMcpToolsFixture.of(hubsManager, resolver, recordingsManager, CLOCK)
                        .withBudgets(Duration.ofSeconds(1), BoundedJobs.WAIT_BUDGET, Duration.ofMinutes(5))
                        .withOperations(operations).build();
                RecordingsDownloadManager downloads = mock(RecordingsDownloadManager.class);
                when(downloads.downloadSession(SESSION_ID)).thenReturn("rec-new");
                resolvesTo(projectWith(jfrSession(SESSION_ID, NOW), downloads));
                noLocalRecordings();

                McpToolOutcome outcome = standard.download(REF.encode(), null, null, null, null, TASKS);

                String answer = assertInstanceOf(McpToolResult.class, outcome).text();
                String taskId = Json.readTree(answer).path("operationId").asString();
                assertEquals(answer, assertInstanceOf(McpTaskState.Completed.class,
                        operations.task(taskId, kind -> true).state()).result().text());
            }
        }

        @Test
        void downloadsTheSessionTheRefNamesAndReturnsTheRecordingId() {
            RecordingsDownloadManager downloads = mock(RecordingsDownloadManager.class);
            when(downloads.downloadSession(SESSION_ID)).thenReturn("rec-new");
            resolvesTo(projectWith(jfrSession(SESSION_ID, NOW), downloads));
            noLocalRecordings();

            String result = complete(tools.download(REF.encode(), null, null, null, null, RESOURCE_READ));

            assertTrue(result.contains("\"recordingId\":\"rec-new\""), result);
            verify(downloads).downloadSession(SESSION_ID);
        }

        @Test
        void pointsAtRecordingsAnalyzeRecordingAsTheNextStep() {
            RecordingsDownloadManager downloads = mock(RecordingsDownloadManager.class);
            when(downloads.downloadSession(SESSION_ID)).thenReturn("rec-new");
            resolvesTo(projectWith(jfrSession(SESSION_ID, NOW), downloads));
            noLocalRecordings();

            assertTrue(complete(tools.download(REF.encode(), null, null, null, null, RESOURCE_READ))
                    .contains("recordings_analyzeRecording"));
        }

        @Test
        void countsTheRecordingAndArtifactFilesItBrought() {
            RecordingsDownloadManager downloads = mock(RecordingsDownloadManager.class);
            when(downloads.downloadSession(SESSION_ID)).thenReturn("rec-new");
            RecordingSession withHeapDump = session(SESSION_ID, NOW,
                    file("f-1", "recording.jfr", ManagedFile.JFR),
                    file("f-2", "heap.hprof", ManagedFile.HEAP_DUMP));
            resolvesTo(projectWith(withHeapDump, downloads));
            noLocalRecordings();

            String result = complete(tools.download(REF.encode(), null, null, null, null, RESOURCE_READ));

            assertTrue(result.contains("\"recordingFiles\":1"), result);
            assertTrue(result.contains("\"artifactFiles\":1"), result);
        }

        @Test
        void returnsTheExistingRecordingRatherThanFetchingItTwice() {
            localRecording("rec-existing", null, REF);

            String result = complete(tools.download(REF.encode(), null, null, null, null, RESOURCE_READ));

            assertTrue(result.contains("rec-existing"), result);
            verifyNoInteractions(resolver);
        }

        @Test
        void pointsStraightAtAnalysisWhenTheSessionIsAlreadyAProfile() {
            localRecording("rec-existing", "profile-1", REF);

            String result = complete(tools.download(REF.encode(), null, null, null, null, RESOURCE_READ));

            assertTrue(result.contains("profile-1"), result);
            verifyNoInteractions(resolver);
        }

        @Test
        void rejectsAMalformedRefWithoutOpeningAHubConnection() {
            assertThrows(IllegalArgumentException.class,
                    () -> complete(tools.download("not-a-ref", null, null, null, null, RESOURCE_READ)));

            verifyNoInteractions(resolver);
            verifyNoInteractions(recordingsManager);
        }

        @Test
        void explainsARefWhoseHubIsGone() {
            noLocalRecordings();
            when(resolver.resolveHub(HUB_ID)).thenThrow(Exceptions.invalidRequest("Hub not found"));

            IllegalArgumentException e = assertThrows(
                    IllegalArgumentException.class,
                            () -> complete(tools.download(REF.encode(), null, null, null, null, RESOURCE_READ)));

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
                    IllegalArgumentException.class,
                            () -> complete(tools.download(REF.encode(), null, null, null, null, RESOURCE_READ)));

            assertTrue(e.getMessage().contains("retention"), e.getMessage());
        }

        @Test
        void refusesASessionWithNothingFinishedToDownload() {
            RecordingsDownloadManager downloads = mock(RecordingsDownloadManager.class);
            RecordingSession logsOnly = session(SESSION_ID, NOW,
                    file("f-1", "gc.log", ManagedFile.JVM_LOG));
            resolvesTo(projectWith(logsOnly, downloads));
            noLocalRecordings();

            IllegalArgumentException e = assertThrows(
                    IllegalArgumentException.class,
                            () -> complete(tools.download(REF.encode(), null, null, null, null, RESOURCE_READ)));

            assertTrue(e.getMessage().contains("no finished recording file"), e.getMessage());
            verify(downloads, never()).downloadSession(any());
        }

        /**
         * A client that renders forms is asked which part of a large session to bring, before anything
         * crosses the network. Only a whole-session call on a large session is asked, and only once.
         */
        @Nested
        class WindowQuestion {

            private static final String KEY = "downloadWindow";
            private static final Instant START = NOW.minus(Duration.ofHours(3));

            /** Three hours in four 45-minute chunks: over the one-hour threshold, small in bytes. */
            private RecordingSession threeHours() {
                List<RepositoryFile> chunks = IntStream.range(0, 4)
                        .mapToObj(i -> new RepositoryFile("c" + i, "profile-" + i + ".jfr",
                                START.plus(Duration.ofMinutes(45L * i)), 100L, true, null))
                        .toList();
                return new RecordingSession(SESSION_ID, "checkout-api", "inst-1", START, NOW,
                        RecordingStatus.FINISHED, null, chunks, false);
            }

            private RecordingsDownloadManager servingThreeHours() {
                RecordingsDownloadManager downloads = mock(RecordingsDownloadManager.class);
                when(downloads.downloadSession(SESSION_ID)).thenReturn("rec-whole");
                when(downloads.downloadWindow(eq(SESSION_ID), any())).thenReturn("rec-window");
                resolvesTo(projectWith(threeHours(), downloads));
                noLocalRecordings();
                return downloads;
            }

            private McpToolOutcome wholeSession(McpCallContext call) {
                return tools.download(REF.encode(), null, null, null, null, call);
            }

            private McpToolOutcome answered(McpInputResponse.Action action, String contentJson) {
                return wholeSession(answering(ELICITING, KEY, action, contentJson));
            }

            private String message(McpToolOutcome outcome) {
                McpToolOutcome.InputRequired asked = assertInstanceOf(McpToolOutcome.InputRequired.class, outcome);
                assertEquals(Set.of(KEY), asked.requests().keySet());
                return asked.requests().get(KEY).toJson().path("params").path("message").asString();
            }

            @Test
            void asksAClientThatCanAnswerBeforeTransferringALargeSession() {
                RecordingsDownloadManager downloads = servingThreeHours();

                String message = message(wholeSession(ELICITING));

                assertTrue(message.contains("checkout-api"), message);
                assertTrue(message.contains("production"), message);
                assertTrue(message.contains("checkout"), message);
                verifyNoInteractions(downloads);
            }

            @Test
            void doesNotAskAClientThatCannotAnswer() {
                RecordingsDownloadManager downloads = servingThreeHours();

                McpToolOutcome outcome = wholeSession(McpCallContext.RESOURCE_READ);

                assertTrue(assertInstanceOf(McpToolResult.class, outcome).text().contains("rec-whole"));
                verify(downloads).downloadSession(SESSION_ID);
            }

            @Test
            void doesNotAskAboutASessionBelowBothThresholds() {
                RecordingsDownloadManager downloads = mock(RecordingsDownloadManager.class);
                when(downloads.downloadSession(SESSION_ID)).thenReturn("rec-small");
                resolvesTo(projectWith(jfrSession(SESSION_ID, NOW), downloads));
                noLocalRecordings();

                McpToolOutcome outcome = wholeSession(ELICITING);

                assertTrue(assertInstanceOf(McpToolResult.class, outcome).text().contains("rec-small"));
                verify(downloads).downloadSession(SESSION_ID);
            }

            @Test
            void asksAboutASessionOverTheSizeThresholdAlone() {
                RecordingsDownloadManager downloads = mock(RecordingsDownloadManager.class);
                McpOperationRegistry operations = new McpOperationRegistry(CLOCK);
                HubsMcpTools strict = new HubsMcpTools(hubsManager, resolver, recordingsManager, CLOCK,
                        Duration.ofSeconds(1), BoundedJobs.WAIT_BUDGET, Duration.ofMinutes(5), operations,
                        ToolFixtures.answers(), new DownloadWindowQuestion(Duration.ofHours(1), 1000),
                        AdvertisedFamiliesFixture.EVERY_FAMILY);
                resolvesTo(projectWith(jfrSession(SESSION_ID, NOW), downloads));
                noLocalRecordings();

                message(strict.download(REF.encode(), null, null, null, null, ELICITING));

                verifyNoInteractions(downloads);
            }

            @Test
            void doesNotAskWhenTheCallNamesAWindow() {
                RecordingsDownloadManager downloads = servingThreeHours();

                McpToolOutcome outcome = tools.download(REF.encode(), null,
                        NOW.minusSeconds(600).toEpochMilli(), null, null, ELICITING);

                assertTrue(assertInstanceOf(McpToolResult.class, outcome).text().contains("rec-window"));
            }

            @Test
            void doesNotAskWhenTheCallNamesFiles() {
                RecordingsDownloadManager downloads = servingThreeHours();
                when(downloads.downloadRecordings(eq(SESSION_ID), any())).thenReturn("rec-files");

                McpToolOutcome outcome = tools.download(REF.encode(), null, null, null, List.of("c3"), ELICITING);

                assertTrue(assertInstanceOf(McpToolResult.class, outcome).text().contains("rec-files"));
            }

            @Test
            void doesNotAskAboutASessionAlreadyHere() {
                localRecording("rec-existing", null, REF);

                McpToolOutcome outcome = wholeSession(ELICITING);

                assertTrue(assertInstanceOf(McpToolResult.class, outcome).text().contains("rec-existing"));
                verifyNoInteractions(resolver);
            }

            /** A transfer already running for the whole session is joined, not questioned. */
            @Test
            void doesNotAskWhileAnAttemptIsInFlight() throws Exception {
                CountDownLatch started = new CountDownLatch(1);
                CountDownLatch release = new CountDownLatch(1);
                RecordingsDownloadManager downloads = blockingDownload("rec-whole", started, release);
                resolvesTo(projectWith(threeHours(), downloads));
                noLocalRecordings();
                HubsMcpTools impatient = toolsWithBudget(Duration.ofMillis(100));
                try {
                    complete(impatient.download(REF.encode(), null, null, null, null, RESOURCE_READ));
                    assertTrue(started.await(5, TimeUnit.SECONDS));

                    McpToolOutcome outcome = impatient.download(REF.encode(), null, null, null, null, ELICITING);

                    assertInstanceOf(McpToolResult.class, outcome);
                } finally {
                    release.countDown();
                }
                verify(downloads, times(1)).downloadSession(SESSION_ID);
            }

            @Test
            void theLastHourBringsTheChunksCoveringTheSessionsFinalHour() {
                RecordingsDownloadManager downloads = servingThreeHours();

                McpToolOutcome outcome = answered(McpInputResponse.Action.ACCEPT, "{\"window\":\"lastHour\"}");

                JsonNode answer = StructuredAnswers.json(HubsMcpTools.class, "download", outcome);
                assertEquals("rec-window", answer.path("recordingId").asString(), answer.toString());
                assertEquals("DOWNLOADED", answer.path("status").asString(), answer.toString());
                assertEquals(START.plus(Duration.ofMinutes(90)).toEpochMilli(), answer.path("coveredStartEpochMs").asLong());
                ArgumentCaptor<ChunkWindow> window = ArgumentCaptor.forClass(ChunkWindow.class);
                verify(downloads).downloadWindow(eq(SESSION_ID), window.capture());
                assertEquals(NOW.minus(Duration.ofHours(1)), window.getValue().start());
                assertEquals(NOW, window.getValue().end());
                verify(downloads, never()).downloadSession(any());
                // The part asked for is echoed as the arguments that name it again.
                assertEquals(NOW.minus(Duration.ofHours(1)).toEpochMilli(), answer.path("startEpochMs").asLong());
                assertEquals(NOW.toEpochMilli(), answer.path("endEpochMs").asLong());
                assertEquals("rec-window", StructuredAnswers.call(answer, "recordings_analyzeRecording")
                        .path("recordingId").asString());
                assertTrue(StructuredAnswers.guidance(answer).contains("recordings_delete"), answer.toString());
            }

            /**
             * The still-running answer to a chosen window names that window, and following it to the
             * letter joins the same transfer: no second question, no second download.
             */
            @Test
            void followingTheStillRunningAnswerToAChosenWindowJoinsItsTransfer() {
                CountDownLatch release = new CountDownLatch(1);
                RecordingsDownloadManager downloads = mock(RecordingsDownloadManager.class);
                when(downloads.downloadWindow(eq(SESSION_ID), any())).thenAnswer(_ -> {
                    assertTrue(release.await(10, TimeUnit.SECONDS));
                    return "rec-window";
                });
                resolvesTo(projectWith(threeHours(), downloads));
                noLocalRecordings();
                HubsMcpTools impatient = toolsWithBudget(Duration.ofMillis(200));
                JsonNode first;
                JsonNode followed;
                try {
                    message(impatient.download(REF.encode(), null, null, null, null, ELICITING));
                    first = StructuredAnswers.json(HubsMcpTools.class, "download", impatient.download(
                            REF.encode(), null, null, null, null, answering(ELICITING, KEY,
                                    McpInputResponse.Action.ACCEPT, "{\"window\":\"lastHour\"}")));
                    assertEquals("RUNNING", first.path("status").asString(), first.toString());
                    assertEquals(List.of("operations_status", "hubs_download"), StructuredAnswers.nextTools(first));
                    JsonNode join = StructuredAnswers.call(first, "hubs_download");
                    assertEquals(NOW.minus(Duration.ofHours(1)).toEpochMilli(), join.path("startEpochMs").asLong(), join.toString());
                    assertEquals(NOW.toEpochMilli(), join.path("endEpochMs").asLong(), join.toString());
                    assertFalse(join.has("retry"), "joining is not a retry");

                    McpToolOutcome followUp = impatient.download(REF.encode(), null,
                            join.path("startEpochMs").asLong(), join.path("endEpochMs").asLong(), null, ELICITING);

                    followed = Json.readTree(assertInstanceOf(McpToolResult.class, followUp).text());
                } finally {
                    release.countDown();
                }
                assertFalse(first.path("operationId").asString().isBlank(), first.toString());
                assertEquals(first.path("operationId").asString(), followed.path("operationId").asString());
                verify(downloads, times(1)).downloadWindow(eq(SESSION_ID), any());
            }

            /** A window no finished chunk covers is asked about again, not turned into an error. */
            @Test
            void aChosenWindowNoFinishedChunkCoversIsAskedAgain() {
                RecordingsDownloadManager downloads = mock(RecordingsDownloadManager.class);
                RecordingSession lateFirstChunk = new RecordingSession(SESSION_ID, "checkout-api", "inst-1", START, NOW,
                        RecordingStatus.FINISHED, null, List.of(new RepositoryFile("c0", "profile-0.jfr",
                        START.plus(Duration.ofHours(2)), 100L, true, null)), false);
                resolvesTo(projectWith(lateFirstChunk, downloads));
                noLocalRecordings();

                String message = message(answered(McpInputResponse.Action.ACCEPT, "{\"window\":\"custom\",\"start\":\""
                        + START + "\",\"end\":\"" + START.plus(Duration.ofMinutes(30)) + "\"}"));

                assertTrue(message.startsWith("No finished chunk"), message);
                verifyNoInteractions(downloads);
            }

            /**
             * A live session: three finished chunks and the newest, opened 45 minutes ago, still being
             * written. A window inside that open chunk is covered by no finished one.
             */
            private RecordingSession liveThreeHours() {
                RecordingSession finished = threeHours();
                return new RecordingSession(finished.id(), finished.name(), finished.instanceId(),
                        finished.createdAt(), null, RecordingStatus.ACTIVE, null, finished.files(), false);
            }

            @Test
            void aChosenWindowInsideTheOpenChunkOfALiveSessionIsAskedAgain() {
                RecordingsDownloadManager downloads = mock(RecordingsDownloadManager.class);
                resolvesTo(projectWith(liveThreeHours(), downloads));
                noLocalRecordings();

                String message = message(answered(McpInputResponse.Action.ACCEPT,
                        "{\"window\":\"lastMinutes\",\"minutes\":10}"));

                assertTrue(message.startsWith("No finished chunk"), message);
                verifyNoInteractions(downloads);
            }

            /** The same window named explicitly gets the refusal an uncovered window always got. */
            @Test
            void anExplicitWindowInsideTheOpenChunkOfALiveSessionIsRefused() {
                RecordingsDownloadManager downloads = mock(RecordingsDownloadManager.class);
                resolvesTo(projectWith(liveThreeHours(), downloads));
                noLocalRecordings();

                IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> tools.download(
                        REF.encode(), null, NOW.minusSeconds(600).toEpochMilli(), NOW.toEpochMilli(), null, ELICITING));

                assertTrue(e.getMessage().startsWith("No finished chunk of session " + SESSION_ID), e.getMessage());
                verifyNoInteractions(downloads);
            }

            /** A client that never declared elicitation cannot steer the download with a forged answer. */
            @Test
            void aForgedAnswerFromAClientThatCannotBeAskedIsIgnored() {
                RecordingsDownloadManager downloads = servingThreeHours();

                McpToolOutcome outcome = wholeSession(answering(McpCallContext.RESOURCE_READ, KEY,
                        McpInputResponse.Action.ACCEPT, "{\"window\":\"lastHour\"}"));

                assertTrue(assertInstanceOf(McpToolResult.class, outcome).text().contains("rec-whole"));
                verify(downloads).downloadSession(SESSION_ID);
                verify(downloads, never()).downloadWindow(any(), any());
            }

            @Test
            void theWholeSessionIsBroughtWhole() {
                RecordingsDownloadManager downloads = servingThreeHours();

                McpToolOutcome outcome = answered(McpInputResponse.Action.ACCEPT, "{\"window\":\"whole\"}");

                assertTrue(assertInstanceOf(McpToolResult.class, outcome).text().contains("rec-whole"));
                verify(downloads).downloadSession(SESSION_ID);
                verify(downloads, never()).downloadWindow(any(), any());
            }

            @Test
            void aDeclineTransfersNothingAndSaysSo() {
                RecordingsDownloadManager downloads = servingThreeHours();

                McpToolOutcome outcome = answered(McpInputResponse.Action.DECLINE, null);

                JsonNode answer = StructuredAnswers.json(HubsMcpTools.class, "download", outcome);
                assertEquals("NOT_DOWNLOADED", answer.path("status").asString(), answer.toString());
                assertFalse(answer.path("reason").asString().isBlank(), answer.toString());
                assertTrue(StructuredAnswers.guidance(answer).contains("startEpochMs"), answer.toString());
                assertEquals(REF.encode(), StructuredAnswers.call(answer, "hubs_files").path("sessionRef").asString());
                assertTrue(answer.path("recordingId").isNull());
                assertTrue(answer.path("uiLink").asString().endsWith("/hubs"), answer.toString());
                verifyNoInteractions(downloads);
            }

            @Test
            void anIncompleteAnswerIsAskedAgainNamingTheProblem() {
                RecordingsDownloadManager downloads = servingThreeHours();

                String message = message(answered(McpInputResponse.Action.ACCEPT, "{\"window\":\"lastMinutes\"}"));

                assertTrue(message.contains("minutes"), message);
                assertTrue(message.contains("checkout-api"), message);
                verifyNoInteractions(downloads);
            }

            /** Asked first, then on the retry the transfer runs as it would have: here, as a task. */
            @Test
            void aClientWithTasksIsAskedAndThenHandedATask() throws Exception {
                McpOperationRegistry operations = new McpOperationRegistry(CLOCK);
                HubsMcpTools standard = HubsMcpToolsFixture.of(hubsManager, resolver, recordingsManager, CLOCK)
                        .withBudgets(Duration.ofSeconds(1), BoundedJobs.WAIT_BUDGET, Duration.ofMinutes(5))
                        .withOperations(operations).withAnswers(new OperationAnswers(SHORT_TASK_WAIT))
                        .build();
                CountDownLatch release = new CountDownLatch(1);
                RecordingsDownloadManager downloads = mock(RecordingsDownloadManager.class);
                when(downloads.downloadWindow(eq(SESSION_ID), any())).thenAnswer(_ -> {
                    assertTrue(release.await(60, TimeUnit.SECONDS));
                    return "rec-window";
                });
                resolvesTo(projectWith(threeHours(), downloads));
                noLocalRecordings();

                message(standard.download(REF.encode(), null, null, null, null, ELICITING_TASKS));
                McpToolOutcome outcome;
                try {
                    outcome = assertTimeout(Duration.ofSeconds(20), () -> standard.download(
                            REF.encode(), null, null, null, null, answering(ELICITING_TASKS, KEY,
                                    McpInputResponse.Action.ACCEPT, "{\"window\":\"lastHour\"}")));
                } finally {
                    release.countDown();
                }

                String taskId = assertInstanceOf(McpToolOutcome.Deferred.class, outcome).taskId();
                await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> assertEquals(
                        McpTaskStatus.COMPLETED, operations.task(taskId, kind -> true).status()));
                JsonNode answer = Json.readTree(assertInstanceOf(McpTaskState.Completed.class,
                        operations.task(taskId, kind -> true).state()).result().text());
                assertEquals("rec-window", answer.path("recordingId").asString(), answer.toString());
            }
        }

        private RecordingSession fourChunks() {
            return session(SESSION_ID, NOW,
                    new RepositoryFile("c0", "profile-0.jfr", NOW, 100L, true, null),
                    new RepositoryFile("c1", "profile-1.jfr", NOW.plusSeconds(150), 100L, true, null),
                    new RepositoryFile("c2", "profile-2.jfr", NOW.plusSeconds(300), 100L, true, null),
                    new RepositoryFile("c3", "profile-3.jfr", NOW.plusSeconds(450), 100L, true, null),
                    file("log", "app.log", ManagedFile.APP_LOG));
        }

        /** The same four chunks, but the session has not finished: its last chunk is open-ended. */
        private RecordingSession activeFourChunks() {
            RecordingSession finished = fourChunks();
            return new RecordingSession(finished.id(), finished.name(), finished.instanceId(),
                    finished.createdAt(), null, RecordingStatus.ACTIVE, finished.absolutePath(),
                    finished.files(), finished.retained());
        }

        @Test
        void aWindowBringsTheCoveringChunksAndReportsTheirSpan() {
            RecordingsDownloadManager downloads = mock(RecordingsDownloadManager.class);
            when(downloads.downloadWindow(eq(SESSION_ID), any())).thenReturn("rec-window");
            resolvesTo(projectWith(fourChunks(), downloads));
            noLocalRecordings();

            String result = complete(tools.download(REF.encode(), null,
                    NOW.plusSeconds(200).toEpochMilli(), NOW.plusSeconds(320).toEpochMilli(), null, RESOURCE_READ));

            assertTrue(result.contains("\"recordingId\":\"rec-window\""), result);
            assertTrue(result.contains("\"recordingFiles\":2"), result);
            assertTrue(result.contains("\"coveredStartEpochMs\":" + NOW.plusSeconds(150).toEpochMilli()), result);
            assertTrue(result.contains("\"coveredEndEpochMs\":" + NOW.plusSeconds(450).toEpochMilli()), result);
            assertTrue(result.contains("recordings_delete"), result);
            ArgumentCaptor<ChunkWindow> window = ArgumentCaptor.forClass(ChunkWindow.class);
            verify(downloads).downloadWindow(eq(SESSION_ID), window.capture());
            assertEquals(NOW.plusSeconds(200), window.getValue().start());
            assertEquals(NOW.plusSeconds(320), window.getValue().end());
            verify(downloads, never()).downloadSession(any());
        }

        @Test
        void aWindowIsNotAnsweredFromTheWholeSessionAlreadyHere() {
            RecordingsDownloadManager downloads = mock(RecordingsDownloadManager.class);
            when(downloads.downloadWindow(eq(SESSION_ID), any())).thenReturn("rec-window");
            resolvesTo(projectWith(fourChunks(), downloads));
            localRecording("rec-existing", "profile-existing", REF);

            String result =
                    complete(tools.download(REF.encode(), null, NOW.plusSeconds(200).toEpochMilli(), null, null, RESOURCE_READ));

            assertTrue(result.contains("rec-window"), result);
            assertFalse(result.contains("rec-existing"), result);
        }

        /**
         * "Everything since 14:00" on a session that is still recording means something later
         * every time it is asked. Handing back the recording made an hour ago would answer a
         * shorter question than the one that was put.
         */
        @Test
        void anOpenEndedWindowOnALiveSessionIsFetchedAgainRatherThanReplayed() {
            RecordingsDownloadManager downloads = mock(RecordingsDownloadManager.class);
            when(downloads.downloadWindow(eq(SESSION_ID), any()))
                    .thenReturn("rec-first", "rec-second");
            resolvesTo(projectWith(activeFourChunks(), downloads));
            noLocalRecordings();
            when(recordingsManager.findRecording(any())).thenReturn(Optional.of(mock(Recording.class)));

            long start = NOW.plusSeconds(200).toEpochMilli();
            assertTrue(complete(tools.download(REF.encode(), null, start, null, null, RESOURCE_READ))
                    .contains("rec-first"));
            String second = complete(tools.download(REF.encode(), null, start, null, null, RESOURCE_READ));

            assertTrue(second.contains("rec-second"), second);
            verify(downloads, times(2)).downloadWindow(eq(SESSION_ID), any());
        }

        /** A closed window is settled, so asking twice costs one transfer. */
        @Test
        void aClosedWindowIsAnsweredFromTheRecordingItAlreadyMade() {
            RecordingsDownloadManager downloads = mock(RecordingsDownloadManager.class);
            when(downloads.downloadWindow(eq(SESSION_ID), any())).thenReturn("rec-window");
            resolvesTo(projectWith(fourChunks(), downloads));
            noLocalRecordings();
            when(recordingsManager.findRecording(any())).thenReturn(Optional.of(mock(Recording.class)));

            long start = NOW.plusSeconds(200).toEpochMilli();
            long end = NOW.plusSeconds(320).toEpochMilli();
            complete(tools.download(REF.encode(), null, start, end, null, RESOURCE_READ));
            complete(tools.download(REF.encode(), null, start, end, null, RESOURCE_READ));

            verify(downloads, times(1)).downloadWindow(eq(SESSION_ID), any());
        }

        @Test
        void aWindowOutsideTheSessionIsRefusedWithTheSessionsSpan() {
            RecordingsDownloadManager downloads = mock(RecordingsDownloadManager.class);
            resolvesTo(projectWith(fourChunks(), downloads));
            noLocalRecordings();

            IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> complete(tools.download(
                    REF.encode(), null, NOW.plusSeconds(3600).toEpochMilli(), NOW.plusSeconds(7200).toEpochMilli(), null, RESOURCE_READ)));

            assertTrue(e.getMessage().contains("started at " + NOW), e.getMessage());
            assertTrue(e.getMessage().contains("finished at " + NOW.plusSeconds(600)), e.getMessage());
            verifyNoInteractions(downloads);
        }

        @Test
        void aWindowWithItsEndBeforeItsStartIsRefusedBeforeTheHubIsAsked() {
            assertThrows(IllegalArgumentException.class, () -> complete(tools.download(
                    REF.encode(), null, NOW.plusSeconds(300).toEpochMilli(), NOW.plusSeconds(200).toEpochMilli(), null, RESOURCE_READ)));
            verifyNoInteractions(resolver);
        }

        @Test
        void namedFilesBringThoseFilesAndCountTheOnesBesideTheChunks() {
            RecordingsDownloadManager downloads = mock(RecordingsDownloadManager.class);
            when(downloads.downloadRecordings(eq(SESSION_ID), any())).thenReturn("rec-files");
            resolvesTo(projectWith(fourChunks(), downloads));
            noLocalRecordings();

            String result =
                    complete(tools.download(REF.encode(), null, null, null, List.of("c2", "log"), RESOURCE_READ));

            assertTrue(result.contains("\"recordingId\":\"rec-files\""), result);
            assertTrue(result.contains("\"recordingFiles\":1"), result);
            assertTrue(result.contains("\"artifactFiles\":1"), result);
            assertTrue(result.contains("\"coveredStartEpochMs\":" + NOW.plusSeconds(300).toEpochMilli()), result);
            verify(downloads).downloadRecordings(SESSION_ID, List.of("c2", "log"));
        }

        @Test
        void anUnknownFileIdIsRefusedBeforeTheTransfer() {
            RecordingsDownloadManager downloads = mock(RecordingsDownloadManager.class);
            resolvesTo(projectWith(fourChunks(), downloads));
            noLocalRecordings();

            IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                    () -> complete(tools.download(REF.encode(), null, null, null, List.of("c2", "nope"), RESOURCE_READ)));

            assertTrue(e.getMessage().contains("[nope]"), e.getMessage());
            verifyNoInteractions(downloads);
        }

        /**
         * The chunks are merged into one recording, so a skipped one would be invisible in the
         * result: the profile would claim c1's start to c3's end while holding two thirds of it.
         */
        @Test
        void namedChunksWithOneSkippedAreRefused() {
            RecordingsDownloadManager downloads = mock(RecordingsDownloadManager.class);
            resolvesTo(projectWith(fourChunks(), downloads));
            noLocalRecordings();

            IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                    () -> complete(tools.download(REF.encode(), null, null, null, List.of("c1", "c3"), RESOURCE_READ)));

            assertTrue(e.getMessage().contains("profile-2.jfr"), e.getMessage());
            assertTrue(e.getMessage().contains("startEpochMs"), e.getMessage());
            verifyNoInteractions(downloads);
        }

        @Test
        void namedChunksNextToEachOtherAreAccepted() {
            RecordingsDownloadManager downloads = mock(RecordingsDownloadManager.class);
            when(downloads.downloadRecordings(eq(SESSION_ID), any())).thenReturn("rec-run");
            resolvesTo(projectWith(fourChunks(), downloads));
            noLocalRecordings();

            String result =
                    complete(tools.download(REF.encode(), null, null, null, List.of("c1", "c2", "log"), RESOURCE_READ));

            assertTrue(result.contains("\"recordingFiles\":2"), result);
            verify(downloads).downloadRecordings(SESSION_ID, List.of("c1", "c2", "log"));
        }

        @Test
        void namedFilesWithoutAChunkAreRefused() {
            RecordingsDownloadManager downloads = mock(RecordingsDownloadManager.class);
            resolvesTo(projectWith(fourChunks(), downloads));
            noLocalRecordings();

            IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                    () -> complete(tools.download(REF.encode(), null, null, null, List.of("log"), RESOURCE_READ)));

            assertTrue(e.getMessage().contains("hubs_fetchFile"), e.getMessage());
            verifyNoInteractions(downloads);
        }

        /**
         * The ids are a list, so the schema says so: a model no longer has to learn that one string
         * carries several of them. A comma-joined string is still read, for a client that learned it.
         */
        @Test
        void fileIdsAreAdvertisedAsAnArrayAndBoundFromOne() {
            RecordingsDownloadManager downloads = mock(RecordingsDownloadManager.class);
            when(downloads.downloadRecordings(eq(SESSION_ID), any())).thenReturn("rec-array");
            resolvesTo(projectWith(fourChunks(), downloads));
            noLocalRecordings();
            ReflectiveToolset toolset = new ReflectiveToolset(tools, "hubs");
            JsonNode fileIds = toolset.specs().stream()
                    .filter(spec -> spec.name().equals("hubs_download"))
                    .findFirst()
                    .orElseThrow()
                    .inputSchema().path("properties").path("fileIds");

            assertEquals("array", fileIds.path("type").asString());
            assertEquals("string", fileIds.path("items").path("type").asString());

            ObjectNode arguments = Json.createObject().put("sessionRef", REF.encode());
            arguments.putArray("fileIds").add("c2").add("log");
            String result = toolset.call("hubs_download", arguments);

            assertTrue(result.contains("\"recordingId\":\"rec-array\""), result);
            verify(downloads).downloadRecordings(SESSION_ID, List.of("c2", "log"));
        }

        @Test
        void aWindowAndFileIdsTogetherAreRefused() {
            assertThrows(IllegalArgumentException.class, () -> complete(tools.download(
                    REF.encode(), null, NOW.toEpochMilli(), null, List.of("c1"), RESOURCE_READ)));
            verifyNoInteractions(resolver);
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
                Future<String> firstCall = callers.submit(
                        () -> complete(shortBudgetTools.download(REF.encode(), null, null, null, null, RESOURCE_READ)));
                Future<String> secondCall = callers.submit(
                        () -> complete(shortBudgetTools.download(otherRef.encode(), null, null, null, null, RESOURCE_READ)));

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
            when(downloads.downloadSession(SESSION_ID)).thenAnswer(_ -> {
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
                Future<String> firstCall = callers.submit(
                        () -> complete(shortBudgetTools.download(REF.encode(), null, null, null, null, RESOURCE_READ)));
                Future<String> secondCall = callers.submit(
                        () -> complete(shortBudgetTools.download(REF.encode(), null, null, null, null, RESOURCE_READ)));

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
            when(downloads.downloadSession(SESSION_ID)).thenAnswer(_ -> {
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
                Future<String> first = callers.submit(
                        () -> complete(tools.download(REF.encode(), null, null, null, null, RESOURCE_READ)));
                assertTrue(transferStarted.await(2, TimeUnit.SECONDS));
                Future<String> second = callers.submit(
                        () -> complete(tools.download(REF.encode(), null, null, null, null, RESOURCE_READ)));
                assertTrue(first.get(3, TimeUnit.SECONDS).contains("rec-new"));
                releasePreflight.countDown();
                assertTrue(second.get(3, TimeUnit.SECONDS).contains("rec-new"));
                assertEquals(1, transfers.get());
            } finally {
                releasePreflight.countDown();
            }
        }

        /**
         * What hubs_download answers once the attempt under its key has failed, until retry=true: the
         * failed attempt's status, not the failure again and not a new transfer.
         */
        private static void assertReportsARetainedFailure(String answer) {
            assertEquals("FAILED", Json.mapper().readTree(answer).path("status").asString(), answer);
        }

        @Test
        void retryPreflightFailureIsNotReplacedByThePreviousAttemptFailure() {
            RecordingsDownloadManager downloads = mock(RecordingsDownloadManager.class);
            when(downloads.downloadSession(SESSION_ID)).thenThrow(new IllegalStateException("old failure"));
            ProjectManager project = projectWith(jfrSession(SESSION_ID, NOW), downloads);
            when(project.repositoryManager().recordingSession(SESSION_ID))
                    .thenReturn(jfrSession(SESSION_ID, NOW))
                    .thenThrow(Status.UNAVAILABLE.withDescription("new preflight failure").asRuntimeException());
            resolvesTo(project);
            noLocalRecordings();
            assertReportsARetainedFailure(complete(tools.download(REF.encode(), null, null, null, null, RESOURCE_READ)));
            JeffreyException failure = assertThrows(JeffreyException.class,
                    () -> complete(tools.download(REF.encode(), true, null, null, null, RESOURCE_READ)));
            assertEquals(ErrorCode.HUB_UNAVAILABLE, failure.getCode());
            assertTrue(failure.getMessage().contains("UNAVAILABLE"), failure.getMessage());
            verify(downloads, times(1)).downloadSession(SESSION_ID);
        }

        @Test
        void mcpFailuresReturnAnOperationIdAndRequireAnExplicitRetry() {
            RecordingsDownloadManager downloads = mock(RecordingsDownloadManager.class);
            when(downloads.downloadSession(SESSION_ID))
                    .thenThrow(new IllegalStateException("connection lost"))
                    .thenReturn("rec-retried");
            resolvesTo(projectWith(jfrSession(SESSION_ID, NOW), downloads));
            noLocalRecordings();
            var first =
                    Json.mapper().readTree(complete(tools.download(REF.encode(), false, null, null, null, RESOURCE_READ)));
            String operationId = first.path("operationId").asString();
            assertFalse(operationId.isBlank());
            assertEquals("FAILED", first.path("status").asString());
            var retained =
                    Json.mapper().readTree(complete(tools.download(REF.encode(), false, null, null, null, RESOURCE_READ)));
            assertEquals(operationId, retained.path("operationId").asString());
            verify(downloads, times(1)).downloadSession(SESSION_ID);
            var retry =
                    Json.mapper().readTree(complete(tools.download(REF.encode(), true, null, null, null, RESOURCE_READ)));
            assertFalse(operationId.equals(retry.path("operationId").asString()));
            assertEquals("COMPLETED", retry.path("operation").path("status").asString());
        }

        /**
         * A failed transfer is reported with the call that starts it again - the same session and the
         * same part, with retry=true - and its operation, as operations_status reports it, names the
         * very same call rather than a sentence.
         */
        @Test
        void aFailedTransferHandsBackTheCallThatRetriesIt() {
            RecordingsDownloadManager downloads = mock(RecordingsDownloadManager.class);
            when(downloads.downloadWindow(eq(SESSION_ID), any())).thenThrow(new IllegalStateException("connection lost"));
            resolvesTo(projectWith(fourChunks(), downloads));
            noLocalRecordings();
            long start = NOW.plusSeconds(200).toEpochMilli();
            long end = NOW.plusSeconds(320).toEpochMilli();

            JsonNode failed = StructuredAnswers.json(HubsMcpTools.class, "download",
                    tools.download(REF.encode(), false, start, end, null, RESOURCE_READ));

            assertEquals("FAILED", failed.path("status").asString(), failed.toString());
            assertTrue(failed.path("reason").asString().contains("connection lost"), failed.toString());
            JsonNode retry = StructuredAnswers.call(failed, "hubs_download");
            assertEquals(REF.encode(), retry.path("sessionRef").asString());
            assertEquals(start, retry.path("startEpochMs").asLong());
            assertEquals(end, retry.path("endEpochMs").asLong());
            assertTrue(retry.path("retry").asBoolean());
            JsonNode operationRetry = failed.path("operation").path("followUp").path("nextTools").get(0);
            assertEquals("hubs_download", operationRetry.path("tool").asString());
            assertEquals(retry, operationRetry.path("arguments"));
            assertTrue(failed.path("uiLink").asString().endsWith("/hubs"), failed.toString());
        }

        /** A whole session already here is a DOWNLOADED answer that counts nothing it did not count. */
        @Test
        void aSessionAlreadyHereClaimsNoCountsItDidNotMake() {
            localRecording("rec-existing", null, REF);

            JsonNode answer = StructuredAnswers.json(HubsMcpTools.class, "download",
                    tools.download(REF.encode(), null, null, null, null, RESOURCE_READ));

            assertEquals("DOWNLOADED", answer.path("status").asString());
            assertTrue(answer.path("recordingFiles").isNull(), answer.toString());
            assertTrue(answer.path("sizeBytes").isNull(), answer.toString());
            assertEquals("rec-existing", StructuredAnswers.call(answer, "recordings_analyzeRecording")
                    .path("recordingId").asString());
            assertTrue(answer.path("uiLink").asString().endsWith("/recordings"), answer.toString());
        }

        @Test
        void aSessionAlreadyAnalysedLinksItsProfile() {
            localRecording("rec-existing", "profile-1", REF);

            JsonNode answer = StructuredAnswers.json(HubsMcpTools.class, "download",
                    tools.download(REF.encode(), null, null, null, null, RESOURCE_READ));

            assertEquals("profile-1", answer.path("profileId").asString());
            assertEquals(List.of("profiles_summary"), StructuredAnswers.nextTools(answer));
            assertTrue(answer.path("uiLink").asString().endsWith("/profiles/profile-1"), answer.toString());
        }

        @Test
        void failedTransferCanBeRetriedWithTheSameFullRef() {
            RecordingsDownloadManager downloads = mock(RecordingsDownloadManager.class);
            when(downloads.downloadSession(SESSION_ID))
                    .thenThrow(new IllegalStateException("connection lost"))
                    .thenReturn("rec-retried");
            resolvesTo(projectWith(jfrSession(SESSION_ID, NOW), downloads));
            noLocalRecordings();

            assertReportsARetainedFailure(complete(tools.download(REF.encode(), null, null, null, null, RESOURCE_READ)));

            assertReportsARetainedFailure(complete(tools.download(REF.encode(), null, null, null, null, RESOURCE_READ)));
            assertTrue(complete(tools.download(REF.encode(), true, null, null, null, RESOURCE_READ))
                    .contains("rec-retried"));
        }

        @Test
        void lateFailureIsRetainedAcrossPollsUntilRetryIsExplicit() throws Exception {
            CountDownLatch transferStarted = new CountDownLatch(1);
            CountDownLatch release = new CountDownLatch(1);
            CountDownLatch failed = new CountDownLatch(1);
            AtomicInteger attempts = new AtomicInteger();
            RecordingsDownloadManager downloads = mock(RecordingsDownloadManager.class);
            when(downloads.downloadSession(SESSION_ID)).thenAnswer(_ -> {
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

            String first = complete(shortBudgetTools.download(REF.encode(), null, null, null, null, RESOURCE_READ));
            assertTrue(first.contains("still running"), first);
            assertTrue(transferStarted.await(1, TimeUnit.SECONDS));
            release.countDown();
            assertTrue(failed.await(1, TimeUnit.SECONDS));

            assertReportsARetainedFailure(complete(shortBudgetTools.download(REF.encode(), null, null, null, null, RESOURCE_READ)));
            assertReportsARetainedFailure(complete(shortBudgetTools.download(REF.encode(), null, null, null, null, RESOURCE_READ)));
            verify(downloads, times(1)).downloadSession(SESSION_ID);

            assertTrue(complete(shortBudgetTools.download(REF.encode(), true, null, null, null, RESOURCE_READ))
                    .contains("rec-retried"));
            verify(downloads, times(2)).downloadSession(SESSION_ID);
        }

        @Test
        void refetchesACompletedSessionWhenItsRetainedRecordingWasDeleted() {
            RecordingsDownloadManager downloads = mock(RecordingsDownloadManager.class);
            when(downloads.downloadSession(SESSION_ID))
                    .thenReturn("rec-deleted")
                    .thenReturn("rec-refetched");
            resolvesTo(projectWith(jfrSession(SESSION_ID, NOW), downloads));
            noLocalRecordings();
            when(recordingsManager.findRecording("rec-deleted")).thenReturn(Optional.empty());

            assertTrue(complete(tools.download(REF.encode(), null, null, null, null, RESOURCE_READ))
                    .contains("rec-deleted"));
            assertTrue(complete(tools.download(REF.encode(), null, null, null, null, RESOURCE_READ))
                    .contains("rec-refetched"));

            verify(downloads, times(2)).downloadSession(SESSION_ID);
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
            when(downloads.downloadSession(SESSION_ID)).thenAnswer(_ -> {
                transferStarted.countDown();
                releaseTransfer.await(2, TimeUnit.SECONDS);
                throw new PublishedFailure(failurePublished);
            });
            ProjectManager project = projectWith(jfrSession(SESSION_ID, NOW), downloads);
            when(project.repositoryManager()).thenReturn(repository);
            resolvesTo(project);
            noLocalRecordings();
            HubsMcpTools shortBudgetTools = toolsWithBudget(Duration.ofMillis(50));

            assertTrue(complete(shortBudgetTools.download(REF.encode(), null, null, null, null, RESOURCE_READ))
                    .contains("still running"));
            assertTrue(transferStarted.await(1, TimeUnit.SECONDS));
            try (ExecutorService callers = Executors.newSingleThreadExecutor()) {
                Future<String> poll = callers.submit(
                        () -> complete(shortBudgetTools.download(REF.encode(), null, null, null, null, RESOURCE_READ)));
                assertTrue(secondPreflight.await(1, TimeUnit.SECONDS));
                releaseTransfer.countDown();
                assertTrue(failurePublished.await(1, TimeUnit.SECONDS));
                releasePreflight.countDown();

                assertReportsARetainedFailure(poll.get(1, TimeUnit.SECONDS));
                verify(downloads, times(1)).downloadSession(SESSION_ID);
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
                    () -> complete(toolsWithBudget(Duration.ofMillis(50)).download(REF.encode(), null, null, null, null, RESOURCE_READ)));

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
                    JeffreyException.class,
                            () -> complete(tools.download(REF.encode(), null, null, null, null, RESOURCE_READ)));

            assertEquals(ErrorCode.HUB_UNAVAILABLE, exception.getCode());
            assertTrue(exception.getMessage().contains("UNAVAILABLE"), exception.getMessage());
        }

        private RecordingsDownloadManager blockingDownload(
                String recordingId,
                CountDownLatch started,
                CountDownLatch release) {

            RecordingsDownloadManager downloads = mock(RecordingsDownloadManager.class);
            when(downloads.downloadSession(SESSION_ID)).thenAnswer(_ -> {
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
