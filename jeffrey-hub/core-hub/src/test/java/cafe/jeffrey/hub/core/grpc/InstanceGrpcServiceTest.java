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
import cafe.jeffrey.hub.api.v1.GetInstanceRequest;
import cafe.jeffrey.hub.api.v1.GetInstanceResponse;
import cafe.jeffrey.hub.api.v1.InstanceInfo;
import cafe.jeffrey.hub.api.v1.InstanceServiceGrpc;
import cafe.jeffrey.hub.api.v1.InstanceStatus;
import cafe.jeffrey.hub.api.v1.ListInstancesRequest;
import cafe.jeffrey.hub.api.v1.ListInstancesResponse;
import cafe.jeffrey.hub.core.manager.RepositoryManager;
import cafe.jeffrey.hub.core.project.repository.InstanceSessions;
import cafe.jeffrey.hub.model.ProjectInstanceInfo;
import cafe.jeffrey.hub.model.ProjectInstanceInfo.ProjectInstanceStatus;
import cafe.jeffrey.hub.model.repository.RecordingSession;
import cafe.jeffrey.hub.model.repository.RecordingStatus;
import cafe.jeffrey.hub.model.repository.RepositoryFile;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class InstanceGrpcServiceTest {

    private static final String PROJECT_ID = "proj-1";
    private static final String INSTANCE_ID = "inst-1";
    private static final Instant FIXED_TIME = Instant.parse("2026-01-15T10:00:00Z");

    private InProcessGrpcServer grpc;

    @AfterEach
    void shutdown() {
        if (grpc != null) {
            grpc.close();
        }
    }

    private final RepositoryManager repositoryManager = mock(RepositoryManager.class);

    /** A service over PROJECT_ID, whose instances the volume reports as given. */
    private InstanceServiceGrpc.InstanceServiceBlockingStub startServer(List<ProjectInstanceInfo> instances) {
        when(repositoryManager.instances()).thenReturn(instances);
        when(repositoryManager.instance("ghost")).thenReturn(Optional.empty());

        grpc = InProcessGrpcServer.start(new InstanceGrpcService(TestLookups.withRepository(PROJECT_ID, repositoryManager)));
        return InstanceServiceGrpc.newBlockingStub(grpc.channel());
    }

    private static ListInstancesRequest request(String projectId) {
        return ListInstancesRequest.newBuilder()
                .setWorkspaceId(TestLookups.WORKSPACE_ID)
                .setProjectId(projectId)
                .build();
    }

    @Nested
    class ListInstances {

        @Test
        void returnsEveryInstanceWithItsLifecycle() {
            var stub = startServer(List.of(
                    new ProjectInstanceInfo(INSTANCE_ID, ProjectInstanceStatus.ACTIVE, FIXED_TIME, null, 2),
                    new ProjectInstanceInfo(
                            "inst-2", ProjectInstanceStatus.FINISHED, FIXED_TIME, FIXED_TIME.plusSeconds(3600), 1)));

            ListInstancesResponse response = stub.listInstances(request(PROJECT_ID));

            assertEquals(2, response.getInstancesCount());

            InstanceInfo active = response.getInstances(0);
            assertEquals(INSTANCE_ID, active.getId());
            assertEquals(InstanceStatus.INSTANCE_STATUS_ACTIVE, active.getStatus());
            assertEquals(FIXED_TIME.toEpochMilli(), active.getCreatedAt());
            assertEquals(2, active.getSessionCount());
            assertFalse(active.hasFinishedAt());

            InstanceInfo finished = response.getInstances(1);
            assertEquals("inst-2", finished.getId());
            assertEquals(InstanceStatus.INSTANCE_STATUS_FINISHED, finished.getStatus());
            assertTrue(finished.hasFinishedAt());
            assertEquals(FIXED_TIME.plusSeconds(3600).toEpochMilli(), finished.getFinishedAt());
        }

        @Test
        void returnsEmptyListWhenNoInstances() {
            var stub = startServer(List.of());

            assertEquals(0, stub.listInstances(request(PROJECT_ID)).getInstancesCount());
        }

        @Test
        void unknownProject_returnsNotFound() {
            var stub = startServer(List.of());

            var ex = assertThrows(StatusRuntimeException.class, () -> stub.listInstances(request("ghost")));

            assertEquals(Status.Code.NOT_FOUND, ex.getStatus().getCode());
        }
    }

    @Nested
    class GetInstance {

        private GetInstanceRequest get(String instanceId) {
            return GetInstanceRequest.newBuilder()
                    .setWorkspaceId(TestLookups.WORKSPACE_ID)
                    .setProjectId(PROJECT_ID)
                    .setInstanceId(instanceId)
                    .build();
        }

        @Test
        void returnsTheInstanceWithItsSessionsButNotTheirFiles() {
            ProjectInstanceInfo instance =
                    new ProjectInstanceInfo(INSTANCE_ID, ProjectInstanceStatus.ACTIVE, FIXED_TIME, null, 1);
            RecordingSession session = new RecordingSession(
                    "s-1", "inst-1/s-1", INSTANCE_ID, FIXED_TIME, null, RecordingStatus.ACTIVE,
                    List.of(new RepositoryFile("f-1", "profile.jfr", FIXED_TIME, 2048L, true, null)), false);
            when(repositoryManager.instance(INSTANCE_ID))
                    .thenReturn(Optional.of(new InstanceSessions(instance, List.of(session))));
            var stub = startServer(List.of(instance));

            GetInstanceResponse response = stub.getInstance(get(INSTANCE_ID));

            assertEquals(INSTANCE_ID, response.getInstance().getId());
            assertEquals(InstanceStatus.INSTANCE_STATUS_ACTIVE, response.getInstance().getStatus());
            assertEquals(1, response.getSessionsCount());
            var sent = response.getSessions(0);
            assertEquals("s-1", sent.getId());
            assertEquals(0, sent.getFilesCount(), "the files stay on the hub");
            assertEquals(1, sent.getFileCount());
            assertEquals(2048L, sent.getSizeBytes());
        }

        @Test
        void unknownInstance_returnsNotFound() {
            var stub = startServer(List.of());

            var ex = assertThrows(StatusRuntimeException.class, () -> stub.getInstance(get("ghost")));

            assertEquals(Status.Code.NOT_FOUND, ex.getStatus().getCode());
        }
    }
}
