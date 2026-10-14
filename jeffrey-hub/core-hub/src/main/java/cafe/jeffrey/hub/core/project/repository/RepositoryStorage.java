/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
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

package cafe.jeffrey.hub.core.project.repository;

import cafe.jeffrey.hub.model.ProjectInfo;
import cafe.jeffrey.hub.model.ProjectInstanceInfo;
import cafe.jeffrey.hub.model.repository.RecordingSession;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

public interface RepositoryStorage {

    @FunctionalInterface
    interface Factory extends Function<ProjectInfo, RepositoryStorage> {
    }

    /**
     * Lists of files for the given session.
     *
     * @param sessionId id of the session to list files for
     * @param detail    whether to walk the session directory for its files too
     * @return list of recordings for the given session
     */
    Optional<RecordingSession> singleSession(String sessionId, SessionDetail detail);

    /**
     * Lists all recording sessions available in the repository.
     *
     * @param detail    whether to walk the session directory for its files too
     *                  (e.g., recordings, metadata files)
     * @return a list of all recording sessions, each containing metadata and
     * associated recordings
     */
    List<RecordingSession> listSessions(SessionDetail detail);

    /**
     * The same session loaded with its files — for a session picked from a headers listing,
     * so that a filtered listing walks only the directories of the sessions it keeps.
     */
    RecordingSession withFiles(RecordingSession session);

    /**
     * Lists recording sessions that belong to the given instance. Walks only
     * the matching sessions' directories — does not scan the whole repository.
     *
     * @param instanceId the instance whose sessions should be returned
     * @param detail     whether to walk the session directory for its files too
     * @return recording sessions for the instance, newest first
     */
    List<RecordingSession> listSessionsByInstanceId(String instanceId, SessionDetail detail);

    /**
     * Deletes specific repository files from the repository.
     *
     * @param sessionId         the unique identifier of the recording session
     * @param repositoryFileIds the list of unique identifiers of the repository files to delete
     */
    void deleteRepositoryFiles(String sessionId, List<String> repositoryFileIds);

    /**
     * Deletes a session's directory with everything in it.
     *
     * @return whether the session was there to delete
     */
    boolean deleteSession(String sessionId);

    /**
     * Every instance of the project, its status read off its newest session at this moment.
     */
    List<ProjectInstanceInfo> instances();

    /**
     * One instance of the project, or empty when the project holds none by that id.
     */
    Optional<ProjectInstanceInfo> instance(String instanceId);

    /**
     * Pins a session against every retention rule, or releases it, by a flag file in its directory.
     *
     * @return whether the project holds the session
     */
    boolean setRetained(String sessionId, boolean retained);

    /**
     * Deletes an instance's directory, its marker file included.
     *
     * @param instanceId the unique identifier of the instance whose directory to delete
     */
    void deleteInstanceDirectory(String instanceId);

    /** Deletes the whole project directory, its marker file included; a no-op when it is gone. */
    void deleteProjectDirectory();

    // ========== Files ==========

    /**
     * Looks up one file of a session for download, whatever kind it is. A lookup only: it neither
     * compresses nor opens the file.
     *
     * @param sessionId the session ID
     * @param fileId    the file's id, as the session's listing reports it
     * @return the path the file has now, or the named reason it is not served
     */
    FileLookup file(String sessionId, String fileId);

    // ========== Session Compression ==========

    /**
     * Compresses the session's closed JFR chunks to LZ4 and deletes the originals; for a live
     * session the chunk the profiler is still writing is left alone. Used by the compression job.
     *
     * @param sessionId the session ID to compress
     * @return number of files compressed
     */
    int compressSession(String sessionId);
}
