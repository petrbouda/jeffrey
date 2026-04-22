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

package pbouda.jeffrey.server.core.grpc;

import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import pbouda.jeffrey.server.api.v1.*;
import pbouda.jeffrey.server.persistence.repository.ProjectInstanceRepository;
import pbouda.jeffrey.server.persistence.repository.ServerPlatformRepositories;
import pbouda.jeffrey.shared.common.model.ProjectInstanceInfo;
import pbouda.jeffrey.shared.common.model.ProjectInstanceInfo.ProjectInstanceStatus;
import pbouda.jeffrey.shared.common.model.ProjectInstanceSessionInfo;

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

    private Server server;
    private ManagedChannel channel;

    private InstanceServiceGrpc.InstanceServiceBlockingStub startServer(
            InstanceGrpcService service) throws IOException {

        String name = InProcessServerBuilder.generateName();
        server = InProcessServerBuilder.forName(name)
                .directExecutor()
                .addService(service)
                .build()
                .start();
        channel = InProcessChannelBuilder.forName(name)
                .directExecutor()
                .build();
        return InstanceServiceGrpc.newBlockingStub(channel);
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

            var platformRepositories = mock(ServerPlatformRepositories.class);
            when(platformRepositories.newProjectInstanceRepository(PROJECT_ID)).thenReturn(instanceRepo);

            var stub = startServer(new InstanceGrpcService(platformRepositories, FIXED_CLOCK));

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

            var platformRepositories = mock(ServerPlatformRepositories.class);
            when(platformRepositories.newProjectInstanceRepository(PROJECT_ID)).thenReturn(instanceRepo);

            var stub = startServer(new InstanceGrpcService(platformRepositories, FIXED_CLOCK));

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

            var platformRepositories = mock(ServerPlatformRepositories.class);
            when(platformRepositories.newProjectInstanceRepository(PROJECT_ID)).thenReturn(instanceRepo);

            var stub = startServer(new InstanceGrpcService(platformRepositories, FIXED_CLOCK));

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

            var platformRepositories = mock(ServerPlatformRepositories.class);
            when(platformRepositories.newProjectInstanceRepository(PROJECT_ID)).thenReturn(instanceRepo);
            when(platformRepositories.findSessionsByProjectId(PROJECT_ID)).thenReturn(List.of(
                    new ProjectInstanceSessionInfo(
                            "session-active", "repo-1", INSTANCE_ID, 0,
                            Path.of("session-active"), null, null, FIXED_TIME, null),
                    new ProjectInstanceSessionInfo(
                            "session-old", "repo-1", INSTANCE_ID, 1,
                            Path.of("session-old"), null, null,
                            FIXED_TIME.minusSeconds(600), FIXED_TIME.minusSeconds(300)),
                    new ProjectInstanceSessionInfo(
                            "session-only", "repo-1", "inst-2", 0,
                            Path.of("session-only"), null, null,
                            FIXED_TIME, FIXED_TIME.plusSeconds(3600))
            ));

            var stub = startServer(new InstanceGrpcService(platformRepositories, FIXED_CLOCK));

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
    }

    // ========== GetInstance ==========

    @Nested
    class GetInstance {

        @Test
        void returnsInstance() throws Exception {
            var platformRepositories = mock(ServerPlatformRepositories.class);
            when(platformRepositories.findInstanceById(INSTANCE_ID)).thenReturn(Optional.of(
                    new ProjectInstanceInfo(
                            INSTANCE_ID, PROJECT_ID, "host-1",
                            ProjectInstanceStatus.EXPIRED, FIXED_TIME,
                            FIXED_TIME.plusSeconds(3600),
                            FIXED_TIME.plusSeconds(7200),
                            FIXED_TIME.plusSeconds(10800),
                            3, null)
            ));

            var stub = startServer(new InstanceGrpcService(platformRepositories, FIXED_CLOCK));

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
            var platformRepositories = mock(ServerPlatformRepositories.class);
            when(platformRepositories.findInstanceById("non-existent")).thenReturn(Optional.empty());

            var stub = startServer(new InstanceGrpcService(platformRepositories, FIXED_CLOCK));

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

        @Test
        void returnsSessionList() throws Exception {
            var platformRepositories = mock(ServerPlatformRepositories.class);
            when(platformRepositories.findSessionsByInstanceId(INSTANCE_ID)).thenReturn(List.of(
                    new ProjectInstanceSessionInfo(
                            "session-1", "repo-1", INSTANCE_ID, 0,
                            Path.of("session-1"), null,
                            null, FIXED_TIME, null),
                    new ProjectInstanceSessionInfo(
                            "session-2", "repo-1", INSTANCE_ID, 1,
                            Path.of("session-2"), null,
                            null, FIXED_TIME.plusSeconds(600),
                            FIXED_TIME.plusSeconds(1200))
            ));

            var stub = startServer(new InstanceGrpcService(platformRepositories, FIXED_CLOCK));

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

            InstanceSessionInfo second = response.getSessions(1);
            assertEquals("session-2", second.getId());
            assertFalse(second.getIsActive());
            assertTrue(second.hasFinishedAt());
            assertEquals(FIXED_TIME.plusSeconds(1200).toEpochMilli(), second.getFinishedAt());
        }

        @Test
        void returnsEmptyListWhenNoSessions() throws Exception {
            var platformRepositories = mock(ServerPlatformRepositories.class);
            when(platformRepositories.findSessionsByInstanceId(INSTANCE_ID)).thenReturn(List.of());

            var stub = startServer(new InstanceGrpcService(platformRepositories, FIXED_CLOCK));

            ListInstanceSessionsResponse response = stub.listInstanceSessions(
                    ListInstanceSessionsRequest.newBuilder()
                            .setInstanceId(INSTANCE_ID)
                            .build());

            assertEquals(0, response.getSessionsCount());
        }
    }
}
