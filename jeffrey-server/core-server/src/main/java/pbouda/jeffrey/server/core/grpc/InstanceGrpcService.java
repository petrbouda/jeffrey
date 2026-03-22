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

package pbouda.jeffrey.server.core.grpc;

import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import pbouda.jeffrey.api.v1.*;
import pbouda.jeffrey.server.core.manager.project.ProjectManager;
import pbouda.jeffrey.server.core.manager.workspace.WorkspaceManager;
import pbouda.jeffrey.server.core.manager.workspace.WorkspacesManager;
import pbouda.jeffrey.shared.common.model.ProjectInstanceInfo;
import pbouda.jeffrey.shared.common.model.ProjectInstanceSessionInfo;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Component
public class InstanceGrpcService extends InstanceServiceGrpc.InstanceServiceImplBase {

    private static final Logger LOG = LoggerFactory.getLogger(InstanceGrpcService.class);

    private final WorkspacesManager workspacesManager;
    private final Clock clock;

    public InstanceGrpcService(WorkspacesManager workspacesManager, Clock clock) {
        this.workspacesManager = workspacesManager;
        this.clock = clock;
    }

    @Override
    public void listInstances(ListInstancesRequest request, StreamObserver<ListInstancesResponse> responseObserver) {
        try {
            ProjectManager project = findProject(request.getWorkspaceId(), request.getProjectId());

            List<InstanceInfo> instances = project.projectInstanceRepository().findAll().stream()
                    .map(i -> toProto(i, clock))
                    .toList();

            LOG.debug("Listed instances via gRPC: projectId={} count={}", request.getProjectId(), instances.size());

            ListInstancesResponse response = ListInstancesResponse.newBuilder()
                    .addAllInstances(instances)
                    .build();
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (io.grpc.StatusRuntimeException e) {
            responseObserver.onError(e);
        } catch (Exception e) {
            LOG.error("Failed to list instances: projectId={}", request.getProjectId(), e);
            responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
        }
    }

    @Override
    public void getInstance(GetInstanceRequest request, StreamObserver<GetInstanceResponse> responseObserver) {
        try {
            ProjectManager project = findProject(request.getWorkspaceId(), request.getProjectId());

            ProjectInstanceInfo instance = project.projectInstanceRepository().find(request.getInstanceId())
                    .orElseThrow(() -> Status.NOT_FOUND
                            .withDescription("Instance not found: " + request.getInstanceId())
                            .asRuntimeException());

            LOG.debug("Fetched instance via gRPC: instanceId={}", request.getInstanceId());

            GetInstanceResponse response = GetInstanceResponse.newBuilder()
                    .setInstance(toProto(instance, clock))
                    .build();
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (io.grpc.StatusRuntimeException e) {
            responseObserver.onError(e);
        } catch (Exception e) {
            LOG.error("Failed to get instance: instanceId={}", request.getInstanceId(), e);
            responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
        }
    }

    @Override
    public void listInstanceSessions(ListInstanceSessionsRequest request, StreamObserver<ListInstanceSessionsResponse> responseObserver) {
        try {
            ProjectManager project = findProject(request.getWorkspaceId(), request.getProjectId());

            List<InstanceSessionInfo> sessions = project.projectInstanceRepository()
                    .findSessions(request.getInstanceId()).stream()
                    .map(s -> toSessionProto(s, clock))
                    .toList();

            LOG.debug("Listed instance sessions via gRPC: instanceId={} count={}",
                    request.getInstanceId(), sessions.size());

            ListInstanceSessionsResponse response = ListInstanceSessionsResponse.newBuilder()
                    .addAllSessions(sessions)
                    .build();
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (io.grpc.StatusRuntimeException e) {
            responseObserver.onError(e);
        } catch (Exception e) {
            LOG.error("Failed to list instance sessions: instanceId={}", request.getInstanceId(), e);
            responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
        }
    }

    private ProjectManager findProject(String workspaceId, String projectId) {
        WorkspaceManager workspace = workspacesManager.findById(workspaceId)
                .orElseThrow(() -> Status.NOT_FOUND
                        .withDescription("Workspace not found: " + workspaceId)
                        .asRuntimeException());
        return workspace.projectsManager().project(projectId)
                .orElseThrow(() -> Status.NOT_FOUND
                        .withDescription("Project not found: " + projectId)
                        .asRuntimeException());
    }

    private static InstanceInfo toProto(ProjectInstanceInfo info, Clock clock) {
        Instant end = info.finishedAt() != null ? info.finishedAt() : clock.instant();
        long durationMillis = end.toEpochMilli() - info.startedAt().toEpochMilli();

        InstanceInfo.Builder builder = InstanceInfo.newBuilder()
                .setId(info.id())
                .setHostname(info.hostname() != null ? info.hostname() : "")
                .setStatus(info.status().name())
                .setCreatedAt(String.valueOf(info.startedAt().toEpochMilli()))
                .setSessionCount(info.sessionCount())
                .setDuration(String.valueOf(durationMillis));

        if (info.finishedAt() != null) {
            builder.setFinishedAt(String.valueOf(info.finishedAt().toEpochMilli()));
        }
        if (info.expiringAt() != null) {
            builder.setExpiringAt(String.valueOf(info.expiringAt().toEpochMilli()));
        }
        if (info.expiredAt() != null) {
            builder.setExpiredAt(String.valueOf(info.expiredAt().toEpochMilli()));
        }
        if (info.activeSessionId() != null) {
            builder.setActiveSessionId(info.activeSessionId());
        }

        return builder.build();
    }

    private static InstanceSessionInfo toSessionProto(ProjectInstanceSessionInfo info, Clock clock) {
        Instant end = info.finishedAt() != null ? info.finishedAt() : clock.instant();
        long durationMillis = end.toEpochMilli() - info.createdAt().toEpochMilli();

        InstanceSessionInfo.Builder builder = InstanceSessionInfo.newBuilder()
                .setId(info.sessionId())
                .setRepositoryId(info.repositoryId() != null ? info.repositoryId() : "")
                .setCreatedAt(String.valueOf(info.createdAt().toEpochMilli()))
                .setIsActive(info.finishedAt() == null)
                .setDuration(String.valueOf(durationMillis));

        if (info.finishedAt() != null) {
            builder.setFinishedAt(String.valueOf(info.finishedAt().toEpochMilli()));
        }

        return builder.build();
    }
}
