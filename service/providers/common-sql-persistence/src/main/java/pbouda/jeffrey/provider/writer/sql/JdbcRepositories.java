/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
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

package pbouda.jeffrey.provider.writer.sql;

import pbouda.jeffrey.provider.api.repository.ProfileCacheRepository;
import pbouda.jeffrey.provider.api.repository.ProfileEventRepository;
import pbouda.jeffrey.provider.api.repository.ProfileEventTypeRepository;
import pbouda.jeffrey.provider.api.repository.ProfileGraphRepository;
import pbouda.jeffrey.provider.api.repository.ProfileRepository;
import pbouda.jeffrey.provider.api.repository.ProfilerRepository;
import pbouda.jeffrey.provider.api.repository.ProjectRecordingRepository;
import pbouda.jeffrey.provider.api.repository.ProjectRepository;
import pbouda.jeffrey.provider.api.repository.ProjectRepositoryRepository;
import pbouda.jeffrey.provider.api.repository.ProjectsRepository;
import pbouda.jeffrey.provider.api.repository.Repositories;
import pbouda.jeffrey.provider.api.repository.SchedulerRepository;
import pbouda.jeffrey.provider.api.repository.WorkspaceRepository;
import pbouda.jeffrey.provider.api.repository.WorkspacesRepository;
import pbouda.jeffrey.provider.writer.sql.client.DatabaseClientProvider;
import pbouda.jeffrey.provider.writer.sql.query.SQLFormatter;
import pbouda.jeffrey.provider.writer.sql.repository.JdbcGlobalSchedulerRepository;
import pbouda.jeffrey.provider.writer.sql.repository.JdbcProfileCacheRepository;
import pbouda.jeffrey.provider.writer.sql.repository.JdbcProfileEventRepository;
import pbouda.jeffrey.provider.writer.sql.repository.JdbcProfileEventTypeRepository;
import pbouda.jeffrey.provider.writer.sql.repository.JdbcProfileGraphRepository;
import pbouda.jeffrey.provider.writer.sql.repository.JdbcProfileRepository;
import pbouda.jeffrey.provider.writer.sql.repository.JdbcProfilerRepository;
import pbouda.jeffrey.provider.writer.sql.repository.JdbcProjectRecordingRepository;
import pbouda.jeffrey.provider.writer.sql.repository.JdbcProjectRepository;
import pbouda.jeffrey.provider.writer.sql.repository.JdbcProjectRepositoryRepository;
import pbouda.jeffrey.provider.writer.sql.repository.JdbcProjectSchedulerRepository;
import pbouda.jeffrey.provider.writer.sql.repository.JdbcProjectsRepository;
import pbouda.jeffrey.provider.writer.sql.repository.JdbcWorkspaceRepository;
import pbouda.jeffrey.provider.writer.sql.repository.JdbcWorkspacesRepository;

import java.time.Clock;

public class JdbcRepositories implements Repositories {

    private final SQLFormatter sqlFormatter;
    private final DatabaseClientProvider databaseClientProvider;
    private final Clock clock;

    public JdbcRepositories(SQLFormatter sqlFormatter, DatabaseClientProvider databaseClientProvider, Clock clock) {
        this.sqlFormatter = sqlFormatter;
        this.databaseClientProvider = databaseClientProvider;
        this.clock = clock;
    }

    @Override
    public ProfileEventRepository newEventRepository(String profileId) {
        return new JdbcProfileEventRepository(sqlFormatter, profileId, databaseClientProvider);
    }

    @Override
    public ProfileEventTypeRepository newEventTypeRepository(String profileId) {
        return new JdbcProfileEventTypeRepository(sqlFormatter, profileId, databaseClientProvider);
    }

    @Override
    public ProfileRepository newProfileRepository(String profileId) {
        return new JdbcProfileRepository(profileId, databaseClientProvider, clock);
    }

    @Override
    public ProfileCacheRepository newProfileCacheRepository(String profileId) {
        return new JdbcProfileCacheRepository(profileId, databaseClientProvider);
    }

    @Override
    public ProfileGraphRepository newProfileGraphRepository(String profileId) {
        return new JdbcProfileGraphRepository(profileId, databaseClientProvider);
    }

    @Override
    public ProjectRepository newProjectRepository(String projectId) {
        return new JdbcProjectRepository(projectId, databaseClientProvider, clock);
    }

    @Override
    public ProjectRecordingRepository newProjectRecordingRepository(String projectId) {
        return new JdbcProjectRecordingRepository(projectId, databaseClientProvider);
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
    public SchedulerRepository newGlobalSchedulerRepository() {
        return new JdbcGlobalSchedulerRepository(databaseClientProvider);
    }

    @Override
    public ProfilerRepository newProfilerRepository() {
        return new JdbcProfilerRepository(databaseClientProvider);
    }

    @Override
    public ProjectRepositoryRepository newProjectRepositoryRepository(String projectId) {
        return new JdbcProjectRepositoryRepository(projectId, databaseClientProvider);
    }

    @Override
    public WorkspaceRepository newWorkspaceRepository(String workspaceId) {
        return new JdbcWorkspaceRepository(workspaceId, databaseClientProvider, clock);
    }

    @Override
    public WorkspacesRepository newWorkspacesRepository() {
        return new JdbcWorkspacesRepository(databaseClientProvider);
    }
}
