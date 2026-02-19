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

package pbouda.jeffrey.platform.repository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pbouda.jeffrey.shared.common.Json;
import pbouda.jeffrey.shared.common.filesystem.FileSystemUtils;
import pbouda.jeffrey.shared.common.model.repository.RemoteProject;
import pbouda.jeffrey.shared.common.model.repository.RemoteProjectInstance;
import pbouda.jeffrey.shared.common.model.repository.RemoteProjectInstanceSession;
import pbouda.jeffrey.shared.common.model.repository.RemoteWorkspaceSettings;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import static java.nio.file.StandardOpenOption.CREATE_NEW;

public class FilesystemRemoteWorkspaceRepository implements RemoteWorkspaceRepository {

    private static final Logger LOG = LoggerFactory.getLogger(FilesystemRemoteWorkspaceRepository.class);

    private static final DateTimeFormatter TIMESTAMP_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HHmmssSSSSSS").withZone(ZoneOffset.UTC);

    private static final Comparator<Path> TIMESTAMP_FILE_COMPARATOR =
            Comparator.comparing((Path path) -> {
                String filename = path.getFileName().toString();
                String substring = filename.substring(filename.indexOf('-') + 1, filename.lastIndexOf('.'));
                return Instant.from(TIMESTAMP_FORMATTER.parse(substring));
            }).reversed();

    private static final String PROJECT_INFO_FILE = ".project-info.json";
    private static final String INSTANCE_INFO_FILE = ".instance-info.json";
    private static final String SESSION_INFO_FILE = ".session-info.json";
    private static final String WORKSPACE_SETTINGS_PREFIX = "settings-";
    private static final String WORKSPACE_SETTINGS_FILE_PATTERN = WORKSPACE_SETTINGS_PREFIX + "<<timestamp>>.json";
    private static final String WORKSPACE_SETTINGS_DIR = ".settings";

    private final Path workspacePath;
    private final Clock clock;

    public FilesystemRemoteWorkspaceRepository(Clock clock, Path workspacePath) {
        this.clock = clock;
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
                .map(path -> path.resolve(PROJECT_INFO_FILE))
                .filter(FilesystemRemoteWorkspaceRepository::hasInfoFile)
                .map(p -> readInfoFile(p, RemoteProject.class))
                .toList();
    }

    @Override
    public List<RemoteProjectInstance> allInstances(RemoteProject project) {
        Path projectDir = workspacePath.resolve(project.projectName());
        if (!FileSystemUtils.isDirectory(projectDir)) {
            throw new IllegalArgumentException("Project directory does not exist: " + project);
        }

        return FileSystemUtils.allDirectoriesInDirectory(projectDir).stream()
                .map(path -> path.resolve(INSTANCE_INFO_FILE))
                .filter(FilesystemRemoteWorkspaceRepository::hasInfoFile)
                .map(p -> readInfoFile(p, RemoteProjectInstance.class))
                .toList();
    }

    @Override
    public List<RemoteProjectInstanceSession> allSessions(RemoteProject project) {
        Path projectDir = workspacePath.resolve(project.projectName());
        if (!FileSystemUtils.isDirectory(projectDir)) {
            throw new IllegalArgumentException("Project directory does not exist: " + project);
        }

        List<RemoteProjectInstanceSession> sessions = new ArrayList<>();

        // Sessions are always under instance directories: project/{instanceId}/{sessionId}
        for (Path instanceDir : FileSystemUtils.allDirectoriesInDirectory(projectDir)) {
            // Only process instance directories (those with instance-info.json)
            Path instanceInfoPath = instanceDir.resolve(INSTANCE_INFO_FILE);
            if (Files.exists(instanceInfoPath)) {
                for (Path sessionDir : FileSystemUtils.allDirectoriesInDirectory(instanceDir)) {
                    Path sessionInfoPath = sessionDir.resolve(SESSION_INFO_FILE);
                    if (Files.exists(sessionInfoPath)) {
                        sessions.add(readInfoFile(sessionInfoPath, RemoteProjectInstanceSession.class));
                    }
                }
            }
        }

        return sessions;
    }

    @Override
    public void uploadSettings(RemoteWorkspaceSettings settings) {
        Path settingsDir = FileSystemUtils.createDirectories(workspacePath.resolve(WORKSPACE_SETTINGS_DIR));

        List<Path> settingsFiles = getSettingsFiles(settingsDir);
        Optional<Path> latestSettings = settingsFiles.stream().findFirst();

        // Skip upload if the latest settings are identical
        if (latestSettings.isPresent()) {
            try {
                String content = Files.readString(latestSettings.get());
                RemoteWorkspaceSettings existingSettings = Json.read(content, RemoteWorkspaceSettings.class);
                if (existingSettings.equals(settings)) {
                    LOG.debug("Skipping upload of workspace settings as they are identical to the latest version");
                    return;
                }
            } catch (IOException e) {
                throw new RuntimeException("Failed to read latest workspace settings file: " + latestSettings.get(), e);
            }
        }

        try {
            String timestamp = TIMESTAMP_FORMATTER.format(clock.instant());
            String settingsFilename = WORKSPACE_SETTINGS_FILE_PATTERN
                    .replace("<<timestamp>>", timestamp);

            Files.writeString(settingsDir.resolve(settingsFilename), Json.toString(settings), CREATE_NEW);
        } catch (IOException e) {
            throw new RuntimeException("Failed to write workspace settings file", e);
        }
    }

    private static List<Path> getSettingsFiles(Path settingsDir) {
        return FileSystemUtils.allFilesInDirectory(settingsDir).stream()
                .filter(path -> {
                    String filename = path.getFileName().toString();
                    return filename.startsWith(WORKSPACE_SETTINGS_PREFIX) && filename.endsWith(".json");
                })
                .sorted(TIMESTAMP_FILE_COMPARATOR)
                .toList();
    }

    private static boolean hasInfoFile(Path path) {
        boolean exists = Files.exists(path);
        if (!exists) {
            LOG.warn("Skipping directory without info file: {}", path);
        }
        return exists;
    }

    private static <T> T readInfoFile(Path path, Class<T> clazz) {
        try {
            String content = Files.readString(path);
            return Json.read(content, clazz);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read info file: " + path, e);
        }
    }

    @Override
    public void removeLegacySettings(int keepMaxVersions) {
        Path settingsDir = workspacePath.resolve(WORKSPACE_SETTINGS_DIR);
        if (!FileSystemUtils.isDirectory(settingsDir)) {
            return;
        }

        List<Path> settingsFiles = getSettingsFiles(settingsDir);

        for (int i = keepMaxVersions; i < settingsFiles.size(); i++) {
            try {
                LOG.info("Deleting legacy settings file: {}", settingsFiles.get(i));
                Files.deleteIfExists(settingsFiles.get(i));
            } catch (IOException e) {
                throw new RuntimeException("Failed to delete legacy settings file: " + settingsFiles.get(i), e);
            }
        }
    }
}
