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
import cafe.jeffrey.hub.api.v1.ReplayStreamingRequest;
import cafe.jeffrey.hub.core.HubJeffreyDirs;
import cafe.jeffrey.hub.core.project.repository.RepositoryStorage;
import cafe.jeffrey.hub.core.streaming.ReplayStreamingManager;
import cafe.jeffrey.hub.core.streaming.ScopedReplaySource;
import cafe.jeffrey.hub.persistence.api.HubPlatformRepositories;
import cafe.jeffrey.hub.persistence.api.ProjectRepository;
import cafe.jeffrey.shared.common.model.ProjectInfo;
import cafe.jeffrey.shared.common.model.repository.RecordingSession;
import cafe.jeffrey.shared.common.model.repository.RepositoryFile;
import io.grpc.Status;
import io.grpc.stub.ServerCallStreamObserver;
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
        RecordingSession session = mock(RecordingSession.class);
        RepositoryFile file = mock(RepositoryFile.class);
        when(file.isRecordingFile()).thenReturn(true);
        when(file.isFinished()).thenReturn(true);
        when(file.filePath()).thenReturn(recording);
        when(file.createdAt()).thenReturn(Instant.EPOCH);
        when(session.files()).thenReturn(List.of(file));
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
        verify(storage, never()).recordings(any(), any());
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
}
