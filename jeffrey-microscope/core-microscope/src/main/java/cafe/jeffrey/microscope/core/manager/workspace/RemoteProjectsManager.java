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

package cafe.jeffrey.microscope.core.manager.workspace;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cafe.jeffrey.microscope.core.MicroscopeJeffreyDirs;
import cafe.jeffrey.microscope.core.manager.ProfilesManager;
import cafe.jeffrey.microscope.core.manager.project.ProjectManager;
import cafe.jeffrey.microscope.core.manager.project.ProjectManager.DetailedProjectInfo;
import cafe.jeffrey.microscope.core.manager.project.ProjectsManager;
import cafe.jeffrey.microscope.core.manager.project.RemoteProjectManager;
import cafe.jeffrey.microscope.core.manager.recordings.RecordingsManager;
import cafe.jeffrey.microscope.core.recording.ProjectRecordingInitializer;
import cafe.jeffrey.microscope.core.client.RemoteClients;
import cafe.jeffrey.microscope.core.client.RemoteMappers;
import cafe.jeffrey.microscope.core.resources.response.RemoteProjectResponse;
import cafe.jeffrey.microscope.persistence.api.MicroscopeCoreRepositories;
import cafe.jeffrey.microscope.persistence.api.RemoteServerInfo;
import cafe.jeffrey.shared.common.model.workspace.WorkspaceInfo;

import java.util.List;
import java.util.Optional;

public class RemoteProjectsManager implements ProjectsManager {

    private static final Logger LOG = LoggerFactory.getLogger(RemoteProjectsManager.class);

    private final MicroscopeJeffreyDirs jeffreyDirs;
    private final RemoteServerInfo serverInfo;
    private final WorkspaceInfo workspaceInfo;
    private final RemoteClients remoteClients;
    private final ProfilesManager.Factory profilesManagerFactory;
    private final RecordingsManager recordingsManager;

    public RemoteProjectsManager(
            MicroscopeJeffreyDirs jeffreyDirs,
            RemoteServerInfo serverInfo,
            WorkspaceInfo workspaceInfo,
            RemoteClients remoteClients,
            ProfilesManager.Factory profilesManagerFactory,
            RecordingsManager recordingsManager) {

        this.jeffreyDirs = jeffreyDirs;
        this.serverInfo = serverInfo;
        this.workspaceInfo = workspaceInfo;
        this.remoteClients = remoteClients;
        this.profilesManagerFactory = profilesManagerFactory;
        this.recordingsManager = recordingsManager;
    }

    @Override
    public List<ProjectManager> findAll() {
        List<RemoteProjectResponse> remoteProjects;
        try {
            remoteProjects = remoteClients.discovery().allProjects(workspaceInfo.id(), false);
        } catch (Exception e) {
            LOG.error("Failed to fetch projects from remote workspace: {}", workspaceInfo, e);
            remoteProjects = List.of();
        }

        return remoteProjects.stream()
                .map(remoteProject -> toRemoteProjectManager(
                        RemoteMappers.toDetailedProjectInfo(remoteProject)))
                .toList();
    }

    @Override
    public List<ProjectManager> findAllIncludingDeleted() {
        List<RemoteProjectResponse> remoteProjects;
        try {
            remoteProjects = remoteClients.discovery().allProjects(workspaceInfo.id(), true);
        } catch (Exception e) {
            LOG.error("Failed to fetch projects from remote workspace: {}", workspaceInfo, e);
            remoteProjects = List.of();
        }

        return remoteProjects.stream()
                .map(remoteProject -> toRemoteProjectManager(
                        RemoteMappers.toDetailedProjectInfo(remoteProject)))
                .toList();
    }

    @Override
    public Optional<ProjectManager> project(String projectId) {
        // In remote-only mode, we look up the project from the remote server
        // Include deleted projects so restore/management lookups work
        List<RemoteProjectResponse> remoteProjects;
        try {
            remoteProjects = remoteClients.discovery().allProjects(workspaceInfo.id(), true);
        } catch (Exception e) {
            LOG.error("Failed to fetch projects from remote workspace: {}", workspaceInfo, e);
            return Optional.empty();
        }

        return remoteProjects.stream()
                .filter(p -> p.id().equals(projectId))
                .findFirst()
                .map(remoteProject -> toRemoteProjectManager(
                        RemoteMappers.toDetailedProjectInfo(remoteProject)));
    }

    private ProjectManager toRemoteProjectManager(DetailedProjectInfo projectInfo) {
        OriginContext originContext = new OriginContext(
                serverInfo.serverId(),
                serverInfo.name(),
                workspaceInfo.id(),
                workspaceInfo.referenceId(),
                projectInfo.projectInfo().id(),
                projectInfo.projectInfo().name());

        return new RemoteProjectManager(
                jeffreyDirs,
                projectInfo,
                remoteClients,
                profilesManagerFactory,
                recordingsManager,
                originContext);
    }

    @Override
    public List<String> findAllNamespaces() {
        return List.of();
    }
}
