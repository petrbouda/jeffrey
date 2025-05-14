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
import pbouda.jeffrey.common.filesystem.FileSystemUtils;
import pbouda.jeffrey.common.model.repository.SupportedRecordingFile;
import pbouda.jeffrey.storage.recording.api.ProjectRecordingStorage;
import pbouda.jeffrey.storage.recording.api.StreamingRecordingUploader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Optional;

public class FilesystemProjectRecordingStorage implements ProjectRecordingStorage {

    private static final Logger LOG = LoggerFactory.getLogger(FilesystemProjectRecordingStorage.class);

    private final Path projectFolder;
    private final SupportedRecordingFile recordingFileType;

    public FilesystemProjectRecordingStorage(Path projectFolder, SupportedRecordingFile recordingFileType) {
        this.projectFolder = projectFolder;
        this.recordingFileType = recordingFileType;
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
            return FileSystemUtils.findSupportedFileInDir(recordingFolder, recordingFileType);
        } else {
            LOG.warn("Main recording file does not exist: {}", recordingFolder);
            return Optional.empty();
        }
    }

    @Override
    public List<Path> findAdditionalFiles(String recordingId) {
        return findAllFiles(recordingId).stream()
                .filter(path -> !recordingFileType.matches(path))
                .toList();
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
    public void deleteAdditionalFile(String recordingId, String recordingFileId) {
        Path recordingFolder = FileSystemUtils.createDirectories(projectFolder.resolve(recordingId));
        Path additionalFile = recordingFolder.resolve(recordingFileId);

        // An additional file can be removed only if it is not a main recording file.
        if (Files.exists(additionalFile)) {
            boolean matchesRecordingFileType = recordingFileType.matches(additionalFile);
            if (matchesRecordingFileType) {
                LOG.warn("Cannot delete main recording file: recording_id={} additional_file={}",
                        recordingId, additionalFile);
                return;
            }

            FileSystemUtils.removeFile(additionalFile);
        }
    }

    @Override
    public StreamingRecordingUploader uploadRecording(String recordingId, String filename) {
        Path recordingFolder = FileSystemUtils.createDirectories(projectFolder.resolve(recordingId));

        // Only one recording file is allowed in the recording folder.
        // Multiple additional files are allowed.
        Optional<Path> recordingFileOpt = FileSystemUtils.findSupportedFileInDir(recordingFolder, recordingFileType);
        if (recordingFileOpt.isPresent()) {
            throw new RuntimeException(
                    "Recording file already exists: recording_id=" + recordingId
                    + " recording_file=" + recordingFileOpt.get().getFileName());
        }

        try {
            Path target = recordingFolder.resolve(filename);
            return new StreamingRecordingUploader(
                    target, Files.newOutputStream(target, StandardOpenOption.CREATE_NEW));
        } catch (IOException e) {
            throw new RuntimeException("Cannot open an output stream for recording file: " + filename, e);
        }
    }

    @Override
    public void addAdditionalFiles(String recordingId, List<Path> files) {
        Path recordingFolder = FileSystemUtils.createDirectories(projectFolder.resolve(recordingId));
        for (Path file : files) {
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
