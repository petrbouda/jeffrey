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

package pbouda.jeffrey.provider.platform.repository;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import pbouda.jeffrey.shared.common.IDGenerator;
import pbouda.jeffrey.shared.common.model.ProjectInstanceSessionInfo;
import pbouda.jeffrey.shared.common.model.RepositoryInfo;
import pbouda.jeffrey.shared.persistence.GroupLabel;
import pbouda.jeffrey.shared.persistence.StatementLabel;
import pbouda.jeffrey.shared.persistence.client.DatabaseClient;
import pbouda.jeffrey.shared.persistence.client.DatabaseClientProvider;

import java.nio.file.Path;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

public class JdbcProjectRepositoryRepository implements ProjectRepositoryRepository {

    //language=sql
    private static final String INSERT_REPOSITORY = """
            INSERT INTO repositories (project_id, repository_id, repository_type, workspaces_path, relative_workspace_path, relative_project_path)
            VALUES (:project_id, :repository_id, :repository_type, :workspaces_path, :relative_workspace_path, :relative_project_path)""";

    //language=sql
    private static final String ALL_IN_PROJECT = "SELECT * FROM repositories WHERE project_id = :project_id";

    //language=sql
    private static final String DELETE_BY_ID = "DELETE FROM repositories WHERE project_id = :project_id AND repository_id = :repository_id";

    //language=sql
    private static final String DELETE_ALL_IN_PROJECT = "DELETE FROM repositories WHERE project_id = :project_id";

    // Workspace Sessions SQL
    //language=SQL
    private static final String INSERT_REPOSITORY_SESSION = """
            INSERT INTO project_instance_sessions
            (session_id, repository_id, instance_id, session_order, relative_session_path, profiler_settings, finished_file, streaming_enabled, origin_created_at, created_at)
            VALUES (:session_id, :repository_id, :instance_id, :session_order, :relative_session_path, :profiler_settings, :finished_file, :streaming_enabled, :origin_created_at, :created_at)
            ON CONFLICT DO NOTHING""";

    //language=SQL
    private static final String SELECT_SESSIONS_BY_PROJECT_ID = """
            SELECT rs.* FROM project_instance_sessions rs
            JOIN repositories r ON rs.repository_id = r.repository_id
            WHERE r.project_id = :project_id
            ORDER BY rs.origin_created_at DESC""";

    //language=SQL
    private static final String SELECT_SESSION_BY_PROJECT_AND_SESSION_ID = """
            SELECT rs.* FROM project_instance_sessions rs
            JOIN repositories r ON rs.repository_id = r.repository_id
            WHERE r.project_id = :project_id AND rs.session_id = :session_id""";

    //language=SQL
    private static final String DELETE_REPOSITORY_SESSION = """
            DELETE FROM project_instance_sessions
            WHERE session_id = :session_id
            AND repository_id IN (SELECT repository_id FROM repositories WHERE project_id = :project_id)
            """;

    //language=SQL
    private static final String SELECT_UNFINISHED_SESSIONS = """
            SELECT rs.* FROM project_instance_sessions rs
            JOIN repositories r ON rs.repository_id = r.repository_id
            WHERE r.project_id = :project_id AND rs.finished_at IS NULL
            ORDER BY rs.origin_created_at DESC""";

    //language=SQL
    private static final String UPDATE_SESSION_FINISHED = """
            UPDATE project_instance_sessions
            SET finished_at = :finished_at
            WHERE session_id = :session_id
            AND repository_id IN (SELECT repository_id FROM repositories WHERE project_id = :project_id)""";

    //language=SQL
    private static final String MARK_UNFINISHED_SESSIONS_FINISHED = """
            UPDATE project_instance_sessions
            SET finished_at = :finished_at
            WHERE instance_id = :instance_id AND finished_at IS NULL
            AND repository_id IN (SELECT repository_id FROM repositories WHERE project_id = :project_id)""";

    private final String projectId;
    private final DatabaseClient databaseClient;
    private final Clock clock;

    public JdbcProjectRepositoryRepository(
            Clock clock, String projectId, DatabaseClientProvider databaseClientProvider) {

        this.clock = clock;
        this.projectId = projectId;
        this.databaseClient = databaseClientProvider.provide(GroupLabel.PROJECT_REPOSITORIES);
    }

    @Override
    public void insert(RepositoryInfo repositoryInfo) {
        SqlParameterSource params = new MapSqlParameterSource()
                .addValue("project_id", projectId)
                .addValue("repository_id", IDGenerator.generate())
                .addValue("repository_type", repositoryInfo.repositoryType().name())
                .addValue("workspaces_path", repositoryInfo.workspacesPath())
                .addValue("relative_workspace_path", repositoryInfo.relativeWorkspacePath())
                .addValue("relative_project_path", repositoryInfo.relativeProjectPath());

        databaseClient.insert(StatementLabel.INSERT_REPOSITORY, INSERT_REPOSITORY, params);
    }

    @Override
    public List<RepositoryInfo> getAll() {
        MapSqlParameterSource paramSource = new MapSqlParameterSource()
                .addValue("project_id", projectId);

        return databaseClient.query(
                StatementLabel.FIND_ALL_REPOSITORIES, ALL_IN_PROJECT, paramSource, Mappers.repositoryInfoMapper());
    }

