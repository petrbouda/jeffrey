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

package pbouda.jeffrey.storage.recording.api;

import pbouda.jeffrey.common.model.ProjectInfo;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

public interface ProjectRecordingStorage {

    @FunctionalInterface
    interface Factory extends Function<ProjectInfo, ProjectRecordingStorage> {
    }

    /**
     * Searches for a recording by its unique identifier and returns the file the recording exists.
     *
     * @param recordingId the unique identifier of the recording to be found
     * @return an {@code Optional} containing the file to the recording if it exists,
     * or an empty {@code Optional} if the recording is not found
     */
    Optional<Path> findRecording(String recordingId);

    /**
     * Retrieves a list of all recording files associated with a specific recording ID.
     * This includes any additional or related files that belong to the recording.
     *
     * @param recordingId the unique identifier of the recording for which to find all associated files
     * @return a list of files associated with the specified recording ID
     */
    List<Path> findAllFiles(String recordingId);

    /**
     * Deletes a recording and all associated files based on the given recording ID.
     *
     * @param recordingId the unique identifier of the recording to delete
     */
    void delete(String recordingId);

    /**
     * Deletes a specific additional file associated with a recording.
     *
     * @param recordingId     the unique identifier of the recording to which the additional file belongs
     * @param recordingFileId the unique identifier of the additional file to delete
     */
    void deleteAdditionalFile(String recordingId, String recordingFileId);

    /**
     * Uploads a recording with the specified unique identifier. This method opens
     * an {@code OutputStream} where the content of the recording can be written.
     *
     * @param recordingId the unique identifier of the recording to be uploaded
     * @param filename    the filename of the recording to be uploaded
     * @return an {@code OutputStream} that allows writing to the recording storage
     */
    StreamingRecordingUploader uploadRecording(String recordingId, String filename);

    /**
     * Adds additional files to the recording with the specified unique identifier.
     *
     * @param recordingId the unique identifier of the recording to which additional
     *                    files should be added
     * @param files       a list of file paths to be added to the recording with the specified ID
     */
    void addAdditionalFiles(String recordingId, List<Path> files);
}
