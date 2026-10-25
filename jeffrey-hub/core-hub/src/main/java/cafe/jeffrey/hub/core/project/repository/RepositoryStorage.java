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
import cafe.jeffrey.shared.volume.layout.SessionDeletion;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

public interface RepositoryStorage {

    @FunctionalInterface
    interface Factory extends Function<ProjectInfo, RepositoryStorage> {
    }

    /**
     * One session with its files, or empty when the project holds none by that id.
     */
    Optional<RecordingSession> session(String sessionId);

    /**
     * Every session of the project, newest first.
     *
     * @param detail whether to walk each session's directory for its files too
     */
    List<RecordingSession> listSessions(SessionDetail detail);

    /**
     * The same session loaded with its files — for a session picked from a headers listing,
     * so that a filtered listing walks only the directories of the sessions it keeps.
     */
    RecordingSession withFiles(RecordingSession session);

    /**
     * Deletes the named files of a session.
     *
     * @param fileIds the files' ids, as the session's listing reports them
     */
    void deleteRepositoryFiles(String sessionId, List<String> fileIds);

    /**
     * Deletes a session's directory with everything in it, and its instance's directory when no
     * other directory is left in it.
     */
    SessionDeletion deleteSession(String sessionId);

    /**
     * Every instance of the project, its status read off its newest session at this moment.
     */
    List<ProjectInstanceInfo> instances();

    /**
     * One instance with its sessions (without their files), or empty when the project holds no
     * instance by that id. Walks that instance's directories alone.
     */
    Optional<InstanceSessions> instanceWithSessions(String instanceId);

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
