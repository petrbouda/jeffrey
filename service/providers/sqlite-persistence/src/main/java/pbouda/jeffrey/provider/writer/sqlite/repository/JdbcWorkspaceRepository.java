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
import pbouda.jeffrey.common.model.workspace.WorkspaceEvent;
import pbouda.jeffrey.common.model.workspace.WorkspaceEventConsumer;
import pbouda.jeffrey.common.model.workspace.WorkspaceEventType;
import pbouda.jeffrey.common.model.workspace.WorkspaceInfo;
import pbouda.jeffrey.common.model.workspace.WorkspaceSessionInfo;
import pbouda.jeffrey.provider.api.repository.WorkspaceRepository;
import pbouda.jeffrey.provider.writer.sqlite.GroupLabel;
import pbouda.jeffrey.provider.writer.sqlite.StatementLabel;
import pbouda.jeffrey.provider.writer.sqlite.client.DatabaseClient;

import javax.sql.DataSource;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public class JdbcWorkspaceRepository implements WorkspaceRepository {

    //language=SQL
    private static final String SELECT_ALL_WORKSPACES = """
            SELECT w.*, (SELECT COUNT(*) FROM main.projects p WHERE p.workspace_id = w.workspace_id) as project_count
            FROM main.workspaces w WHERE w.enabled = true""";

    //language=SQL
    private static final String SELECT_WORKSPACE_BY_ID = """
            SELECT w.*, (SELECT COUNT(*) FROM main.projects p WHERE p.workspace_id = w.workspace_id) as project_count
            FROM main.workspaces w WHERE w.workspace_id = :workspace_id AND w.enabled = true""";

    //language=SQL
    private static final String INSERT_WORKSPACE = """
            INSERT INTO main.workspaces (workspace_id, name, description, path, enabled, created_at)
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
            INSERT OR IGNORE INTO main.workspace_sessions
            (session_id, origin_session_id, project_id, workspace_id, last_detected_file, relative_path, workspaces_path, origin_created_at, created_at)
            VALUES (:session_id, :origin_session_id, :project_id, :workspace_id, :last_detected_file, :relative_path, :workspaces_path, :origin_created_at, :created_at)""";

    //language=SQL
    private static final String SELECT_SESSIONS_BY_PROJECT_ID = """
            SELECT ws.*, w.path as workspace_path FROM main.workspace_sessions ws
            JOIN main.workspaces w ON ws.workspace_id = w.workspace_id WHERE ws.project_id = :project_id
            ORDER BY ws.origin_created_at DESC""";

    //language=SQL
    private static final String SELECT_SESSION_BY_PROJECT_AND_SESSION_ID = """
            SELECT ws.*, w.path as workspace_path FROM main.workspace_sessions ws
            JOIN main.workspaces w ON ws.workspace_id = w.workspace_id
            WHERE ws.project_id = :project_id AND ws.session_id = :session_id""";

    // Workspace Events SQL
    //language=SQL
    private static final String INSERT_WORKSPACE_EVENT = """
            INSERT OR IGNORE INTO main.workspace_events (origin_event_id, workspace_id, project_id, event_type, content, origin_created_at, created_at)
            VALUES (:origin_event_id, :workspace_id, :project_id, :event_type, :content, :origin_created_at, :created_at)""";

    //language=SQL
    private static final String SELECT_EVENTS_BY_WORKSPACE_ID =
            "SELECT * FROM main.workspace_events WHERE workspace_id = :workspace_id ORDER BY created_at DESC";

    //language=SQL
    private static final String SELECT_EVENTS_BY_WORKSPACE_ID_FROM_OFFSET = """
            SELECT * FROM main.workspace_events WHERE workspace_id = :workspace_id AND event_id > :from_offset""";

    // Workspace Event Consumer SQL
    //language=SQL
    private static final String INSERT_EVENT_CONSUMER = """
            INSERT INTO main.workspace_event_consumers (consumer_id, workspace_id, created_at)
            VALUES (:consumer_id, :workspace_id, :created_at)""";

    //language=SQL
    private static final String UPDATE_EVENT_CONSUMER_UPDATE_OFFSET = """
            UPDATE main.workspace_event_consumers
            SET last_execution_at = :last_execution_at, last_offset = :last_offset
            WHERE consumer_id = :consumer_id AND workspace_id = :workspace_id""";

    //language=SQL
    private static final String SELECT_EVENT_CONSUMER_BY_ID = """
            SELECT * FROM main.workspace_event_consumers
            WHERE consumer_id = :consumer_id AND workspace_id = :workspace_id""";

    private final DatabaseClient databaseClient;
    private final Clock clock;

    public JdbcWorkspaceRepository(DataSource dataSource, Clock clock) {
        this.databaseClient = new DatabaseClient(dataSource, GroupLabel.WORKSPACES);
        this.clock = clock;
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

    @Override
    public void insertEvent(WorkspaceEvent workspaceEvent) {
        MapSqlParameterSource paramSource = new MapSqlParameterSource()
                .addValue("origin_event_id", workspaceEvent.originEventId())
                .addValue("workspace_id", workspaceEvent.workspaceId())
                .addValue("project_id", workspaceEvent.projectId())
                .addValue("event_type", workspaceEvent.eventType().name())
                .addValue("content", workspaceEvent.content())
                .addValue("origin_created_at", workspaceEvent.createdAt().toEpochMilli())
                .addValue("created_at", clock.instant().toEpochMilli());

        databaseClient.update(StatementLabel.INSERT_WORKSPACE_EVENT, INSERT_WORKSPACE_EVENT, paramSource);
    }

    @Override
    public void batchInsertEvents(List<WorkspaceEvent> workspaceEvents) {
        if (workspaceEvents.isEmpty()) {
            return;
        }

        MapSqlParameterSource[] paramSources = new MapSqlParameterSource[workspaceEvents.size()];
        for (int i = 0; i < workspaceEvents.size(); i++) {
            WorkspaceEvent event = workspaceEvents.get(i);
            paramSources[i] = new MapSqlParameterSource()
                    .addValue("origin_event_id", event.originEventId())
                    .addValue("workspace_id", event.workspaceId())
                    .addValue("project_id", event.projectId())
                    .addValue("event_type", event.eventType().name())
                    .addValue("content", event.content())
                    .addValue("origin_created_at", event.originCreatedAt().toEpochMilli())
                    .addValue("created_at", clock.instant().toEpochMilli());
        }

        databaseClient.batchInsert(StatementLabel.BATCH_INSERT_WORKSPACE_EVENTS, INSERT_WORKSPACE_EVENT, paramSources);
    }

    @Override
    public List<WorkspaceEvent> findEvents(String workspaceId) {
        MapSqlParameterSource paramSource = new MapSqlParameterSource()
                .addValue("workspace_id", workspaceId);

        return databaseClient.query(
                StatementLabel.FIND_EVENTS_BY_WORKSPACE_ID,
                SELECT_EVENTS_BY_WORKSPACE_ID,
                paramSource,
                workspaceEventMapper());
    }

    @Override
    public List<WorkspaceEvent> findEventsFromOffset(String workspaceId, long fromOffset) {
        MapSqlParameterSource paramSource = new MapSqlParameterSource()
                .addValue("workspace_id", workspaceId)
                .addValue("from_offset", fromOffset);

        return databaseClient.query(
                StatementLabel.FIND_EVENTS_BY_WORKSPACE_ID_FROM_CREATED_AT,
                SELECT_EVENTS_BY_WORKSPACE_ID_FROM_OFFSET,
                paramSource,
                workspaceEventMapper());
    }

    @Override
    public void createEventConsumer(String consumerId, String workspaceId) {
        MapSqlParameterSource paramSource = new MapSqlParameterSource()
                .addValue("consumer_id", consumerId)
                .addValue("workspace_id", workspaceId)
                .addValue("created_at", clock.instant().toEpochMilli());

        databaseClient.update(StatementLabel.INSERT_EVENT_CONSUMER, INSERT_EVENT_CONSUMER, paramSource);
    }

    @Override
    public void updateEventConsumerOffset(String consumerId, String workspaceId, long lastOffset) {
        MapSqlParameterSource paramSource = new MapSqlParameterSource()
                .addValue("consumer_id", consumerId)
                .addValue("workspace_id", workspaceId)
                .addValue("last_offset", lastOffset)
                .addValue("last_execution_at", clock.instant().toEpochMilli());

        databaseClient.update(
                StatementLabel.UPDATE_EVENT_CONSUMER_UPDATE_OFFSET, UPDATE_EVENT_CONSUMER_UPDATE_OFFSET, paramSource);
    }

    @Override
    public Optional<WorkspaceEventConsumer> findEventConsumer(String consumerId, String workspaceId) {
        MapSqlParameterSource paramSource = new MapSqlParameterSource()
                .addValue("consumer_id", consumerId)
                .addValue("workspace_id", workspaceId);

        return databaseClient.querySingle(
                StatementLabel.FIND_EVENT_CONSUMER_BY_ID,
                SELECT_EVENT_CONSUMER_BY_ID,
                paramSource,
                workspaceEventConsumerMapper());
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

    private static RowMapper<WorkspaceEvent> workspaceEventMapper() {
        return (rs, _) -> new WorkspaceEvent(
                rs.getLong("event_id"),
                rs.getString("origin_event_id"),
                rs.getString("project_id"),
                rs.getString("workspace_id"),
                WorkspaceEventType.valueOf(rs.getString("event_type")),
                rs.getString("content"),
                Instant.ofEpochMilli(rs.getLong("origin_created_at")),
                Instant.ofEpochMilli(rs.getLong("created_at"))
        );
    }

    private static RowMapper<WorkspaceEventConsumer> workspaceEventConsumerMapper() {
        return (rs, _) -> {
            long lastExecutionAt = rs.getLong("last_execution_at");
            long lastOffset = rs.getLong("last_offset");

            return new WorkspaceEventConsumer(
                    rs.getString("consumer_id"),
                    lastOffset,
                    lastExecutionAt == 0 ? null : Instant.ofEpochMilli(lastExecutionAt),
                    Instant.ofEpochMilli(rs.getLong("created_at"))
            );
        };
    }
}
