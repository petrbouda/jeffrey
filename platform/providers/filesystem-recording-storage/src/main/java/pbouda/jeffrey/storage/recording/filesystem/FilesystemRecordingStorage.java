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

import pbouda.jeffrey.shared.common.filesystem.FileSystemUtils;
import pbouda.jeffrey.shared.common.model.repository.SupportedRecordingFile;
import pbouda.jeffrey.storage.recording.api.ProjectRecordingStorage;
import pbouda.jeffrey.storage.recording.api.RecordingStorage;

import java.nio.file.Path;
import java.util.List;

public class FilesystemRecordingStorage implements RecordingStorage {

    private final Path recordingStoragePath;
    private final List<SupportedRecordingFile> recordingTypes;

    public FilesystemRecordingStorage(Path recordingStoragePath, List<SupportedRecordingFile> recordingTypes) {
        this.recordingStoragePath = recordingStoragePath;
        this.recordingTypes = recordingTypes;
    }

    @Override
    public List<String> findAllProjects() {
        return FileSystemUtils.allDirectoriesInDirectory(recordingStoragePath).stream()
                .map(dir -> dir.getFileName().toString())
                .toList();
    }

    @Override
    public ProjectRecordingStorage projectRecordingStorage(String projectId) {
        Path projectRecordingStoragePath = recordingStoragePath.resolve(projectId);
        return new FilesystemProjectRecordingStorage(projectRecordingStoragePath, recordingTypes);
    }
}
