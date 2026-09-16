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

import cafe.jeffrey.hub.api.v1.DataChunk;
import cafe.jeffrey.hub.api.v1.DownloadFileRequest;
import cafe.jeffrey.hub.api.v1.FileDownloadServiceGrpc;
import cafe.jeffrey.microscope.grpc.client.GrpcHubConnection;
import io.grpc.Context;
import io.grpc.Deadline;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.Status;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A download is one file per call, and every one of them is a blocking stream the caller drives.
 * What has to hold for both kinds of file: the caller's deadline reaches the hub, and when it
 * expires the call ends rather than hanging on a server that has stopped sending.
 */
class FileStreamClientContextTest {

    private Server server;
    private ManagedChannel channel;
    private FileStreamClient client;
    private final ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor();
    private final CountDownLatch arrived = new CountDownLatch(1);
    private final CountDownLatch cancelled = new CountDownLatch(1);
    private final AtomicReference<Deadline> serverDeadline = new AtomicReference<>();

    @BeforeEach
    void start() throws Exception {
        String name = InProcessServerBuilder.generateName();
        var service = new FileDownloadServiceGrpc.FileDownloadServiceImplBase() {
            private void hold() {
                Context context = Context.current();
                serverDeadline.set(context.getDeadline());
                context.addListener(ignored -> cancelled.countDown(), Runnable::run);
                arrived.countDown();
            }

            @Override
            public void downloadFile(DownloadFileRequest request, StreamObserver<DataChunk> observer) {
                hold();
            }
        };
        server = InProcessServerBuilder.forName(name).directExecutor().addService(service).build().start();
        channel = InProcessChannelBuilder.forName(name).directExecutor().build();
        client = new FileStreamClient(new GrpcHubConnection(channel) {});
    }

    @AfterEach
    void close() {
        channel.shutdownNow();
        server.shutdownNow();
        timer.shutdownNow();
    }

    private void download() {
        client.streamFile("session", "file", (stream, transferred) -> stream.readAllBytes());
    }

    @Test
    @DisplayName("carries the caller's deadline to the hub and ends when it expires")
    void propagatesDeadlineToTheServer() throws Exception {
        try (Context.CancellableContext context = Context.current().withDeadlineAfter(2, TimeUnit.SECONDS, timer)) {
            // The client reports a streaming failure as a RuntimeException carrying the gRPC
            // status as its cause, so the status is read off the chain rather than the type.
            RuntimeException failure = context.call(() -> {
                RuntimeException thrown = assertThrows(RuntimeException.class, () -> download());
                assertTrue(arrived.await(5, TimeUnit.SECONDS));
                return thrown;
            });

            assertNotNull(serverDeadline.get(), "the RPC must carry the caller's deadline");
            assertEquals(Status.Code.DEADLINE_EXCEEDED, Status.fromThrowable(failure).getCode());
            assertTrue(cancelled.await(5, TimeUnit.SECONDS));
        }
    }

    @Test
    @DisplayName("ends the call when the caller's context is cancelled")
    void cancelsTheRpc() throws Exception {
        try (Context.CancellableContext context = Context.current().withCancellation()) {
            timer.schedule(() -> context.cancel(null), 200, TimeUnit.MILLISECONDS);

            RuntimeException failure = context.call(() ->
                    assertThrows(RuntimeException.class, () -> download()));

            assertEquals(Status.Code.CANCELLED, Status.fromThrowable(failure).getCode());
            assertTrue(cancelled.await(5, TimeUnit.SECONDS));
        }
    }
}
