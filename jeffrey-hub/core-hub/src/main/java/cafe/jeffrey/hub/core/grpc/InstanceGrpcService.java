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

import cafe.jeffrey.hub.core.project.repository.SessionDetail;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cafe.jeffrey.hub.api.v1.*;
import cafe.jeffrey.hub.persistence.api.HubPlatformRepositories;
import cafe.jeffrey.hub.model.ProjectInstanceInfo;
import cafe.jeffrey.hub.model.ProjectInstanceSessionInfo;
import cafe.jeffrey.hub.model.repository.InstanceStats;
import cafe.jeffrey.hub.model.repository.RecordingSession;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

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
            // Resolved first so an unknown project is NOT_FOUND whatever include_sessions says
            String projectId = lookups.projectInfo(request.getProjectId()).id();
            List<ProjectInstanceInfo> rawInstances = platformRepositories
                    .newProjectInstanceRepository(projectId).findAll();

            Map<String, List<ProjectInstanceSessionInfo>> sessionsByInstanceId;
            Set<String> failedSessionIds;
            if (request.getIncludeSessions()) {
                sessionsByInstanceId = platformRepositories.findSessionsByProjectId(projectId).stream()
                        .collect(Collectors.groupingBy(ProjectInstanceSessionInfo::instanceId));
                failedSessionIds = failedSessionIds(lookups.repositoryManagerForProject(projectId)
                        .listRecordingSessions(SessionDetail.WITH_FILES));
            } else {
                sessionsByInstanceId = Map.of();
                failedSessionIds = Set.of();
            }

            List<InstanceInfo> instances = rawInstances.stream()
                    .map(info -> ProtoMappers.instance(
                            info, sessionsByInstanceId.getOrDefault(info.id(), List.of()), failedSessionIds))
                    .toList();

            LOG.debug("Listed instances via gRPC: project_id={} count={} include_sessions={}",
                    projectId, instances.size(), request.getIncludeSessions());

            return ListInstancesResponse.newBuilder()
                    .addAllInstances(instances)
                    .build();
        });
    }

    @Override
    public void getInstance(GetInstanceRequest request, StreamObserver<GetInstanceResponse> responseObserver) {
        GrpcUnary.respond(responseObserver, () -> {
            ProjectInstanceInfo instance = lookups.instanceById(request.getInstanceId());

            LOG.debug("Fetched instance via gRPC: instance_id={}", request.getInstanceId());

            return GetInstanceResponse.newBuilder()
                    .setInstance(ProtoMappers.instance(instance, List.of(), Set.of()))
                    .build();
        });
    }

    @Override
    public void listInstanceSessions(ListInstanceSessionsRequest request, StreamObserver<ListInstanceSessionsResponse> responseObserver) {
        GrpcUnary.respond(responseObserver, () -> {
            ProjectInstanceInfo instance = lookups.instanceById(request.getInstanceId());
            Set<String> failedSessionIds = failedSessionIds(
                    lookups.repositoryManagerForProject(instance.projectId()).instanceSessions(instance.id()));

            List<InstanceSessionInfo> sessions = platformRepositories
                    .findSessionsByInstanceId(request.getInstanceId()).stream()
                    .map(s -> ProtoMappers.instanceSession(s, failedSessionIds))
                    .toList();

            LOG.debug("Listed instance sessions via gRPC: instance_id={} count={}",
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

            ProjectInstanceInfo info = lookups.instanceById(instanceId);

            List<ProjectInstanceSessionInfo> sessions = platformRepositories.findSessionsByInstanceId(instanceId);

            // One walk of the instance's directories answers both the statistics and the failed set
            List<RecordingSession> instanceSessions =
                    lookups.repositoryManagerForProject(info.projectId()).instanceSessions(instanceId);
            InstanceStats stats = InstanceStats.of(instanceSessions);
            Set<String> failedSessionIds = failedSessionIds(instanceSessions);

            LOG.debug("Fetched instance detail via gRPC: instance_id={} sessions={} files={} total_size={}",
                    instanceId, sessions.size(), stats.fileCount(), stats.totalSizeBytes());

            return GetInstanceDetailResponse.newBuilder()
                    .setInstance(ProtoMappers.instance(info, sessions, failedSessionIds))
                    .setStats(ProtoMappers.instanceStats(stats))
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

            ProjectInstanceInfo instance = lookups.instanceById(instanceId);

            ProjectInstanceSessionInfo sessionInfo = platformRepositories.findSessionsByInstanceId(instanceId).stream()
                    .filter(s -> s.sessionId().equals(sessionId))
                    .findFirst()
                    .orElseThrow(() -> GrpcExceptions.notFound(
                            "Session not found in instance: instanceId=" + instanceId + " sessionId=" + sessionId));

            // Only this session's directory, not every session of the project
            Set<String> failedSessionIds = lookups.repositoryManagerForProject(instance.projectId())
                    .findRecordingSessions(sessionId)
                    .filter(RecordingSession::isFailedEmpty)
                    .map(session -> Set.of(session.id()))
                    .orElse(Set.of());

            LOG.debug("Fetched instance session detail via gRPC: instance_id={} session_id={}",
                    instanceId, sessionId);

            return GetInstanceSessionDetailResponse.newBuilder()
                    .setSession(ProtoMappers.instanceSession(sessionInfo, failedSessionIds))
                    .build();
        });
    }


    /**
     * IDs of the failed sessions among these — finished without producing any data, which only
     * the files on the volume can say, so the sessions must have been loaded with them.
     */
    private static Set<String> failedSessionIds(List<RecordingSession> sessions) {
        return sessions.stream()
                .filter(RecordingSession::isFailedEmpty)
                .map(RecordingSession::id)
                .collect(Collectors.toSet());
    }

}
