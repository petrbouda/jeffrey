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
import cafe.jeffrey.hub.api.v1.DownloadArtifactFileRequest;
import cafe.jeffrey.hub.api.v1.DownloadMergedRecordingsRequest;
import cafe.jeffrey.hub.api.v1.RecordingDownloadServiceGrpc;
import cafe.jeffrey.microscope.grpc.client.GrpcHubConnection;
import cafe.jeffrey.shared.common.filesystem.TempDirectory;
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
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.Resource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecordingStreamClientContextTest {

    @TempDir
    Path directory;
    private Server server;
    private ManagedChannel channel;
    private RecordingStreamClient client;
    private final ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor();
    private final CountDownLatch arrived = new CountDownLatch(1);
    private final CountDownLatch cancelled = new CountDownLatch(1);
    private final CountDownLatch tempOpened = new CountDownLatch(1);
    private final AtomicReference<Deadline> serverDeadline = new AtomicReference<>();

    @BeforeEach
    void start() throws Exception {
        String name = InProcessServerBuilder.generateName();
        var service = new RecordingDownloadServiceGrpc.RecordingDownloadServiceImplBase() {
            private void hold(StreamObserver<DataChunk> observer) {
                Context context = Context.current();
                serverDeadline.set(context.getDeadline());
                context.addListener(ignored -> cancelled.countDown(), Runnable::run);
                arrived.countDown();
            }

            @Override
            public void downloadMergedRecordings(DownloadMergedRecordingsRequest request, StreamObserver<DataChunk> observer) {
                hold(observer);
            }

            @Override
            public void downloadArtifactFile(DownloadArtifactFileRequest request, StreamObserver<DataChunk> observer) {
                hold(observer);
            }
        };
        server = InProcessServerBuilder.forName(name).directExecutor().addService(service).build().start();
        channel = InProcessChannelBuilder.forName(name).directExecutor().build();
        client = new RecordingStreamClient(new GrpcHubConnection(channel) {}, () -> {
            TempDirectory temp = new TempDirectory(directory.resolve("download"));
            tempOpened.countDown();
            return temp;
        });
    }

    @AfterEach
    void close() {
        channel.shutdownNow();
        server.shutdownNow();
        timer.shutdownNow();
    }

    private CompletableFuture<Resource> download(boolean artifact) {
        return artifact ? client.downloadArtifactFile("session", "file")
                : client.downloadRecordings("session", List.of("file"));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void propagatesDeadlineToTheServerAcrossTheAsyncDownload(boolean artifact) throws Exception {
        try (Context.CancellableContext context = Context.current().withDeadlineAfter(2, TimeUnit.SECONDS, timer)) {
            CompletableFuture<Resource> future = context.call(() -> download(artifact));
            assertTrue(arrived.await(5, TimeUnit.SECONDS));
            assertNotNull(serverDeadline.get(), "the asynchronous RPC must carry the caller's deadline");
            ExecutionException failure = assertThrows(ExecutionException.class,
                    () -> future.get(5, TimeUnit.SECONDS));
            assertEquals(Status.Code.DEADLINE_EXCEEDED, Status.fromThrowable(failure.getCause()).getCode());
            assertTrue(cancelled.await(5, TimeUnit.SECONDS));
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void cancelsTheRpcAndRemovesPartialTemporaryFiles(boolean artifact) throws Exception {
        try (Context.CancellableContext context = Context.current().withCancellation()) {
            CompletableFuture<Resource> future = context.call(() -> download(artifact));
            assertTrue(arrived.await(5, TimeUnit.SECONDS));
            assertTrue(tempOpened.await(5, TimeUnit.SECONDS));
            context.cancel(null);
            ExecutionException failure = assertThrows(ExecutionException.class,
                    () -> future.get(2, TimeUnit.SECONDS));
            assertEquals(Status.Code.CANCELLED, Status.fromThrowable(failure.getCause()).getCode());
            assertTrue(cancelled.await(5, TimeUnit.SECONDS));
            assertFalse(Files.exists(directory.resolve("download")), "failed downloads must remove partial files");
        }
    }
}
