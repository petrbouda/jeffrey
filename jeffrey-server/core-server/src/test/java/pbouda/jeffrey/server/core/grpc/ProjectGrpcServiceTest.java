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

package pbouda.jeffrey.server.core.grpc;

import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import pbouda.jeffrey.server.api.v1.*;
import pbouda.jeffrey.server.core.manager.project.ProjectManager;
import pbouda.jeffrey.server.core.manager.project.ProjectManager.DetailedProjectInfo;
import pbouda.jeffrey.server.core.manager.project.ProjectsManager;
import pbouda.jeffrey.server.core.manager.workspace.WorkspaceManager;
import pbouda.jeffrey.server.core.manager.workspace.WorkspacesManager;
import pbouda.jeffrey.server.persistence.repository.ProjectRepository;
import pbouda.jeffrey.server.persistence.repository.ServerPlatformRepositories;
import pbouda.jeffrey.shared.common.model.workspace.WorkspaceEventCreator;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ProjectGrpcServiceTest {

    private static final String WORKSPACE_ID = "ws-1";
    private static final String PROJECT_ID = "proj-1";
    private static final Instant FIXED_TIME = Instant.parse("2026-01-15T10:00:00Z");

    private Server server;
    private ManagedChannel channel;

    private ProjectServiceGrpc.ProjectServiceBlockingStub startServer(
            ProjectGrpcService service) throws IOException {

        String name = InProcessServerBuilder.generateName();
        server = InProcessServerBuilder.forName(name)
                .directExecutor()
                .addService(service)
                .build()
                .start();
        channel = InProcessChannelBuilder.forName(name)
                .directExecutor()
                .build();
        return ProjectServiceGrpc.newBlockingStub(channel);
    }

    @AfterEach
    void shutdown() {
        if (channel != null) channel.shutdownNow();
        if (server != null) server.shutdownNow();
    }

    @Nested
    class ListProjects {

        @Test
        void returnsProjectList() throws IOException {
            var stub = startServer(serviceForListProjects());

            ListProjectsResponse response = stub.listProjects(
                    ListProjectsRequest.newBuilder().setWorkspaceId(WORKSPACE_ID).build());

            assertEquals(1, response.getProjectsCount());
            assertEquals(PROJECT_ID, response.getProjects(0).getId());
            assertEquals("Test Project", response.getProjects(0).getName());
        }

        @Test
        void workspaceNotFound_returnsNotFound() throws IOException {
            var stub = startServer(serviceWithNoWorkspace());

            var ex = assertThrows(StatusRuntimeException.class, () ->
                    stub.listProjects(ListProjectsRequest.newBuilder().setWorkspaceId("missing").build()));
            assertEquals(Status.Code.NOT_FOUND, ex.getStatus().getCode());
        }
    }

    @Nested
    class GetProject {

        @Test
        void returnsProject() throws IOException {
            var stub = startServer(serviceWithProject());

            GetProjectResponse response = stub.getProject(
                    GetProjectRequest.newBuilder()
                            .setProjectId(PROJECT_ID)
                            .build());

            assertEquals(PROJECT_ID, response.getProject().getId());
            assertEquals(WORKSPACE_ID, response.getProject().getWorkspaceId());
        }

        @Test
        void projectNotFound_returnsNotFound() throws IOException {
            var stub = startServer(serviceWithNoProject());

            var ex = assertThrows(StatusRuntimeException.class, () ->
                    stub.getProject(GetProjectRequest.newBuilder()
                            .setProjectId("missing").build()));
            assertEquals(Status.Code.NOT_FOUND, ex.getStatus().getCode());
        }
    }

    @Nested
    class DeleteProject {

        @Test
        void deletesProject() throws IOException {
            var projectManager = mock(ProjectManager.class);
            when(projectManager.detailedInfo()).thenReturn(testDetailedInfo());
            var stub = startServer(serviceWithProjectManager(projectManager));

            stub.deleteProject(DeleteProjectRequest.newBuilder()
                    .setProjectId(PROJECT_ID).build());

            verify(projectManager).delete(WorkspaceEventCreator.MANUAL);
        }

        @Test
        void projectNotFound_returnsNotFound() throws IOException {
            var stub = startServer(serviceWithNoProject());

            var ex = assertThrows(StatusRuntimeException.class, () ->
                    stub.deleteProject(DeleteProjectRequest.newBuilder()
                            .setProjectId("missing").build()));
            assertEquals(Status.Code.NOT_FOUND, ex.getStatus().getCode());
        }
    }

    @Nested
    class RestoreProject {

        @Test
        void restoresProject() throws IOException {
            var projectManager = mock(ProjectManager.class);
            when(projectManager.detailedInfo()).thenReturn(testDetailedInfo());
            var stub = startServer(serviceWithProjectManager(projectManager));

            stub.restoreProject(RestoreProjectRequest.newBuilder()
                    .setProjectId(PROJECT_ID).build());

            verify(projectManager).restore();
        }

        @Test
        void projectNotFound_returnsNotFound() throws IOException {
            var stub = startServer(serviceWithNoProject());

            var ex = assertThrows(StatusRuntimeException.class, () ->
                    stub.restoreProject(RestoreProjectRequest.newBuilder()
                            .setProjectId("missing").build()));
            assertEquals(Status.Code.NOT_FOUND, ex.getStatus().getCode());
        }
    }

    // ========== Helpers ==========

    private static final pbouda.jeffrey.shared.common.model.ProjectInfo TEST_PROJECT_INFO =
            new pbouda.jeffrey.shared.common.model.ProjectInfo(
                    PROJECT_ID, "origin-1", "Test Project", "label", "namespace",
                    WORKSPACE_ID, FIXED_TIME, null, null, null);

    /**
     * Creates a service for ListProjects (which still goes through WorkspacesManager).
     */
    private ProjectGrpcService serviceForListProjects() {
        var projectManager = mock(ProjectManager.class);
        when(projectManager.detailedInfo()).thenReturn(testDetailedInfo());

        var projectsManager = mock(ProjectsManager.class);
        when(projectsManager.findAll()).thenReturn(List.of(projectManager));

        var workspaceManager = mock(WorkspaceManager.class);
        when(workspaceManager.projectsManager()).thenReturn(projectsManager);

        var workspacesManager = mock(WorkspacesManager.class);
        when(workspacesManager.findById(WORKSPACE_ID)).thenReturn(Optional.of(workspaceManager));

        var platformRepositories = mock(ServerPlatformRepositories.class);
        var projectManagerFactory = mock(ProjectManager.Factory.class);

        return new ProjectGrpcService(workspacesManager, platformRepositories, projectManagerFactory);
    }

    /**
     * Creates a service where findProject(PROJECT_ID) succeeds via platformRepositories.
     */
    private ProjectGrpcService serviceWithProject() {
        var projectManager = mock(ProjectManager.class);
        when(projectManager.detailedInfo()).thenReturn(testDetailedInfo());
        return serviceWithProjectManager(projectManager);
    }

    /**
     * Creates a service where findProject(PROJECT_ID) succeeds with the given ProjectManager.
     */
    private ProjectGrpcService serviceWithProjectManager(ProjectManager projectManager) {
        var projectRepo = mock(ProjectRepository.class);
        when(projectRepo.find()).thenReturn(Optional.of(TEST_PROJECT_INFO));

        var platformRepositories = mock(ServerPlatformRepositories.class);
        when(platformRepositories.newProjectRepository(PROJECT_ID)).thenReturn(projectRepo);

        var projectManagerFactory = mock(ProjectManager.Factory.class);
        when(projectManagerFactory.apply(TEST_PROJECT_INFO)).thenReturn(projectManager);

        var workspacesManager = mock(WorkspacesManager.class);

        return new ProjectGrpcService(workspacesManager, platformRepositories, projectManagerFactory);
    }

    /**
     * Creates a service where WorkspacesManager.findById returns empty (for ListProjects).
     */
    private ProjectGrpcService serviceWithNoWorkspace() {
        var workspacesManager = mock(WorkspacesManager.class);
        when(workspacesManager.findById(any())).thenReturn(Optional.empty());

        var platformRepositories = mock(ServerPlatformRepositories.class);
        var projectManagerFactory = mock(ProjectManager.Factory.class);

        return new ProjectGrpcService(workspacesManager, platformRepositories, projectManagerFactory);
    }

    /**
     * Creates a service where findProject("missing") fails (project not found).
     */
    private ProjectGrpcService serviceWithNoProject() {
        var projectRepo = mock(ProjectRepository.class);
        when(projectRepo.find()).thenReturn(Optional.empty());

        var platformRepositories = mock(ServerPlatformRepositories.class);
        when(platformRepositories.newProjectRepository(any())).thenReturn(projectRepo);

        var projectManagerFactory = mock(ProjectManager.Factory.class);
        var workspacesManager = mock(WorkspacesManager.class);

        return new ProjectGrpcService(workspacesManager, platformRepositories, projectManagerFactory);
    }

    private static DetailedProjectInfo testDetailedInfo() {
        return new DetailedProjectInfo(
                TEST_PROJECT_INFO,
                pbouda.jeffrey.shared.common.model.repository.RecordingStatus.ACTIVE,
                5, false);
    }
}
