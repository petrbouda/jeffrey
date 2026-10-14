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
import cafe.jeffrey.hub.api.v1.InstanceInfo;
import cafe.jeffrey.hub.api.v1.InstanceServiceGrpc;
import cafe.jeffrey.hub.api.v1.ListInstancesRequest;
import cafe.jeffrey.hub.api.v1.ListInstancesResponse;
import cafe.jeffrey.hub.persistence.api.HubPlatformRepositories;

import java.util.List;

/**
 * An instance's lifecycle and nothing else. Its sessions, their files and sizes are listed by
 * {@link RepositoryGrpcService}, narrowed to the instance.
 */
public class InstanceGrpcService extends InstanceServiceGrpc.InstanceServiceImplBase {

    private static final Logger LOG = LoggerFactory.getLogger(InstanceGrpcService.class);

    private final HubPlatformRepositories platformRepositories;
    private final GrpcLookups lookups;

    public InstanceGrpcService(HubPlatformRepositories platformRepositories, GrpcLookups lookups) {
        this.platformRepositories = platformRepositories;
        this.lookups = lookups;
    }

    @Override
    public void listInstances(ListInstancesRequest request, StreamObserver<ListInstancesResponse> responseObserver) {
        GrpcUnary.respond(responseObserver, () -> {
            String projectId = lookups.projectInfo(request.getProjectId()).id();
            List<InstanceInfo> instances = platformRepositories.newProjectInstanceRepository(projectId).findAll().stream()
                    .map(ProtoMappers::instance)
                    .toList();

            LOG.debug("Listed instances via gRPC: project_id={} count={}", projectId, instances.size());

            return ListInstancesResponse.newBuilder()
                    .addAllInstances(instances)
                    .build();
        });
    }
}
