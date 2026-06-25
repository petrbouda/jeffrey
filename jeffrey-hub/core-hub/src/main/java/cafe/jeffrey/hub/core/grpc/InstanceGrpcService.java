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

package cafe.jeffrey.hub.core.grpc;

import tools.jackson.databind.node.ObjectNode;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cafe.jeffrey.hub.api.v1.*;
import cafe.jeffrey.hub.core.manager.RepositoryManager;
import cafe.jeffrey.hub.persistence.api.HubPlatformRepositories;
import cafe.jeffrey.shared.common.Json;
import cafe.jeffrey.shared.common.model.ProjectInfo;
import cafe.jeffrey.shared.common.model.ProjectInstanceInfo;
import cafe.jeffrey.shared.common.model.ProjectInstanceSessionInfo;
import cafe.jeffrey.shared.common.model.repository.InstanceStats;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public class InstanceGrpcService extends InstanceServiceGrpc.InstanceServiceImplBase {

    private static final Logger LOG = LoggerFactory.getLogger(InstanceGrpcService.class);

    private final HubPlatformRepositories platformRepositories;
    private final RepositoryManager.Factory repositoryManagerFactory;
    private final Clock clock;

    public InstanceGrpcService(
            HubPlatformRepositories platformRepositories,
            RepositoryManager.Factory repositoryManagerFactory,
            Clock clock) {
        this.platformRepositories = platformRepositories;
        this.repositoryManagerFactory = repositoryManagerFactory;
        this.clock = clock;
    }

    @Override
    public void listInstances(ListInstancesRequest request, StreamObserver<ListInstancesResponse> responseObserver) {
        GrpcUnary.respond(responseObserver, () -> {
            String projectId = request.getProjectId();
            List<ProjectInstanceInfo> rawInstances = platformRepositories
                    .newProjectInstanceRepository(projectId).findAll();

            Map<String, List<ProjectInstanceSessionInfo>> sessionsByInstanceId;
            if (request.getIncludeSessions()) {
                sessionsByInstanceId = platformRepositories.findSessionsByProjectId(projectId).stream()
                        .collect(Collectors.groupingBy(ProjectInstanceSessionInfo::instanceId));
            } else {
                sessionsByInstanceId = Map.of();
            }

            List<InstanceInfo> instances = rawInstances.stream()
                    .map(info -> toProto(info, sessionsByInstanceId.getOrDefault(info.id(), List.of()), clock))
                    .toList();

            LOG.debug("Listed instances via gRPC: projectId={} count={} include_sessions={}",
                    projectId, instances.size(), request.getIncludeSessions());

            return ListInstancesResponse.newBuilder()
                    .addAllInstances(instances)
                    .build();
        });
    }

    @Override
    public void getInstance(GetInstanceRequest request, StreamObserver<GetInstanceResponse> responseObserver) {
        GrpcUnary.respond(responseObserver, () -> {
            ProjectInstanceInfo instance = platformRepositories.findInstanceById(request.getInstanceId())
                    .orElseThrow(() -> GrpcExceptions.notFound("Instance not found: " + request.getInstanceId()));

            LOG.debug("Fetched instance via gRPC: instanceId={}", request.getInstanceId());

            return GetInstanceResponse.newBuilder()
                    .setInstance(toProto(instance, List.of(), clock))
                    .build();
        });
    }

    @Override
    public void listInstanceSessions(ListInstanceSessionsRequest request, StreamObserver<ListInstanceSessionsResponse> responseObserver) {
        GrpcUnary.respond(responseObserver, () -> {
            List<InstanceSessionInfo> sessions = platformRepositories
                    .findSessionsByInstanceId(request.getInstanceId()).stream()
                    .map(s -> toSessionProto(s, clock))
                    .toList();

            LOG.debug("Listed instance sessions via gRPC: instanceId={} count={}",
                    request.getInstanceId(), sessions.size());

            return ListInstanceSessionsResponse.newBuilder()
                    .addAllSessions(sessions)
                    .build();
        });
    }

    @Override
    public void getInstanceDetail(GetInstanceDetailRequest request, StreamObserver<GetInstanceDetailResponse> responseObserver) {
        GrpcUnary.respond(responseObserver, () -> {
            String instanceId = request.getInstanceId();

            ProjectInstanceInfo info = platformRepositories.findInstanceById(instanceId)
                    .orElseThrow(() -> GrpcExceptions.notFound("Instance not found: " + instanceId));

            List<ProjectInstanceSessionInfo> sessions = platformRepositories.findSessionsByInstanceId(instanceId);

            ProjectInfo projectInfo = platformRepositories.newProjectRepository(info.projectId()).find()
                    .orElseThrow(() -> GrpcExceptions.notFound("Project not found: " + info.projectId()));
            RepositoryManager repoManager = repositoryManagerFactory.apply(projectInfo);
            InstanceStats stats = repoManager.instanceStats(instanceId);

            LOG.debug("Fetched instance detail via gRPC: instanceId={} sessions={} files={} totalSize={}",
                    instanceId, sessions.size(), stats.fileCount(), stats.totalSizeBytes());

            return GetInstanceDetailResponse.newBuilder()
                    .setInstance(toProto(info, sessions, clock))
                    .setStats(toProtoStats(stats))
                    .build();
        });
    }

    @Override
    public void getInstanceSessionDetail(
            GetInstanceSessionDetailRequest request,
            StreamObserver<GetInstanceSessionDetailResponse> responseObserver) {
        GrpcUnary.respond(responseObserver, () -> {
            String instanceId = request.getInstanceId();
            String sessionId = request.getSessionId();

            ProjectInstanceInfo instance = platformRepositories.findInstanceById(instanceId)
                    .orElseThrow(() -> GrpcExceptions.notFound("Instance not found: " + instanceId));

            ProjectInstanceSessionInfo sessionInfo = platformRepositories.findSessionsByInstanceId(instanceId).stream()
                    .filter(s -> s.sessionId().equals(sessionId))
                    .findFirst()
                    .orElseThrow(() -> GrpcExceptions.notFound(
                            "Session not found in instance: instanceId=" + instanceId + " sessionId=" + sessionId));

            ProjectInfo projectInfo = platformRepositories.newProjectRepository(instance.projectId()).find()
                    .orElseThrow(() -> GrpcExceptions.notFound("Project not found: " + instance.projectId()));
            RepositoryManager repoManager = repositoryManagerFactory.apply(projectInfo);

            boolean expectShutdown = sessionInfo.finishedAt() != null;
            Optional<ObjectNode> environment = repoManager.sessionEnvironment(sessionId, expectShutdown);

            String environmentJson = environment.map(Json::toString).orElse("");

            LOG.debug("Fetched instance session detail via gRPC: instanceId={} sessionId={} envTypes={}",
                    instanceId, sessionId,
                    environment.map(node -> List.copyOf(node.propertyNames())).orElse(List.of()));

            return GetInstanceSessionDetailResponse.newBuilder()
                    .setSession(toSessionProto(sessionInfo, clock))
                    .setEnvironmentJsonFields(environmentJson)
                    .build();
        });
    }

    private static cafe.jeffrey.hub.api.v1.InstanceStats toProtoStats(InstanceStats stats) {
        return cafe.jeffrey.hub.api.v1.InstanceStats.newBuilder()
                .setFileCount(stats.fileCount())
                .setTotalSizeBytes(stats.totalSizeBytes())
                .build();
    }

    private static InstanceInfo toProto(ProjectInstanceInfo info, List<ProjectInstanceSessionInfo> sessions, Clock clock) {
        InstanceInfo.Builder builder = InstanceInfo.newBuilder()
                .setId(info.id())
                .setInstanceName(info.instanceName() != null ? info.instanceName() : "")
                .setStatus(toProtoInstanceStatus(info.status()))
                .setCreatedAt(info.startedAt().toEpochMilli())
                .setSessionCount(info.sessionCount());

        if (info.finishedAt() != null) {
            builder.setFinishedAt(info.finishedAt().toEpochMilli());
        }
        if (info.expiringAt() != null) {
            builder.setExpiringAt(info.expiringAt().toEpochMilli());
        }
        if (info.expiredAt() != null) {
            builder.setExpiredAt(info.expiredAt().toEpochMilli());
        }
        if (info.activeSessionId() != null) {
            builder.setActiveSessionId(info.activeSessionId());
        }

        for (ProjectInstanceSessionInfo session : sessions) {
            builder.addSessions(toSessionProto(session, clock));
        }

        return builder.build();
    }

    private static InstanceSessionInfo toSessionProto(ProjectInstanceSessionInfo info, Clock clock) {
        InstanceSessionInfo.Builder builder = InstanceSessionInfo.newBuilder()
                .setId(info.sessionId())
                .setRepositoryId(info.repositoryId() != null ? info.repositoryId() : "")
                .setCreatedAt(info.createdAt().toEpochMilli())
                .setIsActive(info.finishedAt() == null);

        if (info.finishedAt() != null) {
            builder.setFinishedAt(info.finishedAt().toEpochMilli());
        }

        return builder.build();
    }

    private static cafe.jeffrey.hub.api.v1.InstanceStatus toProtoInstanceStatus(
            ProjectInstanceInfo.ProjectInstanceStatus status) {
        return switch (status) {
            case PENDING -> cafe.jeffrey.hub.api.v1.InstanceStatus.INSTANCE_STATUS_PENDING;
            case ACTIVE -> cafe.jeffrey.hub.api.v1.InstanceStatus.INSTANCE_STATUS_ACTIVE;
            case FINISHED -> cafe.jeffrey.hub.api.v1.InstanceStatus.INSTANCE_STATUS_FINISHED;
            case EXPIRED -> cafe.jeffrey.hub.api.v1.InstanceStatus.INSTANCE_STATUS_EXPIRED;
        };
    }
}
