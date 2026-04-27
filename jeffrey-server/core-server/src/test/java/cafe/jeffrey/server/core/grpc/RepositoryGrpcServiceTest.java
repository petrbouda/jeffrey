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

package cafe.jeffrey.server.core.grpc;

import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import cafe.jeffrey.server.api.v1.*;
import cafe.jeffrey.server.core.manager.RepositoryManager;
import cafe.jeffrey.server.persistence.api.SessionWithRepository;
import cafe.jeffrey.server.persistence.api.ProjectRepository;
import cafe.jeffrey.server.persistence.api.ServerPlatformRepositories;
import cafe.jeffrey.shared.common.model.ProjectInfo;
import cafe.jeffrey.shared.common.model.repository.RepositoryFile;
import cafe.jeffrey.shared.common.model.repository.RepositoryStatistics;
import cafe.jeffrey.shared.common.model.repository.RepositoryStatistics.FileTypeStats;
import cafe.jeffrey.shared.common.model.repository.SupportedRecordingFile;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RepositoryGrpcServiceTest {

    private static final String PROJECT_ID = "proj-1";
    private static final String SESSION_ID = "session-1";
    private static final Instant FIXED_TIME = Instant.parse("2026-01-15T10:00:00Z");

    private Server server;
    private ManagedChannel channel;

    private RepositoryServiceGrpc.RepositoryServiceBlockingStub startServer(
            RepositoryGrpcService service) throws IOException {

        String name = InProcessServerBuilder.generateName();
        server = InProcessServerBuilder.forName(name)
                .directExecutor()
                .addService(service)
                .build()
                .start();
        channel = InProcessChannelBuilder.forName(name)
                .directExecutor()
                .build();
        return RepositoryServiceGrpc.newBlockingStub(channel);
    }

    @AfterEach
    void shutdown() {
        if (channel != null) {
            channel.shutdownNow();
        }
        if (server != null) {
            server.shutdownNow();
        }
    }

    // ========== ListSessions ==========

    @Nested
    class ListSessions {

        @Test
        void returnsSessionList() throws Exception {
            var repoManager = mock(RepositoryManager.class);
            when(repoManager.listRecordingSessions(true)).thenReturn(List.of(
                    new cafe.jeffrey.shared.common.model.repository.RecordingSession(
                            SESSION_ID, "session-name", "inst-1",
                            FIXED_TIME, null,
                            cafe.jeffrey.shared.common.model.repository.RecordingStatus.ACTIVE,
                            null, null,
                            List.of(new RepositoryFile(
                                    "file-1", "recording.jfr", FIXED_TIME, 1024L,
                                    SupportedRecordingFile.JFR,
                                    cafe.jeffrey.shared.common.model.repository.RecordingStatus.FINISHED,
                                    null))),
                    new cafe.jeffrey.shared.common.model.repository.RecordingSession(
                            "session-2", "finished-session", null,
                            FIXED_TIME, FIXED_TIME.plusSeconds(3600),
                            cafe.jeffrey.shared.common.model.repository.RecordingStatus.FINISHED,
                            null, null,
                            List.of())
            ));

            var stub = startServer(serviceWithProject(repoManager));

            ListSessionsResponse response = stub.listSessions(
                    ListSessionsRequest.newBuilder()
                            .setProjectId(PROJECT_ID)
                            .build());

            assertEquals(2, response.getSessionsCount());

            cafe.jeffrey.server.api.v1.RecordingSession first = response.getSessions(0);
            assertEquals(SESSION_ID, first.getId());
            assertEquals("session-name", first.getName());
            assertEquals("inst-1", first.getInstanceId());
            assertEquals(FIXED_TIME.toEpochMilli(), first.getCreatedAt());
            assertEquals(RecordingStatus.RECORDING_STATUS_ACTIVE, first.getStatus());
            assertFalse(first.hasFinishedAt());
            assertEquals(1, first.getFilesCount());

            cafe.jeffrey.server.api.v1.RepositoryFile protoFile = first.getFiles(0);
            assertEquals("file-1", protoFile.getId());
            assertEquals("recording.jfr", protoFile.getName());
            assertEquals(1024L, protoFile.getSize());
            assertEquals(RecordingStatus.RECORDING_STATUS_FINISHED, protoFile.getStatus());

            cafe.jeffrey.server.api.v1.RecordingSession second = response.getSessions(1);
            assertEquals("session-2", second.getId());
            assertEquals(RecordingStatus.RECORDING_STATUS_FINISHED, second.getStatus());
            assertTrue(second.hasFinishedAt());
            assertEquals(FIXED_TIME.plusSeconds(3600).toEpochMilli(), second.getFinishedAt());
        }

        @Test
        void returnsEmptyListWhenNoSessions() throws Exception {
            var repoManager = mock(RepositoryManager.class);
            when(repoManager.listRecordingSessions(true)).thenReturn(List.of());

            var stub = startServer(serviceWithProject(repoManager));

            ListSessionsResponse response = stub.listSessions(
                    ListSessionsRequest.newBuilder()
                            .setProjectId(PROJECT_ID)
                            .build());

            assertEquals(0, response.getSessionsCount());
        }

        @Test
        void projectNotFound_returnsNotFound() throws Exception {
            var stub = startServer(serviceWithNoProject());

            StatusRuntimeException ex = assertThrows(StatusRuntimeException.class, () ->
                    stub.listSessions(
                            ListSessionsRequest.newBuilder()
                                    .setProjectId("non-existent")
                                    .build()));

            assertEquals(Status.Code.NOT_FOUND, ex.getStatus().getCode());
        }
    }

    // ========== GetSession ==========

    @Nested
    class GetSession {

        @Test
        void returnsSession() throws Exception {
            var repoManager = mock(RepositoryManager.class);
            when(repoManager.findRecordingSessions(SESSION_ID)).thenReturn(Optional.of(
                    new cafe.jeffrey.shared.common.model.repository.RecordingSession(
                            SESSION_ID, "my-session", "inst-1",
                            FIXED_TIME, FIXED_TIME.plusSeconds(600),
                            cafe.jeffrey.shared.common.model.repository.RecordingStatus.FINISHED,
                            null, null,
                            List.of())
            ));

            var stub = startServer(serviceWithSession(repoManager));

            GetSessionResponse response = stub.getSession(
                    GetSessionRequest.newBuilder()
                            .setSessionId(SESSION_ID)
                            .build());

            cafe.jeffrey.server.api.v1.RecordingSession session = response.getSession();
            assertEquals(SESSION_ID, session.getId());
            assertEquals("my-session", session.getName());
            assertEquals("inst-1", session.getInstanceId());
            assertEquals(RecordingStatus.RECORDING_STATUS_FINISHED, session.getStatus());
            assertTrue(session.hasFinishedAt());
            assertEquals(FIXED_TIME.plusSeconds(600).toEpochMilli(), session.getFinishedAt());
        }

        @Test
        void sessionNotFound_returnsNotFound() throws Exception {
            var platformRepositories = mock(ServerPlatformRepositories.class);
            when(platformRepositories.findSessionWithRepositoryById("non-existent")).thenReturn(Optional.empty());

            var repoManagerFactory = mock(RepositoryManager.Factory.class);

            var stub = startServer(new RepositoryGrpcService(platformRepositories, repoManagerFactory));

            StatusRuntimeException ex = assertThrows(StatusRuntimeException.class, () ->
                    stub.getSession(
                            GetSessionRequest.newBuilder()
                                    .setSessionId("non-existent")
                                    .build()));

            assertEquals(Status.Code.NOT_FOUND, ex.getStatus().getCode());
        }
    }

    // ========== GetRepositoryStatistics ==========

    @Nested
    class GetRepositoryStatistics {

        @Test
        void returnsStatistics() throws Exception {
            var repoManager = mock(RepositoryManager.class);
            when(repoManager.calculateRepositoryStatistics()).thenReturn(
                    new RepositoryStatistics(
                            5,
                            cafe.jeffrey.shared.common.model.repository.RecordingStatus.ACTIVE,
                            FIXED_TIME.toEpochMilli(),
                            1_000_000L,
                            25,
                            500_000L,
                            new FileTypeStats(10, 800_000L),
                            new FileTypeStats(2, 100_000L),
                            new FileTypeStats(3, 50_000L),
                            new FileTypeStats(5, 30_000L),
                            new FileTypeStats(1, 10_000L),
                            new FileTypeStats(4, 10_000L)
                    )
            );

            var stub = startServer(serviceWithProject(repoManager));

            GetRepositoryStatisticsResponse response = stub.getRepositoryStatistics(
                    GetRepositoryStatisticsRequest.newBuilder()
                            .setProjectId(PROJECT_ID)
                            .build());

            assertEquals(5, response.getTotalSessions());
            assertEquals(RecordingStatus.RECORDING_STATUS_ACTIVE, response.getSessionStatus());
            assertEquals(FIXED_TIME.toEpochMilli(), response.getLastActivityTime());
            assertEquals(1_000_000L, response.getTotalSize());
            assertEquals(25, response.getTotalFiles());
            assertEquals(500_000L, response.getBiggestSessionSize());
            assertEquals(10, response.getJfrFiles());
            assertEquals(800_000L, response.getJfrSize());
            assertEquals(2, response.getHeapDumpFiles());
            assertEquals(100_000L, response.getHeapDumpSize());
            assertEquals(3, response.getLogFiles());
            assertEquals(50_000L, response.getLogSize());
            assertEquals(5, response.getAppLogFiles());
            assertEquals(30_000L, response.getAppLogSize());
            assertEquals(1, response.getErrorLogFiles());
            assertEquals(10_000L, response.getErrorLogSize());
            assertEquals(4, response.getOtherFiles());
            assertEquals(10_000L, response.getOtherSize());
        }

        @Test
        void projectNotFound_returnsNotFound() throws Exception {
            var stub = startServer(serviceWithNoProject());

            StatusRuntimeException ex = assertThrows(StatusRuntimeException.class, () ->
                    stub.getRepositoryStatistics(
                            GetRepositoryStatisticsRequest.newBuilder()
                                    .setProjectId("non-existent")
                                    .build()));

            assertEquals(Status.Code.NOT_FOUND, ex.getStatus().getCode());
        }
    }

    // ========== DeleteSession ==========

    @Nested
    class DeleteSession {

        @Test
        void deletesSessionSuccessfully() throws Exception {
            var repoManager = mock(RepositoryManager.class);

            var stub = startServer(serviceWithSession(repoManager));

            DeleteSessionResponse response = stub.deleteSession(
                    DeleteSessionRequest.newBuilder()
                            .setSessionId(SESSION_ID)
                            .build());

            assertNotNull(response);
            verify(repoManager).deleteRecordingSession(
                    SESSION_ID,
                    cafe.jeffrey.shared.common.model.workspace.WorkspaceEventCreator.MANUAL);
        }

        @Test
        void sessionNotFound_returnsNotFound() throws Exception {
            var platformRepositories = mock(ServerPlatformRepositories.class);
            when(platformRepositories.findSessionWithRepositoryById("non-existent")).thenReturn(Optional.empty());

            var repoManagerFactory = mock(RepositoryManager.Factory.class);

            var stub = startServer(new RepositoryGrpcService(platformRepositories, repoManagerFactory));

            StatusRuntimeException ex = assertThrows(StatusRuntimeException.class, () ->
                    stub.deleteSession(
                            DeleteSessionRequest.newBuilder()
                                    .setSessionId("non-existent")
                                    .build()));

            assertEquals(Status.Code.NOT_FOUND, ex.getStatus().getCode());
        }
    }

    // ========== DeleteFilesInSession ==========

    @Nested
    class DeleteFilesInSession {

        @Test
        void deletesFilesSuccessfully() throws Exception {
            var repoManager = mock(RepositoryManager.class);

            var stub = startServer(serviceWithSession(repoManager));

            List<String> fileIds = List.of("file-1", "file-2", "file-3");

            DeleteFilesInSessionResponse response = stub.deleteFilesInSession(
                    DeleteFilesInSessionRequest.newBuilder()
                            .setSessionId(SESSION_ID)
                            .addAllFileIds(fileIds)
                            .build());

            assertNotNull(response);
            verify(repoManager).deleteFilesInSession(SESSION_ID, fileIds);
        }

        @Test
        void sessionNotFound_returnsNotFound() throws Exception {
            var platformRepositories = mock(ServerPlatformRepositories.class);
            when(platformRepositories.findSessionWithRepositoryById("non-existent")).thenReturn(Optional.empty());

            var repoManagerFactory = mock(RepositoryManager.Factory.class);

            var stub = startServer(new RepositoryGrpcService(platformRepositories, repoManagerFactory));

            StatusRuntimeException ex = assertThrows(StatusRuntimeException.class, () ->
                    stub.deleteFilesInSession(
                            DeleteFilesInSessionRequest.newBuilder()
                                    .setSessionId("non-existent")
                                    .addFileIds("file-1")
                                    .build()));

            assertEquals(Status.Code.NOT_FOUND, ex.getStatus().getCode());
        }
    }

    // ========== Helpers ==========

    private static final ProjectInfo TEST_PROJECT_INFO =
            new ProjectInfo(PROJECT_ID, null, null, null, null, null, null, null, null, null);

    /**
     * Creates a service where {@code repositoryManagerForProject(PROJECT_ID)} succeeds.
     */
    private RepositoryGrpcService serviceWithProject(RepositoryManager repoManager) {
        var projectRepo = mock(ProjectRepository.class);
        when(projectRepo.find()).thenReturn(Optional.of(TEST_PROJECT_INFO));

        var platformRepositories = mock(ServerPlatformRepositories.class);
        when(platformRepositories.newProjectRepository(PROJECT_ID)).thenReturn(projectRepo);

        var repoManagerFactory = mock(RepositoryManager.Factory.class);
        when(repoManagerFactory.apply(TEST_PROJECT_INFO)).thenReturn(repoManager);

        return new RepositoryGrpcService(platformRepositories, repoManagerFactory);
    }

    /**
     * Creates a service where {@code repositoryManagerForSession(SESSION_ID)} succeeds.
     */
    private RepositoryGrpcService serviceWithSession(RepositoryManager repoManager) {
        var sessionWithRepo = mock(SessionWithRepository.class);
        when(sessionWithRepo.projectInfo()).thenReturn(TEST_PROJECT_INFO);

        var platformRepositories = mock(ServerPlatformRepositories.class);
        when(platformRepositories.findSessionWithRepositoryById(SESSION_ID)).thenReturn(Optional.of(sessionWithRepo));

        var repoManagerFactory = mock(RepositoryManager.Factory.class);
        when(repoManagerFactory.apply(TEST_PROJECT_INFO)).thenReturn(repoManager);

        return new RepositoryGrpcService(platformRepositories, repoManagerFactory);
    }

    /**
     * Creates a service where {@code newProjectRepository("non-existent").find()} returns empty.
     */
    private RepositoryGrpcService serviceWithNoProject() {
        var projectRepo = mock(ProjectRepository.class);
        when(projectRepo.find()).thenReturn(Optional.empty());

        var platformRepositories = mock(ServerPlatformRepositories.class);
        when(platformRepositories.newProjectRepository("non-existent")).thenReturn(projectRepo);

        var repoManagerFactory = mock(RepositoryManager.Factory.class);

        return new RepositoryGrpcService(platformRepositories, repoManagerFactory);
    }
}
