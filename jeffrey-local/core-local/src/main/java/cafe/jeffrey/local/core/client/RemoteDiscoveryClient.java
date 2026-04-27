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

package cafe.jeffrey.local.core.client;

import cafe.jeffrey.local.grpc.client.*;

import io.grpc.StatusRuntimeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cafe.jeffrey.server.api.v1.*;
import cafe.jeffrey.local.core.resources.response.PublicApiInfoResponse;
import cafe.jeffrey.local.core.resources.response.RemoteProjectResponse;
import cafe.jeffrey.local.core.resources.response.WorkspaceResponse;
import cafe.jeffrey.shared.common.model.repository.RecordingStatus;
import cafe.jeffrey.local.persistence.api.RemoteWorkspaceInfo;
import cafe.jeffrey.shared.common.model.workspace.WorkspaceStatus;

import cafe.jeffrey.shared.common.InstantUtils;

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

            cafe.jeffrey.server.api.v1.WorkspaceInfo proto = response.getWorkspace();
            RemoteWorkspaceInfo info = toWorkspaceInfo(proto);
            return WorkspaceResult.of(info);
        } catch (StatusRuntimeException e) {
            if (e.getStatus().getCode() == io.grpc.Status.Code.NOT_FOUND) {
                LOG.debug("Workspace not found via gRPC: workspaceId={}", workspaceId);
                return WorkspaceResult.of(WorkspaceStatus.UNAVAILABLE);
            }
            LOG.warn("Failed to get workspace via gRPC: workspaceId={} status={}", workspaceId, e.getStatus());
            return WorkspaceResult.of(WorkspaceStatus.OFFLINE);
        } catch (Exception e) {
            LOG.warn("Cannot reach remote server: workspaceId={}", workspaceId, e);
            return WorkspaceResult.of(WorkspaceStatus.OFFLINE);
        }
    }

    public List<RemoteProjectResponse> allProjects(String workspaceId, boolean includeDeleted) {
        ListProjectsResponse response = projectStub.listProjects(
                ListProjectsRequest.newBuilder()
                        .setWorkspaceId(workspaceId)
                        .setIncludeDeleted(includeDeleted)
                        .build());

        return response.getProjectsList().stream()
                .map(RemoteDiscoveryClient::toRemoteProjectResponse)
                .toList();
    }

    private RemoteWorkspaceInfo toWorkspaceInfo(cafe.jeffrey.server.api.v1.WorkspaceInfo proto) {
        return new RemoteWorkspaceInfo(
                proto.getId(),
                proto.getName(),
                proto.getDescription(),
                connection.address(),
                Instant.ofEpochMilli(proto.getCreatedAt()),
                fromProtoStatus(proto.getStatus()),
                proto.getProjectCount());
    }

    private static WorkspaceResponse toWorkspaceResponse(cafe.jeffrey.server.api.v1.WorkspaceInfo proto) {
        return new WorkspaceResponse(
                proto.getId(),
                proto.getName(),
                proto.getDescription(),
                proto.getCreatedAt(),
                proto.getProjectCount(),
                fromProtoStatus(proto.getStatus()));
    }

    private static RemoteProjectResponse toRemoteProjectResponse(ProjectInfo proto) {
        return new RemoteProjectResponse(
                proto.getId(),
                proto.getOriginId().isEmpty() ? null : proto.getOriginId(),
                proto.getName(),
                proto.getLabel().isEmpty() ? null : proto.getLabel(),
                proto.getNamespace().isEmpty() ? null : proto.getNamespace(),
                proto.getCreatedAt() != 0 ? InstantUtils.formatInstant(Instant.ofEpochMilli(proto.getCreatedAt())) : null,
                proto.getWorkspaceId(),
                fromProtoRecordingStatus(proto.getStatus()),
                proto.getSessionCount(),
                proto.hasDeletedAt() ? Instant.ofEpochMilli(proto.getDeletedAt()) : null);
    }

    private static RecordingStatus fromProtoRecordingStatus(cafe.jeffrey.server.api.v1.RecordingStatus status) {
        return switch (status) {
            case RECORDING_STATUS_ACTIVE -> RecordingStatus.ACTIVE;
            case RECORDING_STATUS_FINISHED -> RecordingStatus.FINISHED;
            default -> RecordingStatus.UNKNOWN;
        };
    }

    private static WorkspaceStatus fromProtoStatus(cafe.jeffrey.server.api.v1.WorkspaceStatus status) {
        return switch (status) {
            case WORKSPACE_STATUS_AVAILABLE -> WorkspaceStatus.AVAILABLE;
            case WORKSPACE_STATUS_UNAVAILABLE -> WorkspaceStatus.UNAVAILABLE;
            case WORKSPACE_STATUS_INCOMPATIBLE -> WorkspaceStatus.UNKNOWN;
            default -> WorkspaceStatus.UNKNOWN;
        };
    }

}
