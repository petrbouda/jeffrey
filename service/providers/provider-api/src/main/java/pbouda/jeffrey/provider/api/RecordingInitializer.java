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

package pbouda.jeffrey.provider.api;

import pbouda.jeffrey.provider.api.model.recording.NewRecording;

public interface RecordingInitializer {

    /**
     * Initializes a new recording and provides a {@link NewRecordingHolder}
     * that contains resources for managing the recording's lifecycle.
     *
     * @param recording the details of the recording including filename, folder ID, and input stream
     * @return a {@link NewRecordingHolder} that provides access to the output stream and cleanup logic
     */
    NewRecordingHolder newRecording(NewRecording recording);
}
