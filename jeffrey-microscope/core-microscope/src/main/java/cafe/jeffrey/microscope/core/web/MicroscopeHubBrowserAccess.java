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

package cafe.jeffrey.microscope.core.web;

import cafe.jeffrey.microscope.core.manager.project.ProjectManager;
import cafe.jeffrey.microscope.core.manager.project.ProjectsManager;
import cafe.jeffrey.microscope.core.manager.workspace.WorkspaceManager;
import cafe.jeffrey.microscope.core.web.dto.workspace.Mappers;
import cafe.jeffrey.shared.ui.hub.bridge.HubBrowserAccess;
import cafe.jeffrey.shared.ui.hub.dto.ProjectResponse;
import cafe.jeffrey.shared.ui.hub.dto.WorkspaceResponse;

import java.util.List;

/**
 * Microscope's {@link HubBrowserAccess} bridge: resolves a hub's workspaces and projects
 * through the local {@link ProjectManagerResolver} (manager objects backed by the shared gRPC
 * clients) and maps them into the shared response DTOs.
 */
public class MicroscopeHubBrowserAccess implements HubBrowserAccess {

    private final ProjectManagerResolver resolver;

    public MicroscopeHubBrowserAccess(ProjectManagerResolver resolver) {
        this.resolver = resolver;
    }

    @Override
    public List<WorkspaceResponse> workspaces(String hubId) {
        return resolver.resolveHub(hubId).workspaces().stream()
                .map(Mappers::toResponse)
                .toList();
    }

    @Override
    public WorkspaceResponse workspace(String hubId, String workspaceId) {
        WorkspaceManager workspace = resolver.resolveWorkspace(hubId, workspaceId);
        return Mappers.toResponse(workspace.resolveInfo());
    }

    @Override
    public WorkspaceResponse createWorkspace(String hubId, String referenceId, String name) {
        return Mappers.toResponse(resolver.resolveHub(hubId).createWorkspace(referenceId, name));
    }

    @Override
    public void deleteWorkspace(String hubId, String workspaceId) {
        resolver.resolveWorkspace(hubId, workspaceId).delete();
    }

    @Override
    public List<ProjectResponse> projects(String hubId, String workspaceId, boolean includeDeleted) {
        ProjectsManager projectsManager = resolver.resolveWorkspace(hubId, workspaceId).projectsManager();
        var managers = includeDeleted
                ? projectsManager.findAllIncludingDeleted()
                : projectsManager.findAll();
        return managers.stream()
                .map(ProjectManager::detailedInfo)
                .map(Mappers::toProjectResponse)
                .toList();
    }

    @Override
    public ProjectResponse project(String hubId, String workspaceId, String projectId) {
        ProjectManager pm = resolver.resolve(hubId, workspaceId, projectId).projectManager();
        return Mappers.toProjectResponse(pm.detailedInfo());
    }
}
