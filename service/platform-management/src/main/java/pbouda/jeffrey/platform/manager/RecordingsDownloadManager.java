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

import java.nio.file.Path;
import java.util.List;

public interface RecordingsDownloadManager {

    void mergeAndDownloadSession(String recordingSessionId);

    void mergeAndDownloadRecordings(String recordingSessionId, List<String> rawRecordingIds);

    /**
     * Creates a new recording from the given paths.
     * <p>
     * This method is used by remote download managers that have already downloaded
     * the files to a temporary location and need to merge and store them.
     * </p>
     *
     * @param recordingName the name for the new recording
     * @param recordingPath path to the merged recording file
     * @param artifactPaths paths to artifact files (heap dumps, logs, etc.)
     */
    void createNewRecording(String recordingName, Path recordingPath, List<Path> artifactPaths);
}
