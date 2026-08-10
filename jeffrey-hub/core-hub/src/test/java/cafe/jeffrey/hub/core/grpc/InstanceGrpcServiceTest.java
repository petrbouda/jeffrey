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

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import cafe.jeffrey.hub.api.v1.*;
import cafe.jeffrey.hub.core.manager.RepositoryManager;
import cafe.jeffrey.hub.persistence.api.ProjectInstanceRepository;
import cafe.jeffrey.hub.persistence.api.ProjectRepository;
import cafe.jeffrey.hub.persistence.api.HubPlatformRepositories;
import cafe.jeffrey.shared.common.model.ProjectInfo;
import cafe.jeffrey.shared.common.model.ProjectInstanceInfo;
import cafe.jeffrey.shared.common.model.ProjectInstanceInfo.ProjectInstanceStatus;
import cafe.jeffrey.shared.common.model.ProjectInstanceSessionInfo;
import cafe.jeffrey.shared.common.model.repository.RecordingSession;
import cafe.jeffrey.shared.common.model.repository.RecordingStatus;
import cafe.jeffrey.shared.common.model.repository.RepositoryFile;
import cafe.jeffrey.shared.common.model.repository.SupportedRecordingFile;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class InstanceGrpcServiceTest {

    private static final String PROJECT_ID = "proj-1";
    private static final String INSTANCE_ID = "inst-1";
    private static final Instant FIXED_TIME = Instant.parse("2026-01-15T10:00:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(FIXED_TIME, ZoneId.of("UTC"));

    private InProcessGrpcServer grpc;

    private InstanceServiceGrpc.InstanceServiceBlockingStub startServer(
            InstanceGrpcService service) {
        grpc = InProcessGrpcServer.start(service);
        return InstanceServiceGrpc.newBlockingStub(grpc.channel());
    }

    @AfterEach
    void shutdown() {
        if (grpc != null) {
            grpc.close();
        }
    }

    /**
     * Wires GrpcLookups' project resolution so repositoryManagerForProject(PROJECT_ID)
     * resolves to the given RepositoryManager mock.
     */
    private static RepositoryManager.Factory repositoryManagerFactory(
            HubPlatformRepositories platformRepositories, RepositoryManager repoManager) {

        ProjectInfo projectInfo = new ProjectInfo(
                PROJECT_ID, null, "test-project", null, null, null,
                FIXED_TIME, null, null, null);
        var projectRepo = mock(ProjectRepository.class);
        when(projectRepo.find()).thenReturn(Optional.of(projectInfo));
        when(platformRepositories.newProjectRepository(PROJECT_ID)).thenReturn(projectRepo);
        return p -> repoManager;
    }

    /**
     * A finished repository session — failed sessions carry no files (zero bytes).
     */
    private static RecordingSession repositorySession(String id, boolean failed) {
        List<RepositoryFile> files = failed
                ? List.of()
                : List.of(new RepositoryFile(
                        id + "-file", id + "-file", FIXED_TIME, 1024L,
                        SupportedRecordingFile.JFR, RecordingStatus.FINISHED, null));

        return new RecordingSession(
                id, id, INSTANCE_ID, FIXED_TIME, FIXED_TIME.plusSeconds(60),
                RecordingStatus.FINISHED, null, null, files, false);
    }

    // ========== ListInstances ==========

    @Nested
    class ListInstances {

        @Test
        void returnsInstanceList() throws Exception {
            var instanceRepo = mock(ProjectInstanceRepository.class);
            when(instanceRepo.findAll()).thenReturn(List.of(
                    new ProjectInstanceInfo(
                            INSTANCE_ID, PROJECT_ID, "host-1",
                            ProjectInstanceStatus.ACTIVE, FIXED_TIME, null, null, null,
                            2, "session-active"),
                    new ProjectInstanceInfo(
                            "inst-2", PROJECT_ID, "host-2",
                            ProjectInstanceStatus.FINISHED, FIXED_TIME, FIXED_TIME.plusSeconds(3600),
                            null, null, 1, null)
            ));

            var platformRepositories = mock(HubPlatformRepositories.class);
            when(platformRepositories.newProjectInstanceRepository(PROJECT_ID)).thenReturn(instanceRepo);

            var stub = startServer(new InstanceGrpcService(platformRepositories, new GrpcLookups(platformRepositories, mock(RepositoryManager.Factory.class), null), FIXED_CLOCK));

            ListInstancesResponse response = stub.listInstances(
                    ListInstancesRequest.newBuilder()
                            .setProjectId(PROJECT_ID)
                            .build());

            assertEquals(2, response.getInstancesCount());

            InstanceInfo first = response.getInstances(0);
            assertEquals(INSTANCE_ID, first.getId());
            assertEquals("host-1", first.getInstanceName());
            assertEquals(InstanceStatus.INSTANCE_STATUS_ACTIVE, first.getStatus());
            assertEquals(FIXED_TIME.toEpochMilli(), first.getCreatedAt());
            assertEquals(2, first.getSessionCount());
            assertEquals("session-active", first.getActiveSessionId());
            assertFalse(first.hasFinishedAt());

            InstanceInfo second = response.getInstances(1);
            assertEquals("inst-2", second.getId());
            assertEquals(InstanceStatus.INSTANCE_STATUS_FINISHED, second.getStatus());
            assertTrue(second.hasFinishedAt());
            assertEquals(FIXED_TIME.plusSeconds(3600).toEpochMilli(), second.getFinishedAt());
        }

        @Test
        void returnsEmptyListWhenNoInstances() throws Exception {
            var instanceRepo = mock(ProjectInstanceRepository.class);
            when(instanceRepo.findAll()).thenReturn(List.of());

            var platformRepositories = mock(HubPlatformRepositories.class);
            when(platformRepositories.newProjectInstanceRepository(PROJECT_ID)).thenReturn(instanceRepo);

            var stub = startServer(new InstanceGrpcService(platformRepositories, new GrpcLookups(platformRepositories, mock(RepositoryManager.Factory.class), null), FIXED_CLOCK));

            ListInstancesResponse response = stub.listInstances(
                    ListInstancesRequest.newBuilder()
                            .setProjectId(PROJECT_ID)
                            .build());

            assertEquals(0, response.getInstancesCount());
        }

        @Test
        void includeSessionsFalse_leavesSessionsEmpty() throws Exception {
            var instanceRepo = mock(ProjectInstanceRepository.class);
            when(instanceRepo.findAll()).thenReturn(List.of(
                    new ProjectInstanceInfo(
                            INSTANCE_ID, PROJECT_ID, "host-1",
                            ProjectInstanceStatus.ACTIVE, FIXED_TIME, null, null, null,
                            1, "session-active")
            ));

            var platformRepositories = mock(HubPlatformRepositories.class);
            when(platformRepositories.newProjectInstanceRepository(PROJECT_ID)).thenReturn(instanceRepo);

            var stub = startServer(new InstanceGrpcService(platformRepositories, new GrpcLookups(platformRepositories, mock(RepositoryManager.Factory.class), null), FIXED_CLOCK));

            ListInstancesResponse response = stub.listInstances(
                    ListInstancesRequest.newBuilder()
                            .setProjectId(PROJECT_ID)
                            .build());

            assertEquals(1, response.getInstancesCount());
            assertEquals(0, response.getInstances(0).getSessionsCount());
        }

        @Test
        void includeSessionsTrue_populatesSessionsPerInstance() throws Exception {
            var instanceRepo = mock(ProjectInstanceRepository.class);
            when(instanceRepo.findAll()).thenReturn(List.of(
                    new ProjectInstanceInfo(
                            INSTANCE_ID, PROJECT_ID, "host-1",
                            ProjectInstanceStatus.ACTIVE, FIXED_TIME, null, null, null,
                            2, "session-active"),
                    new ProjectInstanceInfo(
                            "inst-2", PROJECT_ID, "host-2",
                            ProjectInstanceStatus.FINISHED, FIXED_TIME,
                            FIXED_TIME.plusSeconds(3600), null, null, 1, null)
            ));

            var platformRepositories = mock(HubPlatformRepositories.class);
            when(platformRepositories.newProjectInstanceRepository(PROJECT_ID)).thenReturn(instanceRepo);
            when(platformRepositories.findSessionsByProjectId(PROJECT_ID)).thenReturn(List.of(
                    ProjectInstanceSessionInfo.notRetained(
                            "session-active", "repo-1", INSTANCE_ID, 0,
                            Path.of("session-active"), null, FIXED_TIME, null),
                    ProjectInstanceSessionInfo.notRetained(
                            "session-old", "repo-1", INSTANCE_ID, 1,
                            Path.of("session-old"), null,
                            FIXED_TIME.minusSeconds(600), FIXED_TIME.minusSeconds(300)),
                    ProjectInstanceSessionInfo.notRetained(
                            "session-only", "repo-1", "inst-2", 0,
                            Path.of("session-only"), null,
                            FIXED_TIME, FIXED_TIME.plusSeconds(3600))
            ));

            var repoManager = mock(RepositoryManager.class);
            when(repoManager.listRecordingSessions(true)).thenReturn(List.of());
            var factory = repositoryManagerFactory(platformRepositories, repoManager);

            var stub = startServer(new InstanceGrpcService(platformRepositories, new GrpcLookups(platformRepositories, factory, null), FIXED_CLOCK));

            ListInstancesResponse response = stub.listInstances(
                    ListInstancesRequest.newBuilder()
                            .setProjectId(PROJECT_ID)
                            .setIncludeSessions(true)
                            .build());

            assertEquals(2, response.getInstancesCount());

            InstanceInfo first = response.getInstances(0);
            assertEquals(2, first.getSessionsCount());
            assertEquals("session-active", first.getSessions(0).getId());
            assertTrue(first.getSessions(0).getIsActive());
            assertEquals("session-old", first.getSessions(1).getId());
            assertFalse(first.getSessions(1).getIsActive());

            InstanceInfo second = response.getInstances(1);
            assertEquals(1, second.getSessionsCount());
            assertEquals("session-only", second.getSessions(0).getId());
        }

        @Test
        void includeSessionsTrue_marksFailedZeroSizeSessions() throws Exception {
            var instanceRepo = mock(ProjectInstanceRepository.class);
            when(instanceRepo.findAll()).thenReturn(List.of(
                    new ProjectInstanceInfo(
                            INSTANCE_ID, PROJECT_ID, "host-1",
                            ProjectInstanceStatus.ACTIVE, FIXED_TIME, null, null, null,
                            2, null)
            ));

            var platformRepositories = mock(HubPlatformRepositories.class);
            when(platformRepositories.newProjectInstanceRepository(PROJECT_ID)).thenReturn(instanceRepo);
            when(platformRepositories.findSessionsByProjectId(PROJECT_ID)).thenReturn(List.of(
                    ProjectInstanceSessionInfo.notRetained(
                            "session-real", "repo-1", INSTANCE_ID, 0,
                            Path.of("session-real"), null,
                            FIXED_TIME, FIXED_TIME.plusSeconds(600)),
                    ProjectInstanceSessionInfo.notRetained(
                            "session-crashed", "repo-1", INSTANCE_ID, 1,
                            Path.of("session-crashed"), null,
                            FIXED_TIME.plusSeconds(700), FIXED_TIME.plusSeconds(705))
            ));

            // Repository storage knows the file sizes: session-crashed has zero bytes
            var repoManager = mock(RepositoryManager.class);
            when(repoManager.listRecordingSessions(true)).thenReturn(List.of(
                    repositorySession("session-real", false),
                    repositorySession("session-crashed", true)));
            var factory = repositoryManagerFactory(platformRepositories, repoManager);

            var stub = startServer(new InstanceGrpcService(platformRepositories, new GrpcLookups(platformRepositories, factory, null), FIXED_CLOCK));

            ListInstancesResponse response = stub.listInstances(
                    ListInstancesRequest.newBuilder()
                            .setProjectId(PROJECT_ID)
                            .setIncludeSessions(true)
                            .build());

            InstanceInfo instance = response.getInstances(0);
            assertEquals(2, instance.getSessionsCount());
            assertEquals("session-real", instance.getSessions(0).getId());
            assertFalse(instance.getSessions(0).getFailed());
            assertEquals("session-crashed", instance.getSessions(1).getId());
            assertTrue(instance.getSessions(1).getFailed());
        }
    }

    // ========== GetInstance ==========

    @Nested
    class GetInstance {

        @Test
        void returnsInstance() throws Exception {
            var platformRepositories = mock(HubPlatformRepositories.class);
            when(platformRepositories.findInstanceById(INSTANCE_ID)).thenReturn(Optional.of(
                    new ProjectInstanceInfo(
                            INSTANCE_ID, PROJECT_ID, "host-1",
                            ProjectInstanceStatus.EXPIRED, FIXED_TIME,
                            FIXED_TIME.plusSeconds(3600),
                            FIXED_TIME.plusSeconds(7200),
                            FIXED_TIME.plusSeconds(10800),
                            3, null)
            ));

            var stub = startServer(new InstanceGrpcService(platformRepositories, new GrpcLookups(platformRepositories, mock(RepositoryManager.Factory.class), null), FIXED_CLOCK));

            GetInstanceResponse response = stub.getInstance(
                    GetInstanceRequest.newBuilder()
                            .setInstanceId(INSTANCE_ID)
                            .build());

            InstanceInfo instance = response.getInstance();
            assertEquals(INSTANCE_ID, instance.getId());
            assertEquals("host-1", instance.getInstanceName());
            assertEquals(InstanceStatus.INSTANCE_STATUS_EXPIRED, instance.getStatus());
            assertEquals(FIXED_TIME.toEpochMilli(), instance.getCreatedAt());
            assertTrue(instance.hasFinishedAt());
            assertEquals(FIXED_TIME.plusSeconds(3600).toEpochMilli(), instance.getFinishedAt());
            assertTrue(instance.hasExpiringAt());
            assertEquals(FIXED_TIME.plusSeconds(7200).toEpochMilli(), instance.getExpiringAt());
            assertTrue(instance.hasExpiredAt());
            assertEquals(FIXED_TIME.plusSeconds(10800).toEpochMilli(), instance.getExpiredAt());
            assertEquals(3, instance.getSessionCount());
            assertFalse(instance.hasActiveSessionId());
        }

        @Test
        void instanceNotFound_returnsNotFound() throws Exception {
            var platformRepositories = mock(HubPlatformRepositories.class);
            when(platformRepositories.findInstanceById("non-existent")).thenReturn(Optional.empty());

            var stub = startServer(new InstanceGrpcService(platformRepositories, new GrpcLookups(platformRepositories, mock(RepositoryManager.Factory.class), null), FIXED_CLOCK));

            StatusRuntimeException ex = assertThrows(StatusRuntimeException.class, () ->
                    stub.getInstance(
                            GetInstanceRequest.newBuilder()
                                    .setInstanceId("non-existent")
                                    .build()));

            assertEquals(Status.Code.NOT_FOUND, ex.getStatus().getCode());
        }
    }

    // ========== ListInstanceSessions ==========

    @Nested
    class ListInstanceSessions {

        private HubPlatformRepositories platformRepositoriesWithInstance() {
            var platformRepositories = mock(HubPlatformRepositories.class);
            when(platformRepositories.findInstanceById(INSTANCE_ID)).thenReturn(Optional.of(
                    new ProjectInstanceInfo(
                            INSTANCE_ID, PROJECT_ID, "host-1",
                            ProjectInstanceStatus.ACTIVE, FIXED_TIME, null, null, null,
                            2, null)
            ));
            return platformRepositories;
        }

        @Test
        void returnsSessionListWithFailedFlags() throws Exception {
            var platformRepositories = platformRepositoriesWithInstance();
            when(platformRepositories.findSessionsByInstanceId(INSTANCE_ID)).thenReturn(List.of(
                    ProjectInstanceSessionInfo.notRetained(
                            "session-1", "repo-1", INSTANCE_ID, 0,
                            Path.of("session-1"), null,
                            FIXED_TIME, null),
                    ProjectInstanceSessionInfo.notRetained(
                            "session-2", "repo-1", INSTANCE_ID, 1,
                            Path.of("session-2"), null,
                            FIXED_TIME.plusSeconds(600),
                            FIXED_TIME.plusSeconds(1200))
            ));

            // session-2 finished with zero bytes on disk — it must come back flagged as failed
            var repoManager = mock(RepositoryManager.class);
            when(repoManager.listRecordingSessions(true)).thenReturn(List.of(
                    repositorySession("session-2", true)));
            var factory = repositoryManagerFactory(platformRepositories, repoManager);

            var stub = startServer(new InstanceGrpcService(platformRepositories, new GrpcLookups(platformRepositories, factory, null), FIXED_CLOCK));

            ListInstanceSessionsResponse response = stub.listInstanceSessions(
                    ListInstanceSessionsRequest.newBuilder()
                            .setInstanceId(INSTANCE_ID)
                            .build());

            assertEquals(2, response.getSessionsCount());

            InstanceSessionInfo first = response.getSessions(0);
            assertEquals("session-1", first.getId());
            assertEquals("repo-1", first.getRepositoryId());
            assertEquals(FIXED_TIME.toEpochMilli(), first.getCreatedAt());
            assertTrue(first.getIsActive());
            assertFalse(first.hasFinishedAt());
            assertFalse(first.getFailed());

            InstanceSessionInfo second = response.getSessions(1);
            assertEquals("session-2", second.getId());
            assertFalse(second.getIsActive());
            assertTrue(second.hasFinishedAt());
            assertEquals(FIXED_TIME.plusSeconds(1200).toEpochMilli(), second.getFinishedAt());
            assertTrue(second.getFailed());
        }

        @Test
        void instanceNotFound_returnsNotFound() throws Exception {
            var platformRepositories = mock(HubPlatformRepositories.class);
            when(platformRepositories.findInstanceById("non-existent")).thenReturn(Optional.empty());

            var stub = startServer(new InstanceGrpcService(platformRepositories, new GrpcLookups(platformRepositories, mock(RepositoryManager.Factory.class), null), FIXED_CLOCK));

            StatusRuntimeException ex = assertThrows(StatusRuntimeException.class, () ->
                    stub.listInstanceSessions(
                            ListInstanceSessionsRequest.newBuilder()
                                    .setInstanceId("non-existent")
                                    .build()));

            assertEquals(Status.Code.NOT_FOUND, ex.getStatus().getCode());
        }

        @Test
        void returnsEmptyListWhenNoSessions() throws Exception {
            var platformRepositories = platformRepositoriesWithInstance();
            when(platformRepositories.findSessionsByInstanceId(INSTANCE_ID)).thenReturn(List.of());

            var repoManager = mock(RepositoryManager.class);
            when(repoManager.listRecordingSessions(true)).thenReturn(List.of());
            var factory = repositoryManagerFactory(platformRepositories, repoManager);

            var stub = startServer(new InstanceGrpcService(platformRepositories, new GrpcLookups(platformRepositories, factory, null), FIXED_CLOCK));

            ListInstanceSessionsResponse response = stub.listInstanceSessions(
                    ListInstanceSessionsRequest.newBuilder()
                            .setInstanceId(INSTANCE_ID)
                            .build());

            assertEquals(0, response.getSessionsCount());
        }
    }

    // ========== GetInstanceDetail ==========

    @Nested
    class GetInstanceDetail {

        @Test
        void returnsInstanceWithStatsAndSessions() throws Exception {
            var platformRepositories = mock(HubPlatformRepositories.class);
            when(platformRepositories.findInstanceById(INSTANCE_ID)).thenReturn(Optional.of(
                    new ProjectInstanceInfo(
                            INSTANCE_ID, PROJECT_ID, "host-1",
                            ProjectInstanceStatus.FINISHED,
                            FIXED_TIME, FIXED_TIME.plusSeconds(3600), null, null,
                            2, null)
            ));
            when(platformRepositories.findSessionsByInstanceId(INSTANCE_ID)).thenReturn(List.of(
                    ProjectInstanceSessionInfo.notRetained(
                            "session-1", "repo-1", INSTANCE_ID, 0,
                            Path.of("session-1"), null,
                            FIXED_TIME, FIXED_TIME.plusSeconds(1800)),
                    ProjectInstanceSessionInfo.notRetained(
                            "session-2", "repo-1", INSTANCE_ID, 1,
                            Path.of("session-2"), null,
                            FIXED_TIME.plusSeconds(1800), FIXED_TIME.plusSeconds(3600))
            ));

            ProjectInfo projectInfo = new ProjectInfo(
                    PROJECT_ID, null, "test-project", null, null, null,
                    FIXED_TIME, null, null, null);
            var projectRepo = mock(ProjectRepository.class);
            when(projectRepo.find()).thenReturn(Optional.of(projectInfo));
            when(platformRepositories.newProjectRepository(PROJECT_ID)).thenReturn(projectRepo);

            var repoManager = mock(RepositoryManager.class);
            when(repoManager.instanceStats(INSTANCE_ID))
                    .thenReturn(new cafe.jeffrey.shared.common.model.repository.InstanceStats(5, 12_345_678L));
            when(repoManager.listRecordingSessions(true)).thenReturn(List.of(
                    repositorySession("session-2", true)));
            RepositoryManager.Factory factory = p -> repoManager;

            var stub = startServer(new InstanceGrpcService(
                    platformRepositories, new GrpcLookups(platformRepositories, factory, null), FIXED_CLOCK));

            GetInstanceDetailResponse response = stub.getInstanceDetail(
                    GetInstanceDetailRequest.newBuilder()
                            .setInstanceId(INSTANCE_ID)
                            .build());

            assertTrue(response.hasInstance());
            InstanceInfo instance = response.getInstance();
            assertEquals(INSTANCE_ID, instance.getId());
            assertEquals("host-1", instance.getInstanceName());
            assertEquals(InstanceStatus.INSTANCE_STATUS_FINISHED, instance.getStatus());
            assertEquals(2, instance.getSessionsCount());
            assertEquals("session-1", instance.getSessions(0).getId());
            assertFalse(instance.getSessions(0).getFailed());
            assertEquals("session-2", instance.getSessions(1).getId());
            assertTrue(instance.getSessions(1).getFailed());

            assertTrue(response.hasStats());
            assertEquals(5, response.getStats().getFileCount());
            assertEquals(12_345_678L, response.getStats().getTotalSizeBytes());
        }

        @Test
        void instanceNotFound_returnsNotFound() throws Exception {
            var platformRepositories = mock(HubPlatformRepositories.class);
            when(platformRepositories.findInstanceById("non-existent")).thenReturn(Optional.empty());

            var stub = startServer(new InstanceGrpcService(
                    platformRepositories, new GrpcLookups(platformRepositories, mock(RepositoryManager.Factory.class), null), FIXED_CLOCK));

            StatusRuntimeException ex = assertThrows(StatusRuntimeException.class, () ->
                    stub.getInstanceDetail(
                            GetInstanceDetailRequest.newBuilder()
                                    .setInstanceId("non-existent")
                                    .build()));

            assertEquals(Status.Code.NOT_FOUND, ex.getStatus().getCode());
        }
    }
}
