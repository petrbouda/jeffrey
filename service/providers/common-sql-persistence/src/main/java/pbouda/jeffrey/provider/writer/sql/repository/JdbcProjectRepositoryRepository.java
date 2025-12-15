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
import pbouda.jeffrey.common.model.workspace.WorkspaceSessionInfo;
import pbouda.jeffrey.provider.api.model.DBRepositoryInfo;
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
            INSERT INTO repositories (project_id, id, type, finished_session_detection_file)
            VALUES (:project_id, :id, :type, :finished_session_detection_file)""";

    //language=sql
    private static final String ALL_IN_PROJECT = "SELECT * FROM repositories WHERE project_id = :project_id";

    //language=sql
    private static final String DELETE_BY_ID = "DELETE FROM repositories WHERE project_id = :project_id AND id = :id";

    //language=sql
    private static final String DELETE_ALL_IN_PROJECT = "DELETE FROM repositories WHERE project_id = :project_id";

    // Workspace Sessions SQL
    //language=SQL
    private static final String INSERT_WORKSPACE_SESSION = """
            INSERT INTO workspace_sessions
            (session_id, origin_session_id, project_id, workspace_id, last_detected_file, relative_path, workspaces_path, profiler_settings, origin_created_at, created_at)
            VALUES (:session_id, :origin_session_id, :project_id, :workspace_id, :last_detected_file, :relative_path, :workspaces_path, :profiler_settings, :origin_created_at, :created_at)
            ON CONFLICT DO NOTHING""";

    //language=SQL
    private static final String SELECT_SESSIONS_BY_PROJECT_ID = """
            SELECT ws.*, w.location as workspace_path, w.repository_id FROM workspace_sessions ws
            JOIN workspaces w ON ws.workspace_id = w.workspace_id WHERE ws.project_id = :project_id
            ORDER BY ws.origin_created_at DESC""";

    //language=SQL
    private static final String SELECT_SESSION_BY_PROJECT_AND_SESSION_ID = """
            SELECT ws.*, w.location as workspace_path, w.repository_id FROM workspace_sessions ws
            JOIN workspaces w ON ws.workspace_id = w.workspace_id
            WHERE ws.project_id = :project_id AND ws.session_id = :session_id""";

    //language=SQL
    private static final String DELETE_WORKSPACE_SESSION = """
            DELETE FROM workspace_sessions WHERE project_id = :project_id  AND session_id = :session_id
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
    public void insert(DBRepositoryInfo repositoryInfo) {
        SqlParameterSource params = new MapSqlParameterSource()
                .addValue("project_id", projectId)
                .addValue("id", IDGenerator.generate())
                .addValue("type", repositoryInfo.type().name())
                .addValue("finished_session_detection_file", repositoryInfo.finishedSessionDetectionFile());

        databaseClient.insert(StatementLabel.INSERT_REPOSITORY, INSERT_REPOSITORY, params);
    }

    @Override
    public List<DBRepositoryInfo> getAll() {
        MapSqlParameterSource paramSource = new MapSqlParameterSource()
                .addValue("project_id", projectId);

        return databaseClient.query(
                StatementLabel.FIND_ALL_REPOSITORIES, ALL_IN_PROJECT, paramSource, Mappers.repositoryInfoMapper());
    }

    @Override
    public void delete(String id) {
        MapSqlParameterSource paramSource = new MapSqlParameterSource()
                .addValue("id", id)
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
    public void createSession(WorkspaceSessionInfo session) {
        MapSqlParameterSource paramSource = new MapSqlParameterSource()
                .addValue("session_id", session.sessionId())
                .addValue("origin_session_id", session.originSessionId())
                .addValue("project_id", session.projectId())
                .addValue("workspace_id", session.workspaceId())
                .addValue("last_detected_file", session.lastDetectedFile())
                .addValue("relative_path", session.relativePath().toString())
                .addValue("workspaces_path", session.workspacesPath() != null ? session.workspacesPath().toString() : null)
                .addValue("profiler_settings", session.profilerSettings() != null ? Json.toString(session.profilerSettings()) : null)
                .addValue("origin_created_at", session.originCreatedAt().atOffset(ZoneOffset.UTC))
                .addValue("created_at", clock.instant().atOffset(ZoneOffset.UTC));

        databaseClient.update(StatementLabel.INSERT_WORKSPACE_SESSION, INSERT_WORKSPACE_SESSION, paramSource);
    }

    @Override
    public void deleteSession(String sessionId) {
        MapSqlParameterSource paramSource = new MapSqlParameterSource()
                .addValue("project_id", projectId)
                .addValue("session_id", sessionId);

        databaseClient.delete(StatementLabel.DELETE_WORKSPACE_SESSION, DELETE_WORKSPACE_SESSION, paramSource);
    }

    @Override
    public List<WorkspaceSessionInfo> findAllSessions() {
        MapSqlParameterSource paramSource = new MapSqlParameterSource()
                .addValue("project_id", projectId);

        return databaseClient.query(
                StatementLabel.FIND_SESSIONS_BY_PROJECT_ID,
                SELECT_SESSIONS_BY_PROJECT_ID,
                paramSource,
                workspaceSessionMapper());
    }

    @Override
    public Optional<WorkspaceSessionInfo> findSessionById(String sessionId) {
        MapSqlParameterSource paramSource = new MapSqlParameterSource()
                .addValue("project_id", projectId)
                .addValue("session_id", sessionId);

        return databaseClient.querySingle(
                StatementLabel.FIND_SESSION_BY_PROJECT_AND_SESSION_ID,
                SELECT_SESSION_BY_PROJECT_AND_SESSION_ID,
                paramSource,
                workspaceSessionMapper());
    }


    private static RowMapper<WorkspaceSessionInfo> workspaceSessionMapper() {
        return (rs, _) -> {
            String workspacesPathStr = rs.getString("workspaces_path");
            Path workspacesPath = workspacesPathStr != null ? Path.of(workspacesPathStr) : null;

            return new WorkspaceSessionInfo(
                    rs.getString("session_id"),
                    rs.getString("origin_session_id"),
                    rs.getString("project_id"),
                    rs.getString("workspace_id"),
                    rs.getString("repository_id"),
                    rs.getString("last_detected_file"),
                    Path.of(rs.getString("relative_path")),
                    workspacesPath,
                    rs.getString("profiler_settings"),
                    Mappers.instant(rs, "origin_created_at"),
                    Mappers.instant(rs, "created_at")
            );
        };
    }
}
