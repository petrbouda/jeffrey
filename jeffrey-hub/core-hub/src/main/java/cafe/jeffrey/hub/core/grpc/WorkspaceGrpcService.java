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

package cafe.jeffrey.hub.core.grpc;

import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cafe.jeffrey.hub.api.v1.*;
import cafe.jeffrey.shared.common.JeffreyVersion;
import cafe.jeffrey.hub.core.configuration.properties.DefaultWorkspaceProperties;
import cafe.jeffrey.hub.core.manager.workspace.WorkspaceManager;
import cafe.jeffrey.hub.core.manager.workspace.WorkspacesManager;
import cafe.jeffrey.hub.model.workspace.WorkspaceInfo;
import cafe.jeffrey.hub.model.workspace.WorkspaceReferenceId;

import java.util.List;

public class WorkspaceGrpcService extends WorkspaceServiceGrpc.WorkspaceServiceImplBase {

    private static final Logger LOG = LoggerFactory.getLogger(WorkspaceGrpcService.class);
    private static final int CURRENT_API_VERSION = 1;

    private final WorkspacesManager workspacesManager;
    private final DefaultWorkspaceProperties defaultWorkspaceProperties;

    public WorkspaceGrpcService(
            WorkspacesManager workspacesManager,
            DefaultWorkspaceProperties defaultWorkspaceProperties) {
        this.workspacesManager = workspacesManager;
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
            ListWorkspacesResponse.Builder response = ListWorkspacesResponse.newBuilder();
            for (WorkspaceManager workspace : workspacesManager.findAll()) {
                response.addWorkspaces(ProtoMappers.workspace(workspace.resolveInfo()));
            }

            LOG.debug("Listed workspaces via gRPC: count={}", response.getWorkspacesCount());

            return response.build();
        });
    }

    @Override
    public void getWorkspace(GetWorkspaceRequest request, StreamObserver<GetWorkspaceResponse> responseObserver) {
        GrpcUnary.respond(responseObserver, () -> {
            WorkspaceManager workspace = findWorkspace(request.getWorkspaceId());

            LOG.debug("Fetched workspace via gRPC: workspace_id={}", request.getWorkspaceId());

            return GetWorkspaceResponse.newBuilder()
                    .setWorkspace(ProtoMappers.workspace(workspace.resolveInfo()))
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
                    new WorkspacesManager.CreateWorkspaceRequest(request.getReferenceId(), request.getName()));

            LOG.info("Created workspace via gRPC: workspace_id={} reference_id={} name={}",
                    created.id(), created.referenceId(), created.name());

            // The row carries no status; the manager resolves one from the workspace's directory
            return CreateWorkspaceResponse.newBuilder()
                    .setWorkspace(ProtoMappers.workspace(findWorkspace(created.id()).resolveInfo()))
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

            LOG.info("Deleted workspace via gRPC: workspace_id={}", request.getWorkspaceId());

            return DeleteWorkspaceResponse.getDefaultInstance();
        });
    }

    private WorkspaceManager findWorkspace(String workspaceId) {
        return workspacesManager.findById(workspaceId)
                .orElseThrow(() -> GrpcExceptions.notFound("Workspace not found: " + workspaceId));
    }

}
