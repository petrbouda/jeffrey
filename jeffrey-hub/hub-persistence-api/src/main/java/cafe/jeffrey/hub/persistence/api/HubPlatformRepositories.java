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

import cafe.jeffrey.hub.model.ProjectInstanceInfo;
import cafe.jeffrey.hub.model.ProjectInstanceSessionInfo;

import java.util.List;
import java.util.Optional;

/**
 * Factory interface for hub-level platform repositories.
 * Provides access to hub-specific repositories (workspaces, projects, instances,
 * sessions, scheduler).
 */
public interface HubPlatformRepositories {

    ProjectRepository newProjectRepository(String projectId);

    ProjectsRepository newProjectsRepository();

    ProjectRepositoryRepository newProjectRepositoryRepository(String projectId);

    WorkspaceRepository newWorkspaceRepository(String workspaceId);

    WorkspacesRepository newWorkspacesRepository();

    ProjectInstanceRepository newProjectInstanceRepository(String projectId);

    /**
     * Resolves a session by id alone, returning both the session and its parent
     * repository in a single query. Used by flows that only have a session id and
     * need the repository context to compute filesystem paths (e.g. file download).
     *
     * @param sessionId the session id
     * @return the session joined with its repository, or empty if no session matches
     */
    Optional<SessionWithRepository> findSessionWithRepositoryById(String sessionId);

    /**
     * Finds a project instance by its globally unique ID, without requiring project scope.
     */
    Optional<ProjectInstanceInfo> findInstanceById(String instanceId);

    /**
     * Finds all sessions for a project instance by its globally unique instance ID.
     */
    List<ProjectInstanceSessionInfo> findSessionsByInstanceId(String instanceId);

    /**
     * Finds every session across every instance of a project in a single query.
     * Used to populate ListInstancesResponse with embedded sessions without N+1 fan-out.
     * Returned rows carry their instance_id so callers can group them in memory.
     */
    List<ProjectInstanceSessionInfo> findSessionsByProjectId(String projectId);

}
