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

package cafe.jeffrey.hub.client;

import cafe.jeffrey.hub.api.v1.CreateWorkspaceRequest;
import cafe.jeffrey.hub.api.v1.CreateWorkspaceResponse;
import cafe.jeffrey.hub.api.v1.DeleteWorkspaceRequest;
import cafe.jeffrey.hub.api.v1.GetApiInfoRequest;
import cafe.jeffrey.hub.api.v1.GetApiInfoResponse;
import cafe.jeffrey.hub.api.v1.GetProjectRequest;
import cafe.jeffrey.hub.api.v1.GetProjectResponse;
import cafe.jeffrey.hub.api.v1.GetWorkspaceRequest;
import cafe.jeffrey.hub.api.v1.GetWorkspaceResponse;
import cafe.jeffrey.hub.api.v1.ListProjectsRequest;
import cafe.jeffrey.hub.api.v1.ListProjectsResponse;
import cafe.jeffrey.hub.api.v1.ListWorkspacesRequest;
import cafe.jeffrey.hub.api.v1.ListWorkspacesResponse;
import cafe.jeffrey.hub.api.v1.ProjectInfo;
import cafe.jeffrey.hub.api.v1.ProjectServiceGrpc;
import cafe.jeffrey.hub.api.v1.WorkspaceServiceGrpc;
import cafe.jeffrey.hub.client.dto.RemoteProjectResponse;
import cafe.jeffrey.microscope.grpc.client.GrpcHubConnection;
import cafe.jeffrey.shared.common.InstantUtils;
import cafe.jeffrey.shared.common.model.workspace.WorkspaceInfo;
import cafe.jeffrey.shared.common.model.workspace.WorkspaceStatus;
import io.grpc.StatusRuntimeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public class DiscoveryClient {

    public record WorkspaceResult(WorkspaceInfo info, WorkspaceStatus status) {
        public static WorkspaceResult of(WorkspaceStatus status) {
            return new WorkspaceResult(null, status);
        }

        public static WorkspaceResult of(WorkspaceInfo info) {
            return new WorkspaceResult(info, WorkspaceStatus.AVAILABLE);
        }
    }

    private static final Logger LOG = LoggerFactory.getLogger(DiscoveryClient.class);

    private final WorkspaceServiceGrpc.WorkspaceServiceBlockingStub workspaceStub;
    private final ProjectServiceGrpc.ProjectServiceBlockingStub projectStub;

    public DiscoveryClient(GrpcHubConnection connection) {
        this.workspaceStub = WorkspaceServiceGrpc.newBlockingStub(connection.getChannel());
        this.projectStub = ProjectServiceGrpc.newBlockingStub(connection.getChannel());
    }

    public PublicApiInfo info() {
        GetApiInfoResponse response = workspaceStub.getApiInfo(GetApiInfoRequest.getDefaultInstance());
        return new PublicApiInfo(response.getVersion(), response.getApiVersion());
    }

    public record PublicApiInfo(String version, int apiVersion) {
    }

    public List<WorkspaceInfo> allWorkspaces() {
        ListWorkspacesResponse response = workspaceStub.listWorkspaces(ListWorkspacesRequest.getDefaultInstance());
        return response.getWorkspacesList().stream()
                .map(DiscoveryClient::toWorkspaceInfo)
                .toList();
    }

    public WorkspaceResult workspace(String workspaceId) {
        try {
            return workspaceOrThrow(workspaceId)
                    .map(WorkspaceResult::of)
                    .orElseGet(() -> WorkspaceResult.of(WorkspaceStatus.UNAVAILABLE));
        } catch (StatusRuntimeException e) {
            LOG.warn("Failed to get workspace via gRPC: workspaceId={} status={}", workspaceId, e.getStatus());
            return WorkspaceResult.of(WorkspaceStatus.OFFLINE);
        } catch (Exception e) {
            LOG.warn("Cannot reach hub: workspaceId={}", workspaceId, e);
            return WorkspaceResult.of(WorkspaceStatus.OFFLINE);
        }
    }

    /**
     * Gets one workspace without collapsing remote failures into an offline UI state. Callers that
     * need to report whether the hub rejected, timed out, or could not be reached use this path;
     * the ordinary {@link #workspace(String)} method keeps its best-effort UI behavior.
     */
    public Optional<WorkspaceInfo> workspaceOrThrow(String workspaceId) {
        try {
            GetWorkspaceResponse response = workspaceStub.getWorkspace(
                    GetWorkspaceRequest.newBuilder()
                            .setWorkspaceId(workspaceId)
                            .build());

            return Optional.of(toWorkspaceInfo(response.getWorkspace()));
        } catch (StatusRuntimeException e) {
            if (GrpcClientErrors.isNotFound(e)) {
                LOG.debug("Workspace not found via gRPC: workspaceId={}", workspaceId);
                return Optional.empty();
            }
            throw e;
        }
    }

    public WorkspaceInfo createWorkspace(String referenceId, String name) {
        CreateWorkspaceResponse response = workspaceStub.createWorkspace(
                CreateWorkspaceRequest.newBuilder()
                        .setReferenceId(referenceId)
                        .setName(name)
                        .build());
        return toWorkspaceInfo(response.getWorkspace());
    }

    public void deleteWorkspace(String workspaceId) {
        workspaceStub.deleteWorkspace(
                DeleteWorkspaceRequest.newBuilder()
                        .setWorkspaceId(workspaceId)
                        .build());
    }

    public Optional<PublicApiInfo> tryInfo() {
        try {
            return Optional.of(info());
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public List<RemoteProjectResponse> allProjects(String workspaceId, boolean includeDeleted) {
        ListProjectsResponse response = projectStub.listProjects(
                ListProjectsRequest.newBuilder()
                        .setWorkspaceId(workspaceId)
                        .setIncludeDeleted(includeDeleted)
                        .build());

        return response.getProjectsList().stream()
                .map(DiscoveryClient::toRemoteProjectResponse)
                .toList();
    }

    public Optional<RemoteProjectResponse> project(String workspaceId, String projectId) {
        try {
            GetProjectResponse response = projectStub.getProject(
                    GetProjectRequest.newBuilder()
                            .setWorkspaceId(workspaceId)
                            .setProjectId(projectId)
                            .build());

            return Optional.of(toRemoteProjectResponse(response.getProject()));
        } catch (StatusRuntimeException e) {
            if (GrpcClientErrors.isNotFound(e)) {
                LOG.debug("Project not found via gRPC: workspaceId={} projectId={}", workspaceId, projectId);
                return Optional.empty();
            }
            throw e;
        }
    }

    static WorkspaceInfo toWorkspaceInfo(cafe.jeffrey.hub.api.v1.WorkspaceInfo proto) {
        return new WorkspaceInfo(
                proto.getId(),
                proto.getReferenceId().isEmpty() ? proto.getId() : proto.getReferenceId(),
                null,
                proto.getName(),
                null,
                null,
                Instant.ofEpochMilli(proto.getCreatedAt()),
                ClientProtoMappers.workspaceStatus(proto.getStatus()),
                proto.getProjectCount());
    }

    private static RemoteProjectResponse toRemoteProjectResponse(ProjectInfo proto) {
        return new RemoteProjectResponse(
                proto.getId(),
                ClientProtoMappers.nullIfEmpty(proto.getOriginId()),
                proto.getName(),
                ClientProtoMappers.nullIfEmpty(proto.getLabel()),
                ClientProtoMappers.nullIfEmpty(proto.getNamespace()),
                proto.getCreatedAt() != 0 ? InstantUtils.formatInstant(Instant.ofEpochMilli(proto.getCreatedAt())) : null,
                proto.getWorkspaceId(),
                ClientProtoMappers.recordingStatus(proto.getStatus()),
                proto.getSessionCount(),
                proto.hasDeletedAt() ? Instant.ofEpochMilli(proto.getDeletedAt()) : null);
    }
}
