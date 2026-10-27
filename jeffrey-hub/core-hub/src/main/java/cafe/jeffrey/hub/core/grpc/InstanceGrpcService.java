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
import cafe.jeffrey.hub.api.v1.GetInstanceRequest;
import cafe.jeffrey.hub.api.v1.GetInstanceResponse;
import cafe.jeffrey.hub.api.v1.InstanceInfo;
import cafe.jeffrey.hub.api.v1.InstanceServiceGrpc;
import cafe.jeffrey.hub.api.v1.ListInstancesRequest;
import cafe.jeffrey.hub.api.v1.ListInstancesResponse;
import cafe.jeffrey.hub.core.project.repository.InstanceSessions;

import java.util.List;

/**
 * A project's instances, and one instance with its sessions — each session with its file count
 * and size, never its files, which only {@link RepositoryGrpcService#getSession} sends.
 */
public class InstanceGrpcService extends InstanceServiceGrpc.InstanceServiceImplBase {

    private static final Logger LOG = LoggerFactory.getLogger(InstanceGrpcService.class);

    private final GrpcLookups lookups;

    public InstanceGrpcService(GrpcLookups lookups) {
        this.lookups = lookups;
    }

    @Override
    public void listInstances(ListInstancesRequest request, StreamObserver<ListInstancesResponse> responseObserver) {
        GrpcUnary.respond(responseObserver, () -> {
            List<InstanceInfo> instances = lookups.repositoryManager(request.getWorkspaceId(), request.getProjectId())
                    .instances().stream()
                    .map(ProtoMappers::instance)
                    .toList();
            LOG.debug("Listed instances via gRPC: workspace_id={} project_id={} count={}",
                    request.getWorkspaceId(), request.getProjectId(), instances.size());
            return ListInstancesResponse.newBuilder()
                    .addAllInstances(instances)
                    .build();
        });
    }

    @Override
    public void getInstance(GetInstanceRequest request, StreamObserver<GetInstanceResponse> responseObserver) {
        GrpcUnary.respond(responseObserver, () -> {
            InstanceSessions found = lookups.repositoryManager(request.getWorkspaceId(), request.getProjectId())
                    .instance(request.getInstanceId())
                    .orElseThrow(() -> GrpcExceptions.notFound("Instance not found: " + request.getInstanceId()));

            LOG.debug("Fetched instance via gRPC: workspace_id={} project_id={} instance_id={} sessions={}",
                    request.getWorkspaceId(), request.getProjectId(), request.getInstanceId(), found.sessions().size());

            return GetInstanceResponse.newBuilder()
                    .setInstance(ProtoMappers.instance(found.instance()))
                    .addAllSessions(found.sessions().stream().map(ProtoMappers::sessionHeader).toList())
                    .build();
        });
    }
}
