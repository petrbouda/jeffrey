/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
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

import cafe.jeffrey.microscope.remote.client.manager.RepositoryManager;
import cafe.jeffrey.microscope.core.manager.remote.RemoteConnection;
import cafe.jeffrey.microscope.core.manager.project.ProjectManager;
import cafe.jeffrey.microscope.core.manager.recordings.RecordingsManager;
import cafe.jeffrey.microscope.core.mcp.AdvertisedFamilies;
import cafe.jeffrey.microscope.core.mcp.McpTestProperties;
import cafe.jeffrey.microscope.core.mcp.tools.remote.RemoteSessionRef;
import cafe.jeffrey.microscope.core.web.ProjectManagerResolver;
import cafe.jeffrey.microscope.mcp.protocol.McpTaskState;
import cafe.jeffrey.microscope.mcp.protocol.McpTaskStatus;
import cafe.jeffrey.microscope.mcp.protocol.McpToolOutcome;
import cafe.jeffrey.microscope.mcp.protocol.McpToolResult;
import cafe.jeffrey.microscope.persistence.api.RecordingTag;
import cafe.jeffrey.profile.common.operation.OperationState;
import cafe.jeffrey.profile.manager.ProfileManager;
import cafe.jeffrey.profile.mcp.McpNextToolConformance;
import cafe.jeffrey.microscope.model.ProfileInfo;
import cafe.jeffrey.microscope.model.ProjectInfo;
import cafe.jeffrey.microscope.model.RecordingEventSource;
import cafe.jeffrey.microscope.model.remote.AzureFilesAddress;
import cafe.jeffrey.microscope.model.remote.AzureFilesAuth;
import cafe.jeffrey.microscope.model.remote.AzureFilesProtocol;
import cafe.jeffrey.microscope.model.remote.RemoteInfo;
import cafe.jeffrey.microscope.model.repository.RecordingSession;
import cafe.jeffrey.microscope.model.repository.RepositoryFile;
import cafe.jeffrey.microscope.model.repository.StreamedFile;
import cafe.jeffrey.shared.common.Json;
import cafe.jeffrey.shared.common.exception.ErrorCode;
import cafe.jeffrey.shared.common.exception.ErrorType;
import cafe.jeffrey.shared.common.exception.JeffreyException;
import cafe.jeffrey.storage.recording.api.file.FileCategory;
import cafe.jeffrey.storage.recording.api.file.Bundle;
import cafe.jeffrey.storage.recording.api.file.BundleFile;
import cafe.jeffrey.recordings.core.OriginContext;
import cafe.jeffrey.storage.recording.api.file.ManagedFile;
import cafe.jeffrey.storage.recording.api.file.Recording;
import cafe.jeffrey.storage.recording.api.file.RecordingFile;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import tools.jackson.databind.JsonNode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static cafe.jeffrey.microscope.core.mcp.AdvertisedFamiliesFixture.EVERY_FAMILY;
import static cafe.jeffrey.microscope.core.mcp.tools.McpCallContexts.SHORT_TASK_WAIT;
import static cafe.jeffrey.microscope.core.mcp.tools.McpCallContexts.TASKS;
import static cafe.jeffrey.microscope.core.mcp.tools.McpCallContexts.complete;
import static cafe.jeffrey.microscope.mcp.protocol.McpCallContext.RESOURCE_READ;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTimeout;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RemoteArtifactsMcpToolsTest {

    private static final Instant NOW = Instant.parse("2026-09-14T12:00:00Z");
    private static final Instant PROFILE_START = Instant.parse("2026-09-14T11:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private static final String ADDRESS = "azure-files:https://jeffreyprod.file.core.windows.net/recordings";
    private static final String WORKSPACE_ID = "ws-1";
    private static final String PROJECT_ID = "proj-1";
    private static final String SESSION_ID = "session-1";
    private static final RemoteSessionRef REF = new RemoteSessionRef(WORKSPACE_ID, PROJECT_ID, SESSION_ID);

    @TempDir
    Path home;

    private final ProjectManagerResolver resolver = mock(ProjectManagerResolver.class);
    private final RecordingsManager recordingsManager = mock(RecordingsManager.class);
    private final McpOperationRegistry operations = new McpOperationRegistry(CLOCK);
    private final RepositoryManager repository = mock(RepositoryManager.class);

    private RemoteArtifactsMcpTools tools;

    @BeforeEach
    void setUp() {
        // The request every link is built from, inherited by the threads a test starts.
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()), true);
        tools = RemoteArtifactsMcpToolsFixture.of(resolver, recordingsManager, home.resolve("artifacts"),
                        home.resolve("profiles"), operations, CLOCK, EVERY_FAMILY)
                .withBudget(Duration.ofSeconds(5)).build();
    }

    @AfterEach
    void unbindRequest() {
        RequestContextHolder.resetRequestAttributes();
    }

    private static RepositoryFile file(String id, String name, ManagedFile type) {
        return file(id, name, type, NOW);
    }

    private static RepositoryFile file(String id, String name, ManagedFile type, Instant createdAt) {
        return new RepositoryFile(id, name, createdAt, 2048L, type.fileCategory() == FileCategory.RECORDING, null);
    }

    private static RepositoryFile finished(String id, String name, ManagedFile type) {
        return file(id, name, type);
    }

    private static RecordingSession session(RepositoryFile... files) {
        return new RecordingSession(SESSION_ID, "instance-1", "inst-1", NOW, NOW.plusSeconds(600),
                false, null, List.of(files), false);
    }

    /** Its instance's newest session, whose newest recording may still be on its way onto the volume. */
    private static RecordingSession liveSession(RepositoryFile... files) {
        return new RecordingSession(SESSION_ID, "instance-1", "inst-1", NOW, NOW.plusSeconds(120),
                true, null, List.of(files), false);
    }

    private void remoteHolds(RecordingSession session) {
        when(repository.recordingSession(SESSION_ID)).thenReturn(session);
        ProjectManager project = mock(ProjectManager.class);
        when(project.repositoryManager()).thenReturn(repository);
        when(project.info()).thenReturn(new ProjectInfo(
                PROJECT_ID, "checkout", "checkout", "ns", WORKSPACE_ID, NOW, NOW, Map.of()));
        RemoteConnection remote = mock(RemoteConnection.class);
        when(remote.info()).thenReturn(new RemoteInfo("production", new AzureFilesAddress(
                "https://jeffreyprod.file.core.windows.net", "recordings", "",
                AzureFilesProtocol.SMB, AzureFilesAuth.SAS, false)));
        when(resolver.resolveRemote()).thenReturn(remote);
        when(resolver.currentRemote()).thenReturn(Optional.of(remote));
        when(resolver.resolve(WORKSPACE_ID, PROJECT_ID)).thenReturn(project);
        when(recordingsManager.listRecordings()).thenReturn(List.of());
    }

    private void sessionAlreadyDownloadedAs(String recordingId, String profileId, RecordingFile... files) {
        Recording recording = new Recording(recordingId, recordingId, null, RecordingEventSource.JDK, NOW, NOW, NOW,
                profileId != null, profileId, profileId, List.of(files));
        when(recordingsManager.listRecordings()).thenReturn(List.of(recording));
        when(recordingsManager.findRecording(recordingId)).thenReturn(Optional.of(recording));
        when(recordingsManager.tagsForRecordings(any())).thenReturn(Map.of(recordingId, List.of(
                new RecordingTag(OriginContext.TAG_REMOTE_ADDRESS, ADDRESS),
                new RecordingTag("origin.workspaceId", WORKSPACE_ID),
                new RecordingTag("origin.projectId", PROJECT_ID),
                new RecordingTag("origin.recordingId", SESSION_ID))));
        if (profileId != null) {
            ProfileInfo info = new ProfileInfo(profileId, null, null, profileId, RecordingEventSource.JDK,
                    PROFILE_START, PROFILE_START.plusSeconds(60), NOW, true, false, recordingId);
            ProfileManager profile = mock(ProfileManager.class);
            when(profile.info()).thenReturn(info);
            when(recordingsManager.profile(profileId)).thenReturn(Optional.of(profile));
        }
    }

    private Path unlinkedTarget(String name) {
        return home.resolve("artifacts").resolve(WORKSPACE_ID).resolve(PROJECT_ID).resolve(SESSION_ID).resolve(name);
    }

    private Path profileTarget(String profileId, String name) {
        return home.resolve("profiles").resolve(profileId).resolve("artifacts").resolve(name);
    }

    /**
     * Microscope types a hub's file with {@link ManagedFile#of(String)} from the name the hub
     * sent, and remote_fetchFile derives the type the same way when it answers off this disk. A
     * fixture whose name does not classify to
     * the type it is handed is a file Jeffrey could never produce, and a test over it proves nothing
     * about the real path - which is how a GC log once came to be documented under a name that
     * classified as UNKNOWN. The names here are the ones the provisioner writes ({@code gc.jvm-log},
     * {@code hs-jvm-err.log}) and the ones a rolling appender leaves ({@code service-app.log}).
     */
    @Test
    void everyFixtureNameClassifiesAsTheTypeItIsGiven() {
        assertEquals(ManagedFile.JFR, ManagedFile.of("profile-1.jfr"));
        assertEquals(ManagedFile.APP_LOG, ManagedFile.of("service-app.log"));
        assertEquals(ManagedFile.JVM_LOG, ManagedFile.of("gc.jvm-log"));
        assertEquals(ManagedFile.HS_JVM_ERROR_LOG, ManagedFile.of("hs-jvm-err.log"));
        assertEquals(ManagedFile.HEAP_DUMP_GZ, ManagedFile.of("heap-dump.hprof.gz"));
        assertEquals(ManagedFile.UNKNOWN, ManagedFile.of("notes.txt"));
    }

    private static JsonNode fetched(McpToolOutcome outcome) {
        return StructuredAnswers.json(RemoteArtifactsMcpTools.class, "fetchFile", outcome);
    }

    private static JsonNode listed(McpToolResult result) {
        return StructuredAnswers.markdown(RemoteArtifactsMcpTools.class, "files", result);
    }

    private static List<String> fileIds(JsonNode answer) {
        List<String> ids = new ArrayList<>();
        answer.get("files").forEach(row -> ids.add(row.get("fileId").asString()));
        return ids;
    }

    private StreamedFile streamed(String name) throws IOException {
        Path dir = Files.createDirectories(home.resolve("stream"));
        Path file = Files.writeString(dir.resolve(name), "2026-09-14 12:00:00 ERROR boom\n");
        AtomicBoolean cleaned = new AtomicBoolean();
        return new StreamedFile(name, file, () -> cleaned.set(true));
    }

    @Nested
    class Listing {

        @Test
        void pagesTheListingAndSaysWhereTheNextPageStarts() {
            remoteHolds(session(
                    finished("f-1", "a.log", ManagedFile.APP_LOG),
                    finished("f-2", "b.log", ManagedFile.APP_LOG),
                    finished("f-3", "c.log", ManagedFile.APP_LOG),
                    finished("f-4", "d.log", ManagedFile.APP_LOG)));

            JsonNode first = listed(tools.files(REF.encode(), 2, null));
            assertEquals(List.of("f-1", "f-2"), fileIds(first));
            assertTrue(first.get("hasMore").asBoolean());
            JsonNode next = StructuredAnswers.call(first, "remote_files");
            assertEquals(REF.encode(), next.get("sessionRef").asString());
            assertEquals(first.get("nextCursor").asString(), next.get("cursor").asString());

            JsonNode second = listed(tools.files(REF.encode(), 2, next.get("cursor").asString()));
            assertEquals(List.of("f-3", "f-4"), fileIds(second));
            assertFalse(second.get("hasMore").asBoolean());
            assertEquals(4, second.get("total").asInt());
        }

        /** A cursor is bound to its session: one handed out for another session is refused. */
        @Test
        void aCursorOfAnotherSessionIsRefused() {
            remoteHolds(session(
                    finished("f-1", "a.log", ManagedFile.APP_LOG),
                    finished("f-2", "b.log", ManagedFile.APP_LOG)));
            String cursor = listed(tools.files(REF.encode(), 1, null)).get("nextCursor").asString();
            RemoteSessionRef other = new RemoteSessionRef(REF.workspaceId(), REF.projectId(), "other-session");

            assertThrows(IllegalArgumentException.class, () -> tools.files(other.encode(), 1, cursor));
        }

        @Test
        void listsEveryFileWithItsTypeAndCategory() {
            remoteHolds(session(
                    finished("f-jfr", "profile-1.jfr", ManagedFile.JFR),
                    finished("f-log", "service-app.log", ManagedFile.APP_LOG),
                    finished("f-crash", "hs-jvm-err.log", ManagedFile.HS_JVM_ERROR_LOG),
                    finished("f-gc", "gc.jvm-log", ManagedFile.JVM_LOG)));

            String text = tools.files(REF.encode(), null, null).text();

            assertTrue(text.contains("| f-log | service-app.log | APP_LOG | ARTIFACT |  |"), text);
            assertTrue(text.contains("| f-crash | hs-jvm-err.log | HS_JVM_ERROR_LOG | ARTIFACT |"), text);
            assertTrue(text.contains("| f-gc | gc.jvm-log | JVM_LOG | ARTIFACT |  |"), text);
            assertTrue(text.contains("| f-jfr | profile-1.jfr | JFR | RECORDING |"), text);
            assertTrue(text.contains("remote_fetchFile"), text);
        }

        /**
         * The newest column is derived here rather than sent: only the newest recording of the
         * instance's newest session is marked, never an earlier chunk or an artifact beside it.
         */
        @Test
        void theNewestRecordingOfTheNewestSessionIsTheOnlyRowMarkedNewest() {
            remoteHolds(liveSession(
                    file("f-c1", "profile-1.jfr", ManagedFile.JFR, NOW),
                    file("f-c2", "profile-2.jfr", ManagedFile.JFR, NOW.plusSeconds(60)),
                    file("f-log", "service-app.log", ManagedFile.APP_LOG, NOW.plusSeconds(120))));

            String text = tools.files(REF.encode(), null, null).text();

            assertTrue(text.contains("| f-c2 | profile-2.jfr | JFR | RECORDING | yes |"), text);
            assertTrue(text.contains("| f-c1 | profile-1.jfr | JFR | RECORDING |  |"), text);
            assertTrue(text.contains("| f-log | service-app.log | APP_LOG | ARTIFACT |  |"), text);
        }

        @Test
        void aFetchedFileShowsItsPath() throws IOException {
            remoteHolds(session(finished("f-log", "service-app.log", ManagedFile.APP_LOG)));
            Path fetched = unlinkedTarget("service-app.log");
            Files.createDirectories(fetched.getParent());
            Files.writeString(fetched, "x");

            McpToolResult result = tools.files(REF.encode(), null, null);

            assertTrue(result.text().contains("| " + fetched + " |"), result.text());
            assertEquals(fetched.toString(), listed(result).get("files").get(0).get("localPath").asString());
        }

        @Test
        void anArtifactThatCameWithTheDownloadShowsTheRecordingsCopy() {
            remoteHolds(session(
                    finished("f-jfr", "profile-1.jfr", ManagedFile.JFR),
                    finished("f-log", "service-app.log", ManagedFile.APP_LOG)));
            RecordingFile local = new RecordingFile("rf-1", "rec-1", "service-app.log", ManagedFile.APP_LOG, NOW, 2048L);
            sessionAlreadyDownloadedAs("rec-1", null, local);
            Path copy = home.resolve("recordings").resolve("rec-1-service.log");
            when(recordingsManager.findRecordingFile("rec-1", "rf-1")).thenReturn(Optional.of(copy));

            McpToolResult result = tools.files(REF.encode(), null, null);
            String text = result.text();

            assertTrue(text.contains("recording:rec-1"), text);
            assertTrue(text.contains("| " + copy + " |"), text);
            JsonNode answer = listed(result);
            assertEquals("rec-1", answer.get("recordingId").asString());
            assertTrue(answer.get("files").get(0).get("localPath").isNull(), "a chunk is named by its recording");
            assertEquals(copy.toString(), answer.get("files").get(1).get("localPath").asString());
            assertEquals("rec-1", StructuredAnswers.call(answer, "recordings_analyzeRecording").get("recordingId").asString());
        }

        /** A downloaded session is a bundle now: its log is a download-only file of the bundle. */
        @Test
        void aLogThatCameWithABundledDownloadShowsTheBundlesCopy() {
            remoteHolds(session(
                    finished("f-jfr", "profile-1.jfr", ManagedFile.JFR),
                    finished("f-log", "service-app.log", ManagedFile.APP_LOG)));
            RecordingFile chunk = new RecordingFile("rf-1", "rec-1", "profile-1.jfr", ManagedFile.JFR, NOW, 4096L);
            Recording bundled = new Recording("rec-1", "rec-1", null, RecordingEventSource.JDK, NOW, NOW, NOW,
                    false, null, null, List.of(chunk), "bundle-1");
            sessionAlreadyDownloadedAs("rec-1", null, chunk);
            when(recordingsManager.listRecordings()).thenReturn(List.of(bundled));
            when(recordingsManager.findRecording("rec-1")).thenReturn(Optional.of(bundled));
            when(recordingsManager.findBundleMembers("bundle-1")).thenReturn(List.of(bundled));
            BundleFile log = new BundleFile("bf-1", "bundle-1", "service-app.log", ManagedFile.APP_LOG, NOW, 2048L);
            when(recordingsManager.findBundle("bundle-1")).thenReturn(Optional.of(
                    new Bundle("bundle-1", "checkout", null, NOW, List.of(log))));
            Path copy = home.resolve("recordings").resolve("bundle-1-service-app.log");
            when(recordingsManager.findBundleFile("bundle-1", "bf-1")).thenReturn(Optional.of(copy));

            JsonNode answer = listed(tools.files(REF.encode(), null, null));

            assertEquals(copy.toString(), answer.get("files").get(1).get("localPath").asString());
        }

        @Test
        void anAnalysedSessionNamesItsProfileAndZeroPoint() {
            remoteHolds(session(finished("f-jfr", "profile-1.jfr", ManagedFile.JFR)));
            sessionAlreadyDownloadedAs("rec-1", "prof-1");

            McpToolResult result = tools.files(REF.encode(), null, null);
            String text = result.text();

            assertTrue(text.contains("profile:prof-1"), text);
            assertTrue(text.contains(PROFILE_START.toString()), text);
            JsonNode answer = listed(result);
            assertEquals(PROFILE_START.toEpochMilli(), answer.get("profilingStartedAtEpochMs").asLong());
            assertEquals("prof-1", StructuredAnswers.call(answer, "profiles_summary").get("profileId").asString());
        }

        @Test
        void aSessionWithNoFilesSaysSo() {
            remoteHolds(session());

            McpToolResult result = tools.files(REF.encode(), null, null);
            JsonNode answer = listed(result);

            assertEquals("EMPTY", answer.get("status").asString());
            assertTrue(answer.get("reason").asString().contains("holds no files"), answer.toString());
            assertTrue(result.text().contains("holds no files"), result.text());
        }

        @Test
        void theFetchColumnSaysWhichRowsFetchFileWillTake() {
            remoteHolds(session(
                    finished("f-jfr", "profile-1.jfr", ManagedFile.JFR),
                    finished("f-log", "service-app.log", ManagedFile.APP_LOG),
                    finished("f-odd", "notes.txt", ManagedFile.UNKNOWN)));

            McpToolResult result = tools.files(REF.encode(), null, null);
            String text = result.text();

            assertTrue(text.contains("| f-log | service-app.log | APP_LOG | ARTIFACT |  |"), text);
            // the last two cells of each row: an empty `local`, then `fetch`
            assertTrue(text.contains("|  | FETCH |"), text);
            assertTrue(text.contains("|  | DOWNLOAD |"), text);
            assertTrue(text.contains("|  | NEVER |"), text);
            JsonNode answer = listed(result);
            assertEquals("DOWNLOAD", answer.get("files").get(0).get("fetch").asString());
            assertEquals("FETCH", answer.get("files").get(1).get("fetch").asString());
            assertEquals("NEVER", answer.get("files").get(2).get("fetch").asString());
            assertEquals(List.of(), StructuredAnswers.nextTools(answer), "fetching and downloading are the reader's choice");
            assertTrue(StructuredAnswers.guidance(answer).contains("remote_fetchFile"), answer.toString());
        }

        @Test
        void aFileFetchedBeforeAnalysisIsStillReportedAsLocal() throws IOException {
            remoteHolds(session(
                    finished("f-jfr", "profile-1.jfr", ManagedFile.JFR),
                    finished("f-gc", "gc.jvm-log", ManagedFile.JVM_LOG)));
            Path fetched = unlinkedTarget("gc.jvm-log");
            Files.createDirectories(fetched.getParent());
            Files.writeString(fetched, "x");
            sessionAlreadyDownloadedAs("rec-1", "prof-1");

            String text = tools.files(REF.encode(), null, null).text();

            assertTrue(text.contains("| " + fetched + " |"), text);
        }

        @Test
        void aProfileWithNoStartInstantDoesNotPrintANullZeroPoint() {
            remoteHolds(session(finished("f-jfr", "profile-1.jfr", ManagedFile.JFR)));
            sessionAlreadyDownloadedAs("rec-1", "prof-1");
            ProfileInfo noStart = new ProfileInfo("prof-1", null, null, "prof-1", RecordingEventSource.JDK,
                    null, null, NOW, true, false, "rec-1");
            ProfileManager profile = mock(ProfileManager.class);
            when(profile.info()).thenReturn(noStart);
            when(recordingsManager.profile("prof-1")).thenReturn(Optional.of(profile));

            String text = tools.files(REF.encode(), null, null).text();

            assertFalse(text.contains("zero point is null"), text);
            assertTrue(text.contains("carries no start instant"), text);
        }
    }

    @Nested
    class Fetching {

        /**
         * A client that declared the tasks extension is handed a long transfer as a task after the
         * task budget. Only the wait on the transfer is shortened: the preflight that finds the file
         * on the hub keeps the whole response budget.
         */
        @Nested
        class TaskCapableClient {

            private RemoteArtifactsMcpTools standardBudget() {
                return standardBudget(ToolFixtures.answers());
            }

            /** The standard 45 s budget, and a task-capable client handed the task after a tenth of a second. */
            private RemoteArtifactsMcpTools shortTaskWait() {
                return standardBudget(new OperationAnswers(SHORT_TASK_WAIT));
            }

            private RemoteArtifactsMcpTools standardBudget(OperationAnswers answers) {
                return new RemoteArtifactsMcpTools(resolver, recordingsManager, home.resolve("artifacts"),
                        home.resolve("profiles"), operations, answers, CLOCK, BoundedJobs.WAIT_BUDGET,
                        EVERY_FAMILY);
            }

            @Test
            void handsBackARunningTransferAsATask() throws Exception {
                RecordingSession session = session(finished("f-log", "service-app.log", ManagedFile.APP_LOG));
                remoteHolds(session);
                StreamedFile streamed = streamed("service-app.log");
                CountDownLatch release = new CountDownLatch(1);
                when(repository.streamFile(SESSION_ID, "f-log")).thenAnswer(_ -> {
                    assertTrue(release.await(60, TimeUnit.SECONDS));
                    return streamed;
                });
                RemoteArtifactsMcpTools tools = shortTaskWait();

                McpToolOutcome outcome;
                try {
                    outcome = assertTimeout(Duration.ofSeconds(20),
                            () -> tools.fetchFile(REF.encode(), "f-log", TASKS));
                } catch (AssertionError e) {
                    release.countDown();
                    throw e;
                }

                String taskId = assertInstanceOf(McpToolOutcome.Deferred.class, outcome).taskId();
                assertEquals(OperationKind.REMOTE_FETCH, operations.status(taskId).kind());

                release.countDown();
                await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> assertEquals(
                        McpTaskStatus.COMPLETED, operations.task(taskId, kind -> true).status()));
                JsonNode answer = Json.readTree(assertInstanceOf(McpTaskState.Completed.class,
                        operations.task(taskId, kind -> true).state()).result().text());
                assertEquals(unlinkedTarget("service-app.log").toString(), answer.path("path").asString());
                assertEquals(taskId, answer.path("operationId").asString(), answer.toString());
            }

            /**
             * A transfer that fails after the call was handed a task names, in its operation, the call
             * that starts it again: this tool with the same session and file, a typed call rather than a
             * sentence.
             */
            @Test
            void aFailedTransferNamesTheCallThatFetchesItAgain() throws Exception {
                remoteHolds(session(finished("f-log", "service-app.log", ManagedFile.APP_LOG)));
                CountDownLatch release = new CountDownLatch(1);
                when(repository.streamFile(SESSION_ID, "f-log")).thenAnswer(_ -> {
                    assertTrue(release.await(60, TimeUnit.SECONDS));
                    throw new IllegalStateException("hub went away");
                });
                RemoteArtifactsMcpTools tools = shortTaskWait();

                McpToolOutcome outcome;
                try {
                    outcome = tools.fetchFile(REF.encode(), "f-log", TASKS);
                } finally {
                    release.countDown();
                }

                String taskId = assertInstanceOf(McpToolOutcome.Deferred.class, outcome).taskId();
                await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> assertEquals(
                        OperationState.FAILED, operations.status(taskId).status()));
                JsonNode snapshot = Json.toTree(operations.status(taskId));
                JsonNode retry = snapshot.path("followUp").path("nextTools").get(0);
                assertEquals("remote_fetchFile", retry.path("tool").asString(), snapshot.toString());
                assertEquals(REF.encode(), retry.path("arguments").path("sessionRef").asString());
                assertEquals("f-log", retry.path("arguments").path("fileId").asString());
                McpNextToolConformance.assertFollowable(snapshot, StructuredAnswers.reachable());
            }

            /** A transfer that lands inside the task budget answers the call with the task's own text. */
            @Test
            void answersATransferThatLandsInsideTheTaskBudgetWithTheTasksOwnAnswer() throws IOException {
                remoteHolds(session(finished("f-log", "service-app.log", ManagedFile.APP_LOG)));
                when(repository.streamFile(SESSION_ID, "f-log")).thenReturn(streamed("service-app.log"));

                McpToolOutcome outcome = standardBudget().fetchFile(REF.encode(), "f-log", TASKS);

                String answer = assertInstanceOf(McpToolResult.class, outcome).text();
                String taskId = Json.readTree(answer).path("operationId").asString();
                assertEquals(answer, assertInstanceOf(McpTaskState.Completed.class,
                        operations.task(taskId, kind -> true).state()).result().text());
            }
        }

        @Test
        void fetchesAFinishedArtifactUnderTheArtifactsDirectoryAndAnswersWithItsPath() throws IOException {
            remoteHolds(session(finished("f-log", "service-app.log", ManagedFile.APP_LOG)));
            StreamedFile streamed = streamed("service-app.log");
            when(repository.streamFile(SESSION_ID, "f-log")).thenReturn(streamed);

            JsonNode answer = fetched(tools.fetchFile(REF.encode(), "f-log", RESOURCE_READ));

            Path expected = unlinkedTarget("service-app.log");
            assertEquals(expected.toString(), answer.path("path").asText());
            assertTrue(Files.isRegularFile(expected));
            assertFalse(Files.exists(streamed.path()));
            assertFalse(answer.path("alreadyHere").asBoolean());
            assertEquals("FETCHED", answer.path("status").asString());
            assertTrue(StructuredAnswers.guidance(answer).contains("your own tools"), answer.toString());
            assertFalse(answer.path("operationId").asString().isBlank(), answer.toString());
            assertEquals("COMPLETED", answer.path("operation").path("status").asString());
            assertTrue(answer.path("uiLink").asString().endsWith("/remote"), answer.toString());
        }

        @Test
        void anAnalysedSessionsFileLandsBesideItsProfile() throws IOException {
            remoteHolds(session(
                    finished("f-jfr", "profile-1.jfr", ManagedFile.JFR),
                    finished("f-gc", "gc.jvm-log", ManagedFile.JVM_LOG)));
            sessionAlreadyDownloadedAs("rec-1", "prof-1");
            StreamedFile streamed = streamed("gc.jvm-log");
            when(repository.streamFile(SESSION_ID, "f-gc")).thenReturn(streamed);

            JsonNode answer = Json.readTree(complete(tools.fetchFile(REF.encode(), "f-gc", RESOURCE_READ)));

            Path expected = home.resolve("profiles").resolve("prof-1").resolve("artifacts").resolve("gc.jvm-log");
            assertEquals(expected.toString(), answer.path("path").asText());
            assertTrue(Files.isRegularFile(expected));
            assertEquals("rec-1", answer.path("recordingId").asText());
            assertEquals("prof-1", answer.path("profileId").asText());
            assertEquals(PROFILE_START.toEpochMilli(), answer.path("profilingStartedAtEpochMs").asLong());
            assertTrue(StructuredAnswers.guidance(answer).contains("prof-1"), answer.toString());
        }

        @Test
        void aFileAlreadyAtItsPathIsReturnedWithoutATransfer() throws IOException {
            remoteHolds(session(finished("f-log", "service-app.log", ManagedFile.APP_LOG)));
            Path fetched = unlinkedTarget("service-app.log");
            Files.createDirectories(fetched.getParent());
            Files.writeString(fetched, "x");

            JsonNode answer = Json.readTree(complete(tools.fetchFile(REF.encode(), "f-log", RESOURCE_READ)));

            assertEquals(fetched.toString(), answer.path("path").asText());
            assertTrue(answer.path("alreadyHere").asBoolean());
            verify(repository, never()).streamFile(anyString(), anyString());
        }

        @Test
        void aHeapDumpPointsAtTheAnalyseTool() throws IOException {
            remoteHolds(session(finished("f-heap", "heap-dump.hprof.gz", ManagedFile.HEAP_DUMP_GZ)));
            when(repository.streamFile(SESSION_ID, "f-heap")).thenReturn(streamed("heap-dump.hprof.gz"));

            JsonNode answer = fetched(tools.fetchFile(REF.encode(), "f-heap", RESOURCE_READ));

            assertEquals(answer.path("path").asString(),
                    StructuredAnswers.call(answer, "recordings_analyzeFile").path("path").asString());
            assertEquals("HEAP_DUMP_GZ", answer.path("type").asString());
        }

        /** Without heap_ there is nothing here to analyse the dump with, and the answer says so. */
        @Test
        void aHeapDumpSaysTheHeapToolsAreNotServedWhenTheHeapFamilyIsWithheld() throws IOException {
            tools = RemoteArtifactsMcpToolsFixture.of(resolver, recordingsManager, home.resolve("artifacts"),
                            home.resolve("profiles"), operations, CLOCK,
                            AdvertisedFamilies.of(McpTestProperties.of(
                                    true, true, true, Set.of("profiles", "recordings", "remote", "operations"))))
                    .withBudget(Duration.ofSeconds(5)).build();
            remoteHolds(session(finished("f-heap", "heap-dump.hprof.gz", ManagedFile.HEAP_DUMP_GZ)));
            when(repository.streamFile(SESSION_ID, "f-heap")).thenReturn(streamed("heap-dump.hprof.gz"));

            JsonNode answer = Json.readTree(complete(tools.fetchFile(REF.encode(), "f-heap", RESOURCE_READ)));

            assertTrue(StructuredAnswers.guidance(answer).contains("heap_ is not served by this installation"),
                    answer.toString());
            assertEquals(List.of(), StructuredAnswers.nextTools(answer));
        }

        @Test
        void aRecordingChunkIsRefusedInFavourOfDownload() {
            remoteHolds(session(finished("f-jfr", "profile-1.jfr", ManagedFile.JFR)));

            IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
                    () -> complete(tools.fetchFile(REF.encode(), "f-jfr", RESOURCE_READ)));

            assertTrue(refused.getMessage().contains("remote_download"), refused.getMessage());
        }

        /**
         * An artifact of the newest session is fetched like any other: a log a reader wants to grep
         * before deciding whether the recording is worth the transfer is the whole point of this family.
         */
        @Test
        void anArtifactOfTheNewestSessionIsFetched() throws IOException {
            remoteHolds(liveSession(
                    file("f-jfr", "profile-1.jfr", ManagedFile.JFR, NOW.plusSeconds(60)),
                    file("f-gc", "gc.jvm-log", ManagedFile.JVM_LOG, NOW.plusSeconds(120))));
            when(repository.streamFile(SESSION_ID, "f-gc")).thenReturn(streamed("gc.jvm-log"));

            String answer = complete(tools.fetchFile(REF.encode(), "f-gc", RESOURCE_READ));

            assertTrue(answer.contains("gc.jvm-log"), answer);
        }

        @Test
        void aTransferThatFailedIsStartedAgainByCallingAgain() throws IOException {
            remoteHolds(session(finished("f-log", "service-app.log", ManagedFile.APP_LOG)));
            when(repository.streamFile(SESSION_ID, "f-log"))
                    .thenThrow(new IllegalStateException("hub went away"))
                    .thenReturn(streamed("service-app.log"));

            assertThrows(RuntimeException.class, () -> complete(tools.fetchFile(REF.encode(), "f-log", RESOURCE_READ)));

            JsonNode answer = Json.readTree(complete(tools.fetchFile(REF.encode(), "f-log", RESOURCE_READ)));
            assertEquals(unlinkedTarget("service-app.log").toString(), answer.path("path").asText());
            verify(repository, times(2)).streamFile(SESSION_ID, "f-log");
        }

        @Test
        void aFileFetchedBeforeAnalysisIsMovedBesideTheProfileRatherThanTransferredAgain() throws IOException {
            remoteHolds(session(
                    finished("f-jfr", "profile-1.jfr", ManagedFile.JFR),
                    finished("f-gc", "gc.jvm-log", ManagedFile.JVM_LOG)));
            when(repository.streamFile(SESSION_ID, "f-gc")).thenReturn(streamed("gc.jvm-log"));
            Path unlinked = unlinkedTarget("gc.jvm-log");
            assertEquals(unlinked.toString(),
                    Json.readTree(complete(tools.fetchFile(REF.encode(), "f-gc", RESOURCE_READ)))
                            .path("path").asText());

            sessionAlreadyDownloadedAs("rec-1", "prof-1");
            JsonNode answer = Json.readTree(complete(tools.fetchFile(REF.encode(), "f-gc", RESOURCE_READ)));

            Path beside = profileTarget("prof-1", "gc.jvm-log");
            assertEquals(beside.toString(), answer.path("path").asText());
            assertTrue(answer.path("alreadyHere").asBoolean());
            assertTrue(Files.isRegularFile(beside));
            assertFalse(Files.exists(unlinked));
            verify(repository, times(1)).streamFile(SESSION_ID, "f-gc");
        }

        @Test
        void aFileAlreadyFetchedIsAnsweredWithoutAskingTheRemoteAgain() throws IOException {
            remoteHolds(session(finished("f-log", "service-app.log", ManagedFile.APP_LOG)));
            when(repository.streamFile(SESSION_ID, "f-log")).thenReturn(streamed("service-app.log"));
            complete(tools.fetchFile(REF.encode(), "f-log", RESOURCE_READ));

            // The hub is gone: the session lookup the preflight would make now fails.
            when(repository.recordingSession(SESSION_ID)).thenThrow(
                    new JeffreyException(ErrorType.INTERNAL, ErrorCode.REMOTE_UNAVAILABLE, "hub is down"));

            JsonNode answer = Json.readTree(complete(tools.fetchFile(REF.encode(), "f-log", RESOURCE_READ)));

            assertEquals(unlinkedTarget("service-app.log").toString(), answer.path("path").asText());
            assertTrue(answer.path("alreadyHere").asBoolean());
            assertEquals("APP_LOG", answer.path("type").asText());
            verify(repository, times(1)).streamFile(SESSION_ID, "f-log");
        }

        @Test
        void aRetainedPathIsNotAnsweredOnceTheSessionHasBeenAnalysed() throws IOException {
            remoteHolds(session(
                    finished("f-jfr", "profile-1.jfr", ManagedFile.JFR),
                    finished("f-gc", "gc.jvm-log", ManagedFile.JVM_LOG)));
            when(repository.streamFile(SESSION_ID, "f-gc")).thenReturn(streamed("gc.jvm-log"));
            complete(tools.fetchFile(REF.encode(), "f-gc", RESOURCE_READ));

            sessionAlreadyDownloadedAs("rec-1", "prof-1");
            JsonNode answer = Json.readTree(complete(tools.fetchFile(REF.encode(), "f-gc", RESOURCE_READ)));

            // The retained path is stale, so the hub path runs and moves the file beside the profile.
            assertEquals(profileTarget("prof-1", "gc.jvm-log").toString(), answer.path("path").asText());
        }

        @Test
        void anUnclassifiedFileIsRefusedAsNotAnArtifact() {
            remoteHolds(session(finished("f-odd", "notes.txt", ManagedFile.UNKNOWN)));

            IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
                    () -> complete(tools.fetchFile(REF.encode(), "f-odd", RESOURCE_READ)));

            assertTrue(refused.getMessage().contains("remote_download"), refused.getMessage());
        }

        @Test
        void theProfilersCacheFileIsRefusedAsNotAnArtifact() {
            remoteHolds(session(finished("f-cache", "profile-20260220-120000.jfr.1~", ManagedFile.ASPROF_TEMP)));

            IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
                    () -> complete(tools.fetchFile(REF.encode(), "f-cache", RESOURCE_READ)));

            assertTrue(refused.getMessage().contains("profile-20260220-120000.jfr.1~"), refused.getMessage());
            assertTrue(refused.getMessage().contains("remote_download"), refused.getMessage());
        }

        @Test
        void aFileNameThatWouldClimbOutOfTheArtifactsDirectoryIsReducedToItsLastElement() throws IOException {
            remoteHolds(session(finished("f-evil", "../../../../escaped.log", ManagedFile.APP_LOG)));
            when(repository.streamFile(SESSION_ID, "f-evil")).thenReturn(streamed("escaped.log"));

            JsonNode answer = Json.readTree(complete(tools.fetchFile(REF.encode(), "f-evil", RESOURCE_READ)));

            Path landed = Path.of(answer.path("path").asText());
            assertEquals(unlinkedTarget("escaped.log"), landed);
            assertTrue(landed.startsWith(home.resolve("artifacts")), landed.toString());
        }

        @Test
        void anUnknownFileIdNamesTheListingTool() {
            remoteHolds(session(finished("f-log", "service-app.log", ManagedFile.APP_LOG)));

            IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
                    () -> complete(tools.fetchFile(REF.encode(), "f-nope", RESOURCE_READ)));

            assertTrue(refused.getMessage().contains("remote_files"), refused.getMessage());
        }
    }
}
