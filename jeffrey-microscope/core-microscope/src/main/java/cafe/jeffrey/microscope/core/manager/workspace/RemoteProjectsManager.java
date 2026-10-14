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

package cafe.jeffrey.microscope.core.manager.workspace;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cafe.jeffrey.microscope.core.manager.project.ProjectManager;
import cafe.jeffrey.microscope.core.manager.project.ProjectsManager;
import cafe.jeffrey.microscope.core.manager.project.RemoteProjectManagerFactory;
import cafe.jeffrey.hub.client.HubClients;
import cafe.jeffrey.hub.client.dto.RemoteProjectResponse;
import cafe.jeffrey.microscope.model.hub.HubInfo;
import cafe.jeffrey.microscope.model.workspace.WorkspaceInfo;

import java.util.List;
import java.util.Optional;

public class RemoteProjectsManager implements ProjectsManager {

    private static final Logger LOG = LoggerFactory.getLogger(RemoteProjectsManager.class);

    private final HubInfo hubInfo;
    private final WorkspaceInfo workspaceInfo;
    private final HubClients hubClients;
    private final RemoteProjectManagerFactory projectManagerFactory;

    public RemoteProjectsManager(
            HubInfo hubInfo,
            WorkspaceInfo workspaceInfo,
            HubClients hubClients,
            RemoteProjectManagerFactory projectManagerFactory) {

        this.hubInfo = hubInfo;
        this.workspaceInfo = workspaceInfo;
        this.hubClients = hubClients;
        this.projectManagerFactory = projectManagerFactory;
    }

    @Override
    public List<ProjectManager> findAll() {
        List<RemoteProjectResponse> remoteProjects;
        try {
            remoteProjects = hubClients.discovery().allProjects(workspaceInfo.id());
        } catch (Exception e) {
            LOG.error("Failed to fetch projects from remote workspace: {}", workspaceInfo, e);
            remoteProjects = List.of();
        }

        return remoteProjects.stream()
                .map(this::toProjectManager)
                .toList();
    }

    @Override
    public List<ProjectManager> findAllOrThrow() {
        return hubClients.discovery().allProjects(workspaceInfo.id()).stream()
                .map(this::toProjectManager)
                .toList();
    }

    @Override
    public Optional<ProjectManager> project(String projectId) {
        // In remote-only mode, we look up the single project from the hub.
        Optional<RemoteProjectResponse> remoteProject;
        try {
            remoteProject = hubClients.discovery().project(workspaceInfo.id(), projectId);
        } catch (Exception e) {
            LOG.error("Failed to fetch project from remote workspace: workspace={} project_id={}",
                    workspaceInfo, projectId, e);
            return Optional.empty();
        }

        return remoteProject.map(this::toProjectManager);
    }

    @Override
    public Optional<ProjectManager> projectOrThrow(String projectId) {
        return hubClients.discovery().project(workspaceInfo.id(), projectId)
                .map(this::toProjectManager);
    }

    private ProjectManager toProjectManager(RemoteProjectResponse project) {
        return projectManagerFactory.create(hubInfo, hubClients, project);
    }

    @Override
    public List<String> findAllNamespaces() {
        return List.of();
    }
}
