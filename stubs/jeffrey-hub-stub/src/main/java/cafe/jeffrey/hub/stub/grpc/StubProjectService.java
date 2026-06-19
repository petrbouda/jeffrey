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

import cafe.jeffrey.hub.api.v1.GetProjectRequest;
import cafe.jeffrey.hub.api.v1.GetProjectResponse;
import cafe.jeffrey.hub.api.v1.ListProjectsRequest;
import cafe.jeffrey.hub.api.v1.ListProjectsResponse;
import cafe.jeffrey.hub.api.v1.ProjectServiceGrpc;
import cafe.jeffrey.hub.stub.data.StubDataset;
import io.grpc.stub.StreamObserver;

/**
 * Stub {@code ProjectService} backed by the in-memory dataset. Mutating RPCs
 * (Delete/Restore) fall through to the generated {@code UNIMPLEMENTED} default.
 */
public class StubProjectService extends ProjectServiceGrpc.ProjectServiceImplBase {

    private final StubDataset dataset;

    public StubProjectService(StubDataset dataset) {
        this.dataset = dataset;
    }

    @Override
    public void listProjects(ListProjectsRequest request, StreamObserver<ListProjectsResponse> responseObserver) {
        ListProjectsResponse.Builder builder = ListProjectsResponse.newBuilder();
        for (StubDataset.Project project : dataset.projects(request.getWorkspaceId(), request.getIncludeDeleted())) {
            builder.addProjects(StubProtoMappers.projectInfo(project));
        }
        responseObserver.onNext(builder.build());
        responseObserver.onCompleted();
    }

    @Override
    public void getProject(GetProjectRequest request, StreamObserver<GetProjectResponse> responseObserver) {
        dataset.project(request.getProjectId())
                .ifPresentOrElse(
                        project -> {
                            responseObserver.onNext(GetProjectResponse.newBuilder()
                                    .setProject(StubProtoMappers.projectInfo(project))
                                    .build());
                            responseObserver.onCompleted();
                        },
                        () -> responseObserver.onError(
                                StubGrpcExceptions.notFound("Project not found: " + request.getProjectId())));
    }
}
