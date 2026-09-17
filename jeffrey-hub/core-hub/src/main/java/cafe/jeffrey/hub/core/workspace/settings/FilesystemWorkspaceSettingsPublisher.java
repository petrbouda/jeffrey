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
package cafe.jeffrey.hub.core.workspace.settings;

import cafe.jeffrey.shared.common.JeffreyLayout;
import cafe.jeffrey.shared.common.Json;
import cafe.jeffrey.shared.common.filesystem.FileSystemUtils;
import cafe.jeffrey.shared.common.model.repository.RemoteWorkspaceSettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import static java.nio.file.StandardOpenOption.CREATE_NEW;

public class FilesystemWorkspaceSettingsPublisher implements WorkspaceSettingsPublisher {

    private static final Logger LOG = LoggerFactory.getLogger(FilesystemWorkspaceSettingsPublisher.class);

    private static final DateTimeFormatter TIMESTAMP_FORMATTER =
            DateTimeFormatter.ofPattern(JeffreyLayout.SETTINGS_TIMESTAMP_PATTERN).withZone(ZoneOffset.UTC);

    private static final String SETTINGS_DIR = JeffreyLayout.SETTINGS_DIR;
    private static final String SETTINGS_FILE_PREFIX = JeffreyLayout.SETTINGS_FILE_PREFIX;
    private static final String SETTINGS_FILE_EXTENSION = ".json";
    private static final char TIMESTAMP_SEPARATOR = '-';

    /** Newest first, by the timestamp in the file's own name. */
    private static final Comparator<Path> NEWEST_FIRST =
            Comparator.comparing(FilesystemWorkspaceSettingsPublisher::timestampOf).reversed();

    private final Clock clock;
    private final Path workspacePath;

    public FilesystemWorkspaceSettingsPublisher(Clock clock, Path workspacePath) {
        if (workspacePath == null) {
            throw new IllegalArgumentException("Workspace path cannot be null");
        }
        this.clock = clock;
        this.workspacePath = workspacePath;
    }

    @Override
    public void uploadSettings(RemoteWorkspaceSettings settings) {
        Path settingsDir = FileSystemUtils.createDirectories(workspacePath.resolve(SETTINGS_DIR));
        Optional<Path> newest = settingsFiles(settingsDir).stream().findFirst();

        if (newest.isPresent() && read(newest.get()).equals(settings)) {
            LOG.debug("Skipping upload of workspace settings, identical to the newest version: workspace_path={}", workspacePath);
            return;
        }

        String fileName = SETTINGS_FILE_PREFIX + TIMESTAMP_FORMATTER.format(clock.instant()) + SETTINGS_FILE_EXTENSION;
        try {
            Files.writeString(settingsDir.resolve(fileName), Json.toString(settings), CREATE_NEW);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write workspace settings file: " + fileName, e);
        }
    }

    @Override
    public void removeLegacySettings(int keepMaxVersions) {
        Path settingsDir = workspacePath.resolve(SETTINGS_DIR);
        if (!FileSystemUtils.isDirectory(settingsDir)) {
            return;
        }

        List<Path> settingsFiles = settingsFiles(settingsDir);
        for (int i = keepMaxVersions; i < settingsFiles.size(); i++) {
            try {
                LOG.info("Deleting legacy settings file: file={}", settingsFiles.get(i));
                Files.deleteIfExists(settingsFiles.get(i));
            } catch (IOException e) {
                throw new UncheckedIOException("Failed to delete legacy settings file: " + settingsFiles.get(i), e);
            }
        }
    }

    private static RemoteWorkspaceSettings read(Path file) {
        try {
            return Json.read(Files.readString(file), RemoteWorkspaceSettings.class);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read workspace settings file: " + file, e);
        }
    }

    private static List<Path> settingsFiles(Path settingsDir) {
        return FileSystemUtils.allFilesInDirectory(settingsDir).stream()
                .filter(path -> {
                    String name = path.getFileName().toString();
                    return name.startsWith(SETTINGS_FILE_PREFIX) && name.endsWith(SETTINGS_FILE_EXTENSION);
                })
                .sorted(NEWEST_FIRST)
                .toList();
    }

    private static Instant timestampOf(Path path) {
        String name = path.getFileName().toString();
        String timestamp = name.substring(name.indexOf(TIMESTAMP_SEPARATOR) + 1, name.lastIndexOf(SETTINGS_FILE_EXTENSION));
        return Instant.from(TIMESTAMP_FORMATTER.parse(timestamp));
    }
}
