/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
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

import cafe.jeffrey.hub.client.manager.RepositoryManager;
import cafe.jeffrey.microscope.core.manager.hub.HubManager;
import cafe.jeffrey.microscope.core.manager.project.ProjectManager;
import cafe.jeffrey.microscope.core.manager.project.ProjectsManager;
import cafe.jeffrey.microscope.core.manager.recordings.RecordingsManager;
import cafe.jeffrey.microscope.core.manager.workspace.WorkspaceManager;
import cafe.jeffrey.microscope.core.mcp.tools.hubs.HubSessionRef;
import cafe.jeffrey.microscope.core.web.ProjectManagerResolver;
import cafe.jeffrey.microscope.persistence.api.RecordingTag;
import cafe.jeffrey.profile.manager.ProfileManager;
import cafe.jeffrey.shared.common.Json;
import cafe.jeffrey.shared.common.exception.ErrorCode;
import cafe.jeffrey.shared.common.exception.ErrorType;
import cafe.jeffrey.shared.common.exception.JeffreyException;
import cafe.jeffrey.shared.common.model.ProfileInfo;
import cafe.jeffrey.shared.common.model.ProjectInfo;
import cafe.jeffrey.shared.common.model.Recording;
import cafe.jeffrey.shared.common.model.RecordingEventSource;
import cafe.jeffrey.shared.common.model.RecordingFile;
import cafe.jeffrey.shared.common.model.hub.HubAddress;
import cafe.jeffrey.shared.common.model.hub.HubInfo;
import cafe.jeffrey.shared.common.model.hub.HubSource;
import cafe.jeffrey.shared.common.model.repository.RecordingSession;
import cafe.jeffrey.shared.common.model.repository.RecordingStatus;
import cafe.jeffrey.shared.common.model.repository.RepositoryFile;
import cafe.jeffrey.shared.common.model.repository.StreamedFile;
import cafe.jeffrey.shared.common.model.repository.SupportedRecordingFile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.JsonNode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

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

class HubsArtifactsMcpToolsTest {

