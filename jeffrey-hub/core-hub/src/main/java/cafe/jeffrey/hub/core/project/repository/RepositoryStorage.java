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
import java.util.function.Function;

public interface RepositoryStorage {

    @FunctionalInterface
    interface Factory extends Function<ProjectInfo, RepositoryStorage> {
    }

    /**
     * Every session of the project, newest first.
     *
     * @param detail whether to walk each session's directory for its files too
     */
    List<RecordingSession> listSessions(SessionDetail detail);

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
     * Every instance of the project with how many sessions it holds, at this moment.
     */
    List<ProjectInstanceInfo> instances();

    /**
     * Deletes an instance's directory with whatever is left in it.
     *
     * @param instanceId the unique identifier of the instance whose directory to delete
     */
    void deleteInstanceDirectory(String instanceId);

    // ========== Session Compression ==========

    /**
     * The newest {@code perInstance} sessions of every instance, with their files: only those
     * sessions are read. What the compression job works on.
     */
    List<RecordingSession> newestSessions(int perInstance);

    /**
     * Compresses the session's JFR chunks to LZ4 and deletes the originals; the newest recording
     * of an instance's newest session is left alone ({@link RecordingSession#openRecording()}).
     * Used by the compression job.
     *
     * @param session the session, loaded with its files
     * @return number of files compressed
     */
    int compressSession(RecordingSession session);
}
