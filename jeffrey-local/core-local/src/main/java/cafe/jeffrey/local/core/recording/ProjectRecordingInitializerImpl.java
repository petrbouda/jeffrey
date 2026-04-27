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

package cafe.jeffrey.local.core.recording;

import cafe.jeffrey.shared.common.IDGenerator;
import cafe.jeffrey.shared.common.filesystem.FileSystemUtils;
import cafe.jeffrey.shared.common.model.ProjectInfo;
import cafe.jeffrey.shared.common.model.Recording;
import cafe.jeffrey.shared.common.model.RecordingFile;
import cafe.jeffrey.shared.common.model.repository.RepositoryFile;
import cafe.jeffrey.shared.common.model.repository.SupportedRecordingFile;
import cafe.jeffrey.local.core.persistence.NewRecordingHolder;
import cafe.jeffrey.provider.profile.api.RecordingInformationParser;
import cafe.jeffrey.local.core.persistence.NewRecording;
import cafe.jeffrey.provider.profile.api.RecordingInformation;
import cafe.jeffrey.local.persistence.api.RecordingRepository;
import cafe.jeffrey.storage.recording.api.ProjectRecordingStorage;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.List;

public class ProjectRecordingInitializerImpl implements ProjectRecordingInitializer {

    private final RecordingRepository recordingRepository;
    private final ProjectRecordingStorage recordingStorage;
    private final RecordingInformationParser recordingInformationParser;
    private final Clock clock;
    private final ProjectInfo projectInfo;

    public ProjectRecordingInitializerImpl(
            Clock clock,
            ProjectInfo projectInfo,
            ProjectRecordingStorage recordingStorage,
            RecordingRepository recordingRepository,
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
            if (newRecording.groupId() != null) {
                boolean groupExists = recordingRepository.groupExists(newRecording.groupId());
                if (!groupExists) {
                    throw new RuntimeException("Group does not exist: " + newRecording.groupId());
                }
            }

            try {
                // Provide information about the Recording file
                RecordingInformation information = recordingInformationParser.provide(targetPath);
                validateRecordingInformation(information, newRecording.filename());

                Instant createdAt = clock.instant();
                Recording recording = new Recording(
                        recordingId,
                        newRecording.recordingName(),
                        projectInfo.id(),
                        newRecording.groupId(),
                        information.eventSource(),
                        createdAt,
                        information.recordingStartedAt(),
                        information.recordingFinishedAt(),
                        false, null, null,
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
            if (newRecording.groupId() != null) {
                boolean groupExists = recordingRepository.groupExists(newRecording.groupId());
                if (!groupExists) {
                    throw new RuntimeException("Group does not exist: " + newRecording.groupId());
                }
            }

            try {
                // Provide information about the Recording file
                RecordingInformation information = recordingInformationParser.provide(targetPath);
                validateRecordingInformation(information, newRecording.filename());

                Instant createdAt = clock.instant();
                Recording recording = new Recording(
                        recordingId,
                        newRecording.recordingName(),
                        projectInfo.id(),
                        newRecording.groupId(),
                        information.eventSource(),
                        createdAt,
                        information.recordingStartedAt(),
                        information.recordingFinishedAt(),
                        false, null, null,
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

    private static void validateRecordingInformation(RecordingInformation information, String filename) {
        if (information.recordingStartedAt() == null || information.recordingFinishedAt() == null) {
            throw new RuntimeException(
                    "Recording file is empty or corrupt (contains no JFR chunks): " + filename);
        }
    }
}
