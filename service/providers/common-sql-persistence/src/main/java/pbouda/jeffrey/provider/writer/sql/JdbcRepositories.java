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

import javax.sql.DataSource;
import java.time.Clock;

public class JdbcRepositories implements Repositories {

    private final SQLFormatter sqlFormatter;
    private final DataSource dataSource;
    private final Clock clock;

    public JdbcRepositories(SQLFormatter sqlFormatter, DataSource dataSource, Clock clock) {
        this.sqlFormatter = sqlFormatter;
        this.dataSource = dataSource;
        this.clock = clock;
    }

    @Override
    public ProfileEventRepository newEventRepository(String profileId) {
        return new JdbcProfileEventRepository(sqlFormatter, profileId, dataSource);
    }

    @Override
    public ProfileEventTypeRepository newEventTypeRepository(String profileId) {
        return new JdbcProfileEventTypeRepository(sqlFormatter, profileId, dataSource);
    }

    @Override
    public ProfileRepository newProfileRepository(String profileId) {
        return new JdbcProfileRepository(profileId, dataSource, clock);
    }

    @Override
    public ProfileCacheRepository newProfileCacheRepository(String profileId) {
        return new JdbcProfileCacheRepository(profileId, dataSource);
    }

    @Override
    public ProfileGraphRepository newProfileGraphRepository(String profileId) {
        return new JdbcProfileGraphRepository(profileId, dataSource);
    }

    @Override
    public ProjectRepository newProjectRepository(String projectId) {
        return new JdbcProjectRepository(projectId, dataSource, clock);
    }

    @Override
    public ProjectRecordingRepository newProjectRecordingRepository(String projectId) {
        return new JdbcProjectRecordingRepository(projectId, dataSource);
    }

    @Override
    public ProjectsRepository newProjectsRepository() {
        return new JdbcProjectsRepository(dataSource);
    }

    @Override
    public SchedulerRepository newProjectSchedulerRepository(String projectId) {
        return new JdbcProjectSchedulerRepository(projectId, dataSource);
    }

    @Override
    public SchedulerRepository newGlobalSchedulerRepository() {
        return new JdbcGlobalSchedulerRepository(dataSource);
    }

    @Override
    public ProfilerRepository newProfilerRepository() {
        return new JdbcProfilerRepository(dataSource);
    }

    @Override
    public ProjectRepositoryRepository newProjectRepositoryRepository(String projectId) {
        return new JdbcProjectRepositoryRepository(projectId, dataSource);
    }

    @Override
    public WorkspaceRepository newWorkspaceRepository(String workspaceId) {
        return new JdbcWorkspaceRepository(workspaceId, dataSource, clock);
    }

    @Override
    public WorkspacesRepository newWorkspacesRepository() {
        return new JdbcWorkspacesRepository(dataSource);
    }
}
