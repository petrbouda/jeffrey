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

package pbouda.jeffrey.repository;

import pbouda.jeffrey.common.Json;
import pbouda.jeffrey.common.filesystem.FileSystemUtils;
import pbouda.jeffrey.repository.model.RemoteProject;
import pbouda.jeffrey.repository.model.RemoteSession;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class FilesystemRemoteWorkspaceRepository implements RemoteWorkspaceRepository {

    private static final String PROJECT_INFO_FILE = ".project-info.json";
    private static final String SESSION_INFO_FILE = ".session-info.json";

    private final Path workspacePath;

    public FilesystemRemoteWorkspaceRepository(Path workspacePath) {
        if (workspacePath == null) {
            throw new IllegalArgumentException("Workspace path cannot be null");
        }
        this.workspacePath = workspacePath;
    }

    @Override
    public List<RemoteProject> allProjects() {
        if (!FileSystemUtils.isDirectory(workspacePath)) {
            throw new IllegalArgumentException(
                    "Workspace path does not exist or is not a directory: " + workspacePath);
        }

        return FileSystemUtils.allDirectoriesInDirectory(workspacePath).stream()
                .map(path -> {
                    try {
                        String content = Files.readString(path.resolve(PROJECT_INFO_FILE));
                        return Json.read(content, RemoteProject.class);
                    } catch (IOException e) {
                        throw new RuntimeException("Failed to read session info file: " + path, e);
                    }
                })
                .toList();
    }

    @Override
    public List<RemoteSession> allSessions(String projectId) {
        Path projectDir = workspacePath.resolve(projectId);
        if (!FileSystemUtils.isDirectory(projectDir)) {
            throw new IllegalArgumentException("Project directory does not exist: " + projectId);
        }

        return FileSystemUtils.allDirectoriesInDirectory(projectDir).stream()
                .map(path -> {
                    try {
                        String content = Files.readString(path.resolve(SESSION_INFO_FILE));
                        return Json.read(content, RemoteSession.class);
                    } catch (IOException e) {
                        throw new RuntimeException("Failed to read session info file: " + path, e);
                    }
                })
                .toList();
    }
}
