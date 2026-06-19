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

package cafe.jeffrey.hub.stub.grpc;

import cafe.jeffrey.hub.api.v1.GetApiInfoRequest;
import cafe.jeffrey.hub.api.v1.GetApiInfoResponse;
import cafe.jeffrey.hub.api.v1.GetWorkspaceRequest;
import cafe.jeffrey.hub.api.v1.GetWorkspaceResponse;
import cafe.jeffrey.hub.api.v1.ListWorkspacesRequest;
import cafe.jeffrey.hub.api.v1.ListWorkspacesResponse;
import cafe.jeffrey.hub.api.v1.WorkspaceInfo;
import cafe.jeffrey.hub.api.v1.WorkspaceServiceGrpc;
import cafe.jeffrey.hub.api.v1.WorkspaceStatus;
import cafe.jeffrey.hub.stub.data.StubDataset;
import io.grpc.stub.StreamObserver;

/**
 * Stub {@code WorkspaceService} backed by the in-memory dataset. Read RPCs return
 * fake workspaces; mutating RPCs (Create/Delete) are left to the generated
 * {@code UNIMPLEMENTED} default.
 */
public class StubWorkspaceService extends WorkspaceServiceGrpc.WorkspaceServiceImplBase {

    private static final String STUB_VERSION = "stub-1.0.0";
    private static final int CURRENT_API_VERSION = 1;

    private final StubDataset dataset;

    public StubWorkspaceService(StubDataset dataset) {
        this.dataset = dataset;
    }

    @Override
    public void getApiInfo(GetApiInfoRequest request, StreamObserver<GetApiInfoResponse> responseObserver) {
        responseObserver.onNext(GetApiInfoResponse.newBuilder()
                .setVersion(STUB_VERSION)
                .setApiVersion(CURRENT_API_VERSION)
                .build());
        responseObserver.onCompleted();
    }

    @Override
    public void listWorkspaces(ListWorkspacesRequest request, StreamObserver<ListWorkspacesResponse> responseObserver) {
        ListWorkspacesResponse.Builder builder = ListWorkspacesResponse.newBuilder();
        for (StubDataset.Workspace workspace : dataset.workspaces()) {
            builder.addWorkspaces(toProto(workspace));
        }
        responseObserver.onNext(builder.build());
        responseObserver.onCompleted();
    }

    @Override
    public void getWorkspace(GetWorkspaceRequest request, StreamObserver<GetWorkspaceResponse> responseObserver) {
        dataset.workspace(request.getWorkspaceId())
                .ifPresentOrElse(
                        workspace -> {
                            responseObserver.onNext(GetWorkspaceResponse.newBuilder()
                                    .setWorkspace(toProto(workspace))
                                    .build());
                            responseObserver.onCompleted();
                        },
                        () -> responseObserver.onError(
                                StubGrpcExceptions.notFound("Workspace not found: " + request.getWorkspaceId())));
    }

    static WorkspaceInfo toProto(StubDataset.Workspace workspace) {
        return WorkspaceInfo.newBuilder()
                .setId(workspace.id())
                .setName(workspace.name())
                .setReferenceId(workspace.referenceId())
                .setCreatedAt(workspace.createdAt().toEpochMilli())
                .setProjectCount(workspace.projects().size())
                .setStatus(WorkspaceStatus.WORKSPACE_STATUS_AVAILABLE)
                .build();
    }
}
