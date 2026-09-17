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

package cafe.jeffrey.hub.client;

import cafe.jeffrey.hub.api.v1.GetWorkspaceRequest;
import cafe.jeffrey.hub.api.v1.GetWorkspaceResponse;
import cafe.jeffrey.hub.api.v1.WorkspaceServiceGrpc;
import cafe.jeffrey.microscope.grpc.client.GrpcHubConnection;
import cafe.jeffrey.shared.common.model.workspace.WorkspaceStatus;
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
