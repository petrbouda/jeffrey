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

import cafe.jeffrey.hub.api.v1.GetWorkspaceRequest;
import cafe.jeffrey.hub.api.v1.GetWorkspaceResponse;
import cafe.jeffrey.hub.api.v1.WorkspaceServiceGrpc;
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
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiscoveryClientTest {

    private static final String MISSING_WORKSPACE_ID = "missing";
    private static final String SLOW_WORKSPACE_ID = "slow";

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
                    public void getWorkspace(
                            GetWorkspaceRequest request,
                            StreamObserver<GetWorkspaceResponse> observer) {

                        if (MISSING_WORKSPACE_ID.equals(request.getWorkspaceId())) {
                            observer.onError(Status.NOT_FOUND
                                    .withDescription("Workspace not found: " + MISSING_WORKSPACE_ID)
                                    .asRuntimeException());
                            return;
                        }
                        if (SLOW_WORKSPACE_ID.equals(request.getWorkspaceId())) {
                            Context.current().addListener(_ -> slowCallCancelled.countDown(), Runnable::run);
                            return;
                        }
                        observer.onError(Status.INVALID_ARGUMENT.asRuntimeException());
                    }
                })
                .build()
                .start();
        channel = InProcessChannelBuilder.forName(name).build();
        client = new DiscoveryClient(new GrpcHubConnection(channel) { });
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
        void strictLookupPreservesDeadlineStatusAndCancelsTheRpc() throws Exception {
            Context.CancellableContext context = Context.current().withDeadlineAfter(
                    50, TimeUnit.MILLISECONDS, deadlineScheduler);
            try {
                StatusRuntimeException exception = assertThrows(StatusRuntimeException.class,
                        () -> context.call(() -> client.workspaceOrThrow(SLOW_WORKSPACE_ID)));

                assertEquals(Status.Code.DEADLINE_EXCEEDED, exception.getStatus().getCode());
                assertTrue(slowCallCancelled.await(1, TimeUnit.SECONDS));
            } finally {
                context.cancel(null);
            }
        }
    }
}
