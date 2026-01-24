/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
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

package pbouda.jeffrey.shared.common.filesystem;

import java.nio.file.Path;

public class JeffreyDirs {

    public record Directory(Path path) implements AutoCloseable {
        public Directory {
            FileSystemUtils.createDirectories(path);
        }

        public Path path() {
            return path;
        }

        public Path resolve(String other) {
            return path.resolve(other);
        }

        @Override
        public void close() {
            FileSystemUtils.removeDirectory(path);
        }
    }

    private static final String JEFFREY_DB_FILE = "jeffrey.db";
    private static final String WORKSPACES_DIR = "workspaces";
    private static final String PROFILES_DIR = "profiles";
    private static final String QUICK_PROFILES_DIR = "quick-profiles";
    private static final String QUICK_RECORDINGS_DIR = "quick-recordings";
    private static final String HEAP_DUMP_ANALYSIS_DIR = "heap-dump-analysis";

    private final Path homeDir;
    private final Path tempDir;

    public JeffreyDirs(Path homeDir, Path tempDir) {
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

    /**
     * Returns the heap dump analysis directory for a specific profile.
     *
     * @param profileId the profile ID
     * @return path to the heap dump analysis directory
     */
    public Path heapDumpAnalysisDir(String profileId) {
        return profileDir(profileId).resolve(HEAP_DUMP_ANALYSIS_DIR);
    }

    public Path homeDir() {
        return homeDir;
    }

    public Path temp() {
        return tempDir;
    }

    public Directory newTempDir() {
        return newTempDir(System.nanoTime() + "");
    }

    public Directory newTempDir(String directory) {
        return new Directory(tempDir.resolve(directory));
    }

    /**
     * Returns the directory for quick analysis profiles.
     * These profiles are stored in the temp directory and are automatically
     * cleaned up when the application restarts.
     *
     * @return path to the quick profiles directory
     */
    public Path quickProfiles() {
        return tempDir.resolve(QUICK_PROFILES_DIR);
    }

    /**
     * Returns the directory for a specific quick analysis profile.
     *
     * @param profileId the profile ID
     * @return path to the quick profile directory
     */
    public Path quickProfileDir(String profileId) {
        return quickProfiles().resolve(profileId);
    }

    /**
     * Returns the heap dump analysis directory for a specific quick analysis profile.
     *
     * @param profileId the profile ID
     * @return path to the heap dump analysis directory
     */
    public Path quickHeapDumpAnalysisDir(String profileId) {
        return quickProfileDir(profileId).resolve(HEAP_DUMP_ANALYSIS_DIR);
    }

    /**
     * Returns the directory for quick analysis recordings.
     * These recordings are stored in the temp directory and are automatically
     * cleaned up when the application restarts.
     *
     * @return path to the quick recordings directory
     */
    public Path quickRecordings() {
        return tempDir.resolve(QUICK_RECORDINGS_DIR);
    }
}
