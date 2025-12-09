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

import pbouda.jeffrey.common.filesystem.JeffreyDirs;
import pbouda.jeffrey.common.model.workspace.WorkspaceInfo;
import pbouda.jeffrey.common.model.workspace.WorkspaceStatus;
import pbouda.jeffrey.common.model.workspace.WorkspaceType;
import pbouda.jeffrey.manager.project.ProjectsManager;
import pbouda.jeffrey.manager.workspace.WorkspaceEventManager;
import pbouda.jeffrey.manager.workspace.WorkspaceManager;
import pbouda.jeffrey.provider.api.repository.WorkspaceRepository;
import pbouda.jeffrey.repository.RemoteWorkspaceRepository;

public class RemoteWorkspaceManager implements WorkspaceManager {

    private final JeffreyDirs jeffreyDirs;
    private final WorkspaceInfo workspaceInfo;
    private final WorkspaceRepository workspaceRepository;
    private final RemoteWorkspaceClient remoteWorkspaceClient;
    private final ProjectsManager.Factory commonProjectsManagerFactory;

    public RemoteWorkspaceManager(
            JeffreyDirs jeffreyDirs,
            WorkspaceInfo workspaceInfo,
            WorkspaceRepository workspaceRepository,
            RemoteWorkspaceClient remoteWorkspaceClient,
            ProjectsManager.Factory commonProjectsManagerFactory) {

        this.jeffreyDirs = jeffreyDirs;
        this.workspaceInfo = workspaceInfo;
        this.workspaceRepository = workspaceRepository;
        this.remoteWorkspaceClient = remoteWorkspaceClient;
        this.commonProjectsManagerFactory = commonProjectsManagerFactory;
    }

    @Override
    public WorkspaceInfo resolveInfo() {
        RemoteWorkspaceClient.WorkspaceResult result = remoteWorkspaceClient.workspace(workspaceInfo.originId());
        return switch (result.status()) {
            case AVAILABLE -> result.info().withId(workspaceInfo.id());
            case UNAVAILABLE -> workspaceInfo.withStatus(WorkspaceStatus.UNAVAILABLE);
            case OFFLINE -> workspaceInfo.withStatus(WorkspaceStatus.OFFLINE);
            case UNKNOWN -> throw new IllegalStateException("Unknown remote workspace status");
        };
    }

    @Override
    public ProjectsManager projectsManager() {
        return new RemoteProjectsManager(
                jeffreyDirs,
                workspaceInfo,
                remoteWorkspaceClient,
                commonProjectsManagerFactory.apply(workspaceInfo));
    }

    @Override
    public WorkspaceType type() {
        return WorkspaceType.REMOTE;
    }

    @Override
    public void delete() {
        workspaceRepository.delete();
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
