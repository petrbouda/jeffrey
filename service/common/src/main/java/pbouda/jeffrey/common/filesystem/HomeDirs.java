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

public class HomeDirs {

    private static final String JEFFREY_DB_FILE = "jeffrey.db";
    private static final String REPOSITORIES_DIR = "repositories";

    private final Path homeDir;

    public HomeDirs(Path homeDir) {
        this.homeDir = homeDir;
    }

    public Path initialize() {
        FileSystemUtils.createDirectories(homeDir);
        return homeDir;
    }

    public Path database() {
        return homeDir.resolve(JEFFREY_DB_FILE);
    }

    public Path repositories() {
        return homeDir.resolve(REPOSITORIES_DIR);
    }
}
