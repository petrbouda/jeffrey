/*
 * Jeffrey
 * Copyright (C) 2024 Petr Bouda
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

package cafe.jeffrey.hub.model;

import cafe.jeffrey.shared.common.model.RepositoryType;

/**
 * Where a project's recordings live: an optional workspaces root ({@code null} means the hub's
 * own), the workspace's directory under it and the project's under that. Both relative parts
 * come off a marker file another pod wrote, so they are held to {@link RelativePath}.
 */
public record RepositoryInfo(
        String id,
        RepositoryType repositoryType,
        String workspacesPath,
        String relativeWorkspacePath,
        String relativeProjectPath) {

    public RepositoryInfo {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Repository id must not be blank");
        }
        RelativePath.require(relativeWorkspacePath, "relativeWorkspacePath");
        RelativePath.require(relativeProjectPath, "relativeProjectPath");
    }
}
