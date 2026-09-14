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
import io.grpc.stub.ServerCallStreamObserver;
import io.grpc.stub.StreamObserver;
import com.google.protobuf.ByteString;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
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
    private final AtomicBoolean serveChunks = new AtomicBoolean(false);

    /** Enough chunks to fill the client's pipe many times over before the consumer is heard. */
    private static final int SERVED_CHUNKS = 256;
    private static final int SERVED_CHUNK_SIZE = 64 * 1024;

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

            /**
             * Sends chunks from a thread of its own until the client cancels or the file is
             * over, the way the hub's streaming executor does.
             */
            private void serve(StreamObserver<DataChunk> observer) {
                ServerCallStreamObserver<DataChunk> call = (ServerCallStreamObserver<DataChunk>) observer;
                call.setOnCancelHandler(cancelled::countDown);
                arrived.countDown();
                Thread.ofVirtual().start(() -> {
                    ByteString data = ByteString.copyFrom(new byte[SERVED_CHUNK_SIZE]);
                    for (int i = 0; i < SERVED_CHUNKS && !call.isCancelled(); i++) {
                        call.onNext(DataChunk.newBuilder().setData(data).setTotalSize(SERVED_CHUNKS * (long) SERVED_CHUNK_SIZE).build());
                    }
                    if (!call.isCancelled()) {
                        call.onCompleted();
                    }
                });
            }

            @Override
            public void downloadFile(DownloadFileRequest request, StreamObserver<DataChunk> observer) {
                if (serveChunks.get()) {
                    serve(observer);
                } else {
                    hold(observer);
                }
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

    /**
     * A consumer that stops reading part-way — a cancelled download, a full disk — must get its
     * own exception back promptly, with the call cut off behind it. The writer used to be joined
     * before the pipe was closed, which parked both threads on each other for any file larger
     * than the pipe.
     */
    @Test
    void aConsumerThatStopsReadingGetsItsOwnFailureBackAndReleasesTheCall() throws Exception {
        serveChunks.set(true);
        CompletableFuture<Void> future = CompletableFuture.runAsync(
                () -> client.streamFile("session", "file", (in, length) -> {
                    readSome(in);
                    throw new IllegalStateException("gave up");
                }),
                Executors.newVirtualThreadPerTaskExecutor());

        ExecutionException failure = assertThrows(ExecutionException.class,
                () -> future.get(5, TimeUnit.SECONDS));

        assertEquals(IllegalStateException.class, failure.getCause().getClass());
        assertEquals("gave up", failure.getCause().getMessage());
        assertTrue(cancelled.await(5, TimeUnit.SECONDS), "the abandoned call reaches the server as cancelled");
    }

    /**
     * The whole file, read to the end, still arrives intact through the same pipe.
     */
    @Test
    void aConsumerThatReadsToTheEndGetsEveryByte() throws Exception {
        serveChunks.set(true);
        long[] total = {0};
        CompletableFuture<Void> future = CompletableFuture.runAsync(
                () -> client.streamFile("session", "file", (in, length) -> {
                    assertEquals(SERVED_CHUNKS * (long) SERVED_CHUNK_SIZE, length);
                    total[0] = in.transferTo(OutputStream.nullOutputStream());
                }),
                Executors.newVirtualThreadPerTaskExecutor());

        future.get(10, TimeUnit.SECONDS);

        assertEquals(SERVED_CHUNKS * (long) SERVED_CHUNK_SIZE, total[0]);
    }

    private static void readSome(InputStream in) throws IOException {
        byte[] buffer = new byte[1024];
        if (in.read(buffer) < 0) {
            throw new IOException("nothing arrived");
        }
    }
}
