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

package cafe.jeffrey.hub.core;

import cafe.jeffrey.shared.common.JeffreyLayout;
import cafe.jeffrey.shared.common.filesystem.FileSystemUtils;

import java.nio.file.Path;

/**
 * The hub's home directory and the workspaces root under it.
 */
public record HubJeffreyDirs(Path homeDir) {

    public Path initialize() {
        FileSystemUtils.createDirectories(homeDir);
        return homeDir;
    }

    public Path workspaces() {
        return homeDir.resolve(JeffreyLayout.WORKSPACES_DIR);
    }
}
