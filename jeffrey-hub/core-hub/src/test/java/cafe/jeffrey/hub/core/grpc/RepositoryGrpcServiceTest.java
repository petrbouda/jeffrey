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

package cafe.jeffrey.hub.core.grpc;

import cafe.jeffrey.hub.core.project.repository.SessionDetail;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import cafe.jeffrey.hub.api.v1.*;
import cafe.jeffrey.hub.core.manager.RepositoryManager;
import cafe.jeffrey.hub.persistence.api.SessionWithRepository;
import cafe.jeffrey.hub.persistence.api.ProjectRepository;
import cafe.jeffrey.hub.persistence.api.HubPlatformRepositories;
import cafe.jeffrey.hub.model.ProjectInfo;
import cafe.jeffrey.hub.model.repository.RecordingSessionFilter;
import cafe.jeffrey.hub.model.repository.RepositoryFile;
import cafe.jeffrey.hub.model.repository.RepositoryStatistics;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RepositoryGrpcServiceTest {

    private static final String PROJECT_ID = "proj-1";
    private static final String SESSION_ID = "session-1";
    private static final Instant FIXED_TIME = Instant.parse("2026-01-15T10:00:00Z");

    private InProcessGrpcServer grpc;

    private RepositoryServiceGrpc.RepositoryServiceBlockingStub startServer(
            RepositoryGrpcService service) {
        grpc = InProcessGrpcServer.start(service);
        return RepositoryServiceGrpc.newBlockingStub(grpc.channel());
    }

    @AfterEach
    void shutdown() {
        if (grpc != null) {
            grpc.close();
        }
    }

    // ========== ListSessions ==========

    @Nested
    class ListSessions {

        @Test
        void returnsSessionList() throws Exception {
            var repoManager = mock(RepositoryManager.class);
            when(repoManager.listRecordingSessions(SessionDetail.WITH_FILES, RecordingSessionFilter.ALL)).thenReturn(List.of(
                    new cafe.jeffrey.hub.model.repository.RecordingSession(
                            SESSION_ID, "session-name", "inst-1",
                            FIXED_TIME, null,
                            cafe.jeffrey.hub.model.repository.RecordingStatus.ACTIVE,
                            List.of(new RepositoryFile(
                                    "file-1", "recording.jfr", FIXED_TIME, 1024L,
                                    true,
                                    null)),
                            false),
                    new cafe.jeffrey.hub.model.repository.RecordingSession(
                            "session-2", "finished-session", null,
                            FIXED_TIME, FIXED_TIME.plusSeconds(3600),
                            cafe.jeffrey.hub.model.repository.RecordingStatus.FINISHED,
                            List.of(), true)
            ));

            var stub = startServer(serviceWithProject(repoManager));

            ListSessionsResponse response = stub.listSessions(
                    ListSessionsRequest.newBuilder()
                            .setProjectId(PROJECT_ID)
                            .build());

            assertEquals(2, response.getSessionsCount());

            cafe.jeffrey.hub.api.v1.RecordingSession first = response.getSessions(0);
            assertEquals(SESSION_ID, first.getId());
            assertEquals("session-name", first.getName());
            assertEquals("inst-1", first.getInstanceId());
            assertEquals(FIXED_TIME.toEpochMilli(), first.getCreatedAt());
            assertEquals(RecordingStatus.RECORDING_STATUS_ACTIVE, first.getStatus());
            assertFalse(first.hasFinishedAt());
            assertEquals(1, first.getFilesCount());

            cafe.jeffrey.hub.api.v1.RepositoryFile protoFile = first.getFiles(0);
            assertEquals("file-1", protoFile.getId());
            assertEquals("recording.jfr", protoFile.getName());
            assertEquals(1024L, protoFile.getSize());
            assertEquals(FIXED_TIME.toEpochMilli(), protoFile.getCreatedAt(),
                    "the file's own timestamp is what the reader derives an open chunk from");

            cafe.jeffrey.hub.api.v1.RecordingSession second = response.getSessions(1);
            assertEquals("session-2", second.getId());
            assertEquals(RecordingStatus.RECORDING_STATUS_FINISHED, second.getStatus());
            assertTrue(second.hasFinishedAt());
            assertEquals(FIXED_TIME.plusSeconds(3600).toEpochMilli(), second.getFinishedAt());
        }

        @Test
        void returnsEmptyListWhenNoSessions() throws Exception {
            var repoManager = mock(RepositoryManager.class);
            when(repoManager.listRecordingSessions(SessionDetail.WITH_FILES, RecordingSessionFilter.ALL)).thenReturn(List.of());

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

        @Test
        void filterFieldsReachTheRepositoryManager() throws Exception {
            var repoManager = mock(RepositoryManager.class);
            Instant from = FIXED_TIME.minus(Duration.ofHours(1));
            var expected = new RecordingSessionFilter(
                    from, FIXED_TIME, cafe.jeffrey.hub.model.repository.RecordingStatus.FINISHED, 5);
            when(repoManager.listRecordingSessions(SessionDetail.WITH_FILES, expected)).thenReturn(List.of(
                    new cafe.jeffrey.hub.model.repository.RecordingSession(
                            SESSION_ID, "session-name", null,
                            from, FIXED_TIME,
                            cafe.jeffrey.hub.model.repository.RecordingStatus.FINISHED,
                            List.of(), false)));

            var stub = startServer(serviceWithProject(repoManager));

            ListSessionsResponse response = stub.listSessions(
                    ListSessionsRequest.newBuilder()
                            .setProjectId(PROJECT_ID)
                            .setFilter(SessionFilter.newBuilder()
                                    .setActiveFrom(from.toEpochMilli())
                                    .setActiveTo(FIXED_TIME.toEpochMilli())
                                    .setStatus(RecordingStatus.RECORDING_STATUS_FINISHED)
                                    .setLimit(5))
                            .build());

            assertEquals(1, response.getSessionsCount());
            assertEquals(SESSION_ID, response.getSessions(0).getId());
            verify(repoManager).listRecordingSessions(SessionDetail.WITH_FILES, expected);
        }

        @Test
        void absentFilterListsEverything() throws Exception {
            var repoManager = mock(RepositoryManager.class);
            when(repoManager.listRecordingSessions(SessionDetail.WITH_FILES, RecordingSessionFilter.ALL)).thenReturn(List.of());

            var stub = startServer(serviceWithProject(repoManager));

            stub.listSessions(ListSessionsRequest.newBuilder().setProjectId(PROJECT_ID).build());

            verify(repoManager).listRecordingSessions(SessionDetail.WITH_FILES, RecordingSessionFilter.ALL);
        }

        @Test
        void emptyWindow_returnsInvalidArgument() throws Exception {
            var stub = startServer(serviceWithProject(mock(RepositoryManager.class)));

            StatusRuntimeException ex = assertThrows(StatusRuntimeException.class, () ->
                    stub.listSessions(
                            ListSessionsRequest.newBuilder()
                                    .setProjectId(PROJECT_ID)
                                    .setFilter(SessionFilter.newBuilder()
                                            .setActiveFrom(FIXED_TIME.toEpochMilli())
                                            .setActiveTo(FIXED_TIME.minusSeconds(1).toEpochMilli()))
                                    .build()));

            assertEquals(Status.Code.INVALID_ARGUMENT, ex.getStatus().getCode());
        }

        @Test
        void negativeLimit_returnsInvalidArgument() throws Exception {
            var stub = startServer(serviceWithProject(mock(RepositoryManager.class)));

            StatusRuntimeException ex = assertThrows(StatusRuntimeException.class, () ->
                    stub.listSessions(
                            ListSessionsRequest.newBuilder()
                                    .setProjectId(PROJECT_ID)
                                    .setFilter(SessionFilter.newBuilder().setLimit(-1))
                                    .build()));

            assertEquals(Status.Code.INVALID_ARGUMENT, ex.getStatus().getCode());
        }
    }

    // ========== GetSession ==========

    @Nested
    class GetSession {

        @Test
        void returnsSession() throws Exception {
            var repoManager = mock(RepositoryManager.class);
            when(repoManager.findRecordingSessions(SESSION_ID)).thenReturn(Optional.of(
                    new cafe.jeffrey.hub.model.repository.RecordingSession(
                            SESSION_ID, "my-session", "inst-1",
                            FIXED_TIME, FIXED_TIME.plusSeconds(600),
                            cafe.jeffrey.hub.model.repository.RecordingStatus.FINISHED,
                            List.of(), false)
            ));

            var stub = startServer(serviceWithSession(repoManager));

            GetSessionResponse response = stub.getSession(
                    GetSessionRequest.newBuilder()
                            .setSessionId(SESSION_ID)
                            .build());

            cafe.jeffrey.hub.api.v1.RecordingSession session = response.getSession();
            assertEquals(SESSION_ID, session.getId());
            assertEquals("my-session", session.getName());
            assertEquals("inst-1", session.getInstanceId());
            assertEquals(RecordingStatus.RECORDING_STATUS_FINISHED, session.getStatus());
            assertTrue(session.hasFinishedAt());
            assertEquals(FIXED_TIME.plusSeconds(600).toEpochMilli(), session.getFinishedAt());
        }

        @Test
        void sessionNotFound_returnsNotFound() throws Exception {
            var platformRepositories = mock(HubPlatformRepositories.class);
            when(platformRepositories.findSessionWithRepositoryById("non-existent")).thenReturn(Optional.empty());

            var repoManagerFactory = mock(RepositoryManager.Factory.class);

            var stub = startServer(new RepositoryGrpcService(new GrpcLookups(platformRepositories, repoManagerFactory, null)));

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
        void returnsTotalSize() throws Exception {
            var repoManager = mock(RepositoryManager.class);
            when(repoManager.calculateRepositoryStatistics())
                    .thenReturn(new RepositoryStatistics(1_000_000L));

            var stub = startServer(serviceWithProject(repoManager));

            GetRepositoryStatisticsResponse response = stub.getRepositoryStatistics(
                    GetRepositoryStatisticsRequest.newBuilder()
                            .setProjectId(PROJECT_ID)
                            .build());

            assertEquals(1_000_000L, response.getTotalSize());
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
            verify(repoManager).deleteRecordingSession(SESSION_ID);
        }

        @Test
        void sessionNotFound_returnsNotFound() throws Exception {
            var platformRepositories = mock(HubPlatformRepositories.class);
            when(platformRepositories.findSessionWithRepositoryById("non-existent")).thenReturn(Optional.empty());

            var repoManagerFactory = mock(RepositoryManager.Factory.class);

            var stub = startServer(new RepositoryGrpcService(new GrpcLookups(platformRepositories, repoManagerFactory, null)));

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
            var platformRepositories = mock(HubPlatformRepositories.class);
            when(platformRepositories.findSessionWithRepositoryById("non-existent")).thenReturn(Optional.empty());

            var repoManagerFactory = mock(RepositoryManager.Factory.class);

            var stub = startServer(new RepositoryGrpcService(new GrpcLookups(platformRepositories, repoManagerFactory, null)));

            StatusRuntimeException ex = assertThrows(StatusRuntimeException.class, () ->
                    stub.deleteFilesInSession(
                            DeleteFilesInSessionRequest.newBuilder()
                                    .setSessionId("non-existent")
                                    .addFileIds("file-1")
                                    .build()));

            assertEquals(Status.Code.NOT_FOUND, ex.getStatus().getCode());
        }
        /**
         * The manager refuses the chunk the profiler still writes with an {@code IllegalArgumentException};
         * at this boundary that must arrive as INVALID_ARGUMENT carrying the manager's own sentence,
         * not as INTERNAL — which is what an unreachable hub gives, and what the client cannot act on.
         */
        @Test
        void refusingTheOpenChunk_returnsInvalidArgumentWithTheReason() throws Exception {
            var repoManager = mock(RepositoryManager.class);
            doThrow(new IllegalArgumentException("File profile-2.jfr is the chunk the profiler is still writing"))
                    .when(repoManager).deleteFilesInSession(SESSION_ID, List.of("profile-2"));

            var stub = startServer(serviceWithSession(repoManager));

            StatusRuntimeException ex = assertThrows(StatusRuntimeException.class, () ->
                    stub.deleteFilesInSession(
                            DeleteFilesInSessionRequest.newBuilder()
                                    .setSessionId(SESSION_ID)
                                    .addFileIds("profile-2")
                                    .build()));

            assertEquals(Status.Code.INVALID_ARGUMENT, ex.getStatus().getCode());
            assertTrue(ex.getStatus().getDescription().contains("still writing"));
        }
    }

    @Nested
    class SetSessionRetained {

        @Test
        void pinsAndReleasesTheSession() throws Exception {
            var repoManager = mock(RepositoryManager.class);
            var stub = startServer(serviceWithSession(repoManager));

            stub.setSessionRetained(SetSessionRetainedRequest.newBuilder()
                    .setSessionId(SESSION_ID).setRetained(true).build());
            stub.setSessionRetained(SetSessionRetainedRequest.newBuilder()
                    .setSessionId(SESSION_ID).setRetained(false).build());

            verify(repoManager).setSessionRetained(SESSION_ID, true);
            verify(repoManager).setSessionRetained(SESSION_ID, false);
        }

        @Test
        void sessionNotFound_returnsNotFound() throws Exception {
            var platformRepositories = mock(HubPlatformRepositories.class);
            when(platformRepositories.findSessionWithRepositoryById("non-existent")).thenReturn(Optional.empty());

            var stub = startServer(new RepositoryGrpcService(
                    new GrpcLookups(platformRepositories, mock(RepositoryManager.Factory.class), null)));

            StatusRuntimeException ex = assertThrows(StatusRuntimeException.class, () ->
                    stub.setSessionRetained(SetSessionRetainedRequest.newBuilder()
                            .setSessionId("non-existent").setRetained(true).build()));

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

        var platformRepositories = mock(HubPlatformRepositories.class);
        when(platformRepositories.newProjectRepository(PROJECT_ID)).thenReturn(projectRepo);

        var repoManagerFactory = mock(RepositoryManager.Factory.class);
        when(repoManagerFactory.apply(TEST_PROJECT_INFO)).thenReturn(repoManager);

        return new RepositoryGrpcService(new GrpcLookups(platformRepositories, repoManagerFactory, null));
    }

    /**
     * Creates a service where {@code repositoryManagerForSession(SESSION_ID)} succeeds.
     */
    private RepositoryGrpcService serviceWithSession(RepositoryManager repoManager) {
        // The session names its project by id; the lookup then resolves the project itself
        var sessionWithRepo = mock(SessionWithRepository.class);
        when(sessionWithRepo.projectId()).thenReturn(PROJECT_ID);
        var projectRepo = mock(ProjectRepository.class);
        when(projectRepo.find()).thenReturn(Optional.of(TEST_PROJECT_INFO));

        var platformRepositories = mock(HubPlatformRepositories.class);
        when(platformRepositories.findSessionWithRepositoryById(SESSION_ID)).thenReturn(Optional.of(sessionWithRepo));
        when(platformRepositories.newProjectRepository(PROJECT_ID)).thenReturn(projectRepo);

        var repoManagerFactory = mock(RepositoryManager.Factory.class);
        when(repoManagerFactory.apply(TEST_PROJECT_INFO)).thenReturn(repoManager);

        return new RepositoryGrpcService(new GrpcLookups(platformRepositories, repoManagerFactory, null));
    }

    /**
     * Creates a service where {@code newProjectRepository("non-existent").find()} returns empty.
     */
    private RepositoryGrpcService serviceWithNoProject() {
        var projectRepo = mock(ProjectRepository.class);
        when(projectRepo.find()).thenReturn(Optional.empty());

        var platformRepositories = mock(HubPlatformRepositories.class);
        when(platformRepositories.newProjectRepository("non-existent")).thenReturn(projectRepo);

        var repoManagerFactory = mock(RepositoryManager.Factory.class);

        return new RepositoryGrpcService(new GrpcLookups(platformRepositories, repoManagerFactory, null));
    }
}
