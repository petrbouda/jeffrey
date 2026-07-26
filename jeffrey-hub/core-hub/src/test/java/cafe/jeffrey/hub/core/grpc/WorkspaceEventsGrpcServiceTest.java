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

import io.grpc.Context;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import cafe.jeffrey.hub.api.v1.*;
import cafe.jeffrey.hub.core.workspace.WorkspaceEventCallbacks;
import cafe.jeffrey.hub.core.workspace.WorkspaceEventNotifier;
import cafe.jeffrey.hub.core.workspace.WorkspaceEventReader;
import cafe.jeffrey.hub.core.workspace.WorkspaceEventStreamingManager;
import cafe.jeffrey.hub.core.workspace.WorkspaceEventSubscription;
import cafe.jeffrey.shared.common.model.workspace.WorkspaceEvent;
import cafe.jeffrey.shared.common.model.workspace.WorkspaceEventType;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WorkspaceEventsGrpcServiceTest {

    private static final String WORKSPACE_ID = "ws-1";
    private static final Instant FIXED_TIME = Instant.parse("2026-01-15T10:00:00Z");

    /** Long enough that the backstop never fires unless a test asks for it. */
    private static final Duration SLOW_BACKSTOP = Duration.ofMinutes(5);

    private InProcessGrpcServer grpc;
    private final List<WorkspaceEventStreamingManager> managers = new ArrayList<>();
    private final ExecutorService executor = Executors.newCachedThreadPool();

    private WorkspaceEventsServiceGrpc.WorkspaceEventsServiceBlockingStub startServer(
            WorkspaceEventsGrpcService service) {
        grpc = InProcessGrpcServer.start(service);
        return WorkspaceEventsServiceGrpc.newBlockingStub(grpc.channel());
    }

    private WorkspaceEventsServiceGrpc.WorkspaceEventsServiceStub startStreamingServer(
            WorkspaceEventsGrpcService service) {
        grpc = InProcessGrpcServer.start(service);
        return WorkspaceEventsServiceGrpc.newStub(grpc.channel());
    }

    /** A manager with no live source; the unary tests never touch it. */
    private WorkspaceEventStreamingManager streamingManager() {
        return streamingManager(mock(WorkspaceEventReader.class), new WorkspaceEventNotifier(), SLOW_BACKSTOP);
    }

    private WorkspaceEventStreamingManager streamingManager(
            WorkspaceEventReader reader, WorkspaceEventNotifier notifier, Duration backstop) {

        var manager = new WorkspaceEventStreamingManager(reader, notifier, executor, backstop);
        managers.add(manager);
        return manager;
    }

    @AfterEach
    void shutdown() {
        managers.forEach(WorkspaceEventStreamingManager::close);
        executor.shutdownNow();
        if (grpc != null) {
            grpc.close();
        }
    }

    @Nested
    class GetWorkspaceEvents {

        @Test
        void returnsEvents() throws IOException {
            var reader = mock(WorkspaceEventReader.class);
            when(reader.findAll(WORKSPACE_ID, 100)).thenReturn(List.of(testEvent()));
            when(reader.count(WORKSPACE_ID)).thenReturn(1L);

            var stub = startServer(new WorkspaceEventsGrpcService(reader, streamingManager()));

            GetWorkspaceEventsResponse response = stub.getWorkspaceEvents(
                    GetWorkspaceEventsRequest.newBuilder().setWorkspaceId(WORKSPACE_ID).build());

            assertEquals(1, response.getEventsCount());
            assertEquals(1L, response.getTotalCount());
            WorkspaceEventInfo event = response.getEvents(0);
            assertEquals(1L, event.getEventId());
            assertEquals("proj-1", event.getProjectId());
            assertEquals(WORKSPACE_ID, event.getWorkspaceRefId());
            assertEquals("PROJECT_CREATED", event.getEventType());
            assertEquals("{}", event.getContent());
        }

        @Test
        void returnsEmptyList() throws IOException {
            var reader = mock(WorkspaceEventReader.class);
            when(reader.findAll(WORKSPACE_ID, 100)).thenReturn(List.of());
            when(reader.count(WORKSPACE_ID)).thenReturn(0L);

            var stub = startServer(new WorkspaceEventsGrpcService(reader, streamingManager()));

            GetWorkspaceEventsResponse response = stub.getWorkspaceEvents(
                    GetWorkspaceEventsRequest.newBuilder().setWorkspaceId(WORKSPACE_ID).build());

            assertEquals(0, response.getEventsCount());
            assertEquals(0L, response.getTotalCount());
        }

        @Test
        void appliesDefaultLimitWhenUnset() throws IOException {
            var reader = mock(WorkspaceEventReader.class);
            when(reader.findAll(WORKSPACE_ID, 100)).thenReturn(List.of(testEvent()));
            when(reader.count(WORKSPACE_ID)).thenReturn(247L);

            var stub = startServer(new WorkspaceEventsGrpcService(reader, streamingManager()));

            GetWorkspaceEventsResponse response = stub.getWorkspaceEvents(
                    GetWorkspaceEventsRequest.newBuilder().setWorkspaceId(WORKSPACE_ID).build());

            assertEquals(247L, response.getTotalCount());
            assertEquals(1, response.getEventsCount());
        }

        @Test
        void honoursExplicitLimit() throws IOException {
            var reader = mock(WorkspaceEventReader.class);
            when(reader.findAll(WORKSPACE_ID, 5)).thenReturn(List.of(testEvent(), testEvent(), testEvent()));
            when(reader.count(WORKSPACE_ID)).thenReturn(20L);

            var stub = startServer(new WorkspaceEventsGrpcService(reader, streamingManager()));

            GetWorkspaceEventsResponse response = stub.getWorkspaceEvents(
                    GetWorkspaceEventsRequest.newBuilder()
                            .setWorkspaceId(WORKSPACE_ID)
                            .setLimit(5)
                            .build());

            assertEquals(20L, response.getTotalCount());
            assertEquals(3, response.getEventsCount());
        }

        @Test
        void filtersEventsByType_thenAppliesLimitInMemory() throws IOException {
            var created = testEvent();
            var deleted = new WorkspaceEvent(
                    2L, "origin-2", "proj-1", WORKSPACE_ID,
                    WorkspaceEventType.PROJECT_DELETED, "{}", FIXED_TIME, FIXED_TIME, "system");

            var reader = mock(WorkspaceEventReader.class);
            // With a type filter the service fetches unbounded so it can filter then limit.
            when(reader.findAll(WORKSPACE_ID, -1)).thenReturn(List.of(created, deleted));
            when(reader.count(WORKSPACE_ID)).thenReturn(2L);

            var stub = startServer(new WorkspaceEventsGrpcService(reader, streamingManager()));

            GetWorkspaceEventsResponse response = stub.getWorkspaceEvents(
                    GetWorkspaceEventsRequest.newBuilder()
                            .setWorkspaceId(WORKSPACE_ID)
                            .setEventType("PROJECT_DELETED")
                            .build());

            assertEquals(1, response.getEventsCount());
            assertEquals("PROJECT_DELETED", response.getEvents(0).getEventType());
            assertEquals(2L, response.getTotalCount());
        }

        @Test
        void invalidEventType_returnsInvalidArgument() throws IOException {
            var reader = mock(WorkspaceEventReader.class);
            when(reader.findAll(WORKSPACE_ID, -1)).thenReturn(List.of());
            when(reader.count(WORKSPACE_ID)).thenReturn(0L);

            var stub = startServer(new WorkspaceEventsGrpcService(reader, streamingManager()));

            var ex = assertThrows(StatusRuntimeException.class, () ->
                    stub.getWorkspaceEvents(GetWorkspaceEventsRequest.newBuilder()
                            .setWorkspaceId(WORKSPACE_ID)
                            .setEventType("BOGUS_TYPE")
                            .build()));
            assertEquals(Status.Code.INVALID_ARGUMENT, ex.getStatus().getCode());
        }
    }

    @Nested
    class StreamWorkspaceEvents {

        private static StreamWorkspaceEventsRequest.Builder request() {
            return StreamWorkspaceEventsRequest.newBuilder().setWorkspaceId(WORKSPACE_ID);
        }

        private static WorkspaceEvent eventAt(long offset, WorkspaceEventType eventType) {
            return new WorkspaceEvent(offset, "origin-" + offset, "proj-1", WORKSPACE_ID,
                    eventType, "{}", FIXED_TIME, FIXED_TIME, "system");
        }

        /** Stubs a reader that serves the given backlog once and is caught up afterwards. */
        private static WorkspaceEventReader readerWith(List<WorkspaceEvent> backlog) {
            var reader = mock(WorkspaceEventReader.class);
            when(reader.findFromOffset(eq(WORKSPACE_ID), anyLong(), anyInt()))
                    .thenAnswer(invocation -> {
                        long fromOffset = invocation.getArgument(1);
                        return backlog.stream()
                                .filter(event -> event.eventId() > fromOffset)
                                .toList();
                    });
            return reader;
        }

        private static List<WorkspaceEventInfo> deliveredEvents(TestStreamObserver<WorkspaceEventBatch> observer) {
            synchronized (observer.messages) {
                return observer.messages.stream()
                        .flatMap(batch -> batch.getEventsList().stream())
                        .toList();
            }
        }

        @Test
        void streamsBacklogThenReportsCaughtUp() {
            var reader = readerWith(List.of(
                    eventAt(1L, WorkspaceEventType.PROJECT_CREATED),
                    eventAt(2L, WorkspaceEventType.PROJECT_INSTANCE_CREATED)));
            var stub = startStreamingServer(new WorkspaceEventsGrpcService(
                    reader, streamingManager(reader, new WorkspaceEventNotifier(), SLOW_BACKSTOP)));
            var observer = new TestStreamObserver<WorkspaceEventBatch>();

            stub.streamWorkspaceEvents(request().build(), observer);

            await().atMost(5, SECONDS).untilAsserted(() -> assertAll(
                    () -> assertEquals(List.of(1L, 2L), deliveredEvents(observer).stream()
                            .map(WorkspaceEventInfo::getEventId).toList()),
                    () -> assertTrue(observer.messages.stream().anyMatch(WorkspaceEventBatch::getCaughtUp),
                            "The stream must report when the backlog is drained")));
        }

        @Test
        void resumesFromOffsetExclusively() {
            var reader = readerWith(List.of(
                    eventAt(1L, WorkspaceEventType.PROJECT_CREATED),
                    eventAt(2L, WorkspaceEventType.PROJECT_DELETED)));
            var stub = startStreamingServer(new WorkspaceEventsGrpcService(
                    reader, streamingManager(reader, new WorkspaceEventNotifier(), SLOW_BACKSTOP)));
            var observer = new TestStreamObserver<WorkspaceEventBatch>();

            stub.streamWorkspaceEvents(request().setFromOffset(1L).build(), observer);

            await().atMost(5, SECONDS).untilAsserted(() ->
                    assertEquals(List.of(2L), deliveredEvents(observer).stream()
                            .map(WorkspaceEventInfo::getEventId).toList()));
        }

        @Test
        void filtersByEventTypes() {
            var reader = readerWith(List.of(
                    eventAt(1L, WorkspaceEventType.PROJECT_CREATED),
                    eventAt(2L, WorkspaceEventType.PROJECT_INSTANCE_SESSION_RECORDING_FILE_CREATED),
                    eventAt(3L, WorkspaceEventType.PROJECT_INSTANCE_SESSION_ARTIFACT_FILE_CREATED)));
            var stub = startStreamingServer(new WorkspaceEventsGrpcService(
                    reader, streamingManager(reader, new WorkspaceEventNotifier(), SLOW_BACKSTOP)));
            var observer = new TestStreamObserver<WorkspaceEventBatch>();

            stub.streamWorkspaceEvents(request()
                    .addEventTypes("PROJECT_INSTANCE_SESSION_RECORDING_FILE_CREATED")
                    .addEventTypes("PROJECT_INSTANCE_SESSION_ARTIFACT_FILE_CREATED")
                    .build(), observer);

            await().atMost(5, SECONDS).untilAsserted(() ->
                    assertEquals(List.of(2L, 3L), deliveredEvents(observer).stream()
                            .map(WorkspaceEventInfo::getEventId).toList()));
        }

        @Test
        void emptyEventTypesMeansAllTypes() {
            // Deliberately unlike EventStreamingService, which rejects an empty type list.
            var reader = readerWith(List.of(
                    eventAt(1L, WorkspaceEventType.PROJECT_CREATED),
                    eventAt(2L, WorkspaceEventType.PROJECT_INSTANCE_EXPIRED)));
            var stub = startStreamingServer(new WorkspaceEventsGrpcService(
                    reader, streamingManager(reader, new WorkspaceEventNotifier(), SLOW_BACKSTOP)));
            var observer = new TestStreamObserver<WorkspaceEventBatch>();

            stub.streamWorkspaceEvents(request().build(), observer);

            await().atMost(5, SECONDS).untilAsserted(() -> assertAll(
                    () -> assertEquals(2, deliveredEvents(observer).size()),
                    () -> assertNull(observer.error, "An empty filter is valid, not an error")));
        }

        @Test
        void reportsLastOffsetPastFilteredOutEvents() {
            var reader = readerWith(List.of(
                    eventAt(1L, WorkspaceEventType.PROJECT_INSTANCE_SESSION_RECORDING_FILE_CREATED),
                    eventAt(2L, WorkspaceEventType.PROJECT_INSTANCE_SESSION_RECORDING_FILE_CREATED)));
            var stub = startStreamingServer(new WorkspaceEventsGrpcService(
                    reader, streamingManager(reader, new WorkspaceEventNotifier(), SLOW_BACKSTOP)));
            var observer = new TestStreamObserver<WorkspaceEventBatch>();

            stub.streamWorkspaceEvents(request().addEventTypes("PROJECT_CREATED").build(), observer);

            await().atMost(5, SECONDS).untilAsserted(() -> assertAll(
                    () -> assertTrue(deliveredEvents(observer).isEmpty(), "Nothing matches the filter"),
                    () -> assertEquals(2L, observer.messages.stream()
                                    .mapToLong(WorkspaceEventBatch::getLastOffset).max().orElse(-1L),
                            "last_offset must advance so a filtered client can still resume")));
        }

        @Test
        void deliversEventsAppendedAfterSubscribing() {
            var backlog = Collections.synchronizedList(new ArrayList<WorkspaceEvent>());
            var reader = mock(WorkspaceEventReader.class);
            when(reader.findFromOffset(eq(WORKSPACE_ID), anyLong(), anyInt()))
                    .thenAnswer(invocation -> {
                        long fromOffset = invocation.getArgument(1);
                        synchronized (backlog) {
                            return backlog.stream().filter(e -> e.eventId() > fromOffset).toList();
                        }
                    });
            var notifier = new WorkspaceEventNotifier();
            var stub = startStreamingServer(new WorkspaceEventsGrpcService(
                    reader, streamingManager(reader, notifier, SLOW_BACKSTOP)));
            var observer = new TestStreamObserver<WorkspaceEventBatch>();

            stub.streamWorkspaceEvents(request().build(), observer);
            await().atMost(5, SECONDS).untilAsserted(() ->
                    assertTrue(observer.messages.stream().anyMatch(WorkspaceEventBatch::getCaughtUp)));

            backlog.add(eventAt(1L, WorkspaceEventType.PROJECT_INSTANCE_SESSION_RECORDING_FILE_CREATED));
            notifier.onAppended(WORKSPACE_ID);

            await().atMost(5, SECONDS).untilAsserted(() ->
                    assertEquals(1, deliveredEvents(observer).size()));
        }

        @Test
        void blankWorkspaceId_returnsInvalidArgument() {
            var reader = mock(WorkspaceEventReader.class);
            var stub = startStreamingServer(new WorkspaceEventsGrpcService(reader, streamingManager()));
            var observer = new TestStreamObserver<WorkspaceEventBatch>();

            stub.streamWorkspaceEvents(
                    StreamWorkspaceEventsRequest.newBuilder().setWorkspaceId(" ").build(), observer);

            await().atMost(5, SECONDS).untilAsserted(() ->
                    assertEquals(Status.Code.INVALID_ARGUMENT, statusCodeOf(observer.error)));
        }

        @Test
        void negativeFromOffset_returnsInvalidArgument() {
            var reader = mock(WorkspaceEventReader.class);
            var stub = startStreamingServer(new WorkspaceEventsGrpcService(reader, streamingManager()));
            var observer = new TestStreamObserver<WorkspaceEventBatch>();

            stub.streamWorkspaceEvents(request().setFromOffset(-1L).build(), observer);

            await().atMost(5, SECONDS).untilAsserted(() ->
                    assertEquals(Status.Code.INVALID_ARGUMENT, statusCodeOf(observer.error)));
        }

        @Test
        void unknownEventType_returnsInvalidArgument() {
            var reader = mock(WorkspaceEventReader.class);
            var stub = startStreamingServer(new WorkspaceEventsGrpcService(reader, streamingManager()));
            var observer = new TestStreamObserver<WorkspaceEventBatch>();

            stub.streamWorkspaceEvents(request().addEventTypes("BOGUS_TYPE").build(), observer);

            await().atMost(5, SECONDS).untilAsserted(() ->
                    assertEquals(Status.Code.INVALID_ARGUMENT, statusCodeOf(observer.error)));
        }

        @Test
        void subscriptionCapReached_returnsResourceExhausted() {
            var reader = readerWith(List.of());
            var manager = streamingManager(reader, new WorkspaceEventNotifier(), SLOW_BACKSTOP);
            for (int i = 0; i < WorkspaceEventStreamingManager.MAX_SUBSCRIPTIONS; i++) {
                manager.subscribe(
                        new WorkspaceEventSubscription(WORKSPACE_ID, 0, Set.of(), false, 100),
                        new WorkspaceEventCallbacks(_ -> {
                        }, () -> {
                        }, _ -> {
                        }));
            }
            var stub = startStreamingServer(new WorkspaceEventsGrpcService(reader, manager));
            var observer = new TestStreamObserver<WorkspaceEventBatch>();

            stub.streamWorkspaceEvents(request().build(), observer);

            await().atMost(5, SECONDS).untilAsserted(() ->
                    assertEquals(Status.Code.RESOURCE_EXHAUSTED, statusCodeOf(observer.error)));
        }

        @Test
        void clientCancel_releasesTheSubscription() {
            var reader = readerWith(List.of());
            var manager = streamingManager(reader, new WorkspaceEventNotifier(), SLOW_BACKSTOP);
            var stub = startStreamingServer(new WorkspaceEventsGrpcService(reader, manager));
            var cancellableContext = Context.current().withCancellation();
            var observer = new TestStreamObserver<WorkspaceEventBatch>();

            cancellableContext.run(() -> stub.streamWorkspaceEvents(request().build(), observer));
            await().atMost(5, SECONDS).untilAsserted(() ->
                    assertEquals(1, manager.activeSubscriptions()));

            cancellableContext.cancel(null);

            await().atMost(5, SECONDS).untilAsserted(() ->
                    assertEquals(0, manager.activeSubscriptions(),
                            "A disconnected client must not leave its subscriber running"));
        }

        private static Status.Code statusCodeOf(Throwable error) {
            assertInstanceOf(StatusRuntimeException.class, error, "Expected a gRPC status error");
            return ((StatusRuntimeException) error).getStatus().getCode();
        }
    }

    private static WorkspaceEvent testEvent() {
        return new WorkspaceEvent(
                1L, "origin-1", "proj-1", WORKSPACE_ID,
                WorkspaceEventType.PROJECT_CREATED, "{}", FIXED_TIME, FIXED_TIME, "system");
    }
}
