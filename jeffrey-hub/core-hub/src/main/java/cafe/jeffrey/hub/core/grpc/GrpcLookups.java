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

package cafe.jeffrey.hub.core.grpc;

import cafe.jeffrey.hub.core.manager.RepositoryManager;
import cafe.jeffrey.hub.core.manager.project.ProjectManager;
import cafe.jeffrey.hub.core.manager.workspace.WorkspaceManager;
import cafe.jeffrey.hub.core.manager.workspace.WorkspacesManager;

/**
 * Resolves the workspace and project a request names, throwing {@code NOT_FOUND} when either is
 * not on the volume, so every service reports a missing target the same way.
 */
public class GrpcLookups {

    private final WorkspacesManager workspacesManager;

    public GrpcLookups(WorkspacesManager workspacesManager) {
        this.workspacesManager = workspacesManager;
    }

    public WorkspaceManager workspace(String workspaceId) {
        return workspacesManager.findById(workspaceId)
                .orElseThrow(() -> GrpcExceptions.notFound("Workspace not found: " + workspaceId));
    }

    public ProjectManager project(String workspaceId, String projectId) {
        return workspace(workspaceId).projectsManager().project(projectId)
                .orElseThrow(() -> GrpcExceptions.notFound("Project not found: " + projectId));
    }

    public RepositoryManager repositoryManager(String workspaceId, String projectId) {
        return project(workspaceId, projectId).repositoryManager();
    }
}
