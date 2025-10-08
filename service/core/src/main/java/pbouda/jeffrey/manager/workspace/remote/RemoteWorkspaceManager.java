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

package pbouda.jeffrey.manager.workspace.remote;

import pbouda.jeffrey.common.model.workspace.WorkspaceInfo;
import pbouda.jeffrey.common.model.workspace.WorkspaceStatus;
import pbouda.jeffrey.common.model.workspace.WorkspaceType;
import pbouda.jeffrey.manager.project.ProjectsManager;
import pbouda.jeffrey.manager.workspace.WorkspaceEventManager;
import pbouda.jeffrey.manager.workspace.WorkspaceManager;
import pbouda.jeffrey.provider.api.repository.ProjectsRepository;
import pbouda.jeffrey.repository.RemoteWorkspaceRepository;

public class RemoteWorkspaceManager implements WorkspaceManager {

    private final WorkspaceInfo workspaceInfo;
    private final RemoteWorkspaceClient remoteWorkspaceClient;
    private final ProjectsManager.Factory commonProjectsManagerFactory;
    private final ProjectsRepository projectsRepository;

    public RemoteWorkspaceManager(
            WorkspaceInfo workspaceInfo,
            RemoteWorkspaceClient remoteWorkspaceClient,
            ProjectsManager.Factory commonProjectsManagerFactory,
            ProjectsRepository projectsRepository) {

        this.workspaceInfo = workspaceInfo;
        this.remoteWorkspaceClient = remoteWorkspaceClient;
        this.commonProjectsManagerFactory = commonProjectsManagerFactory;
        this.projectsRepository = projectsRepository;
    }

    @Override
    public WorkspaceInfo resolveInfo() {
        RemoteWorkspaceClient.WorkspaceResult result = remoteWorkspaceClient.workspace(workspaceInfo.id());
        return switch (result.status()) {
            case AVAILABLE -> result.info();
            case UNAVAILABLE -> workspaceInfo.withStatus(WorkspaceStatus.UNAVAILABLE);
            case OFFLINE -> workspaceInfo.withStatus(WorkspaceStatus.OFFLINE);
            case UNKNOWN -> throw new IllegalStateException("Unknown remote workspace status");
        };
    }

    @Override
    public ProjectsManager projectsManager() {
        return new RemoteProjectsManager(
                workspaceInfo,
                remoteWorkspaceClient,
                commonProjectsManagerFactory.apply(workspaceInfo),
                projectsRepository);
    }

    @Override
    public WorkspaceType type() {
        return WorkspaceType.REMOTE;
    }

    @Override
    public void delete() {
        throw new UnsupportedOperationException("Remote workspace cannot be deleted.");
    }

    @Override
    public RemoteWorkspaceRepository remoteWorkspaceRepository() {
        throw new UnsupportedOperationException("Remote workspace does not support remote repository.");
    }

    @Override
    public WorkspaceEventManager workspaceEventManager() {
        throw new UnsupportedOperationException("Remote workspace does not support workspace events.");
    }
}
