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

import java.util.List;

public interface RecordingStorage {

    /**
     * List all project IDs that are in the recording storage.
     *
     * @return all project IDs
     */
    List<String> findAllProjects();

    /**
     * Return {@link ProjectRecordingStorage} for a dedicated project.
     *
     * @param projectId Project ID to find the recording storage for.
     * @return Recording Storage for the given project ID.
     */
    ProjectRecordingStorage projectRecordingStorage(String projectId);
}
