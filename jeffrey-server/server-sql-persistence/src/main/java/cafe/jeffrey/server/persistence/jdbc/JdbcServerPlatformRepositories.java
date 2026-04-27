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

package cafe.jeffrey.server.persistence.jdbc;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import cafe.jeffrey.server.persistence.model.SessionWithRepository;
import cafe.jeffrey.server.persistence.repository.*;
import cafe.jeffrey.server.persistence.repository.jdbc.JdbcProfilerRepository;
import cafe.jeffrey.server.persistence.repository.jdbc.JdbcProjectInstanceRepository;
import cafe.jeffrey.server.persistence.repository.jdbc.JdbcProjectRepository;
import cafe.jeffrey.server.persistence.repository.jdbc.JdbcProjectRepositoryRepository;
import cafe.jeffrey.server.persistence.repository.jdbc.JdbcProjectSchedulerRepository;
import cafe.jeffrey.server.persistence.repository.jdbc.JdbcProjectsRepository;
import cafe.jeffrey.server.persistence.repository.jdbc.JdbcWorkspaceRepository;
import cafe.jeffrey.server.persistence.repository.jdbc.JdbcWorkspacesRepository;
import cafe.jeffrey.server.persistence.repository.jdbc.ServerMappers;
import cafe.jeffrey.shared.common.model.ProjectInstanceInfo;
import cafe.jeffrey.shared.common.model.ProjectInstanceInfo.ProjectInstanceStatus;
import cafe.jeffrey.shared.common.model.ProjectInstanceSessionInfo;
import cafe.jeffrey.shared.common.model.RepositoryInfo;
import cafe.jeffrey.shared.persistence.GroupLabel;
import cafe.jeffrey.shared.persistence.StatementLabel;
import cafe.jeffrey.shared.persistence.client.DatabaseClient;
import cafe.jeffrey.shared.persistence.client.DatabaseClientProvider;

import java.time.Clock;
import java.util.List;
import java.util.Optional;

public class JdbcServerPlatformRepositories implements ServerPlatformRepositories {

    //language=SQL
    private static final String SELECT_INSTANCE_BY_ID = """
            SELECT i.*,
                   COUNT(rs.session_id) as session_count,
                   (SELECT rs2.session_id FROM project_instance_sessions rs2
                    WHERE rs2.instance_id = i.instance_id AND rs2.finished_at IS NULL
                    ORDER BY rs2.created_at DESC LIMIT 1) as active_session_id
            FROM project_instances i
            LEFT JOIN project_instance_sessions rs ON rs.instance_id = i.instance_id
            WHERE i.instance_id = :instance_id
            GROUP BY i.instance_id, i.project_id, i.instance_name, i.status,
                     i.started_at, i.finished_at, i.expiring_at, i.expired_at""";

    //language=SQL
    private static final String SELECT_SESSIONS_BY_INSTANCE_ID = """
            SELECT rs.* FROM project_instance_sessions rs
            WHERE rs.instance_id = :instance_id
            ORDER BY rs.created_at DESC""";

    //language=SQL
    private static final String SELECT_SESSIONS_BY_PROJECT_ID = """
            SELECT rs.* FROM project_instance_sessions rs
            JOIN project_instances i ON rs.instance_id = i.instance_id
            WHERE i.project_id = :project_id
            ORDER BY rs.instance_id, rs.created_at DESC""";

    //language=SQL
    private static final String SELECT_SESSION_WITH_REPOSITORY = """
            SELECT r.project_id AS project_id,
                   rs.session_id AS session_id,
                   rs.repository_id AS repository_id,
                   rs.instance_id AS instance_id,
                   rs.session_order AS session_order,
                   rs.relative_session_path AS relative_session_path,
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
                    String projectId = rs.getString("project_id");
                    RepositoryInfo repositoryInfo = ServerMappers.repositoryInfoMapper().mapRow(rs, rowNum);
                    ProjectInstanceSessionInfo sessionInfo = ServerMappers.projectInstanceSessionMapper().mapRow(rs, rowNum);
                    return new SessionWithRepository(projectId, repositoryInfo, sessionInfo);
                });
    }

    @Override
    public Optional<ProjectInstanceInfo> findInstanceById(String instanceId) {
        MapSqlParameterSource paramSource = new MapSqlParameterSource()
                .addValue("instance_id", instanceId);

        return databaseClient.querySingle(
                StatementLabel.FIND_INSTANCE_BY_ID,
                SELECT_INSTANCE_BY_ID,
                paramSource,
                (rs, _) -> {
                    String statusStr = rs.getString("status");
                    ProjectInstanceStatus status = ProjectInstanceStatus.valueOf(statusStr);

                    return new ProjectInstanceInfo(
                            rs.getString("instance_id"),
                            rs.getString("project_id"),
                            rs.getString("instance_name"),
                            status,
                            ServerMappers.instant(rs, "started_at"),
                            ServerMappers.instant(rs, "finished_at"),
                            ServerMappers.instant(rs, "expiring_at"),
                            ServerMappers.instant(rs, "expired_at"),
                            rs.getInt("session_count"),
                            rs.getString("active_session_id"));
                });
    }

    @Override
    public List<ProjectInstanceSessionInfo> findSessionsByInstanceId(String instanceId) {
        MapSqlParameterSource paramSource = new MapSqlParameterSource()
                .addValue("instance_id", instanceId);

        return databaseClient.query(
                StatementLabel.FIND_SESSIONS_BY_INSTANCE_ID,
                SELECT_SESSIONS_BY_INSTANCE_ID,
                paramSource,
                ServerMappers.projectInstanceSessionMapper());
    }

    @Override
    public List<ProjectInstanceSessionInfo> findSessionsByProjectId(String projectId) {
        MapSqlParameterSource paramSource = new MapSqlParameterSource()
                .addValue("project_id", projectId);

        return databaseClient.query(
                StatementLabel.FIND_PROJECT_INSTANCE_SESSIONS_BY_PROJECT_ID,
                SELECT_SESSIONS_BY_PROJECT_ID,
                paramSource,
                ServerMappers.projectInstanceSessionMapper());
    }

}
