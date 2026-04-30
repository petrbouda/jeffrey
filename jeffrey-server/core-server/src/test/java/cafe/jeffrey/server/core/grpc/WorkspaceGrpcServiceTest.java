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
import cafe.jeffrey.server.core.manager.workspace.WorkspaceAlreadyExistsException;
import cafe.jeffrey.server.core.manager.workspace.WorkspaceManager;
import cafe.jeffrey.server.core.manager.workspace.WorkspacesManager;
import cafe.jeffrey.shared.common.model.workspace.WorkspaceInfo;
import cafe.jeffrey.shared.common.model.workspace.WorkspaceStatus;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

class WorkspaceGrpcServiceTest {

    private static final String WORKSPACE_ID = "ws-1";
    private static final Instant FIXED_TIME = Instant.parse("2026-01-15T10:00:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(FIXED_TIME, ZoneOffset.UTC);

    private Server server;
    private ManagedChannel channel;

    private WorkspaceServiceGrpc.WorkspaceServiceBlockingStub startServer(
            WorkspaceGrpcService service) throws IOException {

        String name = InProcessServerBuilder.generateName();
        server = InProcessServerBuilder.forName(name)
                .directExecutor()
                .addService(service)
                .build()
                .start();
        channel = InProcessChannelBuilder.forName(name)
                .directExecutor()
                .build();
        return WorkspaceServiceGrpc.newBlockingStub(channel);
    }

    @AfterEach
    void shutdown() {
        if (channel != null) channel.shutdownNow();
        if (server != null) server.shutdownNow();
    }

    @Nested
    class GetApiInfo {

        @Test
        void returnsVersionAndApiVersion() throws IOException {
            var stub = startServer(new WorkspaceGrpcService(mock(WorkspacesManager.class), FIXED_CLOCK));

            GetApiInfoResponse response = stub.getApiInfo(GetApiInfoRequest.getDefaultInstance());

            assertFalse(response.getVersion().isEmpty());
            assertEquals(1, response.getApiVersion());
        }
    }

    @Nested
    class ListWorkspaces {

        @Test
        void returnsWorkspaceList() throws IOException {
            var workspacesManager = mock(WorkspacesManager.class);
            var workspaceManager = mock(WorkspaceManager.class);
            when(workspaceManager.resolveInfo()).thenReturn(testWorkspaceInfo());
            doReturn(List.of(workspaceManager)).when(workspacesManager).findAll();

            var stub = startServer(new WorkspaceGrpcService(workspacesManager, FIXED_CLOCK));

            ListWorkspacesResponse response = stub.listWorkspaces(ListWorkspacesRequest.getDefaultInstance());

            assertEquals(1, response.getWorkspacesCount());
            assertEquals(WORKSPACE_ID, response.getWorkspaces(0).getId());
            assertEquals("Test Workspace", response.getWorkspaces(0).getName());
        }

        @Test
        void returnsEmptyList() throws IOException {
            var workspacesManager = mock(WorkspacesManager.class);
            doReturn(List.of()).when(workspacesManager).findAll();

            var stub = startServer(new WorkspaceGrpcService(workspacesManager, FIXED_CLOCK));

            ListWorkspacesResponse response = stub.listWorkspaces(ListWorkspacesRequest.getDefaultInstance());

            assertEquals(0, response.getWorkspacesCount());
        }
    }

    @Nested
    class GetWorkspace {

        @Test
        void returnsWorkspace() throws IOException {
            var workspacesManager = mock(WorkspacesManager.class);
            var workspaceManager = mock(WorkspaceManager.class);
            when(workspaceManager.resolveInfo()).thenReturn(testWorkspaceInfo());
            when(workspacesManager.findById(WORKSPACE_ID)).thenReturn(Optional.of(workspaceManager));

            var stub = startServer(new WorkspaceGrpcService(workspacesManager, FIXED_CLOCK));

            GetWorkspaceResponse response = stub.getWorkspace(
                    GetWorkspaceRequest.newBuilder().setWorkspaceId(WORKSPACE_ID).build());

            assertEquals(WORKSPACE_ID, response.getWorkspace().getId());
            assertEquals("Test Workspace", response.getWorkspace().getName());
            assertEquals(cafe.jeffrey.server.api.v1.WorkspaceStatus.WORKSPACE_STATUS_AVAILABLE,
                    response.getWorkspace().getStatus());
        }

        @Test
        void workspaceNotFound_returnsNotFound() throws IOException {
            var workspacesManager = mock(WorkspacesManager.class);
            when(workspacesManager.findById(any())).thenReturn(Optional.empty());

            var stub = startServer(new WorkspaceGrpcService(workspacesManager, FIXED_CLOCK));

            var ex = assertThrows(StatusRuntimeException.class, () ->
                    stub.getWorkspace(GetWorkspaceRequest.newBuilder().setWorkspaceId("missing").build()));
            assertEquals(Status.Code.NOT_FOUND, ex.getStatus().getCode());
        }
    }

    @Nested
    class CreateWorkspace {

        @Test
        void createsWorkspaceAndReturnsIt() throws IOException {
            var workspacesManager = mock(WorkspacesManager.class);
            when(workspacesManager.create(any())).thenReturn(testWorkspaceInfo());

            var stub = startServer(new WorkspaceGrpcService(workspacesManager, FIXED_CLOCK));

            CreateWorkspaceResponse response = stub.createWorkspace(
                    CreateWorkspaceRequest.newBuilder()
                            .setReferenceId("origin-1")
                            .setName("Test Workspace")
                            .build());

            assertEquals(WORKSPACE_ID, response.getWorkspace().getId());
            assertEquals("Test Workspace", response.getWorkspace().getName());
        }

        @Test
        void duplicateReferenceId_returnsAlreadyExists() throws IOException {
            var workspacesManager = mock(WorkspacesManager.class);
            when(workspacesManager.create(any()))
                    .thenThrow(new WorkspaceAlreadyExistsException(
                            "Workspace with reference ID 'origin-1' already exists"));

            var stub = startServer(new WorkspaceGrpcService(workspacesManager, FIXED_CLOCK));

            var ex = assertThrows(StatusRuntimeException.class, () ->
                    stub.createWorkspace(CreateWorkspaceRequest.newBuilder()
                            .setReferenceId("origin-1")
                            .setName("Test Workspace")
                            .build()));
            assertEquals(Status.Code.ALREADY_EXISTS, ex.getStatus().getCode());
        }

        @Test
        void blankReferenceId_returnsInvalidArgument() throws IOException {
            var workspacesManager = mock(WorkspacesManager.class);
            when(workspacesManager.create(any()))
                    .thenThrow(new IllegalArgumentException("Workspace Source ID cannot be null or empty"));

            var stub = startServer(new WorkspaceGrpcService(workspacesManager, FIXED_CLOCK));

            var ex = assertThrows(StatusRuntimeException.class, () ->
                    stub.createWorkspace(CreateWorkspaceRequest.newBuilder()
                            .setReferenceId("")
                            .setName("Test Workspace")
                            .build()));
            assertEquals(Status.Code.INVALID_ARGUMENT, ex.getStatus().getCode());
        }

        @Test
        void blankName_returnsInvalidArgument() throws IOException {
            var workspacesManager = mock(WorkspacesManager.class);
            when(workspacesManager.create(any()))
                    .thenThrow(new IllegalArgumentException("Workspace Name cannot be null or empty"));

            var stub = startServer(new WorkspaceGrpcService(workspacesManager, FIXED_CLOCK));

            var ex = assertThrows(StatusRuntimeException.class, () ->
                    stub.createWorkspace(CreateWorkspaceRequest.newBuilder()
                            .setReferenceId("origin-1")
                            .setName("")
                            .build()));
            assertEquals(Status.Code.INVALID_ARGUMENT, ex.getStatus().getCode());
        }
    }

    @Nested
    class DeleteWorkspace {

        @Test
        void deletesWorkspace() throws IOException {
            var workspacesManager = mock(WorkspacesManager.class);
            var workspaceManager = mock(WorkspaceManager.class);
            when(workspacesManager.findById(WORKSPACE_ID)).thenReturn(Optional.of(workspaceManager));

            var stub = startServer(new WorkspaceGrpcService(workspacesManager, FIXED_CLOCK));

            stub.deleteWorkspace(DeleteWorkspaceRequest.newBuilder().setWorkspaceId(WORKSPACE_ID).build());

            verify(workspaceManager).delete();
        }

        @Test
        void workspaceNotFound_returnsNotFound() throws IOException {
            var workspacesManager = mock(WorkspacesManager.class);
            when(workspacesManager.findById(any())).thenReturn(Optional.empty());

            var stub = startServer(new WorkspaceGrpcService(workspacesManager, FIXED_CLOCK));

            var ex = assertThrows(StatusRuntimeException.class, () ->
                    stub.deleteWorkspace(DeleteWorkspaceRequest.newBuilder().setWorkspaceId("missing").build()));
            assertEquals(Status.Code.NOT_FOUND, ex.getStatus().getCode());
        }
    }

    private static WorkspaceInfo testWorkspaceInfo() {
        return new WorkspaceInfo(WORKSPACE_ID, "origin-1", "repo-1", "Test Workspace", null, null, FIXED_TIME, WorkspaceStatus.AVAILABLE, 3);
    }
}
