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

package pbouda.jeffrey.filesystem;

import pbouda.jeffrey.common.Json;
import pbouda.jeffrey.common.Recording;
import pbouda.jeffrey.repository.model.ProfileInfo;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class ProfileDirs {

    private final ProjectDirs projectDirs;
    private final Path currentPath;
    private final Path exportsPath;
    private final Path recordingsPath;
    private final Path databasePath;
    private final Path infoPath;

    public ProfileDirs(ProjectDirs projectDirs, Path profilePath) {
        this.projectDirs = projectDirs;
        this.currentPath = profilePath;
        this.exportsPath = currentPath.resolve("exports");
        this.recordingsPath = currentPath.resolve("recordings");
        this.databasePath = currentPath.resolve("profile.db");
        this.infoPath = currentPath.resolve("info.json");
    }

    public Path initialize() {
        FilesystemUtils.createDirectories(exportsPath);
        FilesystemUtils.createDirectories(recordingsPath);
        return currentPath;
    }

    public void delete() {
        FilesystemUtils.removeDirectory(currentPath);
    }

    public Path uploadRecording(String filename, InputStream stream) {
        return FilesystemUtils.upload(recordingsPath, filename, stream);
    }

    public void deleteRecording(String filename) {
        FilesystemUtils.delete(recordingsPath.resolve(filename));
    }

    public Path saveInfo(ProfileInfo content) {
        try {
            return Files.writeString(infoPath, Json.toPrettyString(content));
        } catch (IOException e) {
            throw new RuntimeException("Cannot create a Profile Info file: " + infoPath, e);
        }
    }

    public ProfileInfo readInfo() {
        return Json.read(infoPath, ProfileInfo.class);
    }

    public Path exportsDir() {
        return exportsPath;
    }

    public Path recordingsDir() {
        return recordingsPath;
    }

    public List<Recording> allRecordings() {
        return RecordingUtils.all(recordingsPath);
    }

    public Path database() {
        return databasePath;
    }

    public Path get() {
        return currentPath;
    }
}
