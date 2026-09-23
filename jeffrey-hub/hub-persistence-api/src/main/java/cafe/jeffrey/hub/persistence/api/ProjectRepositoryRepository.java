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

package cafe.jeffrey.hub.persistence.api;

import cafe.jeffrey.hub.model.RepositoryInfo;
import cafe.jeffrey.hub.model.ProjectInstanceSessionInfo;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ProjectRepositoryRepository {

    void insert(RepositoryInfo repositoryInfo);

    List<RepositoryInfo> getAll();

    void delete(String id);

    void deleteAll();

    // Workspace Sessions Methods

    /**
     * Create a new workspace session.
     *
     * @param repositorySessionInfo the workspace session to create
     */
    void createSession(ProjectInstanceSessionInfo repositorySessionInfo);

    /**
     * Delete a workspace session by its session ID.
     *
     * @param sessionId the session ID to delete
     */
    void deleteSession(String sessionId);

    /**
     * Find all workspace sessions for a given project ID.
     *
     * @return list of workspace sessions for the project
     */
    List<ProjectInstanceSessionInfo> findAllSessions();

    /**
     * Find all sessions belonging to a specific instance within this project.
     * Filters at the SQL level for efficiency.
     *
     * @param instanceId the instance ID to filter by
     * @return list of sessions for the instance, newest first
     */
    List<ProjectInstanceSessionInfo> findSessionsByInstanceId(String instanceId);

    /**
     * Find a single workspace session by project ID and session ID.
     *
     * @param sessionId the session ID
     * @return the workspace session if it exists, otherwise an empty optional
     */
    Optional<ProjectInstanceSessionInfo> findSessionById(String sessionId);

    /**
     * Find all sessions that have not been marked as finished yet.
     * A session is considered unfinished if its finished_at column is NULL.
     *
     * @return list of unfinished sessions
     */
    List<ProjectInstanceSessionInfo> findUnfinishedSessions();

    /**
     * Find all unfinished sessions for a specific instance.
     * Filters at the SQL level for efficiency instead of fetching all and filtering in Java.
     *
     * @param instanceId the instance ID to filter by
     * @return list of unfinished sessions for the given instance
     */
    List<ProjectInstanceSessionInfo> findUnfinishedSessionsByInstanceId(String instanceId);

    /**
     * Mark a session as finished by setting the finished_at timestamp.
     *
     * @param sessionId  the session ID to mark as finished
     * @param finishedAt the timestamp when the session was detected as finished
     */
    void markSessionFinished(String sessionId, Instant finishedAt);

    /**
     * Mark a session as retained or release it again. A retained session is exempt
     * from every retention job, both age-based and quota-based.
     *
     * @param sessionId the session ID to update
     * @param retained  true to exempt the session from retention, false to release it
     */
    void setSessionRetained(String sessionId, boolean retained);
}
