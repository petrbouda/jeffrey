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

public class HubJeffreyDirs {

    private static final String TMP_DIR = "temp";

    private final Path homeDir;
    private final Path tempDir;

    public HubJeffreyDirs(Path homeDir) {
        this(homeDir, homeDir.resolve(TMP_DIR));
    }

    public HubJeffreyDirs(Path homeDir, Path tempDir) {
        this.homeDir = homeDir;
        this.tempDir = tempDir;
    }

    public Path initialize() {
        FileSystemUtils.createDirectories(homeDir);
        FileSystemUtils.removeAndCreateDirectories(tempDir);
        return homeDir;
    }

    public Path workspaces() {
        return homeDir.resolve(JeffreyLayout.WORKSPACES_DIR);
    }

    public Path homeDir() {
        return homeDir;
    }

    public Path temp() {
        return tempDir;
    }
}
