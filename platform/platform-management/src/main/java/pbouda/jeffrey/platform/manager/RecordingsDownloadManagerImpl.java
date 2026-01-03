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

package pbouda.jeffrey.platform.manager;

import pbouda.jeffrey.platform.project.repository.MergedRecording;
import pbouda.jeffrey.platform.project.repository.RepositoryStorage;
import pbouda.jeffrey.platform.recording.ProjectRecordingInitializer;
import pbouda.jeffrey.provider.platform.NewRecordingHolder;
import pbouda.jeffrey.provider.platform.model.NewRecording;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

public class RecordingsDownloadManagerImpl implements RecordingsDownloadManager {
    private final ProjectRecordingInitializer recordingInitializer;
    private final RepositoryStorage repositoryStorage;

    public RecordingsDownloadManagerImpl(
            ProjectRecordingInitializer recordingInitializer,
            RepositoryStorage repositoryStorage) {

        this.recordingInitializer = recordingInitializer;
        this.repositoryStorage = repositoryStorage;
    }

    @Override
    public void mergeAndDownloadSession(String sessionId) {
        try (MergedRecording merged = repositoryStorage.mergeRecordings(sessionId)) {
            createNewRecording(sessionId, merged.path(), repositoryStorage.artifacts(sessionId));
        }
    }

    @Override
    public void mergeAndDownloadRecordings(String sessionId, List<String> recordingIds) {
        try (MergedRecording merged = repositoryStorage.mergeRecordings(sessionId, recordingIds)) {
            createNewRecording(sessionId, merged.path(), repositoryStorage.artifacts(sessionId));
        }
    }

    @Override
    public void createNewRecording(String recordingName, Path recordingPath, List<Path> artifactPaths) {
        String filename = recordingPath.getFileName().toString();
        NewRecording newRecording = new NewRecording(recordingName, filename, null);

        try (NewRecordingHolder holder = recordingInitializer.newRecordingWithPaths(newRecording, artifactPaths)) {
            Files.copy(recordingPath, holder.outputPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            throw new RuntimeException("Cannot upload the recording: " + recordingName, e);
        }
    }
}
