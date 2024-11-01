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
import pbouda.jeffrey.common.model.ProfileInfo;
import pbouda.jeffrey.common.model.ProjectInfo;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

public class ProjectDirs {

    private final Path currentPath;
    private final Path recordingsPath;
    private final Path profilesPath;
    private final Path databasePath;
    private final Path infoPath;

    public ProjectDirs(Path projectPath) {
        this.currentPath = projectPath;
        this.recordingsPath = currentPath.resolve("recordings");
        this.profilesPath = currentPath.resolve("profiles");
        this.databasePath = currentPath.resolve("project.db");
        this.infoPath = currentPath.resolve("info.json");
    }

    public Path initialize(ProjectInfo projectInfo) {
        FileSystemUtils.createDirectories(currentPath);
        FileSystemUtils.createDirectories(recordingsPath);
        FileSystemUtils.createDirectories(profilesPath);
        saveInfo(projectInfo);
        return currentPath;
    }

    public void saveInfo(ProjectInfo content) {
        try {
            Files.writeString(infoPath, Json.toPrettyString(content));
        } catch (IOException e) {
            throw new RuntimeException("Cannot create a Project Info file: " + infoPath, e);
        }
    }

    public ProjectInfo readInfo() {
        return Json.read(infoPath, ProjectInfo.class);
    }

    public void delete() {
        FileSystemUtils.removeDirectory(currentPath);
    }

    public Path recordingsDir() {
        return recordingsPath;
    }

    public Path profilesDir() {
        return profilesPath;
    }

    public Path database() {
        return databasePath;
    }

    public ProfileDirs profile(ProfileInfo profileInfo) {
        return profile(profileInfo.id());
    }

    public ProfileDirs profile(String profileId) {
        return new ProfileDirs(this, profilesPath.resolve(profileId));
    }

    public List<ProfileInfo> allProfiles() {
        try (Stream<Path> paths = Files.list(profilesPath)) {
            return paths.filter(Files::isDirectory)
                    .map(p -> p.getFileName().toString())
                    .map(profileId -> profile(profileId).readInfo())
                    .toList();
        } catch (IOException e) {
            throw new RuntimeException("Cannot read all profiles", e);
        }
    }

    public Path get() {
        return currentPath;
    }
}
