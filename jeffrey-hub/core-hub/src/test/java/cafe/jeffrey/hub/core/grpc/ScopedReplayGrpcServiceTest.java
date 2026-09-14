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

package cafe.jeffrey.hub.core.grpc;

import cafe.jeffrey.hub.api.v1.EventBatch;
import cafe.jeffrey.hub.api.v1.EventStreamingServiceGrpc;
import cafe.jeffrey.hub.api.v1.ReplayStatus;
import cafe.jeffrey.hub.api.v1.ReplayStreamingRequest;
import cafe.jeffrey.hub.core.HubJeffreyDirs;
import cafe.jeffrey.hub.core.project.repository.RepositoryStorage;
import cafe.jeffrey.hub.core.streaming.ReplayStreamingManager;
import cafe.jeffrey.hub.core.streaming.ScopedReplaySource;
import cafe.jeffrey.hub.persistence.api.HubPlatformRepositories;
import cafe.jeffrey.hub.persistence.api.ProjectRepository;
import cafe.jeffrey.shared.common.filesystem.FileSystemUtils;
import cafe.jeffrey.shared.common.model.ProjectInfo;
import cafe.jeffrey.shared.common.model.repository.RecordingSession;
import cafe.jeffrey.shared.common.model.repository.RecordingStatus;
import cafe.jeffrey.shared.common.model.repository.RepositoryFile;
import io.grpc.Status;
import io.grpc.stub.ServerCallStreamObserver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ScopedReplayGrpcServiceTest {
    @Test
    void scopedReplayNeverUsesGlobalSessionLookupOrCompressingStorageAndReportsCorruption(@TempDir Path temp) throws Exception {
        Path recording = temp.resolve("raw.jfr");
        Files.writeString(recording, "invalid recording");
        HubPlatformRepositories repositories = mock(HubPlatformRepositories.class);
        ProjectRepository projects = mock(ProjectRepository.class);
        ProjectInfo project = new ProjectInfo("project", null, null, null, null, "workspace", null, null, null, null);
        when(repositories.newProjectRepository("project")).thenReturn(projects);
        when(projects.find()).thenReturn(Optional.of(project));
        RepositoryStorage storage = mock(RepositoryStorage.class);
        RepositoryFile file = mock(RepositoryFile.class);
        when(file.isRecordingChunk()).thenReturn(true);
        when(file.isFinished()).thenReturn(true);
        when(file.filePath()).thenReturn(recording);
        when(file.createdAt()).thenReturn(Instant.EPOCH);
        RecordingSession session = sessionOf("shared-session", List.of(file));
        when(storage.singleSession("shared-session", true)).thenReturn(Optional.of(session));
        HubJeffreyDirs dirs = mock(HubJeffreyDirs.class);
        when(dirs.temp()).thenReturn(temp.resolve("scratch"));
        EventStreamingGrpcService service = new EventStreamingGrpcService(dirs, repositories,
                new ReplayStreamingManager(), ignored -> storage,
                new ScopedReplaySource(repositories, ignored -> storage, dirs));
        List<EventBatch> batches = new ArrayList<>();
        CompletableFuture<Void> done = new CompletableFuture<>();
        ServerCallStreamObserver<EventBatch> observer = observer(batches, done);
        service.scopedReplayStreaming(request("workspace"), observer);
        done.get(5, TimeUnit.SECONDS);
        assertEquals("workspace", batches.getFirst().getReplayStatus().getWorkspaceId());
        assertEquals("project", batches.getFirst().getReplayStatus().getProjectId());
        assertTrue(batches.getLast().getReplayStatus().getTerminal());
        assertTrue(batches.getLast().getReplayStatus().getSourceErrors() > 0);
        assertEquals("invalid recording", Files.readString(recording));
        assertFalse(Files.exists(temp.resolve("raw.jfr.lz4")));
        verify(repositories, never()).findSessionWithRepositoryById(any());
        verify(storage, never()).finishedChunks(any());
    }

    @Test
    void rejectsWorkspaceMismatchBeforeReadingFiles(@TempDir Path temp) {
        HubPlatformRepositories repositories = mock(HubPlatformRepositories.class);
        ProjectRepository projects = mock(ProjectRepository.class);
        when(repositories.newProjectRepository("project")).thenReturn(projects);
        when(projects.find()).thenReturn(Optional.of(new ProjectInfo("project", null, null, null, null,
                "real-workspace", null, null, null, null)));
        RepositoryStorage.Factory storage = mock(RepositoryStorage.Factory.class);
        EventStreamingGrpcService service = new EventStreamingGrpcService(mock(HubJeffreyDirs.class), repositories,
                mock(ReplayStreamingManager.class), storage,
                new ScopedReplaySource(repositories, storage, mock(HubJeffreyDirs.class)));
        CompletableFuture<Void> done = new CompletableFuture<>();
        service.scopedReplayStreaming(request("wrong-workspace"), observer(new ArrayList<>(), done));
        assertTrue(done.isCompletedExceptionally());
        done.exceptionally(error -> {
            assertEquals(Status.Code.NOT_FOUND, Status.fromThrowable(error).getCode());
            return null;
        }).join();
        verify(storage, never()).apply(any());
    }

    /**
     * The same service behind a real in-process transport, so the status codes asserted here are the
     * ones a client stub decodes rather than the ones a mocked observer was handed.
     */
    @Nested
    class OverInProcessTransport {

        private static final String SESSION_ID = "shared-session";
        private static final String WORKSPACE_ID = "workspace";
        private static final String PROJECT_ID = "project";
        private static final String EVENT_TYPE = "jdk.CPULoad";

        private InProcessGrpcServer grpc;

        @AfterEach
        void shutdown() {
            if (grpc != null) {
                grpc.close();
            }
        }

        @Test
        void blankWorkspaceIsInvalidArgument(@TempDir Path temp) throws Exception {
            var stub = start(scopedService(temp, mock(RepositoryStorage.class)));
            var observer = new TestStreamObserver<EventBatch>();

            stub.scopedReplayStreaming(request(""), observer);

            assertTrue(observer.errorLatch.await(5, TimeUnit.SECONDS));
            assertCode(Status.Code.INVALID_ARGUMENT, observer.error);
        }

        @Test
        void unknownSessionIsNotFound(@TempDir Path temp) throws Exception {
            RepositoryStorage storage = mock(RepositoryStorage.class);
            when(storage.singleSession(SESSION_ID, true)).thenReturn(Optional.empty());
            var stub = start(scopedService(temp, storage));
            var observer = new TestStreamObserver<EventBatch>();

            stub.scopedReplayStreaming(request(WORKSPACE_ID), observer);

            assertTrue(observer.errorLatch.await(5, TimeUnit.SECONDS));
            assertCode(Status.Code.NOT_FOUND, observer.error);
        }

        @Test
        void sessionWithoutFinishedFilesCompletesEmptyWithCoverage(@TempDir Path temp) throws Exception {
            // The session exists but is still writing its first file: the activity scan admits this
            // scope and counts zero events, and replay must say the same rather than NOT_FOUND.
            RepositoryFile unfinished = mock(RepositoryFile.class);
            when(unfinished.isRecordingChunk()).thenReturn(true);
            when(unfinished.isFinished()).thenReturn(false);
            var stub = start(scopedService(temp, storageWithSession(List.of(unfinished))));
            var observer = new TestStreamObserver<EventBatch>();

            stub.scopedReplayStreaming(request(WORKSPACE_ID), observer);

            assertTrue(observer.completeLatch.await(5, TimeUnit.SECONDS), "Empty replay should complete");
            assertNull(observer.error, "Empty replay must not be an error");
            assertEquals(2, observer.messages.size(), "Exactly the acknowledgement and the terminal status");
            assertAcknowledgement(observer.messages.getFirst());
            assertTerminal(observer.messages.getLast(), 0);
            assertEquals(0, totalEvents(observer));
        }

        @Test
        void replaysEventsFromFinishedFile(@TempDir Path temp) throws Exception {
            RepositoryFile finished = mock(RepositoryFile.class);
            when(finished.isRecordingChunk()).thenReturn(true);
            when(finished.isFinished()).thenReturn(true);
            when(finished.createdAt()).thenReturn(Instant.EPOCH);
            when(finished.filePath()).thenReturn(FileSystemUtils.classpathPath("jfrs/profile-1.jfr"));
            var stub = start(scopedService(temp, storageWithSession(List.of(finished))));
            var observer = new TestStreamObserver<EventBatch>();

            stub.scopedReplayStreaming(request(WORKSPACE_ID), observer);

            assertTrue(observer.completeLatch.await(30, TimeUnit.SECONDS), "Replay should complete");
            assertNull(observer.error);
            assertAcknowledgement(observer.messages.getFirst());
            assertTerminal(observer.messages.getLast(), 0);
            assertTrue(totalEvents(observer) > 0, "Should receive events");
            observer.messages.stream()
                    .flatMap(batch -> batch.getEventsList().stream())
                    .forEach(event -> assertEquals(EVENT_TYPE, event.getEventType()));
        }

        private EventStreamingServiceGrpc.EventStreamingServiceStub start(EventStreamingGrpcService service) {
            grpc = InProcessGrpcServer.start(service);
            return EventStreamingServiceGrpc.newStub(grpc.channel());
        }

        private static EventStreamingGrpcService scopedService(Path temp, RepositoryStorage storage) {
            HubPlatformRepositories repositories = mock(HubPlatformRepositories.class);
            ProjectRepository projects = mock(ProjectRepository.class);
            when(repositories.newProjectRepository(PROJECT_ID)).thenReturn(projects);
            when(projects.find()).thenReturn(Optional.of(new ProjectInfo(
                    PROJECT_ID, null, null, null, null, WORKSPACE_ID, null, null, null, null)));
            HubJeffreyDirs dirs = new HubJeffreyDirs(temp);
            return new EventStreamingGrpcService(dirs, repositories, new ReplayStreamingManager(),
                    ignored -> storage, new ScopedReplaySource(repositories, ignored -> storage, dirs));
        }

        private static RepositoryStorage storageWithSession(List<RepositoryFile> files) {
            RecordingSession session = sessionOf(SESSION_ID, files);
            RepositoryStorage storage = mock(RepositoryStorage.class);
            when(storage.singleSession(SESSION_ID, true)).thenReturn(Optional.of(session));
            return storage;
        }

        private static void assertAcknowledgement(EventBatch batch) {
            ReplayStatus status = batch.getReplayStatus();
            assertEquals(WORKSPACE_ID, status.getWorkspaceId());
            assertEquals(PROJECT_ID, status.getProjectId());
            assertFalse(status.getTerminal());
            assertEquals(0, batch.getEventsCount());
        }

        private static void assertTerminal(EventBatch batch, long sourceErrors) {
            ReplayStatus status = batch.getReplayStatus();
            assertTrue(status.getTerminal());
            assertEquals(sourceErrors, status.getSourceErrors());
            assertEquals(0, batch.getEventsCount());
        }

        private static long totalEvents(TestStreamObserver<EventBatch> observer) {
            return observer.messages.stream().mapToInt(EventBatch::getEventsCount).sum();
        }

        private static void assertCode(Status.Code expected, Throwable error) {
            assertNotNull(error, "Expected an error");
            assertEquals(expected, Status.fromThrowable(error).getCode());
        }
    }

    private static ReplayStreamingRequest request(String workspace) {
        return ReplayStreamingRequest.newBuilder().setSessionId("shared-session")
                .setWorkspaceId(workspace).setProjectId("project").addEventTypes("jdk.CPULoad").build();
    }

    @SuppressWarnings("unchecked")
    private static ServerCallStreamObserver<EventBatch> observer(List<EventBatch> batches, CompletableFuture<Void> done) {
        ServerCallStreamObserver<EventBatch> observer = mock(ServerCallStreamObserver.class);
        when(observer.isReady()).thenReturn(true);
        doAnswer(call -> {
            batches.add(call.getArgument(0));
            return null;
        }).when(observer).onNext(any());
        doAnswer(call -> {
            done.complete(null);
            return null;
        }).when(observer).onCompleted();
        doAnswer(call -> {
            done.completeExceptionally(call.getArgument(0));
            return null;
        }).when(observer).onError(any());
        return observer;
    }

    /**
     * A real session over mocked files, so {@code finishedChunks()} — the one place the replay
     * reads "the session's recording" from — runs for real.
     */
    private static RecordingSession sessionOf(String id, List<RepositoryFile> files) {
        return new RecordingSession(id, id, null, Instant.EPOCH, null, RecordingStatus.FINISHED, null, files, false);
    }
}
