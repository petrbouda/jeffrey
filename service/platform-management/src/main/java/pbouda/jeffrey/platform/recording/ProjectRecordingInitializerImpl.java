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

package pbouda.jeffrey.platform.recording;

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

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.List;

public class ProjectRecordingInitializerImpl implements ProjectRecordingInitializer {

    private final ProjectRecordingRepository recordingRepository;
    private final ProjectRecordingStorage recordingStorage;
    private final RecordingInformationParser recordingInformationParser;
    private final Clock clock;
    private final ProjectInfo projectInfo;

    public ProjectRecordingInitializerImpl(
            Clock clock,
            ProjectInfo projectInfo,
            ProjectRecordingStorage recordingStorage,
            ProjectRecordingRepository recordingRepository,
            RecordingInformationParser recordingInformationParser) {

        this.clock = clock;
        this.projectInfo = projectInfo;
        this.recordingStorage = recordingStorage;
        this.recordingInformationParser = recordingInformationParser;
        this.recordingRepository = recordingRepository;
    }

    @Override
    public NewRecordingHolder newRecording(NewRecording newRecording, List<RepositoryFile> additionalFiles) {
        String recordingId = IDGenerator.generate();
        Path targetPath = recordingStorage.uploadTarget(recordingId, newRecording.filename());

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

                Instant createdAt = clock.instant();
                Recording recording = new Recording(
                        recordingId,
                        newRecording.recordingName(),
                        projectInfo.id(),
                        newRecording.folderId(),
                        information.eventSource(),
                        createdAt,
                        information.recordingStartedAt(),
                        information.recordingFinishedAt(),
                        false,
                        List.of());

                RecordingFile recordingFile = new RecordingFile(
                        IDGenerator.generate(),
                        recordingId,
                        newRecording.filename(),
                        SupportedRecordingFile.of(newRecording.filename()),
                        createdAt,
                        FileSystemUtils.size(targetPath));

                recordingRepository.insertRecording(recording, recordingFile);

                // ------------------------------------------------------
                // Upload Additional files to the newly created recording
                // ------------------------------------------------------

                List<Path> additionalFilePaths = additionalFiles.stream()
                        .map(RepositoryFile::filePath)
                        .toList();

                recordingStorage.addArtifacts(recordingId, additionalFilePaths);

                for (RepositoryFile additionalFile : additionalFiles) {
                    RecordingFile additionalRecordingFile = new RecordingFile(
                            IDGenerator.generate(),
                            recordingId,
                            additionalFile.name(),
                            additionalFile.fileType(),
                            createdAt,
                            additionalFile.size());

                    recordingRepository.insertRecordingFile(additionalRecordingFile);
                }

            } catch (Exception e) {
                FileSystemUtils.removeFile(targetPath);
                throw new RuntimeException("Failed to upload recording: " + newRecording.filename(), e);
            }
        };

        return new NewRecordingHolder(recordingId, targetPath, uploadCompleteCallback);
    }

    @Override
    public NewRecordingHolder newRecordingWithPaths(NewRecording newRecording, List<Path> additionalFilePaths) {
        String recordingId = IDGenerator.generate();
        Path targetPath = recordingStorage.uploadTarget(recordingId, newRecording.filename());

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

                Instant createdAt = clock.instant();
                Recording recording = new Recording(
                        recordingId,
                        newRecording.recordingName(),
                        projectInfo.id(),
                        newRecording.folderId(),
                        information.eventSource(),
                        createdAt,
                        information.recordingStartedAt(),
                        information.recordingFinishedAt(),
                        false,
                        List.of());

                RecordingFile recordingFile = new RecordingFile(
                        IDGenerator.generate(),
                        recordingId,
                        newRecording.filename(),
                        SupportedRecordingFile.of(newRecording.filename()),
                        createdAt,
                        FileSystemUtils.size(targetPath));

                recordingRepository.insertRecording(recording, recordingFile);

                // ------------------------------------------------------
                // Upload Additional files to the newly created recording
                // ------------------------------------------------------

                recordingStorage.addArtifacts(recordingId, additionalFilePaths);

                for (Path additionalFilePath : additionalFilePaths) {
                    String filename = additionalFilePath.getFileName().toString();
                    RecordingFile additionalRecordingFile = new RecordingFile(
                            IDGenerator.generate(),
                            recordingId,
                            filename,
                            SupportedRecordingFile.of(filename),
                            createdAt,
                            FileSystemUtils.size(additionalFilePath));

                    recordingRepository.insertRecordingFile(additionalRecordingFile);
                }

            } catch (Exception e) {
                FileSystemUtils.removeFile(targetPath);
                throw new RuntimeException("Failed to upload recording: " + newRecording.filename(), e);
            }
        };

        return new NewRecordingHolder(recordingId, targetPath, uploadCompleteCallback);
    }

    @Override
    public ProjectRecordingStorage recordingStorage() {
        return recordingStorage;
    }
}
