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

import cafe.jeffrey.microscope.connector.api.DiscoveryClient;
import cafe.jeffrey.microscope.connector.api.manager.RepositoryManager;
import cafe.jeffrey.microscope.core.manager.connector.ConnectorConnection;
import cafe.jeffrey.microscope.core.manager.connector.ConnectorManager;
import cafe.jeffrey.microscope.core.manager.project.ProjectManager;
import cafe.jeffrey.microscope.core.manager.project.ProjectsManager;
import cafe.jeffrey.microscope.core.manager.recordings.RecordingsManager;
import cafe.jeffrey.microscope.core.manager.workspace.WorkspaceManager;
import cafe.jeffrey.microscope.core.mcp.MicroscopePage;
import cafe.jeffrey.microscope.core.mcp.UiLinks;
import cafe.jeffrey.microscope.core.mcp.tools.connector.DownloadWindow;
import cafe.jeffrey.microscope.core.mcp.tools.connector.ChunkedSessions;
import cafe.jeffrey.microscope.core.mcp.tools.connector.DownloadWindowQuestion;
import cafe.jeffrey.microscope.core.mcp.tools.connector.ConnectorAnswers;
import cafe.jeffrey.microscope.core.mcp.tools.connector.ConnectorSessionRef;
import cafe.jeffrey.microscope.core.web.ProjectManagerResolver;
import cafe.jeffrey.microscope.mcp.server.McpCallContext;
import cafe.jeffrey.microscope.mcp.server.McpInputResponse;
import cafe.jeffrey.microscope.mcp.server.McpSchemaGenerator;
import cafe.jeffrey.microscope.mcp.server.McpToolOutcome;
import cafe.jeffrey.microscope.mcp.server.McpToolResult;
import cafe.jeffrey.microscope.mcp.server.testing.McpSchemaConformance;
import cafe.jeffrey.microscope.core.persistence.api.RecordingTag;
import cafe.jeffrey.microscope.mcp.server.McpToolOutput;
import cafe.jeffrey.microscope.mcp.server.ReflectiveToolset;
import cafe.jeffrey.microscope.core.manager.download.OriginContext;
import cafe.jeffrey.microscope.core.manager.download.RecordingsDownloadManager;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;
import cafe.jeffrey.microscope.core.profile.model.ProfileInfo;
import cafe.jeffrey.microscope.common.model.ProjectInfo;
import cafe.jeffrey.microscope.common.model.RecordingEventSource;
import cafe.jeffrey.microscope.connector.azure.AzureFilesAddress;
import cafe.jeffrey.microscope.connector.azure.AzureFilesAuth;
import cafe.jeffrey.microscope.connector.azure.AzureFilesProtocol;
import cafe.jeffrey.microscope.connector.azure.AzureFilesAddress;
import cafe.jeffrey.microscope.connector.azure.AzureFilesAuth;
import cafe.jeffrey.microscope.connector.azure.AzureFilesProtocol;
import cafe.jeffrey.microscope.core.manager.connector.ConnectorInfo;
import cafe.jeffrey.microscope.connector.local.LocalDirectoryAddress;
import cafe.jeffrey.microscope.core.manager.download.ChunkWindow;
import cafe.jeffrey.microscope.common.model.repository.RecordingSession;
import cafe.jeffrey.microscope.common.model.repository.RecordingSessionFilter;
import cafe.jeffrey.microscope.common.model.repository.SessionSize;
import cafe.jeffrey.microscope.common.model.repository.RepositoryFile;
import cafe.jeffrey.microscope.common.model.workspace.WorkspaceInfo;
import cafe.jeffrey.microscope.common.model.workspace.WorkspaceStatus;
import cafe.jeffrey.microscope.core.profile.manager.ProfileManager;
import cafe.jeffrey.microscope.core.utils.Json;
import cafe.jeffrey.microscope.common.exception.ErrorCode;
import cafe.jeffrey.microscope.common.exception.Exceptions;
import cafe.jeffrey.microscope.common.exception.JeffreyException;
import cafe.jeffrey.microscope.common.exception.JeffreyInternalException;
import cafe.jeffrey.microscope.common.model.recording.FileCategory;
import cafe.jeffrey.microscope.common.model.recording.ManagedFile;
import cafe.jeffrey.microscope.core.manager.recordings.Recording;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.mockito.ArgumentCaptor;

import java.nio.file.Path;
import java.lang.reflect.InvocationTargetException;
import java.util.stream.IntStream;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
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
import java.util.concurrent.atomic.AtomicBoolean;

