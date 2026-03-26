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

import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import pbouda.jeffrey.server.api.v1.*;
import pbouda.jeffrey.server.core.manager.project.ProjectManager;
import pbouda.jeffrey.server.core.manager.project.ProjectManager.DetailedProjectInfo;
import pbouda.jeffrey.server.core.manager.workspace.WorkspaceManager;
import pbouda.jeffrey.server.core.manager.workspace.WorkspacesManager;
import pbouda.jeffrey.shared.common.InstantUtils;
import pbouda.jeffrey.shared.common.model.workspace.WorkspaceEventCreator;

import java.util.List;

@Component
public class ProjectGrpcService extends ProjectServiceGrpc.ProjectServiceImplBase {

    private static final Logger LOG = LoggerFactory.getLogger(ProjectGrpcService.class);

    private final WorkspacesManager workspacesManager;

    public ProjectGrpcService(WorkspacesManager workspacesManager) {
        this.workspacesManager = workspacesManager;
    }

    @Override
    public void listProjects(ListProjectsRequest request, StreamObserver<ListProjectsResponse> responseObserver) {
        try {
            WorkspaceManager workspace = findWorkspace(request.getWorkspaceId());

            List<ProjectInfo> projects = workspace.projectsManager().findAll().stream()
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
            ProjectManager project = findProject(request.getWorkspaceId(), request.getProjectId());

            LOG.debug("Fetched project via gRPC: workspaceId={} projectId={}",
                    request.getWorkspaceId(), request.getProjectId());

            GetProjectResponse response = GetProjectResponse.newBuilder()
                    .setProject(toProto(project.detailedInfo()))
                    .build();
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (io.grpc.StatusRuntimeException e) {
            responseObserver.onError(e);
        } catch (Exception e) {
            LOG.error("Failed to get project: workspaceId={} projectId={}",
                    request.getWorkspaceId(), request.getProjectId(), e);
            responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
        }
    }

    @Override
    public void deleteProject(DeleteProjectRequest request, StreamObserver<DeleteProjectResponse> responseObserver) {
        try {
            ProjectManager project = findProject(request.getWorkspaceId(), request.getProjectId());
            project.delete(WorkspaceEventCreator.MANUAL);

            LOG.debug("Deleted project via gRPC: workspaceId={} projectId={}",
                    request.getWorkspaceId(), request.getProjectId());

            responseObserver.onNext(DeleteProjectResponse.getDefaultInstance());
            responseObserver.onCompleted();
        } catch (io.grpc.StatusRuntimeException e) {
            responseObserver.onError(e);
        } catch (Exception e) {
            LOG.error("Failed to delete project: workspaceId={} projectId={}",
                    request.getWorkspaceId(), request.getProjectId(), e);
            responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
        }
    }

    @Override
    public void blockProject(BlockProjectRequest request, StreamObserver<BlockProjectResponse> responseObserver) {
        try {
            ProjectManager project = findProject(request.getWorkspaceId(), request.getProjectId());
            project.block();

            LOG.info("Blocked project via gRPC: workspaceId={} projectId={}",
                    request.getWorkspaceId(), request.getProjectId());

            responseObserver.onNext(BlockProjectResponse.getDefaultInstance());
            responseObserver.onCompleted();
        } catch (io.grpc.StatusRuntimeException e) {
            responseObserver.onError(e);
        } catch (Exception e) {
            LOG.error("Failed to block project: workspaceId={} projectId={}",
                    request.getWorkspaceId(), request.getProjectId(), e);
            responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
        }
    }

    @Override
    public void unblockProject(UnblockProjectRequest request, StreamObserver<UnblockProjectResponse> responseObserver) {
        try {
            ProjectManager project = findProject(request.getWorkspaceId(), request.getProjectId());
            project.unblock();

            LOG.info("Unblocked project via gRPC: workspaceId={} projectId={}",
                    request.getWorkspaceId(), request.getProjectId());

            responseObserver.onNext(UnblockProjectResponse.getDefaultInstance());
            responseObserver.onCompleted();
        } catch (io.grpc.StatusRuntimeException e) {
            responseObserver.onError(e);
        } catch (Exception e) {
            LOG.error("Failed to unblock project: workspaceId={} projectId={}",
                    request.getWorkspaceId(), request.getProjectId(), e);
            responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
        }
    }

    private WorkspaceManager findWorkspace(String workspaceId) {
        return workspacesManager.findById(workspaceId)
                .orElseThrow(() -> Status.NOT_FOUND
                        .withDescription("Workspace not found: " + workspaceId)
                        .asRuntimeException());
    }

    private ProjectManager findProject(String workspaceId, String projectId) {
        WorkspaceManager workspace = findWorkspace(workspaceId);
        return workspace.projectsManager().project(projectId)
                .orElseThrow(() -> Status.NOT_FOUND
                        .withDescription("Project not found: " + projectId)
                        .asRuntimeException());
    }

    static ProjectInfo toProto(DetailedProjectInfo detail) {
        pbouda.jeffrey.shared.common.model.ProjectInfo info = detail.projectInfo();

        return ProjectInfo.newBuilder()
                .setId(info.id())
                .setOriginId(info.originId() != null ? info.originId() : "")
                .setName(info.name())
                .setLabel(info.label() != null ? info.label() : "")
                .setNamespace(info.namespace() != null ? info.namespace() : "")
                .setCreatedAt(info.createdAt() != null ? info.createdAt().toEpochMilli() : 0)
                .setWorkspaceId(info.workspaceId())
                .setStatus(toProtoRecordingStatus(detail.status()))
                .setSessionCount(detail.sessionCount())
                .setIsBlocked(detail.isBlocked())
                .build();
    }

    private static pbouda.jeffrey.server.api.v1.RecordingStatus toProtoRecordingStatus(
            pbouda.jeffrey.shared.common.model.repository.RecordingStatus status) {
        if (status == null) {
            return pbouda.jeffrey.server.api.v1.RecordingStatus.RECORDING_STATUS_UNKNOWN;
        }
        return switch (status) {
            case ACTIVE -> pbouda.jeffrey.server.api.v1.RecordingStatus.RECORDING_STATUS_ACTIVE;
            case FINISHED -> pbouda.jeffrey.server.api.v1.RecordingStatus.RECORDING_STATUS_FINISHED;
            case UNKNOWN -> pbouda.jeffrey.server.api.v1.RecordingStatus.RECORDING_STATUS_UNKNOWN;
        };
    }
}
