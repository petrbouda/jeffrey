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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

public class MicroscopeJeffreyDirs implements TempDirFactory {

    private static final Logger LOG = LoggerFactory.getLogger(MicroscopeJeffreyDirs.class);

    private static final String JEFFREY_DB_FILE = "jeffrey.db";
    private static final String WORKSPACES_DIR = "workspaces";
    private static final String PROFILES_DIR = "profiles";
    private static final String RECORDINGS_DIR = "recordings";
    private static final String FILES_DIR = "files";

    /**
     * What {@link #FILES_DIR} was called before fetched files were files rather than artifacts.
     * The path of a fetched file is its only record — there is no catalogue — so a directory
     * left under the old name would hold files nothing could find again, and every one of them
     * would be fetched a second time. Renamed on start-up, once, home-wide and under each profile.
     */
    private static final String LEGACY_FILES_DIR = "artifacts";
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
        adoptLegacyFilesDirectories();
        FileSystemUtils.createDirectories(files());
        FileSystemUtils.removeAndCreateDirectories(tempDir);
        return homeDir;
    }

    /**
     * Moves every {@code artifacts} directory an earlier build left — the home-wide one and one
     * under each profile — to its {@code files} name, so what was fetched before stays found.
     * A {@code files} directory already there wins: the old one is left as it is rather than
     * merged, and a later start-up finds nothing to do.
     */
    private void adoptLegacyFilesDirectories() {
        adoptLegacyFilesDirectory(homeDir);
        try (Stream<Path> profileDirs = Files.list(profiles())) {
            profileDirs.filter(Files::isDirectory).forEach(this::adoptLegacyFilesDirectory);
        } catch (IOException e) {
            LOG.warn("Cannot look for fetched-file directories under an earlier name: profiles_dir={} reason={}",
                    profiles(), e.getMessage());
        }
    }

    private void adoptLegacyFilesDirectory(Path parent) {
        Path legacy = parent.resolve(LEGACY_FILES_DIR);
        Path current = parent.resolve(FILES_DIR);
        if (!Files.isDirectory(legacy) || Files.exists(current)) {
            return;
        }
        try {
            Files.move(legacy, current);
            LOG.info("Adopted a fetched-file directory under its earlier name: from={} to={}", legacy, current);
        } catch (IOException e) {
            LOG.warn("Cannot adopt a fetched-file directory under its earlier name: from={} to={} reason={}",
                    legacy, current, e.getMessage());
        }
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
     * Where files fetched one at a time from a hub land — the logs, crash files and single chunks a
     * reader asks for without pulling the whole session — as {@code <hub>/<project>/<session>/<name>}.
     * Plain files at a deterministic path, catalogued nowhere: whether one was fetched is whether it
     * is there.
     */
    public Path files() {
        return homeDir.resolve(FILES_DIR);
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
