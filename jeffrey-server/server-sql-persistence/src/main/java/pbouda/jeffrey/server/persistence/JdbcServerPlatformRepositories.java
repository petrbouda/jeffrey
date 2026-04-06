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

package pbouda.jeffrey.server.persistence;

import pbouda.jeffrey.server.persistence.repository.*;
import pbouda.jeffrey.shared.persistence.client.DatabaseClientProvider;

import java.time.Clock;

public class JdbcServerPlatformRepositories implements ServerPlatformRepositories {

    private final DatabaseClientProvider databaseClientProvider;
    private final Clock clock;

    public JdbcServerPlatformRepositories(DatabaseClientProvider databaseClientProvider, Clock clock) {
        this.databaseClientProvider = databaseClientProvider;
        this.clock = clock;
    }

    @Override
    public ProfilerRepository newProfilerRepository() {
        return new JdbcProfilerRepository(databaseClientProvider);
    }

    @Override
    public ProjectRepository newProjectRepository(String projectId) {
        return new JdbcProjectRepository(projectId, databaseClientProvider);
    }

    @Override
    public ProjectsRepository newProjectsRepository() {
        return new JdbcProjectsRepository(databaseClientProvider);
    }

    @Override
    public SchedulerRepository newProjectSchedulerRepository(String projectId) {
        return new JdbcProjectSchedulerRepository(projectId, databaseClientProvider);
    }

    @Override
    public ProjectRepositoryRepository newProjectRepositoryRepository(String projectId) {
        return new JdbcProjectRepositoryRepository(clock, projectId, databaseClientProvider);
    }

    @Override
    public WorkspaceRepository newWorkspaceRepository(String workspaceId) {
        return new JdbcWorkspaceRepository(workspaceId, databaseClientProvider);
    }

    @Override
    public WorkspacesRepository newWorkspacesRepository() {
        return new JdbcWorkspacesRepository(databaseClientProvider);
    }

    @Override
    public ProjectInstanceRepository newProjectInstanceRepository(String projectId) {
        return new JdbcProjectInstanceRepository(projectId, databaseClientProvider);
    }

}
