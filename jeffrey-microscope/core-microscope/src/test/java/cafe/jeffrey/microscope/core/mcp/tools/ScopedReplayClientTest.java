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

package cafe.jeffrey.microscope.core.mcp.tools;

import cafe.jeffrey.hub.api.v1.EventBatch;
import cafe.jeffrey.hub.api.v1.EventStreamingServiceGrpc;
import cafe.jeffrey.hub.api.v1.ReplayStreamingRequest;
import cafe.jeffrey.microscope.grpc.client.EventStreamingClient;
import cafe.jeffrey.microscope.grpc.client.GrpcHubConnection;
import cafe.jeffrey.microscope.grpc.client.ReplaySubscriptionRequest;
import cafe.jeffrey.microscope.grpc.client.StreamingCallbacks;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.Status;
import io.grpc.netty.NettyChannelBuilder;
import io.grpc.netty.NettyServerBuilder;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ScopedReplayClientTest {
    @Test
    void scopedRequestNeverInvokesLegacyRpcOnOlderHub() throws Exception {
        AtomicInteger legacyCalls = new AtomicInteger();
        Server server = NettyServerBuilder.forPort(0).addService(new EventStreamingServiceGrpc.EventStreamingServiceImplBase() {
            @Override
            public void replayStreaming(ReplayStreamingRequest request, StreamObserver<EventBatch> observer) {
                legacyCalls.incrementAndGet();
                observer.onCompleted();
            }
        }).build().start();
        ManagedChannel channel = NettyChannelBuilder.forAddress("localhost", server.getPort()).usePlaintext().build();
        try {
            GrpcHubConnection connection = mock(GrpcHubConnection.class);
            when(connection.getChannel()).thenReturn(channel);
            try (EventStreamingClient client = new EventStreamingClient(connection)) {
                CompletableFuture<Status.Code> scoped = new CompletableFuture<>();
                client.subscribeReplayStreaming(new ReplaySubscriptionRequest("session", Set.of("jdk.CPULoad"),
                                null, null, "workspace", "project"),
                        new StreamingCallbacks(batch -> {}, () -> scoped.complete(Status.Code.OK),
                                error -> scoped.complete(Status.fromThrowable(error).getCode())));
                assertEquals(Status.Code.UNIMPLEMENTED, scoped.get(5, TimeUnit.SECONDS));
                assertEquals(0, legacyCalls.get());
                CompletableFuture<Status.Code> legacy = new CompletableFuture<>();
                client.subscribeReplayStreaming(new ReplaySubscriptionRequest("session", Set.of("jdk.CPULoad"), null, null),
                        new StreamingCallbacks(batch -> {}, () -> legacy.complete(Status.Code.OK),
                                error -> legacy.complete(Status.fromThrowable(error).getCode())));
                assertEquals(Status.Code.OK, legacy.get(5, TimeUnit.SECONDS));
                assertEquals(1, legacyCalls.get());
            }
        } finally {
            channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
            server.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
        }
    }
}
