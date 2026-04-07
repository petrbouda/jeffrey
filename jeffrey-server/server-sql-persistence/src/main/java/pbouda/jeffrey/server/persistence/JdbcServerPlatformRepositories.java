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

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import pbouda.jeffrey.server.persistence.model.SessionWithRepository;
import pbouda.jeffrey.server.persistence.repository.*;
import pbouda.jeffrey.shared.common.model.ProjectInstanceSessionInfo;
import pbouda.jeffrey.shared.common.model.RepositoryInfo;
import pbouda.jeffrey.shared.persistence.GroupLabel;
import pbouda.jeffrey.shared.persistence.StatementLabel;
import pbouda.jeffrey.shared.persistence.client.DatabaseClient;
import pbouda.jeffrey.shared.persistence.client.DatabaseClientProvider;

import java.time.Clock;
import java.util.Optional;

public class JdbcServerPlatformRepositories implements ServerPlatformRepositories {

    //language=SQL
    private static final String SELECT_SESSION_WITH_REPOSITORY = """
            SELECT rs.session_id AS session_id,
                   rs.repository_id AS repository_id,
                   rs.instance_id AS instance_id,
                   rs.session_order AS session_order,
                   rs.relative_session_path AS relative_session_path,
                   rs.profiler_settings AS profiler_settings,
                   rs.origin_created_at AS origin_created_at,
                   rs.created_at AS created_at,
                   rs.finished_at AS finished_at,
                   r.repository_type AS repository_type,
                   r.workspaces_path AS workspaces_path,
                   r.relative_workspace_path AS relative_workspace_path,
                   r.relative_project_path AS relative_project_path
            FROM project_instance_sessions rs
            JOIN repositories r ON rs.repository_id = r.repository_id
            WHERE rs.session_id = :session_id
            LIMIT 1""";

    private final DatabaseClientProvider databaseClientProvider;
    private final DatabaseClient databaseClient;
    private final Clock clock;

    public JdbcServerPlatformRepositories(DatabaseClientProvider databaseClientProvider, Clock clock) {
        this.databaseClientProvider = databaseClientProvider;
        this.databaseClient = databaseClientProvider.provide(GroupLabel.PROJECT_REPOSITORIES);
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

    @Override
    public Optional<SessionWithRepository> findSessionWithRepositoryById(String sessionId) {
        MapSqlParameterSource paramSource = new MapSqlParameterSource()
                .addValue("session_id", sessionId);

        return databaseClient.querySingle(
                StatementLabel.FIND_SESSION_WITH_REPOSITORY_BY_SESSION_ID,
                SELECT_SESSION_WITH_REPOSITORY,
                paramSource,
                (rs, rowNum) -> {
                    RepositoryInfo repositoryInfo = ServerMappers.repositoryInfoMapper().mapRow(rs, rowNum);
                    ProjectInstanceSessionInfo sessionInfo = ServerMappers.projectInstanceSessionMapper().mapRow(rs, rowNum);
                    return new SessionWithRepository(repositoryInfo, sessionInfo);
                });
    }

}
