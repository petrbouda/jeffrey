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

import cafe.jeffrey.hub.model.ProjectInfo;
import cafe.jeffrey.hub.model.ProjectInstanceSessionInfo;

import java.nio.file.Path;

/**
 * Which session is being finished: the project it belongs to, its row, and where its directory
 * is on the volume — the three things every finishing path needs together, and used to carry
 * as three positional parameters beside a repository derivable from the first.
 */
public record SessionRef(ProjectInfo project, ProjectInstanceSessionInfo session, Path path) {

    public SessionRef {
        if (project == null || session == null || path == null) {
            throw new IllegalArgumentException("A session reference names a project, a session and a path");
        }
    }
}
