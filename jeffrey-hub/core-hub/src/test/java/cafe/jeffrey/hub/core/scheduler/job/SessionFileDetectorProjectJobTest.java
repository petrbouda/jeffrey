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

package cafe.jeffrey.hub.core.scheduler.job;

import cafe.jeffrey.hub.core.manager.project.ProjectManager;
import cafe.jeffrey.hub.core.manager.workspace.WorkspacesManager;
import cafe.jeffrey.hub.core.project.repository.RepositoryStorage;
import cafe.jeffrey.hub.core.scheduler.JobContext;
import cafe.jeffrey.hub.core.scheduler.job.descriptor.SessionFileDetectorProjectJobDescriptor;
import cafe.jeffrey.hub.core.workspace.WorkspaceEventPublisher;
import cafe.jeffrey.shared.common.Json;
import cafe.jeffrey.shared.common.model.ProjectInfo;
import cafe.jeffrey.shared.common.model.repository.RecordingSession;
import cafe.jeffrey.shared.common.model.repository.RecordingStatus;
import cafe.jeffrey.shared.common.model.repository.RepositoryFile;
import cafe.jeffrey.shared.common.model.repository.SupportedRecordingFile;
import cafe.jeffrey.shared.common.model.workspace.WorkspaceEvent;
import cafe.jeffrey.shared.common.model.workspace.WorkspaceEventCreator;
import cafe.jeffrey.shared.common.model.workspace.WorkspaceEventType;
import cafe.jeffrey.shared.common.model.workspace.event.SessionFileCreatedEventContent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SessionFileDetectorProjectJobTest {

    private static final Instant NOW = Instant.parse("2026-02-20T12:00:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private static final Duration MAX_FILE_AGE = Duration.ofDays(7);
    private static final Duration SETTLE_THRESHOLD = Duration.ofSeconds(10);
    private static final int MAX_EVENTS_PER_TICK = 500;

    private static final String WORKSPACE_ID = "ws-1";
    private static final String SESSION_ID = "session-1";
    private static final String INSTANCE_ID = "inst-1";

    @Mock
    WorkspacesManager workspacesManager;

    @Mock
    RepositoryStorage.Factory storageFactory;

    @Mock
    RepositoryStorage storage;

    @Mock
    ProjectManager projectManager;

    @Mock
    WorkspaceEventPublisher publisher;

    @TempDir
    Path sessionDir;

    private SessionFileDetectorProjectJob job;

    @BeforeEach
    void setUp() {
        ProjectInfo projectInfo = new ProjectInfo(
                "proj-1", null, "my-project", null, null, WORKSPACE_ID, NOW, NOW, Map.of(), null);

        when(projectManager.info()).thenReturn(projectInfo);
        when(storageFactory.apply(any())).thenReturn(storage);

        job = new SessionFileDetectorProjectJob(
                workspacesManager, storageFactory, descriptor(MAX_EVENTS_PER_TICK),
                Duration.ofSeconds(30), FIXED_CLOCK, publisher);
    }

    private static SessionFileDetectorProjectJobDescriptor descriptor(int maxEventsPerTick) {
        return new SessionFileDetectorProjectJobDescriptor(MAX_FILE_AGE, SETTLE_THRESHOLD, maxEventsPerTick);
    }

    private void execute() {
        execute(MAX_EVENTS_PER_TICK);
    }

    private void execute(int maxEventsPerTick) {
        job.executeOnRepository(projectManager, storage, descriptor(maxEventsPerTick), JobContext.EMPTY);
    }

    /** Creates a real file so the mtime-based settle check has something to read. */
    private Path fileOnDisk(String name, Instant modifiedAt) {
        try {
            Path path = sessionDir.resolve(name);
            Files.writeString(path, "payload");
            Files.setLastModifiedTime(path, FileTime.from(modifiedAt));
            return path;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private RepositoryFile recording(String id, Instant createdAt, Long size, RecordingStatus status) {
        return new RepositoryFile(id, id + ".jfr", createdAt, size,
                SupportedRecordingFile.JFR, status, fileOnDisk(id + ".jfr", createdAt));
    }

    private RepositoryFile artifact(String name, Instant createdAt, Instant modifiedAt) {
        return new RepositoryFile(name, name, createdAt, 128L,
                SupportedRecordingFile.HEAP_DUMP, RecordingStatus.FINISHED, fileOnDisk(name, modifiedAt));
    }

    private RepositoryFile typed(
            String name, SupportedRecordingFile fileType, Instant createdAt) {

        return new RepositoryFile(name, name, createdAt, 64L,
                fileType, RecordingStatus.FINISHED, fileOnDisk(name, createdAt));
    }

    private void givenSession(Instant createdAt, Instant finishedAt, RepositoryFile... files) {
        RecordingSession withoutFiles = new RecordingSession(
                SESSION_ID, SESSION_ID, INSTANCE_ID, createdAt, finishedAt,
                finishedAt == null ? RecordingStatus.ACTIVE : RecordingStatus.FINISHED,
                sessionDir, null, List.of(), false);
        RecordingSession withFiles = new RecordingSession(
                SESSION_ID, SESSION_ID, INSTANCE_ID, createdAt, finishedAt,
                finishedAt == null ? RecordingStatus.ACTIVE : RecordingStatus.FINISHED,
                sessionDir, null, List.of(files), false);

        when(storage.listSessions(false)).thenReturn(List.of(withoutFiles));
        when(storage.singleSession(SESSION_ID, true)).thenReturn(Optional.of(withFiles));
    }

    private List<WorkspaceEvent> publishedEvents() {
        ArgumentCaptor<List<WorkspaceEvent>> captor = ArgumentCaptor.captor();
        verify(publisher).publishBatch(eq(WORKSPACE_ID), captor.capture());
        return captor.getValue();
    }

    @Nested
    class RecordingFiles {

        @Test
        void announcesFinishedRecording() {
            Instant createdAt = NOW.minusSeconds(600);
            givenSession(NOW.minusSeconds(1200), NOW.minusSeconds(60),
                    recording("chunk-1", createdAt, 4096L, RecordingStatus.FINISHED));

            execute();

            List<WorkspaceEvent> events = publishedEvents();
            assertEquals(1, events.size());
            WorkspaceEvent event = events.getFirst();
            SessionFileCreatedEventContent content =
                    Json.read(event.content(), SessionFileCreatedEventContent.class);

            assertAll(
                    () -> assertEquals(WorkspaceEventType.PROJECT_INSTANCE_SESSION_RECORDING_FILE_CREATED,
                            event.eventType()),
                    () -> assertEquals("chunk-1", event.originEventId(),
                            "The file id becomes originEventId, which is what the dedup key is built from"),
                    () -> assertEquals(WorkspaceEventCreator.SESSION_FILE_DETECTOR_JOB.name(), event.createdBy()),
                    () -> assertEquals(SESSION_ID, content.sessionId()),
                    () -> assertEquals(INSTANCE_ID, content.instanceId()),
                    () -> assertEquals(4096L, content.sizeBytes()));
        }

        @Test
        void skipsTheChunkStillBeingWritten() {
            // The repository storage marks the newest recording of a live session ACTIVE; that is
            // the only signal that async-profiler has not finished with it.
            givenSession(NOW.minusSeconds(600), null,
                    recording("chunk-live", NOW.minusSeconds(30), 1024L, RecordingStatus.ACTIVE),
                    recording("chunk-done", NOW.minusSeconds(300), 2048L, RecordingStatus.FINISHED));

            execute();

            assertEquals(List.of("chunk-done"),
                    publishedEvents().stream().map(WorkspaceEvent::originEventId).toList());
        }

        @Test
        void skipsEmptyRecording() {
            givenSession(NOW.minusSeconds(600), NOW.minusSeconds(60),
                    recording("chunk-empty", NOW.minusSeconds(300), 0L, RecordingStatus.FINISHED));

            execute();

            verify(publisher, never()).publishBatch(anyString(), any());
        }

        @Test
        void skipsRecordingWithUnknownSize() {
            givenSession(NOW.minusSeconds(600), NOW.minusSeconds(60),
                    recording("chunk-unsized", NOW.minusSeconds(300), null, RecordingStatus.FINISHED));

            execute();

            verify(publisher, never()).publishBatch(anyString(), any());
        }
    }

    @Nested
    class ArtifactFiles {

        @Test
        void announcesSettledArtifact() {
            Instant createdAt = NOW.minusSeconds(600);
            givenSession(NOW.minusSeconds(1200), NOW.minusSeconds(60),
                    artifact("heap.hprof", createdAt, NOW.minus(SETTLE_THRESHOLD).minusSeconds(5)));

            execute();

            List<WorkspaceEvent> events = publishedEvents();
            assertEquals(1, events.size());
            assertEquals(WorkspaceEventType.PROJECT_INSTANCE_SESSION_ARTIFACT_FILE_CREATED,
                    events.getFirst().eventType());
        }

        @Test
        void skipsArtifactStillBeingWritten() {
            // A heap dump has no ACTIVE marker, so only its advancing mtime reveals it is mid-write.
            givenSession(NOW.minusSeconds(1200), NOW.minusSeconds(60),
                    artifact("heap.hprof", NOW.minusSeconds(600), NOW.minusSeconds(1)));

            execute();

            verify(publisher, never()).publishBatch(anyString(), any());
        }

        @Test
        void announcesZeroByteArtifact() {
            Instant settled = NOW.minus(SETTLE_THRESHOLD).minusSeconds(5);
            RepositoryFile emptyLog = new RepositoryFile(
                    "app.log", "app.log", NOW.minusSeconds(600), 0L,
                    SupportedRecordingFile.APP_LOG, RecordingStatus.FINISHED,
                    fileOnDisk("app.log", settled));
            givenSession(NOW.minusSeconds(1200), NOW.minusSeconds(60), emptyLog);

            execute();

            assertEquals(1, publishedEvents().size(),
                    "An empty log is a real observation, unlike an empty recording chunk");
        }
    }

    @Nested
    class IgnoredCategories {

        @Test
        void skipsTemporaryFiles() {
            givenSession(NOW.minusSeconds(600), NOW.minusSeconds(60),
                    typed("profile-20260220-115000.jfr.1~", SupportedRecordingFile.ASPROF_TEMP,
                            NOW.minusSeconds(300)));

            execute();

            verify(publisher, never()).publishBatch(anyString(), any());
        }
    }

    @Nested
    class AgeWindow {

        @Test
        void skipsFileOlderThanMaxFileAge() {
            // Beyond this bound a file may outlive its dedup row, so it must never be a candidate.
            Instant tooOld = NOW.minus(MAX_FILE_AGE).minusSeconds(60);
            givenSession(NOW.minusSeconds(600), NOW.minusSeconds(60),
                    recording("chunk-ancient", tooOld, 4096L, RecordingStatus.FINISHED));

            execute();

            verify(publisher, never()).publishBatch(anyString(), any());
        }

        @Test
        void skipsSessionFinishedBeyondTheWindow() {
            Instant longAgo = NOW.minus(MAX_FILE_AGE).minusSeconds(3600);
            RecordingSession stale = new RecordingSession(
                    SESSION_ID, SESSION_ID, INSTANCE_ID, longAgo, longAgo.plusSeconds(60),
                    RecordingStatus.FINISHED, sessionDir, null, List.of(), false);
            when(storage.listSessions(false)).thenReturn(List.of(stale));

            execute();

            assertAll(
                    () -> verify(publisher, never()).publishBatch(anyString(), any()),
                    () -> verify(storage, never()).singleSession(anyString(), eq(true)));
        }

        @Test
        void keepsUnfinishedSessionRegardlessOfCreationTime() {
            Instant longAgo = NOW.minus(MAX_FILE_AGE).minusSeconds(3600);
            RecordingSession longRunning = new RecordingSession(
                    SESSION_ID, SESSION_ID, INSTANCE_ID, longAgo, null,
                    RecordingStatus.ACTIVE, sessionDir, null, List.of(), false);
            RecordingSession withFiles = new RecordingSession(
                    SESSION_ID, SESSION_ID, INSTANCE_ID, longAgo, null,
                    RecordingStatus.ACTIVE, sessionDir, null,
                    List.of(recording("chunk-recent", NOW.minusSeconds(120), 4096L, RecordingStatus.FINISHED)),
                    false);
            when(storage.listSessions(false)).thenReturn(List.of(longRunning));
            when(storage.singleSession(SESSION_ID, true)).thenReturn(Optional.of(withFiles));

            execute();

            assertEquals(1, publishedEvents().size(),
                    "A long-running session must keep producing events for its fresh chunks");
        }
    }

    @Nested
    class Deduplication {

        @Test
        void compressedAndRawChunkYieldOneEvent() {
            // During the compression window both files exist and share one id, because the id has
            // the recording extension stripped.
            String sharedId = "profile-20260220-115000";
            Instant createdAt = NOW.minusSeconds(300);
            RepositoryFile raw = new RepositoryFile(sharedId, sharedId + ".jfr", createdAt, 4096L,
                    SupportedRecordingFile.JFR, RecordingStatus.FINISHED,
                    fileOnDisk(sharedId + ".jfr", createdAt));
            RepositoryFile compressed = new RepositoryFile(sharedId, sharedId + ".jfr.lz4", createdAt, 1024L,
                    SupportedRecordingFile.JFR_LZ4, RecordingStatus.FINISHED,
                    fileOnDisk(sharedId + ".jfr.lz4", createdAt));
            givenSession(NOW.minusSeconds(1200), NOW.minusSeconds(60), raw, compressed);

            execute();

            assertEquals(1, publishedEvents().size(),
                    "One file identity must not produce two events within a single tick");
        }
    }

    @Nested
    class Bounds {

        @Test
        void respectsMaxEventsPerTick() {
            givenSession(NOW.minusSeconds(1200), NOW.minusSeconds(60),
                    recording("chunk-1", NOW.minusSeconds(500), 4096L, RecordingStatus.FINISHED),
                    recording("chunk-2", NOW.minusSeconds(400), 4096L, RecordingStatus.FINISHED),
                    recording("chunk-3", NOW.minusSeconds(300), 4096L, RecordingStatus.FINISHED));

            execute(2);

            assertEquals(2, publishedEvents().size());
        }

        @Test
        void publishesNothingWhenNoSessions() {
            when(storage.listSessions(false)).thenReturn(List.of());

            execute();

            verify(publisher, never()).publishBatch(anyString(), any());
        }

        @Test
        void toleratesSessionDisappearingBetweenListAndRead() {
            RecordingSession session = new RecordingSession(
                    SESSION_ID, SESSION_ID, INSTANCE_ID, NOW.minusSeconds(600), null,
                    RecordingStatus.ACTIVE, sessionDir, null, List.of(), false);
            when(storage.listSessions(false)).thenReturn(List.of(session));
            when(storage.singleSession(SESSION_ID, true)).thenReturn(Optional.empty());

            execute();

            verify(publisher, never()).publishBatch(anyString(), any());
        }
    }

    @Nested
    class DescriptorValidation {

        @Test
        void roundTripsThroughParams() {
            var original = descriptor(250);

            var restored = SessionFileDetectorProjectJobDescriptor.of(original.params());

            assertEquals(original, restored);
        }

        @Test
        void parsesOperatorDurationNotation() {
            var restored = SessionFileDetectorProjectJobDescriptor.of(Map.of(
                    "max-file-age", "7d",
                    "artifact-settle-threshold", "10s",
                    "max-events-per-tick", "500"));

            assertAll(
                    () -> assertEquals(Duration.ofDays(7), restored.maxFileAge()),
                    () -> assertEquals(Duration.ofSeconds(10), restored.artifactSettleThreshold()),
                    () -> assertEquals(500, restored.maxEventsPerTick()));
        }

        @Test
        void rejectsNonPositiveValues() {
            assertAll(
                    () -> assertTrue(throwsIllegalArgument(() ->
                            new SessionFileDetectorProjectJobDescriptor(Duration.ZERO, SETTLE_THRESHOLD, 10))),
                    () -> assertTrue(throwsIllegalArgument(() ->
                            new SessionFileDetectorProjectJobDescriptor(
                                    MAX_FILE_AGE, Duration.ofSeconds(-1), 10))),
                    () -> assertTrue(throwsIllegalArgument(() ->
                            new SessionFileDetectorProjectJobDescriptor(MAX_FILE_AGE, SETTLE_THRESHOLD, 0))));
        }

        private static boolean throwsIllegalArgument(Runnable runnable) {
            try {
                runnable.run();
                return false;
            } catch (IllegalArgumentException e) {
                return true;
            }
        }
    }
}
