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
