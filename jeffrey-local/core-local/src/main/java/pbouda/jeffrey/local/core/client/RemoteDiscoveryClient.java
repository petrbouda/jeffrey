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

package pbouda.jeffrey.local.core.client;

import io.grpc.StatusRuntimeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pbouda.jeffrey.api.v1.*;
import pbouda.jeffrey.local.core.resources.response.ProjectResponse;
import pbouda.jeffrey.local.core.resources.response.PublicApiInfoResponse;
import pbouda.jeffrey.local.core.resources.response.WorkspaceResponse;
import pbouda.jeffrey.shared.common.model.RecordingEventSource;
import pbouda.jeffrey.shared.common.model.repository.RecordingStatus;
import pbouda.jeffrey.local.persistence.model.RemoteWorkspaceInfo;
import pbouda.jeffrey.shared.common.model.workspace.WorkspaceLocation;
import pbouda.jeffrey.shared.common.model.workspace.WorkspaceStatus;

import java.time.Instant;
import java.util.List;

public class RemoteDiscoveryClient {

    public record WorkspaceResult(RemoteWorkspaceInfo info, WorkspaceStatus status) {
        public static WorkspaceResult of(WorkspaceStatus status) {
            return new WorkspaceResult(null, status);
        }

        public static WorkspaceResult of(RemoteWorkspaceInfo info) {
            return new WorkspaceResult(info, WorkspaceStatus.AVAILABLE);
        }
    }

    private static final Logger LOG = LoggerFactory.getLogger(RemoteDiscoveryClient.class);

    private final GrpcServerConnection connection;
    private final WorkspaceServiceGrpc.WorkspaceServiceBlockingStub workspaceStub;
    private final ProjectServiceGrpc.ProjectServiceBlockingStub projectStub;

    public RemoteDiscoveryClient(GrpcServerConnection connection) {
        this.connection = connection;
        this.workspaceStub = WorkspaceServiceGrpc.newBlockingStub(connection.getChannel());
        this.projectStub = ProjectServiceGrpc.newBlockingStub(connection.getChannel());
    }

    public PublicApiInfoResponse info() {
        GetApiInfoResponse response = workspaceStub.getApiInfo(GetApiInfoRequest.getDefaultInstance());
        return new PublicApiInfoResponse(response.getVersion(), response.getApiVersion());
    }

    public List<WorkspaceResponse> allWorkspaces() {
        ListWorkspacesResponse response = workspaceStub.listWorkspaces(ListWorkspacesRequest.getDefaultInstance());
        return response.getWorkspacesList().stream()
                .map(RemoteDiscoveryClient::toWorkspaceResponse)
                .toList();
    }

    public WorkspaceResult workspace(String workspaceId) {
        try {
            GetWorkspaceResponse response = workspaceStub.getWorkspace(
                    GetWorkspaceRequest.newBuilder()
                            .setWorkspaceId(workspaceId)
                            .build());

            pbouda.jeffrey.api.v1.WorkspaceInfo proto = response.getWorkspace();
            RemoteWorkspaceInfo info = toWorkspaceInfo(proto);
            return WorkspaceResult.of(info);
        } catch (StatusRuntimeException e) {
            if (e.getStatus().getCode() == io.grpc.Status.Code.NOT_FOUND) {
                LOG.debug("Workspace not found via gRPC: workspaceId={}", workspaceId);
                return WorkspaceResult.of(WorkspaceStatus.UNAVAILABLE);
            }
            LOG.warn("Failed to get workspace via gRPC: workspaceId={} status={}", workspaceId, e.getStatus());
            return WorkspaceResult.of(WorkspaceStatus.UNAVAILABLE);
        } catch (Exception e) {
            LOG.warn("Unexpected error getting workspace via gRPC: workspaceId={}", workspaceId, e);
            return WorkspaceResult.of(WorkspaceStatus.UNAVAILABLE);
        }
    }

    public List<ProjectResponse> allProjects(String workspaceId) {
        ListProjectsResponse response = projectStub.listProjects(
                ListProjectsRequest.newBuilder()
                        .setWorkspaceId(workspaceId)
                        .build());

        return response.getProjectsList().stream()
                .map(RemoteDiscoveryClient::toProjectResponse)
                .toList();
    }

    private RemoteWorkspaceInfo toWorkspaceInfo(pbouda.jeffrey.api.v1.WorkspaceInfo proto) {
        return new RemoteWorkspaceInfo(
                null,
                proto.getId(),
                proto.getName(),
                proto.getDescription(),
                WorkspaceLocation.of(connection.location()),
                Instant.ofEpochMilli(proto.getCreatedAt()),
                fromProtoStatus(proto.getStatus()),
                proto.getProjectCount());
    }

    private static WorkspaceResponse toWorkspaceResponse(pbouda.jeffrey.api.v1.WorkspaceInfo proto) {
        return new WorkspaceResponse(
                proto.getId(),
                proto.getName(),
                proto.getDescription(),
                proto.getCreatedAt(),
                proto.getProjectCount(),
                fromProtoStatus(proto.getStatus()));
    }

    private static ProjectResponse toProjectResponse(ProjectInfo proto) {
        return new ProjectResponse(
                proto.getId(),
                proto.getOriginId().isEmpty() ? null : proto.getOriginId(),
                proto.getName(),
                proto.getLabel().isEmpty() ? null : proto.getLabel(),
                proto.getNamespace().isEmpty() ? null : proto.getNamespace(),
                proto.getCreatedAt().isEmpty() ? null : proto.getCreatedAt(),
                proto.getWorkspaceId(),
                parseRecordingStatus(proto.getStatus()),
                proto.getProfileCount(),
                proto.getRecordingCount(),
                proto.getSessionCount(),
                proto.getJobCount(),
                proto.getAlertCount(),
                parseEventSource(proto.getEventSource()),
                proto.getIsVirtual(),
                proto.getIsOrphaned(),
                proto.getCollectorOnlyModeEnabled());
    }

    private static WorkspaceStatus fromProtoStatus(pbouda.jeffrey.api.v1.WorkspaceStatus status) {
        return switch (status) {
            case WORKSPACE_STATUS_AVAILABLE -> WorkspaceStatus.AVAILABLE;
            case WORKSPACE_STATUS_UNAVAILABLE -> WorkspaceStatus.UNAVAILABLE;
            case WORKSPACE_STATUS_INCOMPATIBLE -> WorkspaceStatus.UNKNOWN;
            default -> WorkspaceStatus.UNKNOWN;
        };
    }

    private static RecordingStatus parseRecordingStatus(String status) {
        try {
            return RecordingStatus.valueOf(status);
        } catch (IllegalArgumentException e) {
            return RecordingStatus.UNKNOWN;
        }
    }

    private static RecordingEventSource parseEventSource(String eventSource) {
        if (eventSource == null || eventSource.isEmpty()) {
            return null;
        }
        try {
            return RecordingEventSource.valueOf(eventSource);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
