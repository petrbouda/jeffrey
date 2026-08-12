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
import io.grpc.StatusRuntimeException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import cafe.jeffrey.hub.api.v1.GetWorkspaceEventsRequest;
import cafe.jeffrey.hub.api.v1.GetWorkspaceEventsResponse;
import cafe.jeffrey.hub.api.v1.WorkspaceEventInfo;
import cafe.jeffrey.hub.api.v1.WorkspaceEventsServiceGrpc;
import cafe.jeffrey.hub.persistence.api.WorkspaceEventLogRepository;
import cafe.jeffrey.hub.persistence.api.WorkspaceEventLogRepository.WorkspaceEventQuery;
import cafe.jeffrey.shared.common.model.workspace.WorkspaceEvent;
import cafe.jeffrey.shared.common.model.workspace.WorkspaceEventType;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WorkspaceEventsGrpcServiceTest {

    private static final String WORKSPACE_ID = "ws-1";
    private static final Instant FIXED_TIME = Instant.parse("2026-01-15T10:00:00Z");

    private InProcessGrpcServer grpc;

    private WorkspaceEventsServiceGrpc.WorkspaceEventsServiceBlockingStub startServer(
            WorkspaceEventsGrpcService service) {
        grpc = InProcessGrpcServer.start(service);
        return WorkspaceEventsServiceGrpc.newBlockingStub(grpc.channel());
    }

    @AfterEach
    void shutdown() {
        if (grpc != null) {
            grpc.close();
        }
    }

    @Nested
    class GetWorkspaceEvents {

        @Test
        void returnsEvents() throws IOException {
            var eventLog = mock(WorkspaceEventLogRepository.class);
            when(eventLog.findLatest(new WorkspaceEventQuery(WORKSPACE_ID, null, Set.of(), 100)))
                    .thenReturn(List.of(testEvent()));
            when(eventLog.count(WORKSPACE_ID)).thenReturn(1L);

            var stub = startServer(new WorkspaceEventsGrpcService(eventLog));

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
            var eventLog = mock(WorkspaceEventLogRepository.class);
            when(eventLog.findLatest(new WorkspaceEventQuery(WORKSPACE_ID, null, Set.of(), 100)))
                    .thenReturn(List.of());
            when(eventLog.count(WORKSPACE_ID)).thenReturn(0L);

            var stub = startServer(new WorkspaceEventsGrpcService(eventLog));

            GetWorkspaceEventsResponse response = stub.getWorkspaceEvents(
                    GetWorkspaceEventsRequest.newBuilder().setWorkspaceId(WORKSPACE_ID).build());

            assertEquals(0, response.getEventsCount());
            assertEquals(0L, response.getTotalCount());
        }

        @Test
        void honoursExplicitLimit() throws IOException {
            var eventLog = mock(WorkspaceEventLogRepository.class);
            when(eventLog.findLatest(new WorkspaceEventQuery(WORKSPACE_ID, null, Set.of(), 5)))
                    .thenReturn(List.of(testEvent(), testEvent(), testEvent()));
            when(eventLog.count(WORKSPACE_ID)).thenReturn(20L);

            var stub = startServer(new WorkspaceEventsGrpcService(eventLog));

            GetWorkspaceEventsResponse response = stub.getWorkspaceEvents(
                    GetWorkspaceEventsRequest.newBuilder()
                            .setWorkspaceId(WORKSPACE_ID)
                            .setLimit(5)
                            .build());

            assertEquals(20L, response.getTotalCount(), "totalCount stays unfiltered");
            assertEquals(3, response.getEventsCount());
        }

        @Test
        void pushesTypeFilterIntoTheQuery() throws IOException {
            var deleted = new WorkspaceEvent(
                    2L, "origin-2", "proj-1", WORKSPACE_ID,
                    WorkspaceEventType.PROJECT_DELETED, "{}", FIXED_TIME, FIXED_TIME, "system");

            var eventLog = mock(WorkspaceEventLogRepository.class);
            when(eventLog.findLatest(new WorkspaceEventQuery(
                    WORKSPACE_ID, WorkspaceEventType.PROJECT_DELETED, Set.of(), 100)))
                    .thenReturn(List.of(deleted));
            when(eventLog.count(WORKSPACE_ID)).thenReturn(2L);

            var stub = startServer(new WorkspaceEventsGrpcService(eventLog));

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
        void pushesProjectFilterIntoTheQuery() throws IOException {
            var mine = new WorkspaceEvent(1L, "origin-1", "proj-1", WORKSPACE_ID,
                    WorkspaceEventType.PROJECT_CREATED, "{}", FIXED_TIME, FIXED_TIME, "system");

            var eventLog = mock(WorkspaceEventLogRepository.class);
            when(eventLog.findLatest(new WorkspaceEventQuery(WORKSPACE_ID, null, Set.of("proj-1"), 100)))
                    .thenReturn(List.of(mine));
            when(eventLog.count(WORKSPACE_ID)).thenReturn(2L);

            var stub = startServer(new WorkspaceEventsGrpcService(eventLog));

            GetWorkspaceEventsResponse response = stub.getWorkspaceEvents(
                    GetWorkspaceEventsRequest.newBuilder()
                            .setWorkspaceId(WORKSPACE_ID)
                            .addProjectIds("proj-1")
                            .build());

            assertEquals(1, response.getEventsCount());
            assertEquals("proj-1", response.getEvents(0).getProjectId());
            assertEquals(2L, response.getTotalCount(), "totalCount stays unfiltered");
        }

        @Test
        void invalidEventType_returnsInvalidArgument() throws IOException {
            var eventLog = mock(WorkspaceEventLogRepository.class);
            when(eventLog.count(WORKSPACE_ID)).thenReturn(0L);

            var stub = startServer(new WorkspaceEventsGrpcService(eventLog));

            var ex = assertThrows(StatusRuntimeException.class, () ->
                    stub.getWorkspaceEvents(GetWorkspaceEventsRequest.newBuilder()
                            .setWorkspaceId(WORKSPACE_ID)
                            .setEventType("BOGUS_TYPE")
                            .build()));
            assertEquals(Status.Code.INVALID_ARGUMENT, ex.getStatus().getCode());
        }
    }

    private static WorkspaceEvent testEvent() {
        return new WorkspaceEvent(
                1L, "origin-1", "proj-1", WORKSPACE_ID,
                WorkspaceEventType.PROJECT_CREATED, "{}", FIXED_TIME, FIXED_TIME, "system");
    }
}
