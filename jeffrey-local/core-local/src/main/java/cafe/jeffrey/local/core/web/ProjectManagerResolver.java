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

package cafe.jeffrey.local.core.web;

import cafe.jeffrey.local.core.manager.project.ProjectManager;
import cafe.jeffrey.local.core.manager.project.ProjectsManager;
import cafe.jeffrey.local.core.manager.workspace.WorkspaceManager;
import cafe.jeffrey.local.core.manager.workspace.WorkspacesManager;
import cafe.jeffrey.shared.common.exception.Exceptions;

/**
 * Resolves a (workspaceId, projectId) pair to the corresponding
 * {@link ProjectManager}. Replaces the JAX-RS sub-resource locator chain
 * (Workspace → WorkspaceProjects → Project) with a single explicit lookup
 * used by every project-scoped Spring controller.
 */
public class ProjectManagerResolver {

    private final WorkspacesManager workspacesManager;

    public ProjectManagerResolver(WorkspacesManager workspacesManager) {
        this.workspacesManager = workspacesManager;
    }

    public ProjectContext resolve(String workspaceId, String projectId) {
        WorkspaceManager workspace = workspacesManager.findById(workspaceId)
                .orElseThrow(() -> Exceptions.workspaceNotFound(workspaceId));
        ProjectsManager projectsManager = workspace.projectsManager();
        ProjectManager projectManager = projectsManager.project(projectId)
                .orElseThrow(() -> Exceptions.projectNotFound(projectId));
        return new ProjectContext(workspace, projectsManager, projectManager);
    }

    public WorkspaceManager resolveWorkspace(String workspaceId) {
        return workspacesManager.findById(workspaceId)
                .orElseThrow(() -> Exceptions.workspaceNotFound(workspaceId));
    }

    public record ProjectContext(
            WorkspaceManager workspace,
            ProjectsManager projectsManager,
            ProjectManager projectManager) {
    }
}
