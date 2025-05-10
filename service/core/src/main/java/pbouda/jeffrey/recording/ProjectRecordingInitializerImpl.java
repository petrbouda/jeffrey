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

package pbouda.jeffrey.recording;

import pbouda.jeffrey.common.IDGenerator;
import pbouda.jeffrey.common.filesystem.FileSystemUtils;
import pbouda.jeffrey.common.model.ProjectInfo;
import pbouda.jeffrey.common.model.Recording;
import pbouda.jeffrey.common.model.RecordingFile;
import pbouda.jeffrey.common.model.repository.RepositoryFile;
import pbouda.jeffrey.common.model.repository.SupportedRecordingFile;
import pbouda.jeffrey.provider.api.NewRecordingHolder;
import pbouda.jeffrey.provider.api.RecordingInformationParser;
import pbouda.jeffrey.provider.api.model.recording.NewRecording;
import pbouda.jeffrey.provider.api.model.recording.RecordingInformation;
import pbouda.jeffrey.provider.api.repository.ProjectRecordingRepository;
import pbouda.jeffrey.storage.recording.api.ProjectRecordingStorage;
import pbouda.jeffrey.storage.recording.api.StreamingRecordingUploader;

import java.io.File;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

public class ProjectRecordingInitializerImpl implements ProjectRecordingInitializer {

    //    private final Path recordingsPath;
    private final ProjectRecordingRepository recordingRepository;
    private final ProjectRecordingStorage recordingStorage;
    private final RecordingInformationParser recordingInformationParser;
    private final ProjectInfo projectInfo;

    public ProjectRecordingInitializerImpl(
            ProjectInfo projectInfo,
            ProjectRecordingStorage recordingStorage,
            ProjectRecordingRepository recordingRepository,
            RecordingInformationParser recordingInformationParser) {

        this.projectInfo = projectInfo;
        this.recordingStorage = recordingStorage;
        this.recordingInformationParser = recordingInformationParser;
        this.recordingRepository = recordingRepository;
    }

    @Override
    public NewRecordingHolder newStreamedRecording(NewRecording newRecording) {
        String recordingId = IDGenerator.generate();

        // Generate a target recording name to be unique and use it to store recording on filesystem
        String filename = newRecording.filename();
        String recordingName = FileSystemUtils.filenameWithoutExtension(Path.of(filename));
        String uniqueRecordingName = recordingId + "+" + recordingName;

        StreamingRecordingUploader uploader = recordingStorage.uploadRecording(recordingId, uniqueRecordingName);
        Path targetPath = uploader.target();

        Runnable uploadCompleteCallback = () -> {
            if (newRecording.folderId() != null) {
                boolean folderExists = recordingRepository.folderExists(newRecording.folderId());
                if (!folderExists) {
                    throw new RuntimeException("Folder does not exist: " + newRecording.folderId());
                }
            }

            try {
                // Provide information about the Recording file
                RecordingInformation information = recordingInformationParser.provide(targetPath);

                Instant createdAt = Instant.now();
                Recording recording = new Recording(
                        recordingId,
                        filename,
                        projectInfo.id(),
                        newRecording.folderId(),
                        information.eventSource(),
                        createdAt,
                        information.recordingStartedAt(),
                        information.recordingFinishedAt(),
                        false,
                        List.of());

                String recordingFileId = IDGenerator.generate();
                RecordingFile recordingFile = new RecordingFile(
                        recordingFileId,
                        recordingId,
                        filename,
                        SupportedRecordingFile.of(filename),
                        createdAt,
                        FileSystemUtils.size(targetPath));

                recordingRepository.insertRecording(recording, recordingFile);

            } catch (Exception e) {
                FileSystemUtils.removeFile(targetPath);
                throw new RuntimeException("Failed to upload recording: " + filename, e);
            }
        };

        return new NewRecordingHolder(recordingId, uploader.stream(), uploadCompleteCallback);
    }

    @Override
    public void newCopiedRecording(NewRecording recording, List<RepositoryFile> files) {
        // TODO: One recording can have only on Recording File + multiple non-recording files
    }
}
