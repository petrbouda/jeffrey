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
import cafe.jeffrey.microscope.core.manager.project.ConnectorProjectManagerFactory;
import cafe.jeffrey.microscope.connector.api.ConnectorClients;
import cafe.jeffrey.microscope.connector.api.dto.ConnectorProjectResponse;
import cafe.jeffrey.microscope.model.connector.ConnectorInfo;
import cafe.jeffrey.microscope.model.workspace.WorkspaceInfo;

import java.util.List;
import java.util.Optional;

public class ConnectorProjectsManager implements ProjectsManager {

    private static final Logger LOG = LoggerFactory.getLogger(ConnectorProjectsManager.class);

    private final ConnectorInfo connector;
    private final WorkspaceInfo workspaceInfo;
    private final ConnectorClients clients;
    private final ConnectorProjectManagerFactory projectManagerFactory;

    public ConnectorProjectsManager(
            ConnectorInfo connector,
            WorkspaceInfo workspaceInfo,
            ConnectorClients clients,
            ConnectorProjectManagerFactory projectManagerFactory) {

        this.connector = connector;
        this.workspaceInfo = workspaceInfo;
        this.clients = clients;
        this.projectManagerFactory = projectManagerFactory;
    }

    @Override
    public List<ProjectManager> findAll() {
        List<ConnectorProjectResponse> connectorProjects;
        try {
            connectorProjects = clients.discovery().allProjects(workspaceInfo.id());
        } catch (Exception e) {
            LOG.error("Failed to fetch projects from a connector workspace: workspaceId={}", workspaceInfo.id(), e);
            connectorProjects = List.of();
        }

        return connectorProjects.stream()
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
        Optional<ConnectorProjectResponse> connectorProject;
        try {
            connectorProject = clients.discovery().project(workspaceInfo.id(), projectId);
        } catch (Exception e) {
            LOG.error("Failed to fetch project from connector workspace: workspace={} project_id={}",
                    workspaceInfo, projectId, e);
            return Optional.empty();
        }

        return connectorProject.map(this::toProjectManager);
    }

    @Override
    public Optional<ProjectManager> projectOrThrow(String projectId) {
        return clients.discovery().project(workspaceInfo.id(), projectId)
                .map(this::toProjectManager);
    }

    private ProjectManager toProjectManager(ConnectorProjectResponse project) {
        return projectManagerFactory.create(connector, clients, project);
    }

    @Override
    public List<String> findAllNamespaces() {
        return List.of();
    }
}
