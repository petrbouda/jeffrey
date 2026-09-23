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
import cafe.jeffrey.hub.model.RepositoryInfo;
import cafe.jeffrey.hub.model.repository.RecordingSession;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

public interface RepositoryStorage {

    @FunctionalInterface
    interface Factory extends Function<ProjectInfo, RepositoryStorage> {
    }

    /**
     * Information about the repository.
     *
     * @return information about the repository.
     */
    RepositoryInfo repositoryInfo();

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
     * Deletes a specific recording session and all its associated recordings
     * from the repository.
     *
     * @param sessionId the unique identifier of the recording session to delete
     */
    void deleteSession(String sessionId);

    /**
     * Deletes an instance's directory (including its marker file) from the repository.
     * Removing the on-disk declaration is what makes an instance deletion final: the
     * workspace reconciler re-creates any instance whose directory still exists.
     *
     * @param instanceId the unique identifier of the instance whose directory to delete
     */
    void deleteInstanceDirectory(String instanceId);

    /**
     * Deletes the whole project directory (including its marker file) from the repository.
     * Removing the on-disk declaration is what makes a project deletion final: the
     * workspace reconciler re-creates any project whose directory still exists. A no-op
     * when the project has no repository or the directory is already gone.
     */
    void deleteProjectDirectory();



    // ========== Files ==========

    /**
     * Where one file of a session is on disk, exactly as it lies.
     *
     * <p>One lookup for every kind of file. There were two — one for recordings, one for
     * artifacts — and by the end they differed only in the word in their refusal. What a file's
     * category decides is what a reader does with it, and the reader knows the category already;
     * it does not need a separate door to be told.
     *
     * <p>Refuses rather than returns nothing, so "not found" means not found. A file the session
     * does not hold, a transient one, one no longer on disk, the chunk the profiler is still
     * writing, and an empty recording are each named with the reason, because a caller that asked
     * for one file and got silence cannot tell which of those happened. An empty <em>artifact</em>
     * is served: emptiness is a statement about parsing, and a log with nothing in it is an
     * answer.
     *
     * @param sessionId the session ID
     * @param fileId    the file's id, as the session's listing reports it
     * @return the path the file has now, which is the name the caller must use for it
     * @throws IllegalArgumentException when the session has no such file, or it is one the hub
     *                                  will not hand over
     */
    Path file(String sessionId, String fileId);

    // ========== Session Compression ==========

    /**
     * Compresses FINISHED JFR recordings in the session and deletes originals.
     * <p>
     * This is used by the scheduler job to save disk space. Files that are already
     * compressed (JFR_LZ4) are skipped. The original JFR files are deleted after
     * successful compression.
     * <p>
     * For ACTIVE sessions, the latest few recording files are skipped because
     * async-profiler may still be flushing data into them.
     * </p>
     *
     * @param sessionId the session ID to compress
     * @return number of files compressed
     */
    int compressSession(String sessionId);
}
