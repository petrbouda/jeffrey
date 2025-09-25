/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
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

package pbouda.jeffrey.manager.workspace;

import pbouda.jeffrey.common.model.ProjectInfo;
import pbouda.jeffrey.common.model.workspace.WorkspaceInfo;
import pbouda.jeffrey.manager.project.ProjectManager;
import pbouda.jeffrey.provider.api.repository.ProjectsRepository;
import pbouda.jeffrey.repository.RemoteWorkspaceRepository;

import java.util.List;

public class LocalWorkspaceManager implements WorkspaceManager {

    public static final String LOCAL_WORKSPACE_ID = "$local";

    private static final RuntimeException UNSUPPORTED_EXCEPTION =
            new UnsupportedOperationException("Not supported operation in local workspace manager");

    private final WorkspaceInfo workspaceInfo;
    private final ProjectsRepository projectsRepository;
    private final ProjectManager.Factory projectManagerFactory;

    public LocalWorkspaceManager(
            WorkspaceInfo workspaceInfo,
            ProjectsRepository projectsRepository,
            ProjectManager.Factory projectManagerFactory) {

        this.workspaceInfo = workspaceInfo;
        this.projectsRepository = projectsRepository;
        this.projectManagerFactory = projectManagerFactory;
    }

    public static WorkspaceInfo localWorkspaceInfo(List<ProjectInfo> projects) {
        return new WorkspaceInfo(
                LOCAL_WORKSPACE_ID,
                null,
                "Local Workspace",
                "A workspace for local projects",
                null,
                null,
                false,
                null,
                false,
                projects.size());
    }

    @Override
    public WorkspaceInfo info() {
        return workspaceInfo;
    }

    @Override
    public List<? extends ProjectManager> findAllProjects() {
        return projectsRepository.findAll(null).stream()
                .map(projectManagerFactory)
                .toList();
    }

    @Override
    public void delete() {
        throw UNSUPPORTED_EXCEPTION;
    }

    @Override
    public RemoteWorkspaceRepository remoteWorkspaceRepository() {
        throw UNSUPPORTED_EXCEPTION;
    }

    @Override
    public WorkspaceEventManager workspaceEventManager() {
        throw UNSUPPORTED_EXCEPTION;
    }
}
