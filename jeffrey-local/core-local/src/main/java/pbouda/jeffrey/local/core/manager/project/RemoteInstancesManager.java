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

package pbouda.jeffrey.local.core.manager.project;

import pbouda.jeffrey.local.core.client.RemoteInstancesClient;
import pbouda.jeffrey.local.core.resources.response.InstanceResponse;
import pbouda.jeffrey.local.core.resources.response.InstanceSessionResponse;
import pbouda.jeffrey.shared.common.InstantUtils;
import pbouda.jeffrey.shared.common.model.ProjectInfo;
import pbouda.jeffrey.shared.common.model.ProjectInstanceInfo;
import pbouda.jeffrey.shared.common.model.ProjectInstanceInfo.ProjectInstanceStatus;
import pbouda.jeffrey.shared.common.model.ProjectInstanceSessionInfo;
import pbouda.jeffrey.shared.common.model.workspace.WorkspaceInfo;

import java.util.List;
import java.util.Optional;

public class RemoteInstancesManager {

    private final ProjectInfo projectInfo;
    private final WorkspaceInfo workspaceInfo;
    private final RemoteInstancesClient instancesClient;

    public RemoteInstancesManager(
            ProjectInfo projectInfo,
            WorkspaceInfo workspaceInfo,
            RemoteInstancesClient instancesClient) {

        this.projectInfo = projectInfo;
        this.workspaceInfo = workspaceInfo;
        this.instancesClient = instancesClient;
    }

    public List<ProjectInstanceInfo> findAll() {
        return instancesClient.projectInstances(workspaceInfo.originId(), projectInfo.originId()).stream()
                .map(this::toProjectInstanceInfo)
                .toList();
    }

    public Optional<ProjectInstanceInfo> find(String instanceId) {
        InstanceResponse response = instancesClient.projectInstance(
                workspaceInfo.originId(), projectInfo.originId(), instanceId);
        return Optional.ofNullable(response).map(this::toProjectInstanceInfo);
    }

    public List<ProjectInstanceSessionInfo> findSessions(String instanceId) {
        return instancesClient.projectInstanceSessions(
                        workspaceInfo.originId(), projectInfo.originId(), instanceId).stream()
                .map(RemoteInstancesManager::toProjectInstanceSessionInfo)
                .toList();
    }

    private ProjectInstanceInfo toProjectInstanceInfo(InstanceResponse response) {
        return new ProjectInstanceInfo(
                response.id(),
                projectInfo.originId(),
                response.hostname(),
                ProjectInstanceStatus.valueOf(response.status()),
                InstantUtils.fromEpochMilli(response.createdAt()),
                InstantUtils.fromEpochMilli(response.finishedAt()),
                InstantUtils.fromEpochMilli(response.expiringAt()),
                InstantUtils.fromEpochMilli(response.expiredAt()),
                response.sessionCount(),
                response.activeSessionId());
    }

    private static ProjectInstanceSessionInfo toProjectInstanceSessionInfo(InstanceSessionResponse response) {
        return new ProjectInstanceSessionInfo(
                response.id(),
                response.repositoryId(),
                null,
                0,
                null,
                null,
                null,
                InstantUtils.fromEpochMilli(response.createdAt()),
                InstantUtils.fromEpochMilli(response.finishedAt()));
    }
}
