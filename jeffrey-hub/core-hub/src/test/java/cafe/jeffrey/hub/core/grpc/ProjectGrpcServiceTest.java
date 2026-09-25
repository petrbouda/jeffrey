/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package cafe.jeffrey.hub.core.grpc;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import cafe.jeffrey.hub.api.v1.*;
import cafe.jeffrey.hub.core.manager.project.ProjectManager;
import cafe.jeffrey.hub.core.manager.project.ProjectManager.DetailedProjectInfo;
import cafe.jeffrey.hub.core.manager.project.ProjectsManager;
import cafe.jeffrey.hub.core.manager.workspace.WorkspaceManager;
import cafe.jeffrey.hub.core.manager.workspace.WorkspacesManager;
import cafe.jeffrey.hub.persistence.api.ProjectRepository;
import cafe.jeffrey.hub.persistence.api.HubPlatformRepositories;

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

    private InProcessGrpcServer grpc;

    private ProjectServiceGrpc.ProjectServiceBlockingStub startServer(
            ProjectGrpcService service) {
        grpc = InProcessGrpcServer.start(service);
        return ProjectServiceGrpc.newBlockingStub(grpc.channel());
    }

    @AfterEach
    void shutdown() {
        if (grpc != null) {
            grpc.close();
        }
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
        void returnsActiveProject() throws IOException {
            var stub = startServer(serviceWithWorkspaceProject());

            GetProjectResponse response = stub.getProject(
                    GetProjectRequest.newBuilder()
                            .setWorkspaceId(WORKSPACE_ID)
                            .setProjectId(PROJECT_ID)
                            .build());

            assertEquals(PROJECT_ID, response.getProject().getId());
            assertEquals(WORKSPACE_ID, response.getProject().getWorkspaceId());
        }

        @Test
        void returnsDeletedProjectViaFallback() throws IOException {
            var stub = startServer(serviceWithDeletedWorkspaceProject());

            GetProjectResponse response = stub.getProject(
                    GetProjectRequest.newBuilder()
                            .setWorkspaceId(WORKSPACE_ID)
                            .setProjectId(PROJECT_ID)
                            .build());

            assertEquals(PROJECT_ID, response.getProject().getId());
            assertTrue(response.getProject().hasDeletedAt());
        }

        @Test
        void projectFromAnotherWorkspace_returnsNotFound() throws IOException {
            var stub = startServer(serviceWithProjectInAnotherWorkspace());

            var ex = assertThrows(StatusRuntimeException.class, () ->
                    stub.getProject(GetProjectRequest.newBuilder()
                            .setWorkspaceId(WORKSPACE_ID)
                            .setProjectId(PROJECT_ID).build()));
            assertEquals(Status.Code.NOT_FOUND, ex.getStatus().getCode());
        }

        @Test
        void projectNotFound_returnsNotFound() throws IOException {
            var stub = startServer(serviceWithWorkspaceWithoutProjects());

            var ex = assertThrows(StatusRuntimeException.class, () ->
                    stub.getProject(GetProjectRequest.newBuilder()
                            .setWorkspaceId(WORKSPACE_ID)
                            .setProjectId("missing").build()));
            assertEquals(Status.Code.NOT_FOUND, ex.getStatus().getCode());
        }

        @Test
        void workspaceNotFound_returnsNotFound() throws IOException {
            var stub = startServer(serviceWithNoWorkspace());

            var ex = assertThrows(StatusRuntimeException.class, () ->
                    stub.getProject(GetProjectRequest.newBuilder()
                            .setWorkspaceId("missing")
                            .setProjectId(PROJECT_ID).build()));
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

            verify(projectManager).delete();
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
        void restoresSoftDeletedProject() throws IOException {
            var deletedInfo = new cafe.jeffrey.hub.model.ProjectInfo(
                    PROJECT_ID, "origin-1", "Test Project", "namespace",
                    WORKSPACE_ID, FIXED_TIME, null, null, FIXED_TIME);

            var projectManager = mock(ProjectManager.class);
            var stub = startServer(serviceWithSoftDeletedProject(deletedInfo, projectManager));

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

    private static final cafe.jeffrey.hub.model.ProjectInfo TEST_PROJECT_INFO =
            new cafe.jeffrey.hub.model.ProjectInfo(
                    PROJECT_ID, "origin-1", "Test Project", "namespace",
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

        var platformRepositories = mock(HubPlatformRepositories.class);
        var projectManagerFactory = mock(ProjectManager.Factory.class);

        return new ProjectGrpcService(workspacesManager, new GrpcLookups(platformRepositories, null, projectManagerFactory));
    }

    private ProjectGrpcService serviceWithWorkspaceProject() {
        return serviceWithProjectInLookup(TEST_PROJECT_INFO, testDetailedInfo());
    }

    /**
     * GetProject has to see a soft-deleted project — restore names one — so it resolves through
     * the deleted-inclusive lookup.
     */
    private ProjectGrpcService serviceWithDeletedWorkspaceProject() {
        var deletedInfo = new cafe.jeffrey.hub.model.ProjectInfo(
                PROJECT_ID, "origin-1", "Test Project", "namespace",
                WORKSPACE_ID, FIXED_TIME, null, null, FIXED_TIME);

        return serviceWithProjectInLookup(deletedInfo, new DetailedProjectInfo(
                deletedInfo,
                cafe.jeffrey.hub.model.repository.RecordingStatus.FINISHED,
                0));
    }

    /**
     * The project exists but belongs to another workspace, so the workspace-scoped GetProject
     * must not resolve it.
     */
    private ProjectGrpcService serviceWithProjectInAnotherWorkspace() {
        var foreignInfo = new cafe.jeffrey.hub.model.ProjectInfo(
                PROJECT_ID, "origin-1", "Test Project", "namespace",
                "other-workspace", FIXED_TIME, null, null, null);

        return serviceWithProjectInLookup(foreignInfo, null);
    }

    /**
     * The workspace exists but no project does.
     */
    private ProjectGrpcService serviceWithWorkspaceWithoutProjects() {
        return serviceWithProjectInLookup(null, null);
    }

    /**
     * A service whose WORKSPACE_ID workspace exists and whose deleted-inclusive project lookup
     * answers with {@code info} for PROJECT_ID (nothing for any id when {@code info} is null).
     */
    private ProjectGrpcService serviceWithProjectInLookup(
            cafe.jeffrey.hub.model.ProjectInfo info, DetailedProjectInfo detail) {

        var workspacesManager = mock(WorkspacesManager.class);
        when(workspacesManager.findById(WORKSPACE_ID)).thenReturn(Optional.of(mock(WorkspaceManager.class)));

        var projectRepo = mock(ProjectRepository.class);
        when(projectRepo.findIncludingDeleted()).thenReturn(Optional.ofNullable(info));
        var platformRepositories = mock(HubPlatformRepositories.class);
        when(platformRepositories.newProjectRepository(any())).thenReturn(projectRepo);

        var projectManager = mock(ProjectManager.class);
        when(projectManager.info()).thenReturn(info);
        when(projectManager.detailedInfo()).thenReturn(detail);
        var projectManagerFactory = mock(ProjectManager.Factory.class);
        when(projectManagerFactory.apply(any())).thenReturn(projectManager);

        return new ProjectGrpcService(workspacesManager, new GrpcLookups(platformRepositories, null, projectManagerFactory));
    }

    /**
     * Creates a service where findProject(PROJECT_ID) succeeds with the given ProjectManager.
     */
    private ProjectGrpcService serviceWithProjectManager(ProjectManager projectManager) {
        var projectRepo = mock(ProjectRepository.class);
        when(projectRepo.find()).thenReturn(Optional.of(TEST_PROJECT_INFO));

        var platformRepositories = mock(HubPlatformRepositories.class);
        when(platformRepositories.newProjectRepository(PROJECT_ID)).thenReturn(projectRepo);

        var projectManagerFactory = mock(ProjectManager.Factory.class);
        when(projectManagerFactory.apply(TEST_PROJECT_INFO)).thenReturn(projectManager);

        var workspacesManager = mock(WorkspacesManager.class);

        return new ProjectGrpcService(workspacesManager, new GrpcLookups(platformRepositories, null, projectManagerFactory));
    }

    /**
     * Creates a service where WorkspacesManager.findById returns empty (for ListProjects).
     */
    private ProjectGrpcService serviceWithNoWorkspace() {
        var workspacesManager = mock(WorkspacesManager.class);
        when(workspacesManager.findById(any())).thenReturn(Optional.empty());

        var platformRepositories = mock(HubPlatformRepositories.class);
        var projectManagerFactory = mock(ProjectManager.Factory.class);

        return new ProjectGrpcService(workspacesManager, new GrpcLookups(platformRepositories, null, projectManagerFactory));
    }

    /**
     * Creates a service where the project exists only as a soft-deleted row: the active-only
     * find() is empty, the deleted-inclusive findIncludingDeleted() resolves it. This is the
     * exact state RestoreProject must be able to operate on.
     */
    private ProjectGrpcService serviceWithSoftDeletedProject(
            cafe.jeffrey.hub.model.ProjectInfo deletedInfo, ProjectManager projectManager) {

        var projectRepo = mock(ProjectRepository.class);
        when(projectRepo.find()).thenReturn(Optional.empty());
        when(projectRepo.findIncludingDeleted()).thenReturn(Optional.of(deletedInfo));

        var platformRepositories = mock(HubPlatformRepositories.class);
        when(platformRepositories.newProjectRepository(PROJECT_ID)).thenReturn(projectRepo);

        var projectManagerFactory = mock(ProjectManager.Factory.class);
        when(projectManagerFactory.apply(deletedInfo)).thenReturn(projectManager);

        var workspacesManager = mock(WorkspacesManager.class);

        return new ProjectGrpcService(workspacesManager, new GrpcLookups(platformRepositories, null, projectManagerFactory));
    }

    /**
     * Creates a service where findProject("missing") fails (project not found).
     */
    private ProjectGrpcService serviceWithNoProject() {
        var projectRepo = mock(ProjectRepository.class);
        when(projectRepo.find()).thenReturn(Optional.empty());
        when(projectRepo.findIncludingDeleted()).thenReturn(Optional.empty());

        var platformRepositories = mock(HubPlatformRepositories.class);
        when(platformRepositories.newProjectRepository(any())).thenReturn(projectRepo);

        var projectManagerFactory = mock(ProjectManager.Factory.class);
        var workspacesManager = mock(WorkspacesManager.class);

        return new ProjectGrpcService(workspacesManager, new GrpcLookups(platformRepositories, null, projectManagerFactory));
    }

    private static DetailedProjectInfo testDetailedInfo() {
        return new DetailedProjectInfo(
                TEST_PROJECT_INFO,
                cafe.jeffrey.hub.model.repository.RecordingStatus.ACTIVE,
                5);
    }
}
