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

package cafe.jeffrey.hub.core.volume;

import cafe.jeffrey.hub.model.workspace.WorkspaceInfo;

import java.nio.file.Path;

/**
 * A workspace directory on the volume and what is known of it.
 *
 * @param path the directory, named by the workspace's id
 */
public record WorkspaceDirectory(Path path, WorkspaceInfo info) {

    public WorkspaceDirectory {
        if (path == null || info == null) {
            throw new IllegalArgumentException("A workspace directory needs a path and its workspace");
        }
    }
}
