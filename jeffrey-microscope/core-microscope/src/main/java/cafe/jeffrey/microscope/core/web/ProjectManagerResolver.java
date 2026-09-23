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
import cafe.jeffrey.microscope.core.manager.hub.HubManager;
import cafe.jeffrey.microscope.core.manager.hub.HubsManager;
import cafe.jeffrey.microscope.core.manager.workspace.WorkspaceManager;
import cafe.jeffrey.shared.common.exception.Exceptions;

/**
 * Resolves a (hubId, workspaceId, projectId) tuple to the corresponding
 * {@link ProjectManager}. Used by every workspace/project-scoped controller.
 */
public class ProjectManagerResolver {

    private final HubsManager hubsManager;

    public ProjectManagerResolver(HubsManager hubsManager) {
        this.hubsManager = hubsManager;
    }

    public HubManager resolveHub(String hubId) {
        return hubsManager.findById(hubId)
                .orElseThrow(() -> Exceptions.invalidRequest("Hub not found: " + hubId));
    }

    public WorkspaceManager resolveWorkspace(String hubId, String workspaceId) {
        return resolveHub(hubId).workspace(workspaceId)
                .orElseThrow(() -> Exceptions.workspaceNotFound(workspaceId));
    }

    public ProjectContext resolve(String hubId, String workspaceId, String projectId) {
        WorkspaceManager workspace = resolveWorkspace(hubId, workspaceId);
        ProjectsManager projectsManager = workspace.projectsManager();
        ProjectManager projectManager = projectsManager.project(projectId)
                .orElseThrow(() -> Exceptions.projectNotFound(projectId));
        return new ProjectContext(workspace, projectsManager, projectManager);
    }

    /**
     * Resolves a remote project without hiding an unavailable hub behind a not-found result.
     */
    public ProjectContext resolveStrict(String hubId, String workspaceId, String projectId) {
        WorkspaceManager workspace = resolveHub(hubId).workspaceOrThrow(workspaceId)
                .orElseThrow(() -> Exceptions.workspaceNotFound(workspaceId));
        ProjectsManager projectsManager = workspace.projectsManager();
        ProjectManager projectManager = projectsManager.projectOrThrow(projectId)
                .orElseThrow(() -> Exceptions.projectNotFound(projectId));
        return new ProjectContext(workspace, projectsManager, projectManager);
    }

    public record ProjectContext(
            WorkspaceManager workspace,
            ProjectsManager projectsManager,
            ProjectManager projectManager) {
    }
}
