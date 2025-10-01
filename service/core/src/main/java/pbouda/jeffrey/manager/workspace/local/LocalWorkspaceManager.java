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

package pbouda.jeffrey.manager.workspace.local;

import pbouda.jeffrey.common.filesystem.FileSystemUtils;
import pbouda.jeffrey.common.filesystem.HomeDirs;
import pbouda.jeffrey.common.model.workspace.WorkspaceInfo;
import pbouda.jeffrey.common.model.workspace.WorkspaceLocation;
import pbouda.jeffrey.common.model.workspace.WorkspaceStatus;
import pbouda.jeffrey.common.model.workspace.WorkspaceType;
import pbouda.jeffrey.manager.project.ProjectManager;
import pbouda.jeffrey.manager.project.ProjectsManager;
import pbouda.jeffrey.manager.workspace.WorkspaceEventManager;
import pbouda.jeffrey.manager.workspace.WorkspaceManager;
import pbouda.jeffrey.provider.api.repository.WorkspaceRepository;
import pbouda.jeffrey.repository.FilesystemRemoteWorkspaceRepository;
import pbouda.jeffrey.repository.RemoteWorkspaceRepository;

import java.nio.file.Path;
import java.util.List;

public class LocalWorkspaceManager implements WorkspaceManager {

    private final HomeDirs homeDirs;
    private final WorkspaceInfo workspaceInfo;
    private final WorkspaceRepository workspaceRepository;
    private final ProjectsManager.Factory projectsManagerFactory;

    public LocalWorkspaceManager(
            HomeDirs homeDirs,
            WorkspaceInfo workspaceInfo,
            WorkspaceRepository workspaceRepository,
            ProjectsManager.Factory projectsManagerFactory) {

        this.homeDirs = homeDirs;
        this.workspaceInfo = workspaceInfo;
        this.workspaceRepository = workspaceRepository;
        this.projectsManagerFactory = projectsManagerFactory;
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
            Path location = homeDirs.workspaces().resolve(workspaceInfo.repositoryId());
            workspaceInfo = workspaceInfo.withLocation(WorkspaceLocation.of(location));
        }

        WorkspaceStatus workspaceStatus = FileSystemUtils.isDirectory(workspaceInfo.location().toPath())
                ? WorkspaceStatus.AVAILABLE
                : WorkspaceStatus.UNAVAILABLE;

        return workspaceInfo.withStatus(workspaceStatus);
    }

    @Override
    public List<? extends ProjectManager> findAllProjects() {
        return workspaceRepository.findAllProjects().stream()
                .map(projectManagerFactory)
                .toList();
    }

    @Override
    public ProjectsManager projectsManager() {
        return projectsManagerFactory.apply(workspaceInfo);
    }

    @Override
    public void delete() {
        workspaceRepository.delete();
    }

    @Override
    public WorkspaceType type() {
        return WorkspaceType.LOCAL;
    }

    @Override
    public RemoteWorkspaceRepository remoteWorkspaceRepository() {
        Path workspacePath = resolveInfo().location().toPath();
        if (!FileSystemUtils.isDirectory(workspacePath)) {
            throw new IllegalStateException("Workspace path does not exist or is not a directory: " + workspacePath);
        }
        return new FilesystemRemoteWorkspaceRepository(workspacePath);
    }

    @Override
    public WorkspaceEventManager workspaceEventManager() {
        return new LocalWorkspaceEventManager(workspaceInfo, workspaceRepository);
    }
}
