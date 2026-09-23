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
