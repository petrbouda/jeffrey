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

import cafe.jeffrey.hub.core.project.repository.FileLookup;
import cafe.jeffrey.hub.core.project.repository.InstanceSessions;
import cafe.jeffrey.hub.model.ProjectInstanceInfo;
import cafe.jeffrey.hub.model.repository.RecordingSession;
import cafe.jeffrey.hub.model.repository.RecordingSessionFilter;
import cafe.jeffrey.shared.volume.layout.SessionDeletion;

import java.util.List;
import java.util.Optional;

public interface RepositoryManager {

    /**
     * One session with its files, or empty when the project holds none by that id.
     */
    Optional<RecordingSession> findSession(String sessionId);

    /**
     * The sessions that satisfy the filter, newest first, each loaded with its files — the
     * figures a listing reports are read off them.
     *
     * @param filter the window, status and count constraints to apply
     */
    List<RecordingSession> listRecordingSessions(RecordingSessionFilter filter);

    /**
     * Every instance of the project, its status read off its newest session at this moment.
     */
    List<ProjectInstanceInfo> instances();

    /**
     * One instance with its sessions, newest first, each loaded with its files; empty when the
     * project holds no instance by that id.
     */
    Optional<InstanceSessions> instance(String instanceId);

    /**
     * Deletes a session, and its instance's directory when no other directory is left in it.
     * {@link SessionDeletion#NOT_FOUND} names a session already gone, which a caller counting
     * reclaimed bytes must not count.
     */
    SessionDeletion deleteRecordingSession(String recordingSessionId);

    /**
     * Marks a recording session as retained, exempting it from every retention rule,
     * or releases it again so normal retention resumes.
     *
     * @param recordingSessionId the session to update
     * @param retained           true to exempt the session from retention, false to release it
     * @return whether the project holds the session
     */
    boolean setSessionRetained(String recordingSessionId, boolean retained);

    /**
     * Looks up one file of a session for download, whatever kind it is: a recording chunk, a heap
     * dump, a log, a crash file.
     *
     * @return the path to serve, or the named reason the file is not served
     */
    FileLookup file(String sessionId, String fileId);
}
