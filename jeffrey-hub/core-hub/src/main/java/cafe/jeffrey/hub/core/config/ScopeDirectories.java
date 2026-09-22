/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
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


package cafe.jeffrey.hub.core.config;

import cafe.jeffrey.hub.core.HubJeffreyDirs;
import cafe.jeffrey.hub.core.manager.RepositoryManager;
import cafe.jeffrey.hub.core.manager.project.ProjectManager;
import cafe.jeffrey.hub.core.manager.workspace.WorkspaceManager;
import cafe.jeffrey.hub.core.manager.workspace.WorkspacesManager;
import cafe.jeffrey.hub.core.project.session.SessionPaths;
import cafe.jeffrey.hub.model.RepositoryInfo;
import cafe.jeffrey.hub.model.config.ScopedConfigKey;
import cafe.jeffrey.shared.common.filesystem.FileSystemUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.Optional;

/**
 * Where each scope's configuration file belongs on the shared volume.
 *
 * <p>A workspace's folder is created when it is missing, deliberately. Its path is derived, not
 * discovered, and it is the same path the provisioner would create; creating it here is what lets
 * a workspace's very first JVM already find a file, instead of running on defaults because nothing
 * had happened in that workspace yet.</p>
 *
 * <p>A project's folder is never created. A project exists in the hub only because the provisioner
 * declared it by writing into its directory, so a missing directory means the project is gone
 * rather than new, and creating it would resurrect a shell of something that was deleted.</p>
 */
public class ScopeDirectories {

    private static final Logger LOG = LoggerFactory.getLogger(ScopeDirectories.class);

    private final HubJeffreyDirs jeffreyDirs;
    private final WorkspacesManager workspacesManager;

    public ScopeDirectories(HubJeffreyDirs jeffreyDirs, WorkspacesManager workspacesManager) {
        this.jeffreyDirs = jeffreyDirs;
        this.workspacesManager = workspacesManager;
    }

    /** Empty when the scope has no folder to write into, which is never an error here. */
    public Optional<Path> resolve(ScopedConfigKey key) {
        return switch (key.scope()) {
            case GLOBAL -> Optional.of(jeffreyDirs.workspaces());
            case WORKSPACE -> workspaceDirectory(key.workspaceId());
            case PROJECT -> projectDirectory(key.workspaceId(), key.projectId());
        };
    }

    private Optional<Path> workspaceDirectory(String workspaceId) {
        return workspacesManager.findById(workspaceId)
                .map(WorkspaceManager::resolveInfo)
                .map(info -> FileSystemUtils.createDirectories(info.location().toPath()));
    }

    private Optional<Path> projectDirectory(String workspaceId, String projectId) {
        Optional<Path> directory = workspacesManager.findById(workspaceId)
                .flatMap(workspace -> workspace.projectsManager().project(projectId))
                .map(ProjectManager::repositoryManager)
                .flatMap(RepositoryManager::info)
                .map(this::projectPath)
                .filter(FileSystemUtils::isDirectory);

        if (directory.isEmpty()) {
            LOG.debug("No project directory to publish into: workspace_id={} project_id={}",
                    workspaceId, projectId);
        }
        return directory;
    }

    private Path projectPath(RepositoryInfo repositoryInfo) {
        return SessionPaths.project(jeffreyDirs.workspaces(), repositoryInfo);
    }
}
