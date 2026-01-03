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

package pbouda.jeffrey.storage.recording.filesystem;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pbouda.jeffrey.shared.common.filesystem.FileSystemUtils;
import pbouda.jeffrey.shared.common.model.repository.SupportedRecordingFile;
import pbouda.jeffrey.storage.recording.api.ProjectRecordingStorage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

public class FilesystemProjectRecordingStorage implements ProjectRecordingStorage {

    private static final Logger LOG = LoggerFactory.getLogger(FilesystemProjectRecordingStorage.class);

    private final Path projectFolder;
    private final List<SupportedRecordingFile> recordingTypes;

    public FilesystemProjectRecordingStorage(Path projectFolder, List<SupportedRecordingFile> recordingTypes) {
        this.projectFolder = projectFolder;
        this.recordingTypes = recordingTypes;
    }

    @Override
    public List<String> findAllRecordingIds() {
        if (Files.exists(projectFolder)) {
            return FileSystemUtils.allDirectoriesInDirectory(projectFolder).stream()
                    .map(dir -> dir.getFileName().toString())
                    .toList();
        }
        return List.of();
    }

    @Override
    public Optional<Path> findRecording(String recordingId) {
        Path recordingFolder = FileSystemUtils.createDirectories(projectFolder.resolve(recordingId));
        if (Files.exists(recordingFolder)) {
            return findRecordingFile(recordingFolder);
        } else {
            LOG.warn("Main recording folder does not exist: {}", recordingFolder);
            return Optional.empty();
        }
    }

    @Override
    public List<Path> findArtifacts(String recordingId) {
        return findAllFiles(recordingId).stream()
                .filter(this::isArtifact)
                .toList();
    }

    private boolean isArtifact(Path path) {
        return recordingTypes.stream()
                .noneMatch(type -> type.matches(path));
    }

    private boolean isRecordingFile(Path path) {
        return recordingTypes.stream()
                .anyMatch(type -> type.matches(path));
    }

    private Optional<Path> findRecordingFile(Path recordingFolder) {
        for (SupportedRecordingFile recordingType : recordingTypes) {
            Optional<Path> recordingOpt = FileSystemUtils.findSupportedFileInDir(recordingFolder, recordingType);
            if (recordingOpt.isPresent()) {
                return recordingOpt;
            }
        }
        return Optional.empty();
    }

    @Override
    public List<Path> findAllFiles(String recordingId) {
        Path recordingFolder = FileSystemUtils.createDirectories(projectFolder.resolve(recordingId));
        if (Files.exists(recordingFolder)) {
            return FileSystemUtils.allFilesInDirectory(recordingFolder);
        } else {
            LOG.warn("Recording folder does not exist: {}", recordingFolder);
            return List.of();
        }
    }

    @Override
    public void delete(String recordingId) {
        Path recordingFolder = projectFolder.resolve(recordingId);
        if (Files.exists(recordingFolder)) {
            FileSystemUtils.removeDirectory(recordingFolder);
        }
    }

    @Override
    public void delete() {
        FileSystemUtils.removeDirectory(projectFolder);
    }

    @Override
    public void deleteArtifact(String recordingId, String artifactId) {
        Path recordingFolder = FileSystemUtils.createDirectories(projectFolder.resolve(recordingId));
        Path artifactFile = recordingFolder.resolve(artifactId);

        // An additional file can be removed only if it is not a main recording file.
        if (Files.exists(artifactFile)) {
            if (isRecordingFile(artifactFile)) {
                LOG.warn("Cannot delete main recording file: recording_id={} additional_file={}",
                        recordingId, artifactFile);
                return;
            }

            FileSystemUtils.removeFile(artifactFile);
        }
    }

    @Override
    public Path uploadTarget(String recordingId, String filename) {
        Path recordingFolder = FileSystemUtils.createDirectories(projectFolder.resolve(recordingId));

        // Only one recording file is allowed in the recording folder.
        // Multiple additional files are allowed.
        Optional<Path> recordingFileOpt = findRecordingFile(recordingFolder);
        if (recordingFileOpt.isPresent()) {
            throw new RuntimeException(
                    "Recording file already exists: recording_id=" + recordingId
                    + " recording_file=" + recordingFileOpt.get().getFileName());
        }

        return recordingFolder.resolve(filename);
    }

    @Override
    public void uploadTarget(String recordingId, Path recordingPath) {
        Path recordingFolder = FileSystemUtils.createDirectories(projectFolder.resolve(recordingId));
        try {
            Files.copy(recordingPath, recordingFolder.resolve(recordingPath.getFileName()));
        } catch (IOException e) {
            throw new RuntimeException(
                    "Cannot copy the recording file to the recording folder: recording_id="
                    + recordingId + " recording_file=" + recordingPath, e);
        }
    }

    @Override
    public void addArtifacts(String recordingId, List<Path> artifacts) {
        Path recordingFolder = FileSystemUtils.createDirectories(projectFolder.resolve(recordingId));
        for (Path file : artifacts) {
            try {
                Files.copy(file, recordingFolder.resolve(file.getFileName()));
            } catch (IOException e) {
                throw new RuntimeException(
                        "Cannot copy an additional file to recording: recording_id="
                        + recordingId + " additional_file=" + file, e);
            }
        }
    }
}
