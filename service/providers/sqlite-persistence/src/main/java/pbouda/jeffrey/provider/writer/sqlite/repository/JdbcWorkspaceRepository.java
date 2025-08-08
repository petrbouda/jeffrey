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

package pbouda.jeffrey.provider.writer.sqlite.repository;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import pbouda.jeffrey.common.model.WorkspaceInfo;
import pbouda.jeffrey.common.model.WorkspaceSessionInfo;
import pbouda.jeffrey.provider.api.repository.WorkspaceRepository;
import pbouda.jeffrey.provider.writer.sqlite.GroupLabel;
import pbouda.jeffrey.provider.writer.sqlite.StatementLabel;
import pbouda.jeffrey.provider.writer.sqlite.client.DatabaseClient;

import javax.sql.DataSource;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public class JdbcWorkspaceRepository implements WorkspaceRepository {

    //language=SQL
    private static final String SELECT_ALL_WORKSPACES = """
            SELECT w.*,
                   (SELECT COUNT(*) FROM main.projects p WHERE p.workspace_id = w.workspace_id) as project_count
            FROM main.workspaces w
            WHERE w.enabled = true""";

    //language=SQL
    private static final String SELECT_WORKSPACE_BY_ID = """
            SELECT w.*,
                   (SELECT COUNT(*) FROM main.projects p WHERE p.workspace_id = w.workspace_id) as project_count
            FROM main.workspaces w
            WHERE w.workspace_id = :workspace_id AND w.enabled = true""";

    //language=SQL
    private static final String INSERT_WORKSPACE = """
            INSERT INTO main.workspaces
            (workspace_id, name, description, path, enabled, created_at)
            VALUES (:workspace_id, :name, :description, :path, :enabled, :created_at)""";

    //language=SQL
    private static final String DELETE_WORKSPACE = 
            "UPDATE main.workspaces SET enabled = false WHERE workspace_id = :workspace_id";

    //language=SQL
    private static final String CHECK_NAME_EXISTS = 
            "SELECT COUNT(*) FROM main.workspaces WHERE name = :name AND enabled = true";

    // Workspace Sessions SQL
    //language=SQL
    private static final String INSERT_WORKSPACE_SESSION = """
            INSERT INTO main.workspace_sessions
            (session_id, project_id, workspace_session_id, last_detected_file, relative_path, created_at)
            VALUES (:session_id, :project_id, :workspace_session_id, :last_detected_file, :relative_path, :created_at)""";

    //language=SQL
    private static final String SELECT_SESSIONS_BY_PROJECT_ID =
            "SELECT * FROM main.workspace_sessions WHERE project_id = :project_id";

    //language=SQL
    private static final String SELECT_SESSION_BY_PROJECT_AND_SESSION_ID =
            "SELECT * FROM main.workspace_sessions WHERE project_id = :project_id AND session_id = :session_id";

    private final DatabaseClient databaseClient;

    public JdbcWorkspaceRepository(DataSource dataSource) {
        this.databaseClient = new DatabaseClient(dataSource, GroupLabel.WORKSPACES);
    }

    @Override
    public List<WorkspaceInfo> findAll() {
        return databaseClient.query(
                StatementLabel.FIND_ALL_WORKSPACES, 
                SELECT_ALL_WORKSPACES, 
                new MapSqlParameterSource(), 
                workspaceMapper());
    }

    @Override
    public Optional<WorkspaceInfo> findById(String workspaceId) {
        MapSqlParameterSource paramSource = new MapSqlParameterSource()
                .addValue("workspace_id", workspaceId);

        return databaseClient.querySingle(
                StatementLabel.FIND_WORKSPACE_BY_ID, 
                SELECT_WORKSPACE_BY_ID, 
                paramSource, 
                workspaceMapper());
    }

    @Override
    public WorkspaceInfo create(WorkspaceInfo workspaceInfo) {
        MapSqlParameterSource paramSource = new MapSqlParameterSource()
                .addValue("workspace_id", workspaceInfo.id())
                .addValue("name", workspaceInfo.name())
                .addValue("description", workspaceInfo.description())
                .addValue("path", workspaceInfo.path())
                .addValue("enabled", workspaceInfo.enabled())
                .addValue("created_at", workspaceInfo.createdAt().toEpochMilli());

        databaseClient.update(StatementLabel.INSERT_WORKSPACE, INSERT_WORKSPACE, paramSource);
        return workspaceInfo;
    }

    @Override
    public boolean delete(String workspaceId) {
        MapSqlParameterSource paramSource = new MapSqlParameterSource()
                .addValue("workspace_id", workspaceId);

        int rowsAffected = databaseClient.update(StatementLabel.DELETE_WORKSPACE, DELETE_WORKSPACE, paramSource);
        return rowsAffected > 0;
    }

    @Override
    public boolean existsByName(String name) {
        MapSqlParameterSource paramSource = new MapSqlParameterSource()
                .addValue("name", name);

        Integer count = databaseClient.querySingle(
                StatementLabel.CHECK_NAME_EXISTS, 
                CHECK_NAME_EXISTS, 
                paramSource, 
                (rs, _) -> rs.getInt(1)
        ).orElse(0);

        return count > 0;
    }

    @Override
    public WorkspaceSessionInfo createSession(WorkspaceSessionInfo session) {
        MapSqlParameterSource paramSource = new MapSqlParameterSource()
                .addValue("session_id", session.sessionId())
                .addValue("project_id", session.projectId())
                .addValue("workspace_session_id", session.workspaceSessionId())
                .addValue("last_detected_file", session.lastDetectedFile())
                .addValue("relative_path", session.relativePath())
                .addValue("created_at", session.createdAt().toEpochMilli());

        databaseClient.update(StatementLabel.INSERT_WORKSPACE_SESSION, INSERT_WORKSPACE_SESSION, paramSource);
        return session;
    }

    @Override
    public List<WorkspaceSessionInfo> findSessionsByProjectId(String projectId) {
        MapSqlParameterSource paramSource = new MapSqlParameterSource()
                .addValue("project_id", projectId);

        return databaseClient.query(
                StatementLabel.FIND_SESSIONS_BY_PROJECT_ID, 
                SELECT_SESSIONS_BY_PROJECT_ID, 
                paramSource, 
                workspaceSessionMapper());
    }

    @Override
    public Optional<WorkspaceSessionInfo> findSessionByProjectIdAndSessionId(String projectId, String sessionId) {
        MapSqlParameterSource paramSource = new MapSqlParameterSource()
                .addValue("project_id", projectId)
                .addValue("session_id", sessionId);

        return databaseClient.querySingle(
                StatementLabel.FIND_SESSION_BY_PROJECT_AND_SESSION_ID, 
                SELECT_SESSION_BY_PROJECT_AND_SESSION_ID, 
                paramSource, 
                workspaceSessionMapper());
    }

    private static RowMapper<WorkspaceInfo> workspaceMapper() {
        return (rs, _) -> new WorkspaceInfo(
                rs.getString("workspace_id"),
                rs.getString("name"),
                rs.getString("description"),
                rs.getString("path"),
                rs.getBoolean("enabled"),
                Instant.ofEpochMilli(rs.getLong("created_at")),
                rs.getInt("project_count")
        );
    }

    private static RowMapper<WorkspaceSessionInfo> workspaceSessionMapper() {
        return (rs, _) -> {
            return new WorkspaceSessionInfo(
                    rs.getString("session_id"),
                    rs.getString("project_id"),
                    rs.getString("workspace_session_id"),
                    rs.getString("last_detected_file"),
                    Path.of(rs.getString("relative_path")),
                    Instant.ofEpochMilli(rs.getLong("created_at"))
            );
        };
    }
}
