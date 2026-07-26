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

import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import cafe.jeffrey.hub.api.v1.StreamWorkspaceEventsRequest;
import cafe.jeffrey.hub.api.v1.WorkspaceEventBatch;
import cafe.jeffrey.hub.api.v1.WorkspaceEventInfo;
import cafe.jeffrey.hub.api.v1.WorkspaceEventsServiceGrpc;
import cafe.jeffrey.hub.client.dto.WorkspaceEventsBatch;
import cafe.jeffrey.microscope.grpc.client.GrpcHubConnection;
import cafe.jeffrey.shared.common.model.workspace.WorkspaceEventType;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkspaceEventsClientStreamTest {

    private static final String WORKSPACE_ID = "ws-1";

    private Server server;
    private ManagedChannel channel;
    private WorkspaceEventsClient client;

    /** Records what the server actually received, so request mapping can be asserted. */
    private final AtomicReference<StreamWorkspaceEventsRequest> received = new AtomicReference<>();
    private final AtomicInteger openStreams = new AtomicInteger();

    @AfterEach
    void shutdown() {
        if (client != null) {
            client.close();
        }
        if (channel != null) {
            channel.shutdownNow();
        }
        if (server != null) {
            server.shutdownNow();
        }
    }

    private void start(WorkspaceEventsServiceGrpc.WorkspaceEventsServiceImplBase service) {
        String name = InProcessServerBuilder.generateName();
        try {
            server = InProcessServerBuilder.forName(name).directExecutor().addService(service).build().start();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        channel = InProcessChannelBuilder.forName(name).directExecutor().build();
        client = new WorkspaceEventsClient(new StubConnection(channel));
    }

    /** Points the client at the in-process channel instead of dialling a real hub. */
    private static final class StubConnection extends GrpcHubConnection {

        StubConnection(ManagedChannel channel) {
            super(channel);
        }
    }

    private static WorkspaceEventInfo protoEvent(long eventId, WorkspaceEventType eventType) {
        return WorkspaceEventInfo.newBuilder()
                .setEventId(eventId)
                .setOriginEventId("origin-" + eventId)
                .setProjectId("proj-1")
                .setWorkspaceRefId(WORKSPACE_ID)
                .setEventType(eventType.name())
                .setContent("{}")
                .setOriginCreatedAt(1000L)
                .setCreatedAt(2000L)
                .setCreatedBy("system")
                .build();
    }

    private record Recorder(
            List<WorkspaceEventsBatch> batches,
            AtomicInteger completions,
            AtomicReference<Throwable> error) {

        static Recorder create() {
            return new Recorder(Collections.synchronizedList(new ArrayList<>()),
                    new AtomicInteger(), new AtomicReference<>());
        }

        WorkspaceEventsStreamCallbacks callbacks() {
            return new WorkspaceEventsStreamCallbacks(
                    batches::add, completions::incrementAndGet, error::set);
        }
    }

    private static WorkspaceEventsClient.WorkspaceEventsSubscribeRequest request(
            long fromOffset, Set<WorkspaceEventType> types) {

        return request(fromOffset, types, Set.of());
    }

    private static WorkspaceEventsClient.WorkspaceEventsSubscribeRequest request(
            long fromOffset, Set<WorkspaceEventType> types, Set<String> projectIds) {

        return new WorkspaceEventsClient.WorkspaceEventsSubscribeRequest(
                fromOffset, types, projectIds, true);
    }

    @Nested
    class Delivery {

        @Test
        void mapsBatchesToDtos() {
            start(new WorkspaceEventsServiceGrpc.WorkspaceEventsServiceImplBase() {
                @Override
                public void streamWorkspaceEvents(
                        StreamWorkspaceEventsRequest request, StreamObserver<WorkspaceEventBatch> observer) {

                    received.set(request);
                    observer.onNext(WorkspaceEventBatch.newBuilder()
                            .addEvents(protoEvent(1L, WorkspaceEventType.PROJECT_CREATED))
                            .addEvents(protoEvent(2L,
                                    WorkspaceEventType.PROJECT_INSTANCE_SESSION_RECORDING_FILE_CREATED))
                            .setLastOffset(2L)
                            .setCaughtUp(false)
                            .build());
                    observer.onNext(WorkspaceEventBatch.newBuilder()
                            .setLastOffset(2L)
                            .setCaughtUp(true)
                            .build());
                    observer.onCompleted();
                }
            });
            var recorder = Recorder.create();

            client.streamEvents(WORKSPACE_ID, request(0, Set.of()), recorder.callbacks());

            await().atMost(5, SECONDS).untilAsserted(() ->
                    assertEquals(1, recorder.completions().get()));

            assertEquals(2, recorder.batches().size());
            WorkspaceEventsBatch first = recorder.batches().getFirst();
            WorkspaceEventsBatch second = recorder.batches().getLast();

            assertAll(
                    () -> assertEquals(2, first.events().size()),
                    () -> assertEquals(2L, first.lastOffset()),
                    () -> assertEquals(WorkspaceEventType.PROJECT_CREATED, first.events().getFirst().eventType()),
                    () -> assertEquals(WorkspaceEventType.PROJECT_INSTANCE_SESSION_RECORDING_FILE_CREATED,
                            first.events().getLast().eventType()),
                    () -> assertEquals("origin-1", first.events().getFirst().originEventId()),
                    () -> assertTrue(second.events().isEmpty(), "The catch-up marker carries no events"),
                    () -> assertTrue(second.caughtUp()));
        }

        @Test
        void reportsErrorsToTheCallback() {
            start(new WorkspaceEventsServiceGrpc.WorkspaceEventsServiceImplBase() {
                @Override
                public void streamWorkspaceEvents(
                        StreamWorkspaceEventsRequest request, StreamObserver<WorkspaceEventBatch> observer) {

                    observer.onError(new IllegalStateException("boom"));
                }
            });
            var recorder = Recorder.create();

            client.streamEvents(WORKSPACE_ID, request(0, Set.of()), recorder.callbacks());

            await().atMost(5, SECONDS).untilAsserted(() -> assertNotNull(recorder.error().get()));
        }
    }

    @Nested
    class RequestMapping {

        private void startEchoServer() {
            start(new WorkspaceEventsServiceGrpc.WorkspaceEventsServiceImplBase() {
                @Override
                public void streamWorkspaceEvents(
                        StreamWorkspaceEventsRequest request, StreamObserver<WorkspaceEventBatch> observer) {

                    received.set(request);
                    observer.onCompleted();
                }
            });
        }

        @Test
        void sendsOffsetAndTypeFilter() {
            startEchoServer();

            client.streamEvents(WORKSPACE_ID,
                    request(42L, Set.of(WorkspaceEventType.PROJECT_INSTANCE_SESSION_ARTIFACT_FILE_CREATED)),
                    Recorder.create().callbacks());

            await().atMost(5, SECONDS).untilAsserted(() -> assertNotNull(received.get()));
            StreamWorkspaceEventsRequest sent = received.get();

            assertAll(
                    () -> assertEquals(WORKSPACE_ID, sent.getWorkspaceId()),
                    () -> assertEquals(42L, sent.getFromOffset()),
                    () -> assertEquals(List.of("PROJECT_INSTANCE_SESSION_ARTIFACT_FILE_CREATED"),
                            sent.getEventTypesList()),
                    () -> assertTrue(sent.getSendEmptyBatches()));
        }

        @Test
        void sendsProjectFilter() {
            startEchoServer();

            client.streamEvents(WORKSPACE_ID,
                    request(0, Set.of(), Set.of("proj-1")),
                    Recorder.create().callbacks());

            await().atMost(5, SECONDS).untilAsserted(() -> assertNotNull(received.get()));

            assertEquals(List.of("proj-1"), received.get().getProjectIdsList());
        }

        @Test
        void omitsFromOffsetWhenStartingFromTheBeginning() {
            startEchoServer();

            client.streamEvents(WORKSPACE_ID, request(0, Set.of()), Recorder.create().callbacks());

            await().atMost(5, SECONDS).untilAsserted(() -> assertNotNull(received.get()));

            assertAll(
                    () -> assertTrue(received.get().getEventTypesList().isEmpty(),
                            "An empty filter must stay empty — the server reads that as all types"),
                    () -> assertTrue(received.get().getProjectIdsList().isEmpty(),
                            "An empty project filter means the whole workspace"),
                    () -> assertEquals(0L, received.get().getFromOffset()));
        }
    }

    @Nested
    class Lifecycle {

        private void startBlockingServer(CountDownLatch cancelled) {
            start(new WorkspaceEventsServiceGrpc.WorkspaceEventsServiceImplBase() {
                @Override
                public void streamWorkspaceEvents(
                        StreamWorkspaceEventsRequest request, StreamObserver<WorkspaceEventBatch> observer) {

                    openStreams.incrementAndGet();
                    io.grpc.Context.current().addListener(_ -> {
                        openStreams.decrementAndGet();
                        cancelled.countDown();
                    }, Runnable::run);
                }
            });
        }

        @Test
        void cancelClosesTheStream() throws InterruptedException {
            var cancelled = new CountDownLatch(1);
            startBlockingServer(cancelled);

            var subscription = client.streamEvents(
                    WORKSPACE_ID, request(0, Set.of()), Recorder.create().callbacks());
            await().atMost(5, SECONDS).untilAsserted(() -> assertEquals(1, openStreams.get()));

            subscription.cancel();

            assertTrue(cancelled.await(5, SECONDS), "Server must observe the cancellation");
        }

        @Test
        void closeCancelsEveryOpenSubscription() throws InterruptedException {
            var cancelled = new CountDownLatch(2);
            startBlockingServer(cancelled);

            client.streamEvents(WORKSPACE_ID, request(0, Set.of()), Recorder.create().callbacks());
            client.streamEvents(WORKSPACE_ID, request(0, Set.of()), Recorder.create().callbacks());
            await().atMost(5, SECONDS).untilAsserted(() -> assertEquals(2, openStreams.get()));

            // This is what CachedHubClientsFactory relies on when a hub connection is evicted.
            client.close();

            assertTrue(cancelled.await(5, SECONDS), "Both streams must be cancelled");
        }
    }
}
