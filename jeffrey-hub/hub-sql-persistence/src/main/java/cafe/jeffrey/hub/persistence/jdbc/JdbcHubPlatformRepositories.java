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

package cafe.jeffrey.hub.persistence.jdbc;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import cafe.jeffrey.hub.persistence.api.SessionWithRepository;
import cafe.jeffrey.hub.persistence.api.HubPlatformRepositories;
import cafe.jeffrey.hub.persistence.api.ProjectInstanceRepository;
import cafe.jeffrey.hub.persistence.api.ProjectRepository;
import cafe.jeffrey.hub.persistence.api.ProjectRepositoryRepository;
import cafe.jeffrey.hub.persistence.api.ProjectsRepository;
import cafe.jeffrey.hub.persistence.api.SessionWithRepository;
import cafe.jeffrey.hub.persistence.api.WorkspaceRepository;
import cafe.jeffrey.hub.persistence.api.WorkspacesRepository;
import cafe.jeffrey.hub.model.ProjectInstanceInfo;
import cafe.jeffrey.hub.model.ProjectInstanceInfo.ProjectInstanceStatus;
import cafe.jeffrey.hub.model.ProjectInstanceSessionInfo;
import cafe.jeffrey.hub.model.RepositoryInfo;
import cafe.jeffrey.shared.persistence.GroupLabel;
import cafe.jeffrey.shared.persistence.StatementLabel;
import cafe.jeffrey.shared.persistence.client.DatabaseClient;
import cafe.jeffrey.shared.persistence.client.DatabaseClientProvider;

import java.time.Clock;
import java.util.List;
import java.util.Optional;

public class JdbcHubPlatformRepositories implements HubPlatformRepositories {

    //language=SQL
    private static final String SELECT_INSTANCE_BY_ID = HubMappers.INSTANCE_WITH_COUNTS + """
            WHERE i.instance_id = :instance_id
            """ + HubMappers.GROUP_BY_INSTANCE;

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
                   rs.retained AS retained,
                   rs.heartbeat_expected AS heartbeat_expected,
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

    public JdbcHubPlatformRepositories(DatabaseClientProvider databaseClientProvider, Clock clock) {
        this.databaseClientProvider = databaseClientProvider;
        this.databaseClient = databaseClientProvider.provide(GroupLabel.PROJECT_REPOSITORIES);
        this.clock = clock;
    }

    @Override
    public ProjectRepository newProjectRepository(String projectId) {
        return new JdbcProjectRepository(clock, projectId, databaseClientProvider);
    }

    @Override
    public ProjectsRepository newProjectsRepository() {
        return new JdbcProjectsRepository(databaseClientProvider);
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
                    RepositoryInfo repositoryInfo = HubMappers.repositoryInfoMapper().mapRow(rs, rowNum);
                    ProjectInstanceSessionInfo sessionInfo = HubMappers.projectInstanceSessionMapper().mapRow(rs, rowNum);
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
                            HubMappers.instant(rs, "started_at"),
                            HubMappers.instant(rs, "finished_at"),
                            HubMappers.instant(rs, "expiring_at"),
                            HubMappers.instant(rs, "expired_at"),
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
                HubMappers.projectInstanceSessionMapper());
    }

    @Override
    public List<ProjectInstanceSessionInfo> findSessionsByProjectId(String projectId) {
        MapSqlParameterSource paramSource = new MapSqlParameterSource()
                .addValue("project_id", projectId);

        return databaseClient.query(
                StatementLabel.FIND_PROJECT_INSTANCE_SESSIONS_BY_PROJECT_ID,
                SELECT_SESSIONS_BY_PROJECT_ID,
                paramSource,
                HubMappers.projectInstanceSessionMapper());
    }

}
