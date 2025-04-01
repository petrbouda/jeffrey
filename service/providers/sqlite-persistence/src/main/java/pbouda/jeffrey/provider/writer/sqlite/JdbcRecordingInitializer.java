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

package pbouda.jeffrey.provider.writer.sqlite;

import pbouda.jeffrey.common.IDGenerator;
import pbouda.jeffrey.common.filesystem.FileSystemUtils;
import pbouda.jeffrey.common.model.Recording;
import pbouda.jeffrey.provider.api.RecordingInformationParser;
import pbouda.jeffrey.provider.api.RecordingInitializer;
import pbouda.jeffrey.provider.api.model.recording.NewRecording;
import pbouda.jeffrey.provider.api.model.recording.RecordingInformation;
import pbouda.jeffrey.provider.writer.sqlite.internal.InternalRecordingRepository;

import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

public class JdbcRecordingInitializer implements RecordingInitializer {

    private final Path recordingsPath;
    private final InternalRecordingRepository recordingRepository;
    private final RecordingInformationParser recordingInformationParser;
    private final String projectId;

    public JdbcRecordingInitializer(
            String projectId,
            Path recordingsPath,
            DataSource datasource,
            RecordingInformationParser recordingInformationParser) {

        if (Files.exists(recordingsPath) && !Files.isDirectory(recordingsPath)) {
            throw new IllegalArgumentException("Recordings path must be a directory");
        } else if (!Files.exists(recordingsPath)) {
            try {
                Files.createDirectories(recordingsPath);
            } catch (Exception e) {
                throw new RuntimeException("Failed to create recordings directory", e);
            }
        }

        this.projectId = projectId;
        this.recordingInformationParser = recordingInformationParser;
        this.recordingRepository = new InternalRecordingRepository(datasource);
        this.recordingsPath = recordingsPath;
    }

    @Override
    public String newRecording(NewRecording newRecording) {
        if (newRecording.folderId() != null) {
            boolean folderExists = recordingRepository.folderExists(projectId, newRecording);
            if (!folderExists) {
                throw new RuntimeException("Folder does not exist: " + newRecording.folderId());
            }
        }

        String recordingId = IDGenerator.generate();

        // Generate a target recording name to be unique and use it to store recording on filesystem
        String uniqueRecordingName = resolveRecordingName(recordingId, newRecording.filename());
        Path targetPath = recordingsPath.resolve(uniqueRecordingName);

        try {
            FileSystemUtils.copyStream(targetPath, newRecording.stream());

            // Provide information about the Recording file
            RecordingInformation information = recordingInformationParser.provide(targetPath);

            Recording recording = new Recording(
                    recordingId,
                    newRecording.filename(),
                    uniqueRecordingName,
                    projectId,
                    newRecording.folderId(),
                    information.eventSource(),
                    information.sizeInBytes(),
                    Instant.now(),
                    information.recordingStartedAt(),
                    information.recordingFinishedAt(),
                    false);

            recordingRepository.insertRecording(recording);
        } catch (Exception e) {
            FileSystemUtils.removeFile(targetPath);
            throw new RuntimeException("Failed to upload recording: " + newRecording.filename(), e);
        }

        return recordingId;
    }

    private static String resolveRecordingName(String recordingId, String originalFilename) {
        String extractedFilename = originalFilename.endsWith(".jfr")
                ? originalFilename.substring(0, originalFilename.length() - 4)
                : originalFilename;

        return recordingId + "+" + extractedFilename;
    }
}
