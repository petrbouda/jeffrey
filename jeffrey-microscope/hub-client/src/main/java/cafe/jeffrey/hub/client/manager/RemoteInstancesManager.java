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
import cafe.jeffrey.hub.client.dto.InstanceDetailResponse;
import cafe.jeffrey.hub.client.dto.InstanceResponse;
import cafe.jeffrey.hub.client.dto.InstanceSessionDetailResponse;
import cafe.jeffrey.hub.client.dto.InstanceSessionResponse;
import cafe.jeffrey.hub.client.environment.SessionEnvironmentReader;
import cafe.jeffrey.shared.common.InstantUtils;
import cafe.jeffrey.microscope.model.ProjectInfo;
import cafe.jeffrey.microscope.model.ProjectInstanceInfo;
import cafe.jeffrey.microscope.model.ProjectInstanceInfo.ProjectInstanceStatus;
import cafe.jeffrey.microscope.model.ProjectInstanceSessionInfo;
import tools.jackson.databind.JsonNode;

import java.util.List;
import java.util.Optional;

public class RemoteInstancesManager {

    private final ProjectInfo projectInfo;
    private final InstancesClient instancesClient;
    private final SessionEnvironmentReader sessionEnvironmentReader;

    public RemoteInstancesManager(
            ProjectInfo projectInfo,
            InstancesClient instancesClient,
            SessionEnvironmentReader sessionEnvironmentReader) {

        this.projectInfo = projectInfo;
        this.instancesClient = instancesClient;
        this.sessionEnvironmentReader = sessionEnvironmentReader;
    }

    public List<ProjectInstanceInfo> findAll(boolean includeSessions) {
        return instancesClient.projectInstances(projectInfo.id(), includeSessions).stream()
                .map(this::toProjectInstanceInfo)
                .toList();
    }

    public Optional<ProjectInstanceInfo> find(String instanceId) {
        InstanceResponse response = instancesClient.projectInstance(instanceId);
        return Optional.ofNullable(response).map(this::toProjectInstanceInfo);
    }

    public Optional<InstanceDetailResponse> detail(String instanceId) {
        return Optional.ofNullable(instancesClient.instanceDetail(instanceId));
    }

    /**
     * The session's metadata from the hub, with its one-shot JFR environment events read here
     * from the session's newest closed chunk. Two calls rather than one: the hub answers about
     * the session, and {@link SessionEnvironmentReader} pulls the chunk and parses it, because
     * the hub holds no JFR reader.
     */
    public Optional<InstanceSessionDetailResponse> sessionDetail(String instanceId, String sessionId) {
        InstanceSessionResponse session = instancesClient.instanceSessionDetail(instanceId, sessionId);
        if (session == null) {
            return Optional.empty();
        }

        boolean expectShutdown = session.finishedAt() != null;
        JsonNode environment = sessionEnvironmentReader.forSession(sessionId, expectShutdown)
                .orElse(null);

        return Optional.of(new InstanceSessionDetailResponse(session, environment));
    }

    public List<ProjectInstanceSessionInfo> findSessions(String instanceId) {
        return instancesClient.projectInstanceSessions(instanceId).stream()
                .map(RemoteInstancesManager::toProjectInstanceSessionInfo)
                .toList();
    }

    private ProjectInstanceInfo toProjectInstanceInfo(InstanceResponse response) {
        List<ProjectInstanceSessionInfo> sessions = response.sessions() == null ? List.of()
                : response.sessions().stream()
                        .map(RemoteInstancesManager::toProjectInstanceSessionInfo)
                        .toList();

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
                response.activeSessionId(),
                sessions);
    }

    private static ProjectInstanceSessionInfo toProjectInstanceSessionInfo(InstanceSessionResponse response) {
        return ProjectInstanceSessionInfo.notRetained(
                        response.id(),
                        response.repositoryId(),
                        null,
                        0,
                        null,
                        null,
                        InstantUtils.fromEpochMilli(response.createdAt()),
                        InstantUtils.fromEpochMilli(response.finishedAt()))
                .withFailed(response.failed());
    }
}
