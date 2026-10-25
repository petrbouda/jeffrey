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

package cafe.jeffrey.microscope.remote.client;

import cafe.jeffrey.hub.api.v1.GetInstanceRequest;
import cafe.jeffrey.hub.api.v1.GetInstanceResponse;
import cafe.jeffrey.hub.api.v1.InstanceInfo;
import cafe.jeffrey.hub.api.v1.InstanceServiceGrpc;
import cafe.jeffrey.hub.api.v1.ListInstancesRequest;
import cafe.jeffrey.hub.api.v1.ListInstancesResponse;
import cafe.jeffrey.microscope.remote.client.dto.InstanceResponse;
import cafe.jeffrey.microscope.grpc.client.GrpcHubConnection;
import io.grpc.StatusRuntimeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;

/**
 * A project's instances, and one instance with its sessions — one level of the tree per call.
 */
public class GrpcInstancesClient implements InstancesClient {

    private static final Logger LOG = LoggerFactory.getLogger(GrpcInstancesClient.class);

    private final InstanceServiceGrpc.InstanceServiceBlockingStub stub;

    public GrpcInstancesClient(GrpcHubConnection connection) {
        this.stub = InstanceServiceGrpc.newBlockingStub(connection.getChannel());
    }

    @Override
    public List<InstanceResponse> projectInstances(ProjectScope scope) {
        ListInstancesResponse response = stub.listInstances(
                ListInstancesRequest.newBuilder()
                        .setWorkspaceId(scope.workspaceId())
                        .setProjectId(scope.projectId())
                        .build());

        LOG.debug("Listed instances via gRPC: workspaceId={} projectId={} count={}",
                scope.workspaceId(), scope.projectId(), response.getInstancesCount());

        return response.getInstancesList().stream()
                .map(GrpcInstancesClient::toInstanceResponse)
                .toList();
    }

    /**
     * One instance with its sessions, newest first, each without its files; empty when the
     * project holds no instance by that id.
     */
    @Override
    public Optional<InstanceRead> instance(ProjectScope scope, String instanceId) {
        try {
            GetInstanceResponse response = stub.getInstance(
                    GetInstanceRequest.newBuilder()
                            .setWorkspaceId(scope.workspaceId())
                            .setProjectId(scope.projectId())
                            .setInstanceId(instanceId)
                            .build());

            LOG.debug("Fetched instance via gRPC: workspaceId={} projectId={} instanceId={} sessions={}",
                    scope.workspaceId(), scope.projectId(), instanceId, response.getSessionsCount());

            return Optional.of(new InstanceRead(
                    toInstanceResponse(response.getInstance()),
                    response.getSessionsList().stream()
                            .map(ClientProtoMappers::session)
                            .toList()));
        } catch (StatusRuntimeException e) {
            if (GrpcClientErrors.isNotFound(e)) {
                return Optional.empty();
            }
            throw e;
        }
    }

    private static InstanceResponse toInstanceResponse(InstanceInfo proto) {
        return InstanceResponse.summary(
                proto.getId(),
                ClientProtoMappers.instanceStatus(proto.getStatus()),
                proto.getCreatedAt(),
                proto.hasFinishedAt() ? proto.getFinishedAt() : null,
                proto.getSessionCount());
    }
}
