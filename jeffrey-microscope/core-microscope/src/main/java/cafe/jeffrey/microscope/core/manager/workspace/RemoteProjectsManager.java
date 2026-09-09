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
import cafe.jeffrey.recordings.core.OriginContext;
import cafe.jeffrey.hub.client.HubClients;
import cafe.jeffrey.microscope.core.client.RemoteMappers;
import cafe.jeffrey.hub.client.dto.RemoteProjectResponse;
import cafe.jeffrey.shared.common.model.hub.HubInfo;
import cafe.jeffrey.shared.common.model.workspace.WorkspaceInfo;

import java.util.List;
import java.util.Optional;

public class RemoteProjectsManager implements ProjectsManager {

    private static final Logger LOG = LoggerFactory.getLogger(RemoteProjectsManager.class);

    private final MicroscopeJeffreyDirs jeffreyDirs;
    private final HubInfo hubInfo;
    private final WorkspaceInfo workspaceInfo;
    private final HubClients hubClients;
    private final ProfilesManager.Factory profilesManagerFactory;
    private final RecordingsManager recordingsManager;

    public RemoteProjectsManager(
            MicroscopeJeffreyDirs jeffreyDirs,
            HubInfo hubInfo,
            WorkspaceInfo workspaceInfo,
            HubClients hubClients,
            ProfilesManager.Factory profilesManagerFactory,
            RecordingsManager recordingsManager) {

        this.jeffreyDirs = jeffreyDirs;
        this.hubInfo = hubInfo;
        this.workspaceInfo = workspaceInfo;
        this.hubClients = hubClients;
        this.profilesManagerFactory = profilesManagerFactory;
        this.recordingsManager = recordingsManager;
    }

    @Override
    public List<ProjectManager> findAll() {
        List<RemoteProjectResponse> remoteProjects;
        try {
            remoteProjects = hubClients.discovery().allProjects(workspaceInfo.id(), false);
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
            remoteProjects = hubClients.discovery().allProjects(workspaceInfo.id(), true);
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
        // In remote-only mode, we look up the single project from the hub.
        // The hub returns deleted projects as well so restore/management lookups work.
        Optional<RemoteProjectResponse> remoteProject;
        try {
            remoteProject = hubClients.discovery().project(workspaceInfo.id(), projectId);
        } catch (Exception e) {
            LOG.error("Failed to fetch project from remote workspace: workspace={} project_id={}",
                    workspaceInfo, projectId, e);
            return Optional.empty();
        }

        return remoteProject.map(project -> toRemoteProjectManager(
                RemoteMappers.toDetailedProjectInfo(project)));
    }

    private ProjectManager toRemoteProjectManager(DetailedProjectInfo projectInfo) {
        OriginContext originContext = new OriginContext(
                hubInfo.hubId(),
                hubInfo.name(),
                workspaceInfo.id(),
                workspaceInfo.referenceId(),
                projectInfo.projectInfo().id(),
                projectInfo.projectInfo().name());

        return new RemoteProjectManager(
                jeffreyDirs,
                projectInfo,
                hubClients,
                profilesManagerFactory,
                recordingsManager,
                originContext);
    }

    @Override
    public List<String> findAllNamespaces() {
        return List.of();
    }
}
