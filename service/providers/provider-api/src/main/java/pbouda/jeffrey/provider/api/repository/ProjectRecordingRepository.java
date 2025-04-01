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

import pbouda.jeffrey.provider.api.model.recording.RecordingFolder;
import pbouda.jeffrey.provider.api.model.recording.RecordingWithFolder;

import java.util.List;

public interface ProjectRecordingRepository {

    /**
     * Finds all recordings in the project.
     *
     * @return a list of recordings with their associated folders
     */
    List<RecordingWithFolder> findAllRecordings();

    /**
     * Creates a new folder in the project.
     *
     * @param folderName the name of the folder
     */
    void insertFolder(String folderName);

    /**
     * Finds all recording folders in the project.
     *
     * @return a list of recording folders
     */
    List<RecordingFolder> findAllRecordingFolders();
}
