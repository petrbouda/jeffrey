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

package pbouda.jeffrey.manager.workspace.sandbox;

import pbouda.jeffrey.common.model.workspace.WorkspaceInfo;
import pbouda.jeffrey.common.model.workspace.WorkspaceStatus;
import pbouda.jeffrey.common.model.workspace.WorkspaceType;
import pbouda.jeffrey.manager.project.ProjectManager;
import pbouda.jeffrey.manager.workspace.WorkspaceEventManager;
import pbouda.jeffrey.manager.workspace.WorkspaceManager;
import pbouda.jeffrey.provider.api.repository.WorkspaceRepository;
import pbouda.jeffrey.repository.RemoteWorkspaceRepository;

import java.util.List;

public class LocalWorkspaceManager implements WorkspaceManager {

    private static final String UNSUPPORTED = "Not supported operation in local workspace manager";

    private final WorkspaceInfo workspaceInfo;
    private final WorkspaceRepository workspaceRepository;
    private final ProjectManager.Factory projectManagerFactory;

    public LocalWorkspaceManager(
            WorkspaceInfo workspaceInfo,
            WorkspaceRepository workspaceRepository,
            ProjectManager.Factory projectManagerFactory) {

        this.workspaceInfo = workspaceInfo;
        this.workspaceRepository = workspaceRepository;
        this.projectManagerFactory = projectManagerFactory;
    }

    @Override
    public WorkspaceInfo resolveInfo() {
        return workspaceInfo.withStatus(WorkspaceStatus.AVAILABLE);
    }

    @Override
    public List<? extends ProjectManager> findAllProjects() {
        return workspaceRepository.findAllProjects().stream()
                .map(projectManagerFactory)
                .toList();
    }

    @Override
    public void delete() {
        workspaceRepository.delete();
    }

    @Override
    public WorkspaceType type() {
        return WorkspaceType.REMOTE;
    }

    @Override
    public RemoteWorkspaceRepository remoteWorkspaceRepository() {
        throw new UnsupportedOperationException(UNSUPPORTED);
    }

    @Override
    public WorkspaceEventManager workspaceEventManager() {
        throw new UnsupportedOperationException(UNSUPPORTED);
    }
}
