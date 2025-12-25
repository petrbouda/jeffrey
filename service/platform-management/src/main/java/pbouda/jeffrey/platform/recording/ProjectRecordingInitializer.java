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

import pbouda.jeffrey.common.model.ProjectInfo;
import pbouda.jeffrey.common.model.repository.RepositoryFile;
import pbouda.jeffrey.provider.api.NewRecordingHolder;
import pbouda.jeffrey.provider.api.model.recording.NewRecording;
import pbouda.jeffrey.storage.recording.api.ProjectRecordingStorage;

import java.util.List;
import java.util.function.Function;

public interface ProjectRecordingInitializer {

    @FunctionalInterface
    interface Factory extends Function<ProjectInfo, ProjectRecordingInitializer> {
    }

    /**
     * Initializes a new recording and provides a {@link NewRecordingHolder} that contains resources
     * for managing the recording's lifecycle. It allows copying some additional files to the recording as well.
     *
     * @param recording       the details of the recording including filename, folder ID, and input stream
     * @param additionalFiles a list of additional files to be associated with the recording
     * @return a {@link NewRecordingHolder} that provides access to the output stream for uploading recording files.
     */
    NewRecordingHolder newRecording(NewRecording recording, List<RepositoryFile> additionalFiles);

    /**
     * Creates a new recording and provides a {@link NewRecordingHolder} that contains resources
     * for managing the recording's lifecycle. Only recording can be uploaded using the returned holder,
     * no additional files will be copied.
     *
     * @param recording the details of the recording including filename, folder ID, and input stream
     * @return a {@link NewRecordingHolder} that provides access to the output stream for uploading recording files.
     */
    default NewRecordingHolder newRecording(NewRecording recording) {
        return newRecording(recording, List.of());
    }

    /**
     * Returns the recording storage for accessing recording files.
     *
     * @return the project recording storage
     */
    ProjectRecordingStorage recordingStorage();
}
