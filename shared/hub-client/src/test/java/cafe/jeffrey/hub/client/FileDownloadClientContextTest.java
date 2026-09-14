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
import org.junit.jupiter.api.Test;

import java.io.OutputStream;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileDownloadClientContextTest {

    private Server server;
    private ManagedChannel channel;
    private FileDownloadClient client;
    private final ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor();
    private final CountDownLatch arrived = new CountDownLatch(1);
    private final CountDownLatch cancelled = new CountDownLatch(1);
    private final AtomicReference<Deadline> serverDeadline = new AtomicReference<>();

    @BeforeEach
    void start() throws Exception {
        String name = InProcessServerBuilder.generateName();
        var service = new FileDownloadServiceGrpc.FileDownloadServiceImplBase() {
            private void hold(StreamObserver<DataChunk> observer) {
                Context context = Context.current();
                serverDeadline.set(context.getDeadline());
                context.addListener(ignored -> cancelled.countDown(), Runnable::run);
                arrived.countDown();
            }

            @Override
            public void downloadFile(DownloadFileRequest request, StreamObserver<DataChunk> observer) {
                hold(observer);
            }
        };
        server = InProcessServerBuilder.forName(name).directExecutor().addService(service).build().start();
        channel = InProcessChannelBuilder.forName(name).directExecutor().build();
        client = new FileDownloadClient(new GrpcHubConnection(channel) {});
    }

    @AfterEach
    void close() {
        channel.shutdownNow();
        server.shutdownNow();
        timer.shutdownNow();
    }

    /**
     * {@code streamFile} blocks its caller, so it runs on its own thread while the test drives the
     * context it was started under; the returned future is that thread's outcome.
     */
    private CompletableFuture<Void> download() {
        Context callerContext = Context.current();
        return CompletableFuture.runAsync(
                () -> callerContext.run(() -> client.streamFile("session", "file", (in, length) -> in.transferTo(OutputStream.nullOutputStream()))),
                Executors.newVirtualThreadPerTaskExecutor());
    }

    @Test
    void propagatesDeadlineToTheServerAcrossTheAsyncDownload() throws Exception {
        try (Context.CancellableContext context = Context.current().withDeadlineAfter(2, TimeUnit.SECONDS, timer)) {
            CompletableFuture<Void> future = context.call(this::download);
            assertTrue(arrived.await(5, TimeUnit.SECONDS));
            assertNotNull(serverDeadline.get(), "the asynchronous RPC must carry the caller's deadline");
            ExecutionException failure = assertThrows(ExecutionException.class,
                    () -> future.get(5, TimeUnit.SECONDS));
            assertEquals(Status.Code.DEADLINE_EXCEEDED, Status.fromThrowable(failure.getCause()).getCode());
            assertTrue(cancelled.await(5, TimeUnit.SECONDS));
        }
    }

    @Test
    void cancellationReachesTheServer() throws Exception {
        try (Context.CancellableContext context = Context.current().withCancellation()) {
            CompletableFuture<Void> future = context.call(this::download);
            assertTrue(arrived.await(5, TimeUnit.SECONDS));
            context.cancel(null);
            ExecutionException failure = assertThrows(ExecutionException.class,
                    () -> future.get(2, TimeUnit.SECONDS));
            assertEquals(Status.Code.CANCELLED, Status.fromThrowable(failure.getCause()).getCode());
            assertTrue(cancelled.await(5, TimeUnit.SECONDS));
        }
    }
}
