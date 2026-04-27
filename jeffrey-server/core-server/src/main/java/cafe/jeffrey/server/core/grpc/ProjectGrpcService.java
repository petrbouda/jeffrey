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

import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cafe.jeffrey.server.api.v1.*;
import cafe.jeffrey.server.core.manager.project.ProjectManager;
import cafe.jeffrey.server.core.manager.project.ProjectManager.DetailedProjectInfo;
import cafe.jeffrey.server.core.manager.workspace.WorkspaceManager;
import cafe.jeffrey.server.core.manager.workspace.WorkspacesManager;
import cafe.jeffrey.server.persistence.api.ServerPlatformRepositories;
import cafe.jeffrey.shared.common.model.workspace.WorkspaceEventCreator;

import java.util.List;

public class ProjectGrpcService extends ProjectServiceGrpc.ProjectServiceImplBase {

    private static final Logger LOG = LoggerFactory.getLogger(ProjectGrpcService.class);

    private final WorkspacesManager workspacesManager;
    private final ServerPlatformRepositories platformRepositories;
    private final ProjectManager.Factory projectManagerFactory;

    public ProjectGrpcService(
            WorkspacesManager workspacesManager,
            ServerPlatformRepositories platformRepositories,
            ProjectManager.Factory projectManagerFactory) {

        this.workspacesManager = workspacesManager;
        this.platformRepositories = platformRepositories;
        this.projectManagerFactory = projectManagerFactory;
    }

    @Override
    public void listProjects(ListProjectsRequest request, StreamObserver<ListProjectsResponse> responseObserver) {
        try {
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

            ListProjectsResponse response = ListProjectsResponse.newBuilder()
                    .addAllProjects(projects)
                    .build();
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (io.grpc.StatusRuntimeException e) {
            responseObserver.onError(e);
        } catch (Exception e) {
            LOG.error("Failed to list projects: workspaceId={}", request.getWorkspaceId(), e);
            responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
        }
    }

    @Override
    public void getProject(GetProjectRequest request, StreamObserver<GetProjectResponse> responseObserver) {
        try {
            ProjectManager project = findProject(request.getProjectId());

            LOG.debug("Fetched project via gRPC: projectId={}", request.getProjectId());

            responseObserver.onNext(GetProjectResponse.newBuilder()
                    .setProject(toProto(project.detailedInfo()))
                    .build());
            responseObserver.onCompleted();
        } catch (io.grpc.StatusRuntimeException e) {
            responseObserver.onError(e);
        } catch (Exception e) {
            LOG.error("Failed to get project: projectId={}", request.getProjectId(), e);
            responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
        }
    }

    @Override
    public void deleteProject(DeleteProjectRequest request, StreamObserver<DeleteProjectResponse> responseObserver) {
        try {
            ProjectManager project = findProject(request.getProjectId());
            project.delete(WorkspaceEventCreator.MANUAL);

            LOG.debug("Deleted project via gRPC: projectId={}", request.getProjectId());

            responseObserver.onNext(DeleteProjectResponse.getDefaultInstance());
            responseObserver.onCompleted();
        } catch (io.grpc.StatusRuntimeException e) {
            responseObserver.onError(e);
        } catch (Exception e) {
            LOG.error("Failed to delete project: projectId={}", request.getProjectId(), e);
            responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
        }
    }

    @Override
    public void restoreProject(RestoreProjectRequest request, StreamObserver<RestoreProjectResponse> responseObserver) {
        try {
            ProjectManager project = findProject(request.getProjectId());
            project.restore();

            LOG.info("Restored project via gRPC: projectId={}", request.getProjectId());

            responseObserver.onNext(RestoreProjectResponse.getDefaultInstance());
            responseObserver.onCompleted();
        } catch (io.grpc.StatusRuntimeException e) {
            responseObserver.onError(e);
        } catch (Exception e) {
            LOG.error("Failed to restore project: projectId={}", request.getProjectId(), e);
            responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
        }
    }

    private ProjectManager findProject(String projectId) {
        return platformRepositories.newProjectRepository(projectId).find()
                .map(projectManagerFactory)
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

    private static cafe.jeffrey.server.api.v1.RecordingStatus toProtoRecordingStatus(
            cafe.jeffrey.shared.common.model.repository.RecordingStatus status) {
        if (status == null) {
            return cafe.jeffrey.server.api.v1.RecordingStatus.RECORDING_STATUS_UNKNOWN;
        }
        return switch (status) {
            case ACTIVE -> cafe.jeffrey.server.api.v1.RecordingStatus.RECORDING_STATUS_ACTIVE;
            case FINISHED -> cafe.jeffrey.server.api.v1.RecordingStatus.RECORDING_STATUS_FINISHED;
            case UNKNOWN -> cafe.jeffrey.server.api.v1.RecordingStatus.RECORDING_STATUS_UNKNOWN;
        };
    }
}
