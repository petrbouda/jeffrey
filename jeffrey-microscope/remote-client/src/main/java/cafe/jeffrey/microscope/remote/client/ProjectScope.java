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

package cafe.jeffrey.microscope.remote.client;

import cafe.jeffrey.microscope.model.ProjectInfo;

import java.util.Objects;

/**
 * The hub project a call is about. The hub keeps no index of its volume, so every request below
 * the workspace names both the workspace and the project it reads.
 */
public record ProjectScope(String workspaceId, String projectId) {

    public ProjectScope {
        Objects.requireNonNull(workspaceId, "workspaceId");
        Objects.requireNonNull(projectId, "projectId");
    }

    public static ProjectScope of(ProjectInfo project) {
        return new ProjectScope(project.workspaceId(), project.id());
    }
}
