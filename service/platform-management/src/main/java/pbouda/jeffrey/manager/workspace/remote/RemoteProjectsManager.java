/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
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

package pbouda.jeffrey.manager.workspace.remote;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pbouda.jeffrey.common.filesystem.JeffreyDirs;
import pbouda.jeffrey.common.model.ProjectInfo;
import pbouda.jeffrey.common.model.workspace.WorkspaceInfo;
import pbouda.jeffrey.profile.manager.model.CreateProject;
import pbouda.jeffrey.manager.project.ProjectManager;
import pbouda.jeffrey.manager.project.ProjectManager.DetailedProjectInfo;
import pbouda.jeffrey.manager.project.ProjectsManager;
import pbouda.jeffrey.manager.project.RemoteProjectManager;
import pbouda.jeffrey.manager.workspace.RemoteMappers;
import pbouda.jeffrey.resources.response.ProjectResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class RemoteProjectsManager implements ProjectsManager {

    private static final Logger LOG = LoggerFactory.getLogger(RemoteProjectsManager.class);

    private final JeffreyDirs jeffreyDirs;
    private final WorkspaceInfo workspaceInfo;
    private final RemoteWorkspaceClient remoteWorkspaceClient;
    private final ProjectsManager commonProjectsManager;

    public RemoteProjectsManager(
            JeffreyDirs jeffreyDirs,
            WorkspaceInfo workspaceInfo,
            RemoteWorkspaceClient remoteWorkspaceClient,
            ProjectsManager commonProjectsManager) {

        this.jeffreyDirs = jeffreyDirs;
        this.workspaceInfo = workspaceInfo;
        this.remoteWorkspaceClient = remoteWorkspaceClient;
        this.commonProjectsManager = commonProjectsManager;
    }

    @Override
    public List<ProjectManager> findAll() {
        List<ProjectResponse> remoteProjects;
        try {
            remoteProjects = remoteWorkspaceClient.allProjects(workspaceInfo.originId());
        } catch (Exception e) {
            LOG.error("Failed to fetch projects from remote workspace: {}", workspaceInfo, e);
            remoteProjects = List.of();
        }

        List<DetailedProjectInfo> liveProjects = commonProjectsManager.findAll().stream()
                .map(ProjectManager::detailedInfo)
                .toList();

        /*
         * First, iterate over remote projects and try to find matching live project (by origin ID).
         */
        List<ProjectManager> result = new ArrayList<>();
        for (ProjectResponse remoteProject : remoteProjects) {
            Optional<ProjectInfo> foundLiveOpt = liveProjects.stream()
                    .map(DetailedProjectInfo::projectInfo)
                    .filter(projectInfo -> projectInfo.originId().equals(remoteProject.id()))
                    .findFirst();

            result.add(toRemoteProjectManager(RemoteMappers.toDetailedProjectInfo(remoteProject, foundLiveOpt)));
        }

        /*
         * Now check if there are any live projects that are not in remote workspace (anymore).
         */
        for (DetailedProjectInfo liveProject : liveProjects) {
            Optional<ProjectResponse> foundRemoteOpt = remoteProjects.stream()
                    .filter(remoteProject -> remoteProject.id().equals(liveProject.projectInfo().originId()))
                    .findFirst();
            if (foundRemoteOpt.isEmpty()) {
                // live project not found in remote, so it's live project that is no longer in remote workspace
                result.add(toRemoteProjectManager(liveProject.orphaned()));
            }
        }

        return result;
    }

    @Override
    public Optional<ProjectManager> project(String projectId) {
        return commonProjectsManager.project(projectId)
                .map(it -> new RemoteProjectManager(
                        jeffreyDirs, workspaceInfo, it.detailedInfo(), Optional.of(it), remoteWorkspaceClient));
    }

    private ProjectManager toRemoteProjectManager(DetailedProjectInfo projectInfo) {
        Optional<ProjectManager> projectOpt = commonProjectsManager.project(projectInfo.projectInfo().id());
        return new RemoteProjectManager(jeffreyDirs, workspaceInfo, projectInfo, projectOpt, remoteWorkspaceClient);
    }

    @Override
    public ProjectManager create(CreateProject createProject) {
        return commonProjectsManager.create(createProject);
    }

    @Override
    public Optional<ProjectManager> findByOriginProjectId(String originProjectId) {
        throw new UnsupportedOperationException("Remote workspace does not support remote repository.");
    }
}
