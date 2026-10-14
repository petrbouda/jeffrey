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

package cafe.jeffrey.hub.client;

import cafe.jeffrey.hub.api.v1.InstanceInfo;
import cafe.jeffrey.hub.api.v1.InstanceServiceGrpc;
import cafe.jeffrey.hub.api.v1.ListInstancesRequest;
import cafe.jeffrey.hub.api.v1.ListInstancesResponse;
import cafe.jeffrey.hub.client.dto.InstanceResponse;
import cafe.jeffrey.microscope.grpc.client.GrpcHubConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * An instance's lifecycle as the hub reports it. Its sessions come from {@link RepositoryClient},
 * narrowed to the instance, so every instance here arrives without them.
 */
public class InstancesClient {

    private static final Logger LOG = LoggerFactory.getLogger(InstancesClient.class);

    private final InstanceServiceGrpc.InstanceServiceBlockingStub stub;

    public InstancesClient(GrpcHubConnection connection) {
        this.stub = InstanceServiceGrpc.newBlockingStub(connection.getChannel());
    }

    public List<InstanceResponse> projectInstances(String projectId) {
        ListInstancesResponse response = stub.listInstances(
                ListInstancesRequest.newBuilder()
                        .setProjectId(projectId)
                        .build());

        LOG.debug("Listed instances via gRPC: projectId={} count={}", projectId, response.getInstancesCount());

        return response.getInstancesList().stream()
                .map(InstancesClient::toInstanceResponse)
                .toList();
    }

    private static InstanceResponse toInstanceResponse(InstanceInfo proto) {
        return new InstanceResponse(
                proto.getId(),
                // The hub names an instance by its id
                proto.getId(),
                ClientProtoMappers.instanceStatus(proto.getStatus()),
                proto.getCreatedAt(),
                proto.hasFinishedAt() ? proto.getFinishedAt() : null,
                proto.hasExpiringAt() ? proto.getExpiringAt() : null,
                proto.hasExpiredAt() ? proto.getExpiredAt() : null,
                proto.getSessionCount(),
                null,
                proto.hasFinishedAt() ? proto.getFinishedAt() - proto.getCreatedAt() : null,
                List.of());
    }
}
