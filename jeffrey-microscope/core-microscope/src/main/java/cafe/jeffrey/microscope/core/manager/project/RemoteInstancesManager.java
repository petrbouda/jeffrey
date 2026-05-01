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

package cafe.jeffrey.microscope.core.manager.project;

import cafe.jeffrey.microscope.core.client.RemoteInstancesClient;
import cafe.jeffrey.microscope.core.resources.response.InstanceDetailResponse;
import cafe.jeffrey.microscope.core.resources.response.InstanceResponse;
import cafe.jeffrey.microscope.core.resources.response.InstanceSessionDetailResponse;
import cafe.jeffrey.microscope.core.resources.response.InstanceSessionResponse;
import cafe.jeffrey.shared.common.InstantUtils;
import cafe.jeffrey.shared.common.model.ProjectInfo;
import cafe.jeffrey.shared.common.model.ProjectInstanceInfo;
import cafe.jeffrey.shared.common.model.ProjectInstanceInfo.ProjectInstanceStatus;
import cafe.jeffrey.shared.common.model.ProjectInstanceSessionInfo;

import java.util.List;
import java.util.Optional;

public class RemoteInstancesManager {

    private final ProjectInfo projectInfo;
    private final RemoteInstancesClient instancesClient;

    public RemoteInstancesManager(
            ProjectInfo projectInfo,
            RemoteInstancesClient instancesClient) {

        this.projectInfo = projectInfo;
        this.instancesClient = instancesClient;
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

    public Optional<InstanceSessionDetailResponse> sessionDetail(String instanceId, String sessionId) {
        return Optional.ofNullable(instancesClient.instanceSessionDetail(instanceId, sessionId));
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
        return new ProjectInstanceSessionInfo(
                response.id(),
                response.repositoryId(),
                null,
                0,
                null,
                null,
                InstantUtils.fromEpochMilli(response.createdAt()),
                InstantUtils.fromEpochMilli(response.finishedAt()));
    }
}
