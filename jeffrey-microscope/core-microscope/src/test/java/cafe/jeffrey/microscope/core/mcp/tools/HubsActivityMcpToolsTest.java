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

import cafe.jeffrey.hub.api.v1.*;
import cafe.jeffrey.microscope.core.manager.EventStreamingManager;
import cafe.jeffrey.microscope.core.manager.project.ProjectManager;
import cafe.jeffrey.microscope.core.mcp.tools.hubs.HubSessionRef;
import cafe.jeffrey.microscope.core.web.ProjectManagerResolver;
import cafe.jeffrey.microscope.grpc.client.EventStreamingClient;
import cafe.jeffrey.microscope.grpc.client.GrpcHubConnection;
import cafe.jeffrey.profile.mcp.ReflectiveToolset;
import cafe.jeffrey.shared.common.Json;
import io.grpc.Context;
import io.grpc.netty.NettyChannelBuilder;
import io.grpc.netty.NettyServerBuilder;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class HubsActivityMcpToolsTest {
    private static final String REF = new HubSessionRef("hub", "workspace", "project", "session").encode();

    private static final ActivityScope SCOPE = ActivityScope.newBuilder()
            .setWorkspaceId("workspace")
            .setProjectId("project")
            .setSessionId("session")
            .build();

    @Test
    void existingHubFamilyRoutesStartPollAndCancelThroughGrpc() throws Exception {
        AtomicInteger starts = new AtomicInteger();
        var remote = new EventActivityServiceGrpc.EventActivityServiceImplBase() {
            @Override
            public void startActivity(StartActivityRequest request, StreamObserver<EventActivitySnapshot> observer) {
                starts.incrementAndGet();
                assertEquals(SCOPE, request.getScope());
                assertEquals(1000, request.getStartTime());
                assertEquals(121000, request.getEndTime());
                assertEquals(60, request.getBucketSeconds());
                assertEquals("jdk.GarbageCollection", request.getEventTypes(0));
                assertNotNull(Context.current().getDeadline());
                observer.onNext(snapshot(ActivityState.ACTIVITY_STATE_RUNNING));
                observer.onCompleted();
            }

            @Override
            public void getActivity(GetActivityRequest request, StreamObserver<EventActivitySnapshot> observer) {
                assertEquals(SCOPE, request.getScope());
                assertEquals("scan", request.getScanId());
                assertEquals(ActivityOrder.ACTIVITY_ORDER_TYPES, request.getOrder());
                assertEquals(1, request.getLimit());
                assertEquals(1, request.getOffset());
                observer.onNext(snapshot(ActivityState.ACTIVITY_STATE_COMPLETED).toBuilder()
                        .setFinishedAt(2000)
                        .setCoverageKnown(true)
                        .setComplete(false)
                        .setSourceErrors(1)
                        .setTotalEvents(1501)
                        .setDistinctEventTypes(1)
                        .setTotalBuckets(2)
                        .setOmittedBuckets(1)
                        .setOffset(request.getOffset())
                        .setHasMoreBuckets(false)
                        .setOrder(request.getOrder())
                        .addBuckets(ActivityBucket.newBuilder()
                                .setStartTime(1000)
                                .setEndTime(61000)
                                .setEventCount(1501)
                                .setDistinctEventTypes(1)
                                .addEventTypes(ActivityTypeCount.newBuilder()
                                        .setEventType("jdk.GarbageCollection")
                                        .setCount(1501)))
                        .build());
                observer.onCompleted();
            }

            @Override
            public void cancelActivity(CancelActivityRequest request, StreamObserver<EventActivitySnapshot> observer) {
                assertEquals(SCOPE, request.getScope());
                assertEquals("scan", request.getScanId());
                observer.onNext(snapshot(ActivityState.ACTIVITY_STATE_CANCEL_REQUESTED));
                observer.onCompleted();
            }
        };
        var server = NettyServerBuilder.forPort(0)
                .addService(remote)
                .build()
                .start();
        var channel = NettyChannelBuilder.forAddress("localhost", server.getPort())
                .usePlaintext()
                .build();
        try {
            GrpcHubConnection connection = mock(GrpcHubConnection.class);
            when(connection.getChannel()).thenReturn(channel);
            ProjectManagerResolver resolver = mock(ProjectManagerResolver.class);
            ProjectManager project = mock(ProjectManager.class);
            when(resolver.resolveStrict("hub", "workspace", "project"))
                    .thenReturn(new ProjectManagerResolver.ProjectContext(null, null, project));
            var manager = new EventStreamingManager(new EventStreamingClient(connection));
            when(project.eventStreamingManager()).thenReturn(manager);
            var tools = new ReflectiveToolset(new HubsReplayMcpTools(resolver, new McpOperationRegistry()), "hubs");
            var started = tools.callResult("hubs_eventActivity", Json.createObject()
                    .put("sessionRef", REF)
                    .put("startTime", 1000)
                    .put("endTime", 121000)
                    .put("bucketSeconds", 60)
                    .put("eventTypes", "jdk.GarbageCollection")).structuredContent();
            assertEquals("scan", started.path("scanId").asText());
            assertEquals(REF, started.path("sessionRef").asText());
            var result = tools.callResult("hubs_activityStatus", Json.createObject()
                    .put("sessionRef", REF)
                    .put("scanId", "scan")
                    .put("order", "types")
                    .put("limit", 1)
                    .put("offset", 1)).structuredContent();
            assertEquals(1501, result.path("totalEvents").asLong());
            assertFalse(result.path("complete").asBoolean());
            assertEquals(1, result.path("sourceErrors").asInt());
            assertEquals(1, result.path("omittedBuckets").asInt());
            assertEquals(1, result.path("offset").asInt());
            assertFalse(result.path("hasMoreBuckets").asBoolean());
            assertEquals(1501, result.path("buckets").get(0).path("eventTypes").get(0).path("count").asLong());
            var cancelled = tools.callResult("hubs_activityCancel", Json.createObject()
                    .put("sessionRef", REF)
                    .put("scanId", "scan")).structuredContent();
            assertEquals("cancel_requested", cancelled.path("status").asText());
            assertEquals(1, starts.get());
        } finally {
            channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
            server.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    @Test
    void olderHubIsReportedAsUnsupportedWithoutRawReplayFallback() throws Exception {
        AtomicInteger rawCalls = new AtomicInteger();
        var legacy = new EventStreamingServiceGrpc.EventStreamingServiceImplBase() {
            @Override
            public void replayStreaming(ReplayStreamingRequest request, StreamObserver<EventBatch> observer) {
                rawCalls.incrementAndGet();
                observer.onCompleted();
            }

            @Override
            public void scopedReplayStreaming(ReplayStreamingRequest request, StreamObserver<EventBatch> observer) {
                rawCalls.incrementAndGet();
                observer.onCompleted();
            }
        };
        var server = NettyServerBuilder.forPort(0)
                .addService(legacy)
                .build()
                .start();
        var channel = NettyChannelBuilder.forAddress("localhost", server.getPort())
                .usePlaintext()
                .build();
        try {
            GrpcHubConnection connection = mock(GrpcHubConnection.class);
            when(connection.getChannel()).thenReturn(channel);
            ProjectManagerResolver resolver = mock(ProjectManagerResolver.class);
            ProjectManager project = mock(ProjectManager.class);
            when(resolver.resolveStrict("hub", "workspace", "project"))
                    .thenReturn(new ProjectManagerResolver.ProjectContext(null, null, project));
            var manager = new EventStreamingManager(new EventStreamingClient(connection));
            when(project.eventStreamingManager()).thenReturn(manager);
            var tools = new HubsReplayMcpTools(resolver, new McpOperationRegistry());
            var error = assertThrows(IllegalStateException.class, () -> tools.eventActivity(REF, 1, 2, null, null));
            assertTrue(error.getMessage().contains("does not support event activity"), error.toString());
            assertEquals(0, rawCalls.get());
        } finally {
            channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
            server.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    /**
     * A Hub scan is one of Microscope's operations, so the generic pair reaches it too. Without this
     * a reader would have to know that background work started by hubs_ is the one kind that is not
     * polled with operations_status.
     */
    @Test
    void startedScanIsReadableThroughTheGenericOperationTools() throws Exception {
        var remote = new EventActivityServiceGrpc.EventActivityServiceImplBase() {
            @Override
            public void startActivity(StartActivityRequest request, StreamObserver<EventActivitySnapshot> observer) {
                observer.onNext(snapshot(ActivityState.ACTIVITY_STATE_RUNNING));
                observer.onCompleted();
            }

            @Override
            public void getActivity(GetActivityRequest request, StreamObserver<EventActivitySnapshot> observer) {
                observer.onNext(snapshot(ActivityState.ACTIVITY_STATE_COMPLETED).toBuilder()
                        .setFinishedAt(2000)
                        .setCoverageKnown(true)
                        .setComplete(true)
                        .setTotalEvents(42)
                        .setFilesTotal(3)
                        .build());
                observer.onCompleted();
            }
        };
        var server = NettyServerBuilder.forPort(0).addService(remote).build().start();
        var channel = NettyChannelBuilder.forAddress("localhost", server.getPort()).usePlaintext().build();
        try {
            GrpcHubConnection connection = mock(GrpcHubConnection.class);
            when(connection.getChannel()).thenReturn(channel);
            ProjectManagerResolver resolver = mock(ProjectManagerResolver.class);
            ProjectManager project = mock(ProjectManager.class);
            when(resolver.resolveStrict("hub", "workspace", "project"))
                    .thenReturn(new ProjectManagerResolver.ProjectContext(null, null, project));
            // Built before the stubbing: constructing the client calls the mocked channel getter,
            // which Mockito would otherwise read as an unfinished stub.
            var manager = new EventStreamingManager(new EventStreamingClient(connection));
            when(project.eventStreamingManager()).thenReturn(manager);

            var operations = new McpOperationRegistry();
            var hubs = new HubsReplayMcpTools(resolver, operations);
            var started = hubs.eventActivity(REF, 1000, 121000, 60L, null).structuredContent();

            // The scan ID is the operation ID, so a reader tracks one identifier rather than two.
            assertEquals("scan", started.path("operationId").asText());

            var status = Json.mapper().readTree(new OperationsMcpTools(operations).status("scan"));
            assertEquals("hub_activity", status.path("kind").asText());
            assertEquals("completed", status.path("status").asText());
            assertEquals(42, status.path("progress").path("details").path("totalEvents").asLong());
            assertEquals(3, status.path("progress").path("details").path("filesTotal").asInt());
        } finally {
            channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
            server.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    @Test
    void invalidScanArgumentsNeverContactHub() {
        var resolver = mock(ProjectManagerResolver.class);
        var tools = new HubsReplayMcpTools(resolver, new McpOperationRegistry());
        assertThrows(IllegalArgumentException.class, () -> tools.eventActivity(REF, 2, 1, null, null));
        assertThrows(IllegalArgumentException.class, () -> tools.eventActivity(REF, 0, Long.MAX_VALUE, 1L, null));
        assertThrows(IllegalArgumentException.class, () -> tools.eventActivity(REF, 1, 2, Long.MAX_VALUE, null));
        assertThrows(IllegalArgumentException.class, () -> tools.eventActivity(REF, 1, 2, null, "GC,"));
        assertThrows(IllegalArgumentException.class, () -> tools.activityStatus(REF, "scan", "time", 21, null));
        assertThrows(IllegalArgumentException.class, () -> tools.activityStatus(REF, "scan", "unknown", 1, null));
        assertThrows(IllegalArgumentException.class, () -> tools.activityStatus(REF, "scan", "time", 1, -1));
        assertThrows(IllegalArgumentException.class, () -> tools.activityStatus(REF, "scan", "time", 1, 289));
        assertThrows(IllegalArgumentException.class, () -> tools.activityCancel(REF, ""));
        String oversizedScope = new HubSessionRef("hub", "w".repeat(513), "project", "session").encode();
        assertThrows(IllegalArgumentException.class, () -> tools.eventActivity(oversizedScope, 1, 2, 1L, null));
        verifyNoInteractions(resolver);
    }

    private static EventActivitySnapshot snapshot(ActivityState state) {
        return EventActivitySnapshot.newBuilder()
                .setScanId("scan")
                .setScope(SCOPE)
                .setState(state)
                .setStartTime(1000)
                .setEndTime(121000)
                .setBucketMillis(60000)
                .build();
    }
}
