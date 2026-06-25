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
import cafe.jeffrey.shared.common.JeffreyVersion;
import cafe.jeffrey.hub.core.configuration.properties.DefaultWorkspaceProperties;
import cafe.jeffrey.hub.core.manager.workspace.WorkspaceManager;
import cafe.jeffrey.hub.core.manager.workspace.WorkspacesManager;
import cafe.jeffrey.shared.common.model.workspace.WorkspaceInfo;
import cafe.jeffrey.shared.common.model.workspace.WorkspaceReferenceId;

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
        GrpcUnary.respond(responseObserver, () -> GetApiInfoResponse.newBuilder()
                .setVersion(JeffreyVersion.resolveJeffreyVersion())
                .setApiVersion(CURRENT_API_VERSION)
                .build());
    }

    @Override
    public void listWorkspaces(ListWorkspacesRequest request, StreamObserver<ListWorkspacesResponse> responseObserver) {
        GrpcUnary.respond(responseObserver, () -> {
            List<cafe.jeffrey.hub.api.v1.WorkspaceInfo> workspaces = workspacesManager.findAll().stream()
                    .map(WorkspaceManager::resolveInfo)
                    .map(WorkspaceGrpcService::toProto)
                    .toList();

            LOG.debug("Listed workspaces via gRPC: count={}", workspaces.size());

            return ListWorkspacesResponse.newBuilder()
                    .addAllWorkspaces(workspaces)
                    .build();
        });
    }

    @Override
    public void getWorkspace(GetWorkspaceRequest request, StreamObserver<GetWorkspaceResponse> responseObserver) {
        GrpcUnary.respond(responseObserver, () -> {
            WorkspaceManager workspace = findWorkspace(request.getWorkspaceId());

            LOG.debug("Fetched workspace via gRPC: workspaceId={}", request.getWorkspaceId());

            return GetWorkspaceResponse.newBuilder()
                    .setWorkspace(toProto(workspace.resolveInfo()))
                    .build();
        });
    }

    @Override
    public void createWorkspace(
            CreateWorkspaceRequest request,
            StreamObserver<CreateWorkspaceResponse> responseObserver) {
        GrpcUnary.respond(responseObserver, () -> {
            if (WorkspaceReferenceId.isSystem(request.getReferenceId())) {
                throw GrpcExceptions.invalidArgument(
                        "Reference IDs starting with '$' are reserved for system workspaces.");
            }

            WorkspaceInfo created = workspacesManager.create(
                    WorkspacesManager.CreateWorkspaceRequest.builder()
                            .referenceId(request.getReferenceId())
                            .name(request.getName())
                            .build());

            LOG.info("Created workspace via gRPC: workspace_id={} reference_id={} name={}",
                    created.id(), created.referenceId(), created.name());

            return CreateWorkspaceResponse.newBuilder()
                    .setWorkspace(toProto(created))
                    .build();
        });
    }

    @Override
    public void deleteWorkspace(DeleteWorkspaceRequest request, StreamObserver<DeleteWorkspaceResponse> responseObserver) {
        GrpcUnary.respond(responseObserver, () -> {
            WorkspaceManager workspace = findWorkspace(request.getWorkspaceId());

            String defaultRefId = defaultWorkspaceProperties.getReferenceId();
            String workspaceRefId = workspace.resolveInfo().referenceId();
            if (defaultRefId.equals(workspaceRefId)) {
                throw GrpcExceptions.failedPrecondition(
                        "Cannot delete the default workspace (" + defaultRefId + "). "
                                + "Reconfigure jeffrey.hub.default-workspace.reference-id "
                                + "and restart to change it.");
            }

            workspace.delete();

            LOG.info("Deleted workspace via gRPC: workspaceId={}", request.getWorkspaceId());

            return DeleteWorkspaceResponse.getDefaultInstance();
        });
    }

    private WorkspaceManager findWorkspace(String workspaceId) {
        return workspacesManager.findById(workspaceId)
                .orElseThrow(() -> GrpcExceptions.notFound("Workspace not found: " + workspaceId));
    }

    static cafe.jeffrey.hub.api.v1.WorkspaceInfo toProto(WorkspaceInfo info) {
        var builder = cafe.jeffrey.hub.api.v1.WorkspaceInfo.newBuilder()
                .setId(info.id())
                .setName(info.name())
                .setReferenceId(ProtoMappers.orEmpty(info.referenceId()))
                .setCreatedAt(info.createdAt().toEpochMilli())
                .setProjectCount(info.projectCount())
                .setStatus(ProtoMappers.workspaceStatus(info.status()));

        return builder.build();
    }
}
