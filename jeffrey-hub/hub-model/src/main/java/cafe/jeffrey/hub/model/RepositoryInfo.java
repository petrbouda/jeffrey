/*
 * Jeffrey
 * Copyright (C) 2024 Petr Bouda
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
