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
import cafe.jeffrey.shared.common.JeffreyVersion;
import cafe.jeffrey.server.core.configuration.properties.DefaultWorkspaceProperties;
import cafe.jeffrey.server.core.manager.workspace.WorkspaceAlreadyExistsException;
import cafe.jeffrey.server.core.manager.workspace.WorkspaceManager;
import cafe.jeffrey.server.core.manager.workspace.WorkspacesManager;
import cafe.jeffrey.shared.common.model.workspace.WorkspaceInfo;
import cafe.jeffrey.shared.common.model.workspace.WorkspaceReferenceId;
import cafe.jeffrey.shared.common.model.workspace.WorkspaceStatus;

import java.time.Clock;
import java.util.List;

public class WorkspaceGrpcService extends WorkspaceServiceGrpc.WorkspaceServiceImplBase {

    private static final Logger LOG = LoggerFactory.getLogger(WorkspaceGrpcService.class);
    private static final int CURRENT_API_VERSION = 1;

    private final WorkspacesManager workspacesManager;
    private final Clock clock;
    private final DefaultWorkspaceProperties defaultWorkspaceProperties;

    public WorkspaceGrpcService(
            WorkspacesManager workspacesManager,
            Clock clock,
            DefaultWorkspaceProperties defaultWorkspaceProperties) {
        this.workspacesManager = workspacesManager;
        this.clock = clock;
        this.defaultWorkspaceProperties = defaultWorkspaceProperties;
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
            List<cafe.jeffrey.server.api.v1.WorkspaceInfo> workspaces = workspacesManager.findAll().stream()
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
    public void createWorkspace(
            CreateWorkspaceRequest request,
            StreamObserver<CreateWorkspaceResponse> responseObserver) {
        try {
            if (WorkspaceReferenceId.isSystem(request.getReferenceId())) {
                LOG.debug("Rejecting createWorkspace with system-reserved reference_id: reference_id={}",
                        request.getReferenceId());
                responseObserver.onError(Status.INVALID_ARGUMENT
                        .withDescription("Reference IDs starting with '$' are reserved for system workspaces.")
                        .asRuntimeException());
                return;
            }

            WorkspaceInfo created = workspacesManager.create(
                    WorkspacesManager.CreateWorkspaceRequest.builder()
                            .referenceId(request.getReferenceId())
                            .name(request.getName())
                            .build());

            LOG.info("Created workspace via gRPC: workspace_id={} reference_id={} name={}",
                    created.id(), created.referenceId(), created.name());

            CreateWorkspaceResponse response = CreateWorkspaceResponse.newBuilder()
                    .setWorkspace(toProto(created))
                    .build();
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (WorkspaceAlreadyExistsException e) {
            LOG.debug("Workspace already exists: reference_id={} name={}",
                    request.getReferenceId(), request.getName());
            responseObserver.onError(
                    Status.ALREADY_EXISTS.withDescription(e.getMessage()).asRuntimeException());
        } catch (IllegalArgumentException e) {
            LOG.debug("Invalid workspace creation request: reference_id={} name={} reason={}",
                    request.getReferenceId(), request.getName(), e.getMessage());
            responseObserver.onError(
                    Status.INVALID_ARGUMENT.withDescription(e.getMessage()).asRuntimeException());
        } catch (Exception e) {
            LOG.error("Failed to create workspace: reference_id={} name={}",
                    request.getReferenceId(), request.getName(), e);
            responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
        }
    }

    @Override
    public void deleteWorkspace(DeleteWorkspaceRequest request, StreamObserver<DeleteWorkspaceResponse> responseObserver) {
        try {
            WorkspaceManager workspace = findWorkspace(request.getWorkspaceId());

            String defaultRefId = defaultWorkspaceProperties.getReferenceId();
            String workspaceRefId = workspace.resolveInfo().referenceId();
            if (defaultRefId.equals(workspaceRefId)) {
                LOG.debug("Rejecting delete of default workspace: workspace_id={} reference_id={}",
                        request.getWorkspaceId(), workspaceRefId);
                responseObserver.onError(Status.FAILED_PRECONDITION
                        .withDescription(
                                "Cannot delete the default workspace (" + defaultRefId + "). "
                                        + "Reconfigure jeffrey.server.default-workspace.reference-id "
                                        + "and restart to change it.")
                        .asRuntimeException());
                return;
            }

            workspace.delete();

            LOG.info("Deleted workspace via gRPC: workspaceId={}", request.getWorkspaceId());

            responseObserver.onNext(DeleteWorkspaceResponse.getDefaultInstance());
            responseObserver.onCompleted();
        } catch (io.grpc.StatusRuntimeException e) {
            responseObserver.onError(e);
        } catch (Exception e) {
            LOG.error("Failed to delete workspace: workspaceId={}", request.getWorkspaceId(), e);
            responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
        }
    }

    private WorkspaceManager findWorkspace(String workspaceId) {
        return workspacesManager.findById(workspaceId)
                .orElseThrow(() -> Status.NOT_FOUND
                        .withDescription("Workspace not found: " + workspaceId)
                        .asRuntimeException());
    }

    static cafe.jeffrey.server.api.v1.WorkspaceInfo toProto(WorkspaceInfo info) {
        var builder = cafe.jeffrey.server.api.v1.WorkspaceInfo.newBuilder()
                .setId(info.id())
                .setName(info.name())
                .setReferenceId(info.referenceId() != null ? info.referenceId() : "")
                .setCreatedAt(info.createdAt().toEpochMilli())
                .setProjectCount(info.projectCount())
                .setStatus(toProtoStatus(info.status()));

        return builder.build();
    }

    private static cafe.jeffrey.server.api.v1.WorkspaceStatus toProtoStatus(WorkspaceStatus status) {
        return switch (status) {
            case AVAILABLE -> cafe.jeffrey.server.api.v1.WorkspaceStatus.WORKSPACE_STATUS_AVAILABLE;
            case UNAVAILABLE -> cafe.jeffrey.server.api.v1.WorkspaceStatus.WORKSPACE_STATUS_UNAVAILABLE;
            case OFFLINE, UNKNOWN -> cafe.jeffrey.server.api.v1.WorkspaceStatus.WORKSPACE_STATUS_INCOMPATIBLE;
        };
    }
}
