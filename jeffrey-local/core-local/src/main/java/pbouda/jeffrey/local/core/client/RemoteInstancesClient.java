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

package pbouda.jeffrey.local.core.client;

import pbouda.jeffrey.local.core.client.grpc.*;

import tools.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pbouda.jeffrey.local.core.resources.response.InstanceDetailResponse;
import pbouda.jeffrey.local.core.resources.response.InstanceResponse;
import pbouda.jeffrey.local.core.resources.response.InstanceSessionDetailResponse;
import pbouda.jeffrey.local.core.resources.response.InstanceSessionResponse;
import pbouda.jeffrey.local.core.resources.response.InstanceStatsResponse;
import pbouda.jeffrey.server.api.v1.*;
import pbouda.jeffrey.shared.common.Json;

import java.util.List;

public class RemoteInstancesClient {

    private static final Logger LOG = LoggerFactory.getLogger(RemoteInstancesClient.class);

    private final InstanceServiceGrpc.InstanceServiceBlockingStub stub;

    public RemoteInstancesClient(GrpcServerConnection connection) {
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
                .map(RemoteInstancesClient::toInstanceResponse)
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

    public InstanceSessionDetailResponse instanceSessionDetail(String instanceId, String sessionId) {
        GetInstanceSessionDetailResponse response = stub.getInstanceSessionDetail(
                GetInstanceSessionDetailRequest.newBuilder()
                        .setInstanceId(instanceId)
                        .setSessionId(sessionId)
                        .build());

        String json = response.getEnvironmentJsonFields();
        JsonNode env = (json == null || json.isBlank())
                ? null
                : Json.readTree(json);

        LOG.debug("Fetched instance session detail via gRPC: instanceId={} sessionId={} envTypes={}",
                instanceId, sessionId, env == null ? 0 : env.size());

        return new InstanceSessionDetailResponse(toSessionResponse(response.getSession()), env);
    }

    public List<InstanceSessionResponse> projectInstanceSessions(String instanceId) {

        ListInstanceSessionsResponse response = stub.listInstanceSessions(
                ListInstanceSessionsRequest.newBuilder()
                        .setInstanceId(instanceId)
                        .build());

        LOG.debug("Listed instance sessions via gRPC: instanceId={} count={}",
                instanceId, response.getSessionsCount());

        return response.getSessionsList().stream()
                .map(RemoteInstancesClient::toSessionResponse)
                .toList();
    }

    private static InstanceResponse toInstanceResponse(InstanceInfo proto) {
        List<InstanceSessionResponse> sessions = proto.getSessionsList().stream()
                .map(RemoteInstancesClient::toSessionResponse)
                .toList();

        return new InstanceResponse(
                proto.getId(),
                proto.getInstanceName().isEmpty() ? null : proto.getInstanceName(),
                fromProtoInstanceStatus(proto.getStatus()),
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
                proto.getRepositoryId().isEmpty() ? null : proto.getRepositoryId(),
                proto.getCreatedAt(),
                proto.hasFinishedAt() ? proto.getFinishedAt() : null,
                proto.getIsActive(),
                proto.hasFinishedAt() ? proto.getFinishedAt() - proto.getCreatedAt() : null);
    }

    private static String fromProtoInstanceStatus(pbouda.jeffrey.server.api.v1.InstanceStatus status) {
        return switch (status) {
            case INSTANCE_STATUS_PENDING -> "PENDING";
            case INSTANCE_STATUS_ACTIVE -> "ACTIVE";
            case INSTANCE_STATUS_FINISHED -> "FINISHED";
            case INSTANCE_STATUS_EXPIRED -> "EXPIRED";
            default -> "UNKNOWN";
        };
    }
}
