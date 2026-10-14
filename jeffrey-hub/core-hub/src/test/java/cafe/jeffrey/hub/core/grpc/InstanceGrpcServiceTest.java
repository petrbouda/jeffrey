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

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import cafe.jeffrey.hub.api.v1.InstanceInfo;
import cafe.jeffrey.hub.api.v1.InstanceServiceGrpc;
import cafe.jeffrey.hub.api.v1.InstanceStatus;
import cafe.jeffrey.hub.api.v1.ListInstancesRequest;
import cafe.jeffrey.hub.api.v1.ListInstancesResponse;
import cafe.jeffrey.hub.core.manager.RepositoryManager;
import cafe.jeffrey.hub.persistence.api.HubPlatformRepositories;
import cafe.jeffrey.hub.persistence.api.ProjectInstanceRepository;
import cafe.jeffrey.hub.persistence.api.ProjectRepository;
import cafe.jeffrey.hub.model.ProjectInfo;
import cafe.jeffrey.hub.model.ProjectInstanceInfo;
import cafe.jeffrey.hub.model.ProjectInstanceInfo.ProjectInstanceStatus;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class InstanceGrpcServiceTest {

    private static final String PROJECT_ID = "proj-1";
    private static final String INSTANCE_ID = "inst-1";
    private static final Instant FIXED_TIME = Instant.parse("2026-01-15T10:00:00Z");

    private InProcessGrpcServer grpc;

    private InstanceServiceGrpc.InstanceServiceBlockingStub startServer(HubPlatformRepositories platformRepositories) {
        GrpcLookups lookups = new GrpcLookups(platformRepositories, mock(RepositoryManager.Factory.class), null);
        grpc = InProcessGrpcServer.start(new InstanceGrpcService(platformRepositories, lookups));
        return InstanceServiceGrpc.newBlockingStub(grpc.channel());
    }

    @AfterEach
    void shutdown() {
        if (grpc != null) {
            grpc.close();
        }
    }

    /**
     * Platform repositories in which PROJECT_ID is a project GrpcLookups can resolve, holding the
     * given instances; every other project id stays unknown.
     */
    private static HubPlatformRepositories projectWithInstances(List<ProjectInstanceInfo> instances) {
        var instanceRepo = mock(ProjectInstanceRepository.class);
        when(instanceRepo.findAll()).thenReturn(instances);

        ProjectInfo projectInfo = new ProjectInfo(PROJECT_ID, null, "test-project", null, FIXED_TIME, null, null);
        var projectRepo = mock(ProjectRepository.class);
        when(projectRepo.find()).thenReturn(Optional.of(projectInfo));

        var platformRepositories = mock(HubPlatformRepositories.class);
        when(platformRepositories.newProjectRepository(any())).thenReturn(mock(ProjectRepository.class));
        when(platformRepositories.newProjectRepository(PROJECT_ID)).thenReturn(projectRepo);
        when(platformRepositories.newProjectInstanceRepository(PROJECT_ID)).thenReturn(instanceRepo);
        return platformRepositories;
    }

    @Nested
    class ListInstances {

        @Test
        void returnsEveryInstanceWithItsLifecycle() {
            var stub = startServer(projectWithInstances(List.of(
                    new ProjectInstanceInfo(
                            INSTANCE_ID, PROJECT_ID, INSTANCE_ID,
                            ProjectInstanceStatus.ACTIVE, FIXED_TIME, null, null, null,
                            2, "session-active"),
                    new ProjectInstanceInfo(
                            "inst-2", PROJECT_ID, "inst-2",
                            ProjectInstanceStatus.EXPIRED, FIXED_TIME, FIXED_TIME.plusSeconds(3600),
                            FIXED_TIME.plusSeconds(7200), FIXED_TIME.plusSeconds(9000), 0, null))));

            ListInstancesResponse response = stub.listInstances(
                    ListInstancesRequest.newBuilder().setProjectId(PROJECT_ID).build());

            assertEquals(2, response.getInstancesCount());

            InstanceInfo active = response.getInstances(0);
            assertEquals(INSTANCE_ID, active.getId());
            assertEquals(InstanceStatus.INSTANCE_STATUS_ACTIVE, active.getStatus());
            assertEquals(FIXED_TIME.toEpochMilli(), active.getCreatedAt());
            assertEquals(2, active.getSessionCount());
            assertFalse(active.hasFinishedAt());
            assertFalse(active.hasExpiringAt());
            assertFalse(active.hasExpiredAt());

            InstanceInfo expired = response.getInstances(1);
            assertEquals("inst-2", expired.getId());
            assertEquals(InstanceStatus.INSTANCE_STATUS_EXPIRED, expired.getStatus());
            assertTrue(expired.hasFinishedAt());
            assertEquals(FIXED_TIME.plusSeconds(3600).toEpochMilli(), expired.getFinishedAt());
            assertEquals(FIXED_TIME.plusSeconds(7200).toEpochMilli(), expired.getExpiringAt());
            assertEquals(FIXED_TIME.plusSeconds(9000).toEpochMilli(), expired.getExpiredAt());
            assertEquals(0, expired.getSessionCount());
        }

        @Test
        void returnsEmptyListWhenNoInstances() {
            var stub = startServer(projectWithInstances(List.of()));

            ListInstancesResponse response = stub.listInstances(
                    ListInstancesRequest.newBuilder().setProjectId(PROJECT_ID).build());

            assertEquals(0, response.getInstancesCount());
        }

        @Test
        void unknownProject_returnsNotFound() {
            var stub = startServer(projectWithInstances(List.of()));

            var ex = assertThrows(StatusRuntimeException.class, () ->
                    stub.listInstances(ListInstancesRequest.newBuilder().setProjectId("ghost").build()));

            assertEquals(Status.Code.NOT_FOUND, ex.getStatus().getCode());
        }
    }
}
