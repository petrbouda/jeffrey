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

import pbouda.jeffrey.common.filesystem.FileSystemUtils;
import pbouda.jeffrey.common.filesystem.HomeDirs;
import pbouda.jeffrey.common.model.ProjectInfo;
import pbouda.jeffrey.common.model.workspace.WorkspaceInfo;
import pbouda.jeffrey.common.model.workspace.WorkspaceLocation;
import pbouda.jeffrey.manager.project.ProjectManager;
import pbouda.jeffrey.provider.api.repository.WorkspaceRepository;
import pbouda.jeffrey.repository.FilesystemRemoteWorkspaceRepository;
import pbouda.jeffrey.repository.RemoteWorkspaceRepository;

import java.nio.file.Path;
import java.util.List;

public class WorkspaceManagerImpl implements WorkspaceManager {

    private final HomeDirs homeDirs;
    private final WorkspaceInfo workspaceInfo;
    private final WorkspaceRepository workspaceRepository;
    private final ProjectManager.Factory projectManagerFactory;

    public WorkspaceManagerImpl(
            HomeDirs homeDirs,
            WorkspaceInfo workspaceInfo,
            WorkspaceRepository workspaceRepository,
            ProjectManager.Factory projectManagerFactory) {

        this.homeDirs = homeDirs;
        this.workspaceInfo = workspaceInfo;
        this.workspaceRepository = workspaceRepository;
        this.projectManagerFactory = projectManagerFactory;
    }

    @Override
    public WorkspaceInfo info() {
        if (workspaceInfo.isMirrored()) {
            return workspaceInfo;
        } else {
            Path workspaceLocation = workspaceInfo.location().isEmpty()
                    ? homeDirs.workspaces().resolve(workspaceInfo.id())
                    : workspaceInfo.location().toPath();

            return FileSystemUtils.isDirectory(workspaceLocation)
                    ? workspaceInfo.withLocation(WorkspaceLocation.of(workspaceLocation))
                    : workspaceInfo;
        }
    }

    @Override
    public List<? extends ProjectManager> findAllProjects() {
        List<ProjectInfo> projects = workspaceRepository.findAllProjects();
        return projects.stream()
                .map(projectManagerFactory)
                .toList();
    }

    @Override
    public void delete() {
        workspaceRepository.delete();
    }

    @Override
    public RemoteWorkspaceRepository remoteWorkspaceRepository() {
        Path workspacePath = info().location().toPath();
        if (!FileSystemUtils.isDirectory(workspacePath)) {
            throw new IllegalStateException("Workspace path does not exist or is not a directory: " + workspacePath);
        }
        return new FilesystemRemoteWorkspaceRepository(workspacePath);
    }

    @Override
    public WorkspaceEventManager workspaceEventManager() {
        return new WorkspaceEventManagerImpl(workspaceInfo, workspaceRepository);
    }
}
