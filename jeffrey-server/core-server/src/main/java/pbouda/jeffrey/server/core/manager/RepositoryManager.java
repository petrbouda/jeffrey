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

package pbouda.jeffrey.server.core.manager;

import tools.jackson.databind.node.ObjectNode;
import pbouda.jeffrey.shared.common.model.ProjectInfo;
import pbouda.jeffrey.shared.common.model.RepositoryInfo;
import pbouda.jeffrey.shared.common.model.repository.RecordingSession;
import pbouda.jeffrey.shared.common.model.ProjectInstanceSessionInfo;
import pbouda.jeffrey.shared.common.model.workspace.WorkspaceEventCreator;
import pbouda.jeffrey.shared.common.model.repository.InstanceStats;
import pbouda.jeffrey.shared.common.model.repository.RepositoryStatistics;
import pbouda.jeffrey.shared.common.model.repository.StreamedRecordingFile;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

public interface RepositoryManager {

    @FunctionalInterface
    interface Factory extends Function<ProjectInfo, RepositoryManager> {
    }

    /**
     * Downloads an artifact file (heap dump, log, etc.) from the repository with validation.
     * Only FINISHED, non-TEMPORARY artifact files can be streamed.
     *
     * @param sessionId id of the session to download from
     * @param fileId    id of the artifact file to download
     * @return entity for file information and streaming to output stream
     * @throws IllegalArgumentException if the file is not found, not finished, or not an artifact
     */
    StreamedRecordingFile streamArtifactFile(String sessionId, String fileId);

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
     * Aggregates storage statistics (file count and total size) for a single
     * instance by walking only its session directories on disk.
     *
     * @param instanceId the instance to compute stats for
     * @return file count and total size across all sessions of that instance
     */
    InstanceStats instanceStats(String instanceId);

    /**
     * Parses the latest finished JFR chunk belonging to the session and
     * returns the one-shot configuration / environment events it contains as
     * a JSON map keyed by JFR event type name (see
     * {@code InstanceEnvironmentParser}).
     *
     * @param sessionId the session to parse the environment for
     * @param expectShutdown when {@code true}, the parser looks for the
     *                       {@code jdk.Shutdown} event (present only in the
     *                       final chunk of a FINISHED session).
     * @return the parsed environment as an {@link ObjectNode}, or empty if no
     *         finished recording chunk exists yet for this session
     */
    Optional<ObjectNode> sessionEnvironment(String sessionId, boolean expectShutdown);

    /**
     * Create a new repository for the project.
     *
     * @param projectRepository the repository information to create
     */
    void create(RepositoryInfo projectRepository);

    /**
     * Create a new project instance session.
     *
     * @param projectInstanceSessionInfo the project instance session to create
     */
    void createSession(ProjectInstanceSessionInfo projectInstanceSessionInfo);

    Optional<RepositoryInfo> info();

    void deleteRecordingSession(String recordingSessionId, WorkspaceEventCreator createdBy);

    void deleteFilesInSession(String recordingSessionId, List<String> fileIds);

    /**
     * Downloads a recording file (JFR) from the repository with validation.
     * Only FINISHED, non-TEMPORARY recording files can be streamed.
     *
     * @param sessionId the session containing the file
     * @param fileId    the unique file ID
     * @return entity for file information and streaming
     * @throws IllegalArgumentException if the file is not found, not finished, or not a recording
     */
    StreamedRecordingFile streamRecordingFile(String sessionId, String fileId);

    void delete();
}
