/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
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

package pbouda.jeffrey.server.core;

import pbouda.jeffrey.shared.common.filesystem.FileSystemUtils;
import pbouda.jeffrey.shared.common.filesystem.TempDirFactory;
import pbouda.jeffrey.shared.common.filesystem.TempDirectory;

import java.nio.file.Path;

public class ServerJeffreyDirs implements TempDirFactory {

    private static final String WORKSPACES_DIR = "workspaces";
    private static final String TMP_DIR = "tmp";
    private static final String LIBS_DIR = "libs";
    private static final String EVENTS_DIR = ".events";
    private static final String STREAMING_REPO_DIR = "streaming-repo";

    private final Path homeDir;
    private final Path tempDir;

    public ServerJeffreyDirs(Path homeDir) {
        this(homeDir, homeDir.resolve(TMP_DIR));
    }

    public ServerJeffreyDirs(Path homeDir, Path tempDir) {
        this.homeDir = homeDir;
        this.tempDir = tempDir;
    }

    public Path initialize() {
        FileSystemUtils.createDirectories(homeDir);
        FileSystemUtils.removeAndCreateDirectories(tempDir);
        return homeDir;
    }

    public Path resolveStreamingRepo(Path sessionPath) {
        return sessionPath.resolve(STREAMING_REPO_DIR);
    }

    public Path workspaces() {
        return homeDir.resolve(WORKSPACES_DIR);
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

    public Path libs() {
        return homeDir.resolve(LIBS_DIR);
    }

    public Path workspaceEvents() {
        return workspaces().resolve(EVENTS_DIR);
    }
}
