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

package cafe.jeffrey.hub.core.project.session;

import cafe.jeffrey.hub.core.HubJeffreyDirs;
import cafe.jeffrey.hub.model.ProjectInstanceSessionInfo;
import cafe.jeffrey.hub.model.RepositoryInfo;

import java.nio.file.Path;

/**
 * The one rule for where a repository's directories are on the volume: a workspaces root — the
 * repository's own when it names one, the hub's otherwise — then the workspace's directory,
 * the project's, and a session's under that. Written once here because it was written twice,
 * and two copies of a path rule are how a job and the storage end up looking in different places.
 */
public final class SessionPaths {

    private SessionPaths() {
    }

    public static Path resolve(
            HubJeffreyDirs jeffreyDirs, RepositoryInfo repositoryInfo, ProjectInstanceSessionInfo sessionInfo) {
        return session(jeffreyDirs.workspaces(), repositoryInfo, sessionInfo);
    }

    public static Path workspace(Path defaultWorkspacesDir, RepositoryInfo repositoryInfo) {
        String workspacesPath = repositoryInfo.workspacesPath();
        Path workspacesDir = workspacesPath == null
                ? defaultWorkspacesDir
                : defaultWorkspacesDir.getFileSystem().getPath(workspacesPath);
        return workspacesDir.resolve(repositoryInfo.relativeWorkspacePath());
    }

    public static Path project(Path defaultWorkspacesDir, RepositoryInfo repositoryInfo) {
        return workspace(defaultWorkspacesDir, repositoryInfo).resolve(repositoryInfo.relativeProjectPath());
    }

    public static Path session(Path defaultWorkspacesDir, RepositoryInfo repositoryInfo, ProjectInstanceSessionInfo sessionInfo) {
        return project(defaultWorkspacesDir, repositoryInfo).resolve(sessionInfo.relativeSessionPath());
    }
}
