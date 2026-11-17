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
package cafe.jeffrey.hub.core.project;

import cafe.jeffrey.hub.core.project.repository.RepositoryStorage;
import cafe.jeffrey.hub.model.ProjectInfo;

/**
 * One project directory on the volume, as the hub's jobs work on it: which project it is and its
 * repository. The hub only keeps the volume in shape — no analysis, no downloads of its own.
 */
public record HubProject(ProjectInfo info, RepositoryStorage repositoryStorage) {

    public HubProject {
        if (info == null || repositoryStorage == null) {
            throw new IllegalArgumentException("A project needs its info and its repository");
        }
    }

    /** The project as structured log keys, e.g. {@code workspace_id=ws-1 project_id=orders}. */
    public String logScope() {
        return "workspace_id=" + info.workspaceId() + " project_id=" + info.id();
    }
}
