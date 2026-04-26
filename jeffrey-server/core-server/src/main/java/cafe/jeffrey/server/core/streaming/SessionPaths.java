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

package cafe.jeffrey.server.core.streaming;

import cafe.jeffrey.server.core.ServerJeffreyDirs;
import cafe.jeffrey.server.persistence.model.SessionWithRepository;
import cafe.jeffrey.shared.common.model.ProjectInstanceSessionInfo;
import cafe.jeffrey.shared.common.model.RepositoryInfo;

import java.nio.file.Path;

/**
 * Utility for resolving session filesystem paths from repository and session info.
 */
public abstract class SessionPaths {

    public static Path resolveStreamingRepo(ServerJeffreyDirs jeffreyDirs, SessionWithRepository session) {
        Path sessionPath = resolve(jeffreyDirs, session.repositoryInfo(), session.sessionInfo());
        return jeffreyDirs.resolveStreamingRepo(sessionPath);
    }

    public static Path resolve(
            ServerJeffreyDirs jeffreyDirs, RepositoryInfo repositoryInfo, ProjectInstanceSessionInfo sessionInfo) {
        String workspacesPath = repositoryInfo.workspacesPath();
        Path resolvedWorkspacesPath = workspacesPath == null ? jeffreyDirs.workspaces() : Path.of(workspacesPath);

        return resolvedWorkspacesPath
                .resolve(repositoryInfo.relativeWorkspacePath())
                .resolve(repositoryInfo.relativeProjectPath())
                .resolve(sessionInfo.relativeSessionPath());
    }
}
