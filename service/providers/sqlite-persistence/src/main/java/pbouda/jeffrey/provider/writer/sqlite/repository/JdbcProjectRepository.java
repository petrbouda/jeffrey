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
import pbouda.jeffrey.common.model.ProfileInfo;
import pbouda.jeffrey.common.model.ProjectInfo;
import pbouda.jeffrey.common.model.workspace.WorkspaceSessionInfo;
import pbouda.jeffrey.provider.api.repository.ProjectRepository;
import pbouda.jeffrey.provider.writer.sqlite.GroupLabel;
import pbouda.jeffrey.provider.writer.sqlite.StatementLabel;
import pbouda.jeffrey.provider.writer.sqlite.client.DatabaseClient;

import javax.sql.DataSource;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public class JdbcProjectRepository implements ProjectRepository {

    //language=SQL
    private static final String SELECT_ALL_PROFILES =
            "SELECT * FROM profiles WHERE project_id = :project_id";

    //language=SQL
    private static final String SELECT_SINGLE_PROJECT = """
            SELECT * FROM projects p
            JOIN workspaces w ON p.workspace_id = w.workspace_id
            WHERE p.project_id = :project_id""";

    //language=SQL
    private static final String UPDATE_PROJECTS_NAME =
            "UPDATE projects SET project_name = :project_name WHERE project_id = :project_id";

    //language=SQL
    private static final String DELETE_PROJECT = """
            BEGIN TRANSACTION;
            DELETE FROM schedulers WHERE project_id = '%project_id%';
            DELETE FROM repositories WHERE project_id = '%project_id%';
            DELETE FROM recording_folders WHERE project_id = '%project_id%';
            DELETE FROM recordings WHERE project_id = '%project_id%';
            DELETE FROM projects WHERE project_id = '%project_id%';
            COMMIT;""";

    // Workspace Sessions SQL
    //language=SQL
    private static final String INSERT_WORKSPACE_SESSION = """
            INSERT OR IGNORE INTO main.workspace_sessions
            (session_id, origin_session_id, project_id, workspace_id, last_detected_file, relative_path, workspaces_path, origin_created_at, created_at)
            VALUES (:session_id, :origin_session_id, :project_id, :workspace_id, :last_detected_file, :relative_path, :workspaces_path, :origin_created_at, :created_at)""";

    //language=SQL
    private static final String SELECT_SESSIONS_BY_PROJECT_ID = """
            SELECT ws.*, w.location as workspace_path FROM main.workspace_sessions ws
            JOIN main.workspaces w ON ws.workspace_id = w.workspace_id WHERE ws.project_id = :project_id
            ORDER BY ws.origin_created_at DESC""";

    //language=SQL
    private static final String SELECT_SESSION_BY_PROJECT_AND_SESSION_ID = """
            SELECT ws.*, w.location as workspace_path FROM main.workspace_sessions ws
            JOIN main.workspaces w ON ws.workspace_id = w.workspace_id
            WHERE ws.project_id = :project_id AND ws.session_id = :session_id""";


    private final String projectId;
    private final DatabaseClient databaseClient;
    private final Clock clock;

    public JdbcProjectRepository(String projectId, DataSource dataSource, Clock clock) {
        this.projectId = projectId;
        this.databaseClient = new DatabaseClient(dataSource, GroupLabel.SINGLE_PROJECT);
        this.clock = clock;
    }

    @Override
    public void delete() {
        databaseClient.delete(
                StatementLabel.DELETE_PROJECT, DELETE_PROJECT.replaceAll("%project_id%", projectId));
    }

    @Override
    public List<ProfileInfo> findAllProfiles() {
        MapSqlParameterSource paramSource = new MapSqlParameterSource()
                .addValue("project_id", projectId);

        return databaseClient.query(
                StatementLabel.FIND_ALL_PROFILES, SELECT_ALL_PROFILES, paramSource, Mappers.profileInfoMapper());
    }

    @Override
    public Optional<ProjectInfo> find() {
        MapSqlParameterSource paramSource = new MapSqlParameterSource()
                .addValue("project_id", projectId);

        return databaseClient.querySingle(
                StatementLabel.FIND_PROJECT, SELECT_SINGLE_PROJECT, paramSource, Mappers.projectInfoMapper());
    }

    @Override
    public void updateProjectName(String name) {
        MapSqlParameterSource paramSource = new MapSqlParameterSource()
                .addValue("project_id", projectId)
                .addValue("project_name", name);

        databaseClient.update(StatementLabel.UPDATE_PROJECT_NAME, UPDATE_PROJECTS_NAME, paramSource);
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
                .addValue("origin_created_at", session.originCreatedAt().toEpochMilli())
                .addValue("created_at", clock.instant().toEpochMilli());

        databaseClient.update(StatementLabel.INSERT_WORKSPACE_SESSION, INSERT_WORKSPACE_SESSION, paramSource);
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
                    rs.getString("last_detected_file"),
                    Path.of(rs.getString("relative_path")),
                    workspacesPath,
                    Instant.ofEpochMilli(rs.getLong("origin_created_at")),
                    Instant.ofEpochMilli(rs.getLong("created_at"))
            );
        };
    }
}
