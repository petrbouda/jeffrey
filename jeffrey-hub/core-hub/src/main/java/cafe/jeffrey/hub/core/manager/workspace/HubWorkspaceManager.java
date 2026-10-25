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

import cafe.jeffrey.hub.core.manager.project.ProjectsManager;
import cafe.jeffrey.hub.core.volume.WorkspaceDirectory;
import cafe.jeffrey.hub.model.workspace.WorkspaceInfo;
import cafe.jeffrey.shared.common.filesystem.FileSystemUtils;
import cafe.jeffrey.shared.volume.FilesystemVolume;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HubWorkspaceManager implements WorkspaceManager {

    private static final Logger LOG = LoggerFactory.getLogger(HubWorkspaceManager.class);

    private final WorkspaceDirectory workspace;
    private final FilesystemVolume volume;
    private final ProjectsManager.Factory projectsManagerFactory;

    /**
     * @param volume the filesystem the workspace lies on, where deleting it happens
     */
    public HubWorkspaceManager(
            WorkspaceDirectory workspace, FilesystemVolume volume, ProjectsManager.Factory projectsManagerFactory) {

        this.workspace = workspace;
        this.volume = volume;
        this.projectsManagerFactory = projectsManagerFactory;
    }

    @Override
    public WorkspaceInfo info() {
        return workspace.info();
    }

    @Override
    public ProjectsManager projectsManager() {
        return projectsManagerFactory.apply(workspace);
    }

    @Override
    public void delete() {
        FileSystemUtils.removeDirectory(volume.resolve(workspace.path()));
        LOG.info("Deleted workspace: workspace_id={} path={}", workspace.id(), workspace.path());
    }
}
