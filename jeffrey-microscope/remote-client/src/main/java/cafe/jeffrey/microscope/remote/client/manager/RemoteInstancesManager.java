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

package cafe.jeffrey.microscope.remote.client.manager;

import cafe.jeffrey.microscope.remote.client.InstancesClient;
import cafe.jeffrey.microscope.remote.client.ProjectScope;
import cafe.jeffrey.microscope.remote.client.RepositoryClient;
import cafe.jeffrey.microscope.remote.client.dto.InstanceDetailResponse;
import cafe.jeffrey.microscope.remote.client.dto.InstanceResponse;
import cafe.jeffrey.microscope.remote.client.dto.InstanceSessionDetailResponse;
import cafe.jeffrey.microscope.remote.client.dto.InstanceSessionResponse;
import cafe.jeffrey.microscope.remote.client.dto.InstanceStatsResponse;
import cafe.jeffrey.microscope.remote.client.dto.RecordingSessionResponse;
import cafe.jeffrey.microscope.remote.client.environment.SessionEnvironmentReader;
import cafe.jeffrey.microscope.model.ProjectInstanceInfo;
import cafe.jeffrey.microscope.model.ProjectInstanceInfo.ProjectInstanceStatus;
import cafe.jeffrey.microscope.model.ProjectInstanceSessionInfo;
import cafe.jeffrey.shared.common.InstantUtils;
import tools.jackson.databind.JsonNode;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * A project's instances, one level of the hub's tree per call: the project's instances from
 * {@link InstancesClient#projectInstances}, one instance with its sessions from
 * {@link InstancesClient#instance}. No session arrives with its files; a session's files come
 * from {@link RepositoryClient#recordingSession} when that session is opened.
 */
public class RemoteInstancesManager {

    private final ProjectScope scope;
    private final InstancesClient instancesClient;
    private final RepositoryClient repositoryClient;
    private final SessionEnvironmentReader sessionEnvironmentReader;

    public RemoteInstancesManager(
            ProjectScope scope,
            InstancesClient instancesClient,
            RepositoryClient repositoryClient,
            SessionEnvironmentReader sessionEnvironmentReader) {

        this.scope = scope;
        this.instancesClient = instancesClient;
        this.repositoryClient = repositoryClient;
        this.sessionEnvironmentReader = sessionEnvironmentReader;
    }

    /**
     * @param includeSessions whether each instance carries its sessions — one more listing, of
     *                        every session header of the project, for the timeline that draws
     *                        them all side by side
     */
    public List<ProjectInstanceInfo> findAll(boolean includeSessions) {
        List<InstanceResponse> instances = instancesClient.projectInstances(scope);
        if (!includeSessions) {
            return instances.stream()
                    .map(instance -> toProjectInstanceInfo(instance, List.of()))
                    .toList();
        }

        Map<String, List<RecordingSessionResponse>> sessionsByInstance =
                repositoryClient.recordingSessions(scope).stream()
                        .filter(session -> session.instanceId() != null)
                        .collect(Collectors.groupingBy(RecordingSessionResponse::instanceId));

        return instances.stream()
                .map(instance -> toProjectInstanceInfo(
                        instance, sessionsByInstance.getOrDefault(instance.id(), List.of())))
                .toList();
    }

    /**
     * One instance with its sessions, from one read of the hub; empty when the project holds no
     * instance by that id.
     */
    public Optional<InstanceWithSessions> find(String instanceId) {
        return instancesClient.instance(scope, instanceId)
                .map(found -> new InstanceWithSessions(
                        toProjectInstanceInfo(found.instance(), found.sessions()),
                        found.sessions().stream()
                                .map(RecordingSessionResponse::toSession)
                                .toList()));
    }

    public Optional<InstanceDetailResponse> detail(String instanceId) {
        return instancesClient.instance(scope, instanceId)
                .map(found -> new InstanceDetailResponse(
                        withSessions(found.instance(), found.sessions()), stats(found.sessions())));
    }

    public Optional<InstanceSessionDetailResponse> sessionDetail(String instanceId, String sessionId) {
        RecordingSessionResponse session = repositoryClient.recordingSession(scope, sessionId);
        if (!instanceId.equals(session.instanceId())) {
            return Optional.empty();
        }
        boolean expectShutdown = session.finishedAt() != null;
        JsonNode environment = sessionEnvironmentReader
                .forSession(session.toSession(), expectShutdown)
                .orElse(null);
        return Optional.of(new InstanceSessionDetailResponse(toSessionResponse(session), environment));
    }

    /** Summed from the size each session arrived with; no session's files are fetched for it. */
    private static InstanceStatsResponse stats(List<RecordingSessionResponse> sessions) {
        int fileCount = sessions.stream().mapToInt(RecordingSessionResponse::fileCount).sum();
        long totalSize = sessions.stream().mapToLong(RecordingSessionResponse::sizeBytes).sum();
        return new InstanceStatsResponse(fileCount, totalSize);
    }

    private static InstanceResponse withSessions(InstanceResponse instance, List<RecordingSessionResponse> sessions) {
        return new InstanceResponse(
                instance.id(),
                instance.instanceName(),
                instance.status(),
                instance.createdAt(),
                instance.finishedAt(),
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
                scope.projectId(),
                response.instanceName(),
                ProjectInstanceStatus.valueOf(response.status()),
                InstantUtils.fromEpochMilli(response.createdAt()),
                InstantUtils.fromEpochMilli(response.finishedAt()),
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
