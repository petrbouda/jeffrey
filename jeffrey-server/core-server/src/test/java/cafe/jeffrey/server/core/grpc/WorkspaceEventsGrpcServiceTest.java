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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import cafe.jeffrey.server.api.v1.*;
import cafe.jeffrey.server.core.workspace.WorkspaceEventReader;
import cafe.jeffrey.shared.common.model.workspace.WorkspaceEvent;
import cafe.jeffrey.shared.common.model.workspace.WorkspaceEventType;

import java.io.IOException;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WorkspaceEventsGrpcServiceTest {

    private static final String WORKSPACE_ID = "ws-1";
    private static final Instant FIXED_TIME = Instant.parse("2026-01-15T10:00:00Z");

    private Server server;
    private ManagedChannel channel;

    private WorkspaceEventsServiceGrpc.WorkspaceEventsServiceBlockingStub startServer(
            WorkspaceEventsGrpcService service) throws IOException {

        String name = InProcessServerBuilder.generateName();
        server = InProcessServerBuilder.forName(name)
                .directExecutor()
                .addService(service)
                .build()
                .start();
        channel = InProcessChannelBuilder.forName(name)
                .directExecutor()
                .build();
        return WorkspaceEventsServiceGrpc.newBlockingStub(channel);
    }

    @AfterEach
    void shutdown() {
        if (channel != null) channel.shutdownNow();
        if (server != null) server.shutdownNow();
    }

    @Nested
    class GetWorkspaceEvents {

        @Test
        void returnsEvents() throws IOException {
            var reader = mock(WorkspaceEventReader.class);
            when(reader.findAll(WORKSPACE_ID)).thenReturn(List.of(testEvent()));

            var stub = startServer(new WorkspaceEventsGrpcService(reader));

            GetWorkspaceEventsResponse response = stub.getWorkspaceEvents(
                    GetWorkspaceEventsRequest.newBuilder().setWorkspaceId(WORKSPACE_ID).build());

            assertEquals(1, response.getEventsCount());
            WorkspaceEventInfo event = response.getEvents(0);
            assertEquals(1L, event.getEventId());
            assertEquals("proj-1", event.getProjectId());
            assertEquals(WORKSPACE_ID, event.getWorkspaceId());
            assertEquals("PROJECT_CREATED", event.getEventType());
            assertEquals("{}", event.getContent());
        }

        @Test
        void returnsEmptyList() throws IOException {
            var reader = mock(WorkspaceEventReader.class);
            when(reader.findAll(WORKSPACE_ID)).thenReturn(List.of());

            var stub = startServer(new WorkspaceEventsGrpcService(reader));

            GetWorkspaceEventsResponse response = stub.getWorkspaceEvents(
                    GetWorkspaceEventsRequest.newBuilder().setWorkspaceId(WORKSPACE_ID).build());

            assertEquals(0, response.getEventsCount());
        }

        @Test
        void filtersEventsByType() throws IOException {
            var created = testEvent();
            var deleted = new WorkspaceEvent(
                    2L, "origin-2", "proj-1", WORKSPACE_ID,
                    WorkspaceEventType.PROJECT_DELETED, "{}", FIXED_TIME, FIXED_TIME, "system");

            var reader = mock(WorkspaceEventReader.class);
            when(reader.findAll(WORKSPACE_ID)).thenReturn(List.of(created, deleted));

            var stub = startServer(new WorkspaceEventsGrpcService(reader));

            GetWorkspaceEventsResponse response = stub.getWorkspaceEvents(
                    GetWorkspaceEventsRequest.newBuilder()
                            .setWorkspaceId(WORKSPACE_ID)
                            .setEventType("PROJECT_DELETED")
                            .build());

            assertEquals(1, response.getEventsCount());
            assertEquals("PROJECT_DELETED", response.getEvents(0).getEventType());
        }

        @Test
        void invalidEventType_returnsInvalidArgument() throws IOException {
            var reader = mock(WorkspaceEventReader.class);
            when(reader.findAll(WORKSPACE_ID)).thenReturn(List.of());

            var stub = startServer(new WorkspaceEventsGrpcService(reader));

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
