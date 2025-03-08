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

import pbouda.jeffrey.common.model.profile.ProfileInfo;
import pbouda.jeffrey.common.model.ProjectInfo;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

public class HomeDirs {

    public static final String JEFFREY_DB_FILE = "jeffrey.db";
    private final Path homeDir;
    private final Path projectsDir;

    public HomeDirs(Path homeDir, Path projectsDir) {
        this.homeDir = homeDir;
        this.projectsDir = projectsDir;
    }

    public Path initialize() {
        FileSystemUtils.createDirectories(homeDir);
        FileSystemUtils.createDirectories(projectsDir);
        return homeDir;
    }

    public Path database() {
        return homeDir.resolve(JEFFREY_DB_FILE);
    }

    public ProjectDirs project(String projectId) {
        return new ProjectDirs(projectsDir.resolve(projectId));
    }

    public ProjectDirs project(ProjectInfo projectInfo) {
        return new ProjectDirs(projectsDir.resolve(projectInfo.id()));
    }

    public ProfileDirs profile(ProfileInfo profileInfo) {
        return project(profileInfo.projectId()).profile(profileInfo.id());
    }
}
