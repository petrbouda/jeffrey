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

package cafe.jeffrey.hub.core.grpc;

import io.grpc.Status;
import io.grpc.CallOptions;
import io.grpc.MethodDescriptor;
import io.grpc.protobuf.ProtoUtils;
import io.grpc.stub.ClientCalls;
import io.grpc.StatusRuntimeException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import cafe.jeffrey.hub.api.v1.EventBatch;
import cafe.jeffrey.hub.api.v1.EventStreamingServiceGrpc;
import cafe.jeffrey.hub.api.v1.ReplayStreamingRequest;
import cafe.jeffrey.hub.core.HubJeffreyDirs;
import cafe.jeffrey.hub.core.project.repository.RepositoryStorage;
import cafe.jeffrey.hub.core.streaming.ReplayStreamingManager;
import cafe.jeffrey.hub.core.streaming.ScopedReplaySource;
import cafe.jeffrey.hub.persistence.api.SessionWithRepository;
import cafe.jeffrey.hub.persistence.api.HubPlatformRepositories;
import cafe.jeffrey.shared.common.model.ProjectInstanceSessionInfo;
import cafe.jeffrey.shared.common.model.RepositoryInfo;
import cafe.jeffrey.shared.common.model.RepositoryType;

import cafe.jeffrey.shared.common.filesystem.FileSystemUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EventStreamingGrpcServiceTest {

    private static final String SESSION_ID = "session-001";

    private InProcessGrpcServer grpc;

    private EventStreamingServiceGrpc.EventStreamingServiceStub startServer(
            EventStreamingGrpcService service) {
        grpc = InProcessGrpcServer.start(service);
        return EventStreamingServiceGrpc.newStub(grpc.channel());
    }

    @AfterEach
    void shutdown() {
        if (grpc != null) {
            grpc.close();
        }
    }

    @Test
    void retiredLiveRpcReturnsUnimplemented(@TempDir Path tempDir) throws Exception {
        startServer(serviceWithNoSession(tempDir));
        MethodDescriptor<ReplayStreamingRequest, EventBatch> retiredMethod =
                MethodDescriptor.<ReplayStreamingRequest, EventBatch>newBuilder()
                        .setType(MethodDescriptor.MethodType.SERVER_STREAMING)
                        .setFullMethodName(EventStreamingServiceGrpc.SERVICE_NAME + "/LiveStreaming")
                        .setRequestMarshaller(ProtoUtils.marshaller(ReplayStreamingRequest.getDefaultInstance()))
                        .setResponseMarshaller(ProtoUtils.marshaller(EventBatch.getDefaultInstance()))
                        .build();
        var observer = new TestStreamObserver<EventBatch>();
        ClientCalls.asyncServerStreamingCall(
                grpc.channel().newCall(retiredMethod, CallOptions.DEFAULT),
                ReplayStreamingRequest.newBuilder().setSessionId(SESSION_ID)
                        .addEventTypes("jdk.CPULoad").build(), observer);

        assertTrue(observer.errorLatch.await(5, TimeUnit.SECONDS));
        assertStatus(Status.Code.UNIMPLEMENTED, observer.error);
    }

    // ========== Replay Streaming ==========

    @Nested
    class ReplayStreamingValidation {

        @Test
        void sessionNotFound_returnsNotFound(@TempDir Path tempDir) throws Exception {
            var service = serviceWithNoSession(tempDir);
            var stub = startServer(service);
            var observer = new TestStreamObserver<EventBatch>();

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
            var observer = new TestStreamObserver<EventBatch>();

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
            var observer = new TestStreamObserver<EventBatch>();

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
            var observer = new TestStreamObserver<EventBatch>();

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
            var observer = new TestStreamObserver<EventBatch>();

            stub.replayStreaming(
                    ReplayStreamingRequest.newBuilder()
                            .setSessionId(SESSION_ID)
                            .addEventTypes("jdk.CPULoad")
                            .build(),
                    observer);

            assertTrue(observer.completeLatch.await(30, TimeUnit.SECONDS), "Stream should complete");
            assertFalse(observer.messages.isEmpty(), "Should receive at least one batch");

            long totalEvents = observer.messages.stream().mapToInt(EventBatch::getEventsCount).sum();
            assertTrue(totalEvents > 0, "Should receive events");

            observer.messages.stream()
                    .flatMap(batch -> batch.getEventsList().stream())
                    .forEach(event -> assertEquals("jdk.CPULoad", event.getEventType()));
        }

        @Test
        void corruptedFileInTheMiddle_isSkipped_andStreamCompletesWithRemainingEvents(@TempDir Path tempDir) throws Exception {
            // A truncated JFR file between two valid ones: the corrupted file must be skipped
            // without terminating the call, and events from BOTH valid files must arrive,
            // followed by a single onCompleted (never onError).
            Path corrupted = tempDir.resolve("corrupted.jfr");
            byte[] valid = Files.readAllBytes(resolveJfr("profile-1.jfr"));
            Files.write(corrupted, Arrays.copyOf(valid, 1024));

            var service = serviceWithSession(tempDir, List.of(
                    resolveJfr("profile-1.jfr"), corrupted, resolveJfr("profile-2.jfr")));
            var stub = startServer(service);
            var observer = new TestStreamObserver<EventBatch>();

            stub.replayStreaming(
                    ReplayStreamingRequest.newBuilder()
                            .setSessionId(SESSION_ID)
                            .addEventTypes("jdk.CPULoad")
                            .build(),
                    observer);

            assertTrue(observer.completeLatch.await(30, TimeUnit.SECONDS),
                    "Stream should complete despite the corrupted file");
            assertNull(observer.error, "A skipped corrupted file must not produce a terminal error");

            // profile-1.jfr alone has ~899 CPULoad events — receiving more proves that the
            // stream continued into profile-2.jfr after skipping the corrupted file
            long totalEvents = observer.messages.stream().mapToInt(EventBatch::getEventsCount).sum();
            assertTrue(totalEvents > 899,
                    "Expected events from both valid files around the corrupted one, got " + totalEvents);
        }

        @Test
        void respectsTimeWindow(@TempDir Path tempDir) throws Exception {
            // Recording starts at 2025-12-20T00:12:24Z, lasts 900s
            Instant recordingStart = Instant.parse("2025-12-20T00:12:24Z");
            Instant windowEnd = recordingStart.plusSeconds(300); // first 5 minutes of 15

            var service = serviceWithSession(tempDir, List.of(resolveJfr("profile-1.jfr")));
            var stub = startServer(service);
            var observer = new TestStreamObserver<EventBatch>();

            stub.replayStreaming(
                    ReplayStreamingRequest.newBuilder()
                            .setSessionId(SESSION_ID)
                            .addEventTypes("jdk.CPULoad")
                            .setStartTime(recordingStart.toEpochMilli())
                            .setEndTime(windowEnd.toEpochMilli())
                            .build(),
                    observer);

            assertTrue(observer.completeLatch.await(30, TimeUnit.SECONDS));
            long windowedEvents = observer.messages.stream().mapToInt(EventBatch::getEventsCount).sum();

            // Windowed should have fewer events than the full 899
            assertTrue(windowedEvents > 0, "Should receive some events in the window");
            assertTrue(windowedEvents < 899, "Windowed events should be fewer than total");
        }
    }

    // ========== Helpers ==========

    private EventStreamingGrpcService serviceWithNoSession(Path tempDir) {
        var repositories = mock(HubPlatformRepositories.class);
        when(repositories.findSessionWithRepositoryById(any())).thenReturn(Optional.empty());

        return new EventStreamingGrpcService(
                new HubJeffreyDirs(tempDir),
                repositories,
                new ReplayStreamingManager(),
                mock(RepositoryStorage.Factory.class),
                new ScopedReplaySource(repositories, mock(RepositoryStorage.Factory.class), new HubJeffreyDirs(tempDir)));
    }

    private EventStreamingGrpcService serviceWithSession(Path tempDir) {
        return serviceWithSession(tempDir, null);
    }

    private EventStreamingGrpcService serviceWithSession(Path tempDir, List<Path> recordingFiles) {
        var repositories = mock(HubPlatformRepositories.class);
        when(repositories.findSessionWithRepositoryById(SESSION_ID))
                .thenReturn(Optional.of(testSession()));

        RepositoryStorage.Factory storageFactory = mock(RepositoryStorage.Factory.class);
        if (recordingFiles != null) {
            var storage = mock(RepositoryStorage.class);
            when(storage.recordings(SESSION_ID, null)).thenReturn(recordingFiles);
            when(storageFactory.apply(any())).thenReturn(storage);
        }

        return new EventStreamingGrpcService(
                new HubJeffreyDirs(tempDir),
                repositories,
                new ReplayStreamingManager(),
                storageFactory,
                new ScopedReplaySource(repositories, storageFactory, new HubJeffreyDirs(tempDir)));
    }

    private static SessionWithRepository testSession() {
        var repoInfo = new RepositoryInfo("repo-1", RepositoryType.JDK, null, "ws", "proj");
        var sessionInfo = ProjectInstanceSessionInfo.notRetained(
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

}
