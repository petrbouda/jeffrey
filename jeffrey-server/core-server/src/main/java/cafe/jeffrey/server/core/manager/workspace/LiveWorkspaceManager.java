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

package cafe.jeffrey.server.core.manager.workspace;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cafe.jeffrey.server.core.manager.project.ProjectManager;
import cafe.jeffrey.server.core.manager.project.ProjectsManager;
import cafe.jeffrey.server.core.repository.FilesystemRemoteWorkspaceRepository;
import cafe.jeffrey.server.core.repository.RemoteWorkspaceRepository;
import cafe.jeffrey.server.persistence.api.ServerPlatformRepositories;
import cafe.jeffrey.server.persistence.api.WorkspaceRepository;
import cafe.jeffrey.shared.common.filesystem.FileSystemUtils;
import cafe.jeffrey.server.core.ServerJeffreyDirs;
import cafe.jeffrey.shared.common.model.workspace.WorkspaceInfo;
import cafe.jeffrey.shared.common.model.workspace.WorkspaceLocation;
import cafe.jeffrey.shared.common.model.workspace.WorkspaceStatus;

import java.nio.file.Path;
import java.time.Clock;

public class LiveWorkspaceManager implements WorkspaceManager {

    private static final Logger LOG = LoggerFactory.getLogger(LiveWorkspaceManager.class);

    private final Clock clock;
    private final ServerJeffreyDirs jeffreyDirs;
    private final WorkspaceInfo workspaceInfo;
    private final WorkspaceRepository workspaceRepository;
    private final ServerPlatformRepositories platformRepositories;
    private final ProjectsManager.Factory projectsManagerFactory;

    public LiveWorkspaceManager(
            Clock clock,
            ServerJeffreyDirs jeffreyDirs,
            WorkspaceInfo workspaceInfo,
            WorkspaceRepository workspaceRepository,
            ServerPlatformRepositories platformRepositories,
            ProjectsManager.Factory projectsManagerFactory) {

        this.clock = clock;
        this.jeffreyDirs = jeffreyDirs;
        this.workspaceInfo = workspaceInfo;
        this.workspaceRepository = workspaceRepository;
        this.platformRepositories = platformRepositories;
        this.projectsManagerFactory = projectsManagerFactory;
    }

    @Override
    public WorkspaceInfo localInfo() {
        return workspaceInfo;
    }

    @Override
    public WorkspaceInfo resolveInfo() {
        /*
         * For regular workspaces, if the location is not set, we default it to a path under the home directory.
         * - this ensures the possibility to download workspaces without location set and copy them to local home dir.
         * - these workspaces are automatically created
         */
        WorkspaceInfo workspaceInfo = this.workspaceInfo;
        if (workspaceInfo.location() == null || workspaceInfo.location().isEmpty()) {
            Path location = jeffreyDirs.workspaces().resolve(workspaceInfo.repositoryId());
            workspaceInfo = workspaceInfo.withLocation(WorkspaceLocation.of(location));
        }

        WorkspaceStatus workspaceStatus = FileSystemUtils.isDirectory(workspaceInfo.location().toPath())
                ? WorkspaceStatus.AVAILABLE
                : WorkspaceStatus.UNAVAILABLE;

        return workspaceInfo.withStatus(workspaceStatus);
    }

    @Override
    public ProjectsManager projectsManager() {
        return projectsManagerFactory.apply(workspaceInfo);
    }

    @Override
    public void delete() {
        for (ProjectManager project : projectsManager().findAll()) {
            platformRepositories.newProjectRepository(project.info().id()).delete();
        }
        workspaceRepository.delete();
        LOG.info("Deleted workspace: workspaceId={}", workspaceInfo.id());
    }

    @Override
    public RemoteWorkspaceRepository remoteWorkspaceRepository() {
        Path workspacePath = resolveInfo().location().toPath();
        if (!FileSystemUtils.isDirectory(workspacePath)) {
            throw new IllegalStateException("Workspace path does not exist or is not a directory: " + workspacePath);
        }
        return new FilesystemRemoteWorkspaceRepository(clock, workspacePath);
    }
}