    private static final Instant NOW = Instant.parse("2026-09-14T12:00:00Z");
    private static final Instant PROFILE_START = Instant.parse("2026-09-14T11:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private static final String HUB_ID = "cfg-production";
    private static final String WORKSPACE_ID = "ws-1";
    private static final String PROJECT_ID = "proj-1";
    private static final String SESSION_ID = "session-1";
    private static final HubSessionRef REF = new HubSessionRef(HUB_ID, WORKSPACE_ID, PROJECT_ID, SESSION_ID);

    @TempDir
    Path home;

    private final ProjectManagerResolver resolver = mock(ProjectManagerResolver.class);
    private final RecordingsManager recordingsManager = mock(RecordingsManager.class);
    private final McpOperationRegistry operations = new McpOperationRegistry(CLOCK);
    private final RepositoryManager repository = mock(RepositoryManager.class);

    private HubsArtifactsMcpTools tools;

    @BeforeEach
    void setUp() {
        tools = new HubsArtifactsMcpTools(resolver, recordingsManager, home.resolve("artifacts"),
                home.resolve("profiles"), operations, CLOCK, Duration.ofSeconds(5), Duration.ofSeconds(5));
    }

    private static RepositoryFile file(String id, String name, SupportedRecordingFile type) {
        return file(id, name, type, NOW);
    }

    private static RepositoryFile file(String id, String name, SupportedRecordingFile type, Instant createdAt) {
        return new RepositoryFile(id, name, createdAt, 2048L, type, null);
    }

    private static RepositoryFile finished(String id, String name, SupportedRecordingFile type) {
        return file(id, name, type);
    }

    private static RecordingSession session(RepositoryFile... files) {
        return new RecordingSession(SESSION_ID, "instance-1", "inst-1", NOW, NOW.plusSeconds(600),
                RecordingStatus.FINISHED, null, List.of(files), false);
    }

    /**
     * A session that is still recording, so its newest chunk is the one the profiler holds open.
     */
    private static RecordingSession liveSession(RepositoryFile... files) {
        return new RecordingSession(SESSION_ID, "instance-1", "inst-1", NOW, null,
                RecordingStatus.ACTIVE, null, List.of(files), false);
    }

    private void hubHolds(RecordingSession session) {
        when(repository.recordingSession(SESSION_ID)).thenReturn(session);
        ProjectManager project = mock(ProjectManager.class);
        when(project.repositoryManager()).thenReturn(repository);
        when(project.info()).thenReturn(new ProjectInfo(
                PROJECT_ID, "checkout", "checkout", "checkout", "ns", WORKSPACE_ID, NOW, NOW, Map.of(), null));
        HubManager hub = mock(HubManager.class);
        when(hub.info()).thenReturn(new HubInfo(
                HUB_ID, "production", new HubAddress("hub.example.com", 443, false), NOW, HubSource.CONFIG));
        when(resolver.resolveHub(HUB_ID)).thenReturn(hub);
        when(resolver.resolveStrict(HUB_ID, WORKSPACE_ID, PROJECT_ID)).thenReturn(
                new ProjectManagerResolver.ProjectContext(mock(WorkspaceManager.class), mock(ProjectsManager.class), project));
        when(recordingsManager.listRecordings()).thenReturn(List.of());
    }

    private void sessionAlreadyDownloadedAs(String recordingId, String profileId, RecordingFile... files) {
        Recording recording = new Recording(recordingId, recordingId, null, null, RecordingEventSource.JDK, NOW, NOW, NOW,
                profileId != null, profileId, profileId, List.of(files));
        when(recordingsManager.listRecordings()).thenReturn(List.of(recording));
        when(recordingsManager.findRecording(recordingId)).thenReturn(Optional.of(recording));
        when(recordingsManager.tagsForRecordings(any())).thenReturn(Map.of(recordingId, List.of(
                new RecordingTag("origin.hubId", HUB_ID),
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
        return home.resolve("artifacts").resolve(HUB_ID).resolve(PROJECT_ID).resolve(SESSION_ID).resolve(name);
    }

    private Path profileTarget(String profileId, String name) {
        return home.resolve("profiles").resolve(profileId).resolve("artifacts").resolve(name);
    }

    /**
     * The hub types a file with {@link SupportedRecordingFile#of(String)}, and hubs_fetchFile derives
     * the type the same way when it answers off this disk. A fixture whose name does not classify to
     * the type it is handed is a file Jeffrey could never produce, and a test over it proves nothing
     * about the real path - which is how a GC log once came to be documented under a name that
     * classified as UNKNOWN. The names here are the ones the provisioner writes ({@code gc.jvm-log},
     * {@code hs-jvm-err.log}) and the ones a rolling appender leaves ({@code service-app.log}).
     */
    @Test
    void everyFixtureNameClassifiesAsTheTypeItIsGiven() {
        assertEquals(SupportedRecordingFile.JFR, SupportedRecordingFile.of("profile-1.jfr"));
        assertEquals(SupportedRecordingFile.APP_LOG, SupportedRecordingFile.of("service-app.log"));
        assertEquals(SupportedRecordingFile.JVM_LOG, SupportedRecordingFile.of("gc.jvm-log"));
        assertEquals(SupportedRecordingFile.HS_JVM_ERROR_LOG, SupportedRecordingFile.of("hs-jvm-err.log"));
        assertEquals(SupportedRecordingFile.HEAP_DUMP_GZ, SupportedRecordingFile.of("heap-dump.hprof.gz"));
        assertEquals(SupportedRecordingFile.UNKNOWN, SupportedRecordingFile.of("notes.txt"));
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
        void listsEveryFileWithItsTypeAndCategory() {
            hubHolds(session(
                    finished("f-jfr", "profile-1.jfr", SupportedRecordingFile.JFR),
                    finished("f-log", "service-app.log", SupportedRecordingFile.APP_LOG),
                    finished("f-crash", "hs-jvm-err.log", SupportedRecordingFile.HS_JVM_ERROR_LOG),
                    finished("f-gc", "gc.jvm-log", SupportedRecordingFile.JVM_LOG)));

            String text = tools.files(REF.encode());

            assertTrue(text.contains("| f-log | service-app.log | APP_LOG | artifact | FINISHED |"), text);
            assertTrue(text.contains("| f-crash | hs-jvm-err.log | HS_JVM_ERROR_LOG | artifact |"), text);
            assertTrue(text.contains("| f-gc | gc.jvm-log | JVM_LOG | artifact | FINISHED |"), text);
            assertTrue(text.contains("| f-jfr | profile-1.jfr | JFR | recording |"), text);
            assertTrue(text.contains("hubs_fetchFile"), text);
        }

        /**
         * The status column is derived here rather than sent: no file carries one, so the row for
         * the chunk the profiler still holds open is the session's status, and every other row —
         * the earlier chunks and every artifact beside them — reads FINISHED.
         */
        @Test
        void theOpenChunkOfALiveSessionIsTheOnlyRowThatIsNotFinished() {
            hubHolds(liveSession(
                    file("f-c1", "profile-1.jfr", SupportedRecordingFile.JFR, NOW),
                    file("f-c2", "profile-2.jfr", SupportedRecordingFile.JFR, NOW.plusSeconds(60)),
                    file("f-log", "service-app.log", SupportedRecordingFile.APP_LOG, NOW.plusSeconds(120))));

            String text = tools.files(REF.encode());

            assertTrue(text.contains("| f-c2 | profile-2.jfr | JFR | recording | ACTIVE |"), text);
            assertTrue(text.contains("| f-c1 | profile-1.jfr | JFR | recording | FINISHED |"), text);
            assertTrue(text.contains("| f-log | service-app.log | APP_LOG | artifact | FINISHED |"), text);
        }

        @Test
        void aFetchedFileShowsItsPath() throws IOException {
            hubHolds(session(finished("f-log", "service-app.log", SupportedRecordingFile.APP_LOG)));
            Path fetched = unlinkedTarget("service-app.log");
            Files.createDirectories(fetched.getParent());
            Files.writeString(fetched, "x");

            String text = tools.files(REF.encode());

            assertTrue(text.contains("| " + fetched + " |"), text);
        }

        @Test
        void anArtifactThatCameWithTheDownloadShowsTheRecordingsCopy() {
            hubHolds(session(
                    finished("f-jfr", "profile-1.jfr", SupportedRecordingFile.JFR),
                    finished("f-log", "service-app.log", SupportedRecordingFile.APP_LOG)));
            RecordingFile local = new RecordingFile("rf-1", "rec-1", "service-app.log", SupportedRecordingFile.APP_LOG, NOW, 2048L);
            sessionAlreadyDownloadedAs("rec-1", null, local);
            Path copy = home.resolve("recordings").resolve("rec-1-service.log");
            when(recordingsManager.findRecordingFile("rec-1", "rf-1")).thenReturn(Optional.of(copy));

            String text = tools.files(REF.encode());

            assertTrue(text.contains("recording:rec-1"), text);
            assertTrue(text.contains("| " + copy + " |"), text);
        }

        @Test
        void anAnalysedSessionNamesItsProfileAndZeroPoint() {
            hubHolds(session(finished("f-jfr", "profile-1.jfr", SupportedRecordingFile.JFR)));
            sessionAlreadyDownloadedAs("rec-1", "prof-1");

            String text = tools.files(REF.encode());

            assertTrue(text.contains("profile:prof-1"), text);
            assertTrue(text.contains(PROFILE_START.toString()), text);
        }

        @Test
        void aSessionWithNoFilesSaysSo() {
            hubHolds(session());

            assertTrue(tools.files(REF.encode()).contains("holds no files"));
        }

        @Test
        void theFetchColumnSaysWhichRowsFetchFileWillTake() {
            hubHolds(session(
                    finished("f-jfr", "profile-1.jfr", SupportedRecordingFile.JFR),
                    finished("f-log", "service-app.log", SupportedRecordingFile.APP_LOG),
                    finished("f-odd", "notes.txt", SupportedRecordingFile.UNKNOWN)));

            String text = tools.files(REF.encode());

            assertTrue(text.contains("| f-log | service-app.log | APP_LOG | artifact | FINISHED |"), text);
            // the last two cells of each row: an empty `local`, then `fetch`
            assertTrue(text.contains("|  | fetch |"), text);
            assertTrue(text.contains("|  | hubs_download |"), text);
            assertTrue(text.contains("|  | no |"), text);
        }

        @Test
        void aFileFetchedBeforeAnalysisIsStillReportedAsLocal() throws IOException {
            hubHolds(session(
                    finished("f-jfr", "profile-1.jfr", SupportedRecordingFile.JFR),
                    finished("f-gc", "gc.jvm-log", SupportedRecordingFile.JVM_LOG)));
            Path fetched = unlinkedTarget("gc.jvm-log");
            Files.createDirectories(fetched.getParent());
            Files.writeString(fetched, "x");
            sessionAlreadyDownloadedAs("rec-1", "prof-1");

            String text = tools.files(REF.encode());

            assertTrue(text.contains("| " + fetched + " |"), text);
        }

        @Test
        void aProfileWithNoStartInstantDoesNotPrintANullZeroPoint() {
            hubHolds(session(finished("f-jfr", "profile-1.jfr", SupportedRecordingFile.JFR)));
            sessionAlreadyDownloadedAs("rec-1", "prof-1");
            ProfileInfo noStart = new ProfileInfo("prof-1", null, null, "prof-1", RecordingEventSource.JDK,
                    null, null, NOW, true, false, "rec-1");
            ProfileManager profile = mock(ProfileManager.class);
            when(profile.info()).thenReturn(noStart);
            when(recordingsManager.profile("prof-1")).thenReturn(Optional.of(profile));

            String text = tools.files(REF.encode());

            assertFalse(text.contains("zero point is null"), text);
            assertTrue(text.contains("carries no start instant"), text);
        }
    }

    @Nested
    class Fetching {

        @Test
        void fetchesAFinishedArtifactUnderTheArtifactsDirectoryAndAnswersWithItsPath() throws IOException {
            hubHolds(session(finished("f-log", "service-app.log", SupportedRecordingFile.APP_LOG)));
            StreamedFile streamed = streamed("service-app.log");
            when(repository.streamFile(SESSION_ID, "f-log")).thenReturn(streamed);

            JsonNode answer = Json.readTree(tools.fetchFile(REF.encode(), "f-log"));

            Path expected = unlinkedTarget("service-app.log");
            assertEquals(expected.toString(), answer.path("path").asText());
            assertTrue(Files.isRegularFile(expected));
            assertFalse(Files.exists(streamed.path()));
            assertFalse(answer.path("alreadyHere").asBoolean());
            assertTrue(answer.path("nextStep").asText().contains("your own tools"), answer.toString());
            assertTrue(answer.has("operationId"));
        }

        @Test
        void anAnalysedSessionsFileLandsBesideItsProfile() throws IOException {
            hubHolds(session(
                    finished("f-jfr", "profile-1.jfr", SupportedRecordingFile.JFR),
                    finished("f-gc", "gc.jvm-log", SupportedRecordingFile.JVM_LOG)));
            sessionAlreadyDownloadedAs("rec-1", "prof-1");
            StreamedFile streamed = streamed("gc.jvm-log");
            when(repository.streamFile(SESSION_ID, "f-gc")).thenReturn(streamed);

            JsonNode answer = Json.readTree(tools.fetchFile(REF.encode(), "f-gc"));

            Path expected = home.resolve("profiles").resolve("prof-1").resolve("artifacts").resolve("gc.jvm-log");
            assertEquals(expected.toString(), answer.path("path").asText());
            assertTrue(Files.isRegularFile(expected));
            assertEquals("rec-1", answer.path("recordingId").asText());
            assertEquals("prof-1", answer.path("profileId").asText());
            assertEquals(PROFILE_START.toString(), answer.path("profilingStartedAt").asText());
        }

        @Test
        void aFileAlreadyAtItsPathIsReturnedWithoutATransfer() throws IOException {
            hubHolds(session(finished("f-log", "service-app.log", SupportedRecordingFile.APP_LOG)));
            Path fetched = unlinkedTarget("service-app.log");
            Files.createDirectories(fetched.getParent());
            Files.writeString(fetched, "x");

            JsonNode answer = Json.readTree(tools.fetchFile(REF.encode(), "f-log"));

            assertEquals(fetched.toString(), answer.path("path").asText());
            assertTrue(answer.path("alreadyHere").asBoolean());
            verify(repository, never()).streamFile(anyString(), anyString());
        }

        @Test
        void aHeapDumpPointsAtTheAnalyseTool() throws IOException {
            hubHolds(session(finished("f-heap", "heap-dump.hprof.gz", SupportedRecordingFile.HEAP_DUMP_GZ)));
            when(repository.streamFile(SESSION_ID, "f-heap")).thenReturn(streamed("heap-dump.hprof.gz"));

            JsonNode answer = Json.readTree(tools.fetchFile(REF.encode(), "f-heap"));

            assertTrue(answer.path("nextStep").asText().contains("recordings_analyzeFile"), answer.toString());
        }

        @Test
        void aRecordingChunkIsRefusedInFavourOfDownload() {
            hubHolds(session(finished("f-jfr", "profile-1.jfr", SupportedRecordingFile.JFR)));

            IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
                    () -> tools.fetchFile(REF.encode(), "f-jfr"));

            assertTrue(refused.getMessage().contains("hubs_download"), refused.getMessage());
        }

        /**
         * An artifact of a session that is still recording is fetched like any other. Only the
         * newest recording chunk is held open, and a log a reader wants to grep before deciding
         * whether the recording is worth the transfer is the whole point of this family.
         */
        @Test
        void anArtifactOfALiveSessionIsStillFetched() throws IOException {
            hubHolds(liveSession(
                    file("f-jfr", "profile-1.jfr", SupportedRecordingFile.JFR, NOW.plusSeconds(60)),
                    file("f-gc", "gc.jvm-log", SupportedRecordingFile.JVM_LOG, NOW.plusSeconds(120))));
            when(repository.streamFile(SESSION_ID, "f-gc")).thenReturn(streamed("gc.jvm-log"));

            String answer = tools.fetchFile(REF.encode(), "f-gc");

            assertTrue(answer.contains("gc.jvm-log"), answer);
        }

        @Test
        void aTransferThatFailedIsStartedAgainByCallingAgain() throws IOException {
            hubHolds(session(finished("f-log", "service-app.log", SupportedRecordingFile.APP_LOG)));
            when(repository.streamFile(SESSION_ID, "f-log"))
                    .thenThrow(new IllegalStateException("hub went away"))
                    .thenReturn(streamed("service-app.log"));

            assertThrows(RuntimeException.class, () -> tools.fetchFile(REF.encode(), "f-log"));

            JsonNode answer = Json.readTree(tools.fetchFile(REF.encode(), "f-log"));
            assertEquals(unlinkedTarget("service-app.log").toString(), answer.path("path").asText());
            verify(repository, times(2)).streamFile(SESSION_ID, "f-log");
        }

        @Test
        void aFileFetchedBeforeAnalysisIsMovedBesideTheProfileRatherThanTransferredAgain() throws IOException {
            hubHolds(session(
                    finished("f-jfr", "profile-1.jfr", SupportedRecordingFile.JFR),
                    finished("f-gc", "gc.jvm-log", SupportedRecordingFile.JVM_LOG)));
            when(repository.streamFile(SESSION_ID, "f-gc")).thenReturn(streamed("gc.jvm-log"));
            Path unlinked = unlinkedTarget("gc.jvm-log");
            assertEquals(unlinked.toString(),
                    Json.readTree(tools.fetchFile(REF.encode(), "f-gc")).path("path").asText());

            sessionAlreadyDownloadedAs("rec-1", "prof-1");
            JsonNode answer = Json.readTree(tools.fetchFile(REF.encode(), "f-gc"));

            Path beside = profileTarget("prof-1", "gc.jvm-log");
            assertEquals(beside.toString(), answer.path("path").asText());
            assertTrue(answer.path("alreadyHere").asBoolean());
            assertTrue(Files.isRegularFile(beside));
            assertFalse(Files.exists(unlinked));
            verify(repository, times(1)).streamFile(SESSION_ID, "f-gc");
        }

        @Test
        void aFileAlreadyFetchedIsAnsweredWithoutAskingTheHubAgain() throws IOException {
            hubHolds(session(finished("f-log", "service-app.log", SupportedRecordingFile.APP_LOG)));
            when(repository.streamFile(SESSION_ID, "f-log")).thenReturn(streamed("service-app.log"));
            tools.fetchFile(REF.encode(), "f-log");

            // The hub is gone: the session lookup the preflight would make now fails.
            when(repository.recordingSession(SESSION_ID)).thenThrow(
                    new JeffreyException(ErrorType.INTERNAL, ErrorCode.HUB_UNAVAILABLE, "hub is down"));

            JsonNode answer = Json.readTree(tools.fetchFile(REF.encode(), "f-log"));

            assertEquals(unlinkedTarget("service-app.log").toString(), answer.path("path").asText());
            assertTrue(answer.path("alreadyHere").asBoolean());
            assertEquals("APP_LOG", answer.path("type").asText());
            verify(repository, times(1)).streamFile(SESSION_ID, "f-log");
        }

        @Test
        void aRetainedPathIsNotAnsweredOnceTheSessionHasBeenAnalysed() throws IOException {
            hubHolds(session(
                    finished("f-jfr", "profile-1.jfr", SupportedRecordingFile.JFR),
                    finished("f-gc", "gc.jvm-log", SupportedRecordingFile.JVM_LOG)));
            when(repository.streamFile(SESSION_ID, "f-gc")).thenReturn(streamed("gc.jvm-log"));
            tools.fetchFile(REF.encode(), "f-gc");

            sessionAlreadyDownloadedAs("rec-1", "prof-1");
            JsonNode answer = Json.readTree(tools.fetchFile(REF.encode(), "f-gc"));

            // The retained path is stale, so the hub path runs and moves the file beside the profile.
            assertEquals(profileTarget("prof-1", "gc.jvm-log").toString(), answer.path("path").asText());
        }

        @Test
        void anUnclassifiedFileIsRefusedWithTheReasonAHubWillNotServeIt() {
            hubHolds(session(finished("f-odd", "notes.txt", SupportedRecordingFile.UNKNOWN)));

            IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
                    () -> tools.fetchFile(REF.encode(), "f-odd"));

            assertTrue(refused.getMessage().contains("hubs_download"), refused.getMessage());
        }

        @Test
        void aFileNameThatWouldClimbOutOfTheArtifactsDirectoryIsReducedToItsLastElement() throws IOException {
            hubHolds(session(finished("f-evil", "../../../../escaped.log", SupportedRecordingFile.APP_LOG)));
            when(repository.streamFile(SESSION_ID, "f-evil")).thenReturn(streamed("escaped.log"));

            JsonNode answer = Json.readTree(tools.fetchFile(REF.encode(), "f-evil"));

            Path landed = Path.of(answer.path("path").asText());
            assertEquals(unlinkedTarget("escaped.log"), landed);
            assertTrue(landed.startsWith(home.resolve("artifacts")), landed.toString());
        }

        @Test
        void anUnknownFileIdNamesTheListingTool() {
            hubHolds(session(finished("f-log", "service-app.log", SupportedRecordingFile.APP_LOG)));

            IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
                    () -> tools.fetchFile(REF.encode(), "f-nope"));

            assertTrue(refused.getMessage().contains("hubs_files"), refused.getMessage());
        }
    }
}
