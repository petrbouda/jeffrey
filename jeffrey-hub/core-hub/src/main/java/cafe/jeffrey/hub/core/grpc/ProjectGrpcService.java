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

import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cafe.jeffrey.hub.api.v1.*;
import cafe.jeffrey.hub.core.manager.project.ProjectManager;
import cafe.jeffrey.hub.core.manager.project.ProjectManager.DetailedProjectInfo;
import cafe.jeffrey.hub.core.manager.project.ProjectsManager;
import cafe.jeffrey.hub.core.manager.workspace.WorkspaceManager;
import cafe.jeffrey.hub.core.manager.workspace.WorkspacesManager;
import cafe.jeffrey.hub.persistence.api.HubPlatformRepositories;
import cafe.jeffrey.shared.common.model.workspace.WorkspaceEventCreator;

import java.util.List;
import java.util.Optional;

public class ProjectGrpcService extends ProjectServiceGrpc.ProjectServiceImplBase {

    private static final Logger LOG = LoggerFactory.getLogger(ProjectGrpcService.class);

    private final WorkspacesManager workspacesManager;
    private final HubPlatformRepositories platformRepositories;
    private final ProjectManager.Factory projectManagerFactory;

    public ProjectGrpcService(
            WorkspacesManager workspacesManager,
            HubPlatformRepositories platformRepositories,
            ProjectManager.Factory projectManagerFactory) {

        this.workspacesManager = workspacesManager;
        this.platformRepositories = platformRepositories;
        this.projectManagerFactory = projectManagerFactory;
    }

    @Override
    public void listProjects(ListProjectsRequest request, StreamObserver<ListProjectsResponse> responseObserver) {
        GrpcUnary.respond(responseObserver, () -> {
            WorkspaceManager workspace = workspacesManager.findById(request.getWorkspaceId())
                    .orElseThrow(() -> GrpcExceptions.notFound("Workspace not found: " + request.getWorkspaceId()));

            var projectsManager = workspace.projectsManager();
            var managers = request.getIncludeDeleted()
                    ? projectsManager.findAllIncludingDeleted()
                    : projectsManager.findAll();

            List<ProjectInfo> projects = managers.stream()
                    .map(ProjectManager::detailedInfo)
                    .map(ProjectGrpcService::toProto)
                    .toList();

            LOG.debug("Listed projects via gRPC: workspaceId={} count={}", request.getWorkspaceId(), projects.size());

            return ListProjectsResponse.newBuilder()
                    .addAllProjects(projects)
                    .build();
        });
    }

    @Override
    public void getProject(GetProjectRequest request, StreamObserver<GetProjectResponse> responseObserver) {
        GrpcUnary.respond(responseObserver, () -> {
            WorkspaceManager workspace = workspacesManager.findById(request.getWorkspaceId())
                    .orElseThrow(() -> GrpcExceptions.notFound("Workspace not found: " + request.getWorkspaceId()));

            ProjectManager project = findProjectInWorkspace(
                    workspace, request.getWorkspaceId(), request.getProjectId());

            LOG.debug("Fetched project via gRPC: workspaceId={} projectId={}",
                    request.getWorkspaceId(), request.getProjectId());

            return GetProjectResponse.newBuilder()
                    .setProject(toProto(project.detailedInfo()))
                    .build();
        });
    }

    @Override
    public void deleteProject(DeleteProjectRequest request, StreamObserver<DeleteProjectResponse> responseObserver) {
        GrpcUnary.respond(responseObserver, () -> {
            ProjectManager project = findProject(request.getProjectId());
            project.delete(WorkspaceEventCreator.MANUAL);

            LOG.debug("Deleted project via gRPC: projectId={}", request.getProjectId());

            return DeleteProjectResponse.getDefaultInstance();
        });
    }

    @Override
    public void restoreProject(RestoreProjectRequest request, StreamObserver<RestoreProjectResponse> responseObserver) {
        GrpcUnary.respond(responseObserver, () -> {
            ProjectManager project = findProjectIncludingDeleted(request.getProjectId());
            project.restore();

            LOG.info("Restored project via gRPC: projectId={}", request.getProjectId());

            return RestoreProjectResponse.getDefaultInstance();
        });
    }

    private ProjectManager findProject(String projectId) {
        return platformRepositories.newProjectRepository(projectId).find()
                .map(projectManagerFactory)
                .orElseThrow(() -> GrpcExceptions.notFound("Project not found: " + projectId));
    }

    /**
     * Resolves a project regardless of its soft-deleted state. Restore must see
     * soft-deleted projects — the active-only {@link #findProject(String)} filters
     * them out, which would make restoring impossible.
     */
    private ProjectManager findProjectIncludingDeleted(String projectId) {
        return platformRepositories.newProjectRepository(projectId).findIncludingDeleted()
                .map(projectManagerFactory)
                .orElseThrow(() -> GrpcExceptions.notFound("Project not found: " + projectId));
    }

    /**
     * Finds a single project within a workspace. Active projects are resolved with a
     * direct single-row lookup; soft-deleted projects fall back to the deleted-inclusive
     * listing so restore/management lookups keep working (mirrors the listing path
     * with include_deleted=true).
     */
    private static ProjectManager findProjectInWorkspace(
            WorkspaceManager workspace, String workspaceId, String projectId) {

        ProjectsManager projectsManager = workspace.projectsManager();

        Optional<ProjectManager> activeProject = projectsManager.project(projectId)
                .filter(manager -> workspaceId.equals(manager.info().workspaceId()));
        if (activeProject.isPresent()) {
            return activeProject.get();
        }

        return projectsManager.findAllIncludingDeleted().stream()
                .filter(manager -> manager.info().id().equals(projectId))
                .findFirst()
                .orElseThrow(() -> GrpcExceptions.notFound("Project not found: " + projectId));
    }

    static ProjectInfo toProto(DetailedProjectInfo detail) {
        cafe.jeffrey.shared.common.model.ProjectInfo info = detail.projectInfo();

        var builder = ProjectInfo.newBuilder()
                .setId(info.id())
                .setOriginId(info.originId() != null ? info.originId() : "")
                .setName(info.name())
                .setLabel(info.label() != null ? info.label() : "")
                .setNamespace(info.namespace() != null ? info.namespace() : "")
                .setCreatedAt(info.createdAt() != null ? info.createdAt().toEpochMilli() : 0)
                .setWorkspaceId(info.workspaceId())
                .setStatus(toProtoRecordingStatus(detail.status()))
                .setSessionCount(detail.sessionCount());

        if (info.deletedAt() != null) {
            builder.setDeletedAt(info.deletedAt().toEpochMilli());
        }

        return builder.build();
    }

    private static cafe.jeffrey.hub.api.v1.RecordingStatus toProtoRecordingStatus(
            cafe.jeffrey.shared.common.model.repository.RecordingStatus status) {
        if (status == null) {
            return cafe.jeffrey.hub.api.v1.RecordingStatus.RECORDING_STATUS_UNKNOWN;
        }
        return switch (status) {
            case ACTIVE -> cafe.jeffrey.hub.api.v1.RecordingStatus.RECORDING_STATUS_ACTIVE;
            case FINISHED -> cafe.jeffrey.hub.api.v1.RecordingStatus.RECORDING_STATUS_FINISHED;
            case UNKNOWN -> cafe.jeffrey.hub.api.v1.RecordingStatus.RECORDING_STATUS_UNKNOWN;
        };
    }
}
