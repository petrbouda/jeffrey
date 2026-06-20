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

package cafe.jeffrey.microscope.core.web;

import cafe.jeffrey.microscope.core.manager.project.ProjectManager;
import cafe.jeffrey.microscope.core.manager.project.ProjectsManager;
import cafe.jeffrey.microscope.core.manager.workspace.WorkspaceManager;
import cafe.jeffrey.microscope.core.resources.workspace.Mappers;
import cafe.jeffrey.shared.ui.workspace.bridge.WorkspaceBrowserAccess;
import cafe.jeffrey.shared.ui.workspace.dto.ProjectResponse;
import cafe.jeffrey.shared.ui.workspace.dto.WorkspaceResponse;

import java.util.List;

/**
 * Microscope's {@link WorkspaceBrowserAccess} bridge: resolves a hub's workspaces and projects
 * through the local {@link ProjectManagerResolver} (manager objects backed by the shared gRPC
 * clients) and maps them into the shared response DTOs.
 */
public class MicroscopeWorkspaceBrowserAccess implements WorkspaceBrowserAccess {

    private final ProjectManagerResolver resolver;

    public MicroscopeWorkspaceBrowserAccess(ProjectManagerResolver resolver) {
        this.resolver = resolver;
    }

    @Override
    public List<WorkspaceResponse> workspaces(String hubId) {
        return resolver.resolveServer(hubId).workspaces().stream()
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
        return Mappers.toResponse(resolver.resolveServer(hubId).createWorkspace(referenceId, name));
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
