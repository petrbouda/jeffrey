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
import pbouda.jeffrey.server.api.v1.*;
import pbouda.jeffrey.shared.common.JeffreyVersion;
import pbouda.jeffrey.server.core.manager.workspace.WorkspaceManager;
import pbouda.jeffrey.server.core.manager.workspace.WorkspacesManager;
import pbouda.jeffrey.shared.common.model.workspace.WorkspaceInfo;
import pbouda.jeffrey.shared.common.model.workspace.WorkspaceStatus;

import java.time.Clock;
import java.util.List;

public class WorkspaceGrpcService extends WorkspaceServiceGrpc.WorkspaceServiceImplBase {

    private static final Logger LOG = LoggerFactory.getLogger(WorkspaceGrpcService.class);
    private static final int CURRENT_API_VERSION = 1;

    private final WorkspacesManager workspacesManager;
    private final Clock clock;

    public WorkspaceGrpcService(WorkspacesManager workspacesManager, Clock clock) {
        this.workspacesManager = workspacesManager;
        this.clock = clock;
    }

    @Override
    public void getApiInfo(GetApiInfoRequest request, StreamObserver<GetApiInfoResponse> responseObserver) {
        try {
            GetApiInfoResponse response = GetApiInfoResponse.newBuilder()
                    .setVersion(JeffreyVersion.resolveJeffreyVersion())
                    .setApiVersion(CURRENT_API_VERSION)
                    .build();
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (Exception e) {
            LOG.error("Failed to get API info", e);
            responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
        }
    }

    @Override
    public void listWorkspaces(ListWorkspacesRequest request, StreamObserver<ListWorkspacesResponse> responseObserver) {
        try {
            List<pbouda.jeffrey.server.api.v1.WorkspaceInfo> workspaces = workspacesManager.findAll().stream()
                    .map(WorkspaceManager::resolveInfo)
                    .map(WorkspaceGrpcService::toProto)
                    .toList();

            LOG.debug("Listed workspaces via gRPC: count={}", workspaces.size());

            ListWorkspacesResponse response = ListWorkspacesResponse.newBuilder()
                    .addAllWorkspaces(workspaces)
                    .build();
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (Exception e) {
            LOG.error("Failed to list workspaces", e);
            responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
        }
    }

    @Override
    public void getWorkspace(GetWorkspaceRequest request, StreamObserver<GetWorkspaceResponse> responseObserver) {
        try {
            WorkspaceManager workspace = workspacesManager.findById(request.getWorkspaceId())
                    .orElseThrow(() -> Status.NOT_FOUND
                            .withDescription("Workspace not found: " + request.getWorkspaceId())
                            .asRuntimeException());

            LOG.debug("Fetched workspace via gRPC: workspaceId={}", request.getWorkspaceId());

            GetWorkspaceResponse response = GetWorkspaceResponse.newBuilder()
                    .setWorkspace(toProto(workspace.resolveInfo()))
                    .build();
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (io.grpc.StatusRuntimeException e) {
            responseObserver.onError(e);
        } catch (Exception e) {
            LOG.error("Failed to get workspace: workspaceId={}", request.getWorkspaceId(), e);
            responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
        }
    }

    @Override
    public void blockWorkspace(BlockWorkspaceRequest request, StreamObserver<BlockWorkspaceResponse> responseObserver) {
        try {
            WorkspaceManager workspace = findWorkspace(request.getWorkspaceId());

            switch (request.getMode()) {
                case BLOCK -> workspace.block();
                case BLOCK_AND_DELETE_DATA -> workspace.blockAndDeleteData();
                case DELETE -> workspace.delete();
                default -> throw Status.INVALID_ARGUMENT
                        .withDescription("Unknown block mode: " + request.getMode())
                        .asRuntimeException();
            }

            LOG.info("Blocked workspace via gRPC: workspaceId={} mode={}", request.getWorkspaceId(), request.getMode());

            responseObserver.onNext(BlockWorkspaceResponse.getDefaultInstance());
            responseObserver.onCompleted();
        } catch (io.grpc.StatusRuntimeException e) {
            responseObserver.onError(e);
        } catch (Exception e) {
            LOG.error("Failed to block workspace: workspaceId={}", request.getWorkspaceId(), e);
            responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
        }
    }

    @Override
    public void unblockWorkspace(UnblockWorkspaceRequest request, StreamObserver<UnblockWorkspaceResponse> responseObserver) {
        try {
            WorkspaceManager workspace = findWorkspace(request.getWorkspaceId());
            workspace.unblock();

            LOG.info("Unblocked workspace via gRPC: workspaceId={}", request.getWorkspaceId());

            responseObserver.onNext(UnblockWorkspaceResponse.getDefaultInstance());
            responseObserver.onCompleted();
        } catch (io.grpc.StatusRuntimeException e) {
            responseObserver.onError(e);
        } catch (Exception e) {
            LOG.error("Failed to unblock workspace: workspaceId={}", request.getWorkspaceId(), e);
            responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
        }
    }

    private WorkspaceManager findWorkspace(String workspaceId) {
        return workspacesManager.findById(workspaceId)
                .orElseThrow(() -> Status.NOT_FOUND
                        .withDescription("Workspace not found: " + workspaceId)
                        .asRuntimeException());
    }

    static pbouda.jeffrey.server.api.v1.WorkspaceInfo toProto(WorkspaceInfo info) {
        var builder = pbouda.jeffrey.server.api.v1.WorkspaceInfo.newBuilder()
                .setId(info.id())
                .setName(info.name())
                .setDescription(info.description() != null ? info.description() : "")
                .setCreatedAt(info.createdAt().toEpochMilli())
                .setProjectCount(info.projectCount())
                .setStatus(toProtoStatus(info.status()))
                .setIsBlocked(info.blocked());

        return builder.build();
    }

    private static pbouda.jeffrey.server.api.v1.WorkspaceStatus toProtoStatus(WorkspaceStatus status) {
        return switch (status) {
            case AVAILABLE -> pbouda.jeffrey.server.api.v1.WorkspaceStatus.WORKSPACE_STATUS_AVAILABLE;
            case UNAVAILABLE -> pbouda.jeffrey.server.api.v1.WorkspaceStatus.WORKSPACE_STATUS_UNAVAILABLE;
            case OFFLINE, UNKNOWN -> pbouda.jeffrey.server.api.v1.WorkspaceStatus.WORKSPACE_STATUS_INCOMPATIBLE;
        };
    }
}
