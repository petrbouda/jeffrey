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

package pbouda.jeffrey.platform.streaming;

import pbouda.jeffrey.shared.common.filesystem.JeffreyDirs;
import pbouda.jeffrey.shared.common.model.ProjectInstanceSessionInfo;
import pbouda.jeffrey.shared.common.model.RepositoryInfo;

import java.nio.file.Path;

/**
 * Utility for resolving session filesystem paths from repository and session info.
 */
public final class SessionPaths {

    private SessionPaths() {
    }

    /**
     * Resolves the full filesystem path for a session directory.
     *
     * @param jeffreyDirs    Jeffrey directory configuration
     * @param repositoryInfo repository info containing workspace and project paths
     * @param sessionInfo    session info containing the relative session path
     * @return the resolved session path
     */
    public static Path resolve(JeffreyDirs jeffreyDirs, RepositoryInfo repositoryInfo, ProjectInstanceSessionInfo sessionInfo) {
        String workspacesPath = repositoryInfo.workspacesPath();
        Path resolvedWorkspacesPath = workspacesPath == null
                ? jeffreyDirs.workspaces()
                : Path.of(workspacesPath);
        return resolvedWorkspacesPath
                .resolve(repositoryInfo.relativeWorkspacePath())
                .resolve(repositoryInfo.relativeProjectPath())
                .resolve(sessionInfo.relativeSessionPath());
    }
}
