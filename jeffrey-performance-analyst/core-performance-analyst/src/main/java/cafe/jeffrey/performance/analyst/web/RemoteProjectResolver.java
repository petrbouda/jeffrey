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

package cafe.jeffrey.performance.analyst.web;

import cafe.jeffrey.performance.analyst.manager.HubManager;
import cafe.jeffrey.performance.analyst.manager.HubsManager;
import cafe.jeffrey.performance.analyst.recordings.ProjectRecordingsManagerFactory;
import cafe.jeffrey.recordings.core.OriginContext;
import cafe.jeffrey.recordings.core.RemoteRecordingsDownloadManager;
import cafe.jeffrey.hub.client.HubClients;
import cafe.jeffrey.hub.client.dto.RemoteProjectResponse;
import cafe.jeffrey.hub.client.manager.RemoteInstancesManager;
import cafe.jeffrey.hub.client.manager.RemoteRepositoryManager;
import cafe.jeffrey.hub.client.manager.RepositoryManager;
import cafe.jeffrey.hub.client.manager.TempDirProvider;
import cafe.jeffrey.shared.common.InstantUtils;
import cafe.jeffrey.shared.common.exception.Exceptions;
import cafe.jeffrey.shared.common.model.ProjectInfo;

import java.util.Map;

/**
 * Resolves a (hubId, workspaceId, projectId) tuple to the per-project remote managers
 * (instances / repository / recording-download), built on the shared gRPC client set.
 * Slim analog of microscope's {@code ProjectManagerResolver} — no profile managers.
 */
public class RemoteProjectResolver {

    private final HubsManager remoteServersManager;
    private final TempDirProvider tempDirProvider;
    private final ProjectRecordingsManagerFactory recordingsManagerFactory;

    public RemoteProjectResolver(
            HubsManager remoteServersManager,
            TempDirProvider tempDirProvider,
            ProjectRecordingsManagerFactory recordingsManagerFactory) {

        this.remoteServersManager = remoteServersManager;
        this.tempDirProvider = tempDirProvider;
        this.recordingsManagerFactory = recordingsManagerFactory;
    }

    public RemoteInstancesManager instancesManager(String hubId, String workspaceId, String projectId) {
        ResolvedProject rp = resolve(hubId, workspaceId, projectId);
        return new RemoteInstancesManager(rp.projectInfo(), rp.clients().instances());
    }

    public RepositoryManager repositoryManager(String hubId, String workspaceId, String projectId) {
        ResolvedProject rp = resolve(hubId, workspaceId, projectId);
        return new RemoteRepositoryManager(
                tempDirProvider,
                rp.projectInfo(),
                rp.clients().repository(),
                rp.clients().recordings());
    }

    public RemoteRecordingsDownloadManager recordingsDownloadManager(String hubId, String workspaceId, String projectId) {
        ResolvedProject rp = resolve(hubId, workspaceId, projectId);
        OriginContext origin = new OriginContext(
                rp.server().info().hubId(),
                rp.server().info().name(),
                workspaceId,
                workspaceId,
                projectId,
                rp.projectInfo().name());
        return new RemoteRecordingsDownloadManager(
                tempDirProvider,
                rp.clients().recordings(),
                rp.clients().repository(),
                recordingsManagerFactory.forProject(projectId),
                origin,
                rp.projectInfo().name());
    }

    private ResolvedProject resolve(String hubId, String workspaceId, String projectId) {
        HubManager server = remoteServersManager.findById(hubId)
                .orElseThrow(() -> Exceptions.invalidRequest("Hub not found: " + hubId));
        HubClients clients = server.clients();
        RemoteProjectResponse project = clients.discovery().project(workspaceId, projectId)
                .orElseThrow(() -> Exceptions.projectNotFound(projectId));
        return new ResolvedProject(server, clients, toProjectInfo(project, workspaceId));
    }

    private static ProjectInfo toProjectInfo(RemoteProjectResponse project, String workspaceId) {
        return new ProjectInfo(
                project.id(),
                project.originId() != null ? project.originId() : project.id(),
                project.name(),
                project.label(),
                project.namespace(),
                project.workspaceId() != null ? project.workspaceId() : workspaceId,
                project.createdAt() != null ? InstantUtils.parseInstant(project.createdAt()) : null,
                null,
                Map.of(),
                project.deletedAt());
    }

    private record ResolvedProject(HubManager server, HubClients clients, ProjectInfo projectInfo) {
    }
}