    @Override
    public void delete(String repositoryId) {
        MapSqlParameterSource paramSource = new MapSqlParameterSource()
                .addValue("repository_id", repositoryId)
                .addValue("project_id", projectId);

        databaseClient.delete(StatementLabel.DELETE_REPOSITORY, DELETE_BY_ID, paramSource);
    }

    @Override
    public void deleteAll() {
        MapSqlParameterSource paramSource = new MapSqlParameterSource()
                .addValue("project_id", projectId);

        databaseClient.delete(StatementLabel.DELETE_ALL_REPOSITORIES, DELETE_ALL_IN_PROJECT, paramSource);
    }

    @Override
    public void createSession(ProjectInstanceSessionInfo session) {
        MapSqlParameterSource paramSource = new MapSqlParameterSource()
                .addValue("session_id", IDGenerator.generate())
                .addValue("repository_id", session.repositoryId())
                .addValue("instance_id", session.instanceId())
                .addValue("session_order", session.order())
                .addValue("relative_session_path", session.relativeSessionPath().toString())
                .addValue("profiler_settings", session.profilerSettings())
                .addValue("finished_file", session.finishedFile())
                .addValue("streaming_enabled", session.streamingEnabled())
                .addValue("origin_created_at", session.originCreatedAt().atOffset(ZoneOffset.UTC))
                .addValue("created_at", clock.instant().atOffset(ZoneOffset.UTC));

        databaseClient.update(StatementLabel.INSERT_WORKSPACE_SESSION, INSERT_REPOSITORY_SESSION, paramSource);
    }

    @Override
    public void deleteSession(String sessionId) {
        MapSqlParameterSource paramSource = new MapSqlParameterSource()
                .addValue("project_id", projectId)
                .addValue("session_id", sessionId);

        databaseClient.delete(StatementLabel.DELETE_WORKSPACE_SESSION, DELETE_REPOSITORY_SESSION, paramSource);
    }

    @Override
    public List<ProjectInstanceSessionInfo> findAllSessions() {
        MapSqlParameterSource paramSource = new MapSqlParameterSource()
                .addValue("project_id", projectId);

        return databaseClient.query(
                StatementLabel.FIND_SESSIONS_BY_PROJECT_ID,
                SELECT_SESSIONS_BY_PROJECT_ID,
                paramSource,
                projectInstanceSessionMapper());
    }

    @Override
    public Optional<ProjectInstanceSessionInfo> findSessionById(String sessionId) {
        MapSqlParameterSource paramSource = new MapSqlParameterSource()
                .addValue("project_id", projectId)
                .addValue("session_id", sessionId);

        return databaseClient.querySingle(
                StatementLabel.FIND_SESSION_BY_PROJECT_AND_SESSION_ID,
                SELECT_SESSION_BY_PROJECT_AND_SESSION_ID,
                paramSource,
                projectInstanceSessionMapper());
    }

    @Override
    public List<ProjectInstanceSessionInfo> findUnfinishedSessions() {
        MapSqlParameterSource paramSource = new MapSqlParameterSource()
                .addValue("project_id", projectId);

        return databaseClient.query(
                StatementLabel.FIND_UNFINISHED_SESSIONS,
                SELECT_UNFINISHED_SESSIONS,
                paramSource,
                projectInstanceSessionMapper());
    }

    @Override
    public void markSessionFinished(String sessionId, java.time.Instant finishedAt) {
        MapSqlParameterSource paramSource = new MapSqlParameterSource()
                .addValue("project_id", projectId)
                .addValue("session_id", sessionId)
                .addValue("finished_at", finishedAt.atOffset(ZoneOffset.UTC));

        databaseClient.update(StatementLabel.UPDATE_SESSION_FINISHED, UPDATE_SESSION_FINISHED, paramSource);
    }

    @Override
    public void markUnfinishedSessionsFinished(String instanceId, java.time.Instant finishedAt) {
        MapSqlParameterSource paramSource = new MapSqlParameterSource()
                .addValue("project_id", projectId)
                .addValue("instance_id", instanceId)
                .addValue("finished_at", finishedAt.atOffset(ZoneOffset.UTC));

        databaseClient.update(StatementLabel.MARK_UNFINISHED_SESSIONS_FINISHED, MARK_UNFINISHED_SESSIONS_FINISHED, paramSource);
    }

    private static RowMapper<ProjectInstanceSessionInfo> projectInstanceSessionMapper() {
        return (rs, _) -> {
            return new ProjectInstanceSessionInfo(
                    rs.getString("session_id"),
                    rs.getString("repository_id"),
                    rs.getString("instance_id"),
                    rs.getInt("session_order"),
                    Path.of(rs.getString("relative_session_path")),
                    rs.getString("finished_file"),
                    rs.getString("profiler_settings"),
                    rs.getBoolean("streaming_enabled"),
                    Mappers.instant(rs, "origin_created_at"),
                    Mappers.instant(rs, "created_at"),
                    Mappers.instant(rs, "finished_at")
            );
        };
    }
}
