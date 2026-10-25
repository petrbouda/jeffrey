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

package cafe.jeffrey.hub.client;

import cafe.jeffrey.hub.api.v1.GetProjectRequest;
import cafe.jeffrey.hub.api.v1.GetProjectResponse;
import cafe.jeffrey.hub.api.v1.ListWorkspacesRequest;
import cafe.jeffrey.hub.api.v1.ListWorkspacesResponse;
import cafe.jeffrey.hub.api.v1.ProjectInfo;
import cafe.jeffrey.hub.api.v1.ProjectServiceGrpc;
import cafe.jeffrey.hub.api.v1.WorkspaceInfo;
import cafe.jeffrey.hub.api.v1.WorkspaceServiceGrpc;
import cafe.jeffrey.hub.client.dto.RemoteProjectResponse;
import cafe.jeffrey.microscope.grpc.client.GrpcHubConnection;
import cafe.jeffrey.microscope.model.workspace.WorkspaceStatus;
import io.grpc.Context;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiscoveryClientTest {

    private static final String WORKSPACE_ID = "ws-1";
    private static final String MISSING_WORKSPACE_ID = "missing";
    private static final String PROJECT_ID = "proj-1";
    private static final String PROJECT_WITHOUT_TIMESTAMP_ID = "proj-no-time";
    private static final Instant PROJECT_CREATED_AT = Instant.parse("2026-03-01T12:00:00.123Z");

    /**
     * The listing hangs for as long as this is set, so a deadline can be seen to cancel it.
     */
    private volatile boolean slowListing;

    private final CountDownLatch slowCallCancelled = new CountDownLatch(1);
    private final ScheduledExecutorService deadlineScheduler = Executors.newSingleThreadScheduledExecutor();

    private Server server;
    private ManagedChannel channel;
    private DiscoveryClient client;

    @BeforeEach
    void start() throws IOException {
        String name = InProcessServerBuilder.generateName();
        server = InProcessServerBuilder.forName(name)
                .addService(new WorkspaceServiceGrpc.WorkspaceServiceImplBase() {
                    @Override
                    public void listWorkspaces(
                            ListWorkspacesRequest request,
                            StreamObserver<ListWorkspacesResponse> observer) {

                        if (slowListing) {
                            Context.current().addListener(_ -> slowCallCancelled.countDown(), Runnable::run);
                            return;
                        }
                        observer.onNext(ListWorkspacesResponse.newBuilder()
                                .addWorkspaces(WorkspaceInfo.newBuilder().setId(WORKSPACE_ID).setName("Production"))
                                .build());
                        observer.onCompleted();
                    }
                })
                .addService(new ProjectServiceGrpc.ProjectServiceImplBase() {
                    @Override
                    public void getProject(GetProjectRequest request, StreamObserver<GetProjectResponse> observer) {
                        ProjectInfo.Builder project = ProjectInfo.newBuilder()
                                .setId(request.getProjectId())
                                .setWorkspaceId(request.getWorkspaceId());
                        if (PROJECT_ID.equals(request.getProjectId())) {
                            project.setCreatedAt(PROJECT_CREATED_AT.toEpochMilli());
                        }
                        observer.onNext(GetProjectResponse.newBuilder().setProject(project).build());
                        observer.onCompleted();
                    }
                })
                .build()
                .start();
        channel = InProcessChannelBuilder.forName(name).build();
        client = new GrpcDiscoveryClient(new GrpcHubConnection(channel) { });
    }

    @AfterEach
    void stop() {
        channel.shutdownNow();
        server.shutdownNow();
        deadlineScheduler.shutdownNow();
    }

    @Nested
    class WorkspaceLookup {

        @Test
        void uiLookupMapsANotFoundWorkspaceToUnavailable() {
            DiscoveryClient.WorkspaceResult result = client.workspace(MISSING_WORKSPACE_ID);

            assertEquals(WorkspaceStatus.UNAVAILABLE, result.status());
        }

        @Test
        void strictLookupReturnsEmptyOnlyForNotFound() {
            Optional<?> result = client.workspaceOrThrow(MISSING_WORKSPACE_ID);

            assertTrue(result.isEmpty());
        }

        @Test
        void lookupFindsAWorkspaceTheListingHolds() {
            DiscoveryClient.WorkspaceResult result = client.workspace(WORKSPACE_ID);

            assertEquals(WorkspaceStatus.AVAILABLE, result.status());
            assertEquals("Production", result.info().name());
        }

        @Test
        void strictLookupPreservesDeadlineStatusAndCancelsTheRpc() throws Exception {
            Context.CancellableContext context = Context.current().withDeadlineAfter(
                    50, TimeUnit.MILLISECONDS, deadlineScheduler);
            try {
                slowListing = true;
                StatusRuntimeException exception = assertThrows(StatusRuntimeException.class,
                        () -> context.call(() -> client.workspaceOrThrow(WORKSPACE_ID)));

                assertEquals(Status.Code.DEADLINE_EXCEEDED, exception.getStatus().getCode());
                assertTrue(slowCallCancelled.await(1, TimeUnit.SECONDS));
            } finally {
                context.cancel(null);
            }
        }
    }

    @Nested
    class ProjectLookup {

        @Test
        void keepsTheCreationInstantToTheMillisecond() {
            RemoteProjectResponse project = client.project(WORKSPACE_ID, PROJECT_ID).orElseThrow();

            assertEquals(PROJECT_CREATED_AT, project.createdAt());
        }

        @Test
        void aProjectWithoutATimestampHasNoCreationInstant() {
            RemoteProjectResponse project = client.project(WORKSPACE_ID, PROJECT_WITHOUT_TIMESTAMP_ID).orElseThrow();

            assertNull(project.createdAt());
        }
    }
}
