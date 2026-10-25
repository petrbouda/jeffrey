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
import cafe.jeffrey.microscope.remote.client.RemoteClients;
import cafe.jeffrey.microscope.remote.client.dto.RemoteProjectResponse;
import cafe.jeffrey.microscope.model.remote.RemoteInfo;
import cafe.jeffrey.microscope.model.workspace.WorkspaceInfo;

import java.util.List;
import java.util.Optional;

public class RemoteProjectsManager implements ProjectsManager {

    private static final Logger LOG = LoggerFactory.getLogger(RemoteProjectsManager.class);

    private final RemoteInfo remote;
    private final WorkspaceInfo workspaceInfo;
    private final RemoteClients clients;
    private final RemoteProjectManagerFactory projectManagerFactory;

    public RemoteProjectsManager(
            RemoteInfo remote,
            WorkspaceInfo workspaceInfo,
            RemoteClients clients,
            RemoteProjectManagerFactory projectManagerFactory) {

        this.remote = remote;
        this.workspaceInfo = workspaceInfo;
        this.clients = clients;
        this.projectManagerFactory = projectManagerFactory;
    }

    @Override
    public List<ProjectManager> findAll() {
        List<RemoteProjectResponse> remoteProjects;
        try {
            remoteProjects = clients.discovery().allProjects(workspaceInfo.id());
        } catch (Exception e) {
            LOG.error("Failed to fetch projects from a remote workspace: workspaceId={}", workspaceInfo.id(), e);
            remoteProjects = List.of();
        }

        return remoteProjects.stream()
                .map(this::toProjectManager)
                .toList();
    }

    @Override
    public List<ProjectManager> findAllOrThrow() {
        return clients.discovery().allProjects(workspaceInfo.id()).stream()
                .map(this::toProjectManager)
                .toList();
    }

    @Override
    public Optional<ProjectManager> project(String projectId) {
        Optional<RemoteProjectResponse> remoteProject;
        try {
            remoteProject = clients.discovery().project(workspaceInfo.id(), projectId);
        } catch (Exception e) {
            LOG.error("Failed to fetch project from remote workspace: workspace={} project_id={}",
                    workspaceInfo, projectId, e);
            return Optional.empty();
        }

        return remoteProject.map(this::toProjectManager);
    }

    @Override
    public Optional<ProjectManager> projectOrThrow(String projectId) {
        return clients.discovery().project(workspaceInfo.id(), projectId)
                .map(this::toProjectManager);
    }

    private ProjectManager toProjectManager(RemoteProjectResponse project) {
        return projectManagerFactory.create(remote, clients, project);
    }

    @Override
    public List<String> findAllNamespaces() {
        return List.of();
    }
}
