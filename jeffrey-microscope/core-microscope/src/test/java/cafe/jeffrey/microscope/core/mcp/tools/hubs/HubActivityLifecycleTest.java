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

package cafe.jeffrey.microscope.core.mcp.tools.hubs;

import cafe.jeffrey.microscope.core.manager.EventStreamingManager;
import cafe.jeffrey.microscope.core.manager.project.ProjectManager;
import cafe.jeffrey.microscope.core.mcp.tools.HubsReplayMcpTools;
import cafe.jeffrey.microscope.core.mcp.tools.McpOperationRegistry;
import cafe.jeffrey.microscope.core.web.ProjectManagerResolver;
import cafe.jeffrey.microscope.grpc.client.ActivityScanSnapshot;
import cafe.jeffrey.shared.common.activity.ActivityOrder;
import cafe.jeffrey.shared.common.activity.ActivityState;
import io.grpc.Status;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HubActivityLifecycleTest {

    private static final String REF = new HubSessionRef("hub", "workspace", "project", "session").encode();

    @Test
    void completedFamilyPollRemainsReadableAfterHubEvictsTheScan() {
        var manager = mock(EventStreamingManager.class);
        var operations = new McpOperationRegistry();
        var tools = tools(manager, operations);
        when(manager.startActivity(any())).thenReturn(snapshot("scan", ActivityState.RUNNING));
        when(manager.getActivity(any()))
                .thenReturn(snapshot("scan", ActivityState.COMPLETED))
                .thenThrow(Status.NOT_FOUND.withDescription("Scan evicted").asRuntimeException());
        tools.eventActivity(REF, 0, 60000, 60L, null);
        assertEquals("completed", tools.activityStatus(REF, "scan", "time", 1, 0)
                .structuredContent().path("status").asString());
        assertEquals("completed", operations.status("scan").status());
    }

    @Test
    void aDifferentScanInTheSameSessionIsRejected() {
        var manager = mock(EventStreamingManager.class);
        var tools = tools(manager, new McpOperationRegistry());
        when(manager.getActivity(any())).thenReturn(snapshot("other-scan", ActivityState.COMPLETED));
        assertThrows(IllegalStateException.class, () -> tools.activityStatus(REF, "scan", "time", 1, 0));
    }

    @Test
    void anOlderPollCannotOverwriteAnObservedTerminalCancellation() throws Exception {
        var polling = new CompletableFuture<Void>();
        var release = new CompletableFuture<Void>();
        var handle = new HubActivityOperation(snapshot("scan", ActivityState.RUNNING), () -> {
            polling.complete(null);
            release.join();
            return snapshot("scan", ActivityState.RUNNING);
        }, () -> snapshot("scan", ActivityState.CANCELLED));
        var done = new CompletableFuture<Void>();
        Thread.ofVirtual().start(() -> {
            try {
                handle.snapshot();
                done.complete(null);
            } catch (Throwable e) {
                done.completeExceptionally(e);
            }
        });
        try {
            polling.get(5, TimeUnit.SECONDS);
            handle.cancel();
            assertEquals("cancelled", handle.lifecycleSnapshot().state().code());
        } finally {
            release.complete(null);
        }
        done.get(5, TimeUnit.SECONDS);
        assertEquals("cancelled", handle.lifecycleSnapshot().state().code());
    }

    @Test
    void anExpiredRemoteScanBecomesTerminalLocally() {
        var manager = mock(EventStreamingManager.class);
        var operations = new McpOperationRegistry();
        var tools = tools(manager, operations);
        when(manager.startActivity(any())).thenReturn(snapshot("scan", ActivityState.RUNNING));
        when(manager.getActivity(any())).thenThrow(Status.NOT_FOUND.asRuntimeException());
        tools.eventActivity(REF, 0, 60000, 60L, null);

        assertEquals("failed", operations.status("scan").status());
        assertEquals("failed", operations.status("scan").status());
        verify(manager, times(1)).getActivity(any());
    }

    @Test
    void forgottenRemoteHandlesExpireWithoutPollingTheHub() {
        var now = new AtomicReference<>(Instant.now());
        var clock = mock(Clock.class);
        when(clock.instant()).thenAnswer(invocation -> now.get());
        var manager = mock(EventStreamingManager.class);
        var operations = new McpOperationRegistry(clock);
        var tools = tools(manager, operations);
        when(manager.startActivity(any())).thenReturn(snapshot("scan", ActivityState.RUNNING));
        tools.eventActivity(REF, 0, 60000, 60L, null);

        now.updateAndGet(time -> time.plus(Duration.ofHours(1)).plusSeconds(1));
        assertThrows(IllegalArgumentException.class, () -> operations.status("scan"));
        verify(manager, never()).getActivity(any());
    }

    @Test
    void aTransientHubFailureDoesNotFinishTheLocalOperation() {
        var manager = mock(EventStreamingManager.class);
        var operations = new McpOperationRegistry();
        var tools = tools(manager, operations);
        when(manager.startActivity(any())).thenReturn(snapshot("scan", ActivityState.RUNNING));
        when(manager.getActivity(any()))
                .thenThrow(Status.UNAVAILABLE.asRuntimeException())
                .thenReturn(snapshot("scan", ActivityState.COMPLETED));
        tools.eventActivity(REF, 0, 60000, 60L, null);

        assertThrows(IllegalStateException.class, () -> operations.status("scan"));
        assertEquals("completed", operations.status("scan").status());
    }

    private static HubsReplayMcpTools tools(EventStreamingManager manager, McpOperationRegistry operations) {
        var resolver = mock(ProjectManagerResolver.class);
        var project = mock(ProjectManager.class);
        when(resolver.resolveStrict("hub", "workspace", "project"))
                .thenReturn(new ProjectManagerResolver.ProjectContext(null, null, project));
        when(project.eventStreamingManager()).thenReturn(manager);
        return new HubsReplayMcpTools(resolver, operations);
    }

    private static ActivityScanSnapshot snapshot(String id, ActivityState state) {
        return new ActivityScanSnapshot(
                id,
                "workspace",
                "project",
                "session",
                state,
                System.currentTimeMillis(),
                state.terminal() ? System.currentTimeMillis() : null,
                state == ActivityState.COMPLETED,
                state == ActivityState.COMPLETED,
                0,
                1,
                null,
                0,
                60000,
                60000,
                List.of(),
                0,
                0,
                1,
                ActivityOrder.TIME,
                0,
                0,
                false,
                List.of());
    }
}
