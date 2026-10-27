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

import cafe.jeffrey.hub.api.v1.GetInstanceRequest;
import cafe.jeffrey.hub.api.v1.GetInstanceResponse;
import cafe.jeffrey.hub.api.v1.InstanceServiceGrpc;
import cafe.jeffrey.hub.api.v1.ListInstancesRequest;
import cafe.jeffrey.hub.api.v1.ListInstancesResponse;
import cafe.jeffrey.hub.stub.data.StubDataset;
import io.grpc.stub.StreamObserver;

/**
 * Stub {@code InstanceService} backed by the in-memory dataset: a project's instances, and one
 * instance with its sessions (each without its files).
 */
public class StubInstanceService extends InstanceServiceGrpc.InstanceServiceImplBase {

    private final StubDataset dataset;

    public StubInstanceService(StubDataset dataset) {
        this.dataset = dataset;
    }

    @Override
    public void listInstances(ListInstancesRequest request, StreamObserver<ListInstancesResponse> responseObserver) {
        ListInstancesResponse.Builder builder = ListInstancesResponse.newBuilder();
        dataset.project(request.getWorkspaceId(), request.getProjectId())
                .ifPresent(project -> project.instances()
                        .forEach(instance -> builder.addInstances(
                                StubProtoMappers.instanceInfo(instance))));
        responseObserver.onNext(builder.build());
        responseObserver.onCompleted();
    }

    @Override
    public void getInstance(GetInstanceRequest request, StreamObserver<GetInstanceResponse> responseObserver) {
        dataset.project(request.getWorkspaceId(), request.getProjectId())
                .flatMap(project -> project.instances().stream()
                        .filter(instance -> instance.id().equals(request.getInstanceId()))
                        .findFirst())
                .ifPresentOrElse(
                        instance -> {
                            GetInstanceResponse.Builder builder = GetInstanceResponse.newBuilder()
                                    .setInstance(StubProtoMappers.instanceInfo(instance));
                            instance.sessions().reversed()
                                    .forEach(session -> builder.addSessions(StubProtoMappers.sessionHeader(session)));
                            responseObserver.onNext(builder.build());
                            responseObserver.onCompleted();
                        },
                        () -> responseObserver.onError(
                                StubGrpcExceptions.notFound("Instance not found: " + request.getInstanceId())));
    }
}
