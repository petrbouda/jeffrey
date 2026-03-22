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

package pbouda.jeffrey.local.core.manager.workspace;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pbouda.jeffrey.local.core.LocalJeffreyDirs;
import pbouda.jeffrey.local.core.manager.ProfilesManager;
import pbouda.jeffrey.local.core.manager.project.ProjectManager;
import pbouda.jeffrey.local.core.manager.project.ProjectManager.DetailedProjectInfo;
import pbouda.jeffrey.local.core.manager.project.ProjectsManager;
import pbouda.jeffrey.local.core.manager.project.RemoteProjectManager;
import pbouda.jeffrey.local.core.recording.ProjectRecordingInitializer;
import pbouda.jeffrey.local.core.client.RemoteClients;
import pbouda.jeffrey.local.core.client.RemoteMappers;
import pbouda.jeffrey.local.core.resources.response.ProjectResponse;
import pbouda.jeffrey.shared.common.model.workspace.WorkspaceInfo;

import java.util.List;
import java.util.Optional;

public class RemoteProjectsManager implements ProjectsManager {

    private static final Logger LOG = LoggerFactory.getLogger(RemoteProjectsManager.class);

    private final LocalJeffreyDirs jeffreyDirs;
    private final WorkspaceInfo workspaceInfo;
    private final RemoteClients remoteClients;
    private final ProfilesManager.Factory profilesManagerFactory;
    private final ProjectRecordingInitializer.Factory recordingInitializerFactory;

    public RemoteProjectsManager(
            LocalJeffreyDirs jeffreyDirs,
            WorkspaceInfo workspaceInfo,
            RemoteClients remoteClients,
            ProfilesManager.Factory profilesManagerFactory,
            ProjectRecordingInitializer.Factory recordingInitializerFactory) {

        this.jeffreyDirs = jeffreyDirs;
        this.workspaceInfo = workspaceInfo;
        this.remoteClients = remoteClients;
        this.profilesManagerFactory = profilesManagerFactory;
        this.recordingInitializerFactory = recordingInitializerFactory;
    }

    @Override
    public List<ProjectManager> findAll() {
        List<ProjectResponse> remoteProjects;
        try {
            remoteProjects = remoteClients.discovery().allProjects(workspaceInfo.originId());
        } catch (Exception e) {
            LOG.error("Failed to fetch projects from remote workspace: {}", workspaceInfo, e);
            remoteProjects = List.of();
        }

        return remoteProjects.stream()
                .map(remoteProject -> toRemoteProjectManager(
                        RemoteMappers.toDetailedProjectInfo(remoteProject, Optional.empty())))
                .toList();
    }

    @Override
    public Optional<ProjectManager> project(String projectId) {
        // In remote-only mode, we look up the project from the remote server
        List<ProjectResponse> remoteProjects;
        try {
            remoteProjects = remoteClients.discovery().allProjects(workspaceInfo.originId());
        } catch (Exception e) {
            LOG.error("Failed to fetch projects from remote workspace: {}", workspaceInfo, e);
            return Optional.empty();
        }

        return remoteProjects.stream()
                .filter(p -> p.id().equals(projectId))
                .findFirst()
                .map(remoteProject -> toRemoteProjectManager(
                        RemoteMappers.toDetailedProjectInfo(remoteProject, Optional.empty())));
    }

    private ProjectManager toRemoteProjectManager(DetailedProjectInfo projectInfo) {
        return new RemoteProjectManager(
                jeffreyDirs,
                workspaceInfo,
                projectInfo,
                remoteClients,
                profilesManagerFactory,
                recordingInitializerFactory);
    }

    @Override
    public List<String> findAllNamespaces() {
        return List.of();
    }
}
