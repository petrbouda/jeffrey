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
import cafe.jeffrey.hub.api.v1.ReplayStatus;
import cafe.jeffrey.hub.api.v1.StreamingEvent;
import cafe.jeffrey.hub.api.v1.TypedValue;
import cafe.jeffrey.microscope.grpc.client.ReplaySubscriptionRequest;
import cafe.jeffrey.microscope.core.manager.EventStreamingManager;
import cafe.jeffrey.microscope.core.manager.project.ProjectManager;
import cafe.jeffrey.microscope.core.mcp.tools.hubs.HubSessionRef;
import cafe.jeffrey.microscope.core.web.ProjectManagerResolver;
import cafe.jeffrey.microscope.grpc.client.EventStreamingClient.EventStreamingSubscription;
import cafe.jeffrey.microscope.grpc.client.StreamingCallbacks;
import io.grpc.Context;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class HubsReplayMcpToolsTest {
    private static final String REF = new HubSessionRef("hub", "workspace", "project", "session").encode();

    @Test
    void noResponseHasFiniteTimeoutAndCancelsSubscription() {
        ProjectManagerResolver resolver = mock(ProjectManagerResolver.class);
        ProjectManager project = mock(ProjectManager.class);
        EventStreamingManager streaming = mock(EventStreamingManager.class);
        when(resolver.resolveStrict("hub", "workspace", "project"))
                .thenReturn(new ProjectManagerResolver.ProjectContext(null, null, project));
        when(project.eventStreamingManager()).thenReturn(streaming);
        Context.CancellableContext context = Context.ROOT.withCancellation();
        when(streaming.subscribeReplayRaw(any(), any())).thenReturn(new EventStreamingSubscription(context, "session"));
        HubsReplayMcpTools tools = new HubsReplayMcpTools(resolver, new McpOperationRegistry(), Duration.ofMillis(40));
        long before = System.nanoTime();
        var result = tools.queryEvents(REF, "jdk.CPULoad", null, null, null, null).structuredContent();
        assertTrue(Duration.ofNanos(System.nanoTime() - before).compareTo(Duration.ofSeconds(2)) < 0);
        assertEquals("timeout", result.path("termination").asText());
        assertFalse(result.path("complete").asBoolean());
        assertTrue(context.isCancelled());
    }

    @Test
    void callerInterruptionCancelsAndReturnsPartialResult() throws Exception {
        ProjectManagerResolver resolver = mock(ProjectManagerResolver.class);
        ProjectManager project = mock(ProjectManager.class);
        EventStreamingManager streaming = mock(EventStreamingManager.class);
        when(resolver.resolveStrict("hub", "workspace", "project"))
                .thenReturn(new ProjectManagerResolver.ProjectContext(null, null, project));
        when(project.eventStreamingManager()).thenReturn(streaming);
        Context.CancellableContext context = Context.ROOT.withCancellation();
        CountDownLatch subscribed = new CountDownLatch(1);
        when(streaming.subscribeReplayRaw(any(), any())).thenAnswer(call -> {
            subscribed.countDown();
            return new EventStreamingSubscription(context, "session");
        });
        CompletableFuture<String> termination = new CompletableFuture<>();
        Thread worker = Thread.ofVirtual().start(() -> termination.complete(new HubsReplayMcpTools(resolver, new McpOperationRegistry())
                .queryEvents(REF, "jdk.CPULoad", null, null, null, null).structuredContent().path("termination").asText()));
        try {
            assertTrue(subscribed.await(2, TimeUnit.SECONDS));
            worker.interrupt();
            assertEquals("interrupted", termination.get(2, TimeUnit.SECONDS));
            assertTrue(context.isCancelled());
        } finally {
            worker.interrupt();
            worker.join(2000);
        }
    }

    @Test
    void invalidLimitsAndTimeWindowNeverContactHub() {
        ProjectManagerResolver resolver = mock(ProjectManagerResolver.class);
        HubsReplayMcpTools tools = new HubsReplayMcpTools(resolver, new McpOperationRegistry());
        assertThrows(IllegalArgumentException.class, () -> tools.queryEvents(REF, "jdk.CPULoad", null, null, 1001, null));
        assertThrows(IllegalArgumentException.class, () -> tools.queryEvents(REF, "jdk.CPULoad", 2L, 1L, null, null));
        assertThrows(IllegalArgumentException.class, () -> tools.queryEvents(REF, "", null, null, null, null));
        verifyNoInteractions(resolver);
    }

    @Test
    void scopedQueryPushesFiltersAndReturnsTypedEventsWithCoverage() {
        ProjectManagerResolver resolver = mock(ProjectManagerResolver.class);
        ProjectManager project = mock(ProjectManager.class);
        EventStreamingManager streaming = mock(EventStreamingManager.class);
        when(resolver.resolveStrict("hub", "workspace", "project"))
                .thenReturn(new ProjectManagerResolver.ProjectContext(null, null, project));
        when(project.eventStreamingManager()).thenReturn(streaming);
        when(streaming.subscribeReplayRaw(any(), any())).thenAnswer(call -> {
            ReplaySubscriptionRequest request = call.getArgument(0);
            assertEquals("workspace", request.workspaceId());
            assertEquals("project", request.projectId());
            assertEquals("session", request.sessionId());
            assertEquals(1L, request.startTime());
            assertEquals(100L, request.endTime());
            assertTrue(request.eventTypes().contains("jdk.CPULoad"));
            StreamingCallbacks callbacks = call.getArgument(1);
            callbacks.onNext().accept(EventBatch.newBuilder().setReplayStatus(ReplayStatus.newBuilder()
                    .setWorkspaceId("workspace").setProjectId("project")).build());
            callbacks.onNext().accept(EventBatch.newBuilder().addEvents(StreamingEvent.newBuilder()
                    .setSessionId("session").setEventType("jdk.CPULoad").setTimestamp(50)
                    .putFields("value", TypedValue.newBuilder().setLongValue(123).build())).build());
            callbacks.onNext().accept(EventBatch.newBuilder().setReplayStatus(ReplayStatus.newBuilder()
                    .setTerminal(true)).build());
            callbacks.onComplete().run();
            return new EventStreamingSubscription(Context.ROOT.withCancellation(), "session");
        });
        var result = new HubsReplayMcpTools(resolver, new McpOperationRegistry()).queryEvents(REF, "jdk.CPULoad", 1L, 100L, null, null).structuredContent();
        assertTrue(result.path("complete").asBoolean());
        assertEquals(123L, result.path("events").get(0).path("fields").path("value").asLong());
        assertEquals(1L, result.path("startTime").asLong());
        assertEquals(100L, result.path("endTime").asLong());
    }

    @Test
    void completedLegacyStreamHasExplicitUnknownCoverage() {
        ProjectManagerResolver resolver = mock(ProjectManagerResolver.class);
        ProjectManager project = mock(ProjectManager.class);
        EventStreamingManager streaming = mock(EventStreamingManager.class);
        when(resolver.resolveStrict("hub", "workspace", "project"))
                .thenReturn(new ProjectManagerResolver.ProjectContext(null, null, project));
        when(project.eventStreamingManager()).thenReturn(streaming);
        Context.CancellableContext context = Context.ROOT.withCancellation();
        when(streaming.subscribeReplayRaw(any(), any())).thenAnswer(call -> {
            StreamingCallbacks callbacks = call.getArgument(1);
            callbacks.onComplete().run();
            return new EventStreamingSubscription(context, "session");
        });
        var result = new HubsReplayMcpTools(resolver, new McpOperationRegistry()).queryEvents(REF, "jdk.CPULoad", null, null, null, null).structuredContent();
        assertEquals("unsupported_hub", result.path("termination").asText());
        assertTrue(context.isCancelled());
    }
}
