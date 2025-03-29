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
import pbouda.jeffrey.provider.api.RecordingWriter;
import pbouda.jeffrey.provider.api.model.NewRecording;
import pbouda.jeffrey.provider.writer.sqlite.internal.InternalRecordingRepository;

import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

public class FileBasedRecordingWriter implements RecordingWriter {

    private final Path recordingsPath;
    private final InternalRecordingRepository recordingRepository;

    public FileBasedRecordingWriter(DataSource datasource, Path recordingsPath) {
        if (Files.exists(recordingsPath) && !Files.isDirectory(recordingsPath)) {
            throw new IllegalArgumentException("Recordings path must be a directory");
        } else if (!Files.exists(recordingsPath)) {
            try {
                Files.createDirectories(recordingsPath);
            } catch (Exception e) {
                throw new RuntimeException("Failed to create recordings directory", e);
            }
        }

        this.recordingRepository = new InternalRecordingRepository(datasource);
        this.recordingsPath = recordingsPath;
    }

    @Override
    public void write(NewRecording newRecording) {
        if (newRecording.folderId() != null) {
            boolean folderExists = recordingRepository.folderExists(newRecording);
            if (!folderExists) {
                throw new RuntimeException("Folder does not exist: " + newRecording.folderId());
            }
        }

        String recordingId = IDGenerator.generate();
        Path targetPath = recordingsPath.resolve(recordingId);

        try {
            FileSystemUtils.copyStream(targetPath, newRecording.stream());

            Recording recording = new Recording(
                    recordingId,
                    newRecording.name(),
                    newRecording.projectId(),
                    newRecording.folderId(),
                    FileSystemUtils.size(targetPath),
                    Instant.now());

            recordingRepository.insertRecording(recording);
        } catch (Exception e) {
            FileSystemUtils.removeFile(targetPath);
            throw new RuntimeException("Failed to upload recording: " + newRecording.name(), e);
        }

    }
}
