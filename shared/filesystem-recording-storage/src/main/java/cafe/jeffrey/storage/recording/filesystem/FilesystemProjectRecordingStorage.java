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

package cafe.jeffrey.storage.recording.filesystem;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cafe.jeffrey.shared.common.filesystem.FileSystemUtils;
import cafe.jeffrey.shared.common.model.repository.SupportedFile;
import cafe.jeffrey.storage.recording.api.ProjectRecordingStorage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

public class FilesystemProjectRecordingStorage implements ProjectRecordingStorage {

    private static final Logger LOG = LoggerFactory.getLogger(FilesystemProjectRecordingStorage.class);

    /**
     * Reading order of a recording's files: by name with the chunk extension stripped, which for
     * the chunks of a session is the order they were written, whichever of them the hub has
     * since compressed.
     */
    private static final Comparator<Path> RECORDING_FILE_ORDER = Comparator.comparing(
            path -> FileSystemUtils.removeExtension(path, SupportedFile.recordingChunkExtensions()));

    private final Path projectFolder;

    public FilesystemProjectRecordingStorage(Path projectFolder) {
        this.projectFolder = projectFolder;
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
    public List<Path> findRecordingFiles(String recordingId) {
        return findAllFiles(recordingId).stream()
                .filter(FilesystemProjectRecordingStorage::isProfileRecording)
                .sorted(RECORDING_FILE_ORDER)
                .toList();
    }

    @Override
    public List<Path> findAdditionalFiles(String recordingId) {
        return findAllFiles(recordingId).stream()
                .filter(path -> !isProfileRecording(path))
                .toList();
    }

    /**
     * The one file of a recording folder Microscope parses as the recording; every other file
     * there is an additional file. Decided by {@link SupportedFile#isProfileRecording()}.
     */
    private static boolean isProfileRecording(Path path) {
        return SupportedFile.of(path).isProfileRecording();
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
    public void deleteAdditionalFile(String recordingId, String additionalFileId) {
        Path recordingFolder = FileSystemUtils.createDirectories(projectFolder.resolve(recordingId));
        Path additionalFile = recordingFolder.resolve(additionalFileId);

        // An additional file can be removed only if it is not a main recording file.
        if (Files.exists(additionalFile)) {
            if (isProfileRecording(additionalFile)) {
                LOG.warn("Cannot delete main recording file: recording_id={} additional_file={}",
                        recordingId, additionalFile);
                return;
            }

            FileSystemUtils.removeFile(additionalFile);
        }
    }

    @Override
    public Path uploadTarget(String recordingId, String filename) {
        // A recording folder may hold several recording files: the chunks of one session.
        Path recordingFolder = FileSystemUtils.createDirectories(projectFolder.resolve(recordingId));
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
    public void addAdditionalFiles(String recordingId, List<Path> additionalFiles) {
        Path recordingFolder = FileSystemUtils.createDirectories(projectFolder.resolve(recordingId));
        for (Path file : additionalFiles) {
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