import static cafe.jeffrey.microscope.core.mcp.tools.McpCallContexts.ELICITING;
import static cafe.jeffrey.microscope.core.mcp.tools.McpCallContexts.answering;
import static cafe.jeffrey.microscope.core.mcp.tools.McpCallContexts.complete;
import static cafe.jeffrey.microscope.mcp.server.McpCallContext.RESOURCE_READ;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ConnectorMcpToolsTest {

    private static final Instant NOW = Instant.parse("2026-03-01T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private static final String ADDRESS = "azure-files:https://jeffreyprod.file.core.windows.net/recordings";
    private static final String WORKSPACE_ID = "ws-1";
    private static final String PROJECT_ID = "proj-1";
    private static final String SESSION_ID = "session-1";
    private static final ConnectorSessionRef REF =
            new ConnectorSessionRef(WORKSPACE_ID, PROJECT_ID, SESSION_ID);

    private final ConnectorManager connectorManager = mock(ConnectorManager.class);
    private final ProjectManagerResolver resolver = mock(ProjectManagerResolver.class);
    private final RecordingsManager recordingsManager = mock(RecordingsManager.class);

    private final ConnectorMcpTools tools =
            ConnectorMcpToolsFixture.of(connectorManager, resolver, recordingsManager, CLOCK).build();

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

    private static ConnectorInfo connectorInfo(String name) {
        return new ConnectorInfo(name, new AzureFilesAddress("https://jeffreyprod.file.core.windows.net",
                "recordings", "", AzureFilesProtocol.SMB, AzureFilesAuth.SAS, false));
    }

    /** The connector configuration declares: what every listing and download reaches. */
    private void configure(ConnectorConnection connector) {
        when(connectorManager.current()).thenReturn(Optional.of(connector));
        when(resolver.currentConnector()).thenReturn(Optional.of(connector));
    }

    private void nothingConfigured() {
        when(connectorManager.current()).thenReturn(Optional.empty());
        when(resolver.currentConnector()).thenReturn(Optional.empty());
    }

    /** A connector that does not answer its probe. */
    private ConnectorConnection unreachableConnector(String name) {
        ConnectorConnection down = mock(ConnectorConnection.class);
        when(down.info()).thenReturn(connectorInfo(name));
        when(down.infoOrThrow())
                .thenThrow(new JeffreyInternalException(ErrorCode.CONNECTOR_UNAVAILABLE, "connection refused"));
        return down;
    }

    private static RepositoryFile file(String id, String name, ManagedFile type) {
        return new RepositoryFile(id, name, NOW, 1024L, type.fileCategory() == FileCategory.RECORDING, null);
    }

    private static RecordingSession session(String id, Instant createdAt, RepositoryFile... files) {
        return new RecordingSession(id, id, "inst-1", createdAt, createdAt.plusSeconds(600),
                false, null, List.of(files), false);
    }

    private static RecordingSession jfrSession(String id, Instant createdAt) {
        return session(id, createdAt, file("f-1", "recording.jfr", ManagedFile.JFR));
    }

    private RepositoryManager repositoryWith(RecordingSession... sessions) {
        RepositoryManager repo = mock(RepositoryManager.class);
        when(repo.listRecordingSessions(any())).thenReturn(List.of(sessions));
        return repo;
    }

    /**
     * A connector that answers its probe, with one workspace holding one project.
     */
    private ConnectorConnection reachableConnector(String connectorName, String projectName, RepositoryManager repo) {
        ConnectorConnection connector = mock(ConnectorConnection.class);
        when(connector.info()).thenReturn(connectorInfo(connectorName));
        when(connector.infoOrThrow()).thenReturn(new DiscoveryClient.PublicApiInfo("2.1.0"));
        WorkspaceInfo workspace = new WorkspaceInfo(
                WORKSPACE_ID, "ref", "repo", "default", null, null, NOW, WorkspaceStatus.AVAILABLE);
        when(connector.workspaces()).thenReturn(List.of(workspace));
        when(connector.workspacesOrThrow()).thenReturn(List.of(workspace));

        ProjectManager project = mock(ProjectManager.class);
        when(project.info()).thenReturn(new ProjectInfo(
                PROJECT_ID, "origin", projectName, "ns", WORKSPACE_ID, NOW, NOW, Map.of()));
        when(project.repositoryManager()).thenReturn(repo);

        ProjectsManager projects = mock(ProjectsManager.class);
        when(projects.findAll()).thenReturn(List.of(project));
        when(projects.findAllOrThrow()).thenReturn(List.of(project));

        WorkspaceManager workspaceManager = mock(WorkspaceManager.class);
        when(workspaceManager.projectsManager()).thenReturn(projects);
        when(connector.workspace(WORKSPACE_ID)).thenReturn(Optional.of(workspaceManager));
        when(connector.workspace(workspace)).thenReturn(workspaceManager);
        return connector;
    }

    /** The recording a download landed as: a JFR recording outside any bundle. */
    private static Recording landedRecording() {
        Recording recording = mock(Recording.class);
        when(recording.eventSource()).thenReturn(RecordingEventSource.JFR);
        return recording;
    }

    /**
     * Answered from the local store: only which connector is configured was looked up, and nothing of
     * the connector itself was read.
     */
    private void nothingReadFromTheConnector() {
        verify(resolver, never()).resolveConnector();
        verify(resolver, never()).resolve(any(), any());
    }

    private void noLocalRecordings() {
        when(recordingsManager.listRecordings()).thenReturn(List.of());
    }

    /** A whole copy of the session, taken once it had finished. */
    private void localRecording(String recordingId, String profileId, ConnectorSessionRef ref) {
        localRecording(recordingId, profileId, ref, true);
    }

    /** A copy of the session as one download left it: whole once finished, or taken while recording. */
    private void localRecording(String recordingId, String profileId, ConnectorSessionRef ref, boolean complete) {
        // A copy is this connector's only while it is configured at the address the copy's tags name.
        ConnectorConnection connector = mock(ConnectorConnection.class);
        when(connector.info()).thenReturn(connectorInfo("production"));
        when(resolver.currentConnector()).thenReturn(Optional.of(connector));
        Recording recording = new Recording(
                recordingId, recordingId, null, RecordingEventSource.JFR, NOW, NOW, NOW,
                profileId != null, profileId, profileId, List.of());
        when(recordingsManager.listRecordings()).thenReturn(List.of(recording));
        when(recordingsManager.findRecording(recordingId)).thenReturn(Optional.of(recording));
        List<RecordingTag> tags = new ArrayList<>(List.of(
                new RecordingTag(OriginContext.TAG_CONNECTOR_ADDRESS, ADDRESS),
                new RecordingTag("origin.workspaceId", ref.workspaceId()),
                new RecordingTag("origin.projectId", ref.projectId()),
                new RecordingTag("origin.recordingId", ref.sessionId())));
        if (complete) {
            tags.add(new RecordingTag(OriginContext.TAG_COMPLETE, "true"));
        }
        when(recordingsManager.tagsForRecordings(any())).thenReturn(Map.of(recordingId, tags));
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

        private final JsonNode schema = McpSchemaGenerator.schemaOf(ConnectorAnswers.Sessions.class);

        private JsonNode page(ConnectorMcpTools target, String workspace, Integer minutes, int limit, String cursor) {
            var method = assertDoesNotThrow(() -> ConnectorMcpTools.class.getMethod("sessions",
                    String.class, String.class, Integer.class, Integer.class, String.class), "sessions must expose cursor pagination");
            McpToolResult result = (McpToolResult) assertDoesNotThrow(() -> method.invoke(target, workspace, null,
                    minutes, limit, cursor));
            StructuredAnswers.markdown(ConnectorMcpTools.class, "sessions", result);
            return Json.toTree(result);
        }

        @Test
        void returnsCompleteEmptyCatalogueMetadata() {
            nothingConfigured();
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
            when(repo.listRecordingSessions(any())).thenAnswer(call -> {
                RecordingSessionFilter filter = call.getArgument(0);
                return filter.apply(List.of(jfrSession("c", NOW), jfrSession("b", NOW), jfrSession("a", NOW)));
            });
            ConnectorConnection connector = reachableConnector("production", "checkout", repo);
            configure(connector);
            noLocalRecordings();
            JsonNode first = page(tools, " DEFAULT ", null, 1, null).path("structuredContent");
            assertEquals(3, first.path("total").asInt());
            assertEquals(3, first.path("observedTotal").asInt());
            assertEquals("a", ConnectorSessionRef.decode(first.path("sessions").get(0).path("sessionRef").asText()).sessionId());
            assertTrue(first.path("hasMore").asBoolean());
            JsonNode second = page(tools, "default", null, 2, first.path("nextCursor").asText()).path("structuredContent");
            assertEquals(2, second.path("returned").asInt());
            assertEquals("b", ConnectorSessionRef.decode(second.path("sessions").get(0).path("sessionRef").asText()).sessionId());
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
            ConnectorConnection production = reachableConnector("production", "checkout", repo);
            configure(production);
            noLocalRecordings();

            JsonNode row = page(tools, null, null, 1, null).path("structuredContent").path("sessions").get(0);

            assertEquals(2048, row.path("sizeBytes").asLong());
            assertEquals(600_000, row.path("durationMs").asLong());
            assertEquals(NOW.toEpochMilli(), row.path("startedAtEpochMs").asLong());
            assertFalse(row.has("duration"), "the readable span stays in the table; the record has durationMs");
            assertFalse(row.has("size"), "the readable size stays in the table; the record has sizeBytes");
            assertFalse(row.path("newest").asBoolean());
            assertFalse(row.has("started"), row.toString());
            assertFalse(row.has("startedAtMs"), row.toString());
        }

        /**
         * A listing brings each session's size but none of its files, so the row's figures come off
         * that size; read off the (empty) file list, they would claim an empty session.
         */
        @Test
        void takesTheFiguresFromTheListedSizeNotTheFiles() {
            RepositoryManager repo = repositoryWith(new RecordingSession(SESSION_ID, SESSION_ID, "inst-1", NOW,
                    NOW.plusSeconds(600), false, null, List.of(), false, new SessionSize(3, 4096L)));
            ConnectorConnection production = reachableConnector("production", "checkout", repo);
            configure(production);
            noLocalRecordings();

            JsonNode row = page(tools, null, null, 1, null).path("structuredContent").path("sessions").get(0);

            assertEquals(3, row.path("files").asInt());
            assertEquals(4096, row.path("sizeBytes").asLong());
        }

        /** A session with no recorded data yet has no duration, and says so with null rather than zero. */
        @Test
        void aSessionWithNoDataYetHasNoDuration() {
            RecordingSession running = new RecordingSession(SESSION_ID, SESSION_ID, "inst-1", NOW, null,
                    true, null, List.of(), false);
            ConnectorConnection production = reachableConnector("production", "checkout", repositoryWith(running));
            configure(production);
            noLocalRecordings();

            JsonNode catalogue = page(tools, null, null, 1, null).path("structuredContent");
            JsonNode row = catalogue.path("sessions").get(0);

            McpSchemaConformance.assertConforms(catalogue, schema);
            assertTrue(row.path("durationMs").isNull(), row.toString());
            assertEquals(NOW.toEpochMilli(), row.path("startedAtEpochMs").asLong());
        }

        /**
         * Data ending before the session started means the chunk names and the session's own name
         * disagree; the span is unknown, which is null, never a negative figure the schema refuses.
         */
        @Test
        void aSessionWhoseDataEndsBeforeItStartedHasNoDuration() {
            RecordingSession skewed = new RecordingSession(SESSION_ID, SESSION_ID, "inst-1", NOW,
                    NOW.minusSeconds(60), false, null, List.of(), false);
            ConnectorConnection production = reachableConnector("production", "checkout", repositoryWith(skewed));
            configure(production);
            noLocalRecordings();

            JsonNode catalogue = page(tools, null, null, 1, null).path("structuredContent");
            JsonNode row = catalogue.path("sessions").get(0);

            McpSchemaConformance.assertConforms(catalogue, schema);
            assertTrue(row.path("durationMs").isNull(), row.toString());
        }

        @Test
        void partialEmptyCatalogueNeverClaimsThereAreZeroSessions() {
            ConnectorConnection down = mock(ConnectorConnection.class);
            when(down.info()).thenReturn(connectorInfo("production"));
            when(down.infoOrThrow()).thenThrow(new JeffreyInternalException(ErrorCode.CONNECTOR_UNAVAILABLE, "Cannot reach the recording volume"));
            configure(down);
            noLocalRecordings();
            JsonNode result = page(tools, null, null, 1, null);
            JsonNode data = result.path("structuredContent");
            McpSchemaConformance.assertConforms(data, schema);
            assertEquals("UNREACHABLE", data.path("failures").get(0).path("kind").asString());
            assertTrue(data.path("total").isNull());
            assertEquals(0, data.path("observedTotal").asInt(-1));
            assertFalse(data.path("complete").asBoolean(true));
            assertFalse(data.path("hasMore").asBoolean());
            assertEquals("connector", data.path("failures").get(0).path("scope").asText());
            assertTrue(result.path("text").asText().contains("Not listed"));
        }

        @Test
        void boundsLargeFailureDetailsWhileKeepingTheScanIncomplete() {
            ConnectorConnection down = mock(ConnectorConnection.class);
            when(down.info()).thenReturn(connectorInfo("production".repeat(20000)));
            when(down.infoOrThrow()).thenThrow(new IllegalStateException("failure".repeat(20000)));
            configure(down);
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
            ConnectorConnection connector = reachableConnector("production", "checkout", repo);
            configure(connector);
            noLocalRecordings();
            String cursor = page(tools, null, null, 1, null).path("structuredContent").path("nextCursor").asText();
            when(repo.listRecordingSessions(any())).thenReturn(List.of(
                    jfrSession("new", NOW.plusSeconds(1)), jfrSession("b", NOW.minusSeconds(1))));
            JsonNode next = page(tools, null, null, 1, cursor).path("structuredContent");
            assertEquals("b", ConnectorSessionRef.decode(next.path("sessions").get(0).path("sessionRef").asText()).sessionId());
            assertFalse(next.path("hasMore").asBoolean());
        }

        @Test
        void cursorKeepsTheOriginalActiveWindowCutoff() {
            RepositoryManager repo = repositoryWith(jfrSession("a", NOW), jfrSession("b", NOW.minusSeconds(1)));
            ConnectorConnection connector = reachableConnector("production", "checkout", repo);
            configure(connector);
            noLocalRecordings();
            String cursor = page(tools, null, 60, 1, null).path("structuredContent").path("nextCursor").asText();
            ConnectorMcpTools later = ConnectorMcpToolsFixture.of(connectorManager, resolver, recordingsManager,
                    Clock.fixed(NOW.plusSeconds(120), ZoneOffset.UTC)).build();
            page(later, null, 60, 1, cursor);
            ArgumentCaptor<RecordingSessionFilter> filters = ArgumentCaptor.forClass(RecordingSessionFilter.class);
            verify(repo, times(2)).listRecordingSessions(filters.capture());
            assertEquals(filters.getAllValues().get(0).activeFrom(), filters.getAllValues().get(1).activeFrom());
        }

        @Test
        void rejectsMalformedAndFilterMismatchedCursorsBeforeConnectorReads() {
            var method = assertDoesNotThrow(() -> ConnectorMcpTools.class.getMethod("sessions",
                    String.class, String.class, Integer.class, Integer.class, String.class));
            var malformed = assertThrows(InvocationTargetException.class,
                    () -> method.invoke(tools, null, null, null, 1, "garbage"));
            assertTrue(malformed.getCause() instanceof IllegalArgumentException);
            verifyNoInteractions(connectorManager);
            RepositoryManager repo = repositoryWith(jfrSession("a", NOW), jfrSession("b", NOW));
            ConnectorConnection connector = reachableConnector("production", "checkout", repo);
            configure(connector);
            noLocalRecordings();
            String cursor = page(tools, "default", null, 1, null).path("structuredContent").path("nextCursor").asText();
            var mismatch = assertThrows(InvocationTargetException.class,
                    () -> method.invoke(tools, "stage", null, null, 1, cursor));
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
            ConnectorConnection connector = reachableConnector("production".repeat(200),
                    "checkout".repeat(200), repositoryWith(sessions));
            configure(connector);
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
                    ConnectorSessionRef.decode(next.path("sessions").get(0).path("sessionRef").asText()).sessionId());
        }
    }

    @Nested
    class ConnectorStatus {

        private JsonNode status(McpToolResult result) {
            return StructuredAnswers.markdown(ConnectorMcpTools.class, "status", result);
        }

        @Test
        void namesTheConfiguredConnectorWithItsAddress() {
            configure(reachableConnector("production", "checkout", repositoryWith()));

            McpToolResult result = tools.status();
            JsonNode answer = status(result);

            assertEquals("REACHABLE", answer.get("state").asString());
            assertTrue(answer.get("reason").isNull());
            assertEquals("production", answer.get("name").asString());
            assertEquals("AZURE_FILES", answer.get("kind").asString());
            assertEquals("azure-files:https://jeffreyprod.file.core.windows.net/recordings",
                    answer.get("address").asString());
            assertFalse(answer.get("readOnly").asBoolean());
            assertEquals("2.1.0", answer.get("version").asString());
            assertTrue(answer.get("uiLink").asString().endsWith("/connector"), answer.get("uiLink").asString());
            assertEquals(List.of("connector_sessions"), StructuredAnswers.nextTools(answer));
            assertTrue(result.text().contains("| production |"), result.text());
        }

        @Test
        void marksAConnectorThatDidNotAnswer() {
            configure(unreachableConnector("production"));

            JsonNode answer = status(tools.status());

            assertEquals("UNREACHABLE", answer.get("state").asString());
            assertTrue(answer.get("version").isNull());
            assertEquals(List.of(), StructuredAnswers.nextTools(answer), "the connector does not answer, so no session list either");
            assertTrue(StructuredAnswers.guidance(answer).contains("did not answer"), answer.toString());
        }

        /** The file share says when nothing can be deleted on it. */
        @Test
        void marksAReadOnlyAzureFileShare() {
            ConnectorConnection share = reachableConnector("prodshare", "checkout", repositoryWith());
            when(share.info()).thenReturn(new ConnectorInfo("prodshare",
                    new AzureFilesAddress("https://jeffreyprod.file.core.windows.net", "recordings", null,
                            AzureFilesProtocol.SMB, AzureFilesAuth.SAS, true)));
            configure(share);

            McpToolResult result = tools.status();
            JsonNode answer = status(result);

            assertEquals("AZURE_FILES", answer.get("kind").asString());
            assertTrue(answer.get("readOnly").asBoolean());
            assertEquals("azure-files:https://jeffreyprod.file.core.windows.net/recordings",
                    answer.get("address").asString());
            assertTrue(result.text().contains("| true |"), result.text());
        }

        /** A local directory is its own kind, named by its absolute path. */
        @Test
        void namesALocalDirectoryByItsKindAndPath() {
            ConnectorConnection directory = reachableConnector("scratch", "checkout", repositoryWith());
            when(directory.info()).thenReturn(new ConnectorInfo("scratch",
                    new LocalDirectoryAddress(Path.of("/tmp/jeffrey/workspaces"), false)));
            configure(directory);

            McpToolResult result = tools.status();
            JsonNode answer = status(result);

            assertEquals("LOCAL", answer.get("kind").asString());
            assertEquals("local:" + Path.of("/tmp/jeffrey/workspaces"), answer.get("address").asString());
            assertTrue(result.text().contains("| LOCAL |"), result.text());
        }

        @Test
        void saysSoWhenNoConnectorIsConfigured() {
            nothingConfigured();

            McpToolResult result = tools.status();
            JsonNode answer = status(result);

            assertEquals("NOT_CONFIGURED", answer.get("state").asString());
            assertTrue(answer.get("name").isNull());
            assertTrue(answer.get("kind").isNull());
            assertTrue(answer.get("readOnly").isNull(), "a connector that does not exist is neither read-only nor writable");
            assertTrue(answer.get("reason").asString().contains("No connector is configured"), answer.toString());
            assertTrue(result.text().contains("recordings_analyzeFile"), result.text());
        }

        @Test
        void tellsHowToDeclareTheConnectorInConfigurationWhenNoneIs() {
            nothingConfigured();

            McpToolResult result = tools.status();
            String reason = status(result).get("reason").asString();

            // Configuration is the only place a connector is declared; the UI cannot connect one.
            assertTrue(reason.contains("configuration only"), reason);
            assertFalse(reason.contains("Connector page"), reason);
            assertTrue(reason.contains("jeffrey.microscope.connector.name=<"), reason);
            assertFalse(reason.contains("hostname"), reason);
            assertTrue(reason.contains("jeffrey.microscope.connector.azure-files.share=<share>"), reason);
            // Every value only the user knows is a placeholder: an example value was copied into answers as
            // if it were the user's own.
            assertFalse(reason.contains("=Production") || reason.contains("=recordings"), reason);
            assertTrue(reason.contains("jeffrey.microscope.connector.local.path=<"), reason);
            assertTrue(result.text().contains(UiLinks.page(MicroscopePage.CONNECTOR)), result.text());
        }
    }

    @Nested
    class Sessions {

        @Test
        void rendersOneRowPerSessionCarryingItsRef() {
            ConnectorConnection production = reachableConnector("production", "checkout", repositoryWith(jfrSession(SESSION_ID, NOW)));
            configure(production);
            noLocalRecordings();

            String result = tools.sessions(null, null, null, null, null).text();

            assertTrue(result.contains("default"), result);
            assertTrue(result.contains("checkout"), result);
            assertTrue(result.contains(REF.encode()), result);
        }

        @Test
        void marksASessionAlreadyDownloadedButNotAnalysed() {
            ConnectorConnection production = reachableConnector("production", "checkout", repositoryWith(jfrSession(SESSION_ID, NOW)));
            configure(production);
            localRecording("rec-1", null, REF);

            String result = tools.sessions(null, null, null, null, null).text();

            assertTrue(result.contains("recording:rec-1"), result);
        }

        @Test
        void showsTheProfileForASessionAlreadyAnalysed() {
            ConnectorConnection production = reachableConnector("production", "checkout", repositoryWith(jfrSession(SESSION_ID, NOW)));
            configure(production);
            localRecording("rec-1", "profile-1", REF);

            String result = tools.sessions(null, null, null, null, null).text();

            assertTrue(result.contains("profile:profile-1"), result);
        }

        @Test
        void namesWhatCouldNotBeReadUnderTheTable() {
            configure(unreachableConnector("production"));
            noLocalRecordings();

            String result = tools.sessions(null, null, null, null, null).text();

            assertTrue(result.contains("Not listed: connector"), result);
            assertTrue(result.contains("connector_status"), result);
        }

        @Test
        void reportsAnUnreachableConnectorEvenWhenNothingMatched() {
            // The answer that would otherwise mislead: "no sessions" while production is simply down.
            configure(unreachableConnector("production"));
            noLocalRecordings();

            String result = tools.sessions(null, null, null, null, null).text();

            assertTrue(result.contains("Not listed"), result);
            assertFalse(result.contains("No recording sessions matched"), result);
        }

        @Test
        void translatesTheLastHourIntoAWindowOnTheConnector() {
            RepositoryManager repo = repositoryWith(jfrSession(SESSION_ID, NOW));
            ConnectorConnection production = reachableConnector("production", "checkout", repo);
            configure(production);
            noLocalRecordings();

            tools.sessions(null, null, 60, null, null).text();

            ArgumentCaptor<RecordingSessionFilter> captor =
                    ArgumentCaptor.forClass(RecordingSessionFilter.class);
            verify(repo).listRecordingSessions(captor.capture());
            assertEquals(NOW.minus(Duration.ofHours(1)), captor.getValue().activeFrom());
        }

        @Test
        void rejectsAWindowThatIsNotAWindow() {
            assertThrows(IllegalArgumentException.class,
                    () -> tools.sessions(null, null, 0, null, null).text());
        }

        @Test
        void readsEveryProjectRowBeforeApplyingThePageLimit() {
            RepositoryManager repo = repositoryWith(jfrSession(SESSION_ID, NOW));
            ConnectorConnection production = reachableConnector("production", "checkout", repo);
            configure(production);
            noLocalRecordings();

            tools.sessions(null, null, null, 10_000, null).text();

            ArgumentCaptor<RecordingSessionFilter> captor =
                    ArgumentCaptor.forClass(RecordingSessionFilter.class);
            verify(repo).listRecordingSessions(captor.capture());
            assertEquals(RecordingSessionFilter.NO_LIMIT, captor.getValue().limit());
        }

        @Test
        void keepsAPipeInAProjectNameOffTheColumnBoundaries() {
            ConnectorConnection production = reachableConnector("production", "check|out", repositoryWith(jfrSession(SESSION_ID, NOW)));
            configure(production);
            noLocalRecordings();

            assertTrue(tools.sessions(null, null, null, null, null).text().contains("check\\|out"));
        }

        @Test
        void saysHowToWidenAWindowThatMatchedNothing() {
            ConnectorConnection production = reachableConnector("production", "checkout", repositoryWith());
            configure(production);

            String result = tools.sessions(null, null, 60, null, null).text();

            assertTrue(result.contains("withinLastMinutes"), result);
            assertTrue(result.contains("60"), result);
        }

        @Test
        void pointsAtTheConnectorStatusWhenNothingMatchedAndNoWindowWasGiven() {
            ConnectorConnection production = reachableConnector("production", "checkout", repositoryWith());
            configure(production);

            assertTrue(tools.sessions(null, null, null, null, null).text().contains("connector_status"));
        }
    }

    @Nested
    class SessionAnswers {

        private JsonNode sessions(McpToolResult result) {
            return StructuredAnswers.markdown(ConnectorMcpTools.class, "sessions", result);
        }

        /**
         * A session not here yet leads to a look at its files; downloading it is guidance, because
         * which part of a session to bring is the user's choice and a transfer is heavy.
         */
        @Test
        void aSessionNotHereLeadsToItsFilesAndNeverStraightToADownload() {
            ConnectorConnection production = reachableConnector("production", "checkout", repositoryWith(jfrSession(SESSION_ID, NOW)));
            configure(production);
            noLocalRecordings();

            JsonNode answer = sessions(tools.sessions(null, null, null, null, null));

            assertEquals(REF.encode(), StructuredAnswers.call(answer, "connector_files").get("sessionRef").asString());
            assertFalse(StructuredAnswers.nextTools(answer).contains("connector_download"), answer.toString());
            assertTrue(StructuredAnswers.guidance(answer).contains("startEpochMs"), answer.toString());
            assertTrue(answer.get("uiLink").asString().endsWith("/connector"), answer.get("uiLink").asString());
            JsonNode row = answer.get("sessions").get(0);
            assertTrue(row.get("recordingId").isNull());
            assertTrue(row.get("profileId").isNull());
        }

        @Test
        void aSessionDownloadedButNotAnalysedLeadsToItsAnalysis() {
            ConnectorConnection production = reachableConnector("production", "checkout", repositoryWith(jfrSession(SESSION_ID, NOW)));
            configure(production);
            localRecording("rec-1", null, REF);

            JsonNode answer = sessions(tools.sessions(null, null, null, null, null));

            assertEquals("rec-1", answer.get("sessions").get(0).get("recordingId").asString());
            assertEquals("rec-1", StructuredAnswers.call(answer, "recordings_analyzeRecording")
                    .get("recordingId").asString());
        }

        @Test
        void aSessionAlreadyAnalysedLeadsToItsProfile() {
            ConnectorConnection production = reachableConnector("production", "checkout", repositoryWith(jfrSession(SESSION_ID, NOW)));
            configure(production);
            localRecording("rec-1", "profile-1", REF);

            JsonNode answer = sessions(tools.sessions(null, null, null, null, null));

            assertEquals("profile-1", answer.get("sessions").get(0).get("profileId").asString());
            assertEquals("profile-1", StructuredAnswers.call(answer, "profiles_summary").get("profileId").asString());
        }

        /** The next page is named once, by the footer's call, with every filter it repeats. */
        @Test
        void theNextPageIsACallWithTheSameFilters() {
            RepositoryManager repo = repositoryWith(jfrSession("a", NOW), jfrSession("b", NOW.minusSeconds(1)));
            ConnectorConnection connector = reachableConnector("production", "checkout", repo);
            configure(connector);
            noLocalRecordings();

            McpToolResult result = tools.sessions("default", null, 60, 1, null);
            JsonNode answer = sessions(result);

            JsonNode next = StructuredAnswers.call(answer, "connector_sessions");
            assertEquals("default", next.get("workspace").asString());
            assertEquals(60, next.get("withinLastMinutes").asInt());
            assertFalse(next.has("status"), next.toString());
            assertEquals(answer.get("nextCursor").asString(), next.get("cursor").asString());
            assertFalse(result.text().contains("nextCursor: `"), "the cursor is not repeated in prose");
        }

        @Test
        void nothingMatchedWithTheConnectorAnsweringIsEmpty() {
            ConnectorConnection production = reachableConnector("production", "checkout", repositoryWith());
            configure(production);

            JsonNode answer = sessions(tools.sessions(null, null, 60, null, null));

            assertEquals("EMPTY", answer.get("status").asString());
            assertTrue(answer.get("reason").asString().contains("withinLastMinutes"), answer.toString());
        }

        @Test
        void aConnectorThatDidNotAnswerIsNeverAnEmptyCatalogue() {
            ConnectorConnection down = mock(ConnectorConnection.class);
            when(down.info()).thenReturn(connectorInfo("production"));
            when(down.infoOrThrow()).thenThrow(new JeffreyInternalException(ErrorCode.CONNECTOR_UNAVAILABLE, "Cannot reach the recording volume"));
            configure(down);
            noLocalRecordings();

            JsonNode answer = sessions(tools.sessions(null, null, null, null, null));

            assertEquals("OK", answer.get("status").asString());
            assertTrue(answer.get("reason").isNull());
            assertEquals(List.of("connector_status"), StructuredAnswers.nextTools(answer));
        }
    }

    @Nested
    class Download {

        @Test
        void aCompleteCopyStillReconcilesMissingArtifacts() {
            localRecording("rec-existing", null, REF);
            RecordingsDownloadManager downloads = mock(RecordingsDownloadManager.class);
            when(downloads.downloadSession(SESSION_ID)).thenReturn("rec-existing");
            resolvesTo(projectWith(session(SESSION_ID, NOW,
                    file("f-1", "recording.jfr", ManagedFile.JFR),
                    file("heap", "heap.hprof", ManagedFile.HEAP_DUMP)), downloads));

            complete(tools.download(REF.encode(), null, null, null, null, null, null, RESOURCE_READ));

            verify(downloads).downloadSession(SESSION_ID);
        }

        @Test
        void namedFilesReconcileAGrowingLogAfterASuccessfulDownload() {
            RecordingsDownloadManager downloads = mock(RecordingsDownloadManager.class);
            when(downloads.downloadRecordings(eq(SESSION_ID), any())).thenReturn("rec-existing");
            ProjectManager project = projectWith(session(SESSION_ID, NOW,
                    file("f-1", "recording.jfr", ManagedFile.JFR),
                    file("log", "app.log", ManagedFile.APP_LOG)), downloads);
            resolvesTo(project);
            noLocalRecordings();
            Recording landed = landedRecording();
            when(recordingsManager.findRecording(any())).thenReturn(Optional.of(landed));

            complete(tools.download(REF.encode(), null, null, null, null, null,
                    List.of("f-1", "log"), RESOURCE_READ));
            when(project.repositoryManager().recordingSession(SESSION_ID)).thenReturn(session(SESSION_ID, NOW,
                    file("f-1", "recording.jfr", ManagedFile.JFR),
                    new RepositoryFile("log", "app.log", NOW, 2048L, false, null)));
            complete(tools.download(REF.encode(), null, null, null, null, null,
                    List.of("f-1", "log"), RESOURCE_READ));

            verify(downloads, times(2)).downloadRecordings(eq(SESSION_ID), any());
        }

        @Test
        void aDisabledProfileDoesNotReplaceTheAnalysisFollowUp() {
            localRecording("rec-existing", "profile-building", REF);
            RecordingsDownloadManager downloads = mock(RecordingsDownloadManager.class);
            when(downloads.downloadSession(SESSION_ID)).thenReturn("rec-existing");
            resolvesTo(projectWith(jfrSession(SESSION_ID, NOW), downloads));
            when(recordingsManager.profile("profile-building").orElseThrow().info().enabled()).thenReturn(false);
            Recording stored = new Recording("rec-existing", "checkout", null, RecordingEventSource.JFR,
                    NOW, NOW, NOW, true, "profile-building", "checkout", List.of());
            when(recordingsManager.findRecording("rec-existing")).thenReturn(Optional.of(stored));

            JsonNode answer = StructuredAnswers.json(ConnectorMcpTools.class, "download",
                    tools.download(REF.encode(), null, null, null, null, null, null, RESOURCE_READ));

            assertTrue(answer.path("profileId").isNull(), answer.toString());
            assertEquals(List.of("recordings_analyzeRecording"), StructuredAnswers.nextTools(answer));
            assertTrue(answer.path("uiLink").asString().endsWith("/recordings"));
        }

        @ParameterizedTest
        @ValueSource(booleans = {false, true})
        void aProfileThatBecomesUnavailableDuringPreflightIsNotOffered(boolean deleted) {
            localRecording("rec-existing", "profile-1", REF);
            ProfileInfo profileInfo = recordingsManager.profile("profile-1").orElseThrow().info();
            Recording stored = new Recording("rec-existing", "checkout", null, RecordingEventSource.JFR,
                    NOW, NOW, NOW, true, "profile-1", "checkout", List.of());
            when(recordingsManager.findRecording("rec-existing")).thenReturn(Optional.of(stored));
            RecordingSession session = jfrSession(SESSION_ID, NOW);
            ProjectManager project = projectWith(session, null);
            when(project.repositoryManager().recordingSession(SESSION_ID)).thenAnswer(_ -> {
                if (deleted) {
                    when(recordingsManager.profile("profile-1")).thenReturn(Optional.empty());
                } else {
                    when(profileInfo.enabled()).thenReturn(false);
                }
                return session;
            });
            resolvesTo(project);

            JsonNode answer = StructuredAnswers.json(ConnectorMcpTools.class, "download",
                    tools.download(REF.encode(), null, null, null, null, null, null, RESOURCE_READ));

            assertTrue(answer.path("profileId").isNull(), answer.toString());
            assertEquals(List.of("recordings_analyzeRecording"), StructuredAnswers.nextTools(answer));
            assertTrue(answer.path("uiLink").asString().endsWith("/recordings"));
        }

        private ProjectManager projectWith(RecordingSession session, RecordingsDownloadManager downloads) {
            RepositoryManager repo = mock(RepositoryManager.class);
            when(repo.recordingSession(SESSION_ID)).thenReturn(session);

            ProjectManager project = mock(ProjectManager.class);
            when(project.repositoryManager()).thenReturn(repo);
            when(project.info()).thenReturn(new ProjectInfo(
                    PROJECT_ID, "origin", "checkout", "ns", WORKSPACE_ID, NOW, NOW, Map.of()));
            if (downloads != null) {
                when(project.recordingsDownloadManager()).thenReturn(downloads);
            }
            return project;
        }

        private void resolvesTo(ProjectManager project) {
            resolvesTo(REF, project);
        }

        private void resolvesTo(ConnectorSessionRef ref, ProjectManager project) {
            ConnectorConnection connector = mock(ConnectorConnection.class);
            when(connector.info()).thenReturn(connectorInfo("production"));
            when(resolver.resolveConnector()).thenReturn(connector);
            when(resolver.currentConnector()).thenReturn(Optional.of(connector));
            when(resolver.resolve(ref.workspaceId(), ref.projectId())).thenReturn(project);
        }

        @Test
        void downloadsTheSessionTheRefNamesAndReturnsTheRecordingId() {
            RecordingsDownloadManager downloads = mock(RecordingsDownloadManager.class);
            when(downloads.downloadSession(SESSION_ID)).thenReturn("rec-new");
            resolvesTo(projectWith(jfrSession(SESSION_ID, NOW), downloads));
            noLocalRecordings();

            String result = complete(tools.download(REF.encode(), null, null, null, null, null, null, RESOURCE_READ));

            assertTrue(result.contains("\"recordingId\":\"rec-new\""), result);
            verify(downloads).downloadSession(SESSION_ID);
        }

        @Test
        void pointsAtRecordingsAnalyzeRecordingAsTheNextStep() {
            RecordingsDownloadManager downloads = mock(RecordingsDownloadManager.class);
            when(downloads.downloadSession(SESSION_ID)).thenReturn("rec-new");
            resolvesTo(projectWith(jfrSession(SESSION_ID, NOW), downloads));
            noLocalRecordings();

            assertTrue(complete(tools.download(REF.encode(), null, null, null, null, null, null, RESOURCE_READ))
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

            String result = complete(tools.download(REF.encode(), null, null, null, null, null, null, RESOURCE_READ));

            assertTrue(result.contains("\"recordingFiles\":1"), result);
            assertTrue(result.contains("\"artifactFiles\":1"), result);
        }

        @Test
        void returnsTheExistingRecordingRatherThanFetchingItTwice() {
            localRecording("rec-existing", null, REF);
            resolvesTo(projectWith(jfrSession(SESSION_ID, NOW), null));

            String result = complete(tools.download(REF.encode(), null, null, null, null, null, null, RESOURCE_READ));

            assertTrue(result.contains("rec-existing"), result);
            verify(resolver).resolve(WORKSPACE_ID, PROJECT_ID);
        }

        /**
         * A copy taken while the session was its instance's newest stops where the session stood
         * then; handed back, it would answer for the session with the chunks of an hour ago.
         */
        @Test
        void downloadsAgainWhenTheCopyHereWasTakenWhileTheSessionWasTheNewest() {
            localRecording("rec-partial", null, REF, false);
            RecordingsDownloadManager downloads = mock(RecordingsDownloadManager.class);
            when(downloads.downloadSession(SESSION_ID)).thenReturn("rec-new");
            resolvesTo(projectWith(jfrSession(SESSION_ID, NOW), downloads));

            String result = complete(tools.download(REF.encode(), null, null, null, null, null, null, RESOURCE_READ));

            assertTrue(result.contains("\"recordingId\":\"rec-new\""), result);
            verify(downloads).downloadSession(SESSION_ID);
        }

        @Test
        void pointsStraightAtAnalysisWhenTheSessionIsAlreadyAProfile() {
            localRecording("rec-existing", "profile-1", REF);
            resolvesTo(projectWith(jfrSession(SESSION_ID, NOW), null));

            String result = complete(tools.download(REF.encode(), null, null, null, null, null, null, RESOURCE_READ));

            assertTrue(result.contains("profile-1"), result);
            verify(resolver).resolve(WORKSPACE_ID, PROJECT_ID);
        }

        @Test
        void rejectsAMalformedRefWithoutReachingTheConnector() {
            assertThrows(IllegalArgumentException.class,
                    () -> complete(tools.download("not-a-ref", null, null, null, null, null, null, RESOURCE_READ)));

            verifyNoInteractions(resolver);
            verifyNoInteractions(recordingsManager);
        }

        @Test
        void sendsARefWithNoConnectorConfiguredToConnectorStatus() {
            noLocalRecordings();
            when(resolver.resolveConnector()).thenThrow(Exceptions.resourceNotFound("No connector is configured"));

            IllegalArgumentException e = assertThrows(
                    IllegalArgumentException.class,
                            () -> complete(tools.download(REF.encode(), null, null, null, null, null, null, RESOURCE_READ)));

            // A fresh listing cannot help while nothing is configured; the status says how to declare one.
            assertTrue(e.getMessage().contains("connector_status"), e.getMessage());
        }

        @Test
        void explainsASessionTheConnectorNoLongerHas() {
            noLocalRecordings();
            RepositoryManager repo = mock(RepositoryManager.class);
            when(repo.recordingSession(SESSION_ID))
                    .thenThrow(Exceptions.invalidRequest("Session not found"));
            ProjectManager project = mock(ProjectManager.class);
            when(project.repositoryManager()).thenReturn(repo);
            resolvesTo(project);

            IllegalArgumentException e = assertThrows(
                    IllegalArgumentException.class,
                            () -> complete(tools.download(REF.encode(), null, null, null, null, null, null, RESOURCE_READ)));

            assertTrue(e.getMessage().contains("retention"), e.getMessage());
        }

        @Test
        void refusesASessionWithNoRecordingToDownload() {
            RecordingsDownloadManager downloads = mock(RecordingsDownloadManager.class);
            RecordingSession logsOnly = session(SESSION_ID, NOW,
                    file("f-1", "gc.log", ManagedFile.JVM_LOG));
            resolvesTo(projectWith(logsOnly, downloads));
            noLocalRecordings();

            IllegalArgumentException e = assertThrows(
                    IllegalArgumentException.class,
                            () -> complete(tools.download(REF.encode(), null, null, null, null, null, null, RESOURCE_READ)));

            assertTrue(e.getMessage().contains("no recording file"), e.getMessage());
            verify(downloads, never()).downloadSession(any());
        }

        /**
         * A part chosen by name: resolved on the session as the connector describes it, downloaded through
         * the same paths a window or named files take, with what was picked and why in the answer.
         */
        @Nested
        class PredefinedWindows {

            private static final Instant SESSION_START = NOW.minus(Duration.ofHours(3));
            private static final Duration CHUNK = Duration.ofMinutes(15);

            /** Three hours of twelve compressed chunks, the third the largest; finished at NOW. */
            private ChunkedSessions threeHours() {
                return ChunkedSessions.startingAt(SESSION_START, CHUNK)
                        .compressed(10, 20, 90, 30, 20, 10, 20, 30, 40, 10, 20, 30);
            }

            private RecordingsDownloadManager serving(ChunkedSessions session) {
                RecordingsDownloadManager downloads = mock(RecordingsDownloadManager.class);
                when(downloads.downloadRecordings(eq(SESSION_ID), any())).thenReturn("rec-chunk");
                when(downloads.downloadWindow(eq(SESSION_ID), any())).thenReturn("rec-window");
                resolvesTo(projectWith(session.build(), downloads));
                noLocalRecordings();
                return downloads;
            }

            private JsonNode download(DownloadWindow window, Integer minutes, Long atEpochMs, McpCallContext call) {
                return StructuredAnswers.json(ConnectorMcpTools.class, "download",
                        tools.download(REF.encode(), window, minutes, atEpochMs, null, null, null, call));
            }

            @Test
            void peakDownloadsTheLargestCompressedChunkByItsId() {
                RecordingsDownloadManager downloads = serving(threeHours());

                JsonNode answer = download(DownloadWindow.PEAK, null, null, RESOURCE_READ);

                verify(downloads).downloadRecordings(SESSION_ID, List.of(ChunkedSessions.fileId(2)));
                JsonNode chosen = answer.get("chosen");
                assertEquals("PEAK", chosen.get("window").asString());
                assertEquals(ChunkedSessions.fileId(2), chosen.get("chunkFileId").asString());
                assertEquals(CHUNK.toMillis(), chosen.get("chunkLengthMs").asLong());
                assertTrue(chosen.get("evidence").asString().contains("4.5×"), chosen.toString());
            }

            @Test
            void startupAndLatestAreTheFirstAndTheNewestChunk() {
                RecordingsDownloadManager downloads = serving(threeHours());

                download(DownloadWindow.STARTUP, null, null, RESOURCE_READ);
                download(DownloadWindow.LATEST, null, null, RESOURCE_READ);

                verify(downloads).downloadRecordings(SESSION_ID, List.of(ChunkedSessions.fileId(0)));
                verify(downloads).downloadRecordings(SESSION_ID, List.of(ChunkedSessions.fileId(11)));
            }

            /** The cleaner removed the first chunks: nothing moves, and the answer says what is left. */
            @Test
            void startupTheConnectorNoLongerHoldsIsReportedNotSubstituted() {
                RecordingsDownloadManager downloads = serving(threeHours().withoutOldest(4));

                JsonNode answer = download(DownloadWindow.STARTUP, null, null, RESOURCE_READ);

                assertEquals("STARTUP_NOT_RETAINED", answer.get("status").asString());
                assertEquals(ChunkedSessions.fileId(4), answer.get("chosen").get("chunkFileId").asString());
                assertTrue(answer.get("reason").asString().contains("profile-4"), answer.toString());
                assertEquals(List.of("connector_files"), StructuredAnswers.nextTools(answer));
                verifyNoInteractions(downloads);
            }

            @Test
            void beforeAMomentDownloadsTheMinutesEndingThere() {
                RecordingsDownloadManager downloads = serving(threeHours());
                long heapDumpWritten = NOW.minus(Duration.ofMinutes(50)).toEpochMilli();

                download(DownloadWindow.BEFORE, 30, heapDumpWritten, RESOURCE_READ);

                verify(downloads).downloadWindow(SESSION_ID, ChunkWindow.ofEpochMillis(
                        heapDumpWritten - Duration.ofMinutes(30).toMillis(), heapDumpWritten));
            }

            /** A window named in conversation is a decision: an eliciting client is not asked again. */
            @Test
            void aNamedWindowIsNeverAskedAbout() {
                serving(threeHours());

                JsonNode answer = download(DownloadWindow.LAST_MINUTES, null, null, ELICITING);

                assertEquals("DOWNLOADED", answer.get("status").asString());
                assertEquals("LAST_MINUTES", answer.get("chosen").get("window").asString());
            }

            /** The form's answer and the call's argument resolve by the same rules to the same download. */
            @Test
            void theFormAnswerDownloadsWhatTheSameWindowNamedByTheCallDoes() {
                RecordingsDownloadManager downloads = serving(threeHours());

                McpToolOutcome asked = tools.download(REF.encode(), null, null, null, null, null, null,
                        answering(ELICITING, DownloadWindowQuestion.KEY, McpInputResponse.Action.ACCEPT,
                                "{\"window\":\"PEAK\"}"));

                assertInstanceOf(McpToolResult.class, asked);
                verify(downloads).downloadRecordings(SESSION_ID, List.of(ChunkedSessions.fileId(2)));
            }

            @Test
            void aWindowArgumentItDoesNotTakeIsRefusedBeforeTheConnectorIsRead() {
                assertThrows(IllegalArgumentException.class, () -> tools.download(
                        REF.encode(), DownloadWindow.PEAK, 10, null, null, null, null, RESOURCE_READ));
                verifyNoInteractions(resolver);
            }
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
                        false, null, chunks, false);
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
                return tools.download(REF.encode(), null, null, null, null, null, null, call);
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
                ConnectorMcpTools strict = ConnectorMcpToolsFixture.of(connectorManager, resolver, recordingsManager, CLOCK)
                        .withWindowQuestion(new DownloadWindowQuestion(Duration.ofHours(1), 1000))
                        .build();
                resolvesTo(projectWith(jfrSession(SESSION_ID, NOW), downloads));
                noLocalRecordings();

                message(strict.download(REF.encode(), null, null, null, null, null, null, ELICITING));

                verifyNoInteractions(downloads);
            }

            @Test
            void doesNotAskWhenTheCallNamesAWindow() {
                RecordingsDownloadManager downloads = servingThreeHours();

                McpToolOutcome outcome = tools.download(REF.encode(), null, null, null,
                        NOW.minusSeconds(600).toEpochMilli(), null, null, ELICITING);

                assertTrue(assertInstanceOf(McpToolResult.class, outcome).text().contains("rec-window"));
            }

            @Test
            void doesNotAskWhenTheCallNamesFiles() {
                RecordingsDownloadManager downloads = servingThreeHours();
                when(downloads.downloadRecordings(eq(SESSION_ID), any())).thenReturn("rec-files");

                McpToolOutcome outcome = tools.download(REF.encode(), null, null, null, null, null, List.of("c3"), ELICITING);

                assertTrue(assertInstanceOf(McpToolResult.class, outcome).text().contains("rec-files"));
            }

            /** The user chose the whole session in conversation: window WHOLE is not asked a second time. */
            @Test
            void doesNotAskWhenTheCallNamesTheWholeSession() {
                RecordingsDownloadManager downloads = servingThreeHours();

                McpToolOutcome outcome = tools.download(REF.encode(), DownloadWindow.WHOLE, null, null, null, null, null, ELICITING);

                assertTrue(assertInstanceOf(McpToolResult.class, outcome).text().contains("rec-whole"));
                verify(downloads).downloadSession(SESSION_ID);
            }

            @Test
            void refusesAWindowTogetherWithBoundsOrFiles() {
                servingThreeHours();

                assertThrows(IllegalArgumentException.class, () -> tools.download(REF.encode(), DownloadWindow.WHOLE, null, null,
                        NOW.minusSeconds(600).toEpochMilli(), null, null, ELICITING));
                assertThrows(IllegalArgumentException.class,
                        () -> tools.download(REF.encode(), DownloadWindow.WHOLE, null, null, null, null, List.of("c3"), ELICITING));
            }

            @Test
            void doesNotAskAboutASessionAlreadyHere() {
                localRecording("rec-existing", null, REF);
                resolvesTo(projectWith(jfrSession(SESSION_ID, NOW), null));

                McpToolOutcome outcome = wholeSession(ELICITING);

                assertTrue(assertInstanceOf(McpToolResult.class, outcome).text().contains("rec-existing"));
                verify(resolver).resolve(WORKSPACE_ID, PROJECT_ID);
            }

            @Test
            void theLastHourBringsTheChunksCoveringTheSessionsFinalHour() {
                RecordingsDownloadManager downloads = servingThreeHours();

                McpToolOutcome outcome = answered(McpInputResponse.Action.ACCEPT, "{\"window\":\"LAST_MINUTES\"}");

                JsonNode answer = StructuredAnswers.json(ConnectorMcpTools.class, "download", outcome);
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


            /** A window no chunk covers is asked about again, not turned into an error. */
            @Test
            void aChosenWindowNoChunkCoversIsAskedAgain() {
                RecordingsDownloadManager downloads = mock(RecordingsDownloadManager.class);
                RecordingSession lateFirstChunk = new RecordingSession(SESSION_ID, "checkout-api", "inst-1", START, NOW,
                        false, null, List.of(new RepositoryFile("c0", "profile-0.jfr",
                        START.plus(Duration.ofHours(2)), 100L, true, null)), false);
                resolvesTo(projectWith(lateFirstChunk, downloads));
                noLocalRecordings();

                String message = message(answered(McpInputResponse.Action.ACCEPT, "{\"window\":\"CUSTOM\",\"start\":\""
                        + START + "\",\"end\":\"" + START.plus(Duration.ofMinutes(30)) + "\"}"));

                assertTrue(message.startsWith("No chunk"), message);
                verifyNoInteractions(downloads);
            }

            /**
             * The instance's newest session: four chunks, the newest started 45 minutes ago. Its data
             * reaches one measured chunk past that start, which is where its windows are measured from.
             */
            private RecordingSession newestThreeHours() {
                RecordingSession ended = threeHours();
                return new RecordingSession(ended.id(), ended.name(), ended.instanceId(),
                        ended.createdAt(), NOW, true, null, ended.files(), false);
            }

            /** The newest chunk is on the volume like any other: a window inside it downloads it. */
            @Test
            void aChosenWindowInsideTheNewestChunkOfTheNewestSessionDownloadsIt() {
                RecordingsDownloadManager downloads = mock(RecordingsDownloadManager.class);
                when(downloads.downloadWindow(eq(SESSION_ID), any())).thenReturn("rec-window");
                resolvesTo(projectWith(newestThreeHours(), downloads));
                noLocalRecordings();

                McpToolOutcome outcome = answered(McpInputResponse.Action.ACCEPT,
                        "{\"window\":\"LAST_MINUTES\",\"minutes\":10}");

                assertTrue(assertInstanceOf(McpToolResult.class, outcome).text().contains("rec-window"));
                verify(downloads).downloadWindow(eq(SESSION_ID), any());
            }

            @Test
            void anExplicitWindowInsideTheNewestChunkOfTheNewestSessionDownloadsIt() {
                RecordingsDownloadManager downloads = mock(RecordingsDownloadManager.class);
                when(downloads.downloadWindow(eq(SESSION_ID), any())).thenReturn("rec-window");
                resolvesTo(projectWith(newestThreeHours(), downloads));
                noLocalRecordings();

                McpToolOutcome outcome = tools.download(REF.encode(), null, null, null,
                        NOW.minusSeconds(600).toEpochMilli(), NOW.toEpochMilli(), null, ELICITING);

                assertTrue(assertInstanceOf(McpToolResult.class, outcome).text().contains("rec-window"));
            }

            /** A client that never declared elicitation cannot steer the download with a forged answer. */
            @Test
            void aForgedAnswerFromAClientThatCannotBeAskedIsIgnored() {
                RecordingsDownloadManager downloads = servingThreeHours();

                McpToolOutcome outcome = wholeSession(answering(McpCallContext.RESOURCE_READ, KEY,
                        McpInputResponse.Action.ACCEPT, "{\"window\":\"LAST_MINUTES\"}"));

                assertTrue(assertInstanceOf(McpToolResult.class, outcome).text().contains("rec-whole"));
                verify(downloads).downloadSession(SESSION_ID);
                verify(downloads, never()).downloadWindow(any(), any());
            }

            @Test
            void theWholeSessionIsBroughtWhole() {
                RecordingsDownloadManager downloads = servingThreeHours();

                McpToolOutcome outcome = answered(McpInputResponse.Action.ACCEPT, "{\"window\":\"WHOLE\"}");

                assertTrue(assertInstanceOf(McpToolResult.class, outcome).text().contains("rec-whole"));
                verify(downloads).downloadSession(SESSION_ID);
                verify(downloads, never()).downloadWindow(any(), any());
            }

            @Test
            void aDeclineTransfersNothingAndSaysSo() {
                RecordingsDownloadManager downloads = servingThreeHours();

                McpToolOutcome outcome = answered(McpInputResponse.Action.DECLINE, null);

                JsonNode answer = StructuredAnswers.json(ConnectorMcpTools.class, "download", outcome);
                assertEquals("NOT_DOWNLOADED", answer.path("status").asString(), answer.toString());
                assertFalse(answer.path("reason").asString().isBlank(), answer.toString());
                assertTrue(StructuredAnswers.guidance(answer).contains("startEpochMs"), answer.toString());
                assertEquals(REF.encode(), StructuredAnswers.call(answer, "connector_files").path("sessionRef").asString());
                assertTrue(answer.path("recordingId").isNull());
                assertTrue(answer.path("uiLink").asString().endsWith("/connector"), answer.toString());
                verifyNoInteractions(downloads);
            }

            @Test
            void anIncompleteAnswerIsAskedAgainNamingTheProblem() {
                RecordingsDownloadManager downloads = servingThreeHours();

                String message = message(answered(McpInputResponse.Action.ACCEPT,
                        "{\"window\":\"LAST_MINUTES\",\"minutes\":\"ten\"}"));

                assertTrue(message.contains("minutes"), message);
                assertTrue(message.contains("checkout-api"), message);
                verifyNoInteractions(downloads);
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

        /** The same four chunks, but the session is its instance's newest: it may add more. */
        private RecordingSession newestFourChunks() {
            RecordingSession ended = fourChunks();
            return new RecordingSession(ended.id(), ended.name(), ended.instanceId(),
                    ended.createdAt(), ended.lastDataAt(), true, ended.absolutePath(),
                    ended.files(), ended.failed());
        }

        @Test
        void aWindowBringsTheCoveringChunksAndReportsTheirSpan() {
            RecordingsDownloadManager downloads = mock(RecordingsDownloadManager.class);
            when(downloads.downloadWindow(eq(SESSION_ID), any())).thenReturn("rec-window");
            resolvesTo(projectWith(fourChunks(), downloads));
            noLocalRecordings();

            String result = complete(tools.download(REF.encode(), null, null, null,
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

        /**
         * The chunk names give only starts, so the last chunk's end is an estimate of one chunk
         * length; once the recording is stored, its own metadata says where the data ends, and a last
         * chunk cut short is reported as short as it is.
         */
        @Test
        void aLandedWindowReportsTheSpanTheRecordingItselfHolds() {
            RecordingsDownloadManager downloads = mock(RecordingsDownloadManager.class);
            when(downloads.downloadWindow(eq(SESSION_ID), any())).thenReturn("rec-window");
            resolvesTo(projectWith(fourChunks(), downloads));
            noLocalRecordings();
            Recording landed = landedRecording();
            when(landed.recordingStartedAt()).thenReturn(NOW.plusSeconds(151));
            when(landed.recordingFinishedAt()).thenReturn(NOW.plusSeconds(400));
            when(recordingsManager.findRecording("rec-window")).thenReturn(Optional.of(landed));

            String result = complete(tools.download(REF.encode(), null, null, null,
                    NOW.plusSeconds(200).toEpochMilli(), NOW.plusSeconds(320).toEpochMilli(), null, RESOURCE_READ));

            assertTrue(result.contains("\"coveredStartEpochMs\":" + NOW.plusSeconds(151).toEpochMilli()), result);
            assertTrue(result.contains("\"coveredEndEpochMs\":" + NOW.plusSeconds(400).toEpochMilli()),
                    "not the estimated " + NOW.plusSeconds(450).toEpochMilli() + ": " + result);
        }

        @Test
        void aReusedAnalysedWindowLinksItsExistingProfile() {
            RecordingsDownloadManager downloads = mock(RecordingsDownloadManager.class);
            when(downloads.downloadWindow(eq(SESSION_ID), any())).thenReturn("rec-window");
            resolvesTo(projectWith(fourChunks(), downloads));
            noLocalRecordings();
            Recording landed = landedRecording();
            when(landed.id()).thenReturn("rec-window");
            when(landed.recordingName()).thenReturn("checkout");
            when(landed.hasProfile()).thenReturn(true);
            when(landed.profileId()).thenReturn("profile-existing");
            ProfileInfo profileInfo = mock(ProfileInfo.class);
            when(profileInfo.enabled()).thenReturn(true);
            ProfileManager profile = mock(ProfileManager.class);
            when(profile.info()).thenReturn(profileInfo);
            when(recordingsManager.profile("profile-existing")).thenReturn(Optional.of(profile));
            when(recordingsManager.findRecording("rec-window")).thenReturn(Optional.of(landed));

            JsonNode result = StructuredAnswers.json(ConnectorMcpTools.class, "download",
                    tools.download(REF.encode(), null, null, null, NOW.plusSeconds(200).toEpochMilli(),
                            NOW.plusSeconds(320).toEpochMilli(), null, RESOURCE_READ));

            assertEquals("profile-existing", result.path("profileId").asString());
            assertEquals(List.of("profiles_summary"), StructuredAnswers.nextTools(result));
            assertTrue(result.path("uiLink").asString().endsWith("/profiles/profile-existing"));
        }

        @Test
        void aWindowIsNotAnsweredFromTheWholeSessionAlreadyHere() {
            RecordingsDownloadManager downloads = mock(RecordingsDownloadManager.class);
            when(downloads.downloadWindow(eq(SESSION_ID), any())).thenReturn("rec-window");
            resolvesTo(projectWith(fourChunks(), downloads));
            localRecording("rec-existing", "profile-existing", REF);

            String result =
                    complete(tools.download(REF.encode(), null, null, null, NOW.plusSeconds(200).toEpochMilli(), null, null, RESOURCE_READ));

            assertTrue(result.contains("rec-window"), result);
            assertFalse(result.contains("rec-existing"), result);
        }

        /**
         * "Everything since 14:00" on the instance's newest session means something later every
         * time it is asked. Handing back the recording made an hour ago would answer a shorter
         * question than the one that was put.
         */
        @Test
        void anOpenEndedWindowOnTheNewestSessionIsFetchedAgainRatherThanReplayed() {
            RecordingsDownloadManager downloads = mock(RecordingsDownloadManager.class);
            when(downloads.downloadWindow(eq(SESSION_ID), any()))
                    .thenReturn("rec-first", "rec-second");
            resolvesTo(projectWith(newestFourChunks(), downloads));
            noLocalRecordings();
            Recording landed = landedRecording();
            when(recordingsManager.findRecording(any())).thenReturn(Optional.of(landed));

            long start = NOW.plusSeconds(200).toEpochMilli();
            assertTrue(complete(tools.download(REF.encode(), null, null, null, start, null, null, RESOURCE_READ))
                    .contains("rec-first"));
            String second = complete(tools.download(REF.encode(), null, null, null, start, null, null, RESOURCE_READ));

            assertTrue(second.contains("rec-second"), second);
            verify(downloads, times(2)).downloadWindow(eq(SESSION_ID), any());
        }

        /**
         * With no retained-download cache, an identical repeat call is not served from the recording the
         * first made: each closed-window call transfers its own part again.
         */
        @Test
        void anIdenticalClosedWindowCallTransfersAgain() {
            RecordingsDownloadManager downloads = mock(RecordingsDownloadManager.class);
            when(downloads.downloadWindow(eq(SESSION_ID), any())).thenReturn("rec-window");
            resolvesTo(projectWith(fourChunks(), downloads));
            noLocalRecordings();
            Recording landed = landedRecording();
            when(recordingsManager.findRecording(any())).thenReturn(Optional.of(landed));

            long start = NOW.plusSeconds(200).toEpochMilli();
            long end = NOW.plusSeconds(320).toEpochMilli();
            complete(tools.download(REF.encode(), null, null, null, start, end, null, RESOURCE_READ));
            complete(tools.download(REF.encode(), null, null, null, start, end, null, RESOURCE_READ));

            verify(downloads, times(2)).downloadWindow(eq(SESSION_ID), any());
        }

        /**
         * The newest recording of the instance's newest session may have been copied while the
         * profiler was still writing it into place, so a part holding it is fetched again rather
         * than handed back — however closed its window.
         */
        @Test
        void aClosedWindowHoldingTheNewestRecordingOfTheNewestSessionIsFetchedAgain() {
            RecordingsDownloadManager downloads = mock(RecordingsDownloadManager.class);
            when(downloads.downloadWindow(eq(SESSION_ID), any())).thenReturn("rec-first", "rec-second");
            resolvesTo(projectWith(newestFourChunks(), downloads));
            noLocalRecordings();
            Recording landed = landedRecording();
            when(recordingsManager.findRecording(any())).thenReturn(Optional.of(landed));

            long start = NOW.plusSeconds(460).toEpochMilli();
            long end = NOW.plusSeconds(500).toEpochMilli();
            complete(tools.download(REF.encode(), null, null, null, start, end, null, RESOURCE_READ));
            String second = complete(tools.download(REF.encode(), null, null, null, start, end, null, RESOURCE_READ));

            assertTrue(second.contains("rec-second"), second);
            verify(downloads, times(2)).downloadWindow(eq(SESSION_ID), any());
        }

        @Test
        void namingTheNewestRecordingOfTheNewestSessionFetchesItAgain() {
            RecordingsDownloadManager downloads = mock(RecordingsDownloadManager.class);
            when(downloads.downloadRecordings(eq(SESSION_ID), any())).thenReturn("rec-first", "rec-second");
            resolvesTo(projectWith(newestFourChunks(), downloads));
            noLocalRecordings();
            Recording landed = landedRecording();
            when(recordingsManager.findRecording(any())).thenReturn(Optional.of(landed));

            complete(tools.download(REF.encode(), null, null, null, null, null, List.of("c3"), RESOURCE_READ));
            complete(tools.download(REF.encode(), null, null, null, null, null, List.of("c3"), RESOURCE_READ));

            verify(downloads, times(2)).downloadRecordings(eq(SESSION_ID), any());
        }

        /**
         * The same on the newest session: with no retained-download cache, an identical closed-window
         * call before the newest recording transfers again rather than being served from the first.
         */
        @Test
        void anIdenticalClosedWindowCallBeforeTheNewestRecordingTransfersAgain() {
            RecordingsDownloadManager downloads = mock(RecordingsDownloadManager.class);
            when(downloads.downloadWindow(eq(SESSION_ID), any())).thenReturn("rec-window");
            resolvesTo(projectWith(newestFourChunks(), downloads));
            noLocalRecordings();
            Recording landed = landedRecording();
            when(recordingsManager.findRecording(any())).thenReturn(Optional.of(landed));

            long start = NOW.plusSeconds(200).toEpochMilli();
            long end = NOW.plusSeconds(320).toEpochMilli();
            complete(tools.download(REF.encode(), null, null, null, start, end, null, RESOURCE_READ));
            complete(tools.download(REF.encode(), null, null, null, start, end, null, RESOURCE_READ));

            verify(downloads, times(2)).downloadWindow(eq(SESSION_ID), any());
        }

        @Test
        void aWindowOutsideTheSessionIsRefusedWithTheSessionsSpan() {
            RecordingsDownloadManager downloads = mock(RecordingsDownloadManager.class);
            resolvesTo(projectWith(fourChunks(), downloads));
            noLocalRecordings();

            IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> complete(tools.download(
                    REF.encode(), null, null, null, NOW.plusSeconds(3600).toEpochMilli(), NOW.plusSeconds(7200).toEpochMilli(), null, RESOURCE_READ)));

            assertTrue(e.getMessage().contains("started at " + NOW), e.getMessage());
            assertTrue(e.getMessage().contains("its recorded data ends at " + NOW.plusSeconds(600)), e.getMessage());
            verifyNoInteractions(downloads);
        }

        @Test
        void aWindowWithItsEndBeforeItsStartIsRefusedBeforeTheConnectorIsAsked() {
            assertThrows(IllegalArgumentException.class, () -> complete(tools.download(
                    REF.encode(), null, null, null, NOW.plusSeconds(300).toEpochMilli(), NOW.plusSeconds(200).toEpochMilli(), null, RESOURCE_READ)));
            verifyNoInteractions(resolver);
        }

        @Test
        void namedFilesBringThoseFilesAndCountTheOnesBesideTheChunks() {
            RecordingsDownloadManager downloads = mock(RecordingsDownloadManager.class);
            when(downloads.downloadRecordings(eq(SESSION_ID), any())).thenReturn("rec-files");
            resolvesTo(projectWith(fourChunks(), downloads));
            noLocalRecordings();

            String result =
                    complete(tools.download(REF.encode(), null, null, null, null, null, List.of("c2", "log"), RESOURCE_READ));

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
                    () -> complete(tools.download(REF.encode(), null, null, null, null, null, List.of("c2", "nope"), RESOURCE_READ)));

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
                    () -> complete(tools.download(REF.encode(), null, null, null, null, null, List.of("c1", "c3"), RESOURCE_READ)));

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
                    complete(tools.download(REF.encode(), null, null, null, null, null, List.of("c1", "c2", "log"), RESOURCE_READ));

            assertTrue(result.contains("\"recordingFiles\":2"), result);
            verify(downloads).downloadRecordings(SESSION_ID, List.of("c1", "c2", "log"));
        }

        @Test
        void namedFilesWithoutAChunkAreRefused() {
            RecordingsDownloadManager downloads = mock(RecordingsDownloadManager.class);
            resolvesTo(projectWith(fourChunks(), downloads));
            noLocalRecordings();

            IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                    () -> complete(tools.download(REF.encode(), null, null, null, null, null, List.of("log"), RESOURCE_READ)));

            assertTrue(e.getMessage().contains("connector_fetchFile"), e.getMessage());
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
            ReflectiveToolset toolset = new ReflectiveToolset(tools, "connector");
            JsonNode fileIds = toolset.specs().stream()
                    .filter(spec -> spec.name().equals("connector_download"))
                    .findFirst()
                    .orElseThrow()
                    .inputSchema().path("properties").path("fileIds");

            assertEquals("array", fileIds.path("type").asString());
            assertEquals("string", fileIds.path("items").path("type").asString());

            ObjectNode arguments = Json.createObject().put("sessionRef", REF.encode());
            arguments.putArray("fileIds").add("c2").add("log");
            String result = toolset.call("connector_download", arguments);

            assertTrue(result.contains("\"recordingId\":\"rec-array\""), result);
            verify(downloads).downloadRecordings(SESSION_ID, List.of("c2", "log"));
        }

        @Test
        void aWindowAndFileIdsTogetherAreRefused() {
            assertThrows(IllegalArgumentException.class, () -> complete(tools.download(
                    REF.encode(), null, null, null, NOW.toEpochMilli(), null, List.of("c1"), RESOURCE_READ)));
            verifyNoInteractions(resolver);
        }

        @Test
        void sameSessionIdUnderAnotherProjectStartsAnIndependentTransfer() throws Exception {
            ConnectorSessionRef otherRef = new ConnectorSessionRef("ws-2", "proj-2", SESSION_ID);
            CountDownLatch started = new CountDownLatch(2);
            CountDownLatch release = new CountDownLatch(1);

            RecordingsDownloadManager first = blockingDownload("rec-first", started, release);
            RecordingsDownloadManager second = blockingDownload("rec-second", started, release);
            resolvesTo(REF, projectWith(jfrSession(SESSION_ID, NOW), first));
            resolvesTo(otherRef, projectWith(jfrSession(SESSION_ID, NOW), second));
            noLocalRecordings();

            try (ExecutorService callers = Executors.newFixedThreadPool(2)) {
                Future<String> firstCall = callers.submit(
                        () -> complete(tools.download(REF.encode(), null, null, null, null, null, null, RESOURCE_READ)));
                Future<String> secondCall = callers.submit(
                        () -> complete(tools.download(otherRef.encode(), null, null, null, null, null, null, RESOURCE_READ)));

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

        /**
         * No tool-level single-flight any more: two concurrent calls for the same ref each run their own
         * transfer. Both succeed and return a recording - duplicate work is fail-safe (isolated temp dirs,
         * fresh ids), never corruption.
         */
        @Test
        void concurrentCallsWithTheSameFullRefEachRunTheirOwnTransfer() throws Exception {
            CountDownLatch preflights = new CountDownLatch(2);
            CountDownLatch transferStarted = new CountDownLatch(1);
            CountDownLatch release = new CountDownLatch(1);
            AtomicInteger transfers = new AtomicInteger();
            AtomicBoolean persisted = new AtomicBoolean();
            Recording recording = landedRecording();
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

            try (ExecutorService callers = Executors.newFixedThreadPool(2)) {
                Future<String> firstCall = callers.submit(
                        () -> complete(tools.download(REF.encode(), null, null, null, null, null, null, RESOURCE_READ)));
                Future<String> secondCall = callers.submit(
                        () -> complete(tools.download(REF.encode(), null, null, null, null, null, null, RESOURCE_READ)));

                assertTrue(preflights.await(1, TimeUnit.SECONDS));
                assertTrue(transferStarted.await(1, TimeUnit.SECONDS));
                release.countDown();

                assertTrue(firstCall.get(2, TimeUnit.SECONDS).contains("rec-new"));
                assertTrue(secondCall.get(2, TimeUnit.SECONDS).contains("rec-new"));
                assertEquals(2, transfers.get());
            } finally {
                release.countDown();
            }
        }

        /**
         * A transfer that finished while another call was still in its preflight is not reused: the second
         * call runs its own transfer. Both return a recording.
         */
        @Test
        void aSecondCallRunsItsOwnTransferEvenAfterTheFirstCompleted() throws Exception {
            CountDownLatch secondPreflight = new CountDownLatch(1);
            CountDownLatch releasePreflight = new CountDownLatch(1);
            CountDownLatch transferStarted = new CountDownLatch(1);
            AtomicInteger preflightCalls = new AtomicInteger();
            AtomicInteger transfers = new AtomicInteger();
            AtomicBoolean persisted = new AtomicBoolean();
            Recording recording = landedRecording();
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
                        () -> complete(tools.download(REF.encode(), null, null, null, null, null, null, RESOURCE_READ)));
                assertTrue(transferStarted.await(2, TimeUnit.SECONDS));
                Future<String> second = callers.submit(
                        () -> complete(tools.download(REF.encode(), null, null, null, null, null, null, RESOURCE_READ)));
                assertTrue(first.get(3, TimeUnit.SECONDS).contains("rec-new"));
                releasePreflight.countDown();
                assertTrue(second.get(3, TimeUnit.SECONDS).contains("rec-new"));
                assertEquals(2, transfers.get());
            } finally {
                releasePreflight.countDown();
            }
        }


        /** A whole session already here is a DOWNLOADED answer that counts nothing it did not count. */
        @Test
        void aSessionAlreadyHereClaimsNoCountsItDidNotMake() {
            localRecording("rec-existing", null, REF);
            resolvesTo(projectWith(jfrSession(SESSION_ID, NOW), null));

            JsonNode answer = StructuredAnswers.json(ConnectorMcpTools.class, "download",
                    tools.download(REF.encode(), null, null, null, null, null, null, RESOURCE_READ));

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
            resolvesTo(projectWith(jfrSession(SESSION_ID, NOW), null));

            JsonNode answer = StructuredAnswers.json(ConnectorMcpTools.class, "download",
                    tools.download(REF.encode(), null, null, null, null, null, null, RESOURCE_READ));

            assertEquals("profile-1", answer.path("profileId").asString());
            assertEquals(List.of("profiles_summary"), StructuredAnswers.nextTools(answer));
            assertTrue(answer.path("uiLink").asString().endsWith("/profiles/profile-1"), answer.toString());
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

            assertTrue(complete(tools.download(REF.encode(), null, null, null, null, null, null, RESOURCE_READ))
                    .contains("rec-deleted"));
            assertTrue(complete(tools.download(REF.encode(), null, null, null, null, null, null, RESOURCE_READ))
                    .contains("rec-refetched"));

            verify(downloads, times(2)).downloadSession(SESSION_ID);
        }


        @Test
        void unavailableProjectLookupIsNotReportedAsAStaleRef() {
            noLocalRecordings();
            ConnectorConnection connector = mock(ConnectorConnection.class);
            when(connector.info()).thenReturn(connectorInfo("production"));
            when(resolver.resolveConnector()).thenReturn(connector);
            when(resolver.resolve(WORKSPACE_ID, PROJECT_ID))
                    .thenThrow(new JeffreyInternalException(ErrorCode.CONNECTOR_UNAVAILABLE, "connection refused"));

            JeffreyException exception = assertThrows(
                    JeffreyException.class,
                            () -> complete(tools.download(REF.encode(), null, null, null, null, null, null, RESOURCE_READ)));

            assertEquals(ErrorCode.CONNECTOR_UNAVAILABLE, exception.getCode());
            assertTrue(exception.getMessage().contains("connection refused"), exception.getMessage());
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
