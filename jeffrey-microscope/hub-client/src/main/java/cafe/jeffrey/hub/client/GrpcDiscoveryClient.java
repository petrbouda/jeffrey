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

package cafe.jeffrey.hub.client;

import cafe.jeffrey.hub.api.v1.CreateWorkspaceRequest;
import cafe.jeffrey.hub.api.v1.CreateWorkspaceResponse;
import cafe.jeffrey.hub.api.v1.DeleteWorkspaceRequest;
import cafe.jeffrey.hub.api.v1.GetApiInfoRequest;
import cafe.jeffrey.hub.api.v1.GetApiInfoResponse;
import cafe.jeffrey.hub.api.v1.GetProjectRequest;
import cafe.jeffrey.hub.api.v1.GetProjectResponse;
import cafe.jeffrey.hub.api.v1.ListProjectsRequest;
import cafe.jeffrey.hub.api.v1.ListProjectsResponse;
import cafe.jeffrey.hub.api.v1.ListWorkspacesRequest;
import cafe.jeffrey.hub.api.v1.ListWorkspacesResponse;
import cafe.jeffrey.hub.api.v1.ProjectInfo;
import cafe.jeffrey.hub.api.v1.ProjectServiceGrpc;
import cafe.jeffrey.hub.api.v1.WorkspaceServiceGrpc;
import cafe.jeffrey.hub.client.dto.RemoteProjectResponse;
import cafe.jeffrey.microscope.grpc.client.GrpcHubConnection;
import cafe.jeffrey.microscope.model.repository.RepositoryStatistics;
import cafe.jeffrey.microscope.model.workspace.WorkspaceInfo;
import cafe.jeffrey.microscope.model.workspace.WorkspaceStatus;
import io.grpc.StatusRuntimeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public class GrpcDiscoveryClient implements DiscoveryClient {

    private static final Logger LOG = LoggerFactory.getLogger(GrpcDiscoveryClient.class);

    private final WorkspaceServiceGrpc.WorkspaceServiceBlockingStub workspaceStub;
    private final ProjectServiceGrpc.ProjectServiceBlockingStub projectStub;

    public GrpcDiscoveryClient(GrpcHubConnection connection) {
        this.workspaceStub = WorkspaceServiceGrpc.newBlockingStub(connection.getChannel());
        this.projectStub = ProjectServiceGrpc.newBlockingStub(connection.getChannel());
    }

    @Override
    public PublicApiInfo info() {
        GetApiInfoResponse response = workspaceStub.getApiInfo(GetApiInfoRequest.getDefaultInstance());
        return new PublicApiInfo(response.getVersion());
    }

    @Override
    public List<WorkspaceInfo> allWorkspaces() {
        ListWorkspacesResponse response = workspaceStub.listWorkspaces(ListWorkspacesRequest.getDefaultInstance());
        return response.getWorkspacesList().stream()
                .map(GrpcDiscoveryClient::toWorkspaceInfo)
                .toList();
    }

    @Override
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
    @Override
    public Optional<WorkspaceInfo> workspaceOrThrow(String workspaceId) {
        // The hub has no single-workspace lookup: it holds a handful of workspaces, and the
        // listing answers this as well as a dedicated RPC would
        Optional<WorkspaceInfo> workspace = allWorkspaces().stream()
                .filter(candidate -> candidate.id().equals(workspaceId))
                .findFirst();
        if (workspace.isEmpty()) {
            LOG.debug("Workspace not found via gRPC: workspaceId={}", workspaceId);
        }
        return workspace;
    }

    @Override
    public WorkspaceInfo createWorkspace(String referenceId, String name) {
        CreateWorkspaceResponse response = workspaceStub.createWorkspace(
                CreateWorkspaceRequest.newBuilder()
                        .setReferenceId(referenceId)
                        .setName(name)
                        .build());
        return toWorkspaceInfo(response.getWorkspace());
    }

    @Override
    public void deleteWorkspace(String workspaceId) {
        workspaceStub.deleteWorkspace(
                DeleteWorkspaceRequest.newBuilder()
                        .setWorkspaceId(workspaceId)
                        .build());
    }

    @Override
    public List<RemoteProjectResponse> allProjects(String workspaceId) {
        ListProjectsResponse response = projectStub.listProjects(
                ListProjectsRequest.newBuilder()
                        .setWorkspaceId(workspaceId)
                        .build());

        return response.getProjectsList().stream()
                .map(GrpcDiscoveryClient::toRemoteProjectResponse)
                .toList();
    }

    @Override
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
                // A hub workspace is named by its directory on the volume, so its id is its reference id
                proto.getId(),
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
                proto.getName(),
                proto.getCreatedAt() != 0 ? Instant.ofEpochMilli(proto.getCreatedAt()) : null,
                proto.getWorkspaceId(),
                ClientProtoMappers.recordingStatus(proto.getStatus()),
                proto.getSessionCount(),
                proto.hasSizeBytes()
                        ? new RepositoryStatistics(proto.getSizeBytes())
                        : RepositoryStatistics.NOT_YET_MEASURED);
    }
}
