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

package cafe.jeffrey.server.core.grpc;

import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import jdk.jfr.Recording;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.io.TempDir;
import cafe.jeffrey.server.api.v1.EventBatch;
import cafe.jeffrey.server.api.v1.EventStreamingServiceGrpc;
import cafe.jeffrey.server.api.v1.LiveStreamingRequest;
import cafe.jeffrey.server.api.v1.ReplayStreamingRequest;
import cafe.jeffrey.server.core.ServerJeffreyDirs;
import cafe.jeffrey.server.core.project.repository.RepositoryStorage;
import cafe.jeffrey.server.core.streaming.LiveStreamingManager;
import cafe.jeffrey.server.core.streaming.ReplayStreamingManager;
import cafe.jeffrey.server.persistence.model.SessionWithRepository;
import cafe.jeffrey.server.persistence.repository.ServerPlatformRepositories;
import cafe.jeffrey.shared.common.model.ProjectInstanceSessionInfo;
import cafe.jeffrey.shared.common.model.RepositoryInfo;
import cafe.jeffrey.shared.common.model.RepositoryType;

import cafe.jeffrey.shared.common.filesystem.FileSystemUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EventStreamingGrpcServiceTest {

    private static final String SESSION_ID = "session-001";

    private Server server;
    private ManagedChannel channel;

    private EventStreamingServiceGrpc.EventStreamingServiceStub startServer(
            EventStreamingGrpcService service) throws IOException {

        String name = InProcessServerBuilder.generateName();
        server = InProcessServerBuilder.forName(name)
                .directExecutor()
                .addService(service)
                .build()
                .start();
        channel = InProcessChannelBuilder.forName(name)
                .directExecutor()
                .build();
        return EventStreamingServiceGrpc.newStub(channel);
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

    // ========== Replay Streaming ==========

    @Nested
    class ReplayStreamingValidation {

        @Test
        void sessionNotFound_returnsNotFound(@TempDir Path tempDir) throws Exception {
            var service = serviceWithNoSession(tempDir);
            var stub = startServer(service);
            var observer = new TestStreamObserver();

            stub.replayStreaming(
                    ReplayStreamingRequest.newBuilder()
                            .setSessionId("non-existent")
                            .addEventTypes("jdk.CPULoad")
                            .build(),
                    observer);

            assertTrue(observer.errorLatch.await(5, TimeUnit.SECONDS));
            assertStatus(Status.Code.NOT_FOUND, observer.error);
        }

        @Test
        void emptyEventTypes_returnsInvalidArgument(@TempDir Path tempDir) throws Exception {
            var service = serviceWithSession(tempDir);
            var stub = startServer(service);
            var observer = new TestStreamObserver();

            stub.replayStreaming(
                    ReplayStreamingRequest.newBuilder()
                            .setSessionId(SESSION_ID)
                            .build(),
                    observer);

            assertTrue(observer.errorLatch.await(5, TimeUnit.SECONDS));
            assertStatus(Status.Code.INVALID_ARGUMENT, observer.error);
        }

        @Test
        void noRecordingFiles_returnsNotFound(@TempDir Path tempDir) throws Exception {
            var service = serviceWithSession(tempDir, List.of());
            var stub = startServer(service);
            var observer = new TestStreamObserver();

            stub.replayStreaming(
                    ReplayStreamingRequest.newBuilder()
                            .setSessionId(SESSION_ID)
                            .addEventTypes("jdk.CPULoad")
                            .build(),
                    observer);

            assertTrue(observer.errorLatch.await(5, TimeUnit.SECONDS));
            assertStatus(Status.Code.NOT_FOUND, observer.error);
        }

        @Test
        void invalidTimeWindow_returnsInvalidArgument(@TempDir Path tempDir) throws Exception {
            var service = serviceWithSession(tempDir, List.of(resolveJfr("profile-1.jfr")));
            var stub = startServer(service);
            var observer = new TestStreamObserver();

            Instant now = Instant.now();
            stub.replayStreaming(
                    ReplayStreamingRequest.newBuilder()
                            .setSessionId(SESSION_ID)
                            .addEventTypes("jdk.CPULoad")
                            .setStartTime(now.toEpochMilli())
                            .setEndTime(now.minusSeconds(60).toEpochMilli())
                            .build(),
                    observer);

            assertTrue(observer.errorLatch.await(5, TimeUnit.SECONDS));
            assertStatus(Status.Code.INVALID_ARGUMENT, observer.error);
        }
    }

    @Nested
    class ReplayStreamingIntegration {

        @Test
        void streamsEventsFromRecordingFiles(@TempDir Path tempDir) throws Exception {
            var service = serviceWithSession(tempDir, List.of(resolveJfr("profile-1.jfr")));
            var stub = startServer(service);
            var observer = new TestStreamObserver();

            stub.replayStreaming(
                    ReplayStreamingRequest.newBuilder()
                            .setSessionId(SESSION_ID)
                            .addEventTypes("jdk.CPULoad")
                            .build(),
                    observer);

            assertTrue(observer.completeLatch.await(30, TimeUnit.SECONDS), "Stream should complete");
            assertFalse(observer.batches.isEmpty(), "Should receive at least one batch");

            long totalEvents = observer.batches.stream().mapToInt(EventBatch::getEventsCount).sum();
            assertTrue(totalEvents > 0, "Should receive events");

            observer.batches.stream()
                    .flatMap(batch -> batch.getEventsList().stream())
                    .forEach(event -> assertEquals("jdk.CPULoad", event.getEventType()));
        }

        @Test
        void respectsTimeWindow(@TempDir Path tempDir) throws Exception {
            // Recording starts at 2025-12-20T00:12:24Z, lasts 900s
            Instant recordingStart = Instant.parse("2025-12-20T00:12:24Z");
            Instant windowEnd = recordingStart.plusSeconds(300); // first 5 minutes of 15

            var service = serviceWithSession(tempDir, List.of(resolveJfr("profile-1.jfr")));
            var stub = startServer(service);
            var observer = new TestStreamObserver();

            stub.replayStreaming(
                    ReplayStreamingRequest.newBuilder()
                            .setSessionId(SESSION_ID)
                            .addEventTypes("jdk.CPULoad")
                            .setStartTime(recordingStart.toEpochMilli())
                            .setEndTime(windowEnd.toEpochMilli())
                            .build(),
                    observer);

            assertTrue(observer.completeLatch.await(30, TimeUnit.SECONDS));
            long windowedEvents = observer.batches.stream().mapToInt(EventBatch::getEventsCount).sum();

            // Windowed should have fewer events than the full 899
            assertTrue(windowedEvents > 0, "Should receive some events in the window");
            assertTrue(windowedEvents < 899, "Windowed events should be fewer than total");
        }
    }

    // ========== Live Streaming ==========

    @Nested
    class LiveStreamingValidation {

        @Test
        void sessionNotFound_returnsNotFound(@TempDir Path tempDir) throws Exception {
            var service = serviceWithNoSession(tempDir);
            var stub = startServer(service);
            var observer = new TestStreamObserver();

            stub.liveStreaming(
                    LiveStreamingRequest.newBuilder()
                            .setSessionId("non-existent")
                            .addEventTypes("jdk.CPULoad")
                            .build(),
                    observer);

            assertTrue(observer.errorLatch.await(5, TimeUnit.SECONDS));
            assertStatus(Status.Code.NOT_FOUND, observer.error);
        }

        @Test
        void emptyEventTypes_returnsInvalidArgument(@TempDir Path tempDir) throws Exception {
            var service = serviceWithSession(tempDir);
            var stub = startServer(service);
            var observer = new TestStreamObserver();

            stub.liveStreaming(
                    LiveStreamingRequest.newBuilder()
                            .setSessionId(SESSION_ID)
                            .build(),
                    observer);

            assertTrue(observer.errorLatch.await(5, TimeUnit.SECONDS));
            assertStatus(Status.Code.INVALID_ARGUMENT, observer.error);
        }

        @Test
        void streamingRepoNotAvailable_returnsUnavailable(@TempDir Path tempDir) throws Exception {
            // Session exists but the streaming-repo directory does not
            var service = serviceWithSession(tempDir);
            var stub = startServer(service);
            var observer = new TestStreamObserver();

            stub.liveStreaming(
                    LiveStreamingRequest.newBuilder()
                            .setSessionId(SESSION_ID)
                            .addEventTypes("jdk.CPULoad")
                            .build(),
                    observer);

            assertTrue(observer.errorLatch.await(5, TimeUnit.SECONDS));
            assertStatus(Status.Code.UNAVAILABLE, observer.error);
        }
    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    class LiveStreamingIntegration {

        private Recording recording;
        private Path jfrRepoPath;

        @BeforeAll
        void startRecording() {
            recording = new Recording();
            recording.enable("jdk.CPULoad").withPeriod(Duration.ofMillis(10));
            recording.setToDisk(true);
            recording.start();
            jfrRepoPath = Path.of(System.getProperty("jdk.jfr.repository"));
        }

        @AfterAll
        void stopRecording() {
            recording.stop();
            recording.close();
        }

        @Test
        void streamsLiveEvents(@TempDir Path tempDir) throws Exception {
            // Create directory structure so SessionPaths resolves to the real JFR repo
            Path streamingRepo = tempDir.resolve("workspaces/ws/proj/session/streaming-repo");
            Files.createDirectories(streamingRepo.getParent());
            Files.createSymbolicLink(streamingRepo, jfrRepoPath);

            var service = serviceWithLiveSession(tempDir);
            var stub = startServer(service);
            var observer = new TestStreamObserver();

            stub.liveStreaming(
                    LiveStreamingRequest.newBuilder()
                            .setSessionId(SESSION_ID)
                            .addEventTypes("jdk.CPULoad")
                            .build(),
                    observer);

            // Wait for at least one batch with events
            assertEventually(10, TimeUnit.SECONDS, () ->
                    observer.batches.stream().anyMatch(b -> b.getEventsCount() > 0));

            // Cancel the stream by shutting down the channel
            channel.shutdownNow();
        }
    }

    // ========== Helpers ==========

    private EventStreamingGrpcService serviceWithNoSession(Path tempDir) {
        var repositories = mock(ServerPlatformRepositories.class);
        when(repositories.findSessionWithRepositoryById(any())).thenReturn(Optional.empty());

        return new EventStreamingGrpcService(
                new ServerJeffreyDirs(tempDir),
                repositories,
                new LiveStreamingManager(),
                new ReplayStreamingManager(),
                mock(RepositoryStorage.Factory.class));
    }

    private EventStreamingGrpcService serviceWithSession(Path tempDir) {
        return serviceWithSession(tempDir, null);
    }

    private EventStreamingGrpcService serviceWithSession(Path tempDir, List<Path> recordingFiles) {
        var repositories = mock(ServerPlatformRepositories.class);
        when(repositories.findSessionWithRepositoryById(SESSION_ID))
                .thenReturn(Optional.of(testSession()));

        RepositoryStorage.Factory storageFactory = mock(RepositoryStorage.Factory.class);
        if (recordingFiles != null) {
            var storage = mock(RepositoryStorage.class);
            when(storage.recordings(SESSION_ID, null)).thenReturn(recordingFiles);
            when(storageFactory.apply(any())).thenReturn(storage);
        }

        return new EventStreamingGrpcService(
                new ServerJeffreyDirs(tempDir),
                repositories,
                new LiveStreamingManager(),
                new ReplayStreamingManager(),
                storageFactory);
    }

    private EventStreamingGrpcService serviceWithLiveSession(Path tempDir) {
        var repositories = mock(ServerPlatformRepositories.class);
        when(repositories.findSessionWithRepositoryById(SESSION_ID))
                .thenReturn(Optional.of(testSession()));

        return new EventStreamingGrpcService(
                new ServerJeffreyDirs(tempDir),
                repositories,
                new LiveStreamingManager(),
                new ReplayStreamingManager(),
                mock(RepositoryStorage.Factory.class));
    }

    private static SessionWithRepository testSession() {
        var repoInfo = new RepositoryInfo("repo-1", RepositoryType.JDK, null, "ws", "proj");
        var sessionInfo = new ProjectInstanceSessionInfo(
                SESSION_ID, "repo-1", "instance-1", 0,
                Path.of("session"), null, null, null);
        return new SessionWithRepository("proj-1", repoInfo, sessionInfo);
    }

    private static Path resolveJfr(String name) {
        return FileSystemUtils.classpathPath("jfrs/" + name);
    }

    private static void assertStatus(Status.Code expected, Throwable error) {
        assertNotNull(error, "Expected an error");
        assertInstanceOf(StatusRuntimeException.class, error);
        assertEquals(expected, ((StatusRuntimeException) error).getStatus().getCode());
    }

    private static void assertEventually(long timeout, TimeUnit unit, Runnable assertion) throws InterruptedException {
        long deadline = System.nanoTime() + unit.toNanos(timeout);
        while (System.nanoTime() < deadline) {
            try {
                assertion.run();
                return;
            } catch (AssertionError e) {
                Thread.sleep(200);
            }
        }
        assertion.run(); // final attempt — let it throw
    }

    private static class TestStreamObserver implements StreamObserver<EventBatch> {

        final List<EventBatch> batches = Collections.synchronizedList(new ArrayList<>());
        final CountDownLatch completeLatch = new CountDownLatch(1);
        final CountDownLatch errorLatch = new CountDownLatch(1);
        volatile Throwable error;

        @Override
        public void onNext(EventBatch value) {
            batches.add(value);
        }

        @Override
        public void onError(Throwable t) {
            error = t;
            errorLatch.countDown();
        }

        @Override
        public void onCompleted() {
            completeLatch.countDown();
        }
    }
}
