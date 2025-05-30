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

package pbouda.jeffrey.provider.api.repository;

import pbouda.jeffrey.common.model.Recording;
import pbouda.jeffrey.common.model.RecordingFile;
import pbouda.jeffrey.provider.api.model.recording.NewRecording;
import pbouda.jeffrey.provider.api.model.recording.RecordingFolder;

import java.util.List;
import java.util.Optional;

public interface ProjectRecordingRepository {

    /**
     * Finds a recording by its ID.
     *
     * @return a single recording
     */
    Optional<Recording> findRecording(String recordingId);

    /**
     * Deletes a recording by its ID.
     *
     * @param recordingId the ID of the recording to delete
     */
    void deleteRecordingWithFiles(String recordingId);

    /**
     * Finds all recordings in the project.
     *
     * @return a list of recordings with their associated folders
     */
    List<Recording> findAllRecordings();

    /**
     * Creates a new folder in the project.
     *
     * @param folderName the name of the folder
     */
    String insertFolder(String folderName);

    /**
     * Deletes a folder by its ID.
     *
     * @param folderId the ID of the folder to delete
     */
    void deleteFolder(String folderId);

    /**
     * Finds all recording folders in the project.
     *
     * @return a list of recording folders
     */
    List<RecordingFolder> findAllRecordingFolders();

    /**
     * Finds a recording by its ID.
     *
     * @param recordingId the ID of the recording to find
     * @return a single recording if it exists in the project otherwise an empty optional
     */
    Optional<Recording> findById(String recordingId);

    /**
     * Inserts a new recording into the project with a main recording file.
     *
     * @param recording the recording to insert.
     */
    void insertRecording(Recording recording, RecordingFile recordingFile);

    /**
     * Inserts a new recording file into the project and recording folder.
     *
     * @param recordingFile the recording file to insert.
     */
    void insertRecordingFile(RecordingFile recordingFile);

    /**
     * Checks if the folder exists.
     *
     * @param folderId the ID of the folder to check
     * @return true if the folder exists, false otherwise
     */
    boolean folderExists(String folderId);
}
