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
import cafe.jeffrey.hub.core.manager.project.ProjectsManager;
import cafe.jeffrey.hub.core.manager.workspace.WorkspaceManager;
import cafe.jeffrey.hub.core.manager.workspace.WorkspacesManager;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Lookups over one workspace, {@link #WORKSPACE_ID}, holding one project whose repository is the
 * given manager. Any other workspace or project id resolves to nothing.
 */
final class TestLookups {

    static final String WORKSPACE_ID = "ws-1";

    private TestLookups() {
    }

    static GrpcLookups withRepository(String projectId, RepositoryManager repositoryManager) {
        ProjectManager project = mock(ProjectManager.class);
        when(project.repositoryManager()).thenReturn(repositoryManager);
        return withProject(projectId, project);
    }

    static GrpcLookups withProject(String projectId, ProjectManager project) {
        ProjectsManager projects = mock(ProjectsManager.class);
        when(projects.project(anyString())).thenReturn(Optional.empty());
        when(projects.project(projectId)).thenReturn(Optional.of(project));

        WorkspaceManager workspace = mock(WorkspaceManager.class);
        when(workspace.projectsManager()).thenReturn(projects);

        WorkspacesManager workspaces = mock(WorkspacesManager.class);
        when(workspaces.findById(anyString())).thenReturn(Optional.empty());
        when(workspaces.findById(WORKSPACE_ID)).thenReturn(Optional.of(workspace));
        return new GrpcLookups(workspaces);
    }
}
