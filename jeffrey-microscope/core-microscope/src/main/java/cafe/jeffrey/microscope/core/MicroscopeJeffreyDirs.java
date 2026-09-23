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

package cafe.jeffrey.microscope.core;

import cafe.jeffrey.shared.common.filesystem.FileSystemUtils;
import cafe.jeffrey.shared.common.filesystem.TempDirFactory;
import cafe.jeffrey.shared.common.filesystem.TempDirectory;

import java.nio.file.Path;

public class MicroscopeJeffreyDirs implements TempDirFactory {

    private static final String JEFFREY_DB_FILE = "jeffrey.db";
    private static final String WORKSPACES_DIR = "workspaces";
    private static final String PROFILES_DIR = "profiles";
    private static final String RECORDINGS_DIR = "recordings";
    private static final String ARTIFACTS_DIR = "artifacts";
    public static final String HEAP_DUMP_ANALYSIS_DIR = "heap-dump";
    private static final String TMP_DIR = "tmp";
    private final Path homeDir;
    private final Path tempDir;

    public MicroscopeJeffreyDirs(Path homeDir) {
        this(homeDir, homeDir.resolve(TMP_DIR));
    }

    public MicroscopeJeffreyDirs(Path homeDir, Path tempDir) {
        this.homeDir = homeDir;
        this.tempDir = tempDir;
    }

    public Path initialize() {
        FileSystemUtils.createDirectories(homeDir);
        FileSystemUtils.createDirectories(profiles());
        FileSystemUtils.createDirectories(recordings());
        FileSystemUtils.createDirectories(artifacts());
        FileSystemUtils.removeAndCreateDirectories(tempDir);
        return homeDir;
    }

    public Path database() {
        return homeDir.resolve(JEFFREY_DB_FILE);
    }

    public Path workspaces() {
        return homeDir.resolve(WORKSPACES_DIR);
    }

    public Path profiles() {
        return homeDir.resolve(PROFILES_DIR);
    }

    public Path recordings() {
        return homeDir.resolve(RECORDINGS_DIR);
    }

    /**
     * Where artifacts fetched one at a time from a hub land — the logs and crash files a reader asks
     * for without pulling the whole session — as {@code <hub>/<project>/<session>/<name>}. Plain files
     * at a deterministic path, catalogued nowhere: whether one was fetched is whether it is there.
     */
    public Path artifacts() {
        return homeDir.resolve(ARTIFACTS_DIR);
    }

    public Path profileDir(String profileId) {
        return profiles().resolve(profileId);
    }

    public Path homeDir() {
        return homeDir;
    }

    public Path temp() {
        return tempDir;
    }

    @Override
    public TempDirectory newTempDir() {
        return newTempDir(System.nanoTime() + "");
    }

    @Override
    public TempDirectory newTempDir(String directory) {
        return new TempDirectory(tempDir.resolve(directory));
    }

}
