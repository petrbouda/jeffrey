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

package cafe.jeffrey.hub.client.manager;

import cafe.jeffrey.hub.client.InstancesClient;
import cafe.jeffrey.hub.client.RepositoryClient;
import cafe.jeffrey.hub.client.dto.InstanceDetailResponse;
import cafe.jeffrey.hub.client.dto.InstanceResponse;
import cafe.jeffrey.hub.client.dto.InstanceSessionDetailResponse;
import cafe.jeffrey.hub.client.dto.InstanceSessionResponse;
import cafe.jeffrey.hub.client.dto.InstanceStatsResponse;
import cafe.jeffrey.hub.client.dto.RecordingSessionResponse;
import cafe.jeffrey.hub.client.environment.SessionEnvironmentReader;
import cafe.jeffrey.microscope.model.ProjectInfo;
import cafe.jeffrey.microscope.model.ProjectInstanceInfo;
import cafe.jeffrey.microscope.model.ProjectInstanceInfo.ProjectInstanceStatus;
import cafe.jeffrey.microscope.model.ProjectInstanceSessionInfo;
import cafe.jeffrey.microscope.model.repository.RecordingSession;
import cafe.jeffrey.shared.common.InstantUtils;
import tools.jackson.databind.JsonNode;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * A project's instances, put together from two hub listings: the instance lifecycle from
 * {@link InstancesClient}, and the sessions under each instance from {@link RepositoryClient}.
 * The hub keeps one session listing; an instance's sessions are that listing narrowed to it.
 */
public class RemoteInstancesManager {

    private final ProjectInfo projectInfo;
    private final InstancesClient instancesClient;
    private final RepositoryClient repositoryClient;
    private final SessionEnvironmentReader sessionEnvironmentReader;

    public RemoteInstancesManager(
            ProjectInfo projectInfo,
            InstancesClient instancesClient,
            RepositoryClient repositoryClient,
            SessionEnvironmentReader sessionEnvironmentReader) {

        this.projectInfo = projectInfo;
        this.instancesClient = instancesClient;
        this.repositoryClient = repositoryClient;
        this.sessionEnvironmentReader = sessionEnvironmentReader;
    }

    /**
     * @param includeSessions whether each instance carries its sessions — one more listing, of
     *                        every session of the project
     */
    public List<ProjectInstanceInfo> findAll(boolean includeSessions) {
        List<InstanceResponse> instances = instancesClient.projectInstances(projectInfo.id());
        if (!includeSessions) {
            return instances.stream()
                    .map(instance -> toProjectInstanceInfo(instance, List.of()))
                    .toList();
        }

        Map<String, List<RecordingSessionResponse>> sessionsByInstance =
                repositoryClient.recordingSessions(projectInfo.id()).stream()
                        .filter(session -> session.instanceId() != null)
                        .collect(Collectors.groupingBy(RecordingSessionResponse::instanceId));

        return instances.stream()
                .map(instance -> toProjectInstanceInfo(
                        instance, sessionsByInstance.getOrDefault(instance.id(), List.of())))
                .toList();
    }

    public Optional<ProjectInstanceInfo> find(String instanceId) {
        return findInstance(instanceId)
                .map(instance -> toProjectInstanceInfo(instance, List.of()));
    }

    public Optional<InstanceDetailResponse> detail(String instanceId) {
        return findInstance(instanceId).map(instance -> {
            List<RecordingSessionResponse> sessions = repositoryClient.instanceSessions(projectInfo.id(), instanceId);
            return new InstanceDetailResponse(withSessions(instance, sessions), stats(sessions));
        });
    }

    public Optional<InstanceSessionDetailResponse> sessionDetail(String instanceId, String sessionId) {
        RecordingSessionResponse session = repositoryClient.recordingSession(sessionId);
        if (!instanceId.equals(session.instanceId())) {
            return Optional.empty();
        }
        boolean expectShutdown = session.finishedAt() != null;
        JsonNode environment = sessionEnvironmentReader
                .forSession(RecordingSessionResponse.from(session), expectShutdown)
                .orElse(null);
        return Optional.of(new InstanceSessionDetailResponse(toSessionResponse(session), environment));
    }

    private Optional<InstanceResponse> findInstance(String instanceId) {
        return instancesClient.projectInstances(projectInfo.id()).stream()
                .filter(instance -> instance.id().equals(instanceId))
                .findFirst();
    }

    private static InstanceStatsResponse stats(List<RecordingSessionResponse> sessions) {
        int fileCount = sessions.stream().mapToInt(session -> session.files().size()).sum();
        long totalSize = sessions.stream()
                .map(RecordingSessionResponse::from)
                .mapToLong(RecordingSession::totalSizeBytes)
                .sum();
        return new InstanceStatsResponse(fileCount, totalSize);
    }

    private static InstanceResponse withSessions(InstanceResponse instance, List<RecordingSessionResponse> sessions) {
        return new InstanceResponse(
                instance.id(),
                instance.instanceName(),
                instance.status(),
                instance.createdAt(),
                instance.finishedAt(),
                instance.expiringAt(),
                instance.expiredAt(),
                instance.sessionCount(),
                activeSessionId(sessions),
                instance.duration(),
                sessions.stream().map(RemoteInstancesManager::toSessionResponse).toList());
    }

    /**
     * The session the instance is still recording: the one without a finish, newest first.
     */
    private static String activeSessionId(List<RecordingSessionResponse> sessions) {
        return sessions.stream()
                .filter(session -> session.finishedAt() == null)
                .map(RecordingSessionResponse::id)
                .findFirst()
                .orElse(null);
    }

    private static InstanceSessionResponse toSessionResponse(RecordingSessionResponse session) {
        Long finishedAt = session.finishedAt();
        return new InstanceSessionResponse(
                session.id(),
                null,
                session.createdAt(),
                finishedAt,
                finishedAt == null,
                finishedAt != null ? finishedAt - session.createdAt() : null,
                session.failed(),
                session.heartbeatMissing());
    }

    private ProjectInstanceInfo toProjectInstanceInfo(
            InstanceResponse response, List<RecordingSessionResponse> sessions) {

        return new ProjectInstanceInfo(
                response.id(),
                projectInfo.id(),
                response.instanceName(),
                ProjectInstanceStatus.valueOf(response.status()),
                InstantUtils.fromEpochMilli(response.createdAt()),
                InstantUtils.fromEpochMilli(response.finishedAt()),
                InstantUtils.fromEpochMilli(response.expiringAt()),
                InstantUtils.fromEpochMilli(response.expiredAt()),
                response.sessionCount(),
                activeSessionId(sessions),
                sessions.stream().map(RemoteInstancesManager::toProjectInstanceSessionInfo).toList());
    }

    private static ProjectInstanceSessionInfo toProjectInstanceSessionInfo(RecordingSessionResponse session) {
        return ProjectInstanceSessionInfo.notRetained(
                        session.id(),
                        null,
                        session.instanceId(),
                        0,
                        null,
                        null,
                        InstantUtils.fromEpochMilli(session.createdAt()),
                        InstantUtils.fromEpochMilli(session.finishedAt()))
                .withFailed(session.failed())
                .withHeartbeatMissing(session.heartbeatMissing());
    }
}
