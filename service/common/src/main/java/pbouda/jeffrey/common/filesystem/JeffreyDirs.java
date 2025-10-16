/*
 * Jeffrey
 * Copyright (C) 2024 Petr Bouda
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

package pbouda.jeffrey.common.filesystem;

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

    private final Path homeDir;
    private final Path tempDir;

    public JeffreyDirs(Path homeDir, Path tempDir) {
        this.homeDir = homeDir;
        this.tempDir = tempDir;
    }

    public Path initialize() {
        FileSystemUtils.createDirectories(homeDir);
        FileSystemUtils.createDirectories(tempDir);
        return homeDir;
    }

    public Path database() {
        return homeDir.resolve(JEFFREY_DB_FILE);
    }

    public Path workspaces() {
        return homeDir.resolve(WORKSPACES_DIR);
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
}
