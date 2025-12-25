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

import pbouda.jeffrey.common.model.ProjectInfo;
import pbouda.jeffrey.common.model.RepositoryInfo;
import pbouda.jeffrey.common.model.repository.RecordingSession;
import pbouda.jeffrey.common.model.workspace.RepositorySessionInfo;
import pbouda.jeffrey.common.model.workspace.WorkspaceEventCreator;
import pbouda.jeffrey.profile.manager.model.RepositoryStatistics;
import pbouda.jeffrey.profile.manager.model.StreamedRecordingFile;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

public interface RepositoryManager {

    @FunctionalInterface
    interface Factory extends Function<ProjectInfo, RepositoryManager> {
    }

    /**
     * Downloads file of every type (even non-recording, e.g. Heap Dump) from the repository.
     *
     * @param sessionId  id of the session to download from
     * @param artifactId id of the artifact to download
     * @return entity for file information and streaming to output stream
     */
    StreamedRecordingFile streamArtifact(String sessionId, String artifactId);

    /**
     * Downloads recordings from the repository and merge them into a single file.
     *
     * @param sessionId        id of the session to download from
     * @param recordingFileIds ids of recordings to merge and download
     * @return entity for file information and streaming to output stream
     */
    StreamedRecordingFile mergeAndStreamRecordings(String sessionId, List<String> recordingFileIds);

    /**
     * Finds a recording session by its ID.
     *
     * @param recordingSessionId the ID of the recording session to find
     * @return an Optional containing the RecordingSession if found, or empty if not found
     */
    Optional<RecordingSession> findRecordingSessions(String recordingSessionId);

    /**
     * Lists all recording sessions in the repository.
     *
     * @param withFiles whether to include file details in the sessions
     * @return list of recording sessions
     */
    List<RecordingSession> listRecordingSessions(boolean withFiles);

    /**
     * Calculates comprehensive repository statistics including session counts,
     * file type distributions, sizes, and activity timestamps.
     *
     * @return repository statistics containing all calculated metrics
     */
    RepositoryStatistics calculateRepositoryStatistics();

    /**
     * Create a new repository for the project.
     *
     * @param projectRepository the repository information to create
     */
    void create(RepositoryInfo projectRepository);

    /**
     * Create a new workspace session.
     *
     * @param repositorySessionInfo the workspace session to create
     */
    void createSession(RepositorySessionInfo repositorySessionInfo);

    Optional<RepositoryInfo> info();

    void deleteRecordingSession(String recordingSessionId, WorkspaceEventCreator createdBy);

    void deleteFilesInSession(String recordingSessionId, List<String> fileIds);

    void delete();
}
