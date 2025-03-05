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

import pbouda.jeffrey.common.Json;
import pbouda.jeffrey.common.model.profile.ProfileInfo;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;

public class ProfileDirs {

    private final Path currentPath;
    private final Path recordingsPath;
    private final Path infoPath;

    public ProfileDirs(ProjectDirs projectDirs, Path profilePath) {
        this.currentPath = profilePath;
        this.recordingsPath = currentPath.resolve("recordings");
        this.infoPath = currentPath.resolve("info.json");
    }

    public Path initialize() {
        FileSystemUtils.createDirectories(recordingsPath);
        return currentPath;
    }

    public void delete() {
        FileSystemUtils.removeDirectory(currentPath);
    }

    public Optional<ProfileInfo> readInfo() {
        try {
            ProfileInfo profileInfo = Json.read(infoPath, ProfileInfo.class);
            return Optional.of(profileInfo);
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    public Path get() {
        return currentPath;
    }
}
