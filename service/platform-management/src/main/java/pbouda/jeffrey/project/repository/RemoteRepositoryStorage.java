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

package pbouda.jeffrey.project.repository;

import pbouda.jeffrey.common.model.ProjectInfo;
import pbouda.jeffrey.common.model.RepositoryType;
import pbouda.jeffrey.common.model.repository.RecordingSession;
import pbouda.jeffrey.common.model.repository.SupportedRecordingFile;
import pbouda.jeffrey.profile.manager.model.StreamedRecordingFile;

import java.io.InputStream;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

public interface RemoteRepositoryStorage {

    @FunctionalInterface
    interface Factory extends Function<ProjectInfo, RemoteRepositoryStorage> {
    }

    /**
     * Lists of files for the given session.
     *
     * @param sessionId id of the session to list files for
     * @param withFiles if true, includes associated files in the session metadata
     * @return list of recordings for the given session
     */
    Optional<RecordingSession> singleSession(String sessionId, boolean withFiles);

    /**
     * Lists all recording sessions available in the repository.
     *
     * @param withFiles if true, includes associated files in the session metadata
     *                  (e.g., recordings, metadata files)
     * @return a list of all recording sessions, each containing metadata and
     * associated recordings
     */
    List<RecordingSession> listSessions(boolean withFiles);

    /**
     * Deletes specific repository files from the repository.
     *
     * @param sessionId         the unique identifier of the recording session
     * @param repositoryFileIds the list of unique identifiers of the repository files to delete
     */
    void deleteRepositoryFiles(String sessionId, List<String> repositoryFileIds);

    /**
     * Deletes a specific recording session and all its associated recordings
     * from the repository.
     *
     * @param sessionId the unique identifier of the recording session to delete
     */
    void deleteSession(String sessionId);

    /**
     * Type of the repository.
     *
     * @return type of the repository.
     */
    RepositoryType type();
}
