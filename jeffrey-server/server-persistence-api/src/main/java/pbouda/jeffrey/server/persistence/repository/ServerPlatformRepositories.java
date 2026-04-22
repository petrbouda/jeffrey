/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
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

package pbouda.jeffrey.server.persistence.repository;

import pbouda.jeffrey.server.persistence.model.SessionWithRepository;
import pbouda.jeffrey.shared.common.model.ProjectInstanceInfo;
import pbouda.jeffrey.shared.common.model.ProjectInstanceSessionInfo;

import java.util.List;
import java.util.Optional;

/**
 * Factory interface for server-level platform repositories.
 * Provides access to server-specific repositories (workspaces, projects, instances,
 * sessions, scheduler, profiler settings).
 */
public interface ServerPlatformRepositories {

    ProfilerRepository newProfilerRepository();

    ProjectRepository newProjectRepository(String projectId);

    ProjectsRepository newProjectsRepository();

    SchedulerRepository newProjectSchedulerRepository(String projectId);

    ProjectRepositoryRepository newProjectRepositoryRepository(String projectId);

    WorkspaceRepository newWorkspaceRepository(String workspaceId);

    WorkspacesRepository newWorkspacesRepository();

    ProjectInstanceRepository newProjectInstanceRepository(String projectId);

    /**
     * Resolves a session by id alone, returning both the session and its parent
     * repository in a single query. Used by flows that only have a session id and
     * need the repository context to compute filesystem paths (e.g. event streaming).
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
