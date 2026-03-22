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

package pbouda.jeffrey.local.core;

import pbouda.jeffrey.shared.common.filesystem.FileSystemUtils;
import pbouda.jeffrey.shared.common.filesystem.TempDirFactory;
import pbouda.jeffrey.shared.common.filesystem.TempDirectory;

import java.nio.file.Path;

public class LocalJeffreyDirs implements TempDirFactory {

    private static final String JEFFREY_DB_FILE = "jeffrey.db";
    private static final String WORKSPACES_DIR = "workspaces";
    private static final String PROFILES_DIR = "profiles";
    private static final String TMP_DIR = "tmp";
    private static final String HEAP_DUMP_ANALYSIS_DIR = "heap-dump-analysis";
    private static final String QUICK_PROFILES_DIR = "quick-profiles";
    private static final String QUICK_RECORDINGS_DIR = "quick-recordings";

    private final Path homeDir;
    private final Path tempDir;

    public LocalJeffreyDirs(Path homeDir) {
        this(homeDir, homeDir.resolve(TMP_DIR));
    }

    public LocalJeffreyDirs(Path homeDir, Path tempDir) {
        this.homeDir = homeDir;
        this.tempDir = tempDir;
    }

    public Path initialize() {
        FileSystemUtils.createDirectories(homeDir);
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

    public Path profileDir(String profileId) {
        return profiles().resolve(profileId);
    }

    public Path heapDumpAnalysisDir(String profileId) {
        return profileDir(profileId).resolve(HEAP_DUMP_ANALYSIS_DIR);
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

    public Path quickProfiles() {
        return homeDir.resolve(QUICK_PROFILES_DIR);
    }

    public Path quickProfileDir(String profileId) {
        return quickProfiles().resolve(profileId);
    }

    public Path quickHeapDumpAnalysisDir(String profileId) {
        return quickProfileDir(profileId).resolve(HEAP_DUMP_ANALYSIS_DIR);
    }

    public Path quickRecordings() {
        return homeDir.resolve(QUICK_RECORDINGS_DIR);
    }
}
