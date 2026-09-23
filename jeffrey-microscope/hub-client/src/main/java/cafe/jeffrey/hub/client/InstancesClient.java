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

import cafe.jeffrey.microscope.grpc.client.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cafe.jeffrey.hub.client.dto.InstanceDetailResponse;
import cafe.jeffrey.hub.client.dto.InstanceResponse;
import cafe.jeffrey.hub.client.dto.InstanceSessionResponse;
import cafe.jeffrey.hub.client.dto.InstanceStatsResponse;
import cafe.jeffrey.hub.api.v1.*;

import java.util.List;

public class InstancesClient {

    private static final Logger LOG = LoggerFactory.getLogger(InstancesClient.class);

    private final InstanceServiceGrpc.InstanceServiceBlockingStub stub;

    public InstancesClient(GrpcHubConnection connection) {
        this.stub = InstanceServiceGrpc.newBlockingStub(connection.getChannel());
    }

    public List<InstanceResponse> projectInstances(String projectId, boolean includeSessions) {
        ListInstancesResponse response = stub.listInstances(
                ListInstancesRequest.newBuilder()
                        .setProjectId(projectId)
                        .setIncludeSessions(includeSessions)
                        .build());

        LOG.debug("Listed instances via gRPC: projectId={} count={} include_sessions={}",
                projectId, response.getInstancesCount(), includeSessions);

        return response.getInstancesList().stream()
                .map(InstancesClient::toInstanceResponse)
                .toList();
    }

    public InstanceResponse projectInstance(String instanceId) {
        GetInstanceResponse response = stub.getInstance(
                GetInstanceRequest.newBuilder()
                        .setInstanceId(instanceId)
                        .build());

        LOG.debug("Fetched instance via gRPC: instanceId={}", instanceId);

        return toInstanceResponse(response.getInstance());
    }

    public InstanceDetailResponse instanceDetail(String instanceId) {
        GetInstanceDetailResponse response = stub.getInstanceDetail(
                GetInstanceDetailRequest.newBuilder()
                        .setInstanceId(instanceId)
                        .build());

        LOG.debug("Fetched instance detail via gRPC: instanceId={} files={} totalSize={}",
                instanceId, response.getStats().getFileCount(), response.getStats().getTotalSizeBytes());

        return new InstanceDetailResponse(
                toInstanceResponse(response.getInstance()),
                new InstanceStatsResponse(
                        response.getStats().getFileCount(),
                        response.getStats().getTotalSizeBytes()));
    }

    /**
     * The hub's metadata for one session of an instance. Carries no JFR data: the environment
     * events shown beside it are read from the session's chunk on this side, by
     * {@code SessionEnvironmentReader}, because the hub holds no JFR reader.
     */
    public InstanceSessionResponse instanceSessionDetail(String instanceId, String sessionId) {
        GetInstanceSessionDetailResponse response = stub.getInstanceSessionDetail(
                GetInstanceSessionDetailRequest.newBuilder()
                        .setInstanceId(instanceId)
                        .setSessionId(sessionId)
                        .build());

        LOG.debug("Fetched instance session detail via gRPC: instanceId={} sessionId={}",
                instanceId, sessionId);

        return toSessionResponse(response.getSession());
    }

    public List<InstanceSessionResponse> projectInstanceSessions(String instanceId) {

        ListInstanceSessionsResponse response = stub.listInstanceSessions(
                ListInstanceSessionsRequest.newBuilder()
                        .setInstanceId(instanceId)
                        .build());

        LOG.debug("Listed instance sessions via gRPC: instanceId={} count={}",
                instanceId, response.getSessionsCount());

        return response.getSessionsList().stream()
                .map(InstancesClient::toSessionResponse)
                .toList();
    }

    private static InstanceResponse toInstanceResponse(InstanceInfo proto) {
        List<InstanceSessionResponse> sessions = proto.getSessionsList().stream()
                .map(InstancesClient::toSessionResponse)
                .toList();

        return new InstanceResponse(
                proto.getId(),
                ClientProtoMappers.nullIfEmpty(proto.getInstanceName()),
                ClientProtoMappers.instanceStatus(proto.getStatus()),
                proto.getCreatedAt(),
                proto.hasFinishedAt() ? proto.getFinishedAt() : null,
                proto.hasExpiringAt() ? proto.getExpiringAt() : null,
                proto.hasExpiredAt() ? proto.getExpiredAt() : null,
                proto.getSessionCount(),
                proto.hasActiveSessionId() ? proto.getActiveSessionId() : null,
                proto.hasFinishedAt() ? proto.getFinishedAt() - proto.getCreatedAt() : null,
                sessions);
    }

    private static InstanceSessionResponse toSessionResponse(InstanceSessionInfo proto) {
        return new InstanceSessionResponse(
                proto.getId(),
                ClientProtoMappers.nullIfEmpty(proto.getRepositoryId()),
                proto.getCreatedAt(),
                proto.hasFinishedAt() ? proto.getFinishedAt() : null,
                proto.getIsActive(),
                proto.hasFinishedAt() ? proto.getFinishedAt() - proto.getCreatedAt() : null,
                proto.getFailed());
    }

}
