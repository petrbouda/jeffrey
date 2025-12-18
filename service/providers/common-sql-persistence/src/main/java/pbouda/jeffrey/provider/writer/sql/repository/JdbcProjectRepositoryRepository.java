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

package pbouda.jeffrey.provider.writer.sql.repository;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import pbouda.jeffrey.common.IDGenerator;
import pbouda.jeffrey.common.Json;
import pbouda.jeffrey.common.model.RepositoryInfo;
import pbouda.jeffrey.common.model.workspace.RepositorySessionInfo;
import pbouda.jeffrey.provider.api.repository.ProjectRepositoryRepository;
import pbouda.jeffrey.provider.writer.sql.GroupLabel;
import pbouda.jeffrey.provider.writer.sql.StatementLabel;
import pbouda.jeffrey.provider.writer.sql.client.DatabaseClient;
import pbouda.jeffrey.provider.writer.sql.client.DatabaseClientProvider;

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
            INSERT INTO repository_sessions
            (session_id, repository_id, relative_session_path, profiler_settings, finished_file, origin_created_at, created_at)
            VALUES (:session_id, :repository_id, :relative_session_path, :profiler_settings, :finished_file, :origin_created_at, :created_at)
            ON CONFLICT DO NOTHING""";

    //language=SQL
    private static final String SELECT_SESSIONS_BY_PROJECT_ID = """
            SELECT rs.* FROM repository_sessions rs
            JOIN repositories r ON rs.repository_id = r.repository_id
            WHERE r.project_id = :project_id
            ORDER BY rs.origin_created_at DESC""";

    //language=SQL
    private static final String SELECT_SESSION_BY_PROJECT_AND_SESSION_ID = """
            SELECT rs.* FROM repository_sessions rs
            JOIN repositories r ON rs.repository_id = r.repository_id
            WHERE r.project_id = :project_id AND rs.session_id = :session_id""";

    //language=SQL
    private static final String DELETE_REPOSITORY_SESSION = """
            DELETE FROM repository_sessions WHERE project_id = :project_id  AND session_id = :session_id
            """;

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
    public void createSession(RepositorySessionInfo session) {
        MapSqlParameterSource paramSource = new MapSqlParameterSource()
                .addValue("session_id", IDGenerator.generate())
                .addValue("repository_id", session.repositoryId())
                .addValue("relative_session_path", session.relativeSessionPath().toString())
                .addValue("profiler_settings", session.profilerSettings())
                .addValue("finished_file", session.finishedFile())
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
    public List<RepositorySessionInfo> findAllSessions() {
        MapSqlParameterSource paramSource = new MapSqlParameterSource()
                .addValue("project_id", projectId);

        return databaseClient.query(
                StatementLabel.FIND_SESSIONS_BY_PROJECT_ID,
                SELECT_SESSIONS_BY_PROJECT_ID,
                paramSource,
                workspaceSessionMapper());
    }

    @Override
    public Optional<RepositorySessionInfo> findSessionById(String sessionId) {
        MapSqlParameterSource paramSource = new MapSqlParameterSource()
                .addValue("project_id", projectId)
                .addValue("session_id", sessionId);

        return databaseClient.querySingle(
                StatementLabel.FIND_SESSION_BY_PROJECT_AND_SESSION_ID,
                SELECT_SESSION_BY_PROJECT_AND_SESSION_ID,
                paramSource,
                workspaceSessionMapper());
    }


    private static RowMapper<RepositorySessionInfo> workspaceSessionMapper() {
        return (rs, _) -> {
            return new RepositorySessionInfo(
                    rs.getString("session_id"),
                    rs.getString("repository_id"),
                    Path.of(rs.getString("relative_session_path")),
                    rs.getString("profiler_settings"),
                    rs.getString("finished_file"),
                    Mappers.instant(rs, "origin_created_at"),
                    Mappers.instant(rs, "created_at")
            );
        };
    }
}
