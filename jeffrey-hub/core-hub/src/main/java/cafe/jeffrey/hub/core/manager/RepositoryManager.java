/*
 * Jeffrey
 * Copyright (C) 2024 Petr Bouda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package cafe.jeffrey.hub.core.manager;

import cafe.jeffrey.hub.core.project.repository.SessionDetail;
import cafe.jeffrey.hub.model.ProjectInfo;
import cafe.jeffrey.hub.model.RepositoryInfo;
import cafe.jeffrey.hub.model.repository.RecordingSession;
import cafe.jeffrey.hub.model.repository.RecordingSessionFilter;
import cafe.jeffrey.hub.model.ProjectInstanceSessionInfo;
import cafe.jeffrey.hub.model.repository.RepositoryStatistics;
import cafe.jeffrey.hub.model.repository.StreamedFile;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

public interface RepositoryManager {

    @FunctionalInterface
    interface Factory extends Function<ProjectInfo, RepositoryManager> {
    }

    /**
     * Finds a recording session by its ID.
     *
     * @param recordingSessionId the ID of the recording session to find
     * @return an Optional containing the RecordingSession if found, or empty if not found
     */
    Optional<RecordingSession> findRecordingSessions(String recordingSessionId);

    /**
     * Lists all recording sessions in the repository, newest first.
     *
     * @param detail whether to load each session's files as well
     * @return list of recording sessions
     */
    default List<RecordingSession> listRecordingSessions(SessionDetail detail) {
        return listRecordingSessions(detail, RecordingSessionFilter.ALL);
    }

    /**
     * Lists the recording sessions that satisfy the filter, newest first.
     *
     * @param detail    whether to load each session's files as well in the sessions
     * @param filter    the window, status and count constraints to apply
     * @return list of matching recording sessions
     */
    List<RecordingSession> listRecordingSessions(SessionDetail detail, RecordingSessionFilter filter);

    /**
     * Calculates comprehensive repository statistics including session counts,
     * file type distributions, sizes, and activity timestamps.
     *
     * @return repository statistics containing all calculated metrics
     */
    RepositoryStatistics calculateRepositoryStatistics();

    /**
     * One instance's sessions with their files, newest first: one walk of that instance's
     * directories, from which a caller derives its statistics and which of its sessions
     * finished empty.
     */
    List<RecordingSession> instanceSessions(String instanceId);

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

    /**
     * @return whether a session of that id existed and was deleted; {@code false} names a
     * session already gone, which a caller counting reclaimed bytes must not count
     */
    boolean deleteRecordingSession(String recordingSessionId);

    void deleteFilesInSession(String recordingSessionId, List<String> fileIds);

    /**
     * Marks a recording session as retained, exempting it from every retention job,
     * or releases it again so normal retention resumes.
     *
     * @param recordingSessionId the session to update
     * @param retained           true to exempt the session from retention, false to release it
     */
    void setSessionRetained(String recordingSessionId, boolean retained);

    /**
     * Opens one file of a session for streaming — a recording chunk, a heap dump, a log, a crash
     * file. One method for every kind: the category decides what a reader does with the file, not
     * whether the hub serves it.
     *
     * @throws IllegalArgumentException when the session has no such file, or it is one the hub
     *                                  will not hand over
     */
    StreamedFile streamFile(String sessionId, String fileId);

    void delete();
}
