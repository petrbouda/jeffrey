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
import cafe.jeffrey.local.core.manager.server.RemoteServerManager;
import cafe.jeffrey.local.core.manager.server.RemoteServersManager;
import cafe.jeffrey.local.core.manager.workspace.WorkspaceManager;
import cafe.jeffrey.shared.common.exception.Exceptions;

/**
 * Resolves a (serverId, workspaceId, projectId) tuple to the corresponding
 * {@link ProjectManager}. Used by every workspace/project-scoped controller.
 */
public class ProjectManagerResolver {

    private final RemoteServersManager remoteServersManager;

    public ProjectManagerResolver(RemoteServersManager remoteServersManager) {
        this.remoteServersManager = remoteServersManager;
    }

    public RemoteServerManager resolveServer(String serverId) {
        return remoteServersManager.findById(serverId)
                .orElseThrow(() -> Exceptions.invalidRequest("Remote server not found: " + serverId));
    }

    public WorkspaceManager resolveWorkspace(String serverId, String workspaceId) {
        return resolveServer(serverId).workspace(workspaceId)
                .orElseThrow(() -> Exceptions.workspaceNotFound(workspaceId));
    }

    public ProjectContext resolve(String serverId, String workspaceId, String projectId) {
        WorkspaceManager workspace = resolveWorkspace(serverId, workspaceId);
        ProjectsManager projectsManager = workspace.projectsManager();
        ProjectManager projectManager = projectsManager.project(projectId)
                .orElseThrow(() -> Exceptions.projectNotFound(projectId));
        return new ProjectContext(workspace, projectsManager, projectManager);
    }

    public record ProjectContext(
            WorkspaceManager workspace,
            ProjectsManager projectsManager,
            ProjectManager projectManager) {
    }
}
