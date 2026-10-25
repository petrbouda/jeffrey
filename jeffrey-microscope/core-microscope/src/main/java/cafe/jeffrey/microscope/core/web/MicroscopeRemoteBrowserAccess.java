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
import cafe.jeffrey.microscope.core.manager.workspace.WorkspaceManager;
import cafe.jeffrey.microscope.core.web.dto.workspace.Mappers;
import cafe.jeffrey.shared.ui.remote.bridge.RemoteBrowserAccess;
import cafe.jeffrey.shared.ui.remote.dto.ProjectResponse;
import cafe.jeffrey.shared.ui.remote.dto.WorkspaceResponse;

import java.util.List;

/**
 * Microscope's {@link RemoteBrowserAccess} bridge: resolves the remote's workspaces and projects
 * through the local {@link ProjectManagerResolver} (manager objects backed by the remote's
 * clients) and maps them into the shared response DTOs.
 */
public class MicroscopeRemoteBrowserAccess implements RemoteBrowserAccess {

    private final ProjectManagerResolver resolver;

    public MicroscopeRemoteBrowserAccess(ProjectManagerResolver resolver) {
        this.resolver = resolver;
    }

    @Override
    public List<WorkspaceResponse> workspaces() {
        return resolver.resolveRemote().workspaces().stream()
                .map(Mappers::toResponse)
                .toList();
    }

    @Override
    public WorkspaceResponse workspace(String workspaceId) {
        WorkspaceManager workspace = resolver.resolveWorkspace(workspaceId);
        return Mappers.toResponse(workspace.resolveInfo());
    }

    @Override
    public WorkspaceResponse createWorkspace(String referenceId, String name) {
        return Mappers.toResponse(resolver.resolveRemote().createWorkspace(referenceId, name));
    }

    @Override
    public void deleteWorkspace(String workspaceId) {
        resolver.resolveWorkspace(workspaceId).delete();
    }

    @Override
    public List<ProjectResponse> projects(String workspaceId) {
        return resolver.resolveWorkspace(workspaceId).projectsManager().findAll().stream()
                .map(ProjectManager::detailedInfo)
                .map(Mappers::toProjectResponse)
                .toList();
    }

    @Override
    public ProjectResponse project(String workspaceId, String projectId) {
        ProjectManager pm = resolver.resolve(workspaceId, projectId);
        return Mappers.toProjectResponse(pm.detailedInfo());
    }
}
