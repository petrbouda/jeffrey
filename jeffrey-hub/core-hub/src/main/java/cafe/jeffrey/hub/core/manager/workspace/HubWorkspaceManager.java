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

package cafe.jeffrey.hub.core.manager.workspace;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cafe.jeffrey.hub.core.manager.project.ProjectManager;
import cafe.jeffrey.hub.core.manager.project.ProjectsManager;
import cafe.jeffrey.hub.persistence.api.WorkspaceRepository;
import cafe.jeffrey.shared.common.filesystem.FileSystemUtils;
import cafe.jeffrey.hub.core.HubJeffreyDirs;
import cafe.jeffrey.hub.model.workspace.WorkspaceInfo;
import cafe.jeffrey.hub.model.workspace.WorkspaceLocation;
import cafe.jeffrey.hub.model.workspace.WorkspaceStatus;

import java.nio.file.Path;
import java.time.Clock;

public class HubWorkspaceManager implements WorkspaceManager {

    private static final Logger LOG = LoggerFactory.getLogger(HubWorkspaceManager.class);

    private final Clock clock;
    private final HubJeffreyDirs jeffreyDirs;
    private final WorkspaceInfo workspaceInfo;
    private final WorkspaceRepository workspaceRepository;
    private final ProjectsManager.Factory projectsManagerFactory;

    public HubWorkspaceManager(
            Clock clock,
            HubJeffreyDirs jeffreyDirs,
            WorkspaceInfo workspaceInfo,
            WorkspaceRepository workspaceRepository,
            ProjectsManager.Factory projectsManagerFactory) {

        this.clock = clock;
        this.jeffreyDirs = jeffreyDirs;
        this.workspaceInfo = workspaceInfo;
        this.workspaceRepository = workspaceRepository;
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
        if (workspaceInfo.location() == null) {
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

    /**
     * Deletes every project the way a project is deleted on its own — rows, then the project's
     * directory tree — and only then the workspace row. The directory is what makes a deletion
     * final: the reconciler re-creates any project whose on-disk declaration still exists, and
     * a workspace deleted rows-only came back, project by project, on the next tick.
     */
    @Override
    public void delete() {
        for (ProjectManager project : projectsManager().findAll()) {
            project.delete();
        }
        workspaceRepository.delete();
        LOG.info("Deleted workspace: workspace_id={}", workspaceInfo.id());
    }
}
