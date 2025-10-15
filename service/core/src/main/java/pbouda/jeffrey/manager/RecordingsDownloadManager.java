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

package pbouda.jeffrey.manager;

import pbouda.jeffrey.common.model.repository.RepositoryFile;

import java.util.List;

public interface RecordingsDownloadManager {

    void mergeAndDownloadSession(String recordingSessionId);

    void downloadSession(String recordingSessionId);

    void mergeAndDownloadSelectedRawRecordings(String recordingSessionId, List<String> rawRecordingIds);

    void downloadSelectedRawRecordings(String recordingSessionId, List<String> rawRecordingIds);

    /**
     * Create new recording in the repository, it takes a collection of repository files and split them into
     * recording files and additional files (Heap Dumps, Logs, etc.). The recording files are then merged into
     * a single recording file (if there are more than one) and stored to local recordings along with the
     * additional files.
     *
     * @param recordingName     name of the new recording.
     * @param repositoryFiles   collection of repository files to be processed.
     */
    void createNewRecording(String recordingName, List<RepositoryFile> repositoryFiles);
}
