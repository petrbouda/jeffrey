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

import pbouda.jeffrey.common.model.Recording;
import pbouda.jeffrey.provider.api.model.recording.NewRecording;
import pbouda.jeffrey.provider.api.model.recording.RecordingFolder;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

public interface RecordingsManager {

    List<Recording> all();

    void upload(NewRecording newRecording, InputStream stream);

    void createFolder(String folderName);

    void deleteFolder(String folderId);

    List<RecordingFolder> allRecordingFolders();

    void delete(String recordingId);

    /**
     * Finds a specific recording file by recording ID and file ID.
     *
     * @param recordingId the ID of the recording
     * @param fileId      the ID (filename) of the file within the recording
     * @return the path to the file if found, empty otherwise
     */
    Optional<Path> findRecordingFile(String recordingId, String fileId);
}
