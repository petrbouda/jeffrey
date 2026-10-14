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

import cafe.jeffrey.hub.api.v1.InstanceServiceGrpc;
import cafe.jeffrey.hub.api.v1.ListInstancesRequest;
import cafe.jeffrey.hub.api.v1.ListInstancesResponse;
import cafe.jeffrey.hub.stub.data.StubDataset;
import io.grpc.stub.StreamObserver;

/**
 * Stub {@code InstanceService} backed by the in-memory dataset: the instance lifecycle only.
 */
public class StubInstanceService extends InstanceServiceGrpc.InstanceServiceImplBase {

    private final StubDataset dataset;

    public StubInstanceService(StubDataset dataset) {
        this.dataset = dataset;
    }

    @Override
    public void listInstances(ListInstancesRequest request, StreamObserver<ListInstancesResponse> responseObserver) {
        ListInstancesResponse.Builder builder = ListInstancesResponse.newBuilder();
        dataset.project(request.getProjectId())
                .ifPresent(project -> project.instances()
                        .forEach(instance -> builder.addInstances(
                                StubProtoMappers.instanceInfo(instance))));
        responseObserver.onNext(builder.build());
        responseObserver.onCompleted();
    }
}
