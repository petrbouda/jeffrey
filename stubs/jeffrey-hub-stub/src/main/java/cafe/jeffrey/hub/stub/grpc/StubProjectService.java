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
